import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))


def module(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


SCAN = module("check_security")
PRECHECK = module("security_precheck")


class SecurityScannerTest(unittest.TestCase):
    def test_findings_never_export_match_or_secret(self):
        rows = SCAN.sanitize([{"Secret": "synthetic-sensitive", "Match": "private source", "File": "sample", "RuleID": "test"}])
        self.assertNotIn("synthetic-sensitive", json.dumps(rows))
        self.assertNotIn("private source", json.dumps(rows))

    def test_exact_configured_secret_is_detected_without_outputting_value(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "asset.js").write_text("synthetic-exact-value", encoding="utf-8")
            rows = SCAN.configured_findings(root, {"JWT_SECRET": "synthetic-exact-value"})
            self.assertEqual(1, len(rows))
            self.assertNotIn("synthetic-exact-value", json.dumps(rows))

    def test_shallow_history_is_incomplete(self):
        with patch.object(SCAN, "git", return_value="true"):
            with self.assertRaises(SCAN.Incomplete):
                SCAN.require_scope(Path("missing"))

    def test_missing_frontend_is_incomplete(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "git", return_value="false"):
            with self.assertRaises(SCAN.Incomplete):
                SCAN.require_scope(Path(folder))

    def test_incomplete_scanner_cannot_be_clean(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "OUTPUT", Path(folder)), \
                patch.object(SCAN, "command", return_value=Mock(returncode=2)):
            with self.assertRaises(SCAN.Incomplete):
                SCAN.scanner("sample", Path(folder))

    def test_old_secret_report_is_not_reused(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "OUTPUT", Path(folder)), \
                patch.object(SCAN, "command", return_value=Mock(returncode=0)):
            (Path(folder) / "sample-redacted.json").write_text("[]", encoding="utf-8")
            with self.assertRaises(SCAN.Incomplete):
                SCAN.scanner("sample", Path(folder))

    def test_missing_secret_scope_still_runs_dependencies_and_exits_incomplete(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "OUTPUT", Path(folder)), \
                patch.object(SCAN, "secrets", side_effect=SCAN.Incomplete("missing")), \
                patch.object(SCAN, "dependencies", return_value={"exitCode": 0}) as dependencies:
            self.assertEqual(2, SCAN.main([]))
            dependencies.assert_called_once()

    def test_old_dependency_logs_do_not_count_as_current_scan(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "ROOT", Path(folder)), \
                patch.object(SCAN, "command", return_value=Mock(returncode=0)):
            logs = Path(folder) / "backend/target/scan-logs"
            logs.mkdir(parents=True)
            for name in ("frontend-lint.log", "frontend-audit.log", "python-ruff.log", "python-pip-audit.log",
                         "java-checkstyle.log", "java-spotbugs.log", "java-dependencies.log", "java-osv.json"):
                target = logs / name
                target.write_text("old", encoding="utf-8")
                os.utime(target, (1, 1))
            result = SCAN.dependencies()
            self.assertFalse(result["complete"])
            self.assertEqual(2, result["exitCode"])

    def test_findings_keep_nonzero_exit_with_complete_scope(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(SCAN, "OUTPUT", Path(folder)), \
                patch.object(SCAN, "secrets", return_value={"exitCode": 1}):
            self.assertEqual(1, SCAN.main(["--only", "secrets"]))
            self.assertTrue((Path(folder) / "summary-secrets.json").is_file())


class SecurityPrecheckTest(unittest.TestCase):
    def test_unexpected_200_is_not_authorization_success(self):
        run = PRECHECK.Precheck.__new__(PRECHECK.Precheck)
        run.report = {}
        with patch.object(PRECHECK.acceptance, "_request", return_value=(200, {"private": "ignored"})):
            with self.assertRaises(AssertionError):
                run.api("PUT", "/api/skills/1", expected=403)
        self.assertEqual(200, run.report["lastHttp"]["status"])
        self.assertNotIn("private", json.dumps(run.report))

    def test_unknown_usage_is_not_written_as_zero(self):
        with tempfile.TemporaryDirectory() as folder:
            run = PRECHECK.Precheck.__new__(PRECHECK.Precheck)
            run.folder, run.ledger, run.report = Path(folder), [], {"checks": {}}
            run.record("SEC-07", {"tokenUsage": None}, green=False)
            self.assertEqual("red", run.report["checks"]["SEC-07"]["status"])
            self.assertIsNone(run.report["checks"]["SEC-07"]["tokenUsage"])

    def test_active_synthetic_task_prevents_cleanup(self):
        run = PRECHECK.Precheck.__new__(PRECHECK.Precheck)
        run.worker_stopped = False
        run.ledger = [{"kind": "assignment", "id": 1}]
        run.query = Mock(return_value=[("RUNNING",)])
        with self.assertRaises(RuntimeError):
            run.cleanup()

    def test_access_only_does_not_run_model_or_worker_checks(self):
        run = Mock()
        run.run_id = "synthetic"
        run.report = {"execution": "running", "cleanup": "complete", "checks": {}}
        with patch.object(PRECHECK, "Precheck", return_value=run):
            self.assertEqual(0, PRECHECK.main(["--checks", "access"]))
        run.access.assert_called_once()
        run.tasks.assert_not_called()
        run.audit.assert_not_called()


if __name__ == "__main__":
    unittest.main()
