"""Fail closed on invalid/unreachable CWL data. Public data only; no user state."""
import importlib.util
import json
import os
import re
import sys
import time
import urllib.request
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

PUBLIC = Path(__file__).resolve().parents[1] / 'public'
DATA = PUBLIC / 'data/ssq.json'
URL = 'https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice'

def validate(item):
    if not isinstance(item, dict) or not re.fullmatch(r'\d{7}', str(item.get('issue', ''))):
        raise ValueError('Invalid issue')
    datetime.strptime(item['date'], '%Y-%m-%d')
    reds = item['redBalls']
    if len(reds) != 6 or len(set(reds)) != 6 or any(type(n) is not int or not 1 <= n <= 33 for n in reds):
        raise ValueError('Invalid red balls')
    if type(item['blueBall']) is not int or not 1 <= item['blueBall'] <= 16:
        raise ValueError('Invalid blue ball')
    return {**item, 'redBalls': sorted(reds)}

def fetch_page(page):
    query = urllib.parse.urlencode(dict(name='ssq', issueCount='', issueStart='', issueEnd='',
        dayStart='', dayEnd='', pageNo=page, pageSize=100, week='', systemType='PC'))
    request = urllib.request.Request(URL + '?' + query, headers={
        'User-Agent': 'Mozilla/5.0 Chrome/127.0.0.0 Safari/537.36',
        'Referer': 'https://www.cwl.gov.cn/', 'Accept': 'application/json,text/plain,*/*',
        'X-Requested-With': 'XMLHttpRequest'})
    error = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                payload = json.load(response)
            if payload.get('state') != 0 or not payload.get('result'):
                raise ValueError('CWL returned no valid results')
            return [validate(dict(issue=str(row['code']), date=str(row['date'])[:10],
                    redBalls=[int(n) for n in row['red'].split(',')], blueBall=int(row['blue'])))
                    for row in payload['result']]
        except Exception as caught:
            error = caught
            if attempt < 3:
                time.sleep(2 ** attempt)
    raise RuntimeError(f'CWL page {page} failed: {error}')

def load_core():
    spec = importlib.util.spec_from_file_location('ssq_core', PUBLIC / 'ssq_core.py')
    core = importlib.util.module_from_spec(spec)
    sys.modules['ssq_core'] = core
    spec.loader.exec_module(core)
    return core

def update(seed=None):
    current = json.loads(DATA.read_text(encoding='utf-8')) if DATA.exists() else None
    existing = current['draws'] if current else json.loads(Path(seed).read_text(encoding='utf-8-sig'))
    merged = {d['issue']: validate(d) for d in existing}
    known = set(merged)
    for page in range(1, 101):
        rows = fetch_page(page)
        for row in rows:
            if row['issue'] in merged and merged[row['issue']] != row:
                raise ValueError('Conflicting published draw: ' + row['issue'])
            merged[row['issue']] = row
        if any(row['issue'] in known for row in rows):
            break
    else:
        raise ValueError('No overlap with cached history; refusing incomplete update')
    draws = sorted(merged.values(), key=lambda d: d['issue'], reverse=True)
    changed = current is None or draws != current['draws']
    now = datetime.now(timezone.utc).isoformat()
    if changed:
        core = load_core()
        print(f'Validated {len(draws)} draws, latest {draws[0]["issue"]}; evaluating default plan', flush=True)
        evaluation = core.build_ssq_model_evaluation([core.SsqDraw(**d) for d in draws])
        payload = dict(schemaVersion=1, source=URL, publishedAt=now, latestIssue=draws[0]['issue'],
                       draws=draws, defaultEvaluation=evaluation)
        DATA.parent.mkdir(parents=True, exist_ok=True)
        temporary = DATA.with_suffix('.tmp')
        temporary.write_text(json.dumps(payload, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
        temporary.replace(DATA)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write(f'changed={str(changed).lower()}\n')
    print(json.dumps(dict(changed=changed, latestIssue=draws[0]['issue'], checkedAt=now)))
    return changed

if __name__ == '__main__':
    update(sys.argv[1] if len(sys.argv) > 1 else None)
