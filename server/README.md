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

## 双色球

双色球开奖会同时缓存在服务端 `server/data/ssq_draws.json` 和 App 本地数据库里。普通推荐直接使用缓存；手动刷新时服务端会做增量同步，优先只拉取本地最新期号之后的新开奖。

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
2. `server/data/worldcup_2026_matches.json` 内置世界杯赛程兜底。

支持玩法：

- `had`：胜平负
- `hhad`：让球胜平负
- `crs`：比分
- `ttg`：总进球
- `hafu`：半全场

足球推荐是基于赔率隐含概率、让球、赛事阶段和球队强弱标签的实验性推荐，不包含投注、串关奖金计算、下单或中奖承诺。
