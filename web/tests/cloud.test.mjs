import test from 'node:test';
import assert from 'node:assert/strict';
import { gzipSync, gunzipSync } from 'node:zlib';
import { downloadArchive, synchronize, setAuthorization, authorized } from '../src/cloud.js';
import { mergeRuns } from '../src/records.js';
const run = { id: 'local', createdAt: 100, targetIssue: '2026001', predictions: [{ redBalls: [1, 2, 3, 4, 5, 6], blueBalls: [1] }] };
const jsonResponse = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
test('large GitHub file uses authenticated raw media type without following download URLs', async t => {
  let requests = 0;
  const bytes = gzipSync(JSON.stringify({ schemaVersion: 1, runs: [run] }));
  setAuthorization('fixture-token');
  t.after(() => setAuthorization(''));
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.ok(url.startsWith('https://api.github.com/repos/lingfenglxz/wealth/contents/'));
    assert.ok(url.includes('codex%2Fssq-records'));
    assert.equal(options.headers.Authorization, 'Bearer fixture-token');
    assert.equal(options.redirect, 'error');
    if (++requests === 1) return jsonResponse({ sha: 'metadata', size: 1000001, encoding: 'none', content: '', download_url: 'https://untrusted.example/unused' });
    assert.equal(options.headers.Accept, 'application/vnd.github.raw+json');
    return new Response(bytes);
  });
  assert.deepEqual((await downloadArchive()).runs, [run]);
  assert.equal(requests, 2);
});
test('records added during upload stay pending and are preserved for the next sync', async t => {
  let local = [run], remote = [], uploaded;
  setAuthorization('fixture-token');
  t.after(() => setAuthorization(''));
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method === 'GET') return jsonResponse({ sha: 's', size: 100, encoding: 'base64', content: gzipSync(JSON.stringify({ schemaVersion: 1, runs: remote })).toString('base64') });
    const body = JSON.parse(options.body);
    uploaded = JSON.parse(gunzipSync(Buffer.from(body.content, 'base64')));
    assert.ok(!JSON.stringify(uploaded).includes('fixture-token'));
    local = mergeRuns(local, [{ ...run, id: 'new' }]);
    return jsonResponse({});
  });
  const result = await synchronize(() => local, async runs => { local = mergeRuns(local, runs); });
  assert.equal(result.pending, true);
  assert.equal(uploaded.runs.length, 1);
  assert.equal(local.length, 2);
});
test('invalid cloud archives and expired credentials never invoke local merging', async t => {
  setAuthorization('fixture-token');
  t.after(() => setAuthorization(''));
  let expired = false;
  t.mock.method(globalThis, 'fetch', async () => expired ? jsonResponse({}, 401) : jsonResponse({ sha: 's', size: 100, encoding: 'base64', content: gzipSync(JSON.stringify({ schemaVersion: 2, runs: [] })).toString('base64') }));
  await assert.rejects(synchronize(() => [run], async () => assert.fail('must not replace local data')), /格式异常/);
  expired = true;
  await assert.rejects(synchronize(() => [run], async () => assert.fail('must not replace local data')), /授权失效/);
  assert.equal(authorized(), false);
});
