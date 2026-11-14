"""Class09 第 7 节四步演示：202/任务状态/跨班读取与检索隔离。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import lesson08_acceptance as acceptance  # noqa: E402
import security_precheck as precheck  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    args = parser.parse_args(argv)
    run = precheck.Precheck(args.env_file.resolve())
    result = {"runId": run.run_id, "steps": {}}
    try:
        run.setup()
        # 1. 登录已由 Precheck 完成；记录账号角色，不输出令牌。
        student = run.api("GET", "/api/auth/me", run.student)
        result["steps"]["login"] = {"role": student.get("role"), "classId": student.get("classId")}

        # 2. 停 worker 后提交，观察 202 且响应不含分数。
        run.compose_run("stop", "grading-worker")
        run.worker_stopped = True
        try:
            status, accepted, submitted_ms = acceptance.submit(
                run.student, run.assignment, "直角三角形中 a²+b²=c²，c 为斜边。")
            result["steps"]["submit"] = {
                "status": status,
                "hasTaskId": bool(accepted.get("taskId")),
                "containsScore": any(key in accepted for key in ("score", "maxScore", "result")),
                "submittedMs": round(submitted_ms, 1),
            }
            pending = acceptance.get_task(run.student, accepted["taskId"])
            result["steps"]["pending"] = {"status": pending.get("status")}
        finally:
            run.compose_run("start", "grading-worker")
            run.worker_stopped = False

        # 3. 恢复 worker 后等待终态；过快时保留 202/任务查询和执行记录作为证据。
        completed = acceptance.wait_task(run.student, accepted["taskId"], timeout=240)
        result["steps"]["completed"] = {
            "status": completed.get("status"),
            "durationMs": acceptance.task_duration_ms(completed),
        }

        # 4. B 班读取 A 班材料 404；直接检索隔离为 200+空集合。
        run.api("GET", f"/api/materials/{run.material}", run.other, expected=404)
        cross = run.api("POST", "/api/retrieval/search", run.other,
                        {"query": run.mark, "mode": "keyword", "limit": 10})
        own = run.api("POST", "/api/retrieval/search", run.student,
                      {"query": run.mark, "mode": "keyword", "limit": 10})
        result["steps"]["crossClass"] = {
            "materialRead": 404,
            "crossHits": len(cross.get("hits", [])),
            "ownHits": len(own.get("hits", [])),
        }
        passed = (
            result["steps"]["submit"]["status"] == 202
            and result["steps"]["submit"]["hasTaskId"]
            and not result["steps"]["submit"]["containsScore"]
            and result["steps"]["pending"]["status"] == "PENDING"
            and result["steps"]["completed"]["status"] == "SUCCEEDED"
            and result["steps"]["crossClass"]["materialRead"] == 404
            and result["steps"]["crossClass"]["crossHits"] == 0
            and result["steps"]["crossClass"]["ownHits"] > 0
        )
        result["passed"] = passed
    finally:
        try:
            run.cleanup()
        finally:
            (ROOT / "backend/target" / f"four-step-demo-{run.run_id}.json").write_text(
                json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result.get("passed") else 1


if __name__ == "__main__":
    raise SystemExit(main())
