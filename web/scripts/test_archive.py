import base64
import copy
import unittest
import urllib.error
from unittest.mock import patch
from update_archive import decode_archive, encode_archive, settle_archive, update

DRAW = dict(issue='2026001', date='2026-01-01', redBalls=[1, 2, 3, 4, 5, 6], blueBall=1)
RUN = dict(id='a', createdAt=123, targetIssue='2026001', predictions=[dict(redBalls=DRAW['redBalls'], blueBalls=[1])], report={'narrative': ['research']})

class ArchiveTests(unittest.TestCase):
    def test_compressed_round_trip_and_settlement(self):
        archive = dict(schemaVersion=1, runs=[copy.deepcopy(RUN)])
        self.assertEqual(decode_archive(base64.b64decode(encode_archive(archive))), archive)
        self.assertEqual(settle_archive(archive, [DRAW]), 1)
        self.assertEqual(archive['runs'][0]['settlement']['tierCounts']['first'], 1)
        self.assertEqual(archive['runs'][0]['settlement']['investedAmount'], 2)
        self.assertEqual(settle_archive(archive, [DRAW]), 0)
        self.assertEqual(archive['runs'][0]['report'], RUN['report'])

    def test_conflict_rereads_and_preserves_concurrent_records(self):
        archive = dict(schemaVersion=1, runs=[copy.deepcopy(RUN)])
        puts = []
        def api(repository, token, method='GET', raw=False, body=None):
            if method == 'GET':
                return dict(sha=str(len(archive['runs'])), size=500, encoding='base64', content=encode_archive(archive))
            puts.append(body)
            if len(puts) == 1:
                archive['runs'].append({**copy.deepcopy(RUN), 'id': 'b', 'targetIssue': '2026002'})
                raise urllib.error.HTTPError('https://api.github.com', 409, 'conflict', {}, None)
            merged = decode_archive(base64.b64decode(body['content']))
            self.assertEqual([r['id'] for r in merged['runs']], ['a', 'b'])
            self.assertEqual(body['sha'], '2')
            self.assertIn('settlement', merged['runs'][0])
            self.assertNotIn('settlement', merged['runs'][1])
            return {}
        with patch('update_archive.time.sleep'):
            self.assertEqual(update('test/repo', 'fixture', [DRAW], api), 1)
        self.assertEqual(len(puts), 2)

    def test_invalid_archive_fails_closed(self):
        for archive in [dict(schemaVersion=2, runs=[]), dict(schemaVersion=1, runs=[RUN, RUN]), dict(schemaVersion=1, runs=[{**RUN, 'predictions': [{'redBalls': [1] }]}])]:
            with self.assertRaises(ValueError):
                decode_archive(base64.b64decode(encode_archive(archive)))

    def test_no_draw_does_not_write(self):
        def api(repository, token, method='GET', **kwargs):
            self.assertEqual(method, 'GET')
            return dict(sha='x', encoding='base64', content=encode_archive(dict(schemaVersion=1, runs=[RUN])))
        self.assertEqual(update('test/repo', 'fixture', [], api), 0)

if __name__ == '__main__':
    unittest.main()
