# 优化路线：以 Obscure Tooltips 为参照

> 参照对象：`C:\Users\Meowmel\Downloads\obscure-tooltips-1.12.2`（Java 5041 行，8 类资源、14 个过滤器、6 种特效、43 条缓动曲线）
> 本模组：`QuantumHue-Visualizer`（Java 47912 行、27 个子包、`tooltips` 3021 行）
> 结论先说：**本模组的动效骨架（时间轴 / 弹簧 / 绘制抽象）在 `pause`、`modernsplash`、`page` 里已经做得比参考模组更细，唯独 `tooltips` 还停在"1.12 原版矩形 + 一个颜色"的水平。** 参考模组最值得学的是它的**渲染纪律**（事件契约、状态隔离、内容缓存、GL 状态收口）；它的 JSON 样式系统不搬——本模组布局固定，外观由代码 + 配置决定。
>
> **方向修订（2026-11-18）**：原 P1「JSON 样式系统 / 资源包主题」整条取消。理由：布局固定，不需要数据驱动的布局与外观；引入注册表、过滤器 DSL、资源包注入会给 1.12 带来额外的重载与冲突面，收益只在"整合包作者改皮肤"，与本模组定位不符。底层正确性修复（P0）保留并已落地，见第 4 节。

---

## 1. 两份代码的定位差异

| 维度 | Obscure Tooltips | QuantumHue 现状 |
| --- | --- | --- |
| 目标 | 只做 tooltip，做成一门"皮肤语言" | 做整包 UI（聊天 / 暂停 / 启动 / 商店 / HUD / tooltip） |
| 样式来源 | JSON 资源（`assets/*/tooltips/**`）+ 内置资源包 | Java 硬编码 + `@Config` 静态字段 |
| 扩展方式 | 写 JSON，不编译 | 改代码重新构建 |
| 组合方式 | `priority` 升序 merge 多个 style | 单一 `TooltipColors` 结构体 |
| 动效 | 每个效果自己读 `state.timeInSeconds()` | 一个 `TooltipAnimation` 做布局插值 |
| 渲染抽象 | `GuiGraphics` + `Pose`（z 轴分层） | 直接 `Gui.drawRect` + 一次 `translate(0,0,500)` |
| 配置 | 203 行 + 带 GUI + 服务端同步 | 845 行 + Forge 自动界面 |

参考模组值得学的不是它的美术，而是它的**分层**：把"长什么样"从代码里赶出去，代码只负责"怎么把描述画出来"。

---

## 2. 参考实现拆解：7 个可迁移的设计

### 2.1 资源种类枚举 + 统一重载（`ResourceKind.java:18-27`、`TooltipManager.java:29-62`）

`PANEL / FRAME / SLOT / ICON / EFFECT / STYLE / DEFINITION / LABEL` 八个枚举值，每个带"目录名 + 解析函数 + 注册表"，重载时统一走 `onReloadStart → load → onReloadEnd`，单文件解析失败只打日志不中断（`ResourceKind.java:49-55`）。

**为什么重要**：新增一种视觉元素 = 加一个枚举值 + 一个 `fromJson`，其它代码零改动。本模组现在加一种边框要动 `TooltipRenderer`、`TooltipColors`、`TooltipColorConfig` 三处。

### 2.2 样式是"可合并的补丁"，不是"整包替换"（`TooltipDefinition.java:34-41`）

```java
for (var definition : TooltipRegistries.TOOLTIP_DEFINITION.listElements()) {
    if (!definition.isFor(stack)) continue;
    style = style.merge(definition.style);   // 高优先级只覆盖它声明过的字段
}
```

基础样式 `default.json` 给底，稀有度样式只写 `panel/frame/effects`，二者合并。**整合包作者可以只加一条"GT 的 XX 机器用这个框"，不必复制整份样式。**

### 2.3 过滤器 DSL（14 个类，`filter/`）

`all_of / any_of / none_of` 组合 `item / item_tag / mod / rarity / nbt / enchantment / id / config / property`。配置项也能当条件：`{"type": "config", "config_value": "@EPIC_STYLE_ENABLED"}`（`epic_items.json:6-10`）。

**对齐本模组场景**：GTQT 整合包里"某台 GregTech 机器 / 某个 NBT 标记的物品要特殊外观"是真实需求，现在的 `TooltipColorHelper.java:33-37` 只能用 `isModLoaded("gregtech")` + 一个颜色粗暴覆盖。

### 2.4 颜色是可求值的 Provider，不是 int（`ARGBProvider` + `@CONFIG_KEY`）

JSON 里写 `"#AARRGGBB"` 或 `"@EPIC_SHIMMER_ACCENT"`，后者运行时读配置。**同一份主题既能在 JSON 里被引用，又能在配置界面里改**，不存在"配置改了但主题没跟上"的双份维护。

### 2.5 渲染抽象：`GuiGraphics` + `Pose`（`render/GuiGraphics.java`、`render/Pose.java`）

把 1.12 的立即模式包成现代画布：`fill / blit / blitGlow / drawString / renderItem / enableScissor`，变换走 `Pose`（push / translate / scale / rotate），z 轴靠平移分层——背景 400、内容 402、边框单独一层（`TooltipRenderer.java:59-68`）。效果因此可以"浮在面板之上、文字之下"。

本模组 `TooltipRenderer.java:27-49` 全是一次性 `Gui.drawRect`，没有层概念；边框和文字谁压谁只能靠调用顺序。

### 2.6 每个 tooltip 一个状态对象（`TooltipState.java`）

`stack / style / label / startTime / particles / uuid` 都是实例字段，`isFirstFrame`、`timeInSeconds()`、音效节流（`maybePlayIconSound` 用 uuid + 300ms 冷却）都在实例上。装备对比、多个 tooltip 并存时天然不串台。

本模组对应的是 `AdvancedTooltipHandler.java:27-32` 的六个 `static` 字段——一旦同时渲染两个 tooltip（`EquipmentCompareHandler` 正是如此），`currentItemId`、`cachedColors` 会互相覆盖。

### 2.7 生态契约：自绘也照发 Forge 事件（`TooltipRenderer.java:84-96`）

取消 `RenderTooltipEvent.Pre` 后，自绘完成时手动 post `PostBackground` / `PostText`，并给出真实的内容矩形。依赖这两个事件加 overlay 的模组（WAILA 系、伤害显示、部分 FPS/hint 模组）才不会静默失效。

**这是本模组当前的真实缺口**：`AdvancedTooltipHandler.java:65-67` 取消 `Pre` 之后，全仓库没有任何一处 post 这两个事件（已 grep 确认）。

---

## 3. 本模组体检（证据）

### 3.1 渲染：三层硬边矩形，没有"材质"

- `TooltipRenderer.java:28-31`——最外圈 `0x50000000` 的 2px 硬阴影，边缘一圈死黑。
- `TooltipRenderer.java:68-73`——四条边各自一个颜色，`drawSimpleBorder` 与它逐行重复。
- 阴影、内填充、页眉渐变全在同一个 `Gui.drawRect` 体系里（该文件 14 处 `drawRect`），无法做 9-slice 贴图框、内发光、圆角。
- `GLStateHelper.setupGLState()/restoreGLState()` 无 `try/finally`，`AdvancedTooltipHandler.java:153-177` 中间任一步抛异常，matrix 栈与 depth 就留在错误状态。

### 3.2 样式：只有一个颜色派生链

`TooltipColorHelper.java:24-50` 的决策顺序是 `GregTech 颜色 → 稀有度颜色 → 配置边框色`，全部退化成 `borderStart = borderEnd = 同一个值`。没有面板/边框/图标/槽位/特效的分层，也没有"按 mod / tag / NBT 定制"的入口。

### 3.3 动效：四套时钟，一套缓动

| 位置 | 实现 |
| --- | --- |
| `TooltipAnimation.java:70-72` | 硬编码 `1-(1-t)^3` |
| `chat/ChatAnimation.java` | 又一份 `easeOutCubic` |
| `pause/PauseTimeline.java` | 纯时间函数，另带 `reducedMotion` |
| `modernsplash/SplashTimeline.java` | 另一套相位表 |

`api/utils/MathUtils.java:10` 的 `easingMethod` 恒等函数**从未被赋值**，是死代码；`TooltipAnimation` 用 `System.currentTimeMillis()` 绝对时间，卡顿一帧就跳进度，且没有 dt 上限（`pause/Spring.java` 反而做对了：dt clamp 50ms + 0.1% 吸附）。

### 3.4 状态与开销：每帧重建内容

`AdvancedTooltipHandler.java:128-132` 每帧都 `extractTooltipContent` + `new TooltipContent` + `wrapTooltipText`，悬停时每帧重新拼接和测量字符串。`TooltipTextRenderer.java:71` 的缓存 key 是 `itemName + '\0' + page`，物品名带颜色码时 key 抖动，96 条全局 LRU 很容易被不同物品挤掉。

### 3.5 配置与分发：845 行，无法外部分发

`QuantumHueConfig.java` 845 行、18 个内部类；tooltip 相关分散在 `TooltipColor` / `TooltipBackground` / `TooltipTextEffects` / `TooltipAnimation` 四处。没有热重载、没有主题包、没有预览；玩家改配色只能重启或依赖 Forge 自动生成的面板。

### 3.6 结构：48k 行里没有"样式注册"这个概念

`QuantumHuePageApi` 只覆盖公告/商店页；`tooltips` 包 3021 行全部是过程式渲染，第三方无法注册一个 filter 或一种特效。

---

## 4. 路线图

### P0 · 打地基（1–2 周，不改观感，只改结构）

| # | 动作 | 落点 | 验收 |
| --- | --- | --- | --- |
| P0-1 | 抽 `api/render/Canvas2D` + `Pose`：`fill / gradient4 / quad / blit / blit9 / text / item / scissor`，所有 GL 状态集中一处 | 新建 `api/render/`；`tooltips/TooltipRenderer`、`menu/GlStatePainter`、`modernsplash/SplashPainter` 逐步改调用方 | 全仓库 `Gui.drawRect` 调用点从 17 个文件收敛到 1 个文件 |
| P0-2 | `TooltipState` 实例化，替换 6 个 static 字段 | 新建 `tooltips/TooltipState.java`；`AdvancedTooltipHandler` 去掉 `currentItemId / cachedColors / cachedAspects / cachedFoodInfo` | 装备对比同时渲染两个 tooltip，颜色/面板不互串 |
| P0-3 | 事件契约修复：自绘后 post `PostBackground` / `PostText`，失败路径回退原版 | `AdvancedTooltipHandler` | WAILA / 伤害显示类模组 overlay 恢复 |
| P0-4 | 统一时钟与缓动：`AnimationClock`（nanos + dt clamp 50ms）+ `Easing`（≥24 条）+ 把 `pause/Spring` 提到 `api/anim` | 新建 `api/anim/`；`TooltipAnimation`、`ChatAnimation`、`SplashTimeline` 改调用 | 四处缓动实现收敛为 1 套；掉帧时动画不跳变 |
| P0-5 | 视觉分层：软阴影（4 层 alpha 递减外扩）+ 边框四角独立色 + 明确 z 序（阴影 398 / 面板 400 / 内发光 401 / 边框 402 / 内容 403 / 粒子 404） | `TooltipRenderer` 重写绘制段 | 截图对照：无 2px 死黑边；粒子可压边框 |

### P1 · ~~样式系统~~（已取消，2026-11-18）

原计划（`StyleRegistry` + `ResourceKind`、14 个过滤器的 DSL、9-slice 元素注册表、`#AARRGGBB` / `@CONFIG_KEY` 颜色 Provider、`IResourceManagerReloadListener` 热重载、内置主题资源包 + mixin）**整体不做**。布局与元素组合是固定的，皮肤差异用配置项表达即可；`TooltipColorConfig` 继续作为唯一的颜色来源。

保留下来的只有与"数据驱动"无关的渲染改进，归入 P0-5：

| # | 动作 | 落点 | 验收 |
| --- | --- | --- | --- |
| P1-1 | 9-slice 边框贴图与内发光（固定布局，不引入 JSON） | `tooltips/TooltipRenderer` | 截图对照：边框不再是一条纯色线 |
| P1-2 | 颜色配置项补全（每类稀有度的 panel/frame/text 三色） | `QuantumHueConfig.TooltipColor` | 改配置即时生效，无需重启 |

### P2 · 体验与生态（4–8 周）

| # | 动作 | 说明 |
| --- | --- | --- |
| P2-1 | 配置界面升级 | Forge 自动面板 → 自绘 `GuiConfig`：左侧分类、右侧实时预览缩略 tooltip、取色器 |
| P2-2 | 超长 tooltip 滚动 | 参考 `TooltipScroll`：滚轮 + 边界回弹 + 边缘裁切，复用 `MathUtils.handleScrollingPosition` |
| P2-3 | 3D 预览（盔甲 / 工具） | 收益大但 1.12 状态坑多（GUI 光照、深度、`ItemCameraTransforms`），放在样式系统稳定之后 |
| P2-4 | 粒子系统 | 池化上限（默认 256）、按稀有度分档、低配自动关闭；`LineParticle` / `TextureParticle` 两种基元即可覆盖参考实现的全部效果 |
| P2-5 | 可访问性与性能档 | `reducedMotion`（`PauseTimeline` 已有，推广到 tooltip）、动画总开关、低配模式（关粒子/关阴影/关打字机） |
| P2-6 | 扩展 API + 文档 | `QuantumHueTooltipApi.registerFilter/registerElement`；`docs/Tooltip-Theming.md` 面向整合包作者 |

---

## 5. 跨子系统收益：不只修 tooltip

这三件事一旦抽出来，收益是全模组的：

1. **`Canvas2D`** → `chat`（气泡面板）、`page`（商店/公告）、`market`、`notice` 全部共用一套填充/渐变/裁剪/9-slice，消除 17 个文件里各写一遍的 `drawRect`。
2. **`ThemeToken`（accent / surface / outline / text / dim）** → 现在 `SplashTheme`、`PageTheme`、`TooltipColorConfig` 三套配色互不相干，视觉上"启动页一个世界、商店一个世界、tooltip 第三个世界"。统一后只换一个 accent，整包跟着变——这是"美学"层面最大的一笔收益，而不是某个 tooltip 变好看。
3. **`Easing` + `AnimationClock` + `Spring`** → `PauseTimeline` / `SplashTimeline` / `ChatAnimation` / `TooltipAnimation` 四套时钟合一，`reducedMotion` 只需实现一次。

建议的视觉语言（对齐现有 `SplashTheme` 的仪器风）：

- 面板：`surface` 90% 不透明 + 顶部 4% 亮边 + 底部 6% 暗边，禁止纯黑描边；
- 边框：1px `outline`，四角可独立色（稀有度渐变从左上到右下）；
- 光效：内发光只在面板内侧 3px，alpha ≤ 0x30，避免"荧光棒"；
- 运动：进场 140ms `easeOutCubic` + 尺寸 spring（22Hz）；退场 90ms；图标 pop 用 `easeOutBack`；
- 文字：行高 10、左右 padding 4、分隔线两端渐隐（`TooltipRenderer.java:84-93` 已经做对，保留）。

---

## 6. 兼容性与风险

| 风险 | 说明 | 对策 |
| --- | --- | --- |
| OptiFine / 光影 | 直接 `glEnable` 会打乱 `GlStateManager` 缓存（`menu/GlStatePainter` 的注释已踩过这个坑） | 一切走 `GlStateManager`；`Canvas2D` 内禁止裸 GL |
| Scissor 与 GUI Scale | `GLStateHelper.enableScissorClip` 已按 `getScaleFactor()` 换算，但未考虑窗口 resize 后 framebuffer 变化 | 统一到 `Canvas2D.scissor`，用当前 `displayHeight` 现算 |
| JEI / 配方界面 | `RenderTooltipEvent.Pre` 在这些界面也会触发，`event.getLines()` 可能已被其他模组改写 | 保留 `skippedGuiPatterns` 逃生阀；自绘前检查行是否为空 |
| Gson 版本 | MC 1.12 自带 Gson 2.8.0，无 `JsonParser.parseReader` 等新 API | 已无 JSON 主题解析需求；若将来加，只用 `new JsonParser().parse(Reader)` |
| Jabel / Java 8 | 已开 `use_modern_java_syntax`，`record`/`var` 可用，但 `List.of` 等运行期 API 不可用 | JSON 解析层用 `Arrays.asList` / `Collections.emptyList` |
| 资源包注入 mixin | ~~`repositoryEntriesAll` 被其他整合包模组也注入过~~ | 已取消：不注入资源包，本模组只用一个 coremod 入口 |
| 热重载 | F3+T 会重载 GT 的大量材质 | ~~样式热重载~~ 已取消；内容缓存在物品切换时惰性失效 |

---

## 7. 验收基准

- **稳态开销**：tooltip 打开后每帧 GL 调用 < 120（当前边框 4 次 + 阴影 1 次 + 每行 1 次 + 分隔线 2 次，尚未计入特效）；CPU < 0.2ms/帧（内容缓存命中）。
- **首帧**：打开 tooltip < 2ms（内容缓存命中时不再解析/换行）。
- **内存**：glyph 缓存 ≤ 96 条 + 5 分钟 TTL；粒子池 ≤ 256；内容缓存最多保留 1 份主 tooltip + 8 份装备对比。
- **回归清单**：OptiFine、TOP、Waila/HWYLA、JEI、Baubles、BetterQuesting、GT5U/CEu、神秘时代、AppleSkin、装备对比、分页、聊天/暂停/启动页。
- **可用性**：任何一步绘制抛异常，tooltip 仍以朴素样式可见，且 GL 状态（matrix / depth / blend / scissor）不留残余。

---

## 8. 建议的提交顺序（9 个 PR，标注已落地）

1. `Canvas2D` + `Pose` 抽取（纯搬移，无行为变化）——待做
2. GL 状态 `try/finally` + 补发 `PostBackground` / `PostText`——**已落地**（tooltip 与装备对比；scissor 改栈式）
3. `TooltipState` 化，删除 6 个 static——**已落地**
4. `Easing` + `AnimationClock` + `Spring` 上提，替换四处调用——**部分落地**（`Easing` 已建，`TooltipAnimation` 已接 dt clamp；`AnimationClock` / `Spring` 上提与 `ChatAnimation`、`SplashTimeline` 改造待做）
5. 软阴影 + 四角边框 + z 层序——待做
6. `TooltipContent` 缓存（按 stack 身份 + 行哈希）——**已落地**（主 tooltip 1 份 + 装备对比 8 份 LRU）
7. 9-slice 边框与内发光贴图（固定布局）——待做
8. 颜色配置项补全（panel / frame / text）——待做
9. 配置界面预览 + 软阴影调参——待做

第 2/3/6 项已在本次改动中落地并通过 `compileJava` 与 `build -x test`；第 1 项（绘制抽象）是后续美术工作的前提。**先做 P0，再谈美术。**
