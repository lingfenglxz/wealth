export function validateSourceStatus(value, data) {
  if (value?.schemaVersion !== 1 || value.policyVersion !== 1 || value.latestIssue !== data.latestIssue || !Array.isArray(value.sources)) throw new Error('开奖与来源状态正在更新，请稍后刷新重试');
  if (value.pending && (!Array.isArray(value.pending.issues) || value.pending.issues.some(issue => !/^\d{7}$/.test(issue)) || !Number.isFinite(Date.parse(value.pending.firstSeenAt)))) throw new Error('开奖确认状态异常，请稍后重试');
  return value;
}
export function pendingIssue(status, data) {
  return status?.pending?.issues.find(issue => issue > data?.latestIssue);
}
export function assertDrawConfirmed(status, data) {
  const issue = pendingIssue(status, data);
  if (issue) throw new Error(`已检测到第 ${issue} 期新开奖，正在等待跨来源确认，请稍后再生成推荐`);
}
