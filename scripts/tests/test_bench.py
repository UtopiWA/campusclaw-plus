import importlib.util
import json
from pathlib import Path
import tempfile
from threading import Barrier
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "bench", Path(__file__).resolve().parents[1] / "bench.py")
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)


class BenchTest(unittest.TestCase):
    def test_percentile_uses_nearest_rank_and_empty_is_zero(self):
        values = [1.0, 2.0, 3.0, 4.0, 100.0]
        self.assertEqual(0.0, bench.percentile([], 0.95))
        self.assertEqual(3.0, bench.percentile(values, 0.50))
        self.assertEqual(100.0, bench.percentile(values, 0.95))

    def test_invalid_or_underfilled_load_does_not_read_environment(self):
        for raw in ("", "0", "-1", "a", "1,x", "16,32,64"):
            with self.subTest(raw=raw):
                with patch.object(bench, "_read_dotenv") as read:
                    self.assertEqual(2, bench.main(["--levels", raw, "--requests", "8"]))
                read.assert_not_called()

    def test_records_peak_and_unexpected_status_count(self):
        barrier = Barrier(3, timeout=5)

        def respond(index):
            barrier.wait()
            return 200 if index == 0 else 503

        result = bench.run_level(respond, range(3), 3, {200})
        self.assertEqual(3, result["peakInFlight"])
        self.assertEqual({"200": 1, "503": 2}, result["statuses"])
        self.assertEqual(0.6667, result["errorRate"])

    def test_cleanup_failure_returns_nonzero_after_worker_restore(self):
        person = {"token": "test", "user": {"classId": 1}}
        http = SimpleNamespace(request=lambda method, path, **kwargs: SimpleNamespace(
            status=202 if path.endswith("/submissions") else 201 if path == "/api/assignments" else 200,
            json=lambda: {"id": 4, "taskId": "test-task", "status": "SUCCEEDED"}))
        config = SimpleNamespace(base_url="http://localhost", http_timeout=1, stream_timeout=1,
                                 teacher_username="teacher", teacher_password="test",
                                 student_username="student", student_password="test")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env_file = root / ".env.test"
            env_file.touch()
            with patch.object(bench, "ROOT", root), patch.dict(bench.os.environ), \
                 patch.object(bench.TestConfig, "load", return_value=config), \
                 patch.object(bench, "HttpClient", return_value=http), patch.object(bench, "DatabaseClient"), \
                 patch.object(bench, "login", return_value=person), patch.object(bench, "queue_depth", return_value=0), \
                 patch.object(bench, "upload_material", return_value=3), patch.object(bench, "wait_for_material"), \
                 patch.object(bench, "retrieval_seed", return_value="sample"), \
                 patch.object(bench, "compose") as compose, \
                 patch.object(bench, "cleanup", side_effect=RuntimeError):
                self.assertEqual(1, bench.main([
                    "--env-file", str(env_file), "--levels", "1", "--requests", "1", "--stop-worker"
                ]))
            self.assertEqual([("stop", "grading-worker"), ("start", "grading-worker")],
                             [call.args[1:] for call in compose.call_args_list])
            result_path, = (root / "backend/target/bench").glob("*.json")
            result = json.loads(result_path.read_text(encoding="utf-8"))
            self.assertFalse(result["passed"])
            self.assertEqual("RuntimeError", result["cleanupError"])


if __name__ == "__main__":
    unittest.main()
