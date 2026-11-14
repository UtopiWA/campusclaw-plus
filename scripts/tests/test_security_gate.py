"""校验安全门禁的退出码聚合和受控故障边界。"""
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import security_gate  # noqa: E402


class SecurityGateTest(unittest.TestCase):
    def test_aggregate_prioritizes_incomplete_then_failure(self):
        self.assertEqual(security_gate.aggregate([0, 0, 0, 0, 0]), 0)
        self.assertEqual(security_gate.aggregate([0, 1, 0]), 1)
        self.assertEqual(security_gate.aggregate([0, 2, 1]), 2)

    def test_stage_commands_cover_all_required_checks(self):
        names = [name for name, _, _ in security_gate.stage_commands(Path(".env.team"))]
        self.assertEqual(names, ["scan", "access", "audit", "api", "unit"])

    def test_unknown_fault_stage_is_rejected_by_cli_choices(self):
        with self.assertRaises(SystemExit):
            security_gate.main(["--fault-stage", "not-a-stage"])


if __name__ == "__main__":
    unittest.main()
