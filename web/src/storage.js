import { mergeRuns } from './records.js';
export const defaultPlan = { singleCount: 0, compoundCount: 1, redCount: 6, blueCount: 3, recentWindow: 500, modelVersion: 'auto' };
export const emptyState = () => ({ schemaVersion: 2, plan: { ...defaultPlan }, runs: [], evaluations: {} });
export function validBalls(reds, blues) {
  return Array.isArray(reds) && Array.isArray(blues) && reds.length >= 6 && reds.length <= 20 && blues.length >= 1 && blues.length <= 16 && new Set(reds).size === reds.length && new Set(blues).size === blues.length && reds.every(n => Number.isInteger(n) && n >= 1 && n <= 33) && blues.every(n => Number.isInteger(n) && n >= 1 && n <= 16);
}
export function validatePlan(plan) {
  const limits = { singleCount: [0, 10], compoundCount: [0, 5], redCount: [6, 20], blueCount: [1, 16], recentWindow: [30, 500] };
  for (const [key, [min, max]] of Object.entries(limits)) if (!Number.isInteger(plan[key]) || plan[key] < min || plan[key] > max) throw new Error('参数范围错误：' + key);
  if (!['auto', 'recent_focus_v3', 'hit_rate_v4', 'balanced_v2', 'baseline_v1'].includes(plan.modelVersion)) throw new Error('未知模型');
  if (plan.singleCount + plan.compoundCount === 0) throw new Error('至少生成一组号码');
  return plan;
}
export function mergeBackup(state, backup) {
  const imported = [];
  if (Array.isArray(backup.runs)) imported.push(...backup.runs);
  else if (Array.isArray(backup.predictions)) {
    const grouped = new Map();
    for (const prediction of backup.predictions) {
      const id = String(prediction.runId || `legacy:${prediction.targetIssue}:${prediction.createdAt}:${prediction.modelVersion}`);
      if (!grouped.has(id)) grouped.set(id, { id, targetIssue: prediction.targetIssue, createdAt: prediction.createdAt, predictions: [] });
      grouped.get(id).predictions.push(prediction);
    }
    for (const run of grouped.values()) {
      run.report = (backup.researchReports || []).find(r => (r.runId && r.runId === run.id) || (r.targetIssue === run.targetIssue && r.createdAt === run.createdAt))?.report;
      // Old issue-only settlements are ambiguous; recompute each run using public draws.
      imported.push(run);
    }
  } else throw new Error('备份中没有推荐记录');
  for (const run of imported) {
    if (!run || typeof run.id !== 'string' || !/^\d{7}$/.test(String(run.targetIssue)) || !Number.isFinite(Number(run.createdAt)) || !Array.isArray(run.predictions) || !run.predictions.length || run.predictions.some(p => !validBalls(p.redBalls, p.blueBalls))) throw new Error('备份包含无效推荐，未导入');
  }
  return { ...state, runs: mergeRuns(state.runs, imported) };
}
let database;
async function db() {
  database ??= new Promise((resolve, reject) => {
    const request = indexedDB.open('wealth-ssq', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('state');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return database;
}
export async function loadState() {
  const connection = await db();
  return new Promise((resolve, reject) => {
    const request = connection.transaction('state').objectStore('state').get('app');
    request.onsuccess = () => resolve(request.result || emptyState());
    request.onerror = () => reject(request.error);
  });
}
export async function saveState(state) {
  const connection = await db();
  return new Promise((resolve, reject) => {
    const transaction = connection.transaction('state', 'readwrite');
    const store = transaction.objectStore('state');
    let saved;
    const read = store.get('app');
    read.onsuccess = () => {
      try {
        saved = { ...state, runs: mergeRuns(read.result?.runs || [], state.runs) };
        store.put(saved, 'app');
      } catch (error) { reject(error); transaction.abort(); }
    };
    transaction.oncomplete = () => resolve(saved);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error || new Error('保存失败'));
  });
}
