"""Publish only independently confirmed new SSQ draws; retain full cached history."""
import importlib.util
import json
import os
import sys
from datetime import datetime
from pathlib import Path
import draw_sources
import source_policy

PUBLIC = Path(__file__).resolve().parents[1] / 'public'
DATA = PUBLIC / 'data/ssq.json'
STATUS = PUBLIC / 'data/source-status.json'
validate = draw_sources.validate

def load_core():
    spec = importlib.util.spec_from_file_location('ssq_core', PUBLIC / 'ssq_core.py')
    core = importlib.util.module_from_spec(spec)
    sys.modules['ssq_core'] = core
    spec.loader.exec_module(core)
    return core

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    temporary.replace(path)

def update(seed=None, *, now=None, fetchers=None):
    current = json.loads(DATA.read_text(encoding='utf-8')) if DATA.exists() else None
    existing = current['draws'] if current else json.loads(Path(seed).read_text(encoding='utf-8-sig'))
    old_status = json.loads(STATUS.read_text(encoding='utf-8')) if STATUS.exists() else {}
    now = (now or datetime.now(draw_sources.CHINA)).astimezone(draw_sources.CHINA)
    session = source_policy.Session(existing, now, fetchers)
    daily = source_policy.health_due(old_status, now)
    if daily:
        session.daily_health()
    selection = source_policy.select(session, (current or {}).get('verification'), old_status)
    merged = {d['issue']: validate(d) for d in existing}
    for row in selection['draws']:
        if row['issue'] in merged and merged[row['issue']] != row:
            raise draw_sources.Conflict('Conflicting published draw: ' + row['issue'])
        merged[row['issue']] = validate(row)
    draws = sorted(merged.values(), key=lambda d: d['issue'], reverse=True)
    initialize = not current or (current.get('verification') or {}).get('policyVersion') != source_policy.POLICY_VERSION
    data_changed = not current or draws != current['draws'] or not current.get('defaultRecommendation') or initialize
    if data_changed:
        if current and draws == current['draws'] and current.get('defaultRecommendation') and current.get('defaultEvaluation'):
            recommendation, evaluation = current['defaultRecommendation'], current['defaultEvaluation']
        else:
            core = load_core()
            print(f'Independently verified {len(draws)} draws, latest {draws[0]["issue"]}; evaluating default plan', flush=True)
            evaluation = core.build_ssq_model_evaluation([core.SsqDraw(**d) for d in draws])
            sys.path.insert(0, str(PUBLIC))
            from browser_api import browser_request
            plan = dict(singleCount=0, compoundCount=1, redCount=6, blueCount=3, recentWindow=500, modelVersion='auto')
            recommendation = json.loads(browser_request(json.dumps(dict(action='recommend', draws=draws, plan=plan, evaluation=evaluation))))
            recommendation.pop('evaluation')
        payload = dict(schemaVersion=1, source=selection['source'], publishedAt=now.isoformat(),
            latestIssue=draws[0]['issue'], draws=draws, defaultEvaluation=evaluation,
            defaultRecommendation=recommendation, verification=selection['verification'])
        write_json(DATA, payload)
    status = source_policy.manifest(old_status, session, selection, daily)
    status_changed = status != old_status
    if status_changed:
        write_json(STATUS, status)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write(f'changed={str(data_changed).lower()}\nstatus_changed={str(status_changed).lower()}\n')
    print(json.dumps(dict(changed=data_changed, statusChanged=status_changed, latestIssue=draws[0]['issue'],
        checkedAt=now.isoformat(), source=(selection['source'] if data_changed else current['source']),
        pending=(selection['pending'] or {}).get('issues', []))))
    return data_changed

if __name__ == '__main__':
    update(sys.argv[1] if len(sys.argv) > 1 else None)
