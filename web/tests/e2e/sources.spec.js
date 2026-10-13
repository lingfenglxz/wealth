import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
const dataset = JSON.parse(readFileSync('public/data/ssq.json', 'utf8'));
test('pending new draw blocks generation and source health remains readable on mobile', async ({ page }) => {
  const next = String(Number(dataset.latestIssue) + 1);
  await page.route('https://api.github.com/**', route => route.fulfill({ json: { sha: 'empty', size: 100, encoding: 'base64', content: gzipSync(JSON.stringify({ schemaVersion: 1, runs: [] })).toString('base64') } }));
  await page.route('**/data/source-status.json*', route => route.fulfill({ json: {
    schemaVersion: 1, policyVersion: 1, latestIssue: dataset.latestIssue, healthCheckedAt: '2026-10-13T12:00:00+08:00',
    pending: { issues: [next], firstSeenAt: '2026-10-13T21:30:00+08:00' },
    sources: [{ id: '500', label: '500 彩票网', role: 'primary', status: 'healthy', latestIssue: next }, { id: 'cwl', label: '中国福彩网', role: 'observe', status: 'unavailable' }],
  } }));
  await page.goto('./');
  await expect(page.locator('#plan button')).toBeDisabled();
  await expect(page.getByText(`第 ${next} 期结果待确认，暂缓生成推荐。`, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '数据备份', exact: true }).click();
  await expect(page.getByRole('heading', { name: '来源检查与交叉确认' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '暂不可用', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/sources-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/sources-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
