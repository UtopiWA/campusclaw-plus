"""确保 MCP 用例中途失败仍能收尾本轮已创建的服务。"""
import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "apitest"))
spec = importlib.util.spec_from_file_location("mcp_case", ROOT / "apitest/cases/api/mcp/servers/POST.py")
case = importlib.util.module_from_spec(spec)
spec.loader.exec_module(case)


class ApiCleanupTest(unittest.TestCase):
    def test_failure_after_create_still_deletes_only_owned_service(self):
        context = SimpleNamespace(
            state={"teacher": {"token": "test-teacher", "user": {"classId": 7}},
                   "student": {"token": "test-student"}},
            run_id="test-123abc",
            config=SimpleNamespace(mcp_base_url="http://mcp-demo:8090", mcp_auth_token="test-token"),
            http=SimpleNamespace(request=Mock()), db=SimpleNamespace(query_one=Mock()),
            add_cleanup=Mock(),
        )
        context.http.request.side_effect = [
            SimpleNamespace(status=403),
            SimpleNamespace(status=201, json=lambda: {"id": 83, "name": "mcp-123abc", "connected": False, "hasAuth": True}),
            SimpleNamespace(status=500),
            SimpleNamespace(status=204),
        ]
        context.db.query_one.side_effect = [
            {"auth_token_ciphertext": "v1:synthetic", "config_version": 1}, {"id": 83}, {"total": 0},
        ]
        with self.assertRaisesRegex(AssertionError, "重名"):
            case.run(context)
        context.add_cleanup.assert_called_once()
        context.add_cleanup.call_args.args[1]()
        context.http.request.assert_called_with("DELETE", "/api/mcp/servers/83", token="test-teacher")
        self.assertEqual((83, 7, "mcp-123abc"), context.db.query_one.call_args_list[1].args[1])


if __name__ == "__main__":
    unittest.main()
