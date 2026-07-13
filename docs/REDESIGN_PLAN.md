# WealthLab APP 全面改造计划

> 状态：已确认 · 待实施
> 风格：现代金融科技（浅色模式）
> 预览页：`design-system/preview.html`
> 依据：ui-ux-pro-max 设计规则审查

---

## 一、问题诊断（16 项）

### 🔴 移除项（4 项）

| # | 问题 | 位置 | 方案 |
|---|------|------|------|
| 1 | 首页 StatusCard 状态提示卡占用黄金位置 | MainActivity.kt:237 | 移除卡片，改为 TopAppBar 连接状态小图标 + Snackbar 临时提示 |
| 2 | 福彩页单按钮 ChoiceButton 行（game 状态无用） | MainActivity.kt:302-306 | 移除 game 状态和 Row，LotteryPage 直接渲染 |
| 3 | 体彩页单按钮 ChoiceButton 行（空 onClick） | MainActivity.kt:342-345 | 移除 selected=true 的空操作按钮 |
| 4 | CardRow 死代码（定义未使用） | MainActivity.kt:1245-1263 | 移除未使用的 composable |

### 🟠 布局重构（4 项）

| # | 问题 | 方案 |
|---|------|------|
| 5 | 福彩页 11 卡片信息过载 | 重组为 3 分组：「推荐与生成」→「数据分析」→「历史记录」，sticky 分组标题 |
| 6 | 首页布局顺序不佳 | 新顺序：ROI Hero → 推荐 → 足球；移除状态卡，ROI 提升为视觉焦点 |
| 7 | 体彩页三层嵌套展开 | 赛事卡直接显示赔率三栏+推荐摘要，取消 FootballRecommendationInline 嵌套 |
| 8 | 数据页两个重复卡片 | 合并"双色球数据"+"体彩足球数据"为单个"数据同步"卡，内部分 section |

### 🟡 按钮优化（4 项）

| # | 问题 | 方案 |
|---|------|------|
| 9 | 按钮尺寸无规范 | 统一 3 级：btn-lg(52dp,全宽主操作)、btn-md(44dp,常规)、btn-sm(36dp,辅助) |
| 10 | "展开/收起"按钮视觉噪音 | 改为卡片标题右侧图标箭头(▾)，整行可点击 |
| 11 | CountStepper 用 Text("-")/Text("+") | 改为 Material Icons(Remove/Add) + contentDescription |
| 12 | 数据页按钮多余 Row 包裹 | 移除 Row+horizontalScroll，更新按钮用全宽 btn-lg |

### 🟣 架构与交互（4 项）

| # | 问题 | 方案 |
|---|------|------|
| 13 | MainActivity.kt 1393 行单文件，37 个 composable 无分离 | 拆分为 theme/(7) + components/(12) + screens/(4) + MainActivity(~80行) |
| 14 | 静默截断列表 .take(N) 无提示 | 加"查看全部 X 条"或"加载更多"按钮 |
| 15 | 无加载/错误状态 | 按钮加载时 spinner+禁用，失败显示错误卡+重试 |
| 16 | 展开状态 remember 回收丢失 | 改为 rememberSaveable 或提升到 ViewModel |

### 视觉设计问题（v1 已诊断，此处汇总）

- ~30 个硬编码内联颜色（3 种灰、4 种黑、5 种边框灰语义重复）
- 无间距系统（混用 4/6/8/10/12/14/16 + 11/5 非网格值）
- 无动画反馈（展开/折叠瞬间跳变）
- 触控目标过小（MetaPill ~26dp、ChoiceButton 36dp、HeatmapGrid 28dp）
- 图表无障碍缺失（Canvas 无 contentDescription）
- 颜色单独编码（热力图仅靠颜色）
- 无视觉层级（全白卡片+0 阴影）
- 无字体定制（默认 Roboto）

---

## 二、设计系统令牌

### 色彩令牌

```kotlin
// 主色
primary           = #10B981  // 翠绿（比原 #0F6B55 更明亮现代）
primaryHover      = #059669
primaryContainer  = #ECFDF5
onPrimary         = #FFFFFF

// 文字层级（3 级统一，消除 4 种灰/4 种黑）
textPrimary       = #0F172A  // slate-900
textSecondary     = #475569  // slate-600
textTertiary      = #94A3B8  // slate-400

// 背景/表面
background        = #F8FAFC  // slate-50
surface           = #FFFFFF
surfaceVariant    = #F1F5F9  // slate-100

// 边框（2 级统一）
borderSubtle      = #E2E8F0  // slate-200
borderDefault     = #CBD5E1  // slate-300

// 语义功能色
success = #10B981  error = #EF4444  warning = #F59E0B  info = #3B82F6

// 彩球专用（令牌化）
lotteryRed = #DC2626  lotteryBlue = #2563EB

// 热力图梯度（5 级）
heat1=#FEE2E2 heat2=#FCA5A5 heat3=#F87171 heat4=#EF4444 heat5=#B91C1C
```

### 间距令牌（8dp 栅格）

```
xs=4dp  sm=8dp  md=12dp  lg=16dp  xl=24dp  2xl=32dp  3xl=48dp
pagePadding=16dp  cardPadding=16dp  sectionGap=16dp  itemGap=12dp
```

### 字体（Inter + 等宽数字）

```
fontFamily: Inter（下载到 res/font/）
数字用 tabular figures: fontFeatureSettings = "tnum"
Type Scale:
  displayLarge 32/40  titleLarge 22/28  titleMedium 16/24
  bodyLarge 16/24     bodyMedium 14/20  bodySmall 13/18  labelSmall 11/16
```

### 形状令牌

```
shapeSm=8dp  shapeMd=12dp  shapeLg=16dp  shapeXl=20dp  shapeFull=999dp
卡片圆角统一 16dp
```

### 阴影令牌（3 级层级）

```
elevation0: 无阴影
elevation1: 0 1px 2px rgba(15,23,42,0.04) + 0 1px 3px rgba(15,23,42,0.06)  // 卡片
elevation2: 0 4px 12px rgba(15,23,42,0.08)   // 悬浮卡片
elevation3: 0 8px 24px rgba(15,23,42,0.12)   // 对话框
```

### 动画令牌

```
durationFast=150ms  durationNormal=250ms  durationSlow=400ms
easing: EmphasizedDecelerate(入场), EmphasizedAccelerate(出场)
所有展开/折叠用 AnimatedVisibility，所有状态切换用 animate*AsState
```

### 按钮规范（3 级）

```
btn-lg: 52dp 高, 全宽, 主 CTA（生成推荐/更新数据）
btn-md: 44dp 高, 常规操作（展开/设为当前组合）
btn-sm: 36dp 高, 辅助操作
展开/收起: 不用按钮，改为卡片标题右侧 ▾ 图标，整行可点击
```

---

## 三、文件拆分架构

```
app/src/main/java/com/demo/wealth/
├── MainActivity.kt                    # ~80 行，仅 Activity 入口 + WealthApp 导航壳
├── ui/
│   ├── theme/
│   │   ├── Color.kt                   # 色彩令牌（14 个语义色）
│   │   ├── Type.kt                    # Typography + Inter 字体
│   │   ├── Shapes.kt                  # 形状令牌（5 级圆角）
│   │   ├── Elevation.kt               # 阴影令牌（3 级）
│   │   ├── Spacing.kt                 # 间距令牌（8dp 栅格）
│   │   ├── Motion.kt                  # 动画令牌（时长+缓动）
│   │   └── Theme.kt                   # WealthTheme 组合
│   ├── components/
│   │   ├── Panel.kt                   # 卡片容器（16dp 圆角+阴影）
│   │   ├── Ball.kt                    # 彩球（40dp 立体+无障碍）
│   │   ├── MetricCard.kt             # 指标卡（等宽数字+趋势）
│   │   ├── MetaPill.kt               # 标签（48dp 触控+语义色）
│   │   ├── ChoiceButton.kt           # 选择按钮（40dp 触控）
│   │   ├── CountStepper.kt           # 计数器（Material Icons+无障碍）
│   │   ├── SectionHeader.kt          # 分组标题（sticky）
│   │   ├── CollapsibleCard.kt        # 可折叠卡片（箭头展开）
│   │   ├── StatusIndicator.kt        # 连接状态指示器（替代 StatusCard）
│   │   ├── HeatmapGrid.kt            # 热力图（数字标签+无障碍）
│   │   ├── OmissionChart.kt          # 遗漏曲线（坐标轴+无障碍）
│   │   └── LoadingState.kt           # 加载/错误/空状态
│   └── screens/
│       ├── HomeScreen.kt             # 首页（ROI Hero+推荐+足球）
│       ├── LotteryScreen.kt          # 福彩页（3 分组重构）
│       ├── SportsScreen.kt           # 体彩页（扁平化）
│       └── DataScreen.kt             # 数据页（合并卡片）
└── res/font/                          # Inter 字体文件（4 个字重）
```

---

## 四、执行阶段

### 阶段 1：建立设计系统（不改 UI 逻辑）

**新建文件：**
- `ui/theme/Color.kt` - 14 个语义色令牌
- `ui/theme/Type.kt` - Typography + Inter 字体配置
- `ui/theme/Shapes.kt` - 5 级圆角令牌
- `ui/theme/Elevation.kt` - 3 级阴影令牌
- `ui/theme/Spacing.kt` - 8dp 栅格间距令牌
- `ui/theme/Motion.kt` - 动画时长+缓动令牌
- `ui/theme/Theme.kt` - WealthTheme 重写（组合上述令牌）
- `res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`

**验证：** `.\gradlew.bat assembleDebug` 编译通过

---

### 阶段 2：重构组件库（提取到独立文件）

**新建 12 个组件文件：**

| 文件 | 职责 | 关键改进 |
|------|------|----------|
| `Panel.kt` | 卡片容器 | 16dp 圆角 + elevation1 阴影 |
| `Ball.kt` | 彩球 | 40dp + 径向渐变 + 内阴影 + contentDescription |
| `MetricCard.kt` | 指标卡 | 等宽数字 + 趋势箭头 |
| `MetaPill.kt` | 标签 | minHeight 28dp + hitSlop 扩展至 48dp |
| `ChoiceButton.kt` | 选择按钮 | minHeight 40dp + 语义色 |
| `CountStepper.kt` | 计数器 | Material Icons(Remove/Add) + contentDescription |
| `SectionHeader.kt` | 分组标题 | sticky 支持 |
| `CollapsibleCard.kt` | 可折叠卡片 | 标题行 ▾ 图标 + AnimatedVisibility(250ms) |
| `StatusIndicator.kt` | 连接状态 | 小图标 + 语义色（替代 StatusCard） |
| `HeatmapGrid.kt` | 热力图 | 数字标签 + semantics contentDescription |
| `OmissionChart.kt` | 遗漏曲线 | 坐标轴 + 网格线 + semantics |
| `LoadingState.kt` | 状态组件 | Loading/Error/Empty 三态 |

**验证：** `.\gradlew.bat assembleDebug` 编译通过

---

### 阶段 3：拆分 4 个页面

**新建文件：**
- `ui/screens/HomeScreen.kt` - 首页
- `ui/screens/LotteryScreen.kt` - 福彩页（含子组件）
- `ui/screens/SportsScreen.kt` - 体彩页
- `ui/screens/DataScreen.kt` - 数据页

**MainActivity.kt 瘦身：** 从 1393 行降至 ~80 行，仅保留：
- Activity 入口
- WealthApp 导航壳（Scaffold + NavigationBar + when(selected)）

**验证：** `.\gradlew.bat assembleDebug` 编译通过

---

### 阶段 4：布局重构

**移除项（对应问题 1-4）：**
- 删除 StatusCard 在 HomePage 的调用
- 删除 WelfareLotteryPage 的 game 状态 + ChoiceButton Row
- 删除 SportsLotteryPage 的 ChoiceButton Row
- 删除 CardRow composable

**布局重组（对应问题 5-8）：**
- 福彩页：11 卡片 → 3 分组（推荐与生成 / 数据分析 / 历史记录）
- 首页：状态卡 → ROI Hero + 推荐 + 足球
- 体彩页：取消嵌套展开，赛事卡直接显示赔率+推荐摘要
- 数据页：合并两个数据卡片为"数据同步"卡

**按钮优化（对应问题 9-12）：**
- 所有按钮统一 3 级尺寸
- 展开/收起改为 ▾ 图标
- CountStepper 改用 Material Icons
- 数据页按钮改为全宽 btn-lg

**验证：** `.\gradlew.bat assembleDebug` 编译通过

---

### 阶段 5：交互与无障碍增强

- 所有展开/折叠加 `AnimatedVisibility`（250ms）
- 所有触控目标 ≥ 44dp（hitSlop 扩展）
- 图表加 `Modifier.semantics { contentDescription = "..." }`
- 热力图每个格子加数字标签
- 网络请求加 `CircularProgressIndicator` + 按钮禁用态
- 错误状态加重试按钮
- 列表截断加"查看全部 N 条"按钮
- CountStepper 加 `contentDescription`
- 展开状态改用 `rememberSaveable`
- 删除死代码

**验证：** `.\gradlew.bat assembleDebug` 编译通过

---

### 阶段 6：验证

- `.\gradlew.bat test` - 单元测试通过
- `.\gradlew.bat assembleDebug` - 构建成功
- Android 模拟器截图验证真实效果

---

## 五、关键决策

- **字体：** Inter（开源、现代、等宽数字支持好）
- **主色：** #0F6B55 → #10B981（更明亮现代）
- **彩球：** 34dp 扁平 → 40dp 径向渐变立体
- **卡片：** 0 阴影细边框 → 16dp 圆角 + 3 级阴影
- **导航：** 保留手动 when(selected)（不引入 NavHost）
- **不引入新依赖：** 纯 Compose + Material3 + Compose 原生动画 API

---

## 六、验收标准

- [ ] MainActivity.kt < 100 行
- [ ] 所有颜色引用语义令牌（无硬编码 Color(0x...)）
- [ ] 所有间距引用 Spacing 令牌（无硬编码 dp，除特殊值）
- [ ] 所有触控目标 ≥ 44dp
- [ ] 所有图表有 contentDescription
- [ ] 所有展开/折叠有动画过渡
- [ ] 网络请求有加载/错误状态
- [ ] 列表截断有"查看全部"提示
- [ ] `.\gradlew.bat test` 通过
- [ ] `.\gradlew.bat assembleDebug` 通过
- [ ] Android 模拟器视觉验证通过
