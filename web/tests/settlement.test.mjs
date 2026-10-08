import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { settlePrediction } from '../src/settlement.js';
test('settlement matches Python for red counts, hit counts and both blue outcomes', () => {
  const draw = { issue: '2026114', date: '2026-10-06', redBalls: [1, 2, 3, 4, 5, 6], blueBall: 7 };
  const plans = [];
  for (const redCount of [6, 7, 10, 20]) for (let hits = 0; hits <= 6; hits++) for (const blueCount of [1, 3, 15]) for (const hasBlue of [false, true]) {
    const reds = [...Array.from({ length: hits }, (_, i) => i + 1), ...Array.from({ length: redCount - hits }, (_, i) => i + 7)];
    const nonWinningBlues = Array.from({ length: 16 }, (_, i) => i + 1).filter(n => n !== 7);
    const blues = hasBlue ? [7, ...nonWinningBlues.slice(0, blueCount - 1)] : nonWinningBlues.slice(0, blueCount);
    plans.push({ redBalls: reds, blueBalls: blues });
  }
  const code = "import sys,json;sys.path.insert(0,'public');from ssq_core import SsqDraw,settle_server_prediction;p=json.load(sys.stdin);print(json.dumps([settle_server_prediction(x,SsqDraw(**p['draw'])) for x in p['plans']]))";
  const expected = JSON.parse(execFileSync('python', ['-c', code], { input: JSON.stringify({ draw, plans }), encoding: 'utf8' }));
  assert.deepEqual(plans.map(p => settlePrediction(p, draw)), expected);
});
