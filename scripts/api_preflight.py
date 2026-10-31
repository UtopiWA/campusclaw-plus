"""复用接口套件的只读预检；不创建业务数据，不输出配置值。"""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "apitest"))


def main() -> int:
    try:
        from run_all import TestConfig, TestContext, _check_environment
        _check_environment(TestContext(TestConfig.load()))
    except Exception:
        print("API environment unavailable: check application health, test config and MySQL")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
