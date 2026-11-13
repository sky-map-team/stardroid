import unittest
from datetime import date

from satellite_alerts import evaluate

TODAY = date(2026, 11, 12)


def week(n):
    return [(f"2026-11-{d:02d}", "satellite_fetch_failed", "503", n) for d in range(4, 12)]


class EvaluateTest(unittest.TestCase):
    def test_spike_over_factor_and_floor_alerts_with_codes(self):
        rows = week(100) + [("2026-11-12", "satellite_fetch_failed", "503", 900),
                            ("2026-11-12", "satellite_fetch_failed", "403", 100)]
        alerts, _ = evaluate(rows, TODAY)
        self.assertEqual(len(alerts), 1)
        self.assertIn("503: 900, 403: 100", alerts[0])

    def test_below_floor_is_quiet(self):
        rows = week(1) + [("2026-11-12", "satellite_fetch_failed", "503", 100)]
        self.assertEqual(evaluate(rows, TODAY)[0], [])

    def test_high_but_steady_is_quiet(self):
        self.assertEqual(evaluate(week(800), TODAY)[0], [])

    def test_volume_over_budget_alerts(self):
        rows = [("2026-11-12", "satellite_fetch_ok", "", 50)]
        self.assertEqual(len(evaluate(rows, TODAY, budget=4000)[0]), 1)
        self.assertEqual(evaluate(rows, TODAY, budget=6000)[0], [])

    def test_no_budget_means_no_volume_alert(self):
        rows = [("2026-11-12", "satellite_fetch_ok", "", 5000)]
        self.assertEqual(evaluate(rows, TODAY)[0], [])


if __name__ == "__main__":
    unittest.main()
