"""团队Docker安全自查；只创建并清理独立命名的合成资源，不代签同伴复现。"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid

import lesson08_acceptance as acceptance

ROOT = Path(__file__).resolve().parents[1]


class Precheck:
    def __init__(self, env_file: Path):
        acceptance.ENV_PATH = env_file
        self.env = acceptance._env()
        if self.env.get("MYSQL_DATABASE") != "campusclaw_plus":
            raise ValueError("expected isolated team database")
        acceptance.BASE_URL = f"http://127.0.0.1:{self.env.get('APP_PORT', '18080')}"
        acceptance.MANAGEMENT_URL = f"http://127.0.0.1:{self.env.get('RABBITMQ_MANAGEMENT_PORT', '15673')}"
        self.compose = ["docker", "compose", "--env-file", str(env_file)]
        config = subprocess.run(self.compose + ["config", "--format", "json"], cwd=ROOT, capture_output=True, check=True)
        parsed = json.loads(config.stdout)
        if parsed.get("name") != "campusclaw-plus" or f"/{self.env['MYSQL_DATABASE']}?" not in parsed["services"]["backend"]["environment"]["DB_URL"]:
            raise ValueError("application/database mismatch")
        self.run_id = uuid.uuid4().hex[:12]
        self.folder = ROOT / "backend/target/security-precheck" / self.run_id
        self.folder.mkdir(parents=True)
        self.ledger: list[dict] = []
        self.worker_stopped = False
        self.report = {"runId": self.run_id, "timeUtc": datetime.now(timezone.utc).isoformat(),
                       "checks": {}, "execution": "running", "cleanup": "pending"}
        self.teacher = acceptance.login("teacher-a", self.env["DEMO_SEED_PASSWORD"])
        self.student = acceptance.login("student-a1", self.env["DEMO_SEED_PASSWORD"])
        self.other = acceptance.login("student-b1", self.env["DEMO_SEED_PASSWORD"])

    def persist(self) -> None:
        (self.folder / "fixtures.json").write_text(json.dumps(self.ledger), encoding="utf-8")
        (self.folder / "results.json").write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")

    def remember(self, kind: str, identifier: int) -> int:
        self.ledger.append({"kind": kind, "id": identifier})
        self.persist()
        return identifier

    def query(self, statement: str, parameters=()):
        with acceptance.database(self.env) as db:
            with db.cursor() as cursor:
                cursor.execute(statement, parameters)
                return cursor.fetchall()

    def api(self, method: str, path: str, token=None, body=None, expected=200):
        code, value = acceptance._request(method, path, token, json.dumps(body).encode() if body is not None else None)
        if code != expected:
            self.report["lastHttp"] = {"method": method, "path": path, "status": code, "expected": expected}
            raise AssertionError(f"unexpected HTTP status {code}, expected {expected}: {method} {path}")
        return value

    def record(self, key: str, result: dict, *, green=True) -> None:
        self.report["checks"][key] = {"status": "green" if green else "red", **result}
        self.persist()

    def compose_run(self, *args: str) -> None:
        with (self.folder / "compose.log").open("ab") as log:
            subprocess.run(self.compose + list(args), cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)

    def setup(self) -> None:
        self.mark = "蓝鲸山谷专属演算" + self.run_id
        boundary = "sec12-boundary"
        text = f"# 自查参考答案\n{self.mark}\n直角三角形满足 a²+b²=c²，c为斜边。平方根81为9。"
        data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sec12-{self.run_id}.md"\r\n'
                f'Content-Type: text/markdown\r\n\r\n{text}\r\n--{boundary}--\r\n').encode()
        request = urllib.request.Request(acceptance.BASE_URL + "/api/materials/upload", data,
                                         {"Authorization": "Bearer " + self.teacher,
                                          "Content-Type": "multipart/form-data; boundary=" + boundary})
        with urllib.request.urlopen(request, timeout=60) as response:
            self.material = self.remember("material", json.loads(response.read())["materialId"])
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            material = self.api("GET", f"/api/materials/{self.material}", self.teacher)
            if material["indexStatus"] == "READY":
                break
            if material["indexStatus"] == "FAILED":
                raise AssertionError("synthetic material failed")
            time.sleep(1)
        else:
            raise TimeoutError("synthetic material not ready")
        self.source = f"名称：自查资料{self.run_id}\n适用条件：自查,归纳\n资料：\n工具：knowledge_search\n启用：是\n\n根据绑定讲义回答。"
        self.skill = self.remember("skill", self.api("POST", "/api/skills", self.teacher,
                                                    {"sourceText": self.source}, 201)["id"])
        self.assistant_body = {"name": "sec12-" + self.run_id, "promptText": "基于材料讲解。", "enabled": True,
                               "skillIds": [self.skill], "materialIds": [self.material]}
        self.assistant = self.remember("assistant", self.api("POST", "/api/assistants", self.teacher,
                                                            self.assistant_body, 201)["id"])
        self.assignment = self.remember("assignment", self.api("POST", "/api/assignments", self.teacher,
                                                               {"title": "sec12-" + self.run_id, "prompt": "解释勾股定理与适用条件。",
                                                                "maxScore": 100, "rubricMaterialIds": [self.material]}, 201)["id"])

    def access(self) -> None:
        routes = [("GET", "/api/auth/me", None), ("GET", "/api/materials", None),
                  ("GET", "/api/assistants", None), ("GET", "/api/conversations", None),
                  ("GET", "/api/assignments", None),
                  ("POST", "/api/retrieval/search", {"query": self.mark, "mode": "keyword"})]
        for method, path, body in routes:
            self.api(method, path, body=body, expected=401)
        self.record("SEC-01", {"routes": [f"{m} {p}" for m, p, _ in routes], "statuses": [401] * len(routes)})
        # B班旧种子只取编号，不读取或修改内容；不存在时明确未完成，不借用真实文件。
        seed = self.query("SELECT m.id FROM materials m JOIN users u ON u.class_id=m.class_id "
                          "WHERE u.username=%s AND m.title=%s AND m.stored_path IS NULL LIMIT 1",
                          ("student-b1", "B 班数学教研示例"))
        if not seed:
            raise ValueError("B-class synthetic seed unavailable")
        self.api("GET", f"/api/materials/{seed[0][0]}", self.student, expected=404)
        self.api("GET", f"/api/materials/{self.material}", self.other, expected=404)
        search = {"query": self.mark, "mode": "keyword", "limit": 10}
        own = self.api("POST", "/api/retrieval/search", self.student, search)
        cross = self.api("POST", "/api/retrieval/search", self.other, search)
        if not any(hit["materialId"] == self.material for hit in own["hits"]) or cross["hits"]:
            raise AssertionError("keyword class boundary not confirmed")
        self.record("SEC-02", {"crossRead": [404, 404], "ownHits": len(own["hits"]), "crossHits": len(cross["hits"])})
        before = (self.api("GET", f"/api/skills/{self.skill}", self.teacher),
                  self.api("GET", f"/api/assistants/{self.assistant}", self.teacher))
        db_before = (self.query("SELECT source_text FROM skills WHERE id=%s", (self.skill,)),
                     self.query("SELECT prompt_text FROM assistants WHERE id=%s", (self.assistant,)))
        self.api("PUT", f"/api/skills/{self.skill}", self.student, {"sourceText": self.source}, 403)
        self.api("PUT", f"/api/assistants/{self.assistant}", self.student,
                 {**self.assistant_body, "promptText": "synthetic denied replacement"}, 403)
        after = (self.api("GET", f"/api/skills/{self.skill}", self.teacher),
                 self.api("GET", f"/api/assistants/{self.assistant}", self.teacher))
        db_after = (self.query("SELECT source_text FROM skills WHERE id=%s", (self.skill,)),
                    self.query("SELECT prompt_text FROM assistants WHERE id=%s", (self.assistant,)))
        if before != after or db_before != db_after:
            raise AssertionError("denied write changed configuration")
        self.record("SEC-03", {"statuses": [403, 403], "configurationUnchanged": True,
                               "dbHashBefore": hashlib.sha256(str(db_before).encode()).hexdigest(),
                               "dbHashAfter": hashlib.sha256(str(db_after).encode()).hexdigest()})
        counts = self.query("SELECT COUNT(*) FROM assignments WHERE title=%s", ("denied-" + self.run_id,))
        reviews_before = self.query("SELECT COUNT(*) FROM submission_reviews WHERE assignment_id=%s", (self.assignment,))[0][0]
        self.api("POST", "/api/assignments", self.student,
                 {"title": "denied-" + self.run_id, "prompt": "synthetic", "maxScore": 100, "rubricMaterialIds": [self.material]}, 403)
        self.api("PUT", f"/api/assignments/{self.assignment}/submissions/99999999/review", self.student,
                 {"score": 80, "comment": "synthetic denied review"}, 403)
        reviews_after = self.query("SELECT COUNT(*) FROM submission_reviews WHERE assignment_id=%s", (self.assignment,))[0][0]
        if counts != self.query("SELECT COUNT(*) FROM assignments WHERE title=%s", ("denied-" + self.run_id,)) or reviews_before != reviews_after:
            raise AssertionError("unauthorized assignment created")
        self.record("SEC-04", {"statuses": [403, 403], "newUnauthorizedAssignments": 0,
                               "newUnauthorizedReviews": reviews_after - reviews_before})

    def tasks(self) -> None:
        if self.query("SELECT COUNT(*) FROM grading_tasks WHERE status IN ('PENDING','RUNNING')")[0][0]:
            raise ValueError("unrelated tasks active; defer worker stop")
        identity = subprocess.run(self.compose + ["ps", "-q", "grading-worker"], cwd=ROOT, capture_output=True, check=True)
        if not identity.stdout.strip():
            raise ValueError("worker was not running")
        self.compose_run("stop", "grading-worker")
        self.worker_stopped = True
        try:
            def submit(_):
                started = time.perf_counter()
                value = self.api("POST", f"/api/assignments/{self.assignment}/submissions", self.student,
                                 {"content": "直角三角形中a²+b²=c²，c为斜边。"}, 202)
                if "result" in value or "score" in value:
                    raise AssertionError("202 contains score")
                return value, round((time.perf_counter() - started) * 1000, 1)
            with ThreadPoolExecutor(max_workers=4) as pool:
                results = list(pool.map(submit, range(4)))
            accepted = results[0][0]
            if len({row[0]["taskId"] for row in results}) != 1:
                raise AssertionError("duplicate tasks accepted")
            if acceptance.get_task(self.student, accepted["taskId"])["status"] != "PENDING":
                raise AssertionError("worker-stop state is not pending")
            for table in ("submissions", "grading_tasks"):
                if self.query(f"SELECT COUNT(*) FROM {table} WHERE assignment_id=%s", (self.assignment,))[0][0] != 1:
                    raise AssertionError("submission transaction produced duplicate rows")
            self.record("transaction", {"pendingAfterWorkerStop": True, "submissionRows": 1, "taskRows": 1,
                                        "responseCodes": [202] * 4, "submitMs": [r[1] for r in results]})
        finally:
            self.compose_run("start", "grading-worker")
            self.worker_stopped = False
        completed = acceptance.wait_task(self.student, accepted["taskId"])
        if completed["status"] != "SUCCEEDED":
            raise AssertionError("synthetic grading failed")
        original = self.query("SELECT * FROM grading_results WHERE task_id=%s", (accepted["taskId"],))
        for _ in range(3):
            acceptance.publish_duplicate(accepted["taskId"], self.env)
        time.sleep(3)
        if original != self.query("SELECT * FROM grading_results WHERE task_id=%s", (accepted["taskId"],)):
            raise AssertionError("duplicate replaced original result")
        self.api("POST", f"/api/assignments/{self.assignment}/submissions", self.student, {"content": "different"}, 409)
        self.api("GET", f"/api/grading/tasks/{accepted['taskId']}", self.other, expected=404)
        self.record("concurrency", {"parallelRequests": 4, "taskRows": 1, "resultRows": len(original),
                                    "duplicateMessages": 3, "resultUnchanged": True, "differentAnswer": 409,
                                    "executeMs": acceptance.task_duration_ms(completed)})

    def audit(self) -> None:
        name = "sec12-" + self.run_id
        server = self.remember("server", self.api("POST", "/api/mcp/servers", self.teacher,
                                                  {"name": name, "baseUrl": "http://mcp-demo:8090", "token": self.env["MCP_AUTH_TOKEN"]}, 201)["id"])
        self.api("POST", f"/api/mcp/servers/{server}/connect", self.teacher)
        source = f"名称：自查计算{self.run_id}\n适用条件：计算,幂\n资料：\n工具：calculator@{name}\n启用：是\n\n调用计算器，依据结果解释。"
        skill = self.remember("skill", self.api("POST", "/api/skills", self.teacher, {"sourceText": source}, 201)["id"])
        assistant = self.remember("assistant", self.api("POST", "/api/assistants", self.teacher,
                                                        {"name": "audit-" + self.run_id, "enabled": True, "promptText": "依据计算结果作答。",
                                                         "skillIds": [skill], "materialIds": []}, 201)["id"])
        conversation = self.remember("conversation", self.api("POST", "/api/conversations", self.student,
                                                              {"assistantId": assistant}, 201)["id"])
        request = urllib.request.Request(acceptance.BASE_URL + f"/api/conversations/{conversation}/messages",
                                         json.dumps({"content": "计算2的20次幂"}).encode(),
                                         {"Authorization": "Bearer " + self.student, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=180) as response:
            stream = response.read().decode()
        if "event: done" not in stream or "event: error" in stream:
            raise AssertionError("synthetic skill stream did not complete")
        detail = self.api("GET", f"/api/conversations/{conversation}", self.student)
        calls = [row for row in self.api("GET", "/api/mcp/tool-calls?limit=50", self.teacher) if row["serverId"] == server]
        if not calls or calls[0]["status"] != "ok":
            raise AssertionError("calculator audit missing or failed")
        message = next(m for m in detail["messages"] if m["role"] == "assistant")
        call = calls[0]
        fields = {"actor": bool(call.get("userId") and call.get("userName")), "skillName": bool(message.get("skillName")),
                  "skillId": "skillId" in call, "status": bool(call.get("status")),
                  "durationMs": call.get("durationMs") is not None, "tokenUsage": call.get("tokenUsage") is not None}
        self.record("SEC-07", {"fields": fields, "snapshot": {k: call.get(k) for k in
                    ("id", "userId", "userName", "conversationId", "toolRef", "status", "durationMs")},
                    "skillName": message.get("skillName"), "tokenUsage": call.get("tokenUsage")}, green=all(fields.values()))

    def cleanup(self) -> None:
        if self.worker_stopped:
            self.compose_run("start", "grading-worker")
            self.worker_stopped = False
        for row in self.ledger:
            if row["kind"] == "assignment" and any(item[0] not in ("SUCCEEDED", "FAILED") for item in
                    self.query("SELECT status FROM grading_tasks WHERE assignment_id=%s", (row["id"],))):
                raise RuntimeError("synthetic task still active; retain fixture ledger")
        # 只清理本次登记资源；审计快照已采集，不改变普通API的审计删除保护。
        with acceptance.database(self.env) as db:
            try:
                with db.cursor() as cursor:
                    for row in self.ledger:
                        if row["kind"] == "server":
                            cursor.execute("DELETE FROM mcp_tool_calls WHERE server_id=%s", (row["id"],))
                    for row in self.ledger:
                        if row["kind"] != "assignment":
                            continue
                        for table in ("grading_outbox", "grading_results", "submission_reviews", "grading_tasks", "submissions", "assignments"):
                            where = ("task_id IN (SELECT task_id FROM grading_tasks WHERE assignment_id=%s)" if table in ("grading_outbox", "grading_results")
                                     else ("id" if table == "assignments" else "assignment_id") + "=%s")
                            cursor.execute(f"DELETE FROM {table} WHERE {where}", (row["id"],))
                db.commit()
            except Exception:
                db.rollback()
                raise
        endpoints = {"conversation": ("/api/conversations/", self.student), "assistant": ("/api/assistants/", self.teacher),
                     "skill": ("/api/skills/", self.teacher), "server": ("/api/mcp/servers/", self.teacher), "material": ("/api/materials/", self.teacher)}
        for kind in ("conversation", "assistant", "skill", "server", "material"):
            for row in self.ledger:
                if row["kind"] == kind:
                    path, token = endpoints[kind]
                    self.api("DELETE", path + str(row["id"]), token, expected=204)
        self.report["cleanup"] = "complete"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.team")
    parser.add_argument("--checks", choices=("all", "access", "tasks", "audit"), default="all")
    args = parser.parse_args(argv)
    run = None
    try:
        run = Precheck(args.env_file.resolve())
        run.report["phase"] = "setup"
        run.setup()
        for name in ("access", "tasks", "audit"):
            if args.checks in ("all", name):
                run.report["phase"] = name
                getattr(run, name)()
        run.report["execution"] = "complete"
    except (Exception, SystemExit) as error:
        if run:
            run.report["execution"] = "incomplete"
            run.report["errorType"] = type(error).__name__
        print("Precheck incomplete; no business response or credentials printed", flush=True)
    finally:
        if run:
            try:
                run.cleanup()
            except (Exception, SystemExit):
                run.report["cleanup"] = "incomplete; use private fixture ledger"
            run.persist()
            print(f"run={run.run_id} execution={run.report['execution']} cleanup={run.report['cleanup']}", flush=True)
    if not run or run.report["execution"] != "complete" or run.report["cleanup"] != "complete":
        return 2
    return int(any(row["status"] == "red" for row in run.report["checks"].values()))


if __name__ == "__main__":
    raise SystemExit(main())
