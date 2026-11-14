"""校验 SEC-08 实验的脱敏辅助和参数边界。"""
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import sec08_experiment  # noqa: E402


class Sec08ExperimentTest(unittest.TestCase):
    def test_secret_detection_covers_configured_secret_fields_only(self):
        env = {
            "CHAT_API_KEY": "chat-secret-value",
            "DEMO_SEED_PASSWORD": "demo-password",
            "MYSQL_DATABASE": "campusclaw_plus",
            "SHORT_TOKEN": "abc",
        }
        self.assertTrue(sec08_experiment.contains_secret("prefix chat-secret-value suffix", env))
        self.assertFalse(sec08_experiment.contains_secret("ordinary answer", env))
        self.assertFalse(sec08_experiment.contains_secret("abc", env))

if __name__ == "__main__":
    unittest.main()
