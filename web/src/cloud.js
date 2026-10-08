import { archiveOf, readArchive, stableJson } from './records.js';

export const cloudConfig = { repository: 'lingfenglxz/wealth', branch: 'codex/ssq-records', path: 'records/ssq-records.json.gz' };
export const archiveUrl = `https://github.com/${cloudConfig.repository}/blob/${cloudConfig.branch}/${cloudConfig.path}`;
const endpoint = `https://api.github.com/repos/${cloudConfig.repository}/contents/${cloudConfig.path}`;
const sessionKey = 'wealth-ssq-github-session';
let token = '';
try { token = sessionStorage.getItem(sessionKey) || ''; } catch { /* Private browsing may disable session storage. */ }
export const authorized = () => Boolean(token);
export function setAuthorization(value) {
  token = value.trim();
  try { if (token) sessionStorage.setItem(sessionKey, token); else sessionStorage.removeItem(sessionKey); } catch { /* Memory-only authorization still works. */ }
}
function headers(raw = false) {
  return { Accept: raw ? 'application/vnd.github.raw+json' : 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', ...(token ? { Authorization: 'Bearer ' + token } : {}) };
}
async function request(method, raw = false, body) {
  const url = method === 'GET' ? `${endpoint}?ref=${encodeURIComponent(cloudConfig.branch)}&t=${Date.now()}` : endpoint;
  return fetch(url, { method, headers: { ...headers(raw), ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), cache: 'no-store', signal: AbortSignal.timeout(30000), redirect: 'error' });
}
function failure(response) {
  if (response.status === 401) { setAuthorization(''); return new Error('GitHub 授权失效，请重新配置；本地记录已保留'); }
  if (response.status === 403 || response.status === 429) return new Error('GitHub 权限不足或请求限额已用完，请检查 Contents 写入权限，稍后重试；本地记录已保留');
  if (response.status === 404) return new Error('GitHub 记录分支未找到或授权无权访问；本地记录已保留');
  return new Error(`GitHub 同步失败（${response.status}），本地记录已保留`);
}
export async function compressArchive(runs) {
  const stream = new Blob([stableJson(archiveOf(runs))]).stream().pipeThrough(new CompressionStream('gzip'));
  const bytes = new Uint8Array(await new Response(stream).arrayBuffer());
  if (bytes.length > 25 * 1024 * 1024) throw new Error('云端压缩记录超过 25 MB，请保留 JSON 备份');
  let binary = '';
  for (let i = 0; i < bytes.length; i += 32768) binary += String.fromCharCode(...bytes.subarray(i, i + 32768));
  return btoa(binary);
}
async function decodeArchive(bytes) {
  const reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')).getReader();
  const chunks = []; let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      size += value.length;
      if (size > 100 * 1024 * 1024) throw new Error('云端记录解压后过大，请检查仓库文件');
      chunks.push(value);
    }
  } finally { await reader.cancel(); }
  return readArchive(JSON.parse(await new Blob(chunks).text()));
}
export async function downloadArchive() {
  const response = await request('GET');
  if (!response.ok) throw failure(response);
  const file = await response.json();
  if (!file.sha || file.size > 25 * 1024 * 1024) throw new Error('GitHub 文件异常或过大，本地记录已保留');
  let bytes;
  if (file.encoding === 'base64' && file.content) bytes = Uint8Array.from(atob(file.content.replace(/\s/g, '')), c => c.charCodeAt(0));
  else {
    const raw = await request('GET', true);
    if (!raw.ok) throw failure(raw);
    bytes = new Uint8Array(await raw.arrayBuffer());
    // A concurrent write between metadata and raw download is detected by the PUT SHA check.
  }
  return { sha: file.sha, runs: await decodeArchive(bytes) };
}
// Read / merge / compare-and-swap. Re-read after a competing device wins the SHA race.
export async function synchronize(getRuns, mergeLocal) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const remote = await downloadArchive();
    await mergeLocal(remote.runs);
    const runs = getRuns();
    const different = stableJson(archiveOf(runs)) !== stableJson(archiveOf(remote.runs));
    if (!different || !authorized()) return { count: remote.runs.length, pending: different, uploaded: false };
    const content = await compressArchive(runs);
    const response = await request('PUT', false, { message: 'Sync SSQ research records', branch: cloudConfig.branch, sha: remote.sha, content });
    if (response.ok) return { count: runs.length, pending: stableJson(archiveOf(getRuns())) !== stableJson(archiveOf(runs)), uploaded: true };
    if (response.status !== 409 && response.status !== 422) throw failure(response);
  }
  throw new Error('其他设备正在更新，请稍后重试；本地记录已保留');
}
