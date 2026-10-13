import concurrent.futures,json,re,subprocess,os
from pathlib import Path
from html.parser import HTMLParser
class Rows(HTMLParser):
 def __init__(self):super().__init__();self.rows=[];self.cells=[];self.cell=None
 def handle_starttag(self,t,a):
  if t=='tr':self.cells=[]
  if t=='td':self.cell=[]
 def handle_data(self,x):
  if self.cell is not None and x.strip():self.cell.append(x.strip())
 def handle_endtag(self,t):
  if t=='td' and self.cell is not None:self.cells.append(' '.join(self.cell));self.cell=None
  if t=='tr' and self.cells:self.rows.append(self.cells)
SOURCES=[
 ('Tianji ten complete draws','tianji-history','https://www.16788.cn/kaijiang/ssq.html'),
 ('711 history','711','https://711kj.cc/lottery/SSQ?panel=history'),
 ('Jiangsu official announcement dates','jiangsu','https://www.jslottery.com/winning_history_a?locale=zh-CN&lottery_type_id=10&page=1&periods='),
 ('ZHCW via curl','zhcw','https://jc.zhcw.com/port/client_json.php?transactionType=10001001&lotteryId=1&issueCount=30&type=0&pageNum=1&pageSize=30&callback=probe'),
]
def parse(s,kind):
 if kind=='tianji':
  issue=re.search(r'开奖期数[^<]*<span[^>]*>\s*第\s*(\d{7})\s*期',s).group(1)
  date=re.search(r'开奖日期[^<]*<span[^>]*>\s*(\d{4})年(\d{1,2})月(\d{1,2})日',s)
  reds=list(map(int,re.findall(r'<div class="red-ball">\s*(\d+)\s*</div>',s)));blue=int(re.search(r'<div class="blue-ball">\s*(\d+)',s).group(1))
  return [dict(issue=issue,date=f'{int(date[1]):04}-{int(date[2]):02}-{int(date[3]):02}',redBalls=reds,blueBall=blue)]
 if kind=='tianji-chart':
  result=[]
  for html in re.findall(r'<tr\b[^>]*>(.*?)</tr>',s,re.S|re.I):
   issue=re.search(r'<td\s+class="leftth">\s*(\d{7})\s*</td>',html)
   if not issue:continue
   reds=list(map(int,re.findall(r'class="zoushiqiu code-red">\s*(\d+)',html)))
   blue=re.findall(r'class="zoushiqiu code-blue">\s*(\d+)',html)
   if len(reds)==6 and len(blue)==1:result.append(dict(issue=issue[1],redBalls=reds,blueBall=int(blue[0])))
  return result
 if kind=='711':
  p=Rows();p.feed(s);result={}
  for row in p.rows:
   if len(row)>=4 and re.fullmatch(r'\d{7}',row[1]):
    balls=list(map(int,row[2].split()))
    if len(balls)==7:result[row[1]]=dict(issue=row[1],redBalls=balls[:6],blueBall=balls[6],date=row[3][:10].replace('/','-'))
  return list(result.values())
 if kind=='jiangsu':
  pairs=re.findall(r'<a[^>]+>[^<]*?(\d{7})[^<]*?</a>\s*<span[^>]+>(\d{4}-\d{2}-\d{2})',s,re.S)
  return [dict(issue=i,date=d) for i,d in pairs]
 if kind=='zhcw':
  d=json.loads(s if s.lstrip().startswith('{') else s[s.index('(')+1:s.rfind(')')]);return [dict(issue=str(r['issue']),date=r['openTime'][:10],redBalls=list(map(int,r['frontWinningNum'].split())),blueBall=int(r['backWinningNum'])) for r in d['data']]
 return []
def download(url):
 binary='curl.exe' if os.name=='nt' else 'curl'
 response=subprocess.run([binary,'--location','--silent','--show-error','--fail-with-body','--connect-timeout','8','--max-time','20','--user-agent','Mozilla/5.0',url],capture_output=True,timeout=25)
 if response.returncode:raise RuntimeError(response.stderr.decode('utf-8',errors='replace').strip())
 try:return response.stdout.decode('utf-8')
 except UnicodeDecodeError:return response.stdout.decode('gb18030')
def probe(item,known):
 name,kind,url=item;result=dict(source=name,url=url)
 try:
  s=download(url)
  if kind=='tianji-history':
   paths=list(dict.fromkeys(re.findall(r'<option\s+value="(/kaijiang/ssq/\d{7}\.html)"',s)))[:10]
   with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    pages=list(pool.map(lambda path:download('https://www.16788.cn'+path),paths))
   rows=[row for page in pages for row in parse(page,'tianji')]
  else:rows=parse(s,kind)
  if not rows:raise ValueError('No usable draw rows')
  overlap=0;conflicts=[]
  for row in rows:
   actual=known.get(row['issue'])
   if actual:
    overlap+=1
    if any(row.get(k)!=actual[k] for k in ['redBalls','blueBall','date'] if k in row):conflicts.append(row['issue'])
  result.update(httpStatus=200,rows=len(rows),latestIssue=max(r['issue'] for r in rows),historyOverlap=overlap,conflicts=conflicts,completeDates=all('date' in r for r in rows),hasNumbers=all('redBalls' in r for r in rows))
  result['result']='consistent' if not conflicts and overlap else 'unverified'
 except Exception as error:result.update(result='error',error=str(error))
 return result
if __name__=='__main__':
 data=json.loads(Path('web/public/data/ssq.json').read_text(encoding='utf-8'));known={d['issue']:d for d in data['draws']}
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda item:probe(item,known),SOURCES))
 for result in results:print(json.dumps(result,ensure_ascii=True),flush=True)
