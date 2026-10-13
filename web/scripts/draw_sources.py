"""Public SSQ readers. No browser credentials, date guessing or executable page scripts."""
import concurrent.futures
import json
import os
import re
import shutil
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser

CHINA = timezone(timedelta(hours=8))
CWL = 'https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice'
HISTORY = 'https://datachart.500.com/ssq/history/newinc/history.php?limit=1000&sort=0'
XML = 'https://kaijiang.500.com/static/info/kaijiang/xml/ssq/list10.xml'
TIANJI = 'https://www.16788.cn/kaijiang/ssq.html'
SINA = 'https://view.lottery.sina.com.cn/lotto/pc_zst/index?lottoType=ssq&actionType=chzs&dpc=1'
JIANGSU = 'https://www.jslottery.com/winning_history_a?locale=zh-CN&lottery_type_id=10&page=1&periods='
AUXILIARY = 'https://711kj.cc/lottery/SSQ?panel=history'
SOURCES = {
    '500': ('500 彩票网', HISTORY, 'primary'),
    'tianji': ('天吉网', TIANJI, 'backup'),
    'sina': ('新浪彩票', SINA, 'backup'),
    'jiangsu': ('江苏福彩（公告日期辅助）', JIANGSU, 'dates'),
    'cwl': ('中国福彩网', CWL, 'observe'),
    '711': ('711 开奖（观察）', AUXILIARY, 'observe'),
}

class Unavailable(RuntimeError):
    pass

class Conflict(ValueError):
    pass

def validate(item, require_date=True):
    if not isinstance(item, dict) or not re.fullmatch(r'\d{7}', str(item.get('issue', ''))):
        raise ValueError('Invalid issue')
    reds, blue = item.get('redBalls'), item.get('blueBall')
    if not isinstance(reds, list) or len(reds) != 6 or any(type(n) is not int or not 1 <= n <= 33 for n in reds) or len(set(reds)) != 6:
        raise ValueError('Invalid red balls')
    if type(blue) is not int or not 1 <= blue <= 16:
        raise ValueError('Invalid blue ball')
    result = dict(issue=str(item['issue']), redBalls=sorted(reds), blueBall=blue)
    if item.get('date'):
        date = str(item['date'])
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', date):
            raise ValueError('Invalid date')
        parsed = datetime.strptime(date, '%Y-%m-%d').date()
        if date[:4] != result['issue'][:4] or parsed > datetime.now(CHINA).date():
            raise ValueError('Invalid issue date')
        result['date'] = date
    elif require_date:
        raise ValueError('Missing actual draw date')
    return result

def numbers(row):
    return tuple(row['redBalls']), row['blueBall']

def normalize(rows):
    index = {}
    for raw in rows:
        row = validate(raw, require_date=False)
        previous = index.get(row['issue'])
        if previous and (numbers(previous) != numbers(row) or (previous.get('date') and row.get('date') and previous['date'] != row['date'])):
            raise Conflict('Duplicate issue disagrees: ' + row['issue'])
        index[row['issue']] = {**previous, **row} if previous else row
    if not index:
        raise ValueError('No draw rows')
    return sorted(index.values(), key=lambda d: d['issue'], reverse=True)

def download(url, attempts=4):
    """curl's overall deadline also bounds DNS/multiple-address connection attempts."""
    binary = shutil.which('curl.exe' if os.name == 'nt' else 'curl')
    if not binary:
        raise RuntimeError('curl is required (bundled with current Windows and GitHub runners)')
    for attempt in range(attempts):
        response = subprocess.run([binary, '--location', '--silent', '--show-error',
            '--connect-timeout', '8', '--max-time', '25', '--user-agent', 'Mozilla/5.0',
            '--write-out', '\n%{http_code}', url], capture_output=True, timeout=30)
        body, _, status_text = response.stdout.rpartition(b'\n')
        status = int(status_text) if status_text.isdigit() else 0
        if response.returncode == 0 and status == 200:
            if len(body) > 8 * 1024 * 1024:
                raise ValueError('Source response exceeds 8 MB')
            return body
        temporary = status in (408, 429, 500, 502, 503, 504) or (not status and response.returncode in (5, 6, 7, 18, 28, 35, 52, 55, 56))
        reason = f'HTTP {status}' if status else f'curl connection error {response.returncode}'
        if not temporary or attempt == attempts - 1:
            raise Unavailable(reason)
        print(f'Source connection retry {attempt + 2}/{attempts}: {reason}', flush=True)
        time.sleep(2 ** attempt)
    raise Unavailable('Source unavailable')

def text(url, attempts=4):
    raw = download(url, attempts)
    try:
        return raw.decode('utf-8')
    except UnicodeDecodeError:
        return raw.decode('gb18030')

class Table(HTMLParser):
    def __init__(self):
        super().__init__(); self.rows = []; self.cells = []; self.cell = None; self.css = ''
    def handle_starttag(self, tag, attributes):
        if tag == 'tr': self.cells = []
        if tag == 'td': self.cell = []; self.css = dict(attributes).get('class', '')
    def handle_data(self, value):
        if self.cell is not None and value.strip(): self.cell.append(value.strip())
    def handle_endtag(self, tag):
        if tag == 'td' and self.cell is not None:
            self.cells.append((' '.join(self.cell), self.css)); self.cell = None
        if tag == 'tr' and self.cells: self.rows.append(self.cells)

def parse_500(html, xml):
    table = Table(); table.feed(html)
    rows = []
    for cells in table.rows:
        values = [c[0] for c in cells]
        if len(values) >= 9 and re.fullmatch(r'\d{5}', values[0]):
            rows.append(validate(dict(issue='20' + values[0], date=values[-1], redBalls=list(map(int, values[1:7])), blueBall=int(values[7]))))
    rows = normalize(rows); index = {r['issue']: r for r in rows}; announcements = []
    for row in ET.fromstring(xml).findall('row'):
        reds, blue = row.attrib['opencode'].split('|')
        draw = validate(dict(issue='20' + row.attrib['expect'], date=row.attrib['opentime'][:10], redBalls=list(map(int, reds.split(','))), blueBall=int(blue)))
        if draw['issue'] in index and index[draw['issue']] != draw:
            raise Conflict('500 formats disagree: ' + draw['issue'])
        announcements.append(draw)
    if not announcements or max(d['issue'] for d in announcements) != rows[0]['issue'] or any(d['issue'] not in index for d in announcements):
        raise Unavailable('500 history and XML have not synchronized yet')
    return rows

def fetch_500(attempts=4):
    return parse_500(text(HISTORY, attempts), text(XML, attempts))

def parse_tianji(html):
    issue = re.search(r'开奖期数[^<]*<span[^>]*>\s*第\s*(\d{7})\s*期', html)
    date = re.search(r'开奖日期[^<]*<span[^>]*>\s*(\d{4})年(\d{1,2})月(\d{1,2})日', html)
    blue = re.search(r'<div\s+class="blue-ball">\s*(\d+)', html)
    if not issue or not date or not blue:
        raise ValueError('Tianji draw markup missing')
    return validate(dict(issue=issue[1], date=f'{int(date[1]):04}-{int(date[2]):02}-{int(date[3]):02}',
        redBalls=list(map(int, re.findall(r'<div\s+class="red-ball">\s*(\d+)\s*</div>', html))), blueBall=int(blue[1])))

def fetch_tianji(known, attempts=4):
    html = text(TIANJI, attempts); latest = parse_tianji(html)
    paths = {path.split('/')[-1][:7]: path for path in re.findall(r'<option\s+value="(/kaijiang/ssq/\d{7}\.html)"', html)}
    issues = sorted((issue for issue in paths if issue <= latest['issue']), reverse=True)
    selected = []; anchors = 0
    for issue in issues:
        selected.append(issue); anchors += issue in known
        if anchors >= min(10, len(known)): break
        if len(selected) >= 40: raise ValueError('Tianji cannot cover missing history within 40 announcements')
    if latest['issue'] not in selected: selected.insert(0, latest['issue'])
    others = [issue for issue in selected if issue != latest['issue']]
    def read(issue):
        draw = parse_tianji(text('https://www.16788.cn' + paths[issue], attempts))
        if draw['issue'] != issue: raise ValueError('Tianji returned another issue')
        return draw
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        return normalize([latest, *pool.map(read, others)])

def parse_sina(html):
    table = Table(); table.feed(html); rows = []
    for cells in table.rows:
        if not re.fullmatch(r'\d{7}', cells[0][0]): continue
        balls = [int(value) for value, css in cells[1:] if 'chartball' in css and value.isdecimal()]
        if len(balls) != 7: raise ValueError('Sina winning-ball markup changed')
        rows.append(dict(issue=cells[0][0], redBalls=balls[:6], blueBall=balls[6]))
    return normalize(rows)

def fetch_sina(attempts=4):
    return parse_sina(text(SINA, attempts))

def parse_jiangsu(html):
    pairs = re.findall(r'<a[^>]+>[^<]*?(\d{7})[^<]*?</a>\s*<span[^>]+>(\d{4}-\d{2}-\d{2})', html, re.S)
    if not pairs: raise ValueError('Jiangsu announcement dates missing')
    result = {}
    for issue, date in pairs:
        datetime.strptime(date, '%Y-%m-%d')
        if issue in result and result[issue] != date: raise Conflict('Announcement dates disagree')
        result[issue] = date
    return result

def fetch_jiangsu(attempts=1):
    return parse_jiangsu(text(JIANGSU, attempts))

def fetch_cwl(attempts=1):
    query = urllib.parse.urlencode(dict(name='ssq', pageNo=1, pageSize=100, systemType='PC'))
    payload = json.loads(text(CWL + '?' + query, attempts))
    if payload.get('state') != 0 or not payload.get('result'): raise ValueError('CWL returned no draws')
    return normalize([validate(dict(issue=str(r['code']), date=str(r['date'])[:10], redBalls=list(map(int, r['red'].split(','))), blueBall=int(r['blue']))) for r in payload['result']])

def parse_711(html):
    table = Table(); table.feed(html); rows = []
    for cells in table.rows:
        values = [c[0] for c in cells]
        if len(values) >= 4 and re.fullmatch(r'\d{7}', values[1]):
            balls = list(map(int, values[2].split()))
            if len(balls) != 7: raise ValueError('Auxiliary draw markup changed')
            rows.append(validate(dict(issue=values[1], date=values[3][:10].replace('/', '-'), redBalls=balls[:6], blueBall=balls[6])))
    return normalize(rows)

def fetch_711(attempts=1):
    return parse_711(text(AUXILIARY, attempts))
