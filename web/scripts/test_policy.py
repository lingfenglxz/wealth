import copy
import unittest
from datetime import datetime, timedelta
from unittest.mock import Mock
import draw_sources as readers
import source_policy as policy

NOW = datetime(2026, 10, 13, 12, tzinfo=readers.CHINA)
BASE = [dict(issue=f'2026{i:03}', date=f'2026-01-{i:02}', redBalls=[1,2,3,4,5,6], blueBall=7) for i in range(20, 0, -1)]
NEW = dict(issue='2026021', date='2026-01-21', redBalls=[2,3,4,5,6,7], blueBall=8)
VERIFIED = dict(policyVersion=1, sources=[dict(id='500'), dict(id='tianji')])

def fetchers(a=None, b=None, c=None):
    return {key: Mock(return_value=copy.deepcopy(rows if rows is not None else BASE)) for key, rows in [('500', a), ('tianji', b), ('sina', c)]}

class PolicyTests(unittest.TestCase):
    def test_normal_hour_reads_only_primary(self):
        calls = fetchers(); result = policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        self.assertEqual(result['draws'], []); self.assertIsNone(result['pending'])
        calls['500'].assert_called_once(); calls['tianji'].assert_not_called(); calls['sina'].assert_not_called()

    def test_fallback_does_not_need_primary_to_succeed(self):
        calls = fetchers(b=[NEW, *BASE], c=[{k:v for k,v in r.items() if k != 'date'} for r in [NEW, *BASE]])
        calls['500'].side_effect = readers.Unavailable('timeout')
        result = policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        self.assertEqual(result['draws'], [NEW]); self.assertEqual(result['verification']['sources'][0]['id'], 'tianji')

    def test_new_draw_needs_independent_confirmation(self):
        calls = fetchers(a=[NEW, *BASE], b=[NEW, *BASE]); result = policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        self.assertEqual(result['draws'], [NEW]); calls['sina'].assert_not_called()

    def test_lagging_confirmation_tries_next_and_never_counts_xml_as_vote(self):
        calls = fetchers(a=[NEW, *BASE], b=BASE, c=[NEW, *BASE])
        result = policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        self.assertEqual([v['id'] for v in result['verification']['sources']], ['500', 'sina'])

    def test_conflicting_new_or_published_numbers_stop_publication(self):
        calls = fetchers(a=[NEW,*BASE], b=[{**NEW, 'blueBall': 9},*BASE])
        with self.assertRaises(readers.Conflict): policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        calls = fetchers(a=[{**BASE[0], 'blueBall': 9}, *BASE[1:]])
        with self.assertRaises(readers.Conflict): policy.select(policy.Session(BASE, NOW, calls), VERIFIED)

    def test_daily_check_cannot_hide_approved_history_conflict(self):
        calls = fetchers(a=[NEW,*BASE], b=[{**BASE[0], 'blueBall': 9},*BASE[1:]], c=[NEW,*BASE])
        session = policy.Session(BASE, NOW, calls)
        session.fetch('tianji', observe=True)
        with self.assertRaises(readers.Conflict): policy.select(session, VERIFIED)

    def test_one_source_pending_then_persistent_problem_alerts(self):
        calls = fetchers(a=[NEW,*BASE])
        result = policy.select(policy.Session(BASE, NOW, calls), VERIFIED)
        self.assertEqual(result['draws'], []); self.assertEqual(result['pending']['issues'], [NEW['issue']])
        with self.assertRaisesRegex(readers.Unavailable, 'two hours'):
            policy.select(policy.Session(BASE, NOW + timedelta(hours=2), fetchers(a=[NEW,*BASE])), VERIFIED, {'pending':result['pending']})

    def test_focus_window_finds_primary_stale_new_draw(self):
        calls = fetchers(b=[NEW,*BASE], c=[NEW,*BASE])
        result = policy.select(policy.Session(BASE, NOW.replace(hour=22), calls), VERIFIED)
        self.assertEqual(result['draws'], [NEW])

    def test_gaps_and_dates_never_guessed(self):
        gap = {**NEW,'issue':'2026022'}
        result = policy.select(policy.Session(BASE, NOW, fetchers(a=[gap,*BASE],b=[gap,*BASE],c=[gap,*BASE])), VERIFIED)
        self.assertEqual(result['draws'], []); self.assertIsNotNone(result['pending'])
        no_dates = [{k:v for k,v in r.items() if k != 'date'} for r in [NEW,*BASE]]
        result = policy.select(policy.Session(BASE, NOW, fetchers(a=no_dates,b=no_dates,c=no_dates)), VERIFIED)
        self.assertEqual(result['draws'], [])

    def test_auxiliary_never_supplies_required_vote(self):
        session = policy.Session(BASE, NOW, fetchers(a=[NEW,*BASE]))
        session.cache['711'] = [NEW,*BASE]
        result = policy.select(session, VERIFIED)
        self.assertEqual(result['draws'], []); self.assertIsNotNone(result['pending'])

    def test_confirmed_prefix_can_publish_while_later_draw_waits(self):
        second = {**NEW,'issue':'2026022','date':'2026-01-22'}
        result = policy.select(policy.Session(BASE, NOW, fetchers(a=[second,NEW,*BASE],b=[NEW,*BASE],c=BASE)), VERIFIED)
        self.assertEqual(result['draws'], [NEW]); self.assertEqual(result['pending']['issues'], ['2026022'])

    def test_daily_health_optional_failures_and_environment(self):
        calls = fetchers()
        calls.update({'cwl':Mock(side_effect=readers.Unavailable('403')), '711':Mock(side_effect=readers.Unavailable('timeout')), 'jiangsu':Mock(side_effect=readers.Unavailable('timeout'))})
        session = policy.Session(BASE, NOW, calls); session.daily_health()
        self.assertEqual(policy.select(session, VERIFIED)['draws'], [])
        previous = {'healthCheckedAt':NOW.isoformat(), 'healthEnvironment':policy.health_environment()}
        self.assertFalse(policy.health_due(previous, NOW)); self.assertTrue(policy.health_due(previous, NOW+timedelta(days=1)))
        self.assertTrue(policy.health_due({**previous,'healthEnvironment':'another-host'}, NOW))

if __name__ == '__main__': unittest.main()
