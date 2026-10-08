export function combination(n, k) {
  if (k < 0 || k > n) return 0;
  let value = 1;
  for (let i = 1; i <= k; i++) value = value * (n - i + 1) / i;
  return Math.round(value);
}
const emptyTiers = () => ({ first: 0, second: 0, third: 0, fourth: 0, fifth: 0, sixth: 0 });
function tier(red, blue) {
  if (red === 6) return blue ? 'first' : 'second';
  if (red === 5 && blue) return 'third';
  if (red === 5 || (red === 4 && blue)) return 'fourth';
  if (red === 4 || (red === 3 && blue)) return 'fifth';
  if (blue) return 'sixth';
}
export function settlePrediction(prediction, draw) {
  const reds = prediction.redBalls, blues = prediction.blueBalls;
  const overlap = reds.filter(n => draw.redBalls.includes(n)).length;
  const hasBlue = blues.includes(draw.blueBall);
  const tiers = emptyTiers();
  for (let hits = 0; hits <= 6; hits++) {
    const count = combination(overlap, hits) * combination(reds.length - overlap, 6 - hits);
    if (hasBlue && tier(hits, true)) tiers[tier(hits, true)] += count;
    if (tier(hits, false)) tiers[tier(hits, false)] += count * (blues.length - Number(hasBlue));
  }
  return { label: reds.length > 6 || blues.length > 1 ? '复式' : '单式', redBalls: [...reds].sort((a, b) => a - b), blueBalls: [...blues].sort((a, b) => a - b), betCount: combination(reds.length, 6) * blues.length, bestRedHits: Math.min(overlap, 6), blueHit: hasBlue, prizeAmount: tiers.third * 3000 + tiers.fourth * 200 + tiers.fifth * 10 + tiers.sixth * 5, tierCounts: tiers };
}
export function settleRun(draw, predictions) {
  const details = predictions.map(p => settlePrediction(p, draw));
  const tiers = emptyTiers();
  for (const detail of details) for (const key of Object.keys(tiers)) tiers[key] += detail.tierCounts[key];
  const bets = details.reduce((sum, d) => sum + d.betCount, 0);
  const prize = details.reduce((sum, d) => sum + d.prizeAmount, 0);
  const invested = bets * 2;
  return { issue: draw.issue, drawDate: draw.date, settledAt: Number(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).replace(/\D/g, '')), betCount: bets, investedAmount: invested, simulatedPrizeAmount: prize, roi: invested ? (prize - invested) / invested : 0, bestRedHits: Math.max(...details.map(d => d.bestRedHits)), blueHit: details.some(d => d.blueHit), tierCounts: tiers, details };
}
