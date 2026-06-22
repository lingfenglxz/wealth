import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

import main


class SsqStateBackupTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
