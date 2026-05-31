# WealthLab

一个安卓自用实验项目：双色球号码实验生成器 + A股量化研究工具。

## 功能

- 双色球历史开奖 CSV导入/官方直连/轻服务端更新、服务端评分模型、目标期号展示、逐组解释、历史回测、模型对比、单式和可选红蓝复式推荐。
- A股日线 CSV/HTTP 更新、MA/RSI/MACD/突破策略、信号生成和回测。
- 本地 Room 数据库保存数据，预测在用户手动点击时由服务端即时生成。
- 原生 Kotlin + Jetpack Compose UI；可选轻服务端只负责抓取双色球数据，不接券商交易。

## CSV 格式

双色球：

```csv
issue,date,r1,r2,r3,r4,r5,r6,blue
2024001,2024-01-02,1,6,12,18,24,30,8
```

A股日线：

```csv
date,open,high,low,close,volume
2024-01-02,10.00,10.50,9.90,10.20,1000000
```

## 构建

需要本机安装 Android SDK，并允许 Gradle 下载 AndroidX、Compose、Room、KSP 依赖。

```powershell
gradle test
gradle assembleDebug
```

## 文档

- [使用文档](docs/USER_GUIDE.md)
- [初版审查](docs/REVIEW.md)
- [轻服务端](server/README.md)
