import json
import unittest
from collections import Counter
from pathlib import Path

from fastapi.testclient import TestClient

import main


class SsqFusionModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.draws = [
            main.SsqDraw(**item)
            for item in json.loads(Path("data/ssq_draws.json").read_text(encoding="utf-8"))
        ]

    def test_fusion_weights_are_normalized(self):
        reports = [
            {"version": "recent_focus_v3", "selectionScore": 15.78},
            {"version": "hit_rate_v4", "selectionScore": 15.48},
            {"version": "balanced_v2", "selectionScore": 15.54},
            {"version": "baseline_v1", "selectionScore": 15.20},
            {"version": "uniform_random_v0", "selectionScore": 99.0},
        ]

        weights = main.compute_fusion_weights(reports)

        self.assertEqual(set(main.MODEL_CONFIGS), set(weights))
        self.assertAlmostEqual(1.0, sum(weights.values()), places=6)
        self.assertGreater(weights["recent_focus_v3"], weights["baseline_v1"])

    def test_fusion_weights_fall_back_to_uniform_without_reports(self):
        weights = main.compute_fusion_weights([])

        self.assertEqual(set(main.MODEL_CONFIGS), set(weights))
        self.assertAlmostEqual(1.0, sum(weights.values()), places=6)
        self.assertEqual(len({round(w, 6) for w in weights.values()}), 1)

    def test_fused_breakdown_stays_within_model_score_range(self):
        ordered = list(reversed(self.draws[:200]))
        recent = ordered[-60:]
        red_freq = Counter(red for draw in ordered for red in draw.redBalls)
        red_miss = main.omission(self.draws[:200], range(1, 34), lambda draw, number: number in draw.redBalls)
        red_recent = main.weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
        weights = main.uniform_fusion_weights()

        fused = main.fuse_score_breakdowns(red_freq, red_recent, red_miss, range(1, 34), weights)

        self.assertEqual(set(range(1, 34)), set(fused))
        per_model = {
            version: main.score_breakdown(red_freq, red_recent, red_miss, range(1, 34), version)
            for version in main.MODEL_CONFIGS
        }
        for number, detail in fused.items():
            model_totals = [per_model[version][number]["total"] for version in main.MODEL_CONFIGS]
            self.assertGreaterEqual(detail["total"], min(model_totals) - 1e-6)
            self.assertLessEqual(detail["total"], max(model_totals) + 1e-6)
            self.assertIn("fullCount", detail)
            self.assertIn("missCount", detail)

    def test_fused_selection_score_is_weighted_average(self):
        reports = [
            {"version": "recent_focus_v3", "selectionScore": 20.0},
            {"version": "hit_rate_v4", "selectionScore": 10.0},
            {"version": "balanced_v2", "selectionScore": 0.0},
            {"version": "baseline_v1", "selectionScore": 0.0},
        ]
        weights = {"recent_focus_v3": 0.5, "hit_rate_v4": 0.5, "balanced_v2": 0.0, "baseline_v1": 0.0}

        score = main.compute_fused_selection_score(reports, weights)

        self.assertAlmostEqual(15.0, score, places=6)

    def test_auto_recommendation_uses_ensemble_fusion(self):
        response = TestClient(main.app).get("/api/lottery/ssq/recommendations?limit=80")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(main.ENSEMBLE_MODEL_VERSION, payload["modelVersion"])
        prediction = payload["predictions"][0]
        self.assertEqual(main.ENSEMBLE_MODEL_VERSION, prediction["modelVersion"])
        self.assertIn("多模型融合", prediction["analysisSummary"])
        backtest = payload["backtest"]
        self.assertEqual(main.ENSEMBLE_MODEL_VERSION, backtest["version"])
        self.assertAlmostEqual(1.0, sum(backtest["fusionWeights"].values()), places=4)
        reports = payload["modelEvaluation"].get("modelReports") or []
        formal_scores = [
            float(item["selectionScore"])
            for item in reports
            if item.get("version") in main.MODEL_CONFIGS
        ]
        if formal_scores:
            self.assertGreaterEqual(prediction["score"], min(formal_scores) - 0.01)
            self.assertLessEqual(prediction["score"], max(formal_scores) + 0.01)

    def test_specific_model_request_keeps_single_model(self):
        response = TestClient(main.app).get(
            "/api/lottery/ssq/recommendations?limit=80&modelVersion=hit_rate_v4"
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("hit_rate_v4", payload["modelVersion"])
        self.assertEqual("hit_rate_v4", payload["predictions"][0]["modelVersion"])


if __name__ == "__main__":
    unittest.main()
