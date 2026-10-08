"""Native Python reference for browser/WASM parity verification."""
import json
import sys
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'public'))
from browser_api import browser_request
data = json.loads((Path(__file__).resolve().parents[1] / 'public/data/ssq.json').read_text(encoding='utf-8'))
plan = dict(singleCount=0, compoundCount=1, redCount=6, blueCount=3, recentWindow=500, modelVersion='auto')
print(browser_request(json.dumps(dict(action='recommend', draws=data['draws'], plan=plan, evaluation=data['defaultEvaluation']))))
