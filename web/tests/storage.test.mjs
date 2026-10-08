import test from 'node:test';
import assert from 'node:assert/strict';
import { emptyState, mergeBackup, validatePlan } from '../src/storage.js';
const p = { targetIssue: '2026001', createdAt: 123, modelVersion: 'auto', redBalls: [1, 2, 3, 4, 5, 6], blueBalls: [1] };
test('Android backup groups plans, preserves distinct generations and imports idempotently', () => {
  const backup = { predictions: [p, { ...p, redBalls: [2, 3, 4, 5, 6, 7] }, { ...p, createdAt: 124 }] };
  const state = mergeBackup(emptyState(), backup);
  assert.equal(state.runs.length, 2);
  assert.equal(state.runs[1].predictions.length, 2);
  assert.deepEqual(mergeBackup(state, backup), state);
});
test('server run IDs remain separate within the same issue', () => {
  const state = mergeBackup(emptyState(), { predictions: [{ ...p, runId: 'a' }, { ...p, runId: 'b' }] });
  assert.equal(state.runs.length, 2);
});
test('malformed import and invalid plans rejected without partial writes', () => {
  assert.throws(() => mergeBackup(emptyState(), { predictions: [p, { ...p, redBalls: [1, 1, 1, 1, 1, 1] }] }));
  assert.throws(() => validatePlan({ ...emptyState().plan, redCount: 34 }));
  assert.throws(() => validatePlan({ ...emptyState().plan, singleCount: 0, compoundCount: 0 }));
});
test('web backup round-trip keeps research reports and valid settlements', () => {
  const settlement = { issue: '2026001', betCount: 1, investedAmount: 2, simulatedPrizeAmount: 0, roi: -1, bestRedHits: 0, tierCounts: { first: 0, second: 0, third: 0, fourth: 0, fifth: 0, sixth: 0 } };
  const backup = { runs: [{ id: 'a', createdAt: 123, targetIssue: '2026001', predictions: [p], report: { narrative: ['test'] }, settlement }] };
  const saved = mergeBackup(emptyState(), backup);
  assert.deepEqual(saved.runs[0].report, backup.runs[0].report);
  assert.deepEqual(saved.runs[0].settlement, settlement);
  assert.deepEqual(mergeBackup(emptyState(), JSON.parse(JSON.stringify(saved))), saved);
});
