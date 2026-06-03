from fastapi.testclient import TestClient
import unittest

from main import app


client = TestClient(app)


class SportsFootballApiTest(unittest.TestCase):
    def test_football_matches_fall_back_to_worldcup_cache(self):
        response = client.get("/api/lottery/sports/football/matches")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn(payload["sourceStatus"], {"official", "cache", "fallback"})
        self.assertGreaterEqual(payload["count"], 1)
        first = payload["matches"][0]
        self.assertTrue(first["matchId"])
        self.assertTrue(first["homeTeam"])
        self.assertTrue(first["awayTeam"])
        self.assertIn("had", first["pools"])

    def test_football_recommendations_include_supported_play_types(self):
        response = client.get("/api/lottery/sports/football/recommendations?playType=all")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn(payload["sourceStatus"], {"official", "cache", "fallback"})
        self.assertTrue(payload["recommendations"])
        play_types = {item["playType"] for item in payload["recommendations"]}
        self.assertTrue({"had", "hhad", "crs", "ttg", "hafu"}.issubset(play_types))
        first = payload["recommendations"][0]
        self.assertGreaterEqual(first["confidence"], 0.0)
        self.assertLessEqual(first["confidence"], 1.0)
        self.assertTrue(first["selection"])
        self.assertTrue(first["reasons"])

    def test_stock_market_api_is_removed(self):
        response = client.get("/api/market/stocks/000001/daily")

        self.assertEqual(404, response.status_code)


if __name__ == "__main__":
    unittest.main()
