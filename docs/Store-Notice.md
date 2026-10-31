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
| `/quantumhue reload` | 重读 `config/quantumhue/*.json` |
| `/quantumhue refresh` | 重读配置，并在客户端/服务端后台重新预取更新日志、公告、社群服务与邮件 |
| `/quantumhue shop` / `news` | 打开对应页面 |

## 配置文件

首次启动会在 `config/quantumhue/` 生成两个文件，之后只读不覆盖：

- `shop.json` —— 市场参数、系统订单池、系统商店分类与商品（商店是本地运营配置）；
- `version.json` —— 当前**整合包**版本号（与模组自身版本无关，公告页显示与更新比较都用它）。

公告/邮件的远程地址、超时、页签这些运营参数**内置在 jar 里**（`NoticeConfig` / `MailConfig`），
不再读写 `notice.json`、`mail.json`；旧版本留下的这两个文件会被忽略，可以直接删掉。
更新日志、公告、社群服务、邮件的正文全部来自远程；本地只保留 `version.json` 的当前版本号
（以及商店的 `shop.json`）。

修改后执行 `/quantumhue reload` 即可生效（商店页每次打开也会重读 `shop.json`）。

### shop.json

```jsonc
{
  "enabled": true,              // 商店总开关
  "currencyName": "货币",
  "market": {
    "playerListingHours": 72,   // 玩家上架存活小时
    "playerOrderHours": 72,     // 玩家收购单存活小时
    "systemOrderHours": 2,      // 系统订单存活小时
    "orderIntervalMinMinutes": 30,
    "orderIntervalMaxMinutes": 60,
    "orderGoodsMin": 3,         // 每次投放的种类数量区间
    "orderGoodsMax": 6,
    "buyPool":  [ { "item": "minecraft:cobblestone", "meta": 0, "minCount": 16, "maxCount": 64, "price": 1 } ],
    "sellPool": [ { "item": "minecraft:torch",       "meta": 0, "minCount": 16, "maxCount": 64, "price": 1 } ]
  },
  "systemShop": {
    "enabled": true,
    "categories": [
      {
        "id": "building",          // 稳定 id，参与库存键，建议不要随意改名
        "name": "建筑方块",         // 页面左侧分类名
        "icon": "minecraft:stone", // 分类图标，留空则用该分类第一件商品
        "iconMeta": 0,
        "items": [
          {
            "id": "minecraft:stone#0", // 留空会自动生成（物品注册名#meta）
            "item": "minecraft:stone", // 物品注册名
            "meta": 0,
            "name": "",                // 显示名覆盖，留空用物品自身名字
            "count": 64,               // 每份包含数量
            "price": 16,               // 每份单价
            "stock": -1                // 库存份数，-1 为无限；有限库存会写进存档
          }
        ]
      }
    ]
  }
}
```

系统商店的货架由**服务端**按配置 + 已售记录重建后同步给客户端，所以专用服务器上以服务端
`shop.json` 为准。已售份数保存在 `quantumhue_shop_data`（`WorldSavedData`）里，重启不会补货。

### version.json

```jsonc
{
  "version": "1.9.0"       // 当前整合包版本号；留空表示版本未知
}
```

- **它只是兜底**：装了 `GTQTCore` 时，当前版本直接反射读取 `meowmel.gtqtcore.GTQTCore.VERSION`，本文件被忽略；
  只有没装核心模组时才读这里；
- 只用于「当前版本」的显示与比较：公告页顶部、更新日志页头部、登录时的更新提示；
- 这里填的是**整合包**版本，不是模组 jar 的版本；**没有代码默认值**，也不会回退到模组版本；
- 两处都没有版本号时：页面显示「版本 未配置」，不做新旧比较，登录也不发更新提示；
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

### 内置的公告参数

写在 `meowmel.quantumhue.notice.NoticeConfig` 里，不走配置文件；要改就改代码并重新编译：

| 字段 | 内置值 | 说明 |
| --- | --- | --- |
| `checkVersionOnLogin` | `true` | 登录时在聊天栏提示版本 |
| `showOnLogin` | `true` | 登录时自动打开公告页 |
| `remote.versionIndexUrl` | GTQT 仓库 `remote/version/index.json` | 版本更新公告索引（raw JSON） |
| `remote.announcementIndexUrl` | GTQT 仓库 `remote/announcement/index.json` | 公告索引（raw JSON） |
| `remote.communityUrl` | GTQT 仓库 `remote/community.txt` | 社群服务文本（raw） |
| `remote.timeoutMs` | `10000` | 单次 HTTP 请求超时 |
| `remote.failureCooldownMs` | `60000` | 失败后的冷却时间 |
| `remote.mirrors` | 空 | 直连失败后依次尝试的镜像模板，`{url}` 为占位符 |
| `tabs` | 更新日志 + 社群服务 | 右侧页签，顺序即显示顺序 |

页签类型：

- `type = update`：读取内存中的更新日志；模组会先比较版本 JSON 文件名/内容里的版本号，
  只保留 **不低于本地版本** 的条目（包含当前版本，旧版本不会展示）；
- `type = community`：读取加载阶段拉取的远程 `community.txt`；
- `type = announcements`：读取公告列表；
- `type = remote` / `type = text`：这两个旧类型不再显示本地正文，只显示空状态；所有正文必须走远程页签；
- 文本支持 `&` 颜色码与 `#` 注释行。
- 「公告」与「邮件」是**内置页签**，不写在 `tabs` 里；它们固定追加在自定义页签之后。

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
