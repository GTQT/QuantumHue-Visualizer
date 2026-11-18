# 美学与功能学习笔记（参照 Obscure Tooltips 1.12.2）

> 目标：在**不改布局**的前提下，把「平涂色块」升级成有材质感的面板，并把参照模组里
> 真正有效的动效/功能语法拆出来，按本模组的固定结构落地。
> 所有结论都对应到本仓库的具体文件与方法，方便直接改。

---

## 0. 结论先行

参照模组好看，不是因为特效多，而是因为三件事同时成立：

1. **颜色只有三个角色**：面板底色、描边色、强调色。三者由同一个稀有度色推导，
   所以任何稀有度看起来都「成套」，不会出现金框配紫底的脏配色。
2. **光影分两层**：面板外侧有投影（拉开与背景的距离），面板边缘有一圈斜面高光
   （让平面有厚度）。这两层都很便宜，但缺一个就会显得「贴纸感」。
3. **动效只有一条语法**：开框 0.25s 两段式（放大→回落）、粒子 0.15~0.2s 一颗、
   微光 1~2Hz 正弦。所有特效都遵守同一套时间尺度，所以同时出现也不吵。

本模组原来的差距恰好在这三点上：单色平涂 + 单层硬阴影 + 1px 双色描边，
没有强调色概念，也没有任何「随时间流动」的装饰。

---

## 1. 参照模组拆解

### 1.1 三层颜色

`ClientConfig` 里每个稀有度只配两组色（`ARGBProvider` 的 top/bottom），
`ColorRectPanel` 用它们做四角渐变，`border_palette` 再取同源的亮色做 1px 框，
特效（微光/环带/射线）继续用同一个 accent。结果：

- 底色永远比描边暗、比描边灰；
- 强调色永远比描边亮 30%~50%；
- 面板、边框、特效之间不会互相抢。

### 1.2 面板的四层结构（ColorRectPanel）

| 层 | 做法 | 作用 |
| --- | --- | --- |
| 投影 | 2×hLine + 2×vLine，偏移 1~2px，alpha 0.3 | 与背景分离 |
| 主体 | `x-3, y-3, w+6, h+6` 的四角渐变 | 打光方向 |
| 描边 | 1px，border_palette 双色 | 定义边界 |
| 斜面 | 框外每条边再补一条背景色高光线 | 厚度 |

注意投影不是高斯模糊，就是几条半透明线——**便宜且有效**。

### 1.3 动效语法

- 开框：`0.25s` 内 `easeOutCubic` 放大到 1.33/1.75，再用 `0.25s` 回落到 1.0，
  同时 `rotateY 360° × easeOutExpo`（像硬币翻面）。
- 粒子：icon 粒子每 `0.15s` 一颗，随机方向、12px 位移、`easeOutCubic(p*0.5)`；
  inward 粒子每 `0.2s` 从随机边点飞向中心。
- 微光：四边各 32 段，`t = 0.5 + 0.5cos(angle*freq + time*speed)`，
  `freq=2, speed=1` 时约 1.5s 一个周期。
- 音效：同 UUID 只响一次，开框后 ≥100ms，距上次 ≥300ms。

### 1.4 功能层

真正值得学的功能只有四个，其余（JSON 定义、14 种过滤器、资源包）都是为
「数据驱动外观」服务的，与本模组固定布局的目标无关：

1. **滚动**：内容超出屏幕时整框随滚轮上移，而不是截断。
2. **3D 预览**：盔甲/工具在 tooltip 里转起来（`ArmorPreviewLayout`，宽 40）。
3. **第二行标签**：物品名下方一行小字（模组名/标签）。
4. **声音反馈**：开框音效，带节流。

本模组已经有：分页（比滚动更适合固定布局）、逐字打字、要素/食物信息、
装备对比、TOP 颜色——这些是参照模组没有的，不要为了「像」而丢掉。

---

## 2. 现状对照（改前）

| 维度 | Obscure Tooltips | 本模组（改前） | 差距 |
| --- | --- | --- | --- |
| 面板 | 四角渐变 + 投影 + 斜面 | 单色 `background` 平涂 | 缺打光、缺厚度 |
| 描边 | 低 alpha 双色 + 9-slice 可选 | 1px 双色，稀有度时整条高饱和 | 高饱和描边显脏 |
| 强调色 | 独立 accent，驱动全部特效 | 无此概念 | 特效无处取色 |
| 动效 | 0.25s 开框 + 粒子 + 微光 | 800ms 果冻 + 逐字 | 节奏偏慢，缺环境动效 |
| 状态 | 每 tooltip 一个 `TooltipState` | 已改为实例状态（本次硬伤修复） | 已对齐 |
| 功能 | 滚动、3D 预览、音效 | 分页、打字、要素、食物、对比 | 各有取舍 |

---

## 3. 落地清单

### P0 — 已落地（本次提交）

全部由 `QuantumHueConfig.tooltip_aesthetic` 控制，关掉 `enabled` 即回退旧版。

| 改动 | 位置 | 说明 |
| --- | --- | --- |
| 柔和投影 | `TooltipRenderer.drawPanel` | 三层同心矩形，alpha 0x3C/0x1E/0x0E |
| 四角渐变 | `TooltipRenderer.drawGradientQuad` | 上亮下暗、左侧 +4% 亮度 |
| 斜面高光 | `TooltipRenderer.drawBevel` | 框外 1px，四边 alpha 递减 |
| 稀有度调色 | `TooltipColorHelper.palette` | 低 alpha 描边（#80…/#60…）+ 亮 accent |
| EPIC 微光 | `TooltipRenderer.drawShimmer` | 四边分段正弦错峰，192 quad 上限 |
| 时间基准 | `TooltipState.elapsedSeconds` | 与动画时钟同源，暂停/切窗不跳变 |

配色取自参照模组（同源，便于对照）：

- UNCOMMON：`#80FF9019` / `#6075350B`
- RARE：`#80BBBBCC` / `#60606070`
- EPIC：`#80FFBB00` / `#60AA5000`，accent `#FFFFE07A`

### P1 — 下一步（低风险，纯代码）

1. **开框动画换语法**：`ItemIconAnimation` 从 800ms 果冻改为
   `0.25s 放大到 1.33 → 0.25s 回落`，EPIC 额外 `rotateY` 一圈。
   现有 `Easing.outCubic/outExpo` 已具备，只需改 `calculateJellyScale`。
2. **粒子层**：新增 `TooltipParticles`（16~24 个对象池），
   ICON 周围 0.15s 一颗、inward 0.2s 一颗，用 `state.elapsedSeconds()` 驱动。
   注意：粒子必须在 `drawShimmer` 之后、文本之前画，避免盖住文字。
3. **音效节流**：开框音效接 `state` 的 uuid + 300ms 节流（照抄参照语义即可）。
4. **微光扩展到 GregTech 电压色**：`TooltipColorHelper` 里 `color != -1` 分支
   目前 `shimmer=false`，可加配置让高电压物品也有微光（默认关，避免刷屏）。

### P2 — 功能层（需要权衡）

1. **3D 盔甲/工具预览**：需要 `RenderLivingBase`/`TileEntityItemStackRenderer`
   在 GUI 里渲染实体，涉及光照与 scissor，工作量大；固定布局下会挤压文本宽度，
   建议作为可选开关（默认关）。
2. **滚动**：本模组用分页替代滚动，二者不要同时开；若要做，复用
   `smoothScrolling` 的弹簧参数即可。
3. **9-slice 边框**：需要美术资源（140×140 图集切 6 块），不是代码问题；
   在拿到贴图前不要做。

---

## 4. 明确不搬的东西

| 东西 | 原因 |
| --- | --- |
| JSON 样式系统 / 资源包主题 / 14 种过滤器 | 布局固定，数据驱动外观的收益为负；用户已明确否决 |
| 每帧重建组件列表 | 本模组已有 `TooltipState.resolveContent` 缓存 |
| 绝对时间动画 | 已改为 `elapsedMs + 50ms clamp` |
| `TooltipDefinition.aggregateStyleFor` | 等价物是 `TooltipColorHelper`，无需第二套 |

---

## 5. 验收方式

- **视觉**：分别看 COMMON/UNCOMMON/RARE/EPIC 各一件物品；EPIC 微光应为
  约 1.5~2s 一个周期的缓慢流动，不能闪烁。
- **回退**：`tooltip_aesthetic.enabled=false` 后面板必须与改前逐像素一致
  （仅 `TooltipColors` 的 3 参数构造器路径）。
- **性能**：微光上限 192 个 quad，打开 tooltip 时帧耗不应有可感知变化；
  若掉帧，先把 `segX/segY` 上限从 48 降到 24。
- **兼容**：装备对比的副框不传时间（静止），不参与微光；JEI/TOP 的 overlay
  仍应正常叠加（事件契约已修）。
