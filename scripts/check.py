"""团队检查入口；不安装工具、不启动应用、不修改运行中数据库配置。"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONTAINERS = ("CampusClawIntegrationTest", "QdrantIntegrationTest", "McpSchemaMigrationTest")


class EnvironmentFailure(Exception):
    pass


def tool(name: str) -> str:
    executable = shutil.which(name + ".cmd" if os.name == "nt" and name in {"npm", "openspec"} else name)
    if not executable:
        raise EnvironmentFailure(f"missing tool: {name}")
    return executable


def maven() -> list[str]:
    tool("java")
    wrapper = ROOT / "backend" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not wrapper.is_file():
        raise EnvironmentFailure("missing Maven wrapper")
    version = os.environ.get("TESTCONTAINERS_API_VERSION")
    if version is not None and not re.fullmatch(r"1\.\d{1,2}", version, flags=re.ASCII):
        raise EnvironmentFailure("invalid TESTCONTAINERS_API_VERSION")
    command = [str(wrapper), "-f", str(ROOT / "backend/pom.xml")]
    if version is not None:
        command.append(f"-Dapi.version={version}")
    return command


def run(label: str, command: list[str]) -> int:
    # 原始输出留在忽略目录，不把潜在凭据或模型响应复制到终端。
    destination = ROOT / "backend/target/check-logs"
    destination.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    try:
        with (destination / f"{label}.log").open("w", encoding="utf-8") as output:
            result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=False)
    except OSError:
        raise EnvironmentFailure(f"cannot execute: {label}") from None
    print(f"{label}: {'PASS' if result.returncode == 0 else 'FAIL'} ({time.monotonic() - started:.1f}s)", flush=True)
    return result.returncode


def verify_reports(started_ns: int, expected: tuple[str, ...] | None = None) -> bool:
    reports = ROOT / "backend/target/surefire-reports"
    files = list(reports.glob("TEST-*.xml"))
    fresh = [path for path in files if path.stat().st_mtime_ns >= started_ns]
    if not fresh:
        return False
    seen = set()
    total = 0
    try:
        for path in fresh:
            suite = ET.parse(path).getroot()
            name = suite.attrib.get("name", "").rsplit(".", 1)[-1]
            if expected is not None and name not in expected:
                continue
            seen.add(name)
            count = int(suite.attrib["tests"])
            total += count
            if count < 1 or any(int(suite.attrib.get(key, "0")) for key in ("failures", "errors", "skipped")):
                return False
    except (OSError, ET.ParseError, KeyError, ValueError):
        return False
    if expected is not None and seen != set(expected):
        return False
    print(f"backend reports: {total} tests, 0 failures/errors/skipped")
    return total > 0


def unit() -> int:
    npm, openspec, mvn = tool("npm"), tool("openspec"), maven()
    if not (ROOT / "frontend/node_modules").is_dir():
        raise EnvironmentFailure("frontend dependencies missing: run npm ci in frontend")
    commands = [
        ("spec", [openspec, "validate", "--all", "--strict"]),
        ("frontend-test", [npm, "--prefix", "frontend", "test", "--", "--run"]),
        ("frontend-build", [npm, "--prefix", "frontend", "run", "build"]),
        ("backend-unit", mvn + ["-Dtest=*," + ",".join("!" + name for name in CONTAINERS), "test"]),
        ("mcp-unit", [sys.executable, "-m", "unittest", "discover", "-s", "mcp-server/tests"]),
        ("runner-unit", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests"]),
    ]
    for label, command in commands:
        started = time.time_ns()
        if run(label, command) != 0:
            return 1
        if label == "backend-unit" and not verify_reports(started):
            return 1
    return 0


def integration() -> int:
    mvn, docker = maven(), tool("docker")
    if run("docker-preflight", [docker, "info", "--format", "{{.ServerVersion}}"]):
        raise EnvironmentFailure("Docker Engine unavailable")
    started = time.time_ns()
    if run("backend-integration", mvn + ["-Dtest=" + ",".join(CONTAINERS), "test"]):
        return 1
    return 0 if verify_reports(started, CONTAINERS) else 1


def api() -> int:
    # 沿用接口套件自己的配置/HTTP/MySQL 预检和清理，不替它创建账号或种子。
    if run("api-preflight", [sys.executable, str(ROOT / "scripts/api_preflight.py")]):
        raise EnvironmentFailure("API environment unavailable; see api-preflight.log")
    code = run("api", [sys.executable, str(ROOT / "apitest/run_all.py")])
    return code if code in (0, 2) else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("unit", "integration", "api", "all"), default="unit")
    args = parser.parse_args(argv)
    selected = ("unit", "integration", "api") if args.mode == "all" else (args.mode,)
    pending = list(selected)
    try:
        # 在执行任何检查前校验覆盖值，即使所选模式不会启动 Maven。
        version = os.environ.get("TESTCONTAINERS_API_VERSION")
        if version is not None and not re.fullmatch(r"1\.\d{1,2}", version, flags=re.ASCII):
            raise EnvironmentFailure("invalid TESTCONTAINERS_API_VERSION")
        for index, mode in enumerate(selected):
            pending = list(selected[index + 1:])
            result = {"unit": unit, "integration": integration, "api": api}[mode]()
            if result:
                for remaining in selected[index + 1:]:
                    print(f"{remaining}: NOT_RUN")
                return result
    except EnvironmentFailure as exception:
        print(f"ENVIRONMENT: {exception}")
        if args.mode == "all":
            for remaining in pending:
                print(f"{remaining}: NOT_RUN")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
