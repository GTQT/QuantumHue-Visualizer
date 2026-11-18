# Tooltip 字体特效

物品提示框（Tooltip）的一套轻量文字动效：逐行打字机、边缘发光、高亮条、故障乱码爆发与呼吸脉冲。默认全部开启，可在配置文件的 `Tooltip Text Effects` 分组里逐项关闭。

特效由**行内触发标签**驱动，不匹配任何具体文案。例如物品说明写成：

```
[效果]冷却液泄漏，反应堆已失控
```

`[效果]` 本身不会显示，也不会参与换行与宽度计算；它后面的整行文字获得“发光 + 高亮条 + 故障”组合。标签可以只覆盖一部分文字，也可以一行内多次出现：

```
[高亮]攻击力 +12[/高亮]  [发光]稀有[/发光]
```

实现目标不是复刻整段 HUD 动画，而是从参考 GIF 里提取**适合 10px 行高、225px 宽度上限**的几种效果，保证稳定态开销接近原版。

## 触发标签

| 标签 | 英文别名 | 效果 |
| --- | --- | --- |
| `[效果]` | `[effect]` `[fx]` `[特效]` | 发光 + 高亮条 + 故障，三者全开 |
| `[发光]` | `[glow]` | 仅边缘发光 |
| `[高亮]` | `[highlight]` `[hl]` | 仅高亮条 |
| `[故障]` | `[glitch]` `[乱码]` | 仅故障乱码 |
| `[/效果]` 等 | `[/effect]` 等 | 关闭对应效果；省略时持续到行尾 |

- 英文标签不区分大小写；中文标签按原样匹配。
- 未闭合的标签持续到行尾；标签可以出现在行首、行中或行尾。
- 多个标签可以叠加，关闭某一个只清除对应的效果位。
- 未识别的方括号内容（例如其他模组的 `[Shift]`）按普通文本保留。
- 标签在换行**之前**被移除，因此不会占用宽度，也不会把一行挤到下一行。
- 即使总开关 `enabled=false`，标签仍会被剥离，只是不再绘制特效。

## 参考与取舍

参考素材：`1c87a0ecd90679bac420a4f1cf143ebf.gif`（654×666，402 帧，150ms/帧）。逐帧统计后的结论：

| 观察到的现象 | 数据 | 本项目取舍 |
| --- | --- | --- |
| 逐行打字机 | 约 10 字/秒，单行 1.5–5.25s | ✅ 采用节奏，但压缩到整框 0.42s（Tooltip 是瞬态 UI，5s/行太慢） |
| 红色泛光 + 白色高光 | 主色 rgb(214,41,2)，光晕 rgb(251,180,180) | ✅ 采用，四向加法叠绘 |
| 重点短语背后的高亮条 | 覆盖整段短语，底部有亮线 | ✅ 采用，由 `[高亮]` 指定范围 |
| 故障乱码 | 字形位移、缺字、白块、红/青残影，集中在 180–270 帧 | ✅ 采用，仅 `[故障]` 片段周期爆发 |
| 全屏/整行抖动 | 静态行中心抖动仅 0.09–0.18px | ❌ 不做，收益低且伤可读性 |
| 红→橙→黄色相循环 | 实测主色恒定，早前判断是分析图增益造成的误判 | ❌ 不做 |
| 自定义像素字体、超大号数字 | 需要自带字体纹理与大字号排版 | ❌ 不做，保持原版字体与布局 |

## 类结构

| 类 | 职责 |
| --- | --- |
| `meowmel.quantumhue.tooltips.text.GlyphLine` | 把一行显示文本解析成字形序列：`§` 颜色/样式、粗体 +1px、中文全角、代理对；缓存相对偏移，不依赖屏幕坐标 |
| `meowmel.quantumhue.tooltips.text.TooltipTextLine` | 一行的显示文本 + 按可见下标记录的特效区间（start/end/位掩码） |
| `meowmel.quantumhue.tooltips.text.TooltipTextMarkup` | 解析 `[效果]/[发光]/[高亮]/[故障]` 标签，剥离标签并生成区间；未知标签原样保留 |
| `meowmel.quantumhue.tooltips.text.TooltipTextRenderer` | 每帧时钟、打字机进度、发光、高亮条、故障爆发与异常回退 |
| `meowmel.quantumhue.tooltips.TooltipUtils#wrapTooltipText` | 先剥标签再按宽度换行，并把特效区间裁剪/平移到每个换行片段 |
| `meowmel.quantumhue.tooltips.TooltipRenderer` | 原绘制入口；构造特效渲染器，把物品名与正文交给它 |
| `meowmel.quantumhue.QuantumHueConfig.TooltipTextEffects` | 配置分组 `tooltip_text_effects` |
| `meowmel.quantumhue.tooltips.TooltipContent#getCurrentPage` | 给打字机时钟提供分页 key，翻页后重新播放 |

## 渲染时序

```
AdvancedTooltipHandler
  ├─ TooltipContentExtractor.extractTooltipContent(...)   // TooltipTextMarkup.parse 每行
  ├─ TooltipUtils.wrapTooltipText(...)                    // 已剥标签的文本换行 + 区间切片
  ├─ PaginationHandler / TooltipLayoutManager             // 只看到显示文本，宽度不受标签影响
  └─ TooltipRenderer.drawTooltipText(content, layout, font, colors)
       ├─ textEffects.beginFrame(content, lines, font)   // 每帧一次
       ├─ textEffects.drawItemName(...)                  // 物品名：只发光
       └─ for each TEXT line:
            textEffects.drawBodyLine(styled, x, y, lineIndex, borderColor, rightBound)
              ├─ 计算打字机可见字数
              ├─ 对 [高亮] 区间画色带
              ├─ 对 [发光]/[高亮] 区间叠四向光晕
              ├─ 对 [故障] 区间检查爆发窗口；命中时逐字形重绘并 return，白块按 rightBound 裁剪
              └─ 无故障时一次 drawStringWithShadow(已截断的原文)
```

`ASPECT_SPACER`、要素图标、分页提示、AppleSkin 食物信息仍走原逻辑，不受影响。

## 五种特效

### 1. 打字机

- 每个 Tooltip 独立计时，key = `物品名 + 分页号`；超过 600ms 未渲染视为重新打开，从 0 开始。
- `charsPerMs = 最长正文行的字形数 / introDuration`，所以最长的那行恰好用 `introDuration` 显示完。
- 第 `lineIndex` 行延迟 `lineIndex × lineStagger` 开始，整框完全展开约 `introDuration + (行数-1) × lineStagger`。
- 只绘制前 `visible` 个字形的原文前缀，保留 `§` 码，因此颜色/粗体在打字过程中不丢失。

### 2. 边缘发光

- 只作用于带 `[发光]` / `[高亮]` / `[效果]` 的区间，以及物品名。
- 对可见片段做 4 次偏移绘制：`(x±1, y)`、`(x, y±1)`。
- 混合模式 `SRC_ALPHA / ONE`（加法），alpha = `255 × glowStrength × 系数`。
- 物品名用边框色（`TooltipColors.borderStart`），`[发光]` 用边框色低强度，`[高亮]`/`[效果]` 用高亮色高强度。

### 3. 高亮条

- 只作用于带 `[高亮]` / `[效果]` 的区间，范围由标签精确指定，不做自动扩词。
- 画三层：外层柔光（±3px）、主体色带（`y-1 .. y+9`）、底部亮线（2.2 倍 alpha）。
- 色带宽度随打字进度增长，不会在文字还没出现时先亮。

### 4. 故障爆发

- 只作用于带 `[故障]` / `[效果]` 的区间；同一行内未标记的文字保持稳定。
- 从 Tooltip 打开起计时，每 `glitchInterval` 毫秒触发一次，持续 `glitchDuration`，不同行用 `lineIndex × 137ms` 错开。
- 打字机结束后（`introDuration + 500ms` 之前）不触发，避免打断入场；默认首次爆发约在打开后 5.4s。
- 强度包络：`min(1, p×6) × min(1, (1-p)×4)`，起落都有缓入缓出。
- 爆发时：
  - 红 `0xFF2A2A` / 青 `0x2AFFFF` 两份残影，alpha 随强度变化；
  - 区间内逐字形哈希抖动 1–2px；
  - 约 8% 字形消失，约 17% 被 `#%&?!*<>/|\=+~` 替换；
  - 少量字形染成暖红/青/金色；
  - 高强度时随机叠加白色块遮挡。
- 哈希每 90ms 换一代，所以乱码是“跳变”而不是平滑移动。

### 5. 呼吸脉冲

- 周期 8s，`0.86 + 0.14 × sin(...)`，作用于带特效标签区间的发光与高亮条 alpha；普通文字不脉冲。

## 配置项

`config/quantumhue.cfg` → `[Tooltip Text Effects]`，或游戏内配置界面同名分组。

| 键 | 默认 | 范围 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `true` | 布尔 | 总开关，关闭后标签仍被剥离，但完全走原版绘制 |
| `typewriter` | `true` | 布尔 | 逐行打字机 |
| `introDuration` | `420` | 0–3000 ms | 最长一行的展开时间 |
| `lineStagger` | `25` | 0–200 ms | 相邻行启动间隔 |
| `glow` | `true` | 布尔 | 边缘发光 |
| `glowStrength` | `0.35` | 0.0–1.0 | 发光强度 |
| `highlight` | `true` | 布尔 | 高亮条 |
| `highlightColor` | `0xFFFF3B30` | ARGB | 高亮条与 `[高亮]` 发光颜色 |
| `glitch` | `true` | 布尔 | 故障爆发 |
| `glitchInterval` | `7000` | 1000–60000 ms | 两次爆发间隔 |
| `glitchDuration` | `420` | 100–1500 ms | 单次爆发时长 |
| `glitchIntensity` | `0.45` | 0.0–1.0 | 抖动幅度/残影强度 |

标签语法本身是固定的（见上表），不通过匹配文案触发。把 `glow` / `highlight` / `glitch` 分别关掉即可只保留其中一种表现。

## 兼容与性能

- **标签不占宽度**：`TooltipTextMarkup` 在换行前剥离标签，`TooltipLayoutManager` 与 `FontRenderer.listFormattedStringToWidth` 只看到显示文本；标签不会导致 Tooltip 变宽或提前换行。
- **§ 码**：解析颜色与 `l/o/n/m/k/r` 样式，绘制时用原文前缀，格式不丢；标签可以夹在格式码之间。
- **粗体**：字宽 +1px，与原版 `FontRenderer` 的粗体逻辑一致。
- **中文/全角**：走 `FontRenderer.getCharWidth`；代理对（emoji 等）按 9px 单字形处理。
- **SmoothFont**：只调用 `FontRenderer` 公开的 `drawString` / `drawStringWithShadow`，不碰字形纹理或字符宽度数组；SmoothFont 的按字重绘可以正常接管。
- **自定义字体**：现代启动画面的 `SplashFontRenderer` 有独立渲染路径，不会进入 Tooltip 特效。
- **稳定态开销**：无标签行 1 次主绘制；带发光标签的片段额外 4 次偏移绘制，`FontRenderer` 会缓存相同字符串/颜色的 display list。只有 `[故障]` 片段处于爆发窗口内才会逐字重绘。
- **缓存**：最多 96 行解析结果（LRU）、8 个 Tooltip 计时器；换字体时自动清空。
- **异常回退**：任一特效抛异常，`failed` 置位并记录一次日志，之后该渲染器直接走原版 `drawStringWithShadow`，不会每帧刷屏。
- **装备对比**：多个 Tooltip 同时渲染时按 `物品名+分页` 各自计时，互不干扰。

## 已知限制

- 特效是**行内片段级**的：不做逐字入场、逐字上浮、超大号数字；这些需要改布局与字号，超出当前 225px 排版范围。
- 标签没有转义语法；如果确实要显示 `[效果]` 字面量，目前只能避开该写法（或改用全角括号等变体）。
- 标签是平坦的效果位，不是嵌套栈；同一效果嵌套时，内层关闭会同时关掉外层。
- 未闭合标签会持续到行尾；跨行需要每行重新写标签。
- 打字机进行中把鼠标移开再移回（600ms 内），会从当前进度继续而不是重播；这是为了避免频繁移入移出导致闪烁。
- 翻页会重新播放打字机（key 含分页号），这是有意为之。
- 时钟基于 `System.currentTimeMillis()`，严重卡顿后会直接跳到完成态，不做补帧。
- 纯客户端效果，不发送任何网络数据。

## 调参建议

- 更接近参考 GIF 的压迫感：`introDuration=700`、`glowStrength=0.5`、`highlightColor=0xFFFF2A1A`、`glitchDuration=600`、`glitchInterval=5000`。
- 更克制：`typewriter=false`、`glitch=false`、`glowStrength=0.2`。
- 只想要发光：`typewriter=false`、`highlight=false`、`glitch=false`，正文写 `[发光]...`。

## 后续可扩展

- 数字强调：对 `+12 攻击力` 这类数值做一次放大/闪白（需要独立字号渲染）。
- 逐字上浮入场：在打字机基础上给每个字形加 `sin` 偏移。
- 按物品/语言键定制样式：例如只在 spawn egg 或 boss 掉落物上启用故障。
- 配置界面内实时预览：复用 `TooltipTextRenderer` 在一个假 Tooltip 上跑时钟。
- 打字音效：按可见字数触发短音，需新增客户端音效注册。
