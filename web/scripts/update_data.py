"""Fail closed on invalid/unreachable CWL data. Public data only; no user state."""
import importlib.util
import json
import os
import re
import sys
import time
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from datetime import datetime, timezone
from pathlib import Path

PUBLIC = Path(__file__).resolve().parents[1] / 'public'
DATA = PUBLIC / 'data/ssq.json'
URL = 'https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice'
HISTORY_URL = 'https://datachart.500.com/ssq/history/newinc/history.php?limit=1000&sort=0'
XML_URL = 'https://kaijiang.500.com/static/info/kaijiang/xml/ssq/list10.xml'

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

class HistoryParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.cells = []
        self.cell = None

    def handle_starttag(self, tag, attributes):
        if tag == 'tr':
            self.cells = []
        elif tag == 'td':
            self.cell = ''

    def handle_data(self, value):
        if self.cell is not None:
            self.cell += value

    def handle_endtag(self, tag):
        if tag == 'td' and self.cell is not None:
            self.cells.append(self.cell.strip())
            self.cell = None
        elif tag == 'tr' and len(self.cells) >= 9 and re.fullmatch(r'\d{5}', self.cells[0]):
            self.rows.append(validate(dict(issue='20' + self.cells[0], date=self.cells[-1],
                redBalls=[int(n) for n in self.cells[1:7]], blueBall=int(self.cells[7]))))

def fetch_backup():
    def download(url):
        request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.500.com/'})
        with urllib.request.urlopen(request, timeout=45) as response:
            return response.read()
    raw = download(HISTORY_URL)
    try:
        text = raw.decode('utf-8')
    except UnicodeDecodeError:
        text = raw.decode('gb18030')
    parser = HistoryParser()
    parser.feed(text)
    rows = sorted(parser.rows, key=lambda d: d['issue'], reverse=True)
    if not rows:
        raise ValueError('Backup history returned no draws')
    index = {row['issue']: row for row in rows}
    xml_rows = []
    for row in ET.fromstring(download(XML_URL)).findall('row'):
        reds, blue = row.attrib['opencode'].split('|')
        draw = validate(dict(issue='20' + row.attrib['expect'], date=row.attrib['opentime'][:10],
                             redBalls=[int(n) for n in reds.split(',')], blueBall=int(blue)))
        if index.get(draw['issue']) != draw:
            raise ValueError('Backup XML and history disagree: ' + draw['issue'])
        xml_rows.append(draw)
    if not xml_rows or max(d['issue'] for d in xml_rows) != rows[0]['issue']:
        raise ValueError('Backup feeds have different latest issues')
    return rows

def collect(known):
    try:
        collected = []
        for page in range(1, 101):
            rows = fetch_page(page)
            collected.extend(rows)
            if any(row['issue'] in known for row in rows):
                return collected, URL
        raise ValueError('No overlap with cached history')
    except Exception as error:
        print(f'Official endpoint unavailable: {error}; checking validated backup feeds', flush=True)
        rows = fetch_backup()
        if not any(row['issue'] in known for row in rows):
            raise ValueError('Backup history does not overlap published history')
        return rows, HISTORY_URL

def update(seed=None):
    current = json.loads(DATA.read_text(encoding='utf-8')) if DATA.exists() else None
    existing = current['draws'] if current else json.loads(Path(seed).read_text(encoding='utf-8-sig'))
    merged = {d['issue']: validate(d) for d in existing}
    known = set(merged)
    rows, source = collect(known)
    overlaps = [row for row in rows if row['issue'] in known]
    if source != URL and len(overlaps) < min(10, len(known)):
        raise ValueError('Insufficient overlap to validate backup source')
    for row in rows:
        if row['issue'] in merged and merged[row['issue']] != row:
            raise ValueError('Conflicting published draw: ' + row['issue'])
        merged[row['issue']] = row
    draws = sorted(merged.values(), key=lambda d: d['issue'], reverse=True)
    changed = current is None or draws != current['draws'] or not current.get('defaultRecommendation')
    now = datetime.now(timezone.utc).isoformat()
    if changed:
        core = load_core()
        print(f'Validated {len(draws)} draws, latest {draws[0]["issue"]}; evaluating default plan', flush=True)
        evaluation = core.build_ssq_model_evaluation([core.SsqDraw(**d) for d in draws])
        sys.path.insert(0, str(PUBLIC))
        from browser_api import browser_request
        plan = dict(singleCount=0, compoundCount=1, redCount=6, blueCount=3, recentWindow=500, modelVersion='auto')
        recommendation = json.loads(browser_request(json.dumps(dict(action='recommend', draws=draws, plan=plan, evaluation=evaluation))))
        recommendation.pop('evaluation')
        payload = dict(schemaVersion=1, source=source, publishedAt=now, latestIssue=draws[0]['issue'],
                       draws=draws, defaultEvaluation=evaluation, defaultRecommendation=recommendation)
        DATA.parent.mkdir(parents=True, exist_ok=True)
        temporary = DATA.with_suffix('.tmp')
        temporary.write_text(json.dumps(payload, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
        temporary.replace(DATA)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write(f'changed={str(changed).lower()}\n')
    print(json.dumps(dict(changed=changed, latestIssue=draws[0]['issue'], checkedAt=now, source=source)))
    return changed

if __name__ == '__main__':
    update(sys.argv[1] if len(sys.argv) > 1 else None)
