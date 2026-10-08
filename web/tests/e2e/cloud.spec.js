import { test, expect } from '@playwright/test';
import { gzipSync, gunzipSync } from 'node:zlib';
import { readFileSync } from 'node:fs';

const pattern = 'https://api.github.com/repos/lingfenglxz/wealth/contents/**';
const fixtureToken = 'test-only-not-a-real-github-token';
const prediction = { redBalls: [1, 2, 3, 4, 5, 6], blueBalls: [1], note: 'fixture' };
const record = id => ({ id, createdAt: 100, targetIssue: '2026999', predictions: [prediction], report: { narrative: ['公开研究报告'] } });

test('authenticated upload merges a concurrent device, fresh devices read without authorization', async ({ page, browser }) => {
  let archive = { schemaVersion: 1, runs: [] }, sha = 1, puts = 0;
  const uploads = [], errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const handler = async route => {
    if (route.request().method() === 'PUT') {
      expect(route.request().headers().authorization).toBe('Bearer ' + fixtureToken);
      const body = route.request().postDataJSON();
      expect(body.branch).toBe('codex/ssq-records');
      uploads.push(gunzipSync(Buffer.from(body.content, 'base64')).toString());
      if (++puts === 1) {
        archive.runs.push(record('other-device')); sha++;
        return route.fulfill({ status: 409, json: { message: 'SHA conflict' } });
      }
      expect(body.sha).toBe(String(sha));
      archive = JSON.parse(uploads.at(-1)); sha++;
      return route.fulfill({ status: 200, json: { content: { sha: String(sha) } } });
    }
    return route.fulfill({ json: { sha: String(sha), size: 1000, encoding: 'base64', content: gzipSync(JSON.stringify(archive)).toString('base64') } });
  };
  await page.route(pattern, handler);
  await page.goto('./');
  await expect(page.locator('[data-cloud-status]')).toContainText('已同步 0');
  await page.getByRole('button', { name: '生成推荐并保存', exact: true }).click();
  await expect(page.locator('[data-cloud-status]')).toContainText('待上传');
  await page.getByRole('button', { name: '数据备份', exact: true }).click();
  await page.locator('#github-token').fill(fixtureToken);
  await page.getByRole('button', { name: '保存本会话授权并同步', exact: true }).click();
  await expect(page.locator('.cloud-panel [data-cloud-status]')).toContainText('已同步 2');
  expect(puts).toBe(2);
  expect(archive.runs).toHaveLength(2);
  expect(archive.runs.some(r => r.report?.analysis && r.report?.evaluation)).toBe(true);
  expect(uploads.join()).not.toContain(fixtureToken);
  await expect(page.locator('#github-token')).toHaveValue('');
  await page.screenshot({ path: 'test-results/cloud-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/cloud-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const downloadPromise = page.waitForEvent('download');
  await page.locator('#export').click();
  await (await downloadPromise).saveAs('test-results/cloud-backup.json');
  expect(readFileSync('test-results/cloud-backup.json', 'utf8')).not.toContain(fixtureToken);
  await page.reload();
  await page.getByRole('button', { name: '数据备份', exact: true }).click();
  await expect(page.locator('#authorization-status')).toContainText('已配置');
  await page.locator('#clear-auth').click();
  expect(await page.evaluate(() => sessionStorage.getItem('wealth-ssq-github-session'))).toBeNull();
  const context = await browser.newContext();
  await context.route(pattern, async route => {
    expect(route.request().headers().authorization).toBeUndefined();
    expect(route.request().method()).toBe('GET');
    return handler(route);
  });
  const phone = await context.newPage();
  await phone.goto(process.env.SITE_URL || 'http://127.0.0.1:4173');
  await expect(phone.locator('[data-cloud-status]')).toContainText('已同步 2');
  await phone.getByRole('button', { name: '记录与开奖', exact: true }).click();
  await expect(phone.locator('.history')).toHaveCount(2);
  await context.close();
  expect(errors).toEqual([]);
});

test('network failure retains local records, online recovery uploads, invalid authorization clears', async ({ page }) => {
  let disconnected = false, deny = false, archive = { schemaVersion: 1, runs: [] };
  await page.route(pattern, async route => {
    if (disconnected) return route.abort('internetdisconnected');
    if (deny && route.request().headers().authorization) return route.fulfill({ status: 401, json: { message: 'Bad credentials' } });
    if (route.request().method() === 'PUT') {
      archive = JSON.parse(gunzipSync(Buffer.from(route.request().postDataJSON().content, 'base64')).toString());
      return route.fulfill({ status: 200, json: { content: { sha: 'new' } } });
    }
    return route.fulfill({ json: { sha: 'current', size: 100, encoding: 'base64', content: gzipSync(JSON.stringify(archive)).toString('base64') } });
  });
  await page.goto('./');
  await expect(page.locator('[data-cloud-status]')).toContainText('已同步 0');
  await page.getByRole('button', { name: '数据备份', exact: true }).click();
  await page.locator('#github-token').fill(fixtureToken);
  await page.locator('#cloud-auth button[type=submit]').click();
  await expect(page.locator('#authorization-status')).toContainText('已配置');
  await expect(page.locator('[data-sync]')).toBeEnabled();
  disconnected = true;
  await page.locator('#import').setInputFiles({ name: 'offline.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ runs: [record('offline')] })) });
  await expect(page.locator('.cloud-panel [data-cloud-status]')).toContainText('保留在本机');
  await page.reload();
  await page.getByRole('button', { name: '记录与开奖', exact: true }).click();
  await expect(page.locator('.history')).toHaveCount(1);
  disconnected = false;
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await expect(page.locator('[data-cloud-status]')).toContainText('已同步 1');
  expect(archive.runs[0].id).toBe('offline');
  deny = true;
  await page.getByRole('button', { name: '数据备份', exact: true }).click();
  await page.locator('[data-sync]').click();
  await expect(page.locator('.cloud-panel [data-cloud-status]')).toContainText('授权失效');
  expect(await page.evaluate(() => sessionStorage.getItem('wealth-ssq-github-session'))).toBeNull();
  await expect(page.locator('#authorization-status')).toContainText('未配置');
});
