import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import main


class ServerImportDirectoryTest(unittest.TestCase):
    def test_ssq_draws_can_be_loaded_from_server_import_directory(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            imports = root / "imports"
            imports.mkdir()
            (imports / "ssq_draws.json").write_text(
                json.dumps(
                    [
                        {"issue": "2026001", "date": "2026-01-01", "redBalls": [1, 2, 3, 4, 5, 6], "blueBall": 7}
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            with patch.object(main, "IMPORT_DIR", imports), patch.object(main, "CACHE_FILE", root / "ssq_draws.json"):
                draws = asyncio.run(main.get_draws(limit=10, refresh=False))

        self.assertEqual("2026001", draws[0].issue)

    def test_football_matches_can_be_loaded_from_server_import_directory(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            imports = Path(temp_dir) / "imports"
            imports.mkdir()
            (imports / "football_matches.json").write_text(
                json.dumps(
                    {
                        "matches": [
                            {
                                "matchId": "manual-001",
                                "kickoffTime": "2026-06-11T19:00:00-06:00",
                                "homeTeam": "Mexico",
                                "awayTeam": "South Africa",
                                "pools": {"had": {"H": 1.8, "D": 3.2, "A": 4.5}},
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            with patch.object(main, "IMPORT_DIR", imports), patch.object(main, "FOOTBALL_CACHE_FILE", Path(temp_dir) / "football_cache.json"):
                matches, source_status, _ = asyncio.run(main.get_football_matches(refresh=False))

        self.assertEqual("manual-001", matches[0].matchId)
        self.assertEqual("import", source_status)


if __name__ == "__main__":
    unittest.main()
