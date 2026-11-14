"""Class09 安全门禁：聚合扫描、授权、审计、接口反例和脚本测试的真实退出码。

用法：
  python scripts/security_gate.py --env-file .env.team
  python scripts/security_gate.py --env-file .env.team --fault-stage audit

退出码：0 全部通过；1 任一检查失败；2 任一阶段未完成/环境缺失。
--fault-stage 只用于受控红绿证据：它只能把已通过的阶段改为合成失败，不能把失败改为通过。
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "backend/target/security-gate"
STAGES = ("scan", "access", "audit", "api", "unit")


def aggregate(codes: list[int]) -> int:
    """2 优先于 1；只有全部为 0 才通过。"""
    if any(code not in (0, 1) for code in codes):
        return 2
    return int(any(codes))


def stage_commands(env_file: Path) -> list[tuple[str, list[str], dict[str, str]]]:
    python = sys.executable
    api_env = os.environ.copy()
    api_env["APITEST_ENV_FILE"] = str(env_file)
    return [
        ("scan", [python, "-B", "scripts/check_security.py", "--env-file", str(env_file)], {}),
        ("access", [python, "-B", "scripts/security_precheck.py", "--env-file", str(env_file),
                    "--checks", "access"], {}),
        ("audit", [python, "-B", "scripts/security_precheck.py", "--env-file", str(env_file),
                   "--checks", "audit"], {}),
        ("api", [python, "-B", "scripts/check.py", "--mode", "api"], api_env),
        ("unit", [python, "-B", "-m", "unittest", "discover", "-s", "scripts/tests"], {}),
    ]


def run(env_file: Path, fault_stage: str | None, output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    summary = {
        "timeUtc": datetime.now(timezone.utc).isoformat(),
        "envFile": env_file.name,
        "faultStage": fault_stage,
        "syntheticFault": False,
        "stages": {},
    }
    codes: list[int] = []
    for name, command, extra_env in stage_commands(env_file):
        environment = os.environ.copy()
        environment.update(extra_env)
        log = output / f"{name}.log"
        with log.open("wb") as stream:
            try:
                result = subprocess.run(command, cwd=ROOT, env=environment,
                                        stdout=stream, stderr=subprocess.STDOUT, check=False)
                code = result.returncode
            except OSError:
                code = 2
        if fault_stage == name and code == 0:
            code = 1
            summary["syntheticFault"] = True
        codes.append(code)
        summary["stages"][name] = {"exitCode": code, "log": str(log.relative_to(ROOT))}
        print(f"{name}: exit={code}", flush=True)
    summary["exitCode"] = aggregate(codes)
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--fault-stage", choices=STAGES,
                        help="受控红绿证据：仅把已通过的指定阶段改为合成失败")
    args = parser.parse_args(argv)
    env_file = args.env_file.resolve()
    if not env_file.is_file():
        print("security gate: private environment file missing", file=sys.stderr)
        return 2
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    folder = OUTPUT / f"{stamp}-{os.getpid()}"
    summary = run(env_file, args.fault_stage, folder)
    print(f"security gate: exit={summary['exitCode']} summary={folder / 'summary.json'}")
    return int(summary["exitCode"])


if __name__ == "__main__":
    raise SystemExit(main())
