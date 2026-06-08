import json
from pathlib import Path
import tempfile
from unittest.mock import patch

from fastapi.testclient import TestClient
import unittest

import main
from main import app


client = TestClient(app)


class SportsFootballApiTest(unittest.TestCase):
    def test_football_matches_fall_back_to_worldcup_cache(self):
        response = client.get("/api/lottery/sports/football/matches")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn(payload["sourceStatus"], {"official", "import", "cache", "fallback"})
        self.assertGreaterEqual(payload["count"], 1)
        first = payload["matches"][0]
        self.assertTrue(first["matchId"])
        self.assertTrue(first["homeTeam"])
        self.assertTrue(first["awayTeam"])
        self.assertIn("had", first["pools"])

    def test_worldcup_schedule_fallback_contains_full_tournament(self):
        schedule = main.load_worldcup_schedule_fallback()

        self.assertEqual(104, len(schedule))
        self.assertEqual("001", schedule[0].matchNum)
        self.assertEqual("104", schedule[-1].matchNum)
        self.assertEqual("Final", schedule[-1].phase)
        self.assertTrue(schedule[-1].stadium)
        self.assertTrue(schedule[-1].city)

    def test_openfootball_schedule_converts_kickoff_to_beijing_time(self):
        payload = {
            "matches": [
                {
                    "round": "Matchday 1",
                    "date": "2026-06-11",
                    "time": "13:00 UTC-6",
                    "team1": "Mexico",
                    "team2": "South Africa",
                    "group": "Group A",
                    "ground": "Mexico City",
                }
            ]
        }

        matches = main.parse_openfootball_worldcup(payload)

        self.assertEqual(1, len(matches))
        self.assertEqual("2026-06-12T03:00:00+08:00", matches[0].kickoffTime)
        self.assertEqual("Mexico City", matches[0].city)

    def test_openfootball_scores_parse_to_results(self):
        payload = {
            "matches": [
                {
                    "date": "2026-06-11",
                    "time": "13:00 UTC-6",
                    "team1": "Mexico",
                    "team2": "South Africa",
                    "score": {"ft": [2, 1], "ht": [1, 0]},
                }
            ]
        }

        results = main.parse_openfootball_results(payload)

        self.assertEqual("wc2026-001", results[0]["matchId"])
        self.assertEqual("2:1", results[0]["fullTimeScore"])
        self.assertEqual("1:0", results[0]["halfTimeScore"])

    def test_group_standings_are_computed_from_results(self):
        matches = [
            main.FootballMatch(
                matchId="wc2026-001",
                matchNum="001",
                leagueName="FIFA World Cup 2026",
                phase="Group A",
                kickoffTime="2026-06-12T03:00:00+08:00",
                homeTeam="Mexico",
                awayTeam="South Africa",
                neutralVenue=False,
                handicap=0,
                teamStrength={"home": 0.5, "away": 0.5},
                pools={},
                stadium="Mexico City Stadium",
                city="Mexico City",
                source="openfootball",
                updatedAt="2026-06-04",
            )
        ]
        results = [{"matchId": "wc2026-001", "fullTimeScore": "2:1"}]

        standings = main.build_football_standings(matches, results)

        self.assertEqual("Mexico", standings[0]["team"])
        self.assertEqual(3, standings[0]["points"])
        self.assertEqual("South Africa", standings[1]["team"])

    def test_fifa_rankings_page_parser_builds_team_ratings(self):
        html = """
        <script>{"rank":1,"name":"France","totalPoints":1889.32}</script>
        <script>{"rank":2,"name":"Spain","totalPoints":1854.64}</script>
        """

        ratings = main.parse_fifa_rankings_page(html)

        self.assertEqual(1, ratings["France"]["rank"])
        self.assertGreater(ratings["France"]["strength"], ratings["Spain"]["strength"])

    def test_poisson_match_model_uses_team_strength(self):
        match = main.FootballMatch(
            matchId="wc2026-001",
            matchNum="001",
            leagueName="FIFA World Cup 2026",
            phase="Group A",
            kickoffTime="2026-06-12T03:00:00+08:00",
            homeTeam="France",
            awayTeam="New Zealand",
            neutralVenue=True,
            handicap=0,
            teamStrength={"home": 0.96, "away": 0.50},
            pools={"had": {"H": 1.6, "D": 3.6, "A": 5.5}},
            stadium="",
            city="",
            source="test",
            updatedAt="2026-06-04",
        )

        profile = main.build_match_model_profile(match)

        self.assertEqual("poisson_v1", profile["modelName"])
        self.assertGreater(profile["expectedGoals"]["home"], profile["expectedGoals"]["away"])
        self.assertGreater(profile["playProbabilities"]["had"]["H"], profile["playProbabilities"]["had"]["A"])
        self.assertAlmostEqual(1.0, sum(profile["playProbabilities"]["had"].values()), places=3)

    def test_recommendations_include_poisson_model_fields(self):
        match = main.FootballMatch(
            matchId="wc2026-001",
            matchNum="001",
            leagueName="FIFA World Cup 2026",
            phase="Group A",
            kickoffTime="2026-06-12T03:00:00+08:00",
            homeTeam="France",
            awayTeam="New Zealand",
            neutralVenue=True,
            handicap=0,
            teamStrength={"home": 0.96, "away": 0.50},
            pools={"had": {"H": 1.6, "D": 3.6, "A": 5.5}},
            stadium="",
            city="",
            source="test",
            updatedAt="2026-06-04",
        )

        recommendation = main.build_football_recommendations([match], ["had"], 1)[0]

        self.assertEqual("poisson_v1", recommendation["modelName"])
        self.assertIn("expectedGoals", recommendation)
        self.assertGreater(recommendation["modelProbability"], 0)

    def test_match_facts_adjust_expected_goals(self):
        match = main.FootballMatch(
            matchId="wc2026-001",
            matchNum="001",
            leagueName="FIFA World Cup 2026",
            phase="Group A",
            kickoffTime="2026-06-12T03:00:00+08:00",
            homeTeam="Mexico",
            awayTeam="South Africa",
            neutralVenue=False,
            handicap=0,
            teamStrength={"home": 0.76, "away": 0.62},
            pools={"had": {"H": 1.6, "D": 3.6, "A": 5.5}},
            stadium="Mexico City Stadium",
            city="Mexico City",
            source="test",
            updatedAt="2026-06-04",
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            facts_file = Path(temp_dir) / "football_match_facts.json"
            facts_file.write_text(
                json.dumps(
                    {
                        "matches": [
                            {
                                "matchId": "wc2026-001",
                                "homeUnavailableImpact": 0.30,
                                "awayUnavailableImpact": 0.05,
                                "totalGoalsMultiplier": 0.90,
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            empty_import_dir = Path(temp_dir) / "imports"
            empty_import_dir.mkdir()
            with patch.object(main, "IMPORT_DIR", empty_import_dir), patch.object(main, "FOOTBALL_MATCH_FACTS_FILE", facts_file):
                adjusted = main.build_match_model_profile(match)
            with patch.object(main, "IMPORT_DIR", empty_import_dir), patch.object(main, "FOOTBALL_MATCH_FACTS_FILE", Path(temp_dir) / "missing.json"):
                baseline = main.build_match_model_profile(match)

        self.assertLess(adjusted["expectedGoals"]["home"], baseline["expectedGoals"]["home"])
        self.assertLess(adjusted["expectedGoals"]["away"], baseline["expectedGoals"]["away"])

    def test_schedule_merge_keeps_odds_and_adds_schedule_only_matches(self):
        schedule = [
            main.FootballMatch(
                matchId="wc2026-001",
                matchNum="001",
                leagueName="FIFA World Cup 2026",
                phase="Group A",
                kickoffTime="2026-06-11T15:00:00-04:00",
                homeTeam="Mexico",
                awayTeam="South Africa",
                neutralVenue=False,
                handicap=0,
                teamStrength={"home": 0.5, "away": 0.5},
                pools={},
                stadium="Estadio Azteca",
                city="Mexico City",
                source="schedule",
                updatedAt="2026-06-04",
            ),
            main.FootballMatch(
                matchId="wc2026-002",
                matchNum="002",
                leagueName="FIFA World Cup 2026",
                phase="Group A",
                kickoffTime="2026-06-11T22:00:00-04:00",
                homeTeam="Korea Republic",
                awayTeam="Czechia",
                neutralVenue=True,
                handicap=0,
                teamStrength={"home": 0.5, "away": 0.5},
                pools={},
                stadium="Estadio Akron",
                city="Guadalajara",
                source="schedule",
                updatedAt="2026-06-04",
            ),
        ]
        odds = [
            main.FootballMatch(
                matchId="official-1",
                matchNum="001",
                leagueName="竞彩足球",
                phase="竞彩赛事",
                kickoffTime="2026-06-11T15:00:00-04:00",
                homeTeam="Mexico",
                awayTeam="South Africa",
                neutralVenue=False,
                handicap=0,
                teamStrength={"home": 0.7, "away": 0.6},
                pools={"had": {"H": 1.8, "D": 3.2, "A": 4.5}},
                stadium="",
                city="",
                source="official",
                updatedAt="2026-06-04",
            )
        ]

        merged = main.merge_schedule_and_odds(schedule, odds)

        self.assertEqual(2, len(merged))
        self.assertIn("had", merged[0].pools)
        self.assertEqual("Estadio Azteca", merged[0].stadium)
        self.assertEqual({}, merged[1].pools)

    def test_official_non_worldcup_odds_are_not_added_to_worldcup_schedule(self):
        schedule = main.build_static_worldcup_schedule()[:1]
        official = [
            main.FootballMatch(
                matchId="sporttery-current",
                matchNum="周五001",
                leagueName="竞彩足球",
                phase="竞彩赛事",
                kickoffTime="2026-06-05T18:00:00+08:00",
                homeTeam="Club A",
                awayTeam="Club B",
                neutralVenue=False,
                handicap=0,
                teamStrength={"home": 0.5, "away": 0.5},
                pools={"had": {"H": 1.8, "D": 3.2, "A": 4.5}},
                stadium="",
                city="",
                source="official",
                updatedAt="2026-06-04",
            )
        ]

        merged = main.merge_schedule_and_odds(schedule, official, include_extra=False)

        self.assertEqual(1, len(merged))
        self.assertEqual("wc2026-001", merged[0].matchId)

    def test_football_recommendations_include_supported_play_types(self):
        response = client.get("/api/lottery/sports/football/recommendations?playType=all")

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn(payload["sourceStatus"], {"official", "import", "cache", "fallback"})
        self.assertTrue(payload["recommendations"])
        play_types = {item["playType"] for item in payload["recommendations"]}
        self.assertTrue({"had", "hhad", "crs", "ttg", "hafu"}.issubset(play_types))
        first = payload["recommendations"][0]
        self.assertGreaterEqual(first["confidence"], 0.0)
        self.assertLessEqual(first["confidence"], 1.0)
        self.assertTrue(first["selection"])
        self.assertTrue(first["reasons"])
        self.assertIn("fairProbability", first)
        self.assertIn("modelProbability", first)
        self.assertIn("edge", first)
        self.assertIn("dataQuality", first)

    def test_football_recommendations_save_snapshots_and_history(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            with (
                patch.object(main, "FOOTBALL_CACHE_FILE", root / "football_matches.json"),
                patch.object(main, "FOOTBALL_ODDS_SNAPSHOT_FILE", root / "football_odds_snapshots.json"),
                patch.object(main, "FOOTBALL_RECOMMENDATION_FILE", root / "football_recommendations.json"),
            ):
                response = TestClient(app).get("/api/lottery/sports/football/recommendations?playType=had&limit=2")

                self.assertEqual(200, response.status_code)
                self.assertTrue((root / "football_odds_snapshots.json").exists())
                self.assertTrue((root / "football_recommendations.json").exists())
                self.assertGreaterEqual(len(main.load_football_odds_snapshots()), 1)
                self.assertGreaterEqual(len(main.load_football_recommendation_history()), 1)

    def test_football_results_import_updates_model_report(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            with (
                patch.object(main, "FOOTBALL_CACHE_FILE", root / "football_matches.json"),
                patch.object(main, "FOOTBALL_ODDS_SNAPSHOT_FILE", root / "football_odds_snapshots.json"),
                patch.object(main, "FOOTBALL_RECOMMENDATION_FILE", root / "football_recommendations.json"),
                patch.object(main, "FOOTBALL_RESULTS_FILE", root / "football_results.json"),
            ):
                api = TestClient(app)
                rec_response = api.get("/api/lottery/sports/football/recommendations?playType=had&limit=1")
                recommendation = rec_response.json()["recommendations"][0]
                selection = recommendation["selection"]
                result_response = api.post(
                    "/api/lottery/sports/football/results/import",
                    json={
                        "results": [
                            {
                                "matchId": recommendation["matchId"],
                                "fullTimeScore": "2:0" if selection == "H" else ("1:1" if selection == "D" else "0:2"),
                            }
                        ]
                    },
                )
                report_response = api.get("/api/lottery/sports/football/model-report")

                self.assertEqual(200, result_response.status_code)
                self.assertEqual(200, report_response.status_code)
                report = report_response.json()
                self.assertEqual(1, report["settledCount"])
                self.assertEqual(1, report["hitCount"])
                self.assertIn("simulatedRoi", report)

    def test_stock_market_api_is_removed(self):
        response = client.get("/api/market/stocks/000001/daily")

        self.assertEqual(404, response.status_code)


if __name__ == "__main__":
    unittest.main()
