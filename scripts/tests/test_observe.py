import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "observe", Path(__file__).resolve().parents[1] / "observe.py")
observe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observe)


class ObserveTest(unittest.TestCase):
    def test_window_parser(self):
        self.assertEqual(30, observe.parse_window("30s"))
        self.assertEqual(300, observe.parse_window("5m"))
        self.assertEqual(7200, observe.parse_window("2h"))
        for raw in ("", "0m", "5", "5d", "-1m"):
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    observe.parse_window(raw)

    def test_api_metrics_counts_only_server_errors_and_excludes_sse_p95(self):
        rows = [
            {"path": "/api/auth/login", "status": 200, "durationMs": 10},
            {"path": "/api/auth/login", "status": 403, "durationMs": 20},
            {"path": "/api/grading/tasks/x", "status": 503, "durationMs": 30},
            {"path": "/api/conversations/1/messages", "status": 200, "durationMs": 900},
        ]
        result = observe.api_metrics(rows)
        self.assertEqual(4, result["requests"])
        self.assertEqual(1, result["serverErrors"])
        self.assertEqual(1, result["clientErrors"])
        self.assertEqual(0.25, result["errorRate"])
        # SSE 轮次不进入不含 SSE 的 P95。
        self.assertLess(result["p95ExcludingSseMs"], 900)

    def test_empty_api_metrics_are_zero_not_fabricated(self):
        result = observe.api_metrics([])
        self.assertEqual(0, result["requests"])
        self.assertEqual(0, result["p95Ms"])
        self.assertEqual(0.0, result["errorRate"])


if __name__ == "__main__":
    unittest.main()
