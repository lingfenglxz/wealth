# WealthLab Light Server

这个轻服务端用于给安卓 App 提供双色球和体彩足球数据。手机端直连官方站点可能遇到 HTTP 403 或安全策略拦截，部署这个服务后，App 只需要访问你自己的服务端地址。

## 启动

```bash
cd server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

也可以直接使用一键启动脚本：

```powershell
.\start-server.bat
```

安卓手机和服务器在同一局域网时，在 App 的“数据”页填写：

```text
http://服务器IP:8000
```

## 接口

- `GET /api/health`
- `GET /api/lottery/ssq/draws?limit=3000`
- `GET /api/lottery/ssq/draws?limit=3000&refresh=true`
- `GET /api/lottery/ssq/recommendations?singleCount=3&compoundCount=1&redCount=9&blueCount=3`
- `GET /api/lottery/sports/football/matches?refresh=true`
- `POST /api/lottery/sports/football/import`
- `GET /api/lottery/sports/football/recommendations?playType=all`
- `POST /api/lottery/sports/football/results/import`
- `GET /api/lottery/sports/football/model-report`

## 服务端数据目录

手机端不再上传开奖或赛事文件，也不再直连官网。手工导入时，把文件放到服务端目录：

- `server/data/imports/ssq_draws.json`
- `server/data/imports/football_matches.json`

App 数据页点击 `更新数据` 后，服务端会优先读取导入目录；没有导入文件时再走缓存/官方抓取/fallback。

## 双色球

双色球开奖会同时缓存在服务端 `server/data/ssq_draws.json` 和 App 本地数据库里。普通推荐直接使用缓存；手动刷新时服务端会做增量同步，优先只拉取本地最新期号之后的新开奖。

服务端还会备份双色球状态到 `server/data/ssq_state.json`：

- 每次生成推荐的独立运行记录（含 `runId` 与生成时间）
- 对应运行的模型报告和号码榜单
- 按运行记录分别保存的历史模拟结算

同一目标期的相同模型、参数和号码只保留首次生成记录，重复查看不会虚增模拟投注；号码或模型参数变化时会新增独立运行与结算。生成推荐时服务端会写入备份；App 更新数据时，如果手机本地缺少这些数据，会从服务端返回的 `stateBackup` 中恢复。

服务端还会在 `server/data/ssq_model_evaluation.json` 缓存默认 6+3 方案的模型评估。手动更新开奖数据成功后，或生成推荐发现最新期号变化时，服务端会以六段、每段 60 期的滚动回测更新缓存；同一期内的后续请求直接复用。报告包含均匀随机对照、各奖级次数、投入、静态返奖和 ROI。

推荐接口会返回：

- 当前模型版本
- 模型历史验证摘要
- 多模型版本对比
- 预算注数内的复式建议
- 每组号码的解释理由
- 每个号码的分项得分和逐球入选原因
- 全量红蓝球评分榜、近 30/60/120 期轨迹和最近出现期号

## 体彩足球

足球彩票接口优先尝试中国竞彩网公开数据；如果被安全策略拦截或网络失败，会使用：

1. `server/data/football_matches.json` 缓存；
2. openfootball 2026 世界杯 JSON，补齐开赛时间并转换为北京时间；
3. FIFA 世界杯赛程抓取/缓存；
4. 内置 104 场世界杯赛程兜底；
5. `server/data/worldcup_2026_matches.json` 里已维护赔率的场次兜底。

服务端会把赛程和赔率分开处理：赛程负责补齐 104 场比赛，竞彩/导入/缓存负责补赔率。没有赔率的场次仍会返回给 App 展示，但不会参与推荐生成。

能自动获取或计算的数据：

- 赛程和开赛时间：优先 openfootball，时间统一保存为北京时间 ISO；
- 赛果：openfootball 有 `score` 后可通过 `POST /api/lottery/sports/football/results/sync` 同步；
- 小组积分：服务端根据赛果自动计算；
- 球队强弱：刷新时尝试读取 FIFA 男足排名页并缓存到 `server/data/football_team_ratings.json`；
- 竞彩赔率：优先中国竞彩网接口，失败时使用缓存或导入文件。

仍建议手工维护的数据：

- `server/data/imports/football_matches.json`：竞彩接口不可用时的赔率/玩法池；
- `server/data/imports/team_ratings.json`：如果 FIFA 排名页结构变化，可手工覆盖球队强弱；
- `server/data/imports/football_match_facts.json`：伤停、停赛、预计首发、天气、战意、轮换等结构化赛事情报。

模板在：

- `server/data/templates/football_matches.template.json`
- `server/data/templates/team_ratings.template.json`
- `server/data/templates/football_match_facts.template.json`

为什么不补这些也能生成推荐：服务端至少可以使用赛程、球队强弱推断、FIFA 排名缓存和已有赔率生成基础推荐。补充手工 JSON 后，模型会更有上下文：

- 赔率文件决定哪些比赛和玩法可以生成推荐；
- 球队强弱文件会覆盖自动排名评分，影响预期进球；
- 赛事情报文件会根据伤停/天气等调整预期进球和数据质量。

支持玩法：

- `had`：胜平负
- `hhad`：让球胜平负
- `crs`：比分
- `ttg`：总进球
- `hafu`：半全场

足球推荐 V1.2 使用 `poisson_v1` 结构化模型：根据球队强弱、主办/中立场和赛事阶段估算双方预期进球，再用 Poisson 比分分布推导胜平负、让球胜平负、比分、总进球、半全场概率，并和去水后的竞彩赔率概率融合。推荐不包含投注、串关奖金计算、下单或中奖承诺。

V1.1 开始，服务端会额外保存足球彩票实验数据：

- `server/data/football_odds_snapshots.json`：每次生成推荐时看到的赔率快照；
- `server/data/football_recommendations.json`：服务端生成过的推荐历史；
- `server/data/football_results.json`：手工导入的赛果；
- `GET /api/lottery/sports/football/model-report`：根据已导入赛果统计命中数、命中率、模拟收益和平均 edge。

赛果导入示例：

```json
{
  "results": [
    {
      "matchId": "wc2026-001",
      "fullTimeScore": "2:1",
      "halfTimeScore": "1:0"
    }
  ]
}
```

推荐返回会包含 `modelName`、`expectedGoals`、`fairProbability`、`modelProbability`、`edge` 和 `dataQuality`。这些字段用于解释推荐价值，不代表真实投注收益承诺。
