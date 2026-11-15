"""校验安全门禁的退出码聚合和受控故障边界。"""
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import patch

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

    def test_run_executes_every_stage_and_preserves_failure_or_incomplete(self):
        for codes, expected in (([0, 1, 0, 0, 0], 1), ([0, 1, 2, 0, 0], 2)):
            with self.subTest(codes=codes), TemporaryDirectory() as directory:
                root = Path(directory)
                with patch.object(security_gate, "ROOT", root), patch.object(
                    security_gate.subprocess, "run",
                    side_effect=[SimpleNamespace(returncode=code) for code in codes]
                ) as execute:
                    summary = security_gate.run(root / ".env.team", None, root / "out")
                self.assertEqual(5, execute.call_count)
                self.assertEqual(expected, summary["exitCode"])
                self.assertEqual(codes, [row["exitCode"] for row in summary["stages"].values()])

    def test_synthetic_fault_can_never_turn_a_failed_stage_green(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(security_gate, "ROOT", root), patch.object(
                security_gate.subprocess, "run",
                side_effect=[SimpleNamespace(returncode=code) for code in [0, 0, 2, 0, 0]]
            ):
                summary = security_gate.run(root / ".env.team", "audit", root / "out")
            self.assertEqual(2, summary["stages"]["audit"]["exitCode"])
            self.assertEqual(2, summary["exitCode"])
            self.assertFalse(summary["syntheticFault"])


if __name__ == "__main__":
    unittest.main()
