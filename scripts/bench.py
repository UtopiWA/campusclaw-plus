"""Class10 压测入口：提交、任务查询、检索三个接口分别测吞吐与 P95。

用法（使用独立验收环境，确认队列为空）：
  python scripts/bench.py --env-file .env.team --levels 1,4,8,16 --requests 20

设计约束：
- 每次提交使用本轮新建的独立作业，避免把幂等重放或 409 去重当成新任务接收能力。
- 默认保留 worker。需要单测接收能力时可暂停 worker，测量后恢复并等待任务终态。
- 原始结果写入 backend/target/bench/（忽略目录）；终端只打印脱敏汇总。
"""
from __future__ import annotations

import argparse
from collections.abc import Callable, Sequence
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "apitest"))

from common.config import TestConfig, _read_dotenv  # noqa: E402
from common.database import DatabaseClient  # noqa: E402
from common.http_client import HttpClient  # noqa: E402


def percentile(values: list[float], ratio: float) -> float:
    """最近秩法；空集合返回 0，避免把未采样写成性能结果。"""
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(ratio * len(ordered)) - 1))
    return round(ordered[index], 2)


def login(http: HttpClient, username: str, password: str) -> dict:
    response = http.request("POST", "/api/auth/login",
                            json_body={"username": username, "password": password})
    if response.status != 200:
        raise RuntimeError(f"登录失败：{username} status={response.status}")
    payload = response.json()
    return {"token": payload["accessToken"], "user": payload["user"]}


def upload_material(http: HttpClient, token: str, run_id: str) -> int:
    """上传本轮唯一的合成讲义，立即返回 materialId 以便清理。"""
    import uuid

    boundary = "campusclaw-bench-" + uuid.uuid4().hex
    text = ("# 压测合成讲义\n\n直角三角形两直角边 a、b 与斜边 c 满足 a²+b²=c²。\n"
            f"运行标识：{run_id}。\n")
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="bench-{run_id}.md"\r\n'
        "Content-Type: text/markdown\r\n\r\n"
        f"{text}\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    response = http.request("POST", "/api/materials/upload", token=token, data=body,
                            content_type=f"multipart/form-data; boundary={boundary}")
    if response.status != 201:
        raise RuntimeError(f"合成讲义上传失败：status={response.status}")
    return int(response.json()["materialId"])


def wait_for_material(db: DatabaseClient, class_id: int, material_id: int, timeout: int = 90) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        row = db.query_one(
            """
            SELECT m.index_status, m.stored_path, COUNT(e.id) AS entries
            FROM materials m LEFT JOIN knowledge_entries e
              ON e.material_id = m.id AND e.class_id = m.class_id
            WHERE m.id = %s AND m.class_id = %s
            GROUP BY m.id, m.index_status, m.stored_path
            """,
            (material_id, class_id),
        )
        if row and row["index_status"] == "READY" and row["stored_path"] and int(row["entries"]) > 0:
            return
        time.sleep(1)
    raise RuntimeError("合成讲义未在超时内完成索引")


def delete_material(http: HttpClient, token: str, material_id: int) -> None:
    response = http.request("DELETE", f"/api/materials/{material_id}", token=token)
    if response.status not in (204, 404):
        raise RuntimeError(f"合成讲义清理失败：HTTP {response.status}")


def retrieval_seed(db: DatabaseClient, class_id: int, material_id: int | None = None) -> str:
    row = db.query_one(
        """
        SELECT chunk_text FROM knowledge_chunks
        WHERE class_id = %s AND index_status = 'READY' AND CHAR_LENGTH(TRIM(chunk_text)) >= 4
          AND (%s IS NULL OR material_id = %s)
        ORDER BY id LIMIT 1
        """,
        (class_id, material_id, material_id),
    )
    if row is None:
        raise RuntimeError("当前班级没有 READY 知识切片，无法压测检索")
    return str(row["chunk_text"]).strip()[:80]


def queue_depth(env: dict[str, str], queue: str) -> int | None:
    """读取 RabbitMQ 管理 API；不可达时返回 None，不把未知写成 0。"""
    import base64
    from urllib.error import URLError
    from urllib.request import Request, urlopen

    host = env.get("RABBITMQ_HOST_FOR_HOST", "127.0.0.1")
    port = env.get("RABBITMQ_MANAGEMENT_PORT", "15672")
    url = f"http://{host}:{port}/api/queues/%2F/{quote(queue, safe='')}"
    token = base64.b64encode(f"{env.get('RABBITMQ_USER', 'guest')}:"
                             f"{env.get('RABBITMQ_PASSWORD', 'guest')}".encode()).decode()
    request = Request(url, headers={"Authorization": f"Basic {token}"})
    try:
        with urlopen(request, timeout=5) as response:
            payload = json.loads(response.read())
    except (URLError, OSError, ValueError):
        return None
    depth = payload.get("messages") if isinstance(payload, dict) else None
    return depth if type(depth) is int and depth >= 0 else None


def compose(env_file: Path, *args: str) -> None:
    subprocess.run(["docker", "compose", "--env-file", str(env_file), *args],
                   cwd=ROOT, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)


def run_level(request: Callable[[int], int], inputs: Sequence[int],
              concurrency: int, expected: set[int]) -> dict:
    def one(value: int) -> tuple[int, int, int]:
        started = time.perf_counter_ns()
        status = request(value)
        return started, time.perf_counter_ns(), status

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        samples = list(pool.map(one, inputs))
    elapsed = time.perf_counter() - started
    latencies = [(end - start) / 1_000_000 for start, end, _ in samples]
    request_count = len(samples)

    statuses: dict[str, int] = {}
    errors = 0
    events = []
    for start, end, status in samples:
        statuses[str(status)] = statuses.get(str(status), 0) + 1
        errors += status not in expected
        events.extend(((start, 1), (end, -1)))
    active = peak = 0
    for _, delta in sorted(events):
        active += delta
        peak = max(peak, active)

    return {
        "requests": request_count,
        "concurrency": concurrency,
        "peakInFlight": peak,
        "elapsedSeconds": round(elapsed, 3),
        "qps": round(request_count / elapsed, 2) if elapsed else 0.0,
        "p50Ms": percentile(latencies, 0.50),
        "p95Ms": percentile(latencies, 0.95),
        "maxMs": round(max(latencies), 2) if latencies else 0.0,
        "statuses": statuses,
        "errorRate": round(errors / request_count, 4),
    }


def cleanup(db: DatabaseClient, class_id: int, assignment_ids: list[int],
            task_ids: list[str], title_prefix: str) -> dict:
    """只删除本轮标题/ID 可归属的合成数据；返回各表删除计数。"""
    counts: dict[str, int] = {}
    if not assignment_ids:
        return counts
    placeholders = ",".join(["%s"] * len(assignment_ids))
    matched = db.query_all(
        f"SELECT id FROM assignments WHERE class_id=%s AND id IN ({placeholders}) AND title LIKE %s",
        [class_id, *assignment_ids, title_prefix + "%"],
    )
    if len(matched) != len(assignment_ids):
        raise RuntimeError("清理前核对失败：存在非本轮作业，停止删除")
    if task_ids:
        task_marks = ",".join(["%s"] * len(task_ids))
        counts["outbox"] = db.cleanup_delete(
            f"DELETE FROM grading_outbox WHERE task_id IN ({task_marks})", task_ids)
        counts["results"] = db.cleanup_delete(
            f"DELETE FROM grading_results WHERE task_id IN ({task_marks})", task_ids)
        counts["reviews"] = db.cleanup_delete(
            f"DELETE FROM submission_reviews WHERE assignment_id IN ({placeholders})", assignment_ids)
        counts["tasks"] = db.cleanup_delete(
            f"DELETE FROM grading_tasks WHERE task_id IN ({task_marks})", task_ids)
    else:
        counts["reviews"] = db.cleanup_delete(
            f"DELETE FROM submission_reviews WHERE assignment_id IN ({placeholders})", assignment_ids)
    counts["submissions"] = db.cleanup_delete(
        f"DELETE FROM submissions WHERE class_id=%s AND assignment_id IN ({placeholders})",
        [class_id, *assignment_ids])
    # learning_assignment_tag_sources 对作业有 ON DELETE CASCADE，随作业删除。
    counts["assignments"] = db.cleanup_delete(
        f"DELETE FROM assignments WHERE class_id=%s AND id IN ({placeholders})",
        [class_id, *assignment_ids])
    return counts


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--levels", default="1,4,8,16", help="并发档位，逗号分隔")
    parser.add_argument("--requests", type=int, default=20, help="每个档位每接口请求数")
    parser.add_argument("--queue", default="grading.tasks")
    parser.add_argument("--stop-worker", action="store_true",
                        help="在隔离环境停止 worker，只测接收能力；默认保留 worker 并等待任务终态")
    parser.add_argument("--wait-timeout", type=int, default=300,
                        help="保留 worker 时等待本轮任务进入终态的秒数")
    args = parser.parse_args(argv)
    if args.requests < 1:
        print("requests 必须大于 0", file=sys.stderr)
        return 2
    try:
        levels = [int(item) for item in args.levels.split(",") if item.strip()]
    except ValueError:
        print("levels 必须是逗号分隔的整数", file=sys.stderr)
        return 2
    if not levels or any(level < 1 for level in levels):
        print("levels 必须包含正整数", file=sys.stderr)
        return 2
    if args.requests < max(levels):
        print("每档请求数不能小于最大并发设置", file=sys.stderr)
        return 2

    env_file = args.env_file.resolve()
    if not env_file.is_file():
        print("ENVIRONMENT: 缺少压测配置文件", file=sys.stderr)
        return 2
    env = _read_dotenv(env_file)
    os.environ["APITEST_ENV_FILE"] = str(env_file)
    try:
        config = TestConfig.load()
    except ValueError as exception:
        print(f"ENVIRONMENT: {exception}", file=sys.stderr)
        return 2
    http = HttpClient(config.base_url, config.http_timeout, config.stream_timeout)
    db = DatabaseClient(config)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    title_prefix = f"bench-{run_id}-"
    output = ROOT / "backend/target/bench"
    output.mkdir(parents=True, exist_ok=True)
    result: dict = {"runId": run_id, "baseUrl": config.base_url, "levels": levels,
                    "requestsPerLevel": args.requests, "endpoints": {}, "cleanup": {},
                    "environment": {"server": "running", "workerStopped": args.stop_worker}}

    initial_depth = queue_depth(env, args.queue)
    result["queue"] = {"name": args.queue, "initialDepth": initial_depth}
    if initial_depth != 0:
        print(f"队列 {args.queue} 初始深度非零或不可读，停止压测。",
              file=sys.stderr)
        return 2

    assignment_ids: list[int] = []
    task_ids: list[str] = []
    worker_stopped = False
    safe_to_clean = True
    material_id: int | None = None
    class_id: int | None = None
    teacher: dict | None = None
    try:
        teacher = login(http, config.teacher_username, config.teacher_password)
        student = login(http, config.student_username, config.student_password)
        class_id = int(student["user"]["classId"])
        if int(teacher["user"]["classId"]) != class_id:
            raise RuntimeError("教师与学生不在同一班级，无法进行提交压测")
        material_id = upload_material(http, teacher["token"], run_id)
        result["materialId"] = material_id
        wait_for_material(db, class_id, material_id)
        query = retrieval_seed(db, class_id, material_id)

        if args.stop_worker:
            compose(env_file, "stop", "grading-worker")
            worker_stopped = True

        total = sum(args.requests for _ in levels)
        for index in range(total):
            response = http.request(
                "POST", "/api/assignments", token=teacher["token"],
                json_body={"title": f"{title_prefix}{index:04d}",
                           "prompt": "合成压测作业：说明勾股定理的适用条件。",
                           "maxScore": 100, "rubricMaterialIds": [material_id]})
            if response.status != 201:
                raise RuntimeError(f"创建压测作业失败：status={response.status}")
            assignment_ids.append(int(response.json()["id"]))

        submit_rows: list[dict] = []
        query_rows: list[dict] = []
        retrieval_rows: list[dict] = []
        result["endpoints"] = {"submit": submit_rows, "taskQuery": query_rows, "retrieval": retrieval_rows}
        safe_to_clean = False
        cursor = 0
        for level in levels:
            level_tasks: list[str] = []
            level_assignments = assignment_ids[cursor:cursor + args.requests]
            cursor += args.requests
            def submit_one(assignment_id: int) -> int:
                response = http.request(
                    "POST", f"/api/assignments/{assignment_id}/submissions",
                    token=student["token"],
                    json_body={"content": f"合成作答 {run_id} 第 {assignment_id} 份。"})
                if response.status != 202:
                    return response.status
                task_id = response.json().get("taskId")
                if not task_id:
                    raise RuntimeError("提交响应缺少 taskId")
                level_tasks.append(task_id)
                return response.status

            submit_rows.append(run_level(submit_one, level_assignments, level, {202}))
            task_ids.extend(level_tasks)
            if submit_rows[-1]["statuses"] != {"202": args.requests}:
                raise RuntimeError("提交返回未预期状态")

            # 查询与检索使用同一档位、同一请求数；任务 ID 来自本轮真实提交。
            query_rows.append(run_level(
                lambda _: http.request("GET", f"/api/grading/tasks/{level_tasks[0]}",
                                       token=student["token"]).status,
                range(args.requests), level, {200}))
            retrieval_rows.append(run_level(
                lambda _: http.request("POST", "/api/retrieval/search", token=student["token"],
                                       json_body={"query": query, "mode": "hybrid", "limit": 5}).status,
                range(args.requests), level, {200}))

        if worker_stopped:
            compose(env_file, "start", "grading-worker")
            worker_stopped = False
        if task_ids:
            started = time.perf_counter()
            deadline = started + args.wait_timeout
            pending = set(task_ids)
            while pending and time.perf_counter() < deadline:
                for task_id in list(pending):
                    response = http.request("GET", f"/api/grading/tasks/{task_id}",
                                            token=student["token"])
                    if response.status == 200 and response.json().get("status") in {"SUCCEEDED", "FAILED"}:
                        pending.discard(task_id)
                if pending:
                    time.sleep(1)
            terminal = {"SUCCEEDED": 0, "FAILED": 0}
            for task_id in task_ids:
                response = http.request("GET", f"/api/grading/tasks/{task_id}",
                                        token=student["token"])
                if response.status == 200:
                    status = response.json().get("status")
                    if status in terminal:
                        terminal[status] += 1
            result["terminal"] = {"seconds": round(time.perf_counter() - started, 2),
                                  **terminal, "pending": len(pending)}
            if pending:
                raise RuntimeError(f"仍有 {len(pending)} 个任务未在 {args.wait_timeout}s 内进入终态")
        safe_to_clean = True
        result["passed"] = all(
            row["statuses"] == {"202" if name == "submit" else "200": row["requests"]}
            for name, rows in result["endpoints"].items() for row in rows
        )
    except Exception as exception:  # noqa: BLE001 - 原始错误脱敏后写入结果
        result["passed"] = False
        result["error"] = type(exception).__name__
        print(f"bench failed: {type(exception).__name__}", file=sys.stderr)
    finally:
        if worker_stopped:
            try:
                compose(env_file, "start", "grading-worker")
            except Exception as exception:  # noqa: BLE001 - 单独记录恢复失败，保留清理结果
                result["workerRestoreError"] = type(exception).__name__
                result["passed"] = False
                print("bench worker restore failed", file=sys.stderr)
        result["assignmentIds"] = assignment_ids
        if not safe_to_clean:
            result["cleanupDeferred"] = True
            print("本轮提交的终态未确认，保留资源标识供人工清理", file=sys.stderr)
        try:
            if safe_to_clean and assignment_ids and class_id is not None:
                result["cleanup"] = cleanup(db, class_id, assignment_ids, task_ids, title_prefix)
            if safe_to_clean and material_id is not None and teacher is not None:
                delete_material(http, teacher["token"], material_id)
                result["materialCleaned"] = True
        except Exception as exception:  # noqa: BLE001 - 清理失败必须保留在工作区供人工处理
            result["cleanupError"] = type(exception).__name__
            result["passed"] = False
            print(f"bench cleanup failed: {type(exception).__name__}", file=sys.stderr)
        (output / f"bench-{run_id}.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    if not result.get("passed"):
        print(f"bench summary: {output / ('bench-' + run_id + '.json')}")
        return 1
    for name in ("submit", "taskQuery", "retrieval"):
        for row in result["endpoints"][name]:
            print(f"{name} c={row['concurrency']} peak={row['peakInFlight']} qps={row['qps']} "
                  f"p50={row['p50Ms']}ms p95={row['p95Ms']}ms status={row['statuses']}")
    print(f"bench summary: {output / ('bench-' + run_id + '.json')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
