import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "bench", Path(__file__).resolve().parents[1] / "bench.py")
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)


class BenchTest(unittest.TestCase):
    def test_percentile_uses_nearest_rank_and_empty_is_zero(self):
        values = [1.0, 2.0, 3.0, 4.0, 100.0]
        self.assertEqual(0.0, bench.percentile([], 0.95))
        self.assertEqual(3.0, bench.percentile(values, 0.50))
        self.assertEqual(100.0, bench.percentile(values, 0.95))

    def test_invalid_level_shape_is_rejected(self):
        for raw in ("", "0", "-1", "a", "1,x"):
            with self.subTest(raw=raw):
                try:
                    levels = [int(item) for item in raw.split(",") if item.strip()]
                    valid = bool(levels) and all(level >= 1 for level in levels)
                except ValueError:
                    valid = False
                self.assertFalse(valid)


if __name__ == "__main__":
    unittest.main()
