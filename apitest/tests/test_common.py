"""验证接口测试框架自身的 HTTP、SSE、脱敏和报告能力。"""

from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import unittest
from unittest.mock import patch

APITEST_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(APITEST_ROOT))
sys.path.insert(0, str(APITEST_ROOT.parent / "scripts"))

from common.config import TestConfig  # noqa: E402
from common.context import TestContext  # noqa: E402
from common.http_client import HttpClient  # noqa: E402
from common.models import CaseResult, CleanupResult  # noqa: E402
from common.report import write_report  # noqa: E402
from sec08_experiment import cross_teacher_credentials  # noqa: E402


class _Handler(BaseHTTPRequestHandler):
    """为公共客户端提供不访问外网的确定性测试响应。"""

    def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler 固定方法名
        length = int(self.headers.get("Content-Length", "0"))
        request_body = self.rfile.read(length)
        if self.path == "/echo":
            body = json.dumps({"body": request_body.decode("utf-8"),
                               "origin": self.headers.get("Origin"),
                               "contentType": self.headers.get("Content-Type"),
                               "authorization": self.headers.get("Authorization")}).encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path == "/json":
            body = json.dumps({"ok": True}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path == "/sse":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            frames = [
                'event: meta\ndata: {"status":"ready","citations":[]}\n\n',
                'event: delta\ndata: {"text":"中文"}\n\n',
                'event: done\ndata: {"messageId":7}\n\n',
            ]
            for frame in frames:
                self.wfile.write(frame.encode("utf-8"))
                self.wfile.flush()
            return
        self.send_error(404)

    def log_message(self, _format, *_args):
        return


class CommonInfrastructureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        cls.thread = Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.client = HttpClient(
            f"http://127.0.0.1:{cls.server.server_port}", timeout=2, stream_timeout=2
        )

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def test_json_request_and_sse_order(self):
        response = self.client.request("POST", "/json", json_body={"value": "测试"})
        self.assertEqual(200, response.status)
        self.assertEqual({"ok": True}, response.json())

        stream = self.client.stream_json_sse("POST", "/sse", token="local-token", json_body={})
        self.assertEqual(200, stream.status)
        self.assertEqual(["meta", "delta", "done"], [event.name for event in stream.events])
        self.assertEqual("中文", stream.events[1].data["text"])

    def test_context_sanitizes_password_and_jwt(self):
        config = TestConfig(
            project_root=Path.cwd(),
            base_url="http://127.0.0.1",
            teacher_username="teacher",
            teacher_password="teacher-secret",
            student_username="student",
            student_password="student-secret",
            db_host="127.0.0.1",
            db_port=3306,
            db_name="test",
            db_user="test",
            db_password="db-secret",
            mcp_auth_token="mcp-secret",
            mcp_base_url="http://mcp-demo:8090",
            http_timeout=1,
            stream_timeout=1,
            other_teacher_password="cross-teacher-secret",
        )
        context = TestContext(config)
        jwt = "eyJ1234567890.abcdefghijklmnop.qrstuvwxyz"
        context.add_secret(jwt)
        safe = context.sanitize(f"teacher-secret db-secret cross-teacher-secret {jwt}")
        self.assertNotIn("teacher-secret", safe)
        self.assertNotIn("db-secret", safe)
        self.assertNotIn(jwt, safe)
        self.assertNotIn("cross-teacher-secret", safe)

    def test_custom_headers_and_utf8_multipart_reach_the_server_unchanged(self):
        boundary = "local-multipart-boundary"
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                'filename="数学.md"\r\nContent-Type: text/markdown\r\n\r\n'
                f'# 合成讲义\r\n--{boundary}--\r\n').encode("utf-8")
        response = self.client.request(
            "POST", "/echo", data=body, content_type=f"multipart/form-data; boundary={boundary}",
            token="local-token", headers={"Origin": "https://example.invalid"})
        self.assertEqual(200, response.status)
        self.assertEqual(body.decode("utf-8"), response.json()["body"])
        self.assertEqual(f"multipart/form-data; boundary={boundary}", response.json()["contentType"])
        self.assertEqual("https://example.invalid", response.json()["origin"])
        self.assertEqual("Bearer local-token", response.json()["authorization"])
        with self.assertRaises(ValueError):
            self.client.request("POST", "/echo", data=body, json_body={})

    def test_cross_class_account_can_be_configured_without_changing_existing_users(self):
        values = {"DEMO_SEED_PASSWORD": "local-password", "MYSQL_PASSWORD": "local-db-password",
                  "MCP_AUTH_TOKEN": "local-mcp-token", "APITEST_OTHER_TEACHER_USERNAME": "peer-teacher",
                  "APITEST_OTHER_TEACHER_PASSWORD": "independent-secret"}
        with patch.dict("os.environ", values, clear=True), patch("common.config._read_dotenv", return_value={}):
            config = TestConfig.load()
            self.assertEqual((config.other_teacher_username, config.other_teacher_password),
                             cross_teacher_credentials(values))
        self.assertEqual("peer-teacher", config.other_teacher_username)
        self.assertEqual("independent-secret", config.other_teacher_password)
        self.assertIn("independent-secret", config.secret_values())
        values.pop("APITEST_OTHER_TEACHER_USERNAME")
        values.pop("APITEST_OTHER_TEACHER_PASSWORD")
        with patch.dict("os.environ", values, clear=True), patch("common.config._read_dotenv", return_value={}):
            config = TestConfig.load()
            self.assertEqual((config.other_teacher_username, config.other_teacher_password),
                             cross_teacher_credentials(values))
        self.assertEqual("teacher-b", config.other_teacher_username)
        self.assertEqual(config.teacher_password, config.other_teacher_password)
        values["APITEST_TEACHER_PASSWORD"] = "teacher-override"
        with patch.dict("os.environ", values, clear=True), patch("common.config._read_dotenv", return_value={}):
            config = TestConfig.load()
            self.assertEqual((config.other_teacher_username, config.other_teacher_password),
                             cross_teacher_credentials(values))
        self.assertEqual("teacher-override", config.other_teacher_password)

    def test_report_escapes_dynamic_text(self):
        with TemporaryDirectory() as directory:
            path = write_report(
                Path(directory),
                "run-id",
                "http://localhost",
                [CaseResult("<script>", "POST", "/api/test", False, 3, "<b>failure</b>")],
                [CleanupResult("cleanup", True, "ok")],
            )
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("<script>", content)
            self.assertIn("&lt;script&gt;", content)
            self.assertIn("&lt;b&gt;failure&lt;/b&gt;", content)


if __name__ == "__main__":
    unittest.main()
