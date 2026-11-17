import json
import sys
import unittest
from copy import deepcopy
from datetime import date
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import security_exceptions  # noqa: E402


BASE = {
    "schemaVersion": 1,
    "approved": True,
    "approvedBy": "user-approved test exception",
    "approvedAt": "2026-11-18",
    "expiresAt": "2099-12-31",
    "compensatingControls": ["dev-only dependency", "raw audit retained"],
    "advisories": [{
        "id": "GHSA-fc5q-39xc-vjpf",
        "url": "https://github.com/advisories/GHSA-fc5q-39xc-vjpf",
        "severities": ["high", "critical"],
        "packages": ["picomatch", "fdir", "tinyglobby", "vite", "vitest"],
    }],
}

AUDIT = {
    "vulnerabilities": {
        "picomatch": {
            "severity": "high",
            "via": [{"url": "https://github.com/advisories/GHSA-fc5q-39xc-vjpf"}],
        },
        "fdir": {"severity": "high", "via": ["picomatch"]},
        "tinyglobby": {"severity": "high", "via": ["picomatch"]},
        "vite": {"severity": "high", "via": ["picomatch"]},
        "vitest": {"severity": "high", "via": ["picomatch"]},
        "moderate-example": {"severity": "moderate", "via": ["somewhere"]},
    }
}


class SecurityExceptionsTest(unittest.TestCase):
    def test_approved_exception_covers_all_high_and_keeps_moderate_out(self):
        accepted, problems, applied = security_exceptions.evaluate(
            json.dumps(AUDIT), BASE, date(2026, 11, 18))
        self.assertTrue(accepted, problems)
        self.assertEqual([], problems)
        self.assertEqual(["GHSA-fc5q-39xc-vjpf"], applied)

    def test_uncovered_package_or_new_advisory_fails(self):
        audit = deepcopy(AUDIT)
        audit["vulnerabilities"]["new-package"] = {
            "severity": "high", "via": [{"url": "https://github.com/advisories/GHSA-new-0000"}]}
        accepted, problems, _ = security_exceptions.evaluate(
            json.dumps(audit), BASE, date(2026, 11, 18))
        self.assertFalse(accepted)
        self.assertTrue(any("new-package" in item for item in problems))
        self.assertTrue(any("GHSA-new-0000" in item for item in problems))

    def test_unapproved_expired_and_malformed_exceptions_fail(self):
        for mutate in (
            lambda item: item.update({"approved": False}),
            lambda item: item.update({"expiresAt": "2026-01-01"}),
            lambda item: item.pop("compensatingControls"),
            lambda item: item["advisories"][0].update({"packages": []}),
        ):
            with self.subTest(mutate=mutate):
                document = deepcopy(BASE)
                mutate(document)
                accepted, problems, _ = security_exceptions.evaluate(
                    json.dumps(AUDIT), document, date(2026, 11, 18))
                self.assertFalse(accepted)
                self.assertTrue(problems)

    def test_clean_audit_needs_no_exception(self):
        accepted, problems, applied = security_exceptions.evaluate(
            json.dumps({"vulnerabilities": {}}), BASE, date(2026, 11, 18))
        self.assertTrue(accepted)
        self.assertEqual([], problems)
        self.assertEqual([], applied)

    def test_malformed_audit_json_fails(self):
        accepted, problems, _ = security_exceptions.evaluate(
            "not json", BASE, date(2026, 11, 18))
        self.assertFalse(accepted)
        self.assertTrue(problems)


if __name__ == "__main__":
    unittest.main()
