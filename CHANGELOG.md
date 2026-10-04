# Changelog

## [Unreleased]

### Fixed
- **背景统一（第二轮）** —— 第一轮只统一了**调用路径**，没有统一**结果**：四条路径跑的是同一个
  `Backdrop`，却画出三种不同的图。两个原因，都已定位并修掉：
  1. **几何量是绝对单位，而调用方的坐标系不同。** 启动画面给 `Backdrop` 的是**设备像素**画布，
     主菜单给的是**缩放后的 GUI 单位**画布（1080p + GUI scale 3 时只有 640×360）。同样是
     `PcbTraces.GRID = 32`，在菜单里等于 96 设备像素，走线条数一样但每条长 3 倍 —— 于是菜单的
     板子又粗又显眼。现改为按画布短边的比例取值（`PITCH_RATIO = 32/720`、`CELL_RATIO = 64/720`），
     锚在 720 这个启动画面原本调好的高度上，所以启动画面**外观不变**，而主菜单落到同样的
     **设备像素**间距。网格漂移速度同样改为"每秒多少格"，否则也会差 3 倍。
  2. **GL 状态从上一个绘制调用漏进来。** `CustomSplash` 从不碰 `GL_ALPHA_TEST`，启动线程上下文
     默认是关的，所以 HERO 阶段网格可见；一旦加载 HUD 开始画字，`FontRenderer` 会打开 alpha test
     （阈值 0.1）并留着不关，网格 alpha 0.075 < 0.1 就被整层丢弃且再也回不来 —— 即"加载到后边
     网格消失了"。现在 `GlSplashPainter` / `GlStatePainter` 的每个图元自己断言 alpha test 关闭
     （共 11 处），不再依赖调用方。顺带修好了同类问题：加载页 0.13 的进度条底槽与刻度线原本也会
     在 `uiAlpha` 跨过 0.77 时闪现。
- **PCB 过孔尺寸（第二轮遗漏）**：上一轮只把*间距*改成了相对量，过孔半边长仍是绝对的 `2f`。
  2 单位在启动画面是 2 设备像素，在主菜单是 2 × GUI scale 像素，所以 GUI scale 3 下过孔是启动
  画面的 **3 倍大** —— 过孔是板子上最亮最方的元素，这就是"主菜单的 PCB 还是太大太显眼"。
  现改为 `pitch * 1/24`（1080p 下正好等于原来的 2，**启动画面逐像素不变**）。走线末端的封口
  过孔同样处理。
- `tools/BackdropCheck.java`：新的回归检查，按调用方真实产生的画布尺寸（1080p 下 GUI scale 1–4）
  各渲染一次裸背景，比较**特征归一化位置**，尺度不一致直接以非零码失败。
  旧代码下它是 31 vs 10 个特征、最坏偏移 0.157 屏宽；修复后 23/23/22/19，最坏偏移 0.014。
  该工具同时记录了三类失败指标的取舍：平均绝对差被底色主导（有 bug 时也只报 2/255）；
  覆盖率由预览的栅格化决定而非几何（几何已正确时仍报 2.6 倍差异）；逐扫描线自适应阈值会被
  变粗的走线拉高而完全数不到网格（同样几何下 12 vs 3）。只有峰值位置对这三者都免疫。
- `tools/BackdropCheck.java` 的**第二项检查：直接读 `PcbTraces` 产出的四边形**，断言过孔宽度
  相对画布短边恒定（270–1440 全部为 0.003704）。
  之所以必须单独有这一项：位置检查对尺寸完全免疫 —— 过孔大了 3 倍，图案一模一样、位置一模一样，
  上面那 23/23/22/19 全部照常通过。而 `MenuPreview` 是按 **GUI scale 1** 渲染的，两种写法在
  scale 1 下算出来都是 4px，所以预览**结构上就不可能**看到这个 bug。

### Added
- **背景统一**：新增 `Backdrop` —— 唯一的一份背景实现，三层，自下而上是**不透明竖向渐变底**、
  **PCB 走线板**、**漂移科技网格**。开机画面 / 加载页（`SplashScene`）、主菜单、其它所有界面
  （经 `ClientHelper.renderPanorama` → 被替换的 `renderSkybox`）、以及两个离线预览工具，
  现在走的是同一条绘制路径。戴森球、HUD、按钮等属于前景，仍由各自屏幕绘制。
  （几何尺度与 alpha test 的问题见上方 Fixed：仅统一调用路径是不够的。）
- 新增 `GlStatePainter` —— GUI 侧的 `SplashPainter` 后端。**不能复用 `GlSplashPainter`**：那个是
  给启动线程用的，直接调 `glEnable`/`glDisable`/`glBlendFunc`，在 GUI 帧里会把
  `GlStateManager` 的状态缓存带偏（缓存仍以为 `GL_TEXTURE_2D` 是开的，下一次
  `enableTexture2D()` 会被当作空操作丢掉，之后所有 `FontRenderer` 文字都会失去贴图）。
  所以适配层全程走 `GlStateManager`。它只实现背景需要的四个图元，其余方法直接抛异常。
- `SplashTheme.SHELL_PANEL`：集光板 / 过孔的浅紫，原先在三个绘制路径里各写一份。

### Changed
- 统一了三处此前各写一份的参数：网格漂移速度（主菜单 1 px/s vs 启动画面 6 px/s，统一为
  6 px/s）、网格 alpha（启动画面原本按 hero 淡入到 0.055，统一为 `Layer.GRID`）、
  底色渐变（启动画面线性 vs 主菜单平方，统一为平方）。
- `Backdrop.paint` 的时钟参数改为 `double`：两个调用方都直接喂 `getSystemTime() / 1000.0`
  （开机毫秒数），各图层内部自行取模。精度修复由此只存在于一处，而不是每个屏幕各修一遍。
- `tools/MenuPreview` 不再自己重画一遍背景（原来有 30 行复制的渐变 + PCB + 网格），改为经
  `SplashPreview` 的 AWT painter 调用 `Backdrop`。背景改动现在不可能只出现在游戏里而不出现在
  预览里。
- `GtqtMenuChrome` 删掉了自带的渐变、PCB、网格、`drawLines`、`drawQuads`；戴森球改走
  `GlStatePainter`，写法与 `SplashScene` 里的一致。

### Removed
- `QuantumHueConfig.mainMenu.grid`：网格属于共享背景，按屏幕单独开关与"统一背景"直接矛盾。
- `SplashTimeline.Frame.gridDrift`：漂移现在由 `Backdrop` 从时钟推导，该字段无人读取。

### Added
- 主菜单接管：**全景天空盒（图片盒渲染）整块移除**。`GuiMainMenuMixin.renderSkybox` 原来是
  手写的六面全景立方体渲染，现在改为在 `HEAD` 处拦截并 `cancel`，改画自家的戴森球 + PCB 背景，
  六张 panorama 贴图一次都不采样；两层全屏渐变、`minecraft.png` 标题图、`edition.png` 缎带
  由一层不透明背景在同一帧内盖掉。
- 换掉的这条 `renderSkybox` **不只服务于标题界面**：`ClientHelper.renderWorldBackground` 会经
  `renderPanorama` 对**所有**其它界面调用同一个方法，所以世界选择等界面也一并换成了同一套背景。
- 主菜单背景换成**三维戴森球**：球心正好落在屏幕左边缘，只露右半圆，**直径等于屏幕高度**
  （上下顶满），球内不放徽记。复用的就是启动画面那套 `DysonSphere` 几何。
- 主菜单品牌锁定移到右上角：齿轮徽记 + GTQT 字标并列，下方一条细导轨。
- 主菜单导航改为右侧仪器台：按钮列右对齐（原来在左侧，会与半球重叠），编号 01–06。
- **PCB 走线同时作为加载画面与主菜单的背景**（`PcbTraces`）：32px 网格上的正交布线 + 过孔 +
  沿走线移动的信号脉冲，铺满全屏，绘制在网格与戴森球**之下**。利萨茹信号迹移除。
- `SplashTheme.Layer`：全部背景图层的 alpha 收进这一张表。它放在 MC-free 的一半里，是因为
  主菜单和两个离线预览各自持有自己的绘制调用——两边各写一份数字时，预览会画出游戏里
  根本不存在的画面（这个坑已经踩过一次）。

### Fixed
- **背景整体过暗**：图层 alpha 定得太保守（走线 0.045、过孔 0.09、结构环 0.14、能量弧 0.45），
  于是右半屏的 p99 就是背景色本身，屏幕看上去是**空的**而不是暗的。现在走线 0.26、过孔 0.30、
  脉冲 0.60（叠加）、结构环 0.30/0.42、能量弧 0.60/0.85、网格 0.075；走线数量 24 → 32。
  只有 PCB 的右半屏现在约 3% 的像素是有内容的布线。
- 集光板过大：`PANEL_COVERAGE` 0.25 让每块板看起来像插在结构线上的灰色小旗，收到 0.16 后
  壳体读作细密的蜂群，剪影交给大圆环。
- 主菜单时钟掉精度：戴森球的自转/倾角原来取 `now / 1000f`（开机毫秒数直接当秒），开机十几
  小时后 float 的小数位耗尽，球体会一顿一顿。改为全程 double 运算、只在最后收窄成 float，
  自转角度按 360° 取模，能量弧相位按 3600 秒取模（3600 × 0.13 正好是整数，循环无缝）。
- 主菜单按钮悬停无效：`GuiButton.isMouseOver()` 只返回 `hovered` 字段，而该字段仅在原版
  `GuiButton.drawButton` 内部赋值——本屏幕刻意不调用它，导致悬停永远为 false。改为自行命中测试。
- 背景图形的 alpha 被忽略：`Gui.drawRect` 以 `GlStateManager.disableBlend()` 收尾，而自绘的
  线/四边形助手从不重新开启混合。现在每次绘制前显式设置混合状态。
- 时间一长脉冲停摆：`Minecraft.getSystemTime()` 是开机以来的毫秒数，直接当秒用会让 float
  在小数位精度耗尽后量化到停滞。改为对 120 秒窗口取模后再传入。
- 按钮悬停改为明确的**选中态**：金色四角选择括号（缓慢脉动）、外侧叠加光晕环、
  内部暖色洗底、满强度金色前导条、标签转白、编号转金。
- `QuantumHueConfig.mainMenu` 配置段，可整体关闭。

### Changed
- `renderPanorama` 的 `panoramaTimer` 整条管线删除：`IGuiMainMenuMixin` 接口、访问器
  `getPanoramaTimer`、`@Shadow panoramaTimer` 与 `clearMyBackground$tickPanoramaTimer` 都不再有
  读取方——那个计时器只喂给被删掉的全景渲染。`@Shadow TITLE_PANORAMA_PATHS` 一并删除。

### Notes
- 六张 `assets/minecraft/textures/gui/title/background/panorama_*.png`（约 2.4 MB）已随天空盒
  一并**删除**：`renderSkybox` 不再采样它们，仓库内也再无任何引用。想退回原版全景，
  从 git 历史里取回这六个文件即可。

### Added
- 启动画面全面接管为科幻 HUD：深空底 + 科技网格 + 扫描带，中央 GTQT 齿轮徽记弹出动画
  （弹性过冲入场，不旋转），随后加载读数淡入。
  `config/modern_splash.cfg` 新增 `sciFiSplash` / `sciFiIntro` / `sciFiIntroSeconds`。
- **三维戴森球环绕徽记**：测地线结构壳 + 队形集光板 + 轨道环能量流，透视投影，
  随徽记一同组装、之后持续自转。实现在 `DysonSphere`（纯数学），
  用「背半球 → 徽记 → 前半球」的画家算法代替深度缓冲。
- `tools/gen_gtqt_splash_assets.py`：从原始 LOGO 生成全部贴图（含预乘 alpha 的正确缩放）。
- `tools/SplashPreview.java` / `tools/MenuPreview.java`：不进游戏、用同一份构图代码离线出图自检。

### Changed
- 徽记下文标与中文副标题两行文字移除，徽记放大（入场 0.68×短边、加载页 0.34×短边），
  品牌名改由 HUD 左上角的 `GTQT // BOOT SEQUENCE` 承担。
- 加载页徽记不再压暗：原 `WATERMARK_ALPHA 0.30` 改为 `STEADY_LOGO_ALPHA 1.0`，
  戴森球也去掉稳态下的 `0.55` 衰减。徽记位于中上、HUD 占据导轨与左下右下角，
  两者不重叠，压暗只让标识发灰而没有换来可读性。

### Notes
- 产品徽记不旋转：它是标识，不是装饰；戴森球是结构，可以转。

## [1.0.0] - 2023-09-15

### Added
- This is a default template changelog that follows the [KeepAChangelog Convention](https://keepachangelog.com/en/1.1.0/)
