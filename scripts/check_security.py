"""SEC-05/06：本地密钥扫描与现有依赖/静态检查，不输出命中正文。"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "backend/target/security-check"
IMAGE = "ghcr.io/gitleaks/gitleaks:v8.24.3"


class Incomplete(Exception):
    pass


def command(args: list[str], *, log: Path | None = None) -> subprocess.CompletedProcess:
    if log:
        with log.open("wb") as stream:
            return subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=False)
    return subprocess.run(args, cwd=ROOT, capture_output=True, check=False)


def git(*args: str) -> str:
    result = command(["git", "-c", f"safe.directory={ROOT.as_posix()}", *args])
    if result.returncode:
        raise Incomplete("Git metadata unavailable")
    return result.stdout.decode("utf-8").strip()


def sanitize(rows: list[dict]) -> list[dict]:
    return [{key: row.get(key) for key in ("RuleID", "File", "StartLine", "Commit", "Fingerprint")}
            for row in rows]


def configured_findings(folder: Path, values: dict[str, str]) -> list[dict]:
    secrets = {key: value.encode() for key, value in values.items() if len(value) >= 8
               and any(marker in key for marker in ("PASSWORD", "SECRET", "TOKEN", "API_KEY"))}
    findings = []
    for path in folder.rglob("*"):
        if path.is_file():
            data = path.read_bytes()
            for key, value in secrets.items():
                if value in data:
                    findings.append({"RuleID": f"configured-{key}", "File": path.relative_to(folder).as_posix()})
    return findings


def require_scope(dist: Path) -> None:
    if git("rev-parse", "--is-shallow-repository") != "false":
        raise Incomplete("shallow history: obtain the full local history first")
    if not dist.is_dir() or not any(path.is_file() for path in dist.rglob("*")):
        raise Incomplete("frontend/dist missing or empty: build the frontend first")


def scanner(label: str, source: Path, *, history: bool = False) -> dict:
    report = OUTPUT / f"{label}-redacted.json"
    report.unlink(missing_ok=True)
    args = ["docker", "run", "--rm", "--network", "none",
            "-e", "GIT_CONFIG_COUNT=1", "-e", "GIT_CONFIG_KEY_0=safe.directory",
            "-e", "GIT_CONFIG_VALUE_0=/scan", "-v", f"{source.resolve()}:/scan:ro",
            "-v", f"{OUTPUT.resolve()}:/out"]
    if history:
        args += ["-v", f"{(ROOT / '.git').resolve()}:/scan/.git:ro"]
    args += [IMAGE, "git" if history else "dir",
            "/scan", "--redact=100", "--no-banner", "--report-format=json",
            f"--report-path=/out/{report.name}"]
    if history:
        args.append("--log-opts=--all")
    result = command(args, log=OUTPUT / f"{label}.log")
    if result.returncode not in (0, 1) or not report.is_file():
        raise Incomplete(f"{label}: scanner did not complete")
    rows = json.loads(report.read_text(encoding="utf-8"))
    if not isinstance(rows, list) or (result.returncode == 1 and not rows):
        raise Incomplete(f"{label}: invalid scanner report")
    return {"exitCode": result.returncode, "findings": sanitize(rows)}


def secrets(env_file: Path) -> dict:
    require_scope(ROOT / "frontend/dist")
    if not env_file.is_file():
        raise Incomplete("private environment file missing")
    version = command(["docker", "run", "--rm", "--network", "none", IMAGE, "version"])
    if version.returncode:
        raise Incomplete(f"scanner unavailable; pull {IMAGE} first")
    # 只复制Git公开文件；私有配置、数据库和其他项目均不进入扫描容器。
    source = OUTPUT / "input"
    if not OUTPUT.resolve().is_relative_to(ROOT.resolve()):
        raise Incomplete("scan output resolves outside repository")
    if source.exists():
        resolved = source.resolve()
        if not resolved.is_relative_to(OUTPUT.resolve()):
            raise Incomplete("invalid scan workspace")
        shutil.rmtree(resolved)
    public, artifacts, logs = source / "public", source / "dist", source / "logs"
    public.mkdir(parents=True)
    (public / ".git").mkdir()
    names = git("ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0")
    copied = 0
    for name in filter(None, names):
        path = ROOT / name
        if not path.resolve().is_relative_to(ROOT.resolve()):
            raise Incomplete("public file resolves outside repository")
        if path.is_file():
            target = public / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
            copied += 1
    shutil.copytree(ROOT / "frontend/dist", artifacts)
    logs.mkdir()
    compose = ["docker", "compose", "--env-file", str(env_file)]
    config = command(compose + ["config", "--format", "json"])
    if config.returncode:
        raise Incomplete("Compose configuration unavailable")
    parsed = json.loads(config.stdout)
    if parsed.get("name") != "campusclaw-plus":
        raise Incomplete("expected isolated campusclaw-plus project")
    services = sorted(parsed["services"])
    for service in services:
        identity = command(compose + ["ps", "-q", service])
        if identity.returncode or not identity.stdout.strip():
            raise Incomplete(f"{service}: no running container/log scope")
        result = command(compose + ["logs", "--no-color", "--no-log-prefix", service], log=logs / f"{service}.log")
        if result.returncode:
            raise Incomplete(f"{service}: logs unavailable")
    # 环境值只用于精确比对，不进入报告和容器参数。
    sys.path.insert(0, str(ROOT))
    from apitest.common.config import _read_dotenv
    known = configured_findings(source, _read_dotenv(env_file))
    scopes = {"history": scanner("history", public, history=True),
              "public": scanner("public", public), "frontend": scanner("frontend", artifacts),
              "logs": scanner("logs", logs)}
    commits = int(git("rev-list", "--all", "--count"))
    return {"image": IMAGE, "version": version.stdout.decode().strip(), "head": git("rev-parse", "HEAD"),
            "commits": commits, "publicFiles": copied, "logServices": services,
            "scopes": scopes, "configuredFindings": known,
            "exitCode": int(bool(known) or any(item["findings"] for item in scopes.values()))}


def dependencies() -> dict:
    started = time.time_ns()
    result = command([sys.executable, str(ROOT / "scripts/scan.py")], log=OUTPUT / "dependencies.log")
    logs = ROOT / "backend/target/scan-logs"
    required = ("frontend-lint.log", "frontend-audit.log", "python-ruff.log", "python-pip-audit.log",
                "java-checkstyle.log", "java-spotbugs.log", "java-dependencies.log", "java-osv.json")
    complete = all((logs / name).is_file() and (logs / name).stat().st_mtime_ns >= started for name in required)
    return {"exitCode": result.returncode if complete else 2, "complete": complete,
            "log": "backend/target/security-check/dependencies.log"}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--only", choices=("all", "secrets", "dependencies"), default="all")
    args = parser.parse_args(argv)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = {"timeUtc": datetime.now(timezone.utc).isoformat(), "stages": {}}
    for name, execute in (("secrets", lambda: secrets(args.env_file.resolve())), ("dependencies", dependencies)):
        if args.only not in ("all", name):
            continue
        try:
            stage = execute()
        except (Incomplete, OSError, ValueError):
            stage = {"exitCode": 2, "error": "incomplete; check local tools, scope and private logs"}
        report["stages"][name] = stage
        print(f"{name}: exit={stage['exitCode']}", flush=True)
    name = "summary.json" if args.only == "all" else f"summary-{args.only}.json"
    (OUTPUT / name).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    codes = [row["exitCode"] for row in report["stages"].values()]
    return 2 if any(code not in (0, 1) for code in codes) else int(any(codes))


if __name__ == "__main__":
    raise SystemExit(main())
