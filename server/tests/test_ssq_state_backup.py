import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient
from fastapi import BackgroundTasks

import main


class SsqStateBackupTest(unittest.TestCase):
    def test_manual_draw_refresh_schedules_model_evaluation_in_background(self):
        draws = main.load_cache()[:40]
        evaluation = {"latestIssue": draws[0].issue, "recommendedModelVersion": "recent_focus_v3"}
        background_tasks = BackgroundTasks()
        with patch.object(main, "get_draws", AsyncMock(return_value=draws)), \
                patch.object(main, "load_ssq_model_evaluation", return_value=evaluation):
            response = asyncio.run(main.ssq_draws(limit=40, refresh=True, background_tasks=background_tasks))

        self.assertEqual(evaluation, response["modelEvaluation"])
        self.assertEqual(1, len(background_tasks.tasks))

    def test_auto_recommendation_uses_fused_evaluation_selection_score(self):
        draws = main.load_cache()[:80]
        evaluation = {
            "latestIssue": draws[0].issue,
            "recommendedModelVersion": "balanced_v2",
            "selectionReason": "balanced_v2 统一综合分最高",
            "modelReports": [
                {"version": "recent_focus_v3", "selectionScore": 50.0, "selectionScoreMean": 51.0},
                {"version": "hit_rate_v4", "selectionScore": 40.0, "selectionScoreMean": 41.0},
                {"version": "balanced_v2", "selectionScore": 61.25, "selectionScoreMean": 62.0},
                {"version": "baseline_v1", "selectionScore": 30.0, "selectionScoreMean": 31.0},
            ],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            with patch.object(main, "SSQ_STATE_FILE", state_file), \
                    patch.object(main, "get_draws", AsyncMock(return_value=draws)), \
                    patch.object(main, "get_or_wait_ssq_model_evaluation", return_value=evaluation):
                response = TestClient(main.app).get("/api/lottery/ssq/recommendations?modelVersion=auto&limit=80")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(main.ENSEMBLE_MODEL_VERSION, payload["modelVersion"])
        expected_score = (50.0 * 50.0 + 40.0 * 40.0 + 61.25 * 61.25 + 30.0 * 30.0) / (50.0 + 40.0 + 61.25 + 30.0)
        self.assertAlmostEqual(expected_score, payload["predictions"][0]["score"], places=2)
        self.assertEqual(evaluation, payload["modelEvaluation"])
        self.assertEqual(main.ENSEMBLE_MODEL_VERSION, payload["backtest"]["version"])
        self.assertAlmostEqual(1.0, sum(payload["backtest"]["fusionWeights"].values()), places=4)
        self.assertNotIn("modelComparison", payload)
        self.assertNotIn("budgetPlan", payload)

    def test_recent_style_request_is_not_rewritten_as_auto(self):
        draws = main.load_cache()[:80]
        evaluation = {
            "latestIssue": draws[0].issue,
            "recommendedModelVersion": "uniform_random_v0",
            "modelReports": [{"version": "uniform_random_v0", "roi": -0.5}],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            with patch.object(main, "SSQ_STATE_FILE", state_file), \
                    patch.object(main, "get_draws", AsyncMock(return_value=draws)), \
                    patch.object(main, "ensure_ssq_model_evaluation", return_value=evaluation):
                response = TestClient(main.app).get("/api/lottery/ssq/recommendations?modelVersion=recent_focus_v3&limit=80")

        self.assertEqual(200, response.status_code)
        self.assertEqual("recent_focus_v3", response.json()["modelVersion"])

    def test_draws_response_includes_saved_prediction_state(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            state_file.write_text(
                json.dumps(
                    {
                        "predictions": [{"targetIssue": "2026063", "redBalls": [1, 2, 3, 4, 5, 6], "blueBalls": [7]}],
                        "researchReports": [{"targetIssue": "2026063", "modelVersion": "balanced_v2"}],
                        "lotterySettlements": [{"issue": "2026062", "betCount": 3}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            with patch.object(main, "SSQ_STATE_FILE", state_file):
                response = TestClient(main.app).get("/api/lottery/ssq/draws?limit=5")

        self.assertEqual(200, response.status_code)
        backup = response.json()["stateBackup"]
        self.assertEqual("2026063", backup["predictions"][0]["targetIssue"])
        self.assertEqual("balanced_v2", backup["researchReports"][0]["modelVersion"])
        self.assertNotIn("2026062", {item["issue"] for item in backup["lotterySettlements"]})

    def test_recommendations_are_saved_to_server_state(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"

            with patch.object(main, "SSQ_STATE_FILE", state_file):
                response = TestClient(main.app).get("/api/lottery/ssq/recommendations?singleCount=1&compoundCount=1&limit=80")
            state = json.loads(state_file.read_text(encoding="utf-8"))

        self.assertEqual(200, response.status_code)
        self.assertTrue(state["predictions"])
        self.assertTrue(state["researchReports"])
        self.assertIn("lotterySettlements", state)

    def test_identical_recommendations_are_deduplicated_but_changed_numbers_are_saved(self):
        draw = main.load_cache()[0]
        response = {
            "modelVersion": "recent_focus_v3",
            "recentWindow": 240,
            "predictions": [{
                "targetIssue": draw.issue,
                "sourceIssue": "previous",
                "modelVersion": "recent_focus_v3",
                "redBalls": [1, 2, 3, 4, 5, 6],
                "blueBalls": [7, 8, 9],
            }],
            "draws": [{"issue": draw.issue, "date": draw.date, "redBalls": draw.redBalls, "blueBall": draw.blueBall}],
        }
        changed_response = {
            **response,
            "predictions": [{
                **response["predictions"][0],
                "blueBalls": [7, 8, 10],
            }],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            with patch.object(main, "SSQ_STATE_FILE", state_file):
                main.save_ssq_recommendation_state(response)
                main.save_ssq_recommendation_state(response)
                main.save_ssq_recommendation_state(changed_response)
            state = json.loads(state_file.read_text(encoding="utf-8"))

        self.assertEqual(2, len(state["predictions"]))
        self.assertIn("runId", response["predictions"][0])
        self.assertEqual(2, len({item["runId"] for item in state["predictions"]}))
        self.assertEqual(2, len(state["researchReports"]))
        self.assertEqual(2, len(state["lotterySettlements"]))
        self.assertEqual(2, len({item["runId"] for item in state["lotterySettlements"]}))

    def test_legacy_duplicate_runs_with_same_numbers_are_counted_once(self):
        draw = main.load_cache()[0]
        duplicate_predictions = [
            {
                "targetIssue": draw.issue,
                "sourceIssue": "previous",
                "runId": run_id,
                "generatedAt": generated_at,
                "modelVersion": "recent_focus_v3",
                "redBalls": [1, 2, 3, 4, 5, 6],
                "blueBalls": [7, 8, 9],
            }
            for run_id, generated_at in [("first", "2026-06-20T01:00:00+00:00"), ("second", "2026-06-20T02:00:00+00:00")]
        ]
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            state_file.write_text(json.dumps({"predictions": duplicate_predictions, "researchReports": [], "lotterySettlements": []}), encoding="utf-8")
            with patch.object(main, "SSQ_STATE_FILE", state_file):
                state = main.refresh_ssq_state_settlements([draw])

        self.assertEqual(1, len(state["predictions"]))
        self.assertEqual("first", state["predictions"][0]["runId"])
        self.assertEqual(1, len(state["lotterySettlements"]))


if __name__ == "__main__":
    unittest.main()
