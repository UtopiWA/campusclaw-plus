"""Class08 三条核心判据和队列观测的本地验收脚本。

用法（仓库根目录，应用已启动）：
  python scripts/lesson08_acceptance.py basic
  python scripts/lesson08_acceptance.py pending
  python scripts/lesson08_acceptance.py wait --task-id <id>
  python scripts/lesson08_acceptance.py duplicate --task-id <id>
  python scripts/lesson08_acceptance.py report

`basic` 创建一份合成作业并提交；`pending` 只提交并打印任务；`wait` 轮询到终态；
`duplicate` 通过 RabbitMQ management API 重投同一任务消息。脚本只打印任务标识、状态、
耗时、分数和队列计数，不打印作答、提示词、凭据或完整模型响应。
"""

from __future__ import annotations

import argparse
import base64
from datetime import datetime
import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "http://127.0.0.1:8081"
MANAGEMENT_URL = "http://127.0.0.1:15672"


def _env() -> dict[str, str]:
    values: dict[str, str] = {}
    path = ROOT / ".env"
    if not path.is_file():
        return values
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _request(method: str, path: str, token: str | None = None, body=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE_URL + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            payload = json.loads(raw) if raw else None
        except Exception:
            payload = raw.decode("utf-8", "replace")[:300]
        return error.code, payload


def login(username: str, password: str) -> str:
    status, payload = _request("POST", "/api/auth/login",
                               body=json.dumps({"username": username, "password": password}).encode())
    if status != 200:
        raise SystemExit(f"login failed: {status}")
    return payload["accessToken"]


def create_assignment(teacher: str) -> int:
    payload = {
        "title": f"Class08 acceptance {int(time.time())}",
        "prompt": "Explain the Pythagorean theorem in one short paragraph.",
        "criteria": [
            {"key": "c1", "description": "Correct formula", "points": 60},
            {"key": "c2", "description": "Clear explanation", "points": 40},
        ],
    }
    status, body = _request("POST", "/api/assignments", teacher, json.dumps(payload).encode())
    if status != 201:
        raise SystemExit(f"create assignment failed: {status} {body}")
    return body["id"]


def submit(student: str, assignment_id: int, content: str) -> tuple[int, dict, float]:
    started = time.perf_counter()
    status, body = _request("POST", f"/api/assignments/{assignment_id}/submissions", student,
                            json.dumps({"content": content}).encode())
    elapsed_ms = (time.perf_counter() - started) * 1000
    if status != 202:
        raise SystemExit(f"submit failed: {status} {body}")
    return status, body, elapsed_ms


def get_task(token: str, task_id: str) -> dict:
    status, body = _request("GET", f"/api/grading/tasks/{task_id}", token)
    if status != 200:
        raise SystemExit(f"task query failed: {status} {body}")
    return body


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    text = re.sub(r"(\.\d{6})\d+", r"\1", value.replace("Z", "+00:00"))
    return datetime.fromisoformat(text)


def task_duration_ms(task: dict) -> int | None:
    start = parse_time(task.get("startedAt") or task.get("createdAt"))
    end = parse_time(task.get("finishedAt"))
    if start is None or end is None:
        return None
    return max(0, int((end - start).total_seconds() * 1000))


def wait_task(token: str, task_id: str, timeout: int = 180) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        task = get_task(token, task_id)
        if task["status"] in {"SUCCEEDED", "FAILED"}:
            return task
        time.sleep(1)
    raise SystemExit("task did not reach terminal state in time")


def management_get(path: str, env: dict[str, str]) -> dict:
    credentials = base64.b64encode(
        f"{env['RABBITMQ_USER']}:{env['RABBITMQ_PASSWORD']}".encode()).decode()
    request = urllib.request.Request(MANAGEMENT_URL + path,
                                     headers={"Authorization": "Basic " + credentials})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def publish_duplicate(task_id: str, env: dict[str, str]) -> None:
    exchange = env.get("GRADING_EXCHANGE", "grading.events")
    routing_key = env.get("GRADING_DISPATCH_KEY", "grading.dispatch")
    payload = json.dumps({"properties": {}, "routing_key": routing_key,
                          "payload": json.dumps({"taskId": task_id}),
                          "payload_encoding": "string"}).encode()
    credentials = base64.b64encode(
        f"{env['RABBITMQ_USER']}:{env['RABBITMQ_PASSWORD']}".encode()).decode()
    request = urllib.request.Request(
        MANAGEMENT_URL + f"/api/exchanges/%2F/{exchange}/publish",
        data=payload, method="POST",
        headers={"Authorization": "Basic " + credentials, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.loads(response.read())
    if not result.get("routed"):
        raise SystemExit("duplicate message was not routed")


def database(env: dict[str, str]):
    return pymysql.connect(host="127.0.0.1", port=3307,
                           user=env.get("MYSQL_USER", "campusclaw"),
                           password=env["MYSQL_PASSWORD"],
                           database=env.get("MYSQL_DATABASE", "campusclaw"),
                           charset="utf8mb4")


def report(env: dict[str, str]) -> None:
    with database(env) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT status, COUNT(*), COALESCE(SUM(attempts),0) "
                           "FROM grading_tasks GROUP BY status")
            print("tasks(status,count,attempts):", cursor.fetchall())
            cursor.execute("SELECT status, COUNT(*) FROM grading_outbox GROUP BY status")
            print("outbox(status,count):", cursor.fetchall())
            cursor.execute("SELECT COUNT(*) FROM grading_results")
            print("results:", cursor.fetchone()[0])
    for queue in (env.get("GRADING_TASK_QUEUE", "grading.tasks"),
                  env.get("GRADING_DLQ", "grading.dlq")):
        data = management_get(f"/api/queues/%2F/{queue}", env)
        print(f"queue {queue}: messages={data.get('messages')} ready={data.get('messages_ready')} "
              f"unacked={data.get('messages_unacknowledged')}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("basic", "pending", "wait", "duplicate", "report"))
    parser.add_argument("--task-id")
    args = parser.parse_args()
    env = _env()
    password = env.get("DEMO_SEED_PASSWORD")
    if not password:
        raise SystemExit("missing DEMO_SEED_PASSWORD")

    if args.mode == "report":
        report(env)
        return 0

    teacher = login("teacher-a", password)
    student = login("student-a1", password)

    if args.mode in {"basic", "pending"}:
        assignment_id = create_assignment(teacher)
        content = ("In a right triangle the square of the hypotenuse equals the sum of the squares "
                   "of the other two sides, so a^2 + b^2 = c^2.")
        status, body, elapsed_ms = submit(student, assignment_id, content)
        task_id = body["taskId"]
        print(f"assignment={assignment_id} task={task_id} status={body['status']} "
              f"submit_ms={elapsed_ms:.1f} has_score={'result' in body}")
        if args.mode == "pending":
            return 0
        task = wait_task(student, task_id)
        duration = task_duration_ms(task)
        print(f"final_status={task['status']} attempts={task['attempts']} "
              f"score={(task.get('result') or {}).get('score')} "
              f"maxScore={(task.get('result') or {}).get('maxScore')} task_ms={duration}")
        report(env)
        return 0

    if not args.task_id:
        raise SystemExit("--task-id is required")
    if args.mode == "wait":
        task = wait_task(student, args.task_id)
        print(f"final_status={task['status']} attempts={task['attempts']}")
        return 0

    publish_duplicate(args.task_id, env)
    time.sleep(2)
    task = get_task(student, args.task_id)
    with database(env) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM grading_results WHERE task_id = %s", (args.task_id,))
            results = cursor.fetchone()[0]
    print(f"duplicate task={args.task_id} status={task['status']} attempts={task['attempts']} "
          f"results={results}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
