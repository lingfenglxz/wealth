# WealthLab Light Server

这个轻服务端用于给安卓 App 提供双色球历史开奖数据，并在用户手动点击“生成推荐”时即时运行分析模型。手机端直连中国福彩网可能遇到 HTTP 403，部署这个服务后，App 只需要访问你自己的服务端地址。

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

```bash
chmod +x start-server.sh
./start-server.sh
```

安卓手机和服务器在同一局域网时，在 App 的“数据”页填写：

```text
http://服务器IP:8000
```

然后点击“服务端更新”。

## 接口

- `GET /api/health`
- `GET /api/lottery/ssq/draws?limit=3000`
- `GET /api/lottery/ssq/draws?limit=3000&refresh=true`
- `GET /api/lottery/ssq/recommendations?singleCount=3&compoundCount=1&redCount=9&blueCount=3`
- `GET /api/market/stocks/000001/daily?startDate=20180101&adjust=qfq`

返回数据格式：

```json
{
  "source": "cwl",
  "count": 3000,
  "draws": [
    {
      "issue": "2026053",
      "date": "2026-05-12",
      "redBalls": [1, 2, 3, 8, 13, 14],
      "blueBall": 2
    }
  ],
  "predictions": [
    {
      "targetIssue": "2026054",
      "sourceIssue": "2026053",
      "redBalls": [1, 6, 12, 18, 24, 30],
      "blueBalls": [8],
      "score": 2.7314
    }
  ]
}
```

当前推荐模型会综合：

- 全量历史频率
- 最近 120 期加权频率
- 遗漏期数
- 常见奇偶结构
- 常见三区分布
- 历史和值区间
- 连号约束

推荐接口还会返回：

- 当前模型版本
- 历史回测摘要
- 多模型版本对比
- 预算注数内的复式建议
- 每组号码的解释理由
- 每个号码的分项得分和逐球入选原因
- 全量红蓝球评分榜、近 30/60/120 期轨迹和最近出现期号

双色球开奖会同时缓存在服务端 `server/data/ssq_draws.json` 和 App 本地数据库里。普通推荐直接使用缓存；手动刷新时服务端会做增量同步，优先只拉取本地最新期号之后的新开奖，遇到已缓存期号就停止，避免每次都向官网全量翻页。

## A股数据

股票日线默认由服务端通过 `akshare.stock_zh_a_hist` 拉取，App 端只需要填写股票代码后点击服务端更新。后续如果需要更高频、盘口或备用源，可以继续把 `mootdx`、腾讯财经等 provider 接到服务端，而不用让手机端承担接入差异。
