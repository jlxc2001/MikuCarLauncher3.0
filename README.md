# MikuCarLauncher

A4L 车机桌面项目。

当前版本：v0.1.1-bg32-sidebar-buttons

本版只做 UI 基础：

- 使用用户指定的 32:9 A4L 背景图作为唯一背景图
- 左侧区域加入 7 个按钮：首页、导航、音乐、车辆、全景、应用、我的
- 按钮样式：圆角白色卡片，图标靠左，文字靠右
- 图标使用项目内已有图标文件
- 功能暂不实现，只保留点击选中态
- 隐藏安卓状态栏和导航栏

项目约束：未经用户许可，不删除任何素材文件，不改变已确认 UI。


## V72.1 home-only amap floating card patch

- 保留测试机推荐参数：x=225 y=50 w=1125 h=515 dpi=200。
- 高德悬浮窗现在只允许在 MainActivity 首页显示。
- MainActivity 需要同时满足：Activity 已 resume、窗口有焦点、LauncherCanvasView activeIndex == 0，才会发送 showmap。
- 进入应用抽屉、我的页、设置页、其它 Activity、外部 App、切后台、失焦、销毁时都会发送 closemap。
- AmapFloatingCardController 新增 allowShowOnHome 闸门，避免 onResume / onWindowFocusChanged(true) 从非首页误触发 showmap。
- 修复设置页说明文字中的换行字符串，避免 Java 编译失败。

ADB 调试：
```bash
adb shell am broadcast -a com.autonavi.plus.showmap --ei x 225 --ei y 50 --ei w 1125 --ei h 515 --ei dpi 200
adb shell am broadcast -a com.autonavi.plus.closemap
```


## v17

- 保持已确认主界面 UI 不变。
- 左侧 7 个按钮开始接入功能。
- 车辆按钮打开原车车辆界面。
- 全景按钮打开 360 全景界面。
- 导航/音乐按钮支持从“我的”里选择默认 App。
- 应用按钮打开应用列表。
- 我的按钮打开设置页。


## v18
- 保持当前已确认首页 UI 不变。
- 左侧按钮功能确认：
  - 首页：返回/刷新当前首页。
  - 导航：打开默认导航软件。
  - 音乐：打开默认音乐软件。
  - 车辆：打开 com.ts.MainUI/com.ts.can.audi.xhd.CanAudiWithCDExdActivity。
  - 全景：打开 com.baony.avm360/com.baony.ui.activity.AVMBVActivity。
  - 应用：打开应用抽屉。
  - 我的：打开“我的”页面。
- 我的页面：
  - 车主名称、汽车品牌、签名。
  - 车机桌面设置二级菜单。
- 车机桌面设置：
  - 默认导航软件。
  - 默认音乐软件。
  - 隐藏应用抽屉里的软件。


## v19
- 应用抽屉改为平板样式网格（默认 3 行 × 6 列）。
- 支持点击打开应用，长按隐藏应用。
- 车机桌面设置新增“应用抽屉显示设置”。
- 可调节：App 图标大小、文字大小、网格列数、网格行数。
- 保留“隐藏应用抽屉里的软件”设置。


## v20
- “应用抽屉”和“我的”不再打开独立全屏页面，改为主界面内的大卡片。
- 左侧按钮列在“应用抽屉”和“我的”界面中始终保留。
- 应用抽屉大卡片继续支持默认 3×6 网格、图标大小、文字大小、网格行列设置。
- “我的”大卡片内支持车主名称、汽车品牌、签名修改，并保留车机桌面设置入口。
- 已确认首页 UI、背景、左侧按钮列、1~6 号卡片位置不改动。


## v21
- 应用抽屉加入左右滑动翻页。
- 应用抽屉加入分页指示与当前页提示。
- 应用抽屉支持实体方向键选择应用。
- 支持回车 / DPAD_CENTER 打开当前选中的应用。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v22
- 设置相关界面全部改为 ScrollView 可滑动列表。
- 放大设置页面标题、按钮、输入框和列表项。
- 应用抽屉显示设置加入明确字段标题，避免只看到数值看不清含义。
- 默认导航/音乐选择、隐藏应用管理等次级页面也放大列表项并保持可滑动。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v23
- 应用抽屉左右翻页加入滑动动画。
- 应用抽屉选项框只在实体按键操作后显示。
- 用户触摸屏幕后，应用抽屉选项框自动隐藏。
- 应用抽屉显示设置中，说明文字放到输入框上方，输入框内部不再放说明文字。
- “我的”页面加入“关于软件”，包含作者、B站、抖音、X（原推特）信息和软件介绍。
- 设置相关页面继续保持可滑动大按钮列表。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v24
- 1号卡片加入 AppWidgetHost 小组件承载能力，可在“我的 → 车机桌面设置”中选择/更换高德地图车机版桌面小组件。
- 小组件会以接近填满 1号卡片 的方式显示，切到应用抽屉/我的时自动隐藏，回到首页自动显示。
- Manifest 增加 BIND_APPWIDGET 声明，用于请求系统允许创建/绑定桌面小组件；实际授权仍以车机系统弹窗为准。
- 实体方向键改为全局支持：可控制左侧按钮列、首页 1~6 号卡片、应用抽屉、我的页面。
- 设置界面保留系统原生焦点与可滑动列表，方向键可在按钮/输入框之间移动，回车确认。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v25
- 修复点击“选择 / 更换 1号卡片高德地图小组件”后被系统桌面小组件选择器带回“我的”初级界面的问题。
- 新增本软件内置 WidgetPickerActivity 小组件选择器，优先把高德地图/AutoNavi/AMap 小组件排在前面。
- 选择小组件后再走 AppWidgetManager.bindAppWidgetIdIfAllowed；没有绑定权限时再请求系统小组件绑定授权。
- 1号卡片小组件显示逻辑不变，仍然只在首页显示，切到“应用/我的”时隐藏。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v26
- 1号卡片小组件改用 RoundedAppWidgetHostView 承载。
- 通过 draw() + clipPath 圆角遮罩裁切小组件内容，使高德地图小组件四个角显示为圆角。
- API 21+ 同时启用 Outline 裁剪，clipPath 作为兜底。
- 保持 1号卡片位置、大小、白底卡片样式不变，只处理小组件自身四角。


## v27
- 1号卡片小组件圆角遮罩从 18dp 调整为 8dp，视觉更接近白底卡片圆角。
- 当 1号卡片未设置小组件时，在 1号卡片内显示“设置小组件”快捷按钮。
- 点击 1号卡片内“设置小组件”按钮会进入车机桌面设置，方便快速选择高德地图小组件。
- 首页 UI、左侧按钮列、1~6 号卡片位置保持不变。


## v28
- 2号卡片加入音乐播放器 UI。
- 通过 MediaSessionManager + NotificationListenerService 获取当前播放歌曲名、歌手、专辑封面、播放状态。
- 2号卡片支持上一曲、播放/暂停、下一曲。
- 点击 2号卡片右上角箭头打开默认音乐软件。
- 未开启通知读取权限时，2号卡片显示“开启音乐信息权限”入口。
- 车机桌面设置中加入“开启音乐信息读取权限”按钮。
- Manifest 注册 MusicNotificationListenerService，用于系统授权通知读取/媒体会话访问。
- 首页其他卡片、左侧按钮列、背景、小组件逻辑保持不变。


## v29
- 2号卡片音乐按钮增加按下反馈，按下时显示浅灰底和描边。
- 2号卡片上一曲/播放暂停/下一曲图标视觉尺寸缩小约 5px，触摸热区保持不变。
- 2号卡片专辑封面加入 8px 小圆角。
- 3号卡片加入蓝牙电话模块，显示当前连接蓝牙设备名称；无法获取或未连接时显示 Miku Phone。
- 3号卡片加入静态蓝牙、电量、信号图标，以及右侧静态手机预览。
- 点击 3号卡片或实体按键选中 3号卡片后回车，会尝试打开 com.ts.MainUI/com.ts.bt.BtMusicActivity。
- Manifest 增加蓝牙相关权限声明。
- 首页其他卡片、左侧按钮列、背景、小组件逻辑保持不变。


## v30
- 用用户提供的高清蓝牙/电池/信号/手机图标替换 3号卡片中的原手绘图标。
- 保持 3号卡片功能、布局、文案不变，仅替换图标素材。


## v31
- 首页 4号卡片改为常用软件卡片，默认显示 5 个常用应用。
- 新增“4号卡片常用软件设置”页面，可在桌面设置里配置各个位置的应用。
- 4号卡片不再显示“添加应用”图标，添加入口统一移动到设置里。


## v32
- 3号卡片状态图标改为使用用户提供的“蓝牙+电池+信号”组合图，不再拆分、不重排。
- 3号卡片手机图标缩小，并向左收一点，避免挨到右上角箭头。
- 3号卡片保持整卡点击逻辑，任意区域点击都会打开 com.ts.MainUI/com.ts.bt.BtMusicActivity。


## v33
- 修复 CommonAppsSettingsActivity.java 中提示文字字符串跨行导致的 Javac 编译失败。
- 补齐 LauncherCanvasView 中蓝牙设备列表可能需要的 java.util.Set import。
- 不修改 UI 布局、不删除素材文件。


## v34
- 直接重写 CommonAppsSettingsActivity.java，彻底去掉普通 Java 字符串中的真实换行。
- 修复 GitHub Actions 中 unclosed string literal 编译失败。
- 不修改首页 UI、卡片布局、素材文件。


## v35
- 优化应用抽屉性能：应用扫描和图标加载改为后台线程执行，避免低配车机按回车进入“应用”时主线程卡死。
- 应用列表缓存不再每 2 秒自动重扫；只有首次加载或隐藏应用列表变化时才重载。
- 启动后会后台预热应用抽屉缓存，第一次进入应用抽屉更快。
- 缓存尚未加载完成时，应用抽屉会立即显示“应用列表加载中…”，不会卡住界面。
- 不修改首页 UI、卡片布局、素材文件。


## v36
- 修复 2号卡片“下一首”图标竖条穿进三角形导致显示异常的问题。
- 重新绘制上一首/下一首图标，竖条和三角形分离，视觉更干净。
- 3号卡片手机图标整体向左移动 20px，避免靠近右上角箭头。
- 不修改首页卡片位置、背景、左侧按钮列、素材文件。


## v38
- 5号卡片加入汽车状态模块。
- 续航里程读取 MainApp CarInfoService 的 requestCarBaseInfo() → baseInfo[13]。
- 不直接 import com.ts.can.carinfo.ICarInfoService，改用 Binder transaction 动态探测 int[82]，避免 GitHub Actions 编译失败。
- 车辆数据低频 1500ms 轮询，避免高频轮询导致 MainApp 崩溃。
- 5号卡片中间加入用户提供的 A4L 俯视图素材。
- 根据 baseInfo[61~66] 尝试做四门、后备箱、引擎盖打开反馈动画。
- 忽略参考图右侧剩余电量显示，不绘制电池百分比。
- 不修改已确认的首页卡片布局、左侧按钮列和现有素材文件。


## v39
- 修复 5号卡片“--km”文字挤在一起的问题。
- 原因是先把字体切到 18f 后才 measureText(rangeText)，导致 km 的 X 坐标计算过短。
- 现在先用 32f 大号数字字体测量续航数字宽度，再绘制小号 km。
- 不改车辆数据读取逻辑，不改卡片布局和素材。


## v40
- 3号卡片：去掉右上角 > 箭头，手机图标固定贴右，蓝牙设备名过长时自动省略。
- 3号卡片仍保持整张卡片点击后直接打开 com.ts.MainUI/com.ts.bt.BtMusicActivity。
- 5号卡片：接入用户提供的 4 张开门俯视图（右前门、右后门、左后门、左前门），按门状态自动切换车模。
- 保留原有门/后备箱/引擎盖低频刷新逻辑；若后续门位没有专用图，仍可回退轻量反馈。


## v41
- 6号卡片加入天气卡片。
- 使用高德开放平台天气接口：城市 adcode + API Key 可在 我的 → 车机桌面设置 → 设置 6号天气卡片 中填写。
- 加入 16 张天气图标素材并抠成透明背景 PNG。
- 天气文字自动映射晴天、晴夜、多云、阴天、小雨、中雨、大雨、暴雨、雷阵雨、雪、雨夹雪、雾、霾、沙尘、大风、默认天气。
- 点击 6号天气卡片可打开天气设置；实体按键选中 6号卡片后按回车也可打开天气设置。
- 5号卡片默认关闭状态车模更新为用户补发的正常状态俯视图。
- 不删除任何已有素材，不改变已确认的首页卡片布局和左侧按钮列。


## v42
- 新增全局实体 HOME 键处理。
- 在 MainActivity、应用抽屉、我的、车机桌面设置、天气设置、应用选择、隐藏应用、常用软件设置等本软件界面中，按下实体 HOME 键会回到首页页面。
- 同时兼容 KEYCODE_HOME 与部分车机/键盘可能发送的 KEYCODE_MOVE_HOME。
- MainActivity 已支持收到 HOME 返回时重置为首页：左侧选中“首页”，隐藏应用抽屉/我的/焦点框，显示首页 1~6 号卡片。
- 不改已确认 UI 布局，不删除已有素材。


## v43
- 6号天气卡片改为中国天气免费页面/接口，不再需要高德 Key。
- 默认中国天气城市 ID 改为 101240904，可在 我的 → 车机桌面设置 → 设置 6号天气卡片 中修改。
- 车机桌面设置新增“更换APP背景图片”和“恢复默认APP背景”，用于更换整个 APP 背景图。
- 首页右上车辆旁新增问候文字：上午好/下午好 + 车主名称；副标题读取“签名”。
- 3号蓝牙电话卡片删除右侧手机图标，长设备名可占用更宽区域并自动省略。
- 保持已有 UI 布局与素材，不删除任何素材文件。


## v44
- 新增夜间模式，内置用户提供的 32:9 夜间 A4L 背景图 bg_a4l_night.png。
- 我的 → 车机桌面设置 → 夜间模式设置：可切换日间、夜间、按日出日落自动切换。
- 自动切换支持用户设置日出/日落时间，默认 06:00 / 18:00。
- 夜间模式下首页 1~6 号卡片、应用抽屉大卡片、我的页面大卡片、左侧按钮栏、选中背景统一变为暗色系。
- 夜间模式下左侧未选中图标和文字自动反色，主要文字/次级文字/焦点框/分页点等做暗色适配。
- 3号蓝牙状态组合图在夜间模式下自动变为浅色，避免黑色图标看不清。
- 日间模式保留原 UI 和默认背景；不删除任何已有素材。


## v45
- 5号卡片车模素材重新处理为透明背景 PNG，包括默认关闭状态与四个车门打开状态。
- 保留原白底车模素材备份，文件名后缀为 `_white_bg_backup.png`，不删除素材。
- 5号卡片车模绘制区域略微放大，夜间模式下不再出现白色底块。
- 6号天气卡片中“天气状态 + 温度”右移并放大，利用右侧空白区域，整体更协调。
- 天气图标位置略微右移，避免和放大的天气文字挤在一起。


## v46
- 新增第三方图标包支持：可读取大部分图标包 app 的 `assets/appfilter.xml`，类似 Nova Launcher 的图标包机制。
- 我的 → 车机桌面设置 → 图标包设置 / 导入第三方图标包，可选择已安装图标包或恢复系统默认图标。
- 应用抽屉显示设置中也加入图标包设置入口。
- 应用抽屉与 4号卡片常用软件会使用当前图标包图标。
- 支持单个应用长按弹出操作：选择/打开、重命名、更换图标、隐藏应用、卸载、软件详情。
- “更换图标”支持：从图片选择自定义图标、使用当前图标包图标、恢复系统默认图标。
- 重命名和单独图标覆盖只影响 MikuCarLauncher 内显示，不修改原应用。
- 图标/名称变更后会自动刷新应用抽屉与 4号卡片缓存。


## v47
- “更换APP背景图片”拆分为“更换日间背景图片”和“更换夜间背景图片”。
- 日间模式读取 `app_day_background_uri`，兼容旧版 `app_background_uri`；夜间模式读取 `app_night_background_uri`，没有自定义时使用内置夜间背景。
- 车机桌面设置里新增日间/夜间背景分别显示与选择，恢复默认会同时恢复日间和夜间背景。
- 长按应用图标 → 更换图标：新增“从当前图标包选择任意图标”。
- 新增图标包图标选择器：读取当前图标包 appfilter.xml 中列出的所有图标名称，进入后以 8 列网格显示，可为单个应用选择任意图标。
- 保留原有“从图片选择自定义图标”“使用当前图标包对应图标”“恢复系统默认图标”。


## v48
- 新增 Live2D 装饰模型层，层级位于 APP 背景之上、所有功能卡片和左侧按钮之下。
- MainActivity 层级拆分为：背景层 → Live2D 装饰层 → 桌面 UI / 功能卡片层 → 1号卡片 AppWidget 层。
- 新增 `LauncherBackgroundView`，专门绘制日间/夜间背景，原 `LauncherCanvasView` 可关闭背景绘制。
- 新增 `Live2DDecorView`，使用透明 WebView 承载 Live2D 模型。
- 新增 `Live2DSettingsActivity`：我的 → 车机桌面设置 → Live2D 装饰模型设置。
- Live2D 可配置启用/关闭、model3.json/model.json 路径或 URL、位置 X/Y、宽高、模型缩放。
- 默认放置区域为首页中间偏右空白区域：x=1188, y=246, w=520, h=300。
- 支持 `/sdcard/MikuCarLauncher/live2d/xxx/model3.json` 这类本地模型路径，也支持 http/https/file/content URI。
- 当前版本先用 WebView + 在线 JS 运行库方案，首次加载运行库需要联网；模型文件夹内 moc3、贴图、physics 等资源需保持 Live2D 原目录结构。


## v49
- Live2D 设置方式重做：不再要求用户手动输入模型路径。
- 新增“选择 Live2D 模型文件夹”：使用 Android 文件夹选择器选择包含 `model3.json` / `model.json` 的模型文件夹。
- 选择后会把整个模型文件夹复制到应用内部目录，再用内部 `file://` 路径加载，减少 WebView 读取 `content://` 或外部存储导致模型不显示的问题。
- 新增 `Live2DModelImporter`：递归复制模型文件夹，自动寻找 `.model3.json`、`model.json` 或 JSON 模型文件。
- 新增 `Live2DAdjustActivity`：位置和大小不再靠输入数值，直接用手拖动调整位置、双指捏合调整大小。
- `Live2DDecorView` 新增调整模式：支持拖动、捏合、自动保存设计坐标。
- Live2D 调整页面会显示一个淡蓝色可操作区域，方便用户知道当前可拖动/捏合的位置。
- Live2D Web 页面增加本地运行库优先加载：
  - `/sdcard/MikuCarLauncher/live2d/runtime/pixi.min.js`
  - `/sdcard/MikuCarLauncher/live2d/runtime/live2dcubismcore.min.js`
  - `/sdcard/MikuCarLauncher/live2d/runtime/pixi-live2d-display.min.js` 或 `index.min.js`
- 如果本地运行库不存在，再尝试在线 CDN。
- 新增 `Live2DAdjustActivity` 到 Manifest。


## v50
- Live2D 运行库改为 APK 内置离线加载。
- `live2d_decor.html` 加载顺序调整为：
  1. `file:///android_asset/live2d/runtime/`
  2. `/sdcard/MikuCarLauncher/live2d/runtime/`
  3. 在线 CDN 兜底
- 新增 Gradle 任务 `prepareOfflineLive2DRuntime`，编译时自动下载并写入 `app/src/main/assets/live2d/runtime/`：
  - `pixi.min.js`
  - `pixi-live2d-display.min.js`
  - `live2dcubismcore.min.js`
  - `live2d.min.js`
- APK 安装到车机后，Live2D 运行时不再依赖车机联网。
- 如果 GitHub Actions / 编译环境无法访问外网，也可以手动把上述 4 个 JS 文件放到 `app/src/main/assets/live2d/runtime/`，任务会自动复用本地文件。
- 设置页文案已更新为“v50 已把 Live2D 运行库改成 APK 内置离线加载，车机运行时不需要联网”。


## v51
- Live2D 显示层改成全屏透明层，不再通过拉伸 WebView 框来缩放模型。
- Live2D 调整逻辑改为直接调整模型中心点和模型缩放：
  - 单指拖动：移动模型位置。
  - 双指捏合：直接改变人物大小。
- 修复 v49/v50 中“框变大但人物大小不明显变化”和捏合时模型突然闪大/占满框的问题。
- Live2D 调整页面新增模拟首页卡片位置：
  - 左侧按钮栏
  - 1~6 号功能卡片
  - 建议模型区域虚线框
  方便用户把模型放到不会挡 UI 的位置。
- Live2D Web 页面加入 Idle 动作触发逻辑，会尝试读取模型 motion group 并循环播放 Idle / idling 等动作。
- 即使模型没有可播放 Idle 动作，也会叠加轻微呼吸/漂浮兜底，避免人物完全静态。
- 旧版 x/y/w/h 配置会自动迁移为中心点位置，保留兼容。


## v52
- 去掉 v51 的轻微呼吸/漂浮兜底动画，不再用假动作模拟 Live2D 动态。
- 导入 Live2D 模型文件夹时，会递归统计动作文件：
  - `.motion3.json`
  - `.mtn`
  - `.motion.json`
- Live2D 设置页会显示当前模型读取到的动作文件数量。
- `live2d_decor.html` 改为读取模型 JSON 里的 Motion Groups，并随机播放模型自带动作。
- 动作播放逻辑：
  - 加载后先随机播放一次模型自带动作。
  - 之后每 6~12 秒随机选择一个动作组、随机选择该动作组内一个动作播放。
  - 优先包含 Idle / idle / Idling / idling / TapBody / tap_body 等动作组，但不会只限于 Idle。
- 如果模型文件夹本身没有动作组，则人物保持静态，不再播放假的呼吸/漂浮。


## v53
- Live2D 模型导入时，如果没有检测到动作文件，会自动给 Cubism 3/4 `.model3.json` 注入通用动作：
  - 通用待机动作
  - 通用微笑动作
  - 通用点头动作
- 通用动作会写入模型缓存目录 `motions_default/`，并自动挂到 `FileReferences.Motions` 的 `Idle` 与 `TapBody` 动作组。
- 如果没有检测到表情文件，会自动给 Cubism 3/4 `.model3.json` 注入通用表情：
  - 微笑
  - 眨眼
  - 惊讶
- 通用表情会写入 `expressions_default/`，并自动挂到 `FileReferences.Expressions`。
- 设置页会显示动作文件数量与表情文件数量。
- 点击首页中间 Live2D 人物区域，会切换下一个动作，并随机切换表情。
- 修复双指捏合缩放后，如果两根手指不是同时离开，剩余那根手指会把模型拖走的问题。现在双指缩放结束后会忽略剩余手指移动，直到所有手指松开。
- 不再使用 v51 的假呼吸/漂浮动画；只有模型自带动作或自动注入的通用动作会播放。
- 通用动作/表情基于常见 Cubism 参数名，若模型参数命名非常特殊，效果可能不明显，但不会破坏原模型文件夹，导入的是应用内部缓存副本。


## v54
- Live2D 增加专用显示遮罩，只对 Live2D 模型生效。
- 遮罩不会遮挡 App 背景图，也不会改变 5 号卡片、6 号卡片或任何卡片的形状。
- 遮罩区域按 2560×720 设计坐标处理：Live2D 只允许显示在底部卡片顶部 y=546.5 以上。
- 解决模型腿部从 5 号卡片下面露出来的问题。


## v55
- 默认动作增强：无动作模型会自动注入 4 个通用动作：
  - 待机
  - 眨眼
  - 微笑
  - 点头
- 保留 v53 的通用表情注入：
  - 微笑
  - 眨眼
  - 惊讶
- Live2D 显示层新增随机眨眼逻辑：
  - 不依赖 motion 文件是否播放成功
  - 尽量直接修改 `ParamEyeLOpen` / `ParamEyeROpen`
- 默认待机效果改成参数级模拟：
  - 轻微头部转向
  - 轻微身体摆动
  - 呼吸参数
  - 头发/服装辅助参数尝试
  - 不再使用整体缩放/漂浮那种假动作
- 只有模型无自带动作或使用自动注入的默认动作时，才启用参数级默认待机，避免干扰有完整动作文件的模型。
- 点击人物：
  - 有自带动作时：切换下一个动作并随机表情
  - 使用默认动作时：切换微笑/点头/看向/眨眼等默认效果，并随机表情
- 保留 v54 的 Live2D 专用遮罩，避免腿部从 5 号卡片下方露出。
- 说明：没有直接内置 Live2DViewerEX 的专有动作文件，而是做了接近 ViewerEX 默认待机观感的通用参数级效果。


## v56
- 修复 v55 仍然露腿的问题：
  - 除 Pixi mask 外，额外给 Live2D WebView 内部 stage 做 CSS 裁切。
  - 只裁切 Live2D 内容，不遮挡 App 背景，也不改变 5 号卡片形状。
  - 调整页面 `clip=0`，不会影响用户拖动/捏合调位置。
- 修复默认 `.motion3.json` 可能不生效的问题：
  - 修正 `Meta.TotalSegmentCount` 与 `Meta.TotalPointCount`，不再使用粗略估算。
  - 之前部分运行库可能因为 motion3 元数据不匹配而忽略默认动作。
- 默认待机效果加强：
  - 同时兼容 Cubism 3/4 参数名 `ParamAngleX` 等，以及 Cubism 2 风格参数名 `PARAM_ANGLE_X` 等。
  - 参数设置前会检查参数是否存在，避免一个不存在的参数名挡住后续兼容参数名。
  - 没有头部/身体参数时，增加很小幅度的容器级兜底动作，保证能看到轻微待机变化。
- 眨眼仍然通过 `ParamEyeLOpen` / `ParamEyeROpen` 和 `PARAM_EYE_L_OPEN` / `PARAM_EYE_R_OPEN` 尝试实现。
- 保留点击人物切换动作和随机表情逻辑。


## v57
- 夜间模式下新增 Live2D 单独变暗效果。
- 在“夜间模式设置”中新增“Live2D 夜间变暗透明度（0~85）”。
- 默认透明度为 35%，0 表示夜间不压暗 Live2D，85 为最大压暗。
- 实现方式为只给 Live2D 的透明 canvas 加亮度滤镜，相当于只对 Live2D 人物套一层黑色遮罩。透明区域仍然透明，不会遮挡 App 背景图片，也不会影响任何功能卡片。

## v58
- Live2D 低配车机优化：降低 Pixi 渲染分辨率、关闭抗锯齿、限制帧率到约 20fps，并使用 low-power 渲染偏好。
- Live2D WebView 优化：开启缓存优先、绑定 Renderer 优先级，增加 WebGL context lost/restored 处理。
- 修复 Live2D 频繁 applySettings 导致重复 reload 的问题：普通设置刷新不再每次带时间戳重载，只有主动 reload 才改变 reload token。
- 点击左侧“首页”按钮、返回键从其他页回首页、HomeKeyHelper 回首页时，会主动 reload Live2D，防止低配车机把模型卡没后无法恢复。
- Launcher 返回键语义修正：返回键不再退回上一个应用；在本软件内按返回键回首页，首页下返回键无操作。
- MainActivity 增加 singleTask / clearTaskOnLaunch，作为 HOME Launcher 时尽量回到唯一桌面实例。
- 所有子页面继续通过 HomeKeyHelper 支持实体 HOME 键回到首页。
- 应用抽屉加入“APP 略缩图缓存”：首次扫描会把应用图标渲染成 PNG 缓存在应用内部目录，之后优先读取本地略缩图和应用索引，避免每次现场 loadIcon。
- 应用抽屉缓存会在检测到安装/卸载/更新应用后后台重建；图标包、隐藏应用、重命名变化也会触发重建。
- 应用抽屉显示设置新增：手动更新 APP 略缩图缓存、清空 APP 略缩图缓存。


## v59
- 修复返回键逻辑：MainActivity 里返回键只回首页；已经在首页时返回键无操作，不再 finish，也不再露出上一个 App。
- 返回键回首页不会触发 Live2D reload，避免“桌面被重新加载”的感觉；只有点击左侧“首页”按钮才主动 reload Live2D。
- 增加 onKeyDown / onKeyUp 双重兜底，消费 BACK / ESCAPE / HOME / MOVE_HOME，防止部分车机只在 UP 阶段把返回键交给系统。
- 去掉 `clearTaskOnLaunch=true`，避免 Launcher 任务栈被系统清理导致返回/回桌面行为异常。
- 应用抽屉不再在桌面 onResume、窗口聚焦、任意菜单点击时主动预加载/扫描应用。
- 应用抽屉不再每次打开都后台计算安装列表签名；平时只用缓存，安装/卸载/更新 App 时再由 PackageChangeReceiver 重建缓存。
- 新增 `PackageChangeReceiver`：监听 PACKAGE_ADDED / PACKAGE_REMOVED / PACKAGE_CHANGED / PACKAGE_REPLACED 后后台更新 APP 缩略图缓存。
- APP 缩略图缓存增加内存缓存，同一进程内再次打开应用抽屉不再重复读磁盘 PNG。
- APP 缩略图缓存尺寸从 96dp 降到 72dp，降低车规级慢存储的读取和解码压力。
- 车机桌面设置主页面直接加入“手动更新应用列表 / APP 缩略图缓存”和“清空应用列表 / APP 缩略图缓存”，不用再进二级菜单找。
- AppDrawerSettings 文案从“略缩图”统一改成“缩略图”。
- Live2D 本次不改动，保留 v58 的重载机制。


## v60
- 修复 v59 后部分车机上 Live2D 不自动加载的问题。
- 不改 Live2D 渲染画质 / 帧率 / 动作逻辑，只恢复 Live2D 的保险重载机制。
- 首次进入首页、从外部 App 回到首页、窗口重新获得焦点时，会延迟检查并只重载 Live2D WebView。
- 返回键 / HOME 键仍然只回首页，不 finish Launcher，不露出上一个 App。
- 在首页按返回键仍然无操作，不触发桌面重建，也不强制重载 Live2D。
- 加入 12 秒防抖，避免低配车机短时间内反复 reload Live2D。


## v61
- 修复 v60 / v59 后 Live2D 在首页和预览页都无法加载的问题。
- 回滚 Live2D WebView 页面和 Live2D 渲染逻辑到 v56 稳定加载版本。
- 保留 v60 的返回键 / HOME 键 / 应用缓存修复。
- 保留 Live2D 专用遮罩、默认动作/表情、点击人物切换动作等 v56 前后的核心功能。
- 暂时移除 v58 的低画质 / 限帧 / low-power WebGL 优化参数，因为这些参数在部分车机 WebView 上可能导致 Live2D canvas 初始化失败。
- 保留单独 reloadLive2D 机制：点击首页、首次进入首页、从外部 App 回桌面仍会只重载 Live2D WebView，不重建桌面 UI。
- 首次首页 Live2D 保险 reload 延迟调到 1200ms，避免车机 WebView 初始化较慢时加载过早失败。


## v62
- Live2D 设置页新增画质与帧率输入框：
  - Live2D 画质倍率：0.5~2.0，最高 2.0，默认 1.0。
  - Live2D 帧率：15~60，最高 60，默认 60。
- 画质倍率会传给 Live2D Web 页面并调整 Pixi renderer resolution：
  - 1.0 = 稳定默认。
  - 1.5 / 2.0 = 更清晰，但更吃 GPU / WebView。
  - 0.5 / 0.75 = 更省性能，但会更糊。
- 帧率会写入 Pixi ticker.maxFPS：
  - 低配车机建议 30 或 45。
  - 想要顺滑可以设置 60。
- 修复刚导入 Live2D 文件夹后立刻进入“拖动/捏合调整位置大小”时，第一次可能加载不出来的问题：
  - 调整页首次进入会自动延迟重载一次 Live2D。
  - 调整页新增“重载模型”按钮。
  - 从调整页退出再进入仍可正常加载。
- 保留 v61 的稳定 Live2D 加载逻辑，不重新启用 v58 的 low-power WebGL 参数。

## v63
- 修复 Live2D 调整页自动重载后，“恢复默认位置 / 重载模型 / 完成”按钮被 Live2D WebView 压到下层，导致无法点击的问题。
- 调整页重载模型时不再对 Live2DView 执行 bringToFront。
- 新增 bringAdjustOverlaysToFront()，每次重载、恢复默认、onResume、窗口重新获得焦点后，都会把模拟卡片层、提示文字、底部按钮栏重新置顶。
- Live2D 首页显示、画质倍率、帧率设置不变。


## v64
- 修复 Live2D 在桌面首页显示时会“闪一下”的问题。
- 不再在首次进入首页、onResume、窗口重新获得焦点时自动强制 reload Live2D。
- 从应用抽屉 / 我的 / 子页面回首页时，只显示现有 Live2D，不重新加载。
- 保留手动修复机制：如果已经在首页，再点一次左侧“首页”按钮，才会手动 reload Live2D。
- 恢复夜间模式下 Live2D 单独变暗：
  - Live2DDecorView 重新传入 `night` 和 `dim` 参数。
  - live2d_decor.html 对 Live2D canvas 使用 brightness filter。
  - 只压暗 Live2D 人物，不压暗 App 背景图和功能卡片。
- 保留 v62 的画质倍率和帧率设置。
- 保留 v63 的调整页按钮层级修复。


## v67
- 回到 V64 稳定分支继续修复，不基于 V65/V66 自适应缩放版本。
- Live2D 稳定性修复：
  - live2d_decor.html 每 2 秒向 Android Java 层发送心跳。
  - Live2DDecorView 增加 watchdog。
  - 首页显示 Live2D 时，如果超过约 10 秒没有心跳，自动 soft reload。
  - 连续恢复失败后执行 hard reload：先 about:blank，再重新 load 模型页面。
  - 页面 onPageFinished 后会延迟同步两次模型位置，降低偶发错位。
  - watchdog 每 5 秒同步一次模型中心点/缩放，修复长时间运行后偶发偏移。
- HOME 键回首页修复：
  - MainActivity 保持 singleTask。
  - onNewIntent 无论是否带 extra，都回到首页。
  - 动态注册 ACTION_CLOSE_SYSTEM_DIALOGS，兼容部分 Android 10 车机的 homekey/recentapps 回调。
  - 返回键逻辑保持：非首页回首页，首页返回无操作。
- 长按应用“卸载”修复：
  - 先尝试 Intent.ACTION_UNINSTALL_PACKAGE。
  - 失败后降级 Intent.ACTION_DELETE。
  - 再失败打开系统应用详情页，并 Toast 提示。
- 保留 V64 的 Live2D 夜间变暗、画质/帧率、调整页按钮层级等逻辑。

## v67.1
- 修复 MainActivity 缺少 BroadcastReceiver / Context / IntentFilter import 和 homeKeyReceiver 字段导致的编译错误。
- 功能逻辑不变。

## v68
- 基于 v67.1 编译修复版继续开发。
- 新增转向音 / 转向提示功能：
  - 设置入口：我的 → 车机桌面设置 → 转向音 / 转向提示设置。
  - 可选择自定义 WAV / audio 文件。
  - 打左/右转向时循环播放，停止转向时停止播放。
  - 屏幕顶部显示左转 / 右转闪烁箭头提示。
- 转向状态读取：
  - 默认读取 VehicleDataProvider baseInfo[67] / baseInfo[68]，激活值 1。
  - 设置页允许修改左转索引、右转索引、激活值，方便实机调试。
- VehicleDataProvider 轮询间隔从 1500ms 调整到 650ms，以便转向反馈更及时。
- 保留 v67.1 的 Live2D watchdog、HOME 回首页、卸载 fallback 修复。

## v69.1
- 基于 v68，而不是 v69。
- 撤销 v69 中错误的 baseInfo[2] 868/876 转向判断。
- 保留转向音 WAV 选择、循环播放、顶部闪烁箭头。
- 新增转向调试浮层：
  - 设置入口：我的 → 车机桌面设置 → 转向音 / 转向提示设置 → 显示转向调试浮层。
  - 桌面上显示当前 leftTurn/rightTurn、rpm/range、数据来源、关键 baseInfo 值、变化字段 changed。
- VehicleDataProvider Snapshot 新增 debugText / dataSource。
- 当前仍使用 baseInfo 手动备用模式，目的是确认 Launcher 读到的数据与 Hook Demo 是否同源。
- 如果调试浮层里 leftTurn/rightTurn 仍一直 false，而 Demo 为 true，说明 Launcher 还没有接入 Demo Hook 层的真实 parsed leftTurn/rightTurn。

## v70
- 基于 v69.1 安全调试版继续开发。
- 内置 Hook 数据服务，不再手动猜转向 baseInfo 索引。
- VehicleDataProvider 改为同时绑定：
  - com.ts.MainUI/com.ts.can.carinfo.CarInfoService
  - com.ts.MainUI/com.ts.tsspeechlib.car.TsCarService
- 数据解析逻辑按 CarDataHook / VehicleDataCore Demo 迁移：
  - baseInfo[2] speed
  - baseInfo[3] rpm
  - baseInfo[13] rangeKm
  - baseInfo[17]/[18] left/right turn fallback
  - baseInfo[19] driver seatbelt
  - baseInfo[20] high beam
  - baseInfo[30] fuel level
  - baseInfo[36] passenger seatbelt
  - baseInfo[61~64] four doors
  - baseInfo[65] trunk
  - baseInfo[66] hood
  - TsCarService code 17 total mileage
  - code 18 oil leftover
  - code 19 left turn
  - code 20 right turn
  - code 21 hazard
  - code 22 speed
  - code 25 front radar
  - code 26 rear radar
- 桌面车辆状态、转向音、转向箭头现在都使用 VehicleDataProvider.Snapshot 的 Hook parsed 数据。
- 设置新增：
  - 我的 → 车机桌面设置 → 查看 Hook 原始数据 / 可读状态 / 轮询设置
  - 可查看原始 baseInfo、frontRadar/rearRadar、可读状态和调试数据
  - 可调轮询率 500~10000ms，默认 650ms
- 转向音设置页简化：
  - WAV 选择
  - 启用转向音
  - 显示转向调试浮层
  - 轮询间隔
  - 跳转 Hook 数据页面

## v71
- 基于 V70 稳定版继续开发。
- 新增 HUD 局域网广播功能：
  - 使用 VehicleDataProvider.Snapshot 的 Hook parsed 数据。
  - UDP JSON 广播，默认 255.255.255.255:36970。
  - 默认启用，默认 200ms 广播一次。
  - 支持改成指定旧手机 IP 定向发送。
- 新增 VehicleDataBroadcaster：
  - protocol=MikuCarHUD
  - version=1
  - source=MikuCarLauncher
  - seq 递增序号
  - speedKmh / rpm / rangeKm / fuelLevel / totalMileageKm
  - driverSeatbelt / passengerSeatbelt
  - doors.frontLeft/frontRight/rearLeft/rearRight/trunk/hood
  - leftTurn / rightTurn / highBeam / hazard
  - frontRadar / rearRadar / rawBaseInfo
  - dataSource / debugText
- 设置新增：
  - 我的 → 车机桌面设置 → HUD 数据广播设置
  - 可开关广播、设置广播地址、端口、广播间隔
  - 车辆 Hook 数据页面也加入 HUD 广播设置入口
- Manifest 增加 ACCESS_WIFI_STATE / CHANGE_WIFI_MULTICAST_STATE 兼容部分车机 UDP 广播限制。

## v71.1
- 基于 V71 HUD UDP 广播版。
- HUD 数据广播设置页新增“车机当前 IP”显示：
  - 遍历 NetworkInterface，显示所有非回环 IPv4。
  - 增加“刷新车机 IP 显示”按钮。
  - 方便 HUD 接收端在需要手动填写车机 IP 时使用。
- 车机桌面设置页的 HUD 广播摘要中也显示第一个可用 IPv4。

## v72
- 基于 V71.1 稳定版创建新分支。
- 备份文件：MikuCarLauncher_V71_1_stable_hud_ip_display_backup.zip。
- 1号卡片不再使用高德 AppWidget，改为悬浮版高德地图“伪嵌入”。
- 新增 Kotlin 模块 AmapFloatingCardController：
  - 测量 mapCardContainer 的屏幕绝对坐标。
  - 支持 insetDp，默认 6dp。
  - 发送广播 com.autonavi.plus.showmap，extras: x/y/w/h。
  - onPause / 失焦 / 离开首页时发送 com.autonavi.plus.closemap。
  - 检测 com.autonavi.amapautoys 是否安装。
  - 提供打开悬浮版高德地图悬浮窗权限设置的方法。
  - 提供 Android 8+ TYPE_APPLICATION_OVERLAY、旧版 TYPE_PHONE 类型常量给高德悬浮端参考。
- MainActivity 新增透明 mapCardContainer，用于测量 1号卡片内地图区域。
- LauncherCanvasView 1号卡片改为显示悬浮地图提示，未安装 com.autonavi.amapautoys 时提示“未安装悬浮版高德地图”。
- DesktopSettingsActivity 中 1号卡片设置改为悬浮版高德权限入口，并保留清理旧 AppWidget 配置按钮。
- adb 调试：
  - adb shell am broadcast -a com.autonavi.plus.showmap --ei x 100 --ei y 80 --ei w 900 --ei h 500
  - adb shell am broadcast -a com.autonavi.plus.closemap


## V72.1

- 增加“1号卡片悬浮高德设置”页面。
- 可调节：inset 内缩 dp、X/Y 偏移 px、宽高缩放百分比、强制宽高 px、高德显示 DPI。
- `AmapFloatingCardController` 改为从 `SharedPreferences` 读取悬浮高德参数。
- `com.autonavi.plus.showmap` 广播 extras 扩展为 `x / y / w / h / dpi`，旧版高德端不读取 `dpi` 时仍保持兼容。
- 设置页支持“保存并测试显示悬浮地图”和“关闭悬浮地图”。
- ADB 调试命令：
  - `adb shell am broadcast -a com.autonavi.plus.showmap --ei x 100 --ei y 80 --ei w 900 --ei h 500 --ei dpi 240`
  - `adb shell am broadcast -a com.autonavi.plus.closemap`


## V72.1 测试机推荐参数更新

- 已把测试机确认完美的悬浮高德广播参数作为当前推荐默认值：`x=225 y=50 w=1125 h=515 dpi=200`。
- 因 Launcher 侧设置项是基于 1号卡片测量区域进行微调，所以默认设置换算为：
  - inset 内缩 dp：`0`
  - X 偏移 px：`43`
  - Y 偏移 px：`2`
  - 宽度缩放 %：`100`
  - 高度缩放 %：`100`
  - 强制宽度 px：`1125`
  - 强制高度 px：`515`
  - 高德显示 DPI：`200`
- “1号卡片悬浮高德设置”页面新增“应用测试机推荐参数（225,50,1125,515,DPI200）”。
- ADB 推荐调试命令：

```bash
adb shell am broadcast -a com.autonavi.plus.showmap --ei x 225 --ei y 50 --ei w 1125 --ei h 515 --ei dpi 200
adb shell am broadcast -a com.autonavi.plus.closemap
```


## V72.1 布局调整（卡片 1/2/3）
- 1号卡片右边界由 x=730 调整到 x=1140，与4号卡片右边界对齐。
- 2号卡片整体右移到 x=1158~1550，左边界与5号卡片左边界对齐。
- 3号卡片整体右移到 x=1158~1550，左边界与5号卡片左边界对齐。
- 同步调整了音乐卡片按钮热区、Live2D 空白点击区与首页问候语位置，避免与新布局重叠。


## V72.1 最新悬浮高德推荐参数（卡片1拉长后）
- 当前测试机确认完美尺寸：`x=225 y=50 w=1125 h=515 dpi=200`。
- 默认换算设置：inset=0dp，X偏移=3px，Y偏移=2px，宽高缩放=100%，强制宽度=1125px，强制高度=515px，DPI=200。
- ADB 调试命令：
```bash
adb shell am broadcast -a com.autonavi.plus.showmap --ei x 225 --ei y 50 --ei w 1125 --ei h 515 --ei dpi 200
adb shell am broadcast -a com.autonavi.plus.closemap
```


## V72.1 转向提示修正
- 修复首页顶部“左转 / 右转”提示中，箭头图标与文字发生重叠的问题。
- 现在转向提示胶囊内改为：左侧箭头图标 + 右侧文字，视觉上更清晰。
- 不改动转向逻辑本身，仅调整顶部转向提示的绘制布局。


## V72.1 转向提示位置二次修正
- 左转提示：文字位于图标左侧，文字右边缘与图标左边缘间隔 10px。
- 右转提示：文字位于图标右侧，图标右边缘与文字左边缘间隔 10px。
- 左转整组提示框距离屏幕左边约 30px；右转整组提示框距离屏幕右边约 30px。
- 仅调整顶部转向提示的绘制位置，不修改转向状态逻辑。


## V72.1 高德冷启动唤醒修正
- 修复高德共存悬浮版不在后台运行时，Launcher 只发送 `com.autonavi.plus.showmap` 但高德端未触发的问题。
- 首页首次显示悬浮高德时，如果判断高德进程可能未运行，会先尝试拉起 `com.autonavi.amapautoys` 的启动 Activity 进行进程预热。
- 预热后自动把 Launcher 拉回前台，并强制补发一次 `showmap` 广播。
- 保留首页限定逻辑：只有首页允许显示高德悬浮窗；离开首页、失焦、退后台仍然发送 `closemap`。
- 该修正只影响高德进程冷启动，不改变卡片布局与推荐参数。


## V72.1 后置 AI 视觉节点
- 新增后置 AI 视觉节点接入，后置手机 App 包名：`com.jlxc.vehicleinfoncnn`。
- 控制协议：UDP `47210`，支持 `TURN_LEFT`、`TURN_RIGHT`、`TURN_OFF`、`PING`。
- 视频协议：HTTP/MJPEG `47211`，视频流地址 `http://后置手机IP:47211/stream`，状态地址 `http://后置手机IP:47211/status`。
- 车机端在检测到左/右转向打开时，在右半屏显示 1280×720 后置 AI 视频区域。
- 左转风险判定：`left=true` 或 `status=1/0`；右转风险判定：`right=true` 或 `status=2/0`。
- 状态 500ms 内未更新时按 `status=3` 安全状态处理，避免误报警。
- 新增“后置 AI 视觉节点设置”页面，可手动填写后置手机 IP、隐藏延迟秒数，并分别选择左/右侧风险提示音。
- 保留上一版转向提示贴边修复、1/2/3号卡片布局、高德 `x=225 y=50 w=1125 h=515 dpi=200` 参数和高德冷启动唤醒逻辑。


## V72.1 / MikuCarLauncher3.0 快捷文字屏节点
- 新增“快捷文字屏节点 / MikuTextDisplayNode”局域网输出功能。
- 接收端包名：`com.jlxc.mikutextdisplay`，目标设备为 Android 5.1.1 超长条屏，分辨率 1920×158。
- UDP 端口：`47230`，HTTP 端口：`47231`。
- UDP UTF-8 指令：`SHOW:文字`、`CLEAR`、`PING`。
- SHOW/CLEAR 在 UDP 发送异常时会尝试 HTTP 备用接口：`/show?text=URL编码文字`、`/clear`。
- 所有网络发送均放在后台线程，不阻塞 UI；相同文字默认 500ms 去抖，可在 300~1000ms 范围内设置。
- 新增设置入口：`我的 → 车机桌面设置 → 快捷文字屏节点设置`。
- 4号卡片常用软件设置中新增：
  - 设置位置为“快捷文字”：可自定义卡片显示名称、发送文字、图标文字/Emoji。
  - 设置位置为“喊话文字”：点击后弹出输入框，确认后把输入内容发送到后置文字屏。
- 不修改接收端的显示逻辑；文字最大化、居中、超长滚动由 `com.jlxc.mikutextdisplay` 自行处理。


## V72.1 / 3.0 高德 HOME 与冷启动修复
- 修复在 Launcher 首页再次按 HOME 时，高德悬浮窗被误关闭的问题。
- HOME 键在首页触发时加入短暂保护窗口，瞬时失焦 / 重入不再发送 closemap。
- 调整高德冷启动兼容逻辑：不再主动打开 com.autonavi.amapautoys 的前台 Activity。
- 冷启动时仅被动补发 showmap 广播，避免开机后出现“Launcher → 高德 → Launcher → 高德”的前台回弹。
- 如果高德悬浮版完全没有静态 Receiver，仍需要在高德端加入静态广播接收器才能真正后台冷启动；Launcher 端不会再为了唤醒而抢前台。


## MikuCarLauncher3.0 - V0.7.3.2 修复
- 修复 Live2D 偶发不显示：首页恢复时强制恢复 WebView 计时器、置为可见，并加入延迟健康检查；若心跳异常会自动 hardReload。
- 修复首页重复按 HOME / 实体 HOME 键导致高德悬浮窗被误关闭：HOME 瞬时保护窗口延长到 8 秒，并区分主动打开外部 App 与 HOME 重入。
- 新增高德首次启动前台预热：冷启动时可先打开 com.autonavi.amapautoys，默认等待 5 秒后再回到 MikuCarLauncher；延迟可在“1号卡片悬浮高德设置”中修改，范围 0~30 秒。
- 6号天气卡片向右拉长：右边距调整为约 20px，与 1号卡片距离左侧项目栏的间距一致。


## V0.7.3.3 修复：APP刷新、转向音、4号卡片键盘选择、图标搜索
- 修复应用抽屉设置页“保存 / 强制刷新 APP 列表 / 手动更新 APP 缩略图缓存 / 清空缓存”后，桌面应用列表不立即更新的问题。现在会写入强制刷新标记，并清理内存缓存。
- 应用列表页新增“刷新 APP 列表 / 重建缓存”按钮。
- 修复打开设置页后，后台 LauncherCanvasView 仍可能播放转向提示音导致双重音的问题；MainActivity 失焦/暂停时会停止转向音。
- 4号常用 APP 卡片支持实体按键逐个选择卡片内的每个 APP/快捷文字/喊话文字，并对选中的子项绘制焦点框。
- 从 4号卡片或应用操作菜单启动外部 APP 前，会先发送 closemap，确保高德悬浮窗关闭。
- 单独给软件选择图标时，图标包选择界面新增搜索框，可按图标资源名搜索。


## V0.7.3.4 高德前台规则 / 预设备份 / 隐藏列表修正
- 高德悬浮窗严格限定为：MainActivity 首页 + Activity resumed + 窗口有焦点。桌面退后台、打开 360 全景、进入其它 Activity、左侧切到其它页面时都会发送 closemap。
- 首页再次点击首页按钮或实体 HOME 键，会执行“首页自修复”：重载 Live2D，并关闭后重新发送 showmap，防止高德悬浮窗偶发不加载。
- 高德首次启动预热默认延迟改为 8 秒，仍可在“1号卡片悬浮高德设置”中设置 0~30 秒。
- 新增“预设备份 / 导入备份”，导出/导入所有 SharedPreferences 预设，包括隐藏应用列表、常用 APP、自定义图标映射、Live2D 文件 URI、日夜壁纸 URI、转向音、天气、高德、后置 AI、文字屏节点等设置。
- 隐藏应用列表勾选时不再重建整个页面，避免每选一个应用就跳回顶部。


## V0.7.3.5 - 高德主悬浮窗保障机制
- 新增首页高德主悬浮窗保障补发机制：进入首页、开机高德预热返回桌面、首页再次点击首页、实体 HOME 重入时，会在约 14 秒内多次强制补发 `showmap`。
- 解决高德已经加载到主界面、或导航过程中已有高德小悬浮提示窗，但 1 号卡片主悬浮地图偶发不加载的问题。
- 首页手动修复会先 `closemap` 再重发 `showmap`，同时重载 Live2D；自动保障补发不会频繁关闭窗口，减少闪烁。
- 高德预热返回桌面后，增加 2.5 秒和 5.6 秒的二次拉回桌面与主悬浮窗补发，用于防止高德资源加载完成后再次抢前台。
- 实体 HOME 键首页重入期间不再被瞬时 `onPause` / 失焦误关闭悬浮窗；打开 360、外部 App、非首页页面仍会关闭高德悬浮窗。


## V0.7.3.6 高德主悬浮窗“只补发不关闭”修复
- 修复：开机预热返回桌面后，高德主悬浮窗出现不到一秒又自动消失。
- 修复：在首页再次按 HOME，高德主悬浮窗会消失。
- 首页/实体 HOME/预热返回桌面的自修复逻辑改为只强制补发 `showmap`，不再先发送 `closemap`。
- HOME 重入和高德预热回桌面的短暂失焦阶段不再触发 `closemap`。
- 离开首页、打开全景 360、打开外部 App、进入设置/应用/我的页面时仍然会立即关闭高德悬浮窗。
- 高德主悬浮窗保障补发延长到约 23 秒，适配高德资源加载较慢和导航小浮窗抢状态的情况。


## V0.7.3.7 高德首页焦点守护修复
- 修复高德主悬浮窗已经显示但几秒后自动消失的问题。
- 首页状态下不再因为 `onWindowFocusChanged(false)` 或 HOME 重入造成的瞬时 `onPause` 发送 `closemap`。
- 首页显示高德不再强依赖窗口焦点；高德自己的 WindowManager 浮层/导航小浮窗造成 Launcher 失焦时，继续保持并补发 `showmap`。
- `closemap` 仍然在非首页、打开外部 App、打开 360 全景、进入设置页/应用页/我的页、最近任务或 Activity 销毁时发送。
- 首页 HOME 键、再次点击首页、从第三方 App 返回桌面时，会延长 30 秒首页高德守护窗口并补发 `showmap`。


## V0.7.3.8 高德全景/倒车安全守护修复
- 修复倒车、全景 360、雷达靠近物体等安全画面出现时，高德悬浮窗不会自动关闭的问题。
- 新增高德安全阻断逻辑：检测到前/后雷达有有效值时，立即关闭高德悬浮窗，并暂停首页高德守护补发。
- 新增前台应用安全检查：如果车机系统自动把全景/360/倒车画面拉到前台，即使不是从 MikuCarLauncher 点击进入，也会强制关闭高德悬浮窗。
- 首页按 HOME / 高德自身悬浮窗造成的短暂失焦仍然不会误关；但一旦前台切到第三方安全画面，会立即关闭。
- 版本：versionCode 82，versionName 0.7.3.8-amap-panorama-safety-guard。


## V0.7.3.9 高德返回首页恢复修正
- 修复普通第三方 App 前台时被误当成“安全阻断”的问题：现在普通第三方 App 只会关闭高德悬浮窗，不会留下 5 秒阻断，按 HOME 回首页后可立即重新 showmap。
- 倒车 / 全景 / 360 / 车辆界面仍然会进入安全阻断，防止高德遮挡泊车画面。
- 高德前台预热增加 30 分钟冷却时间，避免从第三方 App 回桌面或 Activity 重建时反复拉起高德主界面。
- 新增 MikuAmap 日志标签：会输出 showmap / closemap / safety close / external close，后续 logcat 更容易判断是谁关闭了悬浮窗。


## V0.7.4.0 高德逻辑重置

本版把之前逐步叠加的高德预热、首页守护、安全阻断状态机全部降级为最小广播模型，避免干扰高德自己的导航状态。

- Launcher 不再主动 `startActivity` 打开 `com.autonavi.amapautoys` 做前台预热。
- Launcher 不再 force-stop / kill / stop 高德进程。
- 首页显示时只发送 `com.autonavi.plus.showmap`。
- 离开首页、打开外部 App、进入应用页/我的页/设置页时只发送 `com.autonavi.plus.closemap`。
- 首页再次点击首页 / 按 HOME 键只补发 `showmap`，不会先 `closemap`。
- 暂时禁用基于雷达数组的“vehicle-monitor”自动关闭，避免误判导致高德主悬浮窗反复消失。
- 高德前台预热设置在界面中标记为停用，仅保留旧配置字段兼容导入导出预设。


## V0.7.4.1 高德倒车安全关闭修正
- 保留 V0.7.4.0 的高德最小状态机：Launcher 不启动、不杀掉、不重启高德，只管理 showmap / closemap 广播。
- 新增监听车机系统设置 `carletter_reserve_state`，该值为 1 时判定倒车/全景安全画面正在显示。
- 新增监听 `com.forfan.operator_reversing` 广播，用于辅助触发倒车状态刷新。
- 倒车状态激活时立即发送 `com.autonavi.plus.closemap`，并暂停首页 showmap；倒车状态恢复 0 后，回到首页时再允许 showmap。
- 不再使用雷达数组误判，不会阻断普通第三方 App 返回首页。


## V0.7.4.2 Live2D 通用兜底动作/表情开关
- 默认关闭 Live2D 导入时的通用兜底动作 / 表情生成。
- Live2D 设置页新增“导入时启用通用兜底动作 / 表情”开关，默认不勾选。
- 新增“删除当前模型的通用兜底动作 / 表情”按钮，可清理已导入模型里的 `motions_default/`、`expressions_default/` 以及 model3.json 中对应引用。
- 模型自带的动作文件和表情文件不会删除。


## V0.7.4.3 高德 AVM 前台安全守护
- 保持 V0.7.4.0 之后的高德最小状态机：Launcher 不启动/不杀/不重启高德，只发送 showmap/closemap。
- 在 V0.7.4.1 的 carletter_reserve_state 倒车守护基础上，新增前台 Activity 安全检测。
- 检测到 `com.baony.avm360` 或 `com.baony.ui.activity.AVMBVActivity` 位于前台时，持续节流发送 `closemap`，用于雷达/双闪自动调出全景影像的场景。
- MainActivity 在 pause / 失焦后延迟检查前台 Activity，避免 AVM 自动拉起时首页逻辑仍保留高德悬浮窗。
- 退出 AVM 后回到首页，恢复普通首页 showmap 逻辑，不保留安全阻断倒计时。


## V0.7.4.4 高德全景 Surface 保持窗口修复
- 保留 V0.7.4.0 的高德最小状态机：不启动、不杀、不重启高德，只发送 showmap / closemap。
- 保留倒车档位 `carletter_reserve_state` 关闭高德逻辑。
- 新增监听 `com.ldfy.car360ctrl.action`，用于双闪 / 雷达 / 全景按钮等非倒车触发场景。
- 新增 AVM 安全保持窗口：检测到 `com.baony.avm360` / `AVMBVActivity` 后，即使 Activity 短暂退到后台，也会继续保持约 12 秒不显示高德，避免 BirdviewSurface 仍在显示时被高德悬浮窗遮挡。
- AVM 保持窗口期间会节流补发 closemap；窗口结束并回到首页后再恢复 showmap。


## V0.7.4.8 高德安全无障碍检测 + 快速启动恢复
- 基于 V0.7.4.4 无故障版本重新修改，没有叠加 Hook 雷达守护。
- 新增轻量无障碍服务：仅监听 `com.baony.avm360` 的窗口状态变化，不读取屏幕内容、不扫描控件、不点击。
- 检测到全景 App / AVMBVActivity 前台时发送 `com.autonavi.plus.closemap`。
- 移除对 `com.ts.MainUI` 的宽泛前台误判，避免 MainApp / 车机系统界面被无障碍或高德逻辑干扰。
- 快速启动/冷启动回到首页后，会在 0.6s / 1.6s / 3.2s / 6.2s / 10s 自动恢复 Live2D 和高德 showmap。


## V0.7.4.8a 编译修复
- 修复 `AmapFloatingCardSettingsActivity.java` 无障碍状态说明字符串换行写法错误导致的 Java 编译失败。
- 仅修复编译错误，不改动 V0.7.4.8 的高德 / Live2D / 无障碍运行逻辑。


## V0.7.4.9 熄火快速启动恢复增强
- 基于 V0.7.4.8a 编译修正版继续修改。
- 新增屏幕点亮 / 用户解锁 / 开机完成 / 时间恢复监听。
- 车机熄火低功耗后快速启动时，会在 0~30 秒内多次恢复首页资源。
- 恢复动作包括：Live2D resume/applySettings/hardReload、Amap showmap 补发、1号地图卡片状态刷新。
- 仍然不主动启动/杀掉/force-stop 高德地图，避免打断正在导航的路线。
