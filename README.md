# WealthLab

一个安卓自用彩票实验项目：福彩双色球 + 体彩足球彩票。

## 功能

- 福彩：双色球历史开奖 CSV 导入、官网直连、轻服务端更新、服务端评分模型、复式推荐、逐组解释、模型历史验证和真实推荐结算。
- 体彩：足球彩票首版覆盖世界杯赛事，支持胜平负、让球胜平负、比分、总进球、半全场的赛程展示和实验性推荐。
- 数据：本地 Room 数据库保存开奖、推荐、结算、足球赛事；轻服务端优先抓官方数据，失败时使用缓存或内置世界杯赛程。
- 原生 Kotlin + Jetpack Compose UI；不包含购彩、下单、支付、账户或公开部署能力。

## CSV / JSON 格式

双色球 CSV：

```csv
issue,date,r1,r2,r3,r4,r5,r6,blue
2024001,2024-01-02,1,6,12,18,24,30,8
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
