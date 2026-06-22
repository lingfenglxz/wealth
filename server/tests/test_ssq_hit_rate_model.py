import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

import main


class SsqHitRateModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.draws = [
            main.SsqDraw(**item)
            for item in json.loads(Path("data/ssq_draws.json").read_text(encoding="utf-8"))
        ]

    def test_default_recommendation_is_one_six_plus_three_group(self):
        self.assertIn("hit_rate_v4", main.MODEL_CONFIGS)

        response = TestClient(main.app).get("/api/lottery/ssq/recommendations?limit=80")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("recent_focus_v3", payload["modelVersion"])
        self.assertEqual(240, payload["recentWindow"])
        self.assertEqual(1, len(payload["predictions"]))
        self.assertEqual(6, len(payload["predictions"][0]["redBalls"]))
        self.assertEqual(3, len(payload["predictions"][0]["blueBalls"]))

    def test_single_shape_request_returns_one_single_group(self):
        response = TestClient(main.app).get(
            "/api/lottery/ssq/recommendations?redCount=6&blueCount=1&limit=80"
        )

        self.assertEqual(200, response.status_code)
        predictions = response.json()["predictions"]
        self.assertEqual(1, len(predictions))
        self.assertEqual([6], [len(item["redBalls"]) for item in predictions])
        self.assertEqual([1], [len(item["blueBalls"]) for item in predictions])
        self.assertTrue(predictions[0]["note"].startswith("单式6+1"))

    def test_backtest_uses_requested_six_plus_three_plan(self):
        report = main.backtest_model(
            self.draws,
            "recent_focus_v3",
            recent_window=240,
            sample_limit=5,
            single_count=0,
            compound_count=1,
            red_count=6,
            blue_count=3,
        )

        self.assertEqual(5, report["issueCount"])
        self.assertEqual(3.0, report["averageBetCount"])
        self.assertIn("prizeHitRate", report)
        self.assertEqual(30.0, report["investedAmount"])
        self.assertIn("simulatedPrizeAmount", report)
        self.assertIn("roi", report)
        self.assertEqual({"first", "second", "third", "fourth", "fifth", "sixth"}, set(report["tierCounts"]))

    def test_uniform_random_control_uses_equal_number_scores(self):
        scores = main.score_numbers(
            full_frequency=main.Counter({1: 99, 2: 1}),
            recent_frequency={1: 9.0, 2: 0.1},
            misses={1: 0, 2: 20},
            numbers=range(1, 3),
            model_version="uniform_random_v0",
        )

        self.assertEqual({1: 1.0, 2: 1.0}, scores)

    def test_model_evaluation_aggregates_multiple_historical_folds(self):
        report = main.build_ssq_model_evaluation(
            self.draws,
            recent_window=60,
            sample_limit=5,
            fold_count=2,
        )

        self.assertEqual(self.draws[0].issue, report["latestIssue"])
        self.assertEqual(2, report["foldCount"])
        self.assertIn("uniform_random_v0", {item["version"] for item in report["modelReports"]})
        self.assertTrue(all(item["issueCount"] == 10 for item in report["modelReports"]))
        self.assertIn("recommendedModelVersion", report)

    def test_model_evaluation_cache_reuses_same_latest_issue(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_file = Path(temp_dir) / "ssq_model_evaluation.json"
            cached_report = {
                "evaluationVersion": "ssq-evaluation-v1",
                "latestIssue": self.draws[0].issue,
                "recentWindow": 60,
                "sampleLimit": 5,
                "foldCount": 2,
                "singleCount": 0,
                "compoundCount": 1,
                "redCount": 6,
                "blueCount": 3,
                "marker": "built",
            }
            with patch.object(main, "SSQ_MODEL_EVALUATION_FILE", cache_file), \
                    patch.object(main, "build_ssq_model_evaluation", return_value=cached_report) as build:
                first = main.ensure_ssq_model_evaluation(self.draws, recent_window=60, sample_limit=5, fold_count=2)
                second = main.ensure_ssq_model_evaluation(self.draws, recent_window=60, sample_limit=5, fold_count=2)
                changed = [main.SsqDraw(issue="2099999", date="2099-01-01", redBalls=[1, 2, 3, 4, 5, 6], blueBall=7)] + self.draws
                third = main.ensure_ssq_model_evaluation(changed, recent_window=60, sample_limit=5, fold_count=2)

        self.assertEqual(cached_report, first)
        self.assertEqual(cached_report, second)
        self.assertEqual(cached_report, third)
        self.assertEqual(2, build.call_count)


if __name__ == "__main__":
    unittest.main()
