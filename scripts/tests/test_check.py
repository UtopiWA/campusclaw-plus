import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("check", Path(__file__).resolve().parents[1] / "check.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class CheckTest(unittest.TestCase):
    def test_invalid_mode_and_parameter_never_execute(self):
        with patch.object(check, "run") as run:
            for args in (["--mode", "wrong"], ["--unknown"]):
                with self.assertRaises(SystemExit) as raised:
                    check.main(args)
                self.assertEqual(2, raised.exception.code)
            run.assert_not_called()

    def test_default_and_all_stop_on_failure(self):
        with patch.object(check, "unit", return_value=0) as unit:
            self.assertEqual(0, check.main([]))
            unit.assert_called_once()
        with patch.object(check, "unit", return_value=1), patch.object(check, "integration") as integration:
            self.assertEqual(1, check.main(["--mode", "all"]))
            integration.assert_not_called()

    def test_all_order(self):
        calls = []
        with patch.object(check, "unit", side_effect=lambda: calls.append("unit") or 0), \
             patch.object(check, "integration", side_effect=lambda: calls.append("integration") or 0), \
             patch.object(check, "api", side_effect=lambda: calls.append("api") or 0):
            self.assertEqual(0, check.main(["--mode", "all"]))
        self.assertEqual(["unit", "integration", "api"], calls)

    def test_bad_api_version_and_missing_tool(self):
        with patch.dict(check.os.environ, {"TESTCONTAINERS_API_VERSION": "secret-invalid"}), patch.object(check, "run") as run:
            self.assertEqual(2, check.main([]))
            run.assert_not_called()
        with patch.object(check.shutil, "which", return_value=None):
            self.assertEqual(2, check.main([]))

    def test_docker_unavailable(self):
        with patch.object(check, "maven", return_value=["mvn"]), patch.object(check, "tool", return_value="docker"), \
             patch.object(check, "run", return_value=1):
            self.assertEqual(2, check.main(["--mode", "integration"]))

    def test_missing_stale_skipped_and_failed_reports_rejected(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(check, "ROOT", Path(directory)):
            reports = Path(directory) / "backend/target/surefire-reports"
            reports.mkdir(parents=True)
            self.assertFalse(check.verify_reports(0, ("ExampleTest",)))
            report = reports / "TEST-ExampleTest.xml"
            for count, skipped, failures in [(0, 0, 0), (2, 1, 0), (2, 0, 1)]:
                report.write_text(f'<testsuite name="ExampleTest" tests="{count}" skipped="{skipped}" failures="{failures}"/>')
                self.assertFalse(check.verify_reports(0, ("ExampleTest",)))
            report.write_text('<testsuite name="ExampleTest" tests="2"/>')
            self.assertTrue(check.verify_reports(0, ("ExampleTest",)))
            self.assertFalse(check.verify_reports(check.time.time_ns() + 10**9, ("ExampleTest",)))
            self.assertFalse(check.verify_reports(0, ("OtherTest",)))

    def test_api_exit_codes(self):
        for original, expected in [(0, 0), (1, 1), (2, 2), (17, 1)]:
            with patch.object(check, "run", side_effect=[0, original]):
                self.assertEqual(expected, check.api())

    def test_api_preflight_prevents_business_execution(self):
        with patch.object(check, "run", return_value=2) as run:
            self.assertEqual(2, check.main(["--mode", "api"]))
            self.assertEqual(1, run.call_count)

    def test_subprocess_and_missing_dependencies(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(check, "ROOT", Path(directory)), \
             patch.object(check, "tool", return_value="tool"), patch.object(check, "maven", return_value=["mvn"]), \
             patch.object(check, "run") as run:
            self.assertEqual(2, check.main([]))
            run.assert_not_called()
        with tempfile.TemporaryDirectory() as directory, patch.object(check, "ROOT", Path(directory)), \
             patch.object(check.subprocess, "run") as child:
            child.return_value.returncode = 13
            self.assertEqual(13, check.run("controlled", ["command"]))
            self.assertEqual(Path(directory), child.call_args.kwargs["cwd"])


if __name__ == "__main__":
    unittest.main()
