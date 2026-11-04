"""独立团队部署的配置选择不能误读旧项目的连接参数。"""

import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "api_config_under_test", Path(__file__).resolve().parents[2] / "apitest/common/config.py")
CONFIG = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CONFIG
SPEC.loader.exec_module(CONFIG)


class TeamApiConfigTest(unittest.TestCase):
    def test_selected_config_supplies_team_ports_and_credentials(self):
        with tempfile.TemporaryDirectory() as folder:
            env_file = Path(folder) / "team.env"
            env_file.write_text(
                "APP_PORT=18080\nMYSQL_HOST_PORT=13306\nMYSQL_DATABASE=team_synthetic\n"
                "MYSQL_USER=team_user\nMYSQL_PASSWORD=synthetic-db\n"
                "MCP_AUTH_TOKEN=synthetic-token\nDEMO_SEED_PASSWORD=synthetic-login\n",
                encoding="utf-8")
            with patch.dict(os.environ, {"APITEST_ENV_FILE": str(env_file)}, clear=True):
                config = CONFIG.TestConfig.load()
            self.assertEqual(config.base_url, "http://127.0.0.1:18080")
            self.assertEqual(config.db_port, 13306)
            self.assertEqual(config.db_name, "team_synthetic")
            self.assertEqual(config.teacher_password, "synthetic-login")
            with patch.dict(os.environ, {"APITEST_ENV_FILE": str(env_file),
                                        "APITEST_BASE_URL": "http://127.0.0.1:28080/"}, clear=True):
                self.assertEqual(CONFIG.TestConfig.load().base_url, "http://127.0.0.1:28080")

    def test_missing_selected_file_does_not_fall_back_to_private_default(self):
        with tempfile.TemporaryDirectory() as folder:
            with patch.dict(os.environ, {"APITEST_ENV_FILE": str(Path(folder) / "missing.env")}, clear=True):
                with self.assertRaisesRegex(ValueError, "缺少接口测试配置"):
                    CONFIG.TestConfig.load()
