// The archive contains research records only; browser settings and credentials stay local.
const pick = (value, keys) => Object.fromEntries(keys.filter(key => value?.[key] !== undefined).map(key => [key, value[key]]));
export function stableJson(value) {
  if (Array.isArray(value)) return '[' + value.map(stableJson).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().filter(k => value[k] !== undefined).map(k => JSON.stringify(k) + ':' + stableJson(value[k])).join(',') + '}';
  return JSON.stringify(value);
}
function ballsValid(reds, blues) {
  return Array.isArray(reds) && Array.isArray(blues) && reds.length >= 6 && reds.length <= 20 && blues.length >= 1 && blues.length <= 16 && new Set(reds).size === reds.length && new Set(blues).size === blues.length && reds.every(n => Number.isInteger(n) && n >= 1 && n <= 33) && blues.every(n => Number.isInteger(n) && n >= 1 && n <= 16);
}
const predictionKeys = ['targetIssue', 'sourceIssue', 'modelVersion', 'redBalls', 'blueBalls', 'score', 'analysisSummary', 'note', 'reasons', 'ballDetails'];
function prediction(value) {
  if (!value || !ballsValid(value.redBalls, value.blueBalls)) throw new Error('记录包含无效号码，未合并');
  if (value.reasons !== undefined && (!Array.isArray(value.reasons) || value.reasons.some(r => typeof r !== 'string'))) throw new Error('选号依据格式无效');
  if (value.ballDetails !== undefined && (!Array.isArray(value.ballDetails) || value.ballDetails.some(r => !r || typeof r !== 'object'))) throw new Error('评分格式无效');
  return pick(value, predictionKeys);
}
function report(value) {
  if (!value || typeof value !== 'object') return undefined;
  const result = pick(value, ['analysis', 'predictions', 'narrative', 'weights', 'rankings', 'evaluation']);
  if (value.modelEvaluation && !result.evaluation) result.evaluation = value.modelEvaluation;
  if (value.numberRankings && !result.rankings) result.rankings = value.numberRankings;
  if (result.predictions) result.predictions = result.predictions.map(prediction);
  if (result.narrative !== undefined && (!Array.isArray(result.narrative) || result.narrative.some(n => typeof n !== 'string'))) throw new Error('报告格式无效');
  return Object.keys(result).length ? result : undefined;
}
function settlement(value, issue) {
  if (!value) return undefined;
  const keys = ['issue', 'drawDate', 'settledAt', 'betCount', 'investedAmount', 'simulatedPrizeAmount', 'roi', 'bestRedHits', 'blueHit', 'tierCounts', 'details'];
  if (String(value.issue) !== issue || !['betCount', 'investedAmount', 'simulatedPrizeAmount', 'roi', 'bestRedHits'].every(k => Number.isFinite(value[k])) || !value.tierCounts || !['first', 'second', 'third', 'fourth', 'fifth', 'sixth'].every(k => Number.isInteger(value.tierCounts[k]) && value.tierCounts[k] >= 0)) return undefined;
  return pick(value, keys);
}
export function normalizeRun(value) {
  if (!value || typeof value.id !== 'string' || !value.id || value.id.length > 1000 || !/^\d{7}$/.test(String(value.targetIssue)) || !Number.isFinite(Number(value.createdAt)) || !Array.isArray(value.predictions) || !value.predictions.length) throw new Error('记录格式无效，未合并');
  const result = { id: value.id, targetIssue: String(value.targetIssue), createdAt: Number(value.createdAt), predictions: value.predictions.map(prediction) };
  if (value.originId) result.originId = String(value.originId);
  if (/^\d{7}$/.test(String(value.sourceIssue))) result.sourceIssue = String(value.sourceIssue);
  if (value.plan) result.plan = pick(value.plan, ['singleCount', 'compoundCount', 'redCount', 'blueCount', 'recentWindow', 'modelVersion']);
  const savedReport = report(value.report), savedSettlement = settlement(value.settlement, result.targetIssue);
  if (savedReport) result.report = savedReport;
  if (savedSettlement) result.settlement = savedSettlement;
  return result;
}
function signature(run) {
  return stableJson([run.targetIssue, run.predictions.map(p => [[...p.redBalls].sort((a, b) => a - b), [...p.blueBalls].sort((a, b) => a - b)])]);
}
function fingerprint(text) {
  let a = 2166136261, b = 3339675911;
  for (let i = 0; i < text.length; i++) { a = Math.imul(a ^ text.charCodeAt(i), 16777619); b = Math.imul(b ^ text.charCodeAt(i), 2246822519); }
  return (a >>> 0).toString(16).padStart(8, '0') + (b >>> 0).toString(16).padStart(8, '0');
}
function richer(a, b) {
  if (a === undefined) return b;
  if (b === undefined) return a;
  const x = stableJson(a), y = stableJson(b);
  return x.length > y.length || (x.length === y.length && x > y) ? a : b;
}
function mergeReports(a, b) {
  if (!a || !b) return a || b;
  return Object.fromEntries([...new Set([...Object.keys(a), ...Object.keys(b)])].map(key => [key, richer(a[key], b[key])]));
}
export function mergeRuns(...lists) {
  const groups = new Map();
  for (const input of lists.flat()) {
    const run = normalizeRun(input), root = run.originId || run.id, key = signature(run);
    if (!groups.has(root)) groups.set(root, new Map());
    const group = groups.get(root), previous = group.get(key);
    if (!previous) group.set(key, run);
    else {
      const merged = { ...previous, createdAt: Math.min(previous.createdAt, run.createdAt) };
      for (const field of ['sourceIssue', 'plan', 'predictions', 'report', 'settlement', 'originId']) {
        const chosen = field === 'report' ? mergeReports(previous[field], run[field]) : richer(previous[field], run[field]);
        if (chosen !== undefined) merged[field] = chosen;
      }
      group.set(key, merged);
    }
  }
  const result = [];
  for (const [root, group] of groups) for (const [key, run] of group) {
    const variant = group.size > 1 || run.originId;
    result.push({ ...run, id: variant ? root + '~' + fingerprint(key) : root, ...(variant ? { originId: root } : {}) });
  }
  if (new Set(result.map(r => r.id)).size !== result.length) throw new Error('记录标识冲突，未覆盖任何记录');
  return result.sort((a, b) => b.createdAt - a.createdAt || a.id.localeCompare(b.id, 'en'));
}
export const archiveOf = runs => ({ schemaVersion: 1, runs: mergeRuns(runs) });
export function readArchive(value) {
  if (value?.schemaVersion !== 1 || !Array.isArray(value.runs)) throw new Error('GitHub 记录格式异常，本地记录已保留');
  return mergeRuns(value.runs);
}
