import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSourceStatus, assertDrawConfirmed, pendingIssue } from '../src/source-status.js';
const data = { latestIssue: '2026116' };
const status = { schemaVersion: 1, policyVersion: 1, latestIssue: data.latestIssue, sources: [], pending: null };
test('new draw pending confirmation prevents recommending the already drawn issue', () => {
  const pending = { ...status, pending: { issues: ['2026117'], firstSeenAt: '2026-10-13T21:30:00+08:00' } };
  validateSourceStatus(pending, data);
  assert.equal(pendingIssue(pending, data), '2026117');
  assert.throws(() => assertDrawConfirmed(pending, data), /等待跨来源确认/);
  assert.doesNotThrow(() => assertDrawConfirmed(status, data));
});
test('mixed deployments and malformed metadata fail closed', () => {
  assert.throws(() => validateSourceStatus({ ...status, latestIssue: '2026117' }, data));
  assert.throws(() => validateSourceStatus({ ...status, pending: { issues: ['bad'], firstSeenAt: 'bad' } }, data));
});
