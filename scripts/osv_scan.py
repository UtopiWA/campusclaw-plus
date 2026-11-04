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


class ScanIncomplete(Exception):
    pass


def maven() -> list[str]:
    java = shutil.which("java")
    if not java:
        raise SystemExit("missing java")
    wrapper = BACKEND / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not wrapper.is_file():
        raise SystemExit("missing Maven wrapper")
    return [str(wrapper), "-f", str(BACKEND / "pom.xml")]


def dependencies() -> list[tuple[str, str, str]]:
    REPORT.unlink(missing_ok=True)
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
    if not found:
        raise ScanIncomplete("empty dependency list")
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
                data = json.loads(response.read())
                vulnerabilities = data.get("vulns", [])
                if not isinstance(vulnerabilities, list):
                    raise ValueError("invalid vulnerability response")
                return vulnerabilities
        except (urllib.error.URLError, TimeoutError, ValueError, OSError):
            if attempt == 1:
                raise ScanIncomplete(f"query failed: {group}:{artifact}:{version}") from None
            time.sleep(1)
    raise ScanIncomplete("query did not complete")


def severity(vulnerability: dict) -> str:
    direct = vulnerability.get("database_specific", {}).get("severity")
    if direct:
        return str(direct).upper()
    for item in vulnerability.get("severity", []) or []:
        if item.get("type", "").startswith("CVSS"):
            score = str(item.get("score", ""))
            try:
                numeric = float(score)
            except ValueError:
                continue
            return "CRITICAL" if numeric >= 9 else "HIGH" if numeric >= 7 else "MEDIUM" if numeric >= 4 else "LOW"
    return "UNKNOWN"


def main() -> int:
    try:
        items = dependencies()
    except ScanIncomplete as exception:
        print(f"INCOMPLETE: {exception}")
        return 2
    results: list[tuple[str, str, str, dict]] = []
    failures = []
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = {
            pool.submit(query, group, artifact, version): (group, artifact, version)
            for group, artifact, version in items
        }
        for future in as_completed(futures):
            group, artifact, version = futures[future]
            try:
                vulnerabilities = future.result()
            except ScanIncomplete:
                failures.append(f"{group}:{artifact}:{version}")
                continue
            for vulnerability in vulnerabilities:
                results.append((group, artifact, version, vulnerability))
    results.sort(key=lambda item: (item[0], item[1], item[2], item[3].get("id", "")))
    LOGS.mkdir(parents=True, exist_ok=True)
    (LOGS / "java-osv.json").write_text(json.dumps({
        "dependencies": len(items), "queryFailures": sorted(failures),
        "findings": [{"package": f"{g}:{a}:{v}", "advisory": item} for g, a, v, item in results],
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    (LOGS / "java-osv.log").write_text(
        "\n".join(f"{g}:{a}:{v} {item.get('id')} {severity(item)} {item.get('summary', '')}"
                  for g, a, v, item in results) + ("\n" if results else ""),
        encoding="utf-8")
    print(f"dependencies checked: {len(items) - len(failures)}/{len(items)}, vulnerabilities: {len(results)}")
    high = 0
    unknown = 0
    for group, artifact, version, item in results:
        level = severity(item)
        print(f"{group}:{artifact}:{version} {item.get('id')} {level}")
        if level in {"CRITICAL", "HIGH"}:
            high += 1
        elif level not in {"LOW", "MEDIUM", "MODERATE"}:
            unknown += 1
    if failures or unknown:
        print(f"INCOMPLETE: query failures={len(failures)}, unknown severities={unknown}")
        return 2
    return 1 if high else 0


if __name__ == "__main__":
    raise SystemExit(main())
