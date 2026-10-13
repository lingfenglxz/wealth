import ast
import json
import sys
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).parent))
import update_data
from test_policy import BASE, NOW, NEW, VERIFIED, fetchers

class DataTests(unittest.TestCase):
    def test_validation(self):
        good = dict(issue='2026114', date='2026-10-06', redBalls=[1,2,3,4,5,6], blueBall=1)
        self.assertEqual(update_data.validate(good), good)
        for change in [dict(redBalls=[1]*6), dict(blueBall=17), dict(date='bad'), dict(issue='xyz')]:
            with self.assertRaises((ValueError, TypeError)): update_data.validate({**good, **change})

    def test_public_core_matches_original_functions(self):
        original = ast.parse((Path(__file__).resolve().parents[2] / 'server/main.py').read_text(encoding='utf-8-sig'))
        public = ast.parse((update_data.PUBLIC / 'ssq_core.py').read_text(encoding='utf-8'))
        definitions = {n.name:n for n in original.body if isinstance(n, (ast.FunctionDef,ast.ClassDef))}
        for node in public.body:
            if isinstance(node, (ast.FunctionDef,ast.ClassDef)): self.assertEqual(ast.dump(node),ast.dump(definitions[node.name]),node.name)
        self.assertNotIn('FastAPI',(update_data.PUBLIC / 'ssq_core.py').read_text(encoding='utf-8'))

    def prepare(self):
        root = Path(__file__).resolve().parents[1] / 'test-results'; root.mkdir(exist_ok=True)
        temporary = tempfile.TemporaryDirectory(prefix='source-policy-',dir=root); self.addCleanup(temporary.cleanup)
        self.data, self.status = Path(temporary.name)/'ssq.json',Path(temporary.name)/'source-status.json'
        self.calls = fetchers()
        payload = dict(schemaVersion=1,draws=BASE,latestIssue=BASE[0]['issue'],source='existing',defaultRecommendation={'predictions':[]},defaultEvaluation={'modelReports':[]},verification=VERIFIED)
        self.data.write_text(json.dumps(payload),encoding='utf-8')
        self.status.write_text(json.dumps(dict(schemaVersion=1,policyVersion=1,latestIssue=BASE[0]['issue'],pending=None,healthCheckedAt=NOW.isoformat(),healthEnvironment=update_data.source_policy.health_environment(),sources=[])),encoding='utf-8')
        for field,value in [('DATA',self.data),('STATUS',self.status)]:
            stub = patch.object(update_data,field,value); stub.start(); self.addCleanup(stub.stop)

    def test_no_change_skips_evaluation_and_write(self):
        self.prepare(); before = self.data.read_bytes()
        with patch.object(update_data,'load_core') as core:
            self.assertFalse(update_data.update(now=NOW,fetchers=self.calls)); core.assert_not_called()
        self.assertEqual(before,self.data.read_bytes())

    def test_initialization_reuses_default_report(self):
        self.prepare(); payload=json.loads(self.data.read_text(encoding='utf-8'));payload.pop('verification')
        self.data.write_text(json.dumps(payload),encoding='utf-8')
        with patch.object(update_data,'load_core') as core:
            self.assertTrue(update_data.update(now=NOW,fetchers=self.calls));core.assert_not_called()
        saved=json.loads(self.data.read_text(encoding='utf-8'))
        self.assertEqual(saved['defaultRecommendation'],payload['defaultRecommendation']);self.assertEqual(saved['draws'],BASE)
        self.assertEqual(saved['verification']['policyVersion'],1)

    def test_conflict_retains_both_files(self):
        self.prepare();before=(self.data.read_bytes(),self.status.read_bytes())
        self.calls['500']=lambda:[NEW,*BASE];self.calls['tianji']=lambda:[{**NEW,'blueBall':9},*BASE]
        with self.assertRaises(update_data.draw_sources.Conflict):update_data.update(now=NOW,fetchers=self.calls)
        self.assertEqual((self.data.read_bytes(),self.status.read_bytes()),before)

    def test_pending_updates_status_only_and_persistent_failure_alerts(self):
        self.prepare();self.calls['500']=lambda:[NEW,*BASE];before=self.data.read_bytes()
        self.assertFalse(update_data.update(now=NOW,fetchers=self.calls))
        self.assertEqual(before,self.data.read_bytes())
        pending=json.loads(self.status.read_text(encoding='utf-8'))['pending'];self.assertEqual(pending['issues'],[NEW['issue']])
        with self.assertRaises(update_data.draw_sources.Unavailable):update_data.update(now=NOW+timedelta(hours=2),fetchers=self.calls)
        self.assertEqual(before,self.data.read_bytes())

if __name__=='__main__':unittest.main()
