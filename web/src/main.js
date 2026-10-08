import './style.css';
import { defaultPlan, emptyState, loadState, saveState, mergeBackup, validatePlan } from './storage.js';

const app = document.querySelector('#app');
const names = { auto: '多模型融合', recent_focus_v3: '近期优先', hit_rate_v4: '命中率模型', balanced_v2: '均衡模型', baseline_v1: '长期基线', uniform_random_v0: '随机对照' };
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const balls = (values, blue = false) => `<span class="balls">${values.map(n => `<span class="ball ${blue ? 'blue' : ''}">${String(n).padStart(2, '0')}</span>`).join('')}</span>`;
const date = timestamp => new Date(Number(timestamp)).toLocaleString('zh-CN');
let state = emptyState(), data, page = 'recommend', busy = false, message = '', result;
let worker, sequence = 0;
const pending = new Map();
function compute(payload) {
  if (!worker) {
    worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
    worker.onmessage = ({ data: response }) => {
      const task = pending.get(response.id);
      pending.delete(response.id);
      if (response.error) task.reject(new Error(response.error)); else task.resolve(response.result);
    };
    worker.onerror = event => {
      for (const task of pending.values()) task.reject(new Error(event.message || '计算环境加载失败'));
      pending.clear(); worker.terminate(); worker = null;
    };
  }
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    pending.set(id, { resolve, reject });
    worker.postMessage({ id, base: new URL(import.meta.env.BASE_URL, location.href).href, payload });
  });
}
async function refresh() {
  const response = await fetch(`${import.meta.env.BASE_URL}data/ssq.json?t=${Date.now()}`, { cache: 'no-store' });
  if (!response.ok) throw new Error('开奖数据下载失败，请检查网络后重试');
  const next = await response.json();
  if (next.schemaVersion !== 1 || !Array.isArray(next.draws) || next.draws.length < 30 || next.draws[0].issue !== next.latestIssue) throw new Error('网站数据格式异常');
  if (data && next.latestIssue < data.latestIssue) throw new Error('检测到数据回退，请稍后重试');
  data = next;
}
function planKey(plan) {
  return [data.latestIssue, plan.singleCount, plan.compoundCount, plan.redCount, plan.blueCount, plan.recentWindow].join('|');
}
async function settle() {
  const unsettled = state.runs.filter(r => !r.settlement && data.draws.some(d => d.issue === r.targetIssue));
  if (!unsettled.length) return;
  const settlements = await compute({ action: 'settle', draws: data.draws, runs: unsettled });
  state = { ...state, runs: state.runs.map(r => settlements[r.id] ? { ...r, settlement: settlements[r.id] } : r) };
  await saveState(state);
}
function card(prediction, index) {
  const bets = combination(prediction.redBalls.length, 6) * prediction.blueBalls.length;
  return `<article class="ticket"><div class="row"><strong>方案 ${String(index + 1).padStart(2, '0')}</strong><span class="muted">${prediction.redBalls.length} 红 + ${prediction.blueBalls.length} 蓝 · ${bets} 注 / ¥${bets * 2}</span></div><div class="number-row">${balls(prediction.redBalls)}<span class="separator">＋</span>${balls(prediction.blueBalls, true)}</div><p class="muted">${escape(prediction.note)}</p><details><summary>选号依据与单球评分</summary><ul>${(prediction.reasons || []).map(reason => `<li>${escape(reason)}</li>`).join('')}</ul><div class="table-scroll"><table><thead><tr><th>号码</th><th>综合分</th><th>全历史次数</th><th>遗漏期数</th></tr></thead><tbody>${(prediction.ballDetails || []).map(d => `<tr><td>${escape(d.number ?? d.ball)}</td><td>${escape(d.totalScore ?? d.total ?? d.score)}</td><td>${escape(d.fullCount)}</td><td>${escape(d.missCount)}</td></tr>`).join('')}</tbody></table></div></details></article>`;
}
function combination(n, k) { let value = 1; for (let i = 1; i <= k; i++) value = value * (n - i + 1) / i; return Math.round(value); }
function evaluationHtml(evaluation) {
  if (!evaluation) return '';
  return `<section class="panel"><div class="row"><h2>历史验证</h2><span class="muted">${evaluation.foldCount} 个窗口 · 每窗口 ${evaluation.sampleLimit} 期</span></div><p class="muted">按相同注数比较模型；综合分来自历史表现，不是下一期中奖概率。一等奖、二等奖奖金浮动，模拟收益未计入。</p><div class="table-scroll"><table><thead><tr><th>模型</th><th>综合分</th><th>平均红球命中</th><th>蓝球命中率</th><th>中奖期比例</th><th>模拟收益率</th></tr></thead><tbody>${evaluation.modelReports.map(r => `<tr><td>${escape(names[r.version] || r.version)}</td><td><strong>${r.selectionScore.toFixed(2)}</strong></td><td>${r.averageBestRedHits.toFixed(2)}</td><td>${(r.blueHitRate * 100).toFixed(1)}%</td><td>${(r.prizeHitRate * 100).toFixed(1)}%</td><td>${(r.roi * 100).toFixed(1)}%</td></tr>`).join('')}</tbody></table></div></section>`;
}
function recommendationHtml() {
  const plan = state.plan;
  const latest = state.runs.find(r => r.sourceIssue === data?.latestIssue);
  const saved = latest?.report;
  const shown = result || (saved?.analysis && Array.isArray(saved.predictions) && Array.isArray(saved.narrative) ? saved : null);
  return `<section class="panel"><div class="row"><h2>生成下一期方案</h2><span class="tag">浏览器内计算</span></div><form id="plan"><div class="fields">${[['redCount', '复式红球', 6, 20], ['blueCount', '复式蓝球', 1, 16], ['compoundCount', '复式组数', 0, 5], ['singleCount', '单式组数', 0, 10], ['recentWindow', '近期窗口 / 期', 30, 500]].map(([key, label, min, max]) => `<label>${label}<input name="${key}" type="number" min="${min}" max="${max}" required value="${plan[key]}" ${busy ? 'disabled' : ''}></label>`).join('')}<label>模型<select name="modelVersion" ${busy ? 'disabled' : ''}>${Object.entries(names).filter(([key]) => key !== 'uniform_random_v0').map(([key, name]) => `<option value="${key}" ${plan.modelVersion === key ? 'selected' : ''}>${name}</option>`).join('')}</select></label></div><div class="row action-row"><p class="muted">本次 ${plan.singleCount + plan.compoundCount * combination(plan.redCount, 6) * plan.blueCount} 注 · 模拟投入 ¥${2 * (plan.singleCount + plan.compoundCount * combination(plan.redCount, 6) * plan.blueCount)}</p><button class="primary" type="submit" ${busy || !data ? 'disabled' : ''}>${busy ? '计算中，请稍候…' : '生成推荐并保存'}</button></div></form><p class="small muted">首次计算需下载运行文件；自定义参数会重新回测，可能需要数分钟。相同数据和参数会得到相同号码，不重复保存。彩票开奖结果随机，研究评分无法保证中奖。</p></section>${shown ? `<section class="panel"><div class="row"><h2>第 ${escape(shown.analysis.targetIssue)} 期推荐</h2><span class="muted">依据 ${escape(shown.analysis.latestIssue || data.latestIssue)} 期及之前数据</span></div>${shown.predictions.map(card).join('')}<div class="narrative">${shown.narrative.map(n => `<p>${escape(n)}</p>`).join('')}</div></section>${evaluationHtml(shown.evaluation)}` : `<section class="empty panel"><span class="empty-mark">06 + 03</span><h2>从一组有依据的方案开始</h2><p class="muted">默认使用四个模型的历史验证结果加权融合。</p></section>${data ? evaluationHtml(data.defaultEvaluation) : ''}`}`;
}
function historyHtml() {
  return `<section class="panel"><div class="row"><h2>推荐记录</h2><span class="muted">${state.runs.length} 次生成 · 仅保存在此浏览器</span></div>${state.runs.length ? state.runs.map(r => `<details class="history"><summary><span><strong>第 ${escape(r.targetIssue)} 期</strong><span class="muted">${date(r.createdAt)}</span></span><span class="tag">${r.settlement ? '已结算' : '待开奖'}</span></summary>${r.predictions.map(card).join('')}${r.settlement ? `<div class="settlement">${r.settlement.betCount} 注 · 投入 ¥${r.settlement.investedAmount} · 固定奖金 ¥${r.settlement.simulatedPrizeAmount} · 最佳红球 ${r.settlement.bestRedHits} 个 · 蓝球${r.settlement.blueHit ? '命中' : '未命中'}<p class="small">一等奖 ${r.settlement.tierCounts.first} 注，二等奖 ${r.settlement.tierCounts.second} 注；这两项浮动奖金未计入收益。</p></div>` : ''}</details>`).join('') : '<p class="empty muted">生成的方案会保存在这里，开奖后自动结算。</p>'}</section><section class="panel"><div class="row"><h2>近期开奖</h2><span class="muted">展示最近 50 期 / 完整数据 ${data?.draws.length || 0} 期</span></div>${(data?.draws || []).slice(0, 50).map(d => `<div class="draw-row"><span><strong>${d.issue}</strong><small>${d.date}</small></span><div class="number-row">${balls(d.redBalls)}${balls([d.blueBall], true)}</div></div>`).join('')}</section>`;
}
function backupHtml() {
  return `<section class="panel"><h2>数据与备份</h2><p>推荐、参数和结算保存在当前浏览器。换设备或清理浏览器前，请导出备份。</p><div class="backup-grid"><article><h3>导出完整备份</h3><p class="muted">包含 ${state.runs.length} 次推荐、报告和结算；不限制记录数量。</p><button id="export">下载 JSON 备份</button></article><article><h3>导入并合并</h3><p class="muted">支持本网页、Android 应用及原服务器的双色球备份。相同记录自动去重。</p><label class="file-button">选择备份文件<input id="import" type="file" accept=".json,application/json" ${busy ? 'disabled' : ''}></label></article></div></section><section class="panel"><h2>自动更新状态</h2><p>开奖数据来自中国福彩网。GitHub Actions 每小时检查一次，发现新开奖后更新静态网站；调度可能延迟。</p><dl><dt>当前期号</dt><dd>${escape(data?.latestIssue || '未加载')}</dd><dt>数据发布时间</dt><dd>${data ? new Date(data.publishedAt).toLocaleString('zh-CN') : '—'}</dd><dt>完整历史</dt><dd>${data?.draws.length || 0} 期</dd></dl><button id="refresh" ${busy ? 'disabled' : ''}>检查网站最新数据</button><p class="small muted">跨设备记录通过备份迁移；无需登录。网页不连接原后端服务器。</p></section>`;
}
function render() {
  const latest = data?.draws[0];
  app.innerHTML = `<header><div class="brand"><span class="logo">W</span><span>Wealth <small>双色球研究</small></span></div><nav aria-label="主导航">${[['recommend', '推荐'], ['history', '记录与开奖'], ['backup', '数据备份']].map(([id, text]) => `<button data-page="${id}" class="${page === id ? 'active' : ''}">${text}</button>`).join('')}</nav><span class="desktop muted">自动开奖 · 本地计算</span></header><main><div class="title-row"><div><p class="eyebrow">DOUBLE COLOR BALL / RESEARCH</p><h1>${page === 'recommend' ? '下一期，从数据出发' : page === 'history' ? '把每次研究留下来' : '你的数据，由你保管'}</h1></div><button id="top-refresh" ${busy ? 'disabled' : ''}>更新数据 ↻</button></div>${message ? `<div class="notice" role="status">${escape(message)}</div>` : ''}<section class="latest panel"><div><span class="tag">最新开奖</span><h2>${latest ? `第 ${latest.issue} 期` : '正在加载开奖数据…'}</h2><span class="muted">${latest?.date || ''} · ${data?.draws.length || 0} 期历史</span></div>${latest ? `<div class="number-row">${balls(latest.redBalls)}<span class="separator">＋</span>${balls([latest.blueBall], true)}</div>` : ''}</section>${page === 'recommend' ? recommendationHtml() : page === 'history' ? historyHtml() : backupHtml()}<footer>双色球历史统计与模拟研究 · 量力而行，理性购彩</footer></main>`;
  app.querySelectorAll('[data-page]').forEach(button => button.onclick = () => { page = button.dataset.page; render(); });
  for (const id of ['refresh', 'top-refresh']) app.querySelector('#' + id)?.addEventListener('click', () => perform(async () => { await refresh(); await settle(); message = `已检查，当前最新第 ${data.latestIssue} 期`; }));
  app.querySelector('#plan')?.addEventListener('change', event => {
    const form = event.currentTarget;
    const plan = Object.fromEntries(new FormData(form));
    for (const key of Object.keys(defaultPlan)) if (key !== 'modelVersion') plan[key] = Number(plan[key]);
    state.plan = plan;
    const summary = form.querySelector('.action-row p');
    const count = plan.singleCount + plan.compoundCount * combination(plan.redCount, 6) * plan.blueCount;
    summary.textContent = `本次 ${count} 注 · 模拟投入 ¥${count * 2}`;
  });
  app.querySelector('#plan')?.addEventListener('submit', event => {
    event.preventDefault();
    perform(async () => {
      validatePlan(state.plan);
      await refresh();
      const age = (Date.now() - new Date(data.draws[0].date + 'T21:15:00+08:00')) / 86400000;
      if (age > 5) throw new Error('开奖数据已超过 5 天未更新，请先检查 Actions 运行状态；休市期间请等待恢复开奖');
      const key = planKey(state.plan);
      const isDefault = ['singleCount', 'compoundCount', 'redCount', 'blueCount', 'recentWindow'].every(k => state.plan[k] === defaultPlan[k]);
      message = '正在浏览器内计算；自定义方案需要完成历史回测，请保持页面打开'; render();
      const report = await compute({ action: 'recommend', draws: data.draws, plan: state.plan, evaluation: state.evaluations[key] || (isDefault ? data.defaultEvaluation : null) });
      const id = `${key}|${state.plan.modelVersion}`;
      const run = { id, createdAt: Date.now(), targetIssue: report.analysis.targetIssue, sourceIssue: data.latestIssue, plan: { ...state.plan }, predictions: report.predictions, report };
      const next = { ...state, runs: state.runs.some(r => r.id === id) ? state.runs : [run, ...state.runs], evaluations: { [key]: report.evaluation } };
      await saveState(next); state = next; result = report;
      await settle(); message = `第 ${run.targetIssue} 期方案已保存；相同参数不重复记录`;
    });
  });
  app.querySelector('#export')?.addEventListener('click', () => {
    const url = URL.createObjectURL(new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url; link.download = `wealth-ssq-${new Date().toISOString().slice(0, 10)}.json`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  app.querySelector('#import')?.addEventListener('change', event => {
    const file = event.target.files[0]; if (!file) return;
    perform(async () => {
      if (file.size > 30 * 1024 * 1024) throw new Error('备份超过 30 MB，请检查文件');
      const next = mergeBackup(state, JSON.parse(await file.text()));
      await saveState(next); state = next; await settle(); message = `备份已合并，共 ${state.runs.length} 次推荐`;
    });
  });
}
async function perform(action) {
  if (busy) return;
  busy = true; message = ''; render();
  try { await action(); } catch (error) { message = error.message; }
  finally { busy = false; render(); }
}
render();
await perform(async () => { state = await loadState(); await refresh(); await settle(); });
