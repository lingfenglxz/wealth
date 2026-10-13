import ast, concurrent.futures, json, re, time, urllib.request, urllib.error
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path

class Rows(HTMLParser):
    def __init__(self):
        super().__init__(); self.rows=[]; self.cells=[]; self.cell=None; self.css=''
    def handle_starttag(self, tag, attributes):
        if tag=='tr': self.cells=[]
        if tag=='td': self.cell=[]; self.css=dict(attributes).get('class','')
    def handle_data(self, text):
        if self.cell is not None and text.strip(): self.cell.append(text.strip())
    def handle_endtag(self, tag):
        if tag=='td' and self.cell is not None: self.cells.append((' '.join(self.cell),self.css)); self.cell=None
        if tag=='tr' and self.cells: self.rows.append(self.cells)

def parse(text, kind):
    if kind=='shanghai':
        result=[]
        for item in re.findall(r'employee.push\((\{[^}]+\})\)',text):
            row=ast.literal_eval(item); reds,blue=row['c'].split('|')
            result.append(dict(issue=row['id'],date=row['t'][:10],redBalls=list(map(int,reds.split(','))),blueBall=int(blue)))
        return result
    if kind=='zhcw':
        payload=json.loads(text[text.index('(')+1:text.rfind(')')])
        if payload.get('resCode')!='000000': raise ValueError('Provider rejected query')
        return [dict(issue=str(r['issue']),date=r['openTime'][:10],redBalls=list(map(int,r['frontWinningNum'].split())),blueBall=int(r['backWinningNum'])) for r in payload['data']]
    if kind in ('guangdong','sina'):
        try:
            value=json.loads(text)
            if isinstance(value,str): text=value
        except ValueError: pass
        table=Rows();table.feed(text);result=[]
        for row in table.rows:
            if not row or not re.fullmatch(r'\d{7}',row[0][0]):continue
            if kind=='guangdong':
                result.append(dict(issue=row[0][0],redBalls=list(map(int,row[1][0].split())),blueBall=int(row[2][0])))
            else:
                winning=[int(value) for value,css in row[1:] if 'chartball' in css and value.isdecimal()]
                if len(winning)!=7:raise ValueError('Unexpected Sina winning-ball markup')
                result.append(dict(issue=row[0][0],redBalls=winning[:6],blueBall=winning[6]))
        return result
    return []

SOURCES=[
    ('Shanghai welfare lottery','shanghai','https://appsh.swlc.net.cn/shfcoc_datachart/datachart/ssq/lskj/ls_award.html'),
    ('Guangdong welfare lottery','guangdong','https://www.gdfc.org.cn/lottery/ssq.html'),
    ('ZHCW public history API','zhcw','https://jc.zhcw.com/port/client_json.php?transactionType=10001001&lotteryId=1&issueCount=30&type=0&pageNum=1&pageSize=30&callback=probe'),
    ('Sina lottery chart','sina','https://view.lottery.sina.com.cn/lotto/pc_zst/index?lottoType=ssq&actionType=chzs&dpc=1'),
    ('Lecai downloadable history','lecai','https://data.17500.cn/ssq_asc.txt'),
    ('Vipc published latest draw','vipc','https://www.vipc.cn/result/ssq/2026116'),
]

def probe(source, known):
    name,kind,url=source;started=time.monotonic();result=dict(source=name,url=url)
    try:
        request=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Accept':'text/html,application/json,*/*','Referer':url.split('/',3)[0]+'//'+url.split('/',3)[2]+'/'})
        with urllib.request.urlopen(request,timeout=25) as response:
            raw=response.read(4*1024*1024);result['httpStatus']=response.status
        try:text=raw.decode('utf-8')
        except UnicodeDecodeError:text=raw.decode('gb18030')
        rows=parse(text,kind);result['rows']=len(rows)
        if kind in ('lecai','vipc'):
            result['result']='reachable; format not validated'
        else:
            if not rows:raise ValueError('No draw rows')
            for row in rows:
                if not re.fullmatch(r'\d{7}',row['issue']) or len(row['redBalls'])!=6 or len(set(row['redBalls']))!=6 or not all(1<=n<=33 for n in row['redBalls']) or not 1<=row['blueBall']<=16:raise ValueError('Invalid draw')
                if row.get('date'):datetime.strptime(row['date'],'%Y-%m-%d')
            conflicts=[];overlap=0
            for row in rows:
                actual=known.get(row['issue'])
                if actual:
                    overlap+=1
                    if row['redBalls']!=actual['redBalls'] or row['blueBall']!=actual['blueBall'] or (row.get('date') and row['date']!=actual['date']):conflicts.append(row['issue'])
            result.update(latestIssue=max(row['issue'] for row in rows),historyOverlap=overlap,conflicts=conflicts,completeDates=all(row.get('date') for row in rows))
            result['result']='validated' if not conflicts and overlap>=10 and result['latestIssue']>=max(known) else 'stale or inconsistent'
    except urllib.error.HTTPError as error:
        result.update(httpStatus=error.code,result='unavailable');error.close()
    except Exception as error:
        result.update(result='error',error=str(error))
    result['seconds']=round(time.monotonic()-started,2)
    return result

if __name__=='__main__':
    published=json.loads(Path('web/public/data/ssq.json').read_text(encoding='utf-8'))
    known={draw['issue']:draw for draw in published['draws']}
    print('Read-only probe baseline issue: '+published['latestIssue'],flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results=list(pool.map(lambda source:probe(source,known),SOURCES))
    for result in results:print(json.dumps(result,ensure_ascii=True),flush=True)
