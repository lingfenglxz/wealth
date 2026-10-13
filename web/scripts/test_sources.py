import json
import subprocess
import unittest
from unittest.mock import patch
import draw_sources as sources

class SourceTests(unittest.TestCase):
    def test_500_comments_and_two_formats(self):
        html = '<tr><!--<td>wrong</td>-->' + ''.join(f'<td>{v}</td>' for v in ['26114', 1, 2, 3, 4, 5, 6, 7, '2026-10-06']) + '</tr>'
        xml = '<xml><row expect="26114" opencode="01,02,03,04,05,06|07" opentime="2026-10-06 21:15:00"/></xml>'
        self.assertEqual(sources.parse_500(html, xml)[0]['blueBall'], 7)
        with self.assertRaises(sources.Conflict): sources.parse_500(html, xml.replace('|07', '|08'))
        with self.assertRaises(sources.Unavailable): sources.parse_500(html, xml.replace('26114', '26115'))

    def test_tianji_selected_issue_not_dropdown_first_or_prediction(self):
        html = '<option value="/kaijiang/ssq/2026116.html">future</option>开奖期数：<span>第 2026114 期</span>开奖日期：<span>2026年10月6日</span>'
        html += ''.join(f'<div class="red-ball">{n}</div>' for n in range(1, 7)) + '<div class="blue-ball">7</div>'
        row = sources.parse_tianji(html)
        self.assertEqual(row['issue'], '2026114'); self.assertEqual(row['date'], '2026-10-06')
        with self.assertRaises(ValueError): sources.parse_tianji(html.replace('red-ball', 'changed-markup'))

    def test_sina_hits_only_and_announcement_dates_separate(self):
        html = '<tr><td>2026114</td><td class="yl01">33</td>'
        html += ''.join(f'<td class="chartball01">{n}</td>' for n in range(1, 7)) + '<td class="chartball02">7</td></tr>'
        rows = sources.parse_sina(html)
        self.assertNotIn('date', rows[0]); self.assertEqual(rows[0]['redBalls'], list(range(1, 7)))
        dates = sources.parse_jiangsu('<a href="https://www.cwl.gov.cn/x">双色球第2026114期开奖公告</a><span class="articleDate">2026-10-06</span>')
        self.assertEqual(dates, {'2026114': '2026-10-06'})

    def test_auxiliary_duplicate_layout_deduplicated(self):
        row = '<tr><td>双色球</td><td>2026114</td><td>1 2 3 4 5 6 7</td><td>2026/10/06 21:15</td></tr>'
        self.assertEqual(len(sources.parse_711(row + row)), 1)
        with self.assertRaises(sources.Conflict): sources.parse_711(row + row.replace('5 6 7', '5 8 7'))

    def test_validation_rejects_duplicates_bool_future_and_missing_date(self):
        row = dict(issue='2026114', date='2026-10-06', redBalls=list(range(1, 7)), blueBall=7)
        for changed in [dict(redBalls=[1]*6), dict(blueBall=True), dict(date='2099-01-01'), dict(date=None)]:
            with self.assertRaises(ValueError): sources.validate({**row, **changed})

    def test_network_retries_and_absolute_deadline(self):
        failure = subprocess.CompletedProcess([], 28, b'\n000', b'timed out')
        success = subprocess.CompletedProcess([], 0, b'body\n200', b'')
        with patch.object(sources.shutil, 'which', return_value='curl'), patch.object(sources.subprocess, 'run', side_effect=[failure, success]) as run, patch.object(sources.time, 'sleep') as sleep:
            self.assertEqual(sources.download(sources.HISTORY), b'body')
            self.assertEqual(run.call_count, 2); sleep.assert_called_once_with(1)
            self.assertIn('--max-time', run.call_args.args[0]); self.assertEqual(run.call_args.kwargs['timeout'], 30)
        denied = subprocess.CompletedProcess([], 0, b'forbidden\n403', b'')
        with patch.object(sources.shutil, 'which', return_value='curl'), patch.object(sources.subprocess, 'run', return_value=denied) as run, patch.object(sources.time, 'sleep') as sleep:
            with self.assertRaisesRegex(sources.Unavailable, '403'): sources.download(sources.CWL)
            self.assertEqual(run.call_count, 1); sleep.assert_not_called()

if __name__ == '__main__': unittest.main()
