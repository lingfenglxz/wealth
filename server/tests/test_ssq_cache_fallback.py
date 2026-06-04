import asyncio
import unittest
from unittest.mock import patch

import main


class SsqCacheFallbackTest(unittest.TestCase):
    def test_refresh_uses_cached_draws_when_official_site_times_out(self):
        cached = [
            main.SsqDraw(issue="2026062", date="2026-06-02", redBalls=[6, 11, 12, 17, 25, 28], blueBall=2)
        ]

        with patch("main.load_cache", return_value=cached), \
                patch("main.refresh_ssq_draws", side_effect=TimeoutError("official timeout")), \
                patch("main.save_cache") as save_cache:
            draws = asyncio.run(main.get_draws(limit=3000, refresh=True))

        self.assertEqual(cached, draws)
        save_cache.assert_not_called()


if __name__ == "__main__":
    unittest.main()
