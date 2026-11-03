"""Class08 固定批量扫描入口。

执行代码规范、依赖漏洞和 Java 静态检查；数据库查询语义/索引风险仍需人工
`EXPLAIN` 并记录到 docs/lesson-08/review.md。原始输出写入忽略目录
backend/target/scan-logs/，不打印凭据或业务正文。

用法：
  python scripts/scan.py
  python scripts/scan.py --owasp   # 额外执行 OWASP NVD 检查，耗时较长
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
LOGS = ROOT / "backend" / "target" / "scan-logs"


class EnvironmentFailure(Exception):
    pass


def tool(name: str) -> str:
    executable = shutil.which(name + ".cmd" if os.name == "nt" and name in {"npm"} else name)
    if not executable:
        raise EnvironmentFailure(f"missing tool: {name}")
    return executable


def maven() -> list[str]:
    tool("java")
    wrapper = ROOT / "backend" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not wrapper.is_file():
        raise EnvironmentFailure("missing Maven wrapper")
    return [str(wrapper), "-f", str(ROOT / "backend/pom.xml")]


def run(label: str, command: list[str]) -> int:
    LOGS.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    with (LOGS / f"{label}.log").open("w", encoding="utf-8") as output:
        result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=False)
    print(f"{label}: {'PASS' if result.returncode == 0 else 'FAIL'} ({time.monotonic() - started:.1f}s)")
    return result.returncode


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owasp", action="store_true", help="also run the slow OWASP NVD check")
    args = parser.parse_args(argv)
    try:
        npm = tool("npm")
        mvn = maven()
        commands = [
            ("frontend-lint", [npm, "--prefix", "frontend", "run", "lint"]),
            ("frontend-audit", [npm, "--prefix", "frontend", "audit", "--audit-level=high",
                                 "--registry=https://registry.npmjs.org"]),
            ("python-ruff", [sys.executable, "-m", "ruff", "check", "mcp-server", "apitest", "scripts"]),
            ("python-pip-audit", [sys.executable, "-m", "pip_audit", "-r", "apitest/requirements.txt"]),
            ("java-checkstyle", mvn + ["checkstyle:check"]),
            ("java-spotbugs", mvn + ["-DskipTests", "compile", "spotbugs:check"]),
            ("java-dependencies", [sys.executable, "scripts/osv_scan.py"]),
        ]
        if args.owasp:
            commands.append(("java-owasp",
                             mvn + ["org.owasp:dependency-check-maven:check", "-DfailBuildOnCVSS=9"]))
        for label, command in commands:
            if run(label, command) != 0:
                return 1
        print("database query risk: run EXPLAIN for changed queries and record index/N+1 review in "
              "docs/lesson-08/review.md")
        return 0
    except EnvironmentFailure as exception:
        print(f"ENVIRONMENT: {exception}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
