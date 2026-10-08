import ast
import importlib.util
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import update_data

class DataTests(unittest.TestCase):
    def test_validation(self):
        good = dict(issue='2026114', date='2026-10-06', redBalls=[1,2,3,4,5,6], blueBall=1)
        self.assertEqual(update_data.validate(good), good)
        for change in [dict(redBalls=[1]*6), dict(blueBall=17), dict(date='bad'), dict(issue='xyz')]:
            with self.assertRaises((ValueError, TypeError)):
                update_data.validate({**good, **change})

    def test_public_core_matches_original_functions(self):
        original = ast.parse((Path(__file__).resolve().parents[2] / 'server/main.py').read_text(encoding='utf-8-sig'))
        public = ast.parse((update_data.PUBLIC / 'ssq_core.py').read_text(encoding='utf-8'))
        definitions = {n.name: n for n in original.body if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
        for node in public.body:
            if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
                self.assertEqual(ast.dump(node), ast.dump(definitions[node.name]), node.name)
        self.assertNotIn('FastAPI', (update_data.PUBLIC / 'ssq_core.py').read_text(encoding='utf-8'))

    def test_no_change_skips_evaluation_and_write(self):
        payload = json.loads(update_data.DATA.read_text(encoding='utf-8'))
        before = update_data.DATA.read_bytes()
        with patch.object(update_data, 'fetch_page', return_value=payload['draws'][:100]), patch.object(update_data, 'load_core') as core:
            self.assertFalse(update_data.update())
            core.assert_not_called()
        self.assertEqual(before, update_data.DATA.read_bytes())

    def test_history_parser_ignores_commented_cells(self):
        parser = update_data.HistoryParser()
        parser.feed('<tr><!--<td>2</td>--><td>26114</td>' + ''.join(f'<td>{i}</td>' for i in range(1, 8)) + '<td>2026-10-06</td></tr>')
        self.assertEqual(parser.rows[0]['issue'], '2026114')
        self.assertEqual(parser.rows[0]['redBalls'], [1,2,3,4,5,6])

    def test_official_failure_uses_backup_with_history_overlap(self):
        row = dict(issue='2026114', date='2026-10-06', redBalls=[1,2,3,4,5,6], blueBall=7)
        with patch.object(update_data, 'fetch_page', side_effect=RuntimeError('403')), patch.object(update_data, 'fetch_backup', return_value=[row]):
            rows, source = update_data.collect({row['issue']})
            self.assertEqual(rows, [row])
            self.assertEqual(source, update_data.HISTORY_URL)
        with patch.object(update_data, 'fetch_page', side_effect=RuntimeError('403')), patch.object(update_data, 'fetch_backup', return_value=[row]):
            with self.assertRaises(ValueError):
                update_data.collect({'2026113'})

if __name__ == '__main__':
    unittest.main()
