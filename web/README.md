# Wealth 双色球静态网站

浏览器运行推荐、评分和历史回测；GitHub Actions 每小时检查开奖并结算公开研究记录，发现新开奖后发布到 GitHub Pages。无需后端服务器。网站运行文件、Python WASM 和开奖由同一站点提供，不依赖外部 CDN；研究记录通过 GitHub 官方 API 跨设备同步。

## 本地开发

需要 Node.js 24 和 Python 3.14。

```powershell
Set-Location web
npm.cmd ci
npm.cmd run build
npm.cmd test
python scripts/test_data.py
python scripts/test_archive.py
npm.cmd run test:e2e
npm.cmd run dev
```

Windows 浏览器测试使用已安装 Edge；CI 使用 Chromium。首次使用 CI 浏览器可运行 `npx.cmd playwright install chromium`。

## 改造实施方案

1. 范围仅双色球：推荐参数、单球依据、四模型融合、随机对照、多窗口回测、完整开奖记录、每次推荐独立结算及 JSON 备份。
2. 算法从原项目 `server/main.py` 的纯函数依赖闭包提取到 `public/ssq_core.py`，保留 Python 随机数和评分行为。更新原算法后，在原仓库执行 `python web/scripts/extract_core.py` 并重新验证。没有复制服务端路由、体彩或个人运行数据。
3. WASM Python 在 Worker 内计算，默认 6 红 3 蓝、1 组复式、500 期窗口的六窗口回测与推荐由 Actions 预先计算；自定义方案在浏览器回测，避免阻塞页面。
4. IndexedDB 保存本地完整副本，公开记录存放在当前仓库 `codex/ssq-records` 分支的 `records/ssq-records.json.gz`。网页启动、恢复联网、返回标签页及前台每五分钟自动合并；配置当前标签页上传授权后，生成或导入记录自动上传。相同参数和号码去重；相同 ID 的不同号码保留独立方案。Android / 服务端备份按 runId 或期号、时间、模型归组，缺少结算时根据公开开奖重新计算。浏览器和 Actions 均使用 GitHub 文件 SHA 比较后写入，并发冲突重新读取合并，避免覆盖其他设备记录。
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

## 跨设备记录同步

打开网站的「数据备份」页。未授权的浏览器自动读取当前仓库公开记录；上传新记录需要仓库写入权限。使用 GitHub Fine-grained personal access token，Repository access 只选择 `wealth`，Repository permissions → Contents 选择 Read and write。令牌只保留在内存和当前标签页的 sessionStorage，不进入源码、IndexedDB、备份或公开记录。关闭标签页或授权到期后，重新配置；手机、电脑分别配置。任何人都能读取公开记录，只有有写入权限的账号可以上传。不要把令牌提交到仓库。

配置授权后，网页自动补传已有本地记录；无需逐条手动更新。没有授权或网络不可用时，推荐仍保存在本机，稍后可补传。换设备时已上传记录自动出现；未上传记录仍仅在原浏览器，清理浏览器前需要同步或导出。网站不自动上传原服务器私有运行文件；旧数据通过用户主动选择 JSON 备份导入。

初次部署需在仓库中创建 `codex/ssq-records` 分支和一个 gzip 压缩的 `{"schemaVersion":1,"runs":[]}` 文件 `records/ssq-records.json.gz`。这个分支只用于数据更新，不触发 main 分支的 Pages 构建。Actions 在每次开奖检查成功后读取并结算云端已有记录，即使没有新开奖或没有浏览器在线也会检查。档案压缩后上限 25 MB、解压后上限 100 MB，超限时提示保留本地备份；建议按年份归档后扩展存储。

公共 API 未授权请求受 GitHub 每小时 60 次/IP 限制；网站每五分钟检查并限制返回页面触发频率。网络失败、授权失效、远程格式异常、并发重试失败都保留本地数据并显示状态，不把失败视为上传成功。数据与令牌都直接发送到 GitHub API，不经过其他服务器。文档：[文件读写接口](https://docs.github.com/en/rest/repos/contents)、[浏览器跨域支持](https://docs.github.com/en/rest/using-the-rest-api/using-cors-and-jsonp-to-make-cross-origin-requests)、[个人访问令牌](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)。
