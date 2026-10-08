# Wealth 双色球静态网站

浏览器运行推荐、评分和历史回测；GitHub Actions 每小时从中国福彩网优先检查新开奖，受限时使用 500 彩票网历史页面与 XML 公告核对补充，并发布到 GitHub Pages。无需后端服务器。网站运行文件、Python WASM 和开奖均由同一站点提供，不依赖外部 CDN。

## 本地开发

需要 Node.js 24 和 Python 3.14。

```powershell
Set-Location web
npm.cmd ci
npm.cmd run build
npm.cmd test
python scripts/test_data.py
npm.cmd run test:e2e
npm.cmd run dev
```

Windows 浏览器测试使用已安装 Edge；CI 使用 Chromium。首次使用 CI 浏览器可运行 `npx.cmd playwright install chromium`。

## 改造实施方案

1. 范围仅双色球：推荐参数、单球依据、四模型融合、随机对照、多窗口回测、完整开奖记录、每次推荐独立结算及 JSON 备份。
2. 算法从原项目 `server/main.py` 的纯函数依赖闭包提取到 `public/ssq_core.py`，保留 Python 随机数和评分行为。更新原算法后，在原仓库执行 `python web/scripts/extract_core.py` 并重新验证。没有复制服务端路由、体彩或个人运行数据。
3. WASM Python 在 Worker 内计算，默认 6 红 3 蓝、1 组复式、500 期窗口的六窗口回测与推荐由 Actions 预先计算；自定义方案在浏览器回测，避免阻塞页面。
4. IndexedDB 保存推荐、参数和结算；同一期不同参数分别保存，相同参数保持幂等。Android / 服务端旧备份按 runId 或期号、时间、模型归组，重新逐次结算。清除浏览器或换设备需导出备份；不再提供服务端多设备同步。
5. 自动更新先检查官网响应、号码范围、历史交集和已发布号码一致性。异常失败保留上一版，不覆盖成空数据。官网返回 403 等错误时使用 500 彩票网两个公开数据格式交叉校验，并要求至少十条已发布记录一致（同属一个供应商，不作为两个独立来源）。异常不放行。每小时 17 分运行，有新数据才回测、构建、浏览器验证和发布；手动触发不要求手动录入数据。
6. 按用户授权将当前 `lingfenglxz/wealth` 仓库设为公开，继续使用已有 main 分支。根目录 `.github/workflows/pages.yml` 构建 `web/` 并发布 `web/dist`；Pages 选择 GitHub Actions。不上传未跟踪的 `server.zip` 和已忽略的个人运行文件 `server/data/ssq_state.json`。仓库已有源码及提交历史公开。

## 验收与维护

- 必须通过单元测试、数据校验、算法源 AST 一致性（原仓库内）及真实浏览器生成、持久化、导入导出、手机布局测试。
- 每次生成先重新下载同源 JSON；失败提示重试。最新开奖超过五天则暂停生成并提示检查更新（包含休市情况）；历史数据日期与发布时刻都可查看。
- 一、二等奖奖金浮动，历史模拟仅统计中奖注数，不估计这两项金额；综合分不是中奖概率。
- GitHub 调度存在延迟，不能承诺严格一小时 SLA；按本项目两三天生成一次的使用频率执行自动检查。若官网阻挡 GitHub Runner，必须修复抓取后重新验收，不能以本机抓取成功代替。
- 每小时任务失败会使 Actions 显示失败，仓库所有者可使用 GitHub 默认工作流通知。公共仓库长期没有活动可能停用定时任务；正常开奖会产生数据提交，休市或长期失败时需关注 Actions。
- 默认方案直接读取轻量静态数据；结算由轻量 JavaScript 完成并通过 168 组参数与原 Python 逐字段比对；首次自定义计算需下载约 13 MB Python 运行文件，国内实测可能需要两三分钟，后续浏览器 HTTP 缓存复用。国内访问仍受 GitHub Pages 网络情况影响，本站测试不能代表所有运营商。

## 发布命令

在仓库 `web/` 中执行 `npm ci` 和 `npm run build` 输出 `web/dist`。工作流自动上传 Pages artifact，不需要部署分支，也不需要网站中保存 API 密钥。部署地址为 `https://lingfenglxz.github.io/wealth/`。
