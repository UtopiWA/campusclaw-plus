import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

SPEC = importlib.util.spec_from_file_location("osv_scan", Path(__file__).resolve().parents[1] / "osv_scan.py")
SCAN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SCAN)


class OsvScanTest(unittest.TestCase):
    def test_query_failure_is_not_a_clean_result(self):
        with patch.object(SCAN.urllib.request, "urlopen", side_effect=urllib.error.URLError("offline")), \
                patch.object(SCAN.time, "sleep"):
            with self.assertRaises(SCAN.ScanIncomplete):
                SCAN.query("g", "a", "1")

    def test_valid_empty_response_is_clean(self):
        with patch.object(SCAN.urllib.request, "urlopen", return_value=io.BytesIO(b'{}')):
            self.assertEqual([], SCAN.query("g", "a", "1"))

    def run_scan(self, query):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(SCAN, "LOGS", Path(temporary)), \
                patch.object(SCAN, "dependencies", return_value=[("g", "a", "1")]), \
                patch.object(SCAN, "query", side_effect=query):
            result = SCAN.main()
            report = json.loads((Path(temporary) / "java-osv.json").read_text())
            return result, report

    def test_partial_scan_cannot_pass(self):
        code, report = self.run_scan(SCAN.ScanIncomplete("offline"))
        self.assertEqual(2, code)
        self.assertEqual(["g:a:1"], report["queryFailures"])

    def test_critical_is_not_suppressed(self):
        code, _ = self.run_scan(lambda *args: [{"id": "sample", "database_specific": {"severity": "CRITICAL"}}])
        self.assertEqual(1, code)

    def test_unknown_vector_requires_review(self):
        code, _ = self.run_scan(lambda *args: [{"id": "sample", "severity": [{"type": "CVSS_V3", "score": "CVSS:3.1/AV:N"}]}])
        self.assertEqual(2, code)

    def test_numeric_score_is_classified(self):
        self.assertEqual("HIGH", SCAN.severity({"severity": [{"type": "CVSS_V3", "score": "7.5"}]}))


if __name__ == "__main__":
    unittest.main()
