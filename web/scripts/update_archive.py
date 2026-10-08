"""Settle public research records without a running browser or backend server."""
import base64
import gzip
import io
import json
import math
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from zoneinfo import ZoneInfo
from update_data import DATA, load_core

BRANCH = 'codex/ssq-records'
ARCHIVE_PATH = 'records/ssq-records.json.gz'
MAX_COMPRESSED = 25 * 1024 * 1024
MAX_EXPANDED = 100 * 1024 * 1024

def decode_archive(content):
    if len(content) > MAX_COMPRESSED:
        raise ValueError('Public archive exceeds size limit')
    with gzip.GzipFile(fileobj=io.BytesIO(content)) as archive:
        expanded = archive.read(MAX_EXPANDED + 1)
    if len(expanded) > MAX_EXPANDED:
        raise ValueError('Expanded public archive exceeds size limit')
    payload = json.loads(expanded)
    if payload.get('schemaVersion') != 1 or not isinstance(payload.get('runs'), list):
        raise ValueError('Invalid public archive schema')
    ids = set()
    for run in payload['runs']:
        if not isinstance(run, dict) or not isinstance(run.get('id'), str) or not run['id'] or run['id'] in ids:
            raise ValueError('Invalid or duplicate record ID')
        ids.add(run['id'])
        if not re.fullmatch(r'\d{7}', str(run.get('targetIssue', ''))) or not isinstance(run.get('createdAt'), (int, float)) or not math.isfinite(run['createdAt']):
            raise ValueError('Invalid research record')
        predictions = run.get('predictions')
        if not isinstance(predictions, list) or not predictions:
            raise ValueError('Missing predictions')
        for prediction in predictions:
            for key, low, high, minimum, maximum in [('redBalls', 1, 33, 6, 20), ('blueBalls', 1, 16, 1, 16)]:
                balls = prediction.get(key)
                if not isinstance(balls, list) or not minimum <= len(balls) <= maximum or len(set(balls)) != len(balls) or any(type(n) is not int or not low <= n <= high for n in balls):
                    raise ValueError('Invalid prediction balls')
    return payload

def encode_archive(payload):
    content = gzip.compress(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8'), mtime=0)
    if len(content) > MAX_COMPRESSED:
        raise ValueError('Compressed archive exceeds size limit')
    return base64.b64encode(content).decode('ascii')

def settle_archive(payload, draws):
    core = load_core()
    index = {d['issue']: d for d in draws}
    changed = 0
    for run in payload['runs']:
        if run.get('settlement') or run['targetIssue'] not in index:
            continue
        draw = core.SsqDraw(**index[run['targetIssue']])
        settlement = core.build_server_settlement(draw, run['predictions'])
        if not settlement:
            raise ValueError('Unable to settle valid research record')
        settlement['settledAt'] = int(datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%d'))
        run['settlement'] = settlement
        changed += 1
    return changed

def request(repository, token, method='GET', raw=False, body=None):
    endpoint = f'https://api.github.com/repos/{repository}/contents/{ARCHIVE_PATH}'
    if method == 'GET':
        endpoint += '?' + urllib.parse.urlencode({'ref': BRANCH})
    headers = {'Authorization': 'Bearer ' + token, 'User-Agent': 'wealth-public-record-settlement',
               'Accept': 'application/vnd.github.raw+json' if raw else 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28'}
    data = None
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode('utf-8')
    with urllib.request.urlopen(urllib.request.Request(endpoint, data=data, headers=headers, method=method), timeout=45) as response:
        return response.read() if raw else json.load(response)

def update(repository, token, draws, api=request):
    for attempt in range(4):
        file = api(repository, token)
        if file.get('size', 0) > MAX_COMPRESSED:
            raise ValueError('Public archive exceeds size limit')
        content = base64.b64decode(file['content']) if file.get('encoding') == 'base64' else api(repository, token, raw=True)
        payload = decode_archive(content)
        changed = settle_archive(payload, draws)
        if not changed:
            print(f'Public archive: {len(payload["runs"])} records, no new settlements')
            return 0
        try:
            api(repository, token, method='PUT', body={'branch': BRANCH, 'sha': file['sha'],
                'message': 'Settle public SSQ research records', 'content': encode_archive(payload)})
            print(f'Public archive: settled {changed} records, {len(payload["runs"])} total')
            return changed
        except urllib.error.HTTPError as error:
            error.close()
            if error.code not in (409, 422) or attempt == 3:
                raise
            time.sleep(attempt + 1)
    raise RuntimeError('Archive remains busy; no records overwritten')

if __name__ == '__main__':
    repository = os.environ['GITHUB_REPOSITORY']
    token = os.environ['GITHUB_TOKEN']
    update(repository, token, json.loads(DATA.read_text(encoding='utf-8'))['draws'])
