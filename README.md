# WealthLab

双色球静态网站现已加入本项目，使用 GitHub Pages + Actions 自动更新开奖，无需后端服务器。

- [打开双色球网站](https://lingfenglxz.github.io/wealth/)
- [网站实施方案与开发部署说明](web/README.md)
- [自动更新与发布工作流](.github/workflows/pages.yml)

原 Android 应用与轻服务端代码保留，下面说明适用于原应用。

## 功能

- 福彩：双色球历史开奖服务端更新、服务端评分模型、复式推荐、逐组解释、模型历史验证和真实推荐结算。
- 体彩：足球彩票覆盖 104 场世界杯赛程，支持胜平负、让球胜平负、比分、总进球、半全场的赔率展示、`poisson_v1` 实验性推荐、赔率快照、赛果导入和模型报告。
- 数据：手机端只从轻服务端更新数据；服务端负责官方抓取、缓存、目录文件导入，并备份双色球推荐、模型报告和结算信息。
- 原生 Kotlin + Jetpack Compose UI；不包含购彩、下单、支付、账户或公开部署能力。

## 服务端导入目录

手机端不再上传数据文件。需要手工导入时，把文件放到服务端：

- `server/data/imports/ssq_draws.json`
- `server/data/imports/football_matches.json`
- `server/data/imports/team_ratings.json`
- `server/data/imports/football_match_facts.json`

然后在 App 的数据页点击 `更新数据`。

## JSON 格式

双色球 JSON：

```json
[
  {"issue": "2024001", "date": "2024-01-02", "redBalls": [1, 6, 12, 18, 24, 30], "blueBall": 8}
]
```

体彩足球 JSON 使用服务端返回结构，最小形态：

```json
{
  "matches": [
    {
      "matchId": "wc2026-001",
      "matchNum": "001",
      "leagueName": "FIFA World Cup 2026",
      "phase": "小组赛 A组",
      "kickoffTime": "2026-06-11T19:00:00-06:00",
      "homeTeam": "Mexico",
      "awayTeam": "South Africa",
      "handicap": 0,
      "pools": {
        "had": {"H": 1.78, "D": 3.30, "A": 4.60}
      }
    }
  ]
}
```

## 构建

需要本机安装 Android SDK，并允许 Gradle 下载 AndroidX、Compose、Room、KSP 依赖。

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 文档

- [使用文档](docs/USER_GUIDE.md)
- [初版审查](docs/REVIEW.md)
- [轻服务端](server/README.md)
