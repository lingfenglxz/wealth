import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

import main


class SsqStateBackupTest(unittest.TestCase):
    def test_manual_draw_refresh_builds_model_evaluation(self):
        draws = main.load_cache()[:40]
        evaluation = {"latestIssue": draws[0].issue, "recommendedModelVersion": "recent_focus_v3"}
        with patch.object(main, "get_draws", AsyncMock(return_value=draws)), \
                patch.object(main, "ensure_ssq_model_evaluation", return_value=evaluation) as ensure:
            response = TestClient(main.app).get("/api/lottery/ssq/draws?limit=40&refresh=true")

        self.assertEqual(200, response.status_code)
        self.assertEqual(evaluation, response.json()["modelEvaluation"])
        ensure.assert_called_once()

    def test_auto_recommendation_uses_cached_recommended_model(self):
        draws = main.load_cache()[:80]
        evaluation = {
            "latestIssue": draws[0].issue,
            "recommendedModelVersion": "balanced_v2",
            "modelReports": [{"version": "balanced_v2", "roi": -0.5}],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            state_file = Path(temp_dir) / "ssq_state.json"
            with patch.object(main, "SSQ_STATE_FILE", state_file), \
                    patch.object(main, "get_draws", AsyncMock(return_value=draws)), \
                    patch.object(main, "ensure_ssq_model_evaluation", return_value=evaluation) as ensure:
                response = TestClient(main.app).get("/api/lottery/ssq/recommendations?modelVersion=auto&limit=80")

        self.assertEqual(200, response.status_code)
        self.assertEqual("balanced_v2", response.json()["modelVersion"])
        self.assertEqual(evaluation, response.json()["modelEvaluation"])
        self.assertEqual(evaluation["modelReports"], response.json()["modelComparison"])
        ensure.assert_called_once()

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
        self.assertIn("2026062", {item["issue"] for item in backup["lotterySettlements"]})

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
        self.assertEqual(2, len({item["runId"] for item in state["predictions"]}))
        self.assertEqual(2, len(state["researchReports"]))
        self.assertEqual(2, len(state["lotterySettlements"]))
        self.assertEqual(2, len({item["runId"] for item in state["lotterySettlements"]}))


if __name__ == "__main__":
    unittest.main()
