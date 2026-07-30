<img width="2048" height="2048" alt="logo" src="https://github.com/user-attachments/assets/db10f5b0-0a0a-4a72-9bd2-09a40914aa36" />

## 量子色域 — 视界 (QuantumHue Visualizer)

专注于 Minecraft 1.12.2 UI 美化与渲染优化的客户端模组，提供沉浸式、高度可定制的界面体验。

---

### 功能特性

#### 🎨 高级物品提示 (Tooltips)
- 稀有度自适应边框着色，完全可配置的颜色方案
- 物品图标「果冻弹出」弹性动画
- 装备对比系统 —— 并排显示已装备与悬停物品的属性差异
- 分页支持、模组来源标注
- AppleSkin / 神秘时代 / GregTech 集成

#### 💬 自研聊天系统
- **三频道架构**：世界频道 / 私聊 / 群聊，支持自定义网络协议
- **气泡式消息 UI**：暗色半透明面板、玩家头像、时间分隔、刷屏合并
- **表情支持**：内置 50 个常用表情，短码输入（如 `:joy:` `:heart:` `:fire:`），5×10 网格选择器
- **命令分支保护**：命令输入走原版逻辑，不受自定义 UI 影响
- 聊天历史持久化（按世界/服务器分离存储）

#### 📚 内置 Wiki 系统
- Markdown 渲染引擎，支持表格、列表、内联资源
- **LaTeX 公式渲染**（纹理缓存）
- 多方块结构蓝图 3D 预览
- `/wiki` 命令快速打开

#### 📊 IGI 游戏内信息 HUD
- JSON 全配置化布局，支持 25+ 种信息元素
- FPS / TPS / 坐标 / 生物群系 / 光照 / 神秘时代 Vis/Flux 等
- 自由排列、分组、对齐

#### 🌟 现代化启动画面
- 自定义进度条与加载阶段时间预估
- 替换原版单调的加载界面

#### ✨ 其他视觉增强
- **动态模糊**：打开 GUI 时背景模糊（可配置着色器）
- **平滑滚动**：列表滚动动画，可调速度与弹性
- **主菜单美化**：全景背景、重设计布局
- **生物群系提示**：进入新区块时淡入淡出显示群系名
- **中键高亮**：点击方块/实体显示彩色线框轮廓

---

### 集成支持

| 模组 | 集成内容 |
|------|---------|
| Thaumcraft (神秘时代) | Tooltip 要素渲染、IGI Vis/Flux 显示 |
| The One Probe | TOP 面板自定义配色 |
| GregTech | Tooltip 增强、多方块 Wiki 页面 |
| AppleSkin | Tooltip 饱食度/饱和度预览 |

---

### 协议与致谢

本模组代码采用 [MIT License](LICENSE)。

聊天表情图片来自 [Twemoji](https://github.com/twitter/twemoji)，版权归 Twitter/X 所有，遵循 [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) 协议。
