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

import security_exceptions

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
    code, _ = run_capture(label, command)
    return code


def run_capture(label: str, command: list[str]) -> tuple[int, str]:
    """执行并保留原始输出；返回码和文本供例外评估使用。"""
    LOGS.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", check=False)
    text = (result.stdout or "") + (result.stderr or "")
    (LOGS / f"{label}.log").write_text(text, encoding="utf-8")
    print(f"{label}: {'PASS' if result.returncode == 0 else 'FAIL'} ({time.monotonic() - started:.1f}s)")
    return result.returncode, text


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owasp", action="store_true", help="also run the slow OWASP NVD check")
    parser.add_argument("--exceptions", type=Path,
                        help="显式批准的开发依赖例外文件；不传则 npm audit 失败即退出 1")
    args = parser.parse_args(argv)
    try:
        npm = tool("npm")
        mvn = maven()
        exception_doc = None
        if args.exceptions is not None:
            if not args.exceptions.is_file():
                raise EnvironmentFailure("exception file missing")
            try:
                exception_doc = security_exceptions.load(args.exceptions.resolve())
            except ValueError as exception:
                raise EnvironmentFailure(f"invalid exception file: {exception}") from None
        commands = [
            ("frontend-lint", [npm, "--prefix", "frontend", "run", "lint"]),
            ("python-ruff", [sys.executable, "-m", "ruff", "check", "mcp-server", "apitest", "scripts"]),
            ("python-pip-audit", [sys.executable, "-m", "pip_audit", "-r", "apitest/requirements.txt"]),
            ("java-checkstyle", mvn + ["checkstyle:check"]),
            ("java-spotbugs", mvn + ["-DskipTests", "compile", "spotbugs:check"]),
            ("java-dependencies", [sys.executable, "scripts/osv_scan.py"]),
        ]
        if args.owasp:
            commands.append(("java-owasp",
                             mvn + ["org.owasp:dependency-check-maven:check", "-DfailBuildOnCVSS=9"]))

        code, audit_text = run_capture(
            "frontend-audit",
            [npm, "--prefix", "frontend", "audit", "--json", "--audit-level=high",
             "--registry=https://registry.npmjs.org"])
        (LOGS / "frontend-audit.json").write_text(audit_text, encoding="utf-8")
        if code != 0:
            if exception_doc is None:
                print("frontend-audit: FAIL; no approved exception file provided")
                return 1
            accepted, problems, applied = security_exceptions.evaluate(audit_text, exception_doc)
            if not accepted:
                print("frontend-audit: FAIL; exception does not cover: " + "; ".join(problems))
                return 1
            print("frontend-audit: ACCEPTED_EXCEPTION " + ",".join(applied)
                  + "; raw high findings retained in backend/target/scan-logs/frontend-audit.json")

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
