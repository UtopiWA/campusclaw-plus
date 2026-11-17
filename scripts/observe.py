"""Class10 轻量观测入口：按时间窗汇总技能调用、错误率、API P95 和队列深度。

数据来源沿用既有事实：
- 技能调用/错误率：`messages`（成功技能轮次的事实来源）；
- API 耗时/状态：后端既有 `http_request_completed` JSON 日志；
- 队列深度：RabbitMQ management API。

不新增应用接口、不采集用户正文；输出 HTML/JSON 到忽略目录。告警必须显式传入
webhook 并实际送达；未送达不会记为通过。

用法：
  python scripts/observe.py --env-file .env.team --window 5m
  python scripts/observe.py --env-file .env.team --window 5m \
      --alert-webhook http://127.0.0.1:18081/alerts --alert-min-requests 20
"""
from __future__ import annotations

import argparse
import base64
from collections import defaultdict
from datetime import datetime, timezone
from html import escape
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "apitest"))

from common.config import TestConfig, _read_dotenv  # noqa: E402
from common.database import DatabaseClient  # noqa: E402


def parse_window(value: str) -> int:
    match = re.fullmatch(r"(\d+)([smh])", value.strip())
    if not match or int(match.group(1)) <= 0:
        raise ValueError("window 必须形如 30s、5m 或 1h")
    return int(match.group(1)) * {"s": 1, "m": 60, "h": 3600}[match.group(2)]


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(ratio * len(ordered)) - 1))
    return round(ordered[index], 2)


def rabbit_settings(env: dict[str, str]) -> tuple[str, str, str, str]:
    return (env.get("RABBITMQ_MANAGEMENT_HOST", "127.0.0.1"),
            env.get("RABBITMQ_MANAGEMENT_PORT", "15672"),
            env.get("RABBITMQ_USER", "guest"), env.get("RABBITMQ_PASSWORD", "guest"))


def queue_depth(env: dict[str, str], queue: str) -> int | None:
    host, port, user, password = rabbit_settings(env)
    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    request = Request(f"http://{host}:{port}/api/queues/%2F/{quote(queue, safe='')}",
                      headers={"Authorization": f"Basic {token}"})
    try:
        with urlopen(request, timeout=5) as response:
            return int(json.loads(response.read()).get("messages", 0))
    except (URLError, OSError, ValueError, TypeError):
        return None


def backend_container(env_file: Path) -> str | None:
    """通过 Compose 找到当前项目的 backend 容器；不按名字猜其他项目。"""
    try:
        result = subprocess.run(
            ["docker", "compose", "--env-file", str(env_file), "ps", "-q", "backend"],
            cwd=ROOT, capture_output=True, text=True, timeout=20, check=False)
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    name = result.stdout.strip().splitlines()
    return name[0] if name and name[0] else None


def api_logs(container: str, seconds: int) -> list[dict]:
    try:
        result = subprocess.run(
            ["docker", "logs", "--since", f"{seconds}s", container],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=30, check=False)
    except (OSError, subprocess.SubprocessError):
        return []
    rows: list[dict] = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            continue
        if payload.get("event") != "http_request_completed":
            continue
        path = payload.get("path")
        status = payload.get("status")
        duration = payload.get("durationMs")
        if not isinstance(path, str) or not path.startswith("/api/"):
            continue
        if not isinstance(status, int) or not isinstance(duration, int):
            continue
        rows.append({"path": path, "status": status, "durationMs": duration})
    return rows


def skill_metrics(db: DatabaseClient, seconds: int) -> dict:
    window = int(seconds)
    rows = db.query_all(
        f"""
        SELECT COALESCE(skill_name, CONCAT('skill-', skill_id)) AS skill,
               COUNT(*) AS total,
               SUM(status = 'COMPLETED') AS completed,
               SUM(status <> 'COMPLETED') AS failed
        FROM messages
        WHERE role = 'ASSISTANT' AND skill_id IS NOT NULL
          AND created_at >= UTC_TIMESTAMP(6) - INTERVAL {window} SECOND
        GROUP BY skill
        ORDER BY total DESC, skill
        """)
    series = db.query_all(
        f"""
        SELECT COALESCE(skill_name, CONCAT('skill-', skill_id)) AS skill, created_at
        FROM messages
        WHERE role = 'ASSISTANT' AND skill_id IS NOT NULL
          AND created_at >= UTC_TIMESTAMP(6) - INTERVAL {window} SECOND
        ORDER BY created_at, id
        """)
    audit_failures = db.query_all(
        f"""
        SELECT COALESCE(skill_name, CONCAT('skill-', skill_id)) AS skill, COUNT(*) AS failed
        FROM security_audit_events
        WHERE action = 'SKILL_EXECUTE' AND status = 'FAILED'
          AND created_at >= UTC_TIMESTAMP(6) - INTERVAL {window} SECOND
        GROUP BY skill
        """)
    skills = []
    for row in rows:
        total = int(row["total"] or 0)
        failed = int(row["failed"] or 0)
        skills.append({"skill": row["skill"], "total": total, "completed": total - failed,
                       "failed": failed, "errorRate": round(failed / total, 4) if total else 0.0})
    buckets: dict[str, dict[int, int]] = defaultdict(lambda: defaultdict(int))
    bucket_seconds = max(60, seconds // 12)
    start = int(datetime.now(timezone.utc).timestamp()) - seconds
    for row in series:
        created = row["created_at"]
        stamp = created.replace(tzinfo=timezone.utc).timestamp() if created.tzinfo is None else created.timestamp()
        key = int((stamp - start) // bucket_seconds)
        buckets[str(row["skill"])][max(0, key)] += 1
    return {"skills": skills,
            "auditFailures": {row["skill"]: int(row["failed"]) for row in audit_failures},
            "buckets": {skill: dict(sorted(points.items())) for skill, points in buckets.items()},
            "bucketSeconds": bucket_seconds}


def api_metrics(rows: list[dict]) -> dict:
    durations = [float(row["durationMs"]) for row in rows]
    non_sse = [float(row["durationMs"]) for row in rows
               if not row["path"].endswith("/messages")]
    errors = [row for row in rows if row["status"] >= 500]
    client_errors = [row for row in rows if 400 <= row["status"] < 500]
    return {
        "requests": len(rows),
        "p50Ms": percentile(durations, 0.50),
        "p95Ms": percentile(durations, 0.95),
        "p95ExcludingSseMs": percentile(non_sse, 0.95),
        "maxMs": round(max(durations), 2) if durations else 0.0,
        "serverErrors": len(errors),
        "clientErrors": len(client_errors),
        "errorRate": round(len(errors) / len(rows), 4) if rows else 0.0,
    }


def svg_lines(buckets: dict[str, dict[int, int]], bucket_count: int) -> str:
    if not buckets:
        return "<p class=empty>窗口内没有技能调用。</p>"
    width, row_height, left, top = 720, 46, 140, 16
    max_value = max((max(points.values()) for points in buckets.values() if points), default=1)
    colors = ["#2563eb", "#dc2626", "#059669", "#7c3aed", "#d97706", "#0891b2"]
    height = top + row_height * len(buckets) + 10
    parts = [f"<svg viewBox='0 0 {width} {height}' role='img' aria-label='技能调用量分线'>"]
    for index, (skill, points) in enumerate(buckets.items()):
        color = colors[index % len(colors)]
        y = top + index * row_height + row_height / 2
        parts.append(f"<text x='0' y='{y + 4}' font-size='12'>{escape(skill)}</text>")
        parts.append(f"<line x1='{left}' y1='{y}' x2='{width - 20}' y2='{y}' stroke='#e5e7eb'/>")
        coords = []
        for bucket in range(bucket_count):
            value = points.get(bucket, 0)
            x = left + (width - left - 20) * (bucket / max(1, bucket_count - 1))
            py = y - (value / max(1, max_value)) * (row_height - 16)
            coords.append(f"{x:.1f},{py:.1f}")
        parts.append(f"<polyline fill='none' stroke='{color}' stroke-width='2' points='{' '.join(coords)}'/>")
        for bucket, value in points.items():
            if 0 <= bucket < bucket_count:
                x = left + (width - left - 20) * (bucket / max(1, bucket_count - 1))
                py = y - (value / max(1, max_value)) * (row_height - 16)
                parts.append(f"<circle cx='{x:.1f}' cy='{py:.1f}' r='2.5' fill='{color}'/>")
    parts.append("</svg>")
    return "".join(parts)


def svg_error_bars(skills: list[dict]) -> str:
    if not skills:
        return "<p class=empty>窗口内没有技能调用。</p>"
    width, row_height, left = 720, 30, 170
    height = len(skills) * row_height + 10
    parts = [f"<svg viewBox='0 0 {width} {height}' role='img' aria-label='每技能错误率'>"]
    for index, row in enumerate(skills):
        y = index * row_height + 6
        rate = float(row["errorRate"])
        width_px = max(1 if row["failed"] else 0, int((width - left - 70) * rate))
        parts.append(f"<text x='0' y='{y + 13}' font-size='12'>{escape(row['skill'])}</text>")
        parts.append(f"<rect x='{left}' y='{y}' width='{width_px}' height='18' fill='#dc2626' rx='3'/>")
        parts.append(f"<text x='{left + width_px + 6}' y='{y + 13}' font-size='12'>"
                     f"{rate * 100:.1f}% ({row['failed']}/{row['total']})</text>")
    parts.append(f"<line x1='{left}' y1='0' x2='{left}' y2='{height}' stroke='#d1d5db'/>")
    parts.append("</svg>")
    return "".join(parts)


def write_html(path: Path, result: dict) -> None:
    skills = result["skill"]["skills"]
    api = result["api"]
    queue = result["queue"]
    bucket_count = max(1, (result["windowSeconds"] // result["skill"]["bucketSeconds"]) + 1)
    rows = "".join(
        f"<tr><td>{escape(row['skill'])}</td><td>{row['total']}</td>"
        f"<td>{row['failed']}</td><td>{row['errorRate'] * 100:.1f}%</td></tr>"
        for row in skills)
    alert = result.get("alert") or {}
    html = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CampusClaw-Plus 观测快照</title>
<style>
body{{font-family:system-ui,sans-serif;margin:2rem;color:#111827;max-width:900px}}
h1{{font-size:1.4rem}} h2{{font-size:1.1rem;margin-top:2rem}}
table{{border-collapse:collapse;width:100%}} th,td{{text-align:left;padding:.35rem .6rem;border-bottom:1px solid #e5e7eb}}
.empty{{color:#6b7280}} .alert{{background:#fef2f2;border:1px solid #fecaca;padding:.8rem;border-radius:6px}}
.ok{{background:#ecfdf5;border:1px solid #a7f3d0;padding:.8rem;border-radius:6px}}
code{{background:#f3f4f6;padding:.1rem .3rem;border-radius:3px}}
</style></head><body>
<h1>CampusClaw-Plus 观测快照</h1>
<p>生成时间：{escape(result['generatedAt'])} · 时间窗：{result['windowSeconds']} 秒 ·
API 请求 {api['requests']} 条 · 队列 <code>{escape(result['queue']['name'])}</code>
深度 {escape(str(queue['depth']))}</p>
<h2>1. 技能调用量（按时间分桶）</h2>
{svg_lines(result['skill']['buckets'], bucket_count)}
<h2>2. 每技能错误率</h2>
{svg_error_bars(skills)}
<table><thead><tr><th>技能</th><th>调用</th><th>失败</th><th>错误率</th></tr></thead><tbody>{rows}</tbody></table>
<h2>3. 接口 P95 与队列深度</h2>
<table><tbody>
<tr><td>API P95</td><td>{api['p95Ms']} ms</td></tr>
<tr><td>API P95（不含 SSE）</td><td>{api['p95ExcludingSseMs']} ms</td></tr>
<tr><td>API P50 / 最大</td><td>{api['p50Ms']} ms / {api['maxMs']} ms</td></tr>
<tr><td>5xx / 4xx</td><td>{api['serverErrors']} / {api['clientErrors']}</td></tr>
<tr><td>队列深度</td><td>{escape(str(queue['depth']))}</td></tr>
</tbody></table>
<h2>告警</h2>
<p class="{'alert' if alert.get('triggered') else 'ok'}">
规则：{escape(alert.get('rule', '未配置告警'))}<br>
触发：{escape(str(alert.get('triggered')))} · 送达：{escape(str(alert.get('delivered')))}<br>
{escape(alert.get('detail', ''))}
</p>
</body></html>"""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(html, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--window", default="15m")
    parser.add_argument("--queue", default="grading.tasks")
    parser.add_argument("--html", type=Path, help="HTML 面板输出路径")
    parser.add_argument("--alert-webhook", help="告警实际送达的 HTTP 接收地址")
    parser.add_argument("--alert-error-rate", type=float, default=0.01)
    parser.add_argument("--alert-min-requests", type=int, default=20)
    parser.add_argument("--owner", default="Class10 值班成员")
    parser.add_argument("--action", default="检查后端错误日志与依赖状态，按 runbook 处置")
    args = parser.parse_args(argv)
    try:
        seconds = parse_window(args.window)
    except ValueError as exception:
        print(f"ENVIRONMENT: {exception}", file=sys.stderr)
        return 2
    if args.alert_error_rate < 0 or args.alert_error_rate > 1 or args.alert_min_requests < 1:
        print("ENVIRONMENT: 告警阈值/最小请求数非法", file=sys.stderr)
        return 2

    env_file = args.env_file.resolve()
    env = _read_dotenv(env_file)
    os.environ.setdefault("APITEST_ENV_FILE", str(env_file))
    try:
        config = TestConfig.load()
    except ValueError as exception:
        print(f"ENVIRONMENT: {exception}", file=sys.stderr)
        return 2
    db = DatabaseClient(config)
    skill = skill_metrics(db, seconds)
    container = backend_container(env_file)
    logs = api_logs(container, seconds) if container else []
    api = api_metrics(logs)
    depth = queue_depth(env, args.queue)

    now = datetime.now(timezone.utc)
    stamp = now.strftime("%Y%m%dT%H%M%SZ")
    result = {
        "generatedAt": now.isoformat(),
        "windowSeconds": seconds,
        "skill": skill,
        "api": {**api, "source": "backend http_request_completed logs",
                "container": container or "unavailable"},
        "queue": {"name": args.queue, "depth": depth},
    }
    rule = (f"API 5xx 比例在 {args.window} 内 > {args.alert_error_rate:.2%} "
            f"且请求数 >= {args.alert_min_requests}")
    triggered = (api["requests"] >= args.alert_min_requests
                 and api["errorRate"] > args.alert_error_rate)
    delivered = False
    delivery_status = None
    if triggered and args.alert_webhook:
        payload = {"rule": rule, "observedErrorRate": api["errorRate"],
                   "requests": api["requests"], "serverErrors": api["serverErrors"],
                   "windowSeconds": seconds, "triggeredAt": now.isoformat(),
                   "owner": args.owner, "action": args.action,
                   "panel": str(args.html or ""), "runId": stamp}
        try:
            request = Request(args.alert_webhook, method="POST",
                              data=json.dumps(payload, ensure_ascii=False).encode(),
                              headers={"Content-Type": "application/json"})
            with urlopen(request, timeout=10) as response:
                delivered = 200 <= response.status < 300
                delivery_status = response.status
        except (URLError, OSError) as exception:
            delivery_status = type(exception).__name__
    detail = (f"规则：{rule}；触发={triggered}；送达={delivered}；"
              f"HTTP={delivery_status}；负责人：{args.owner}；动作：{args.action}")
    result["alert"] = {"rule": rule, "triggered": triggered, "delivered": delivered,
                       "deliveryStatus": delivery_status, "owner": args.owner,
                       "action": args.action, "webhookConfigured": bool(args.alert_webhook),
                       "detail": detail}

    html_path = args.html or (ROOT / "backend/target/observe" / f"observe-{stamp}.html")
    result["html"] = str(html_path)
    write_html(html_path, result)
    output = ROOT / "backend/target/observe" / f"observe-{stamp}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"skills={len(skill['skills'])} apiRequests={api['requests']} "
          f"apiP95={api['p95Ms']}ms serverErrors={api['serverErrors']} queue={depth}")
    print(f"alert triggered={triggered} delivered={delivered} status={delivery_status}")
    print(f"observe summary: {output}")
    print(f"observe panel: {html_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
