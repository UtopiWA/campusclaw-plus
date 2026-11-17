"""为观测面板生成三条可归属的教学技能调用（解题引导/作业批改/错题讲解）。

脚本只创建本轮唯一命名的技能和一个学生私人会话，结束后恢复智能体配置并删除
会话与技能；不写入真实用户成绩或材料。运行结果 JSON 保存在忽略目录。
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "apitest"))

from common.config import TestConfig, _read_dotenv  # noqa: E402
from common.http_client import HttpClient  # noqa: E402

import observe  # noqa: E402 - 同目录脚本，复用同一指标口径


def login(http: HttpClient, username: str, password: str) -> dict:
    response = http.request("POST", "/api/auth/login",
                            json_body={"username": username, "password": password})
    if response.status != 200:
        raise RuntimeError(f"登录失败：{username}")
    payload = response.json()
    return {"token": payload["accessToken"], "user": payload["user"]}


def skill_source(name: str, trigger: str) -> str:
    return (f"名称：{name}\n"
            f"适用条件：{trigger}\n"
            "资料：\n"
            "工具：knowledge_search\n"
            "启用：是\n\n"
            "这是观测面板专用技能：只说明下一步学习方向，不输出最终答案或评分。")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--observe-window", default="5m")
    parser.add_argument("--observe-html", type=Path,
                        help="在清理前生成观测面板；默认 backend/target/observe/observe-<run>.html")
    args = parser.parse_args(argv)
    env_file = args.env_file.resolve()
    _read_dotenv(env_file)
    os.environ.setdefault("APITEST_ENV_FILE", str(env_file))
    try:
        config = TestConfig.load()
    except ValueError as exception:
        print(f"ENVIRONMENT: {exception}", file=sys.stderr)
        return 2
    http = HttpClient(config.base_url, config.http_timeout, config.stream_timeout)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    suffix = run_id[-6:]
    specs = [
        ("解题引导-obs-" + suffix, "obs-guidance-" + suffix, "学生遇到一道几何题，请引导他先写出已知条件。"),
        ("作业批改-obs-" + suffix, "obs-grading-" + suffix, "请说明这次作答的得分依据和改进方向。"),
        ("错题讲解-obs-" + suffix, "obs-explanation-" + suffix, "请讲解这道错题错在哪里、下一步怎么改。"),
    ]
    result: dict = {"runId": run_id, "steps": {}, "passed": False}
    teacher = login(http, config.teacher_username, config.teacher_password)
    student = login(http, config.student_username, config.student_password)
    assistants = http.request("GET", "/api/assistants", token=teacher["token"]).json()
    enabled = [item for item in assistants if item.get("enabled")] if isinstance(assistants, list) else []
    if not enabled:
        print("ENVIRONMENT: 本班没有已启用智能体", file=sys.stderr)
        return 2
    assistant = enabled[0]
    assistant_id = assistant["id"]
    original = http.request("GET", f"/api/assistants/{assistant_id}", token=teacher["token"]).json()
    original_config = {"name": original["name"], "promptText": original.get("promptText") or "",
                       "enabled": True, "skillIds": original.get("skillIds") or [],
                       "materialIds": original.get("materialIds") or []}
    created_skills: list[int] = []
    conversation_id: int | None = None
    try:
        for name, trigger, _ in specs:
            response = http.request("POST", "/api/skills", token=teacher["token"],
                                    json_body={"sourceText": skill_source(name, trigger)})
            if response.status != 201:
                raise RuntimeError(f"创建技能失败：{name} status={response.status}")
            created_skills.append(int(response.json()["id"]))
        bound = dict(original_config)
        bound["skillIds"] = created_skills
        response = http.request("PUT", f"/api/assistants/{assistant_id}", token=teacher["token"],
                                json_body=bound)
        if response.status != 200:
            raise RuntimeError(f"绑定技能失败：status={response.status}")

        response = http.request("POST", "/api/conversations", token=student["token"],
                                json_body={"assistantId": assistant_id})
        if response.status != 201:
            raise RuntimeError(f"创建会话失败：status={response.status}")
        conversation_id = int(response.json()["id"])

        for name, trigger, question in specs:
            stream = http.stream_json_sse("POST", f"/api/conversations/{conversation_id}/messages",
                                          token=student["token"],
                                          json_body={"content": f"{trigger} {question}"})
            names = [event.name for event in stream.events]
            meta = stream.events[0].data if stream.events and stream.events[0].name == "meta" else {}
            result["steps"][name] = {
                "httpStatus": stream.status,
                "events": names[:1] + (["..."] if len(names) > 2 else []) + names[-1:],
                "skillName": meta.get("skillName"),
                "done": bool(names and names[-1] == "done"),
            }
            if stream.status != 200 or not names or names[-1] != "done" or meta.get("skillName") != name:
                raise RuntimeError(f"技能调用未按预期完成：{name}")

        # 面板必须在删除会话前生成，否则调用事实会被本轮清理掉。
        panel = args.observe_html or (ROOT / "backend/target/observe" / f"observe-{run_id}.html")
        result["observeExit"] = observe.main([
            "--env-file", str(env_file), "--window", args.observe_window,
            "--html", str(panel)])
        result["observeHtml"] = str(panel)
        if result["observeExit"] != 0:
            raise RuntimeError("观测快照生成失败")
        result["passed"] = True
    finally:
        if conversation_id is not None:
            http.request("DELETE", f"/api/conversations/{conversation_id}", token=student["token"])
        http.request("PUT", f"/api/assistants/{assistant_id}", token=teacher["token"],
                     json_body=original_config)
        for skill_id in created_skills:
            http.request("DELETE", f"/api/skills/{skill_id}", token=teacher["token"])
    output = ROOT / "backend/target/observe" / f"seed-{run_id}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"seed passed={result['passed']} skills={len(created_skills)} summary={output}")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
