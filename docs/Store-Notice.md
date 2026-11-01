# 商店 / 公告 / 邮件系统

本模组接管了原先 GTQT Core 的更新检查、登录公告与自由交易商店，并补上了系统商店与邮件系统。
公告与商店共用同一套整屏外壳与配色（`meowmel.quantumhue.page.PageScreen` / `PageTheme`），
配色方案可以在界面里切换（标题栏关闭键左边的设置键），与 GuideQT 的强调色一致。
ESC 暂停菜单的「商店」「公告」磁贴现在直接打开本模组的界面，不再通过反射探测 GTQT Core。

远程内容统一放在整合包仓库的 `remote/` 目录。每个目录有一个 `index.json` 列出文件名，
模组先读索引，再按索引里的相对路径拉取文件；不再调用 GitHub Contents API。

```
remote/
  version/
    index.json                版本索引（列出 1.8.0.json、1.9.0.json ...）
    1.9.0.json                一个版本一个 JSON
  announcement/
    index.json                公告索引
    <announcement>.json       一条公告一个 JSON
  mail/
    index.json                邮件索引
    <mail>.json               一封邮件一个 JSON（正文 + 奖励）
  community.txt               社群服务文本
```

更新日志、公告、社群服务、邮件都在**游戏加载阶段**由后台线程预取到内存；进入游戏后页面只读内存，
除非执行 `/quantumhue refresh`，不会主动联网。**不写本地缓存文件。**

## 入口

| 入口 | 说明 |
| --- | --- |
| ESC → 商店 / 公告 | 打开 `GuiShop` / `GuiAnnouncement` |
| `/market` | 打开商店 |
| `/announcement` | 打开公告页（右侧含「公告」「邮件」页签） |
| `/sell <单价> [数量]` | 把手持物品上架到市场 |
| `/quantumhue reload` | 丢弃远程缓存，重新采用当前经 API 注册的配置 |
| `/quantumhue refresh` | 重读配置，并在客户端/服务端后台重新预取更新日志、公告、社群服务与邮件 |
| `/quantumhue shop` / `news` | 打开对应页面 |

## 配置来源

商店与整合包版本**不再读写本地 json 文件**，改为通过对外开放 API 注册：

- `meowmel.quantumhue.api.QuantumHuePageApi` 的 `addShopCategory` / `addShopItem` / `addMarketBuyPool` / `addMarketSellPool` —— 往商店里**增量添加**分类、商品与系统订单池条目；
- `QuantumHuePageApi#registerPackVersion(String)` —— 当前**整合包**版本号（与模组自身版本无关，公告页显示与更新比较都用它）。

商店是**增量注册**（不是一次性整棵替换），分三步：

1. QuantumHue 在加载初始阶段（`FMLPreInitializationEvent`）实例化一个**参数不变的默认商店**；
2. 各模组在各自的加载阶段（`preInit` / `init` / `postInit` 均可）调用 `addShop*` **添加**分类与商品——这一步只写进记录表，不实际改动生效配置；
3. 游戏加载完成阶段（`FMLLoadCompleteEvent`）由 QuantumHue 从记录表统一构建商店。

因为是「先记录、后构建」，所以多个模组往同一分类加东西时**不必关心彼此的加载顺序**：A 模组建分类 `ore` 并加铁矿、B 模组往 `ore` 加铜矿，无论 A、B 谁先加载，同名分类都会合并成一条。市场节奏参数、货币名等由 QuantumHue 侧的默认实例提供，注册方只填业务内容。

未注册时的默认状态：

- 商店是**空目录**（没有分类、没有订单池，系统不投放订单）；
- 整合包版本未注册时为内置的 `1.0.0`；`GTQTCore` 会主动把自己的 `VERSION` 经 API 注册进来。

公告的远程地址、超时、镜像与本机代理端口**只有一份**：`config/quantumhue.cfg` 的 `Notice` 段；
邮件参数只内置在 `MailConfig` 里。两者都没有 `notice.json` / `mail.json`，旧版本留下的
这两个文件会被忽略，可以直接删掉。同理，旧版本生成的 `shop.json` / `version.json` 现在也不再读取，可以直接删掉。
更新日志、公告、社群服务、邮件的正文全部来自远程。

改了经 API 注册的配置需要**重新构建 / 重启模组**才会生效（配置写死在代码里）；`/quantumhue reload` 用于丢弃远程缓存、重新采用当前配置，`/quantumhue refresh` 会重新预取远程内容。在模组设置里改 `Notice` 段的话，关闭设置界面就立即生效，不需要重启。

### 注册示例

分类、商品、订单池都用增量 API 添加；`ShopConfig.PoolEntry` 与商品的字段含义与原 `shop.json` 一致：

```java
// 建分类（同 id 会合并；name/icon 只补空、不覆盖已有非空值）
QuantumHuePageApi.addShopCategory("building", "建筑方块", "minecraft:stone");
// 往分类加商品：物品注册名、meta、显示名覆盖（留空用物品自身名字）、每份数量、每份单价、库存（-1 无限）
QuantumHuePageApi.addShopItem("building", "minecraft:stone", 0, "", 64, 16, -1);
// 别的模组也能往同一个 building 分类里补货，不用管谁先加载
QuantumHuePageApi.addShopItem("building", "minecraft:planks", 0, "", 64, 16, -1);
// 嫌 item+meta 麻烦？直接丢一个 ItemStack：注册名/meta/每份数量都从堆栈里取
ItemStack torch = new ItemStack(Blocks.TORCH, 64);
QuantumHuePageApi.addShopItem("utility", torch, 8, -1);
// 系统订单池：收购池（从玩家手里回收）与出售池（向玩家投放）
QuantumHuePageApi.addMarketBuyPool(new ShopConfig.PoolEntry("minecraft:cobblestone", 0, 16, 64, 1));
QuantumHuePageApi.addMarketSellPool(new ShopConfig.PoolEntry("minecraft:torch", 0, 16, 64, 1));
```

市场节奏参数（上架/订单存活小时、投放间隔与种类数）与货币名不在这里配置，保持 QuantumHue 默认商店实例里的值。

- 商品/分类 `id` 留空会在构建时自动生成（分类为 `category`、商品为 `物品注册名#meta`），越界值会被夹到合法区间；同一个分类 id 的多条 `addShopCategory` / `addShopItem` 会合并到一条货架；
- `Item` 构造参数依次为：物品注册名、meta、显示名覆盖（留空用物品自身名字）、每份数量、每份单价、库存份数（`-1` 为无限，有限库存会写进存档）；
- 系统商店的货架由**服务端**按配置 + 已售记录重建后同步给客户端，所以专用服务器上以服务端注册的配置为准。已售份数保存在 `quantumhue_shop_data`（`WorldSavedData`）里，重启不会补货。

### 整合包版本

```java
QuantumHuePageApi.registerPackVersion("1.9.0");   // 传空串表示版本未知
```

- **只有一个来源**：这里经 API 注册的值。`GTQTCore` 不再被反射读取，而是主动调用 `registerPackVersion(meowmel.gtqtcore.GTQTCore.VERSION)` 把自己的版本注册进来；
- 只用于「当前版本」的显示与比较：公告页顶部、更新日志页头部、登录时的更新提示；
- 这里填的是**整合包**版本，不是模组 jar 的版本；未注册时兜底为内置的 `1.0.0`，也不会回退到模组版本；
- 版本为空串（显式注册了空值）时：页面显示「版本 未配置」，不做新旧比较，登录也不发更新提示；
- 改完需要重新预取才会按新版本过滤更新日志：`/quantumhue refresh`。

### theme.json

商店 / 公告页的**界面配色**，纯客户端，第一次在设置页改配色时才生成：

```jsonc
{
  "accentColor": "#00E5FF"   // 强调色，写法与 GuideQT 的 settings.json 相同
}
```

- 可选方案：量子青（默认）、鎏金、冰蓝、青翠、紫晶、珊瑚；后五个与 GuideQT 的强调色完全一致；
- 只影响客户端观感：整页的边框、外发光、标题栏分割线、页签、滚动条、输入框、主按钮都由它推导，
  深色底与红/橙/绿的语义色（关闭、出售、警告、成功）不跟着变；
- 也可以在标题栏关闭键左边的设置键里改，点色卡就生效并写盘，不需要重启。

### 公告参数

`config/quantumhue.cfg` 的 `Notice` 段（`QuantumHueConfig.notice`）是公告参数的**唯一来源**，
没有另一份内置默认值需要覆盖；代码里直接读它，所以改完 cfg 或模组设置界面就是生效路径：

| cfg 键 | 默认值 | 说明 |
| --- | --- | --- |
| `Check Version On Login` | `true` | 登录时在聊天栏提示版本 |
| `Show On Login` | `true` | 登录时自动打开公告页 |
| `Version Index Url` | GTQT 仓库 `remote/version/index.json` | 版本更新公告索引（raw JSON） |
| `Announcement Index Url` | GTQT 仓库 `remote/announcement/index.json` | 公告索引（raw JSON） |
| `Community Url` | GTQT 仓库 `remote/community.txt` | 社群服务文本（raw） |
| `Timeout Ms` | `10000` | 单次 HTTP 请求超时 |
| `Mirrors` | 空 | 直连失败后依次尝试的镜像模板，`{url}` 为占位符 |
| `Local Proxy Port` | `0` | 直连失败后尝试的本机代理端口，只允许填一个，`0` = 不启用 |

地址类字段留空就是「不拉取该项」；`Local Proxy Port` 超出 0~65535 时视为不启用。

### 固定页签

公告页（`GuiAnnouncement`）的页签写死在代码里，**没有任何可配置页签**，按顺序四个：

1. **更新日志**：读取内存中的更新日志；模组会先比较版本 JSON 文件名/内容里的版本号，
   只保留 **不低于本地版本** 的条目（包含当前版本，旧版本不会展示）；
2. **公告**：`AnnouncementPanel` 绘制，左侧列表、右侧正文，数据来自公告索引列出的 JSON；
3. **社群服务**：加载阶段拉取的远程 `community.txt`；
4. **邮件**：`MailPanel` 绘制，只来自服务端远程数据；`MailConfig.enabled` 为 `false` 时不显示。

地址只决定对应页签有没有数据；拉取失败时页签仍在，显示「尚未同步」的空状态。
文本支持 `&` 颜色码与 `#` 注释行。

### 拉取策略

启动阶段读取当前版本号后，由 `PageBootstrap`（更新日志/公告/社群服务）和
`MailService.preloadAsync`（邮件）在独立守护线程里预取。两边的启动窗口都是 **30 秒**：
单次请求最多 10 秒，失败后每 2 秒重试，直到 30 秒用完或对应来源成功。

- 结果只保存在内存（`UpdateFeedStore` / `CommunityStore` / `AnnouncementStore` / `MailService`）；
- **不写本地缓存文件**，进程重启后重新拉取；
- 版本更新公告按文件名/JSON 中的版本号比较，只下载不低于本地版本的文件（当前版本也会显示）；
- 索引漏了当前版本、或索引暂时不可用时，会直接补拉 `remote/version/<当前版本>.json`；
- 进入游戏后页面不联网；
- 只有 `/quantumhue refresh` 会重新预取（客户端会收到刷新包）。

## 版本更新公告

`remote/version/<version>.json`：

```jsonc
{
  "version": "1.9.0",
  "date": "2026-10-31",
  "title": "1.9.0 重构完成更新公告",
  "body": ["&6标题行", "&7更新内容"]
}
```

- 模组读取 `version/index.json` 得到文件名列表；
- 文件名是版本号时，比本地旧的版本文件**连下载都不会发**；
- 文件名不是版本号时才下载，并以 JSON 里的 `version` 为准；
- 最终只展示不低于本地版本的版本，最新版本号显示在更新日志页顶部。

## 公告系统

`remote/announcement/*.json`，每条公告一个文件：

```jsonc
{
  "id": "2025-refactor",
  "title": "关于项目重构的公告",
  "date": "2025-10-01",
  "author": "GTQT 运营组",
  "pinned": true,
  "body": ["&6标题行", "&7正文第一行"]
}
```

- 公告由 `announcement/index.json` 列出，按 `pinned` + `date` 排序；
- 新增公告：新增 JSON + 把文件名写进 `index.json`。

## 邮件系统

### 内容来源

邮件正文与奖励**只来自远程** `remote/mail/index.json` 及其列出的 JSON。服务端在加载阶段读取索引、
拉取全部邮件到内存，世界加载时直接使用；运行期不自动联网。

邮件参数（`meowmel.quantumhue.mail.MailConfig`）也是内置的，不读 `mail.json`：
`enabled` 总开关，`remote.url` 指向 GTQT 仓库的 `remote/mail/index.json`，`remote.timeoutMs` 为单请求超时。
因为不读本地文件，玩家改存档或配置文件都造不出邮件。

`remote/mail/<name>.json`：

```jsonc
{
  "id": "gtqt-1.9.0-refactor",   // 稳定 id；领取记录按它记
  "title": "1.9.0 重构纪念礼包",
  "sender": "GTQT 运营组",
  "date": "2026-10-31",
  "startsAt": 0,                 // epoch 毫秒，0 = 立即
  "expiresAt": 0,                // epoch 毫秒，0 = 不过期
  "money": 0,                    // 可选，FTB Money
  "body": ["&6标题", "&7正文"],
  "rewards": [
    { "item": "minecraft:diamond", "meta": 0, "count": 8 }
  ]
}
```

### 领取与存档

- 客户端只发送要领取的 `mailId`，不发送奖励内容；
- 服务端重新校验：邮件存在、已开始、未过期、未领取、奖励总量不超过单次上限；
- 物品通过 `ShopDelivery.give` 发放（按最大堆叠拆分，背包满时掉落在脚下），货币通过 `EconomyBridge`；
- 领取记录写入存档 `quantumhue_mail_data`：`{ player, mailId }` 集合；
- 存档里不保存邮件内容：邮件只来自远程实时拉取，重启后需要重新同步；
- 拉取失败保留旧列表，不会清空邮箱。

### 关于单机

专用服务器上，普通玩家无法修改内置的邮件地址与存档，上述约束成立。
单机或自己开房时玩家本人就是服务端，能改配置、能改存档、能开创造——这不由邮件系统负责防范；
它防的是多人服务器里客户端单方面伪造奖励。

## 货币兼容

商店与邮件奖励通过 `meowmel.quantumhue.market.EconomyBridge` 反射调用 FTB Money 的
`getMoney(EntityPlayer)` / `setMoney(EntityPlayer, long)`，不把 FTB Money 变成硬依赖：
没装时界面照常打开，余额为 0，购买/货币奖励会被服务端拒绝并提示。

## 服务端权威与网络包

| 包 | 方向 | 用途 |
| --- | --- | --- |
| `OpenPagePacket` | S→C | `/market`、`/announcement`、登录公告打开界面 |
| `RefreshPagesPacket` | S→C | 手动刷新时通知客户端重新预取 |
| `MessageShopAction` | C→S | 请求系统货架 / 浏览页 / 收购页 / 我的上架 |
| `MessageShopView` | S→C | 分页数据、单条增量、系统货架、余额 |
| `MessageBuyItem` | C→S | 购买玩家上架 |
| `MessageSellToOrder` | C→S | 卖给收购订单 |
| `MessageSystemBuy` | C→S | 购买系统商店商品（只传分类/商品 id/份数） |
| `MessageSellItem` / `MessageRemoveListing` | C→S | 上架 / 下架取回 |
| `MessageMailAction` | C→S | 请求邮件列表/详情、领取（只传 mailId） |
| `MessageMailView` | S→C | 邮件摘要、详情、奖励、错误提示 |

## UI 统一

- 调色板与版式集中在 `PageTheme`：外发光、渐变底、四角括号、标题栏、页签、滚动条、
  输入框、按钮、文字颜色都只有一份定义；其中强调色相关的部分由 `PageTheme.apply(int)` 推导，
  不再硬编码；
- `PageScreen` 提供整屏外壳、裁剪（用 `ScaledResolution#getScaleFactor()`，避免全屏裁掉顶部）、
  滚动条、关闭键、配色设置键与滚轮/按键分发；
- 配色偏好存在 `PageThemeSettings`（`config/quantumhue/theme.json`），设置面板是页面内的
  `PageThemePanel` 浮层，点色卡即写盘，因此不需要关闭页面、也不会丢搜索词和滚动位置；
- 「公告」页签由 `AnnouncementPanel` 绘制，左侧公告列表、右侧正文预览；
- 「邮件」页签由 `MailPanel` 绘制，左侧邮件列表、右侧正文/奖励/领取按钮；
- 两个面板共用同一套配色与滚动条，与商店页保持一致。
