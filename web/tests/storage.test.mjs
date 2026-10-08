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
test('web backup round-trip keeps reports and settlements', () => {
  const backup = { runs: [{ id: 'a', createdAt: 123, targetIssue: '2026001', predictions: [p], report: { test: true }, settlement: { betCount: 1 } }] };
  assert.deepEqual(mergeBackup(emptyState(), backup).runs, backup.runs);
});
