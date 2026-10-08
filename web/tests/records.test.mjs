import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeRuns, archiveOf, stableJson, readArchive } from '../src/records.js';
const run = { id: 'x', createdAt: 100, targetIssue: '2026001', predictions: [{ redBalls: [1, 2, 3, 4, 5, 6], blueBalls: [1] }] };
test('merge preserves report upgrades, removes local-only fields and converges', () => {
  const a = { ...run, report: { analysis: { targetIssue: '2026001' }, narrative: ['report'] }, token: 'not-an-actual-secret' };
  const b = { ...run, createdAt: 200, report: { evaluation: { modelReports: [] } }, stateBackup: { private: true } };
  const merged = mergeRuns([a], [b]);
  assert.equal(merged[0].createdAt, 100);
  assert.ok(merged[0].report.analysis);
  assert.ok(merged[0].report.evaluation);
  assert.deepEqual(merged, mergeRuns([b], [a]));
  assert.deepEqual(merged, mergeRuns(merged, [a], [b]));
  assert.ok(!stableJson(archiveOf(merged)).includes('not-an-actual-secret'));
  assert.ok(!stableJson(archiveOf(merged)).includes('stateBackup'));
});
test('same ID with different numbers keeps both records with stable variant IDs', () => {
  const other = { ...run, predictions: [{ redBalls: [2, 3, 4, 5, 6, 7], blueBalls: [1] }] };
  const merged = mergeRuns([run], [other]);
  assert.equal(merged.length, 2);
  assert.equal(new Set(merged.map(r => r.id)).size, 2);
  assert.deepEqual(merged, mergeRuns([other], [run]));
  assert.deepEqual(merged, mergeRuns(merged, [run], [other]));
});
test('stale records cannot remove a completed settlement; malformed cloud archive rejected', () => {
  const settlement = { issue: run.targetIssue, betCount: 1, investedAmount: 2, simulatedPrizeAmount: 0, roi: -1, bestRedHits: 0, tierCounts: { first: 0, second: 0, third: 0, fourth: 0, fifth: 0, sixth: 0 } };
  assert.deepEqual(mergeRuns([{ ...run, settlement }], [run])[0].settlement, settlement);
  assert.throws(() => readArchive({ schemaVersion: 2, runs: [] }));
  assert.throws(() => readArchive({ schemaVersion: 1, runs: [{ ...run, predictions: [{ redBalls: [1], blueBalls: [1] }] }] }));
});
