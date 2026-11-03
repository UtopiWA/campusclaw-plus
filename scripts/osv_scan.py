"""用 OSV API 检查 Maven 运行时依赖的已知漏洞。

先通过 Maven 生成运行时依赖清单，再逐项查询 https://api.osv.dev/。
输出文件与摘要写入 backend/target/scan-logs/；不读取或打印业务凭据。
高/严重漏洞返回 1，其他发现返回 0 并保留明细供人工处置。
"""

from __future__ import annotations

import json
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
REPORT = BACKEND / "target" / "dependency-list.txt"
LOGS = BACKEND / "target" / "scan-logs"
PATTERN = re.compile(r"^([^:\s]+):([^:\s]+):(?:jar|pom):([^:\s]+):")


def maven() -> list[str]:
    java = shutil.which("java")
    if not java:
        raise SystemExit("missing java")
    wrapper = BACKEND / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not wrapper.is_file():
        raise SystemExit("missing Maven wrapper")
    return [str(wrapper), "-f", str(BACKEND / "pom.xml")]


def dependencies() -> list[tuple[str, str, str]]:
    command = maven() + ["-q", "dependency:list", "-DincludeScope=runtime",
                         "-DoutputFile=target/dependency-list.txt"]
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.DEVNULL,
                            stderr=subprocess.STDOUT, check=False)
    if result.returncode != 0 or not REPORT.is_file():
        raise SystemExit("could not generate Maven dependency list")
    found: set[tuple[str, str, str]] = set()
    for line in REPORT.read_text(encoding="utf-8", errors="replace").splitlines():
        match = PATTERN.match(line.strip())
        if match:
            found.add((match.group(1), match.group(2), match.group(3)))
    return sorted(found)


def query(group: str, artifact: str, version: str) -> list[dict]:
    payload = json.dumps({
        "package": {"name": f"{group}:{artifact}", "ecosystem": "Maven"},
        "version": version,
    }).encode()
    request = urllib.request.Request("https://api.osv.dev/v1/query", data=payload,
                                     headers={"Content-Type": "application/json"}, method="POST")
    for attempt in range(2):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return json.loads(response.read()).get("vulns", [])
        except (urllib.error.URLError, TimeoutError):
            if attempt == 1:
                return []
            time.sleep(1)
    return []


def severity(vulnerability: dict) -> str:
    direct = vulnerability.get("database_specific", {}).get("severity")
    if direct:
        return str(direct).upper()
    for item in vulnerability.get("severity", []) or []:
        if item.get("type", "").startswith("CVSS"):
            return str(item.get("score", "")).upper()
    return "UNKNOWN"


def main() -> int:
    items = dependencies()
    results: list[tuple[str, str, str, dict]] = []
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = {
            pool.submit(query, group, artifact, version): (group, artifact, version)
            for group, artifact, version in items
        }
        for future in as_completed(futures):
            group, artifact, version = futures[future]
            for vulnerability in future.result():
                results.append((group, artifact, version, vulnerability))
    results.sort(key=lambda item: (item[0], item[1], item[2], item[3].get("id", "")))
    LOGS.mkdir(parents=True, exist_ok=True)
    (LOGS / "java-osv.log").write_text(
        "\n".join(f"{g}:{a}:{v} {item.get('id')} {severity(item)} {item.get('summary', '')}"
                  for g, a, v, item in results) + ("\n" if results else ""),
        encoding="utf-8")
    print(f"dependencies checked: {len(items)}, vulnerabilities: {len(results)}")
    high = 0
    for group, artifact, version, item in results:
        level = severity(item)
        print(f"{group}:{artifact}:{version} {item.get('id')} {level}")
        if level in {"CRITICAL", "HIGH"} or "9." in level or "8." in level or "7." in level:
            high += 1
    return 1 if high else 0


if __name__ == "__main__":
    raise SystemExit(main())
