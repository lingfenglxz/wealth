# 搞钱 Design System

## 1. Atmosphere & Identity

这是一个克制、清晰、可信赖的数据工具界面，避免实验性视觉噪音，让用户优先看到推荐结果与关键指标。识别性来自“靛蓝操作线索”：靛蓝只承担品牌、选中态与主要操作，数据内容始终置于白色扁平表面上。

## 2. Color

### Palette

| Role | Token | Light | Dark | Usage |
|---|---|---|---|---|
| Background | `background` | `#F5F6FA` | `#111318` | 页面背景 |
| Surface | `surface` | `#FFFFFF` | `#1A1D24` | 卡片、底栏 |
| Surface variant | `surfaceVariant` | `#F7F8FC` | `#232630` | 次级信息区 |
| Text primary | `onSurface` | `#171A25` | `#F4F5FA` | 标题、正文 |
| Text secondary | `onSurfaceVariant` | `#667085` | `#AEB6C7` | 日期、说明、标签 |
| Border | `outlineVariant` | `#E1E5EE` | `#343946` | 卡片、分隔线 |
| Primary | `primary` | `#5062D9` | `#8D9BFF` | 主按钮、导航选中态、焦点 |
| Primary container | `primaryContainer` | `#EEF0FF` | `#303862` | 选中背景、轻强调 |
| Primary pressed | `primaryPressed` | `#4051C4` | `#AAB4FF` | 按压态 |
| Success | `success` | `#16855B` | `#4CCB92` | 成功状态，仅限语义 |
| Warning | `warning` | `#B56A00` | `#FFB95C` | 警告状态 |
| Error | `error` | `#C63C47` | `#FF8A92` | 错误、未连接 |
| Red ball | `lotteryRed` | `#E5484D` | `#FF6B70` | 双色球红球 |
| Blue ball | `lotteryBlue` | `#3E63DD` | `#7894FF` | 双色球蓝球 |

### Rules

- 主色仅用于交互、选中态与 ROI 主卡，不用绿色表达品牌。
- 成功绿色只用于成功语义；红蓝球保留彩票固有语义色。
- 禁止渐变与装饰性色块；新增颜色前必须先扩展本表。

## 3. Typography

### Scale

| Level | Size | Weight | Line Height | Usage |
|---|---:|---:|---:|---|
| Display | 36sp | 700 | 44sp | ROI 核心数值 |
| H1 | 28sp | 700 | 36sp | 页面品牌标题 |
| H2 | 22sp | 700 | 30sp | 页面区块标题 |
| H3 | 18sp | 600 | 26sp | 卡片标题 |
| Body | 16sp | 400 | 24sp | 正文、主要数据 |
| Body small | 14sp | 400 | 20sp | 次级信息 |
| Caption | 12sp | 500 | 16sp | 元数据、辅助标签 |

### Font Stack

- Primary: Android system sans-serif / browser `system-ui, sans-serif`。
- Numeric: 使用同一字体的等宽数字特性；不引入第二套字体资源。

### Rules

- 正文不低于 14sp；仅元数据可使用 12sp。
- 同一卡片最多使用三个字号层级，避免信息层级碎片化。

## 4. Spacing & Layout

### Base Unit

所有间距来自 **4dp** 基础单位。

| Token | Value | Usage |
|---|---:|---|
| `space-1` | 4dp | 图标与标签 |
| `space-2` | 8dp | 行内元素、紧凑列表 |
| `space-3` | 12dp | 卡片内部小间距 |
| `space-4` | 16dp | 页面边距、标准卡片内边距 |
| `space-5` | 20dp | 主要卡片内边距 |
| `space-6` | 24dp | 区块间距 |
| `space-8` | 32dp | 页面级分组 |

### Grid

- 手机页面左右边距 16dp；平板/桌面预览最大内容宽度 1120px。
- 375px 单列；768px 允许双列信息卡；1280px 保持 1120px 居中布局。
- 底部导航固定四等分，内容区不得产生水平页面滚动。

### Rules

- 主滚动容器每页只保留一个；短横向排行使用带稳定 key 的惰性列表。
- 推荐号码禁止嵌套水平滚动；红球可换行，蓝球独占下一行。

## 5. Components

### Flat Card

- **Structure**：标题行、可选操作、内容区。
- **Variants**：普通白色、靛蓝 ROI、折叠卡片。
- **Spacing**：内边距 `space-4` 或 `space-5`，内容间距 `space-3`。
- **States**：默认、展开、折叠、加载、空、错误。
- **Accessibility**：边框对比清晰；整卡不可替代明确按钮。
- **Motion**：只对展开内容使用 200ms 透明度/尺寸过渡；减少动态效果时立即切换。

### Compact Action

- **Structure**：48dp 最小触控容器内的 36–40dp 视觉按钮，图标可选。
- **Variants**：主操作、次操作、文字操作、步进图标按钮。
- **Spacing**：水平 12–16dp，图标与文字 `space-2`。
- **States**：默认、按压、焦点、禁用、加载。
- **Accessibility**：触控区域至少 48dp；图标按钮必须有内容描述。
- **Motion**：100ms 颜色反馈，不缩放布局。

### Lottery Ball

- **Structure**：纯色圆形与居中两位数字。
- **Variants**：红球、蓝球、中性历史球。
- **Spacing**：球间距 `space-2`，红蓝行间距 `space-3`。
- **States**：默认、选中、禁用。
- **Accessibility**：颜色之外保留“红球/蓝球”文本分组标签。
- **Motion**：无持续动画、无阴影。

### Bottom Navigation

- **Structure**：图标、标签、四等分目的地。
- **Variants**：未选中、靛蓝选中。
- **Spacing**：图标与标签 `space-1`。
- **States**：默认、选中、按压、焦点。
- **Accessibility**：目的地标签常显；触控宽高至少 48dp。
- **Motion**：100ms 颜色与容器切换。

## 6. Motion & Interaction

### Timing

| Type | Duration | Easing | Usage |
|---|---:|---|---|
| Micro | 100ms | ease-out | 按钮、导航按压 |
| Standard | 200ms | ease-in-out | 折叠卡、页面状态切换 |

### Rules

- 只动画颜色、透明度和变换；不加入装饰性滚动动画。
- 尊重系统“减少动态效果”。加载时只对必要进度指示器动画。
- 页面列表与详情数据使用 `collectAsStateWithLifecycle`，并下沉到当前目的地；导航壳只保留连接与全局同步状态。
- 号码排行使用带稳定 key 的 `LazyRow`，避免一次测量全部项目。

### Android Frame Evidence

- 目标：janky frames ≤ 5%，P90 ≤ 16ms，P95 ≤ 24ms，无 >700ms 冻结帧。
- 2026-07-14 基线：改造前设备未连接，因此没有可与改造后同构建、同设备比较的可信基线；不补造基线数据。
- 2026-07-14 真机压力 A/B：BKQ-AN00（1256×2760）安装 debug APK，使用同一脚本连续执行 140 个全屏手势。移除历史开奖与排序行的嵌套横向滚动、缓存可见开奖记录与期号日期映射前为 4515 帧、janky 507 帧（11.23%）、P90 17ms、P95 22ms；优化后为 4893 帧、janky 380 帧（7.77%）、P90 13ms、P95 18ms。慢 UI 帧由 474 降到 353，两个样本均无 >700ms 冻结帧。
- 压力脚本不是最终验收口径：它连续注入 140 个手势，优化后仍记录 5078 次 high input latency，远高于计划的十次纵向滑动。
- 2026-07-14 最终十手势复测：BKQ-AN00（1256×2760）安装最新 debug APK，`dumpsys gfxinfo com.demo.wealth reset` 后在福彩页连续执行 10 次全屏纵向滑动。结果：1029 帧、janky 7 帧（0.68%）、P50 5ms、P90 8ms、P95 8ms、P99 15ms，直方图最长帧约 21ms，无 >700ms 冻结帧。janky ≤ 5%、P90 ≤ 16ms、P95 ≤ 24ms 全部达标。
- GPU P95 为 4–5ms，慢帧主要来自 UI 线程而非 GPU。`dumpsys display` 显示设备工作在 120Hz 物理模式，presentation deadline 约 11.3ms；高刷新率截止线和 debug 构建开销会放大系统 jank 计数。
- 曾尝试用 AndroidX Macrobenchmark 1.4.1 在该设备补充 release/profile 数据，但 Perfetto/Profile 输出阶段稳定进入 `D (disk sleep)`，5–15 分钟内输出目录保持 0 字节。由于未生成应用自身 Profile，且临时基准配置会影响正式 release 签名，相关实验模块未保留在项目中，也未将失败结果包装成通过。
- 诊断结论：福彩滚动的主要成本是历史卡进入视口时的首次组合/测量以及连续输入排队；页面状态订阅下沉、生命周期感知收集、惰性横向列表、扁平卡片、取消推荐横向滚动和本轮历史列表精简均已落地。十手势复测与压力 A/B 均证明短慢帧显著减少，且最终门槛已达标。

### Browser Preview Evidence

- 2026-07-14：Playwright/Chromium 在 375、768、1280px 验证 `design-system/preview.html`，三档 `scrollWidth` 均等于视口宽度，控制台 0 error / 0 warning。
- 截图保存于 `output/playwright/preview-375.png`、`preview-768.png`、`preview-1280.png`（本地验收产物，不纳入版本管理）。

## 7. Depth & Surface

### Strategy

采用 **borders-only**：卡片为白色表面、12dp 圆角、1dp `outlineVariant` 边框、零阴影。ROI 卡使用纯色靛蓝表面，不使用渐变；弹层若出现仍优先使用边框和色阶，不为普通卡片添加 elevation。

| Type | Value | Usage |
|---|---|---|
| Default | 1dp solid `outlineVariant` | 卡片、折叠容器 |
| Divider | 1dp solid `outlineVariant` | 行内分隔 |
| Focus | 2dp solid `primary` | 键盘/无障碍焦点 |
