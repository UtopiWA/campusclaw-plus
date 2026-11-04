import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "lesson08_acceptance", Path(__file__).resolve().parents[1] / "lesson08_acceptance.py")
ACCEPTANCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ACCEPTANCE)


class Lesson08AcceptanceTest(unittest.TestCase):
    def test_failed_grading_does_not_pass_basic_acceptance(self):
        with patch("sys.argv", ["acceptance", "basic"]), \
                patch.object(ACCEPTANCE, "_env", return_value={"DEMO_SEED_PASSWORD": "synthetic"}), \
                patch.object(ACCEPTANCE, "login", return_value="synthetic-token"), \
                patch.object(ACCEPTANCE, "create_assignment", return_value=1), \
                patch.object(ACCEPTANCE, "submit", return_value=(202, {"taskId": "task", "status": "PENDING"}, 1)), \
                patch.object(ACCEPTANCE, "wait_task", return_value={"status": "FAILED", "attempts": 1}), \
                patch.object(ACCEPTANCE, "report") as report:
            with self.assertRaises(SystemExit):
                ACCEPTANCE.main()
            report.assert_not_called()

    def test_202_with_score_is_rejected(self):
        with patch.object(ACCEPTANCE, "_request", return_value=(202, {"taskId": "task", "score": 100})):
            with self.assertRaises(SystemExit):
                ACCEPTANCE.submit("synthetic-token", 1, "synthetic answer")


if __name__ == "__main__":
    unittest.main()
