# 群任务板（Group Task Board）

群任务板是聊天系统里的一块**群内公共记事板**：任何群成员都可以发布任务、拆检查项、勾选进度、写评论。它不是任务分配系统，没有负责人、指派、转交或截止时间；目标是把“群里说好要做的事”变成一张大家都能改的清单。

> 适用范围：仅 **GROUP** 频道。世界频道与私聊不显示入口，也不接受任务操作。

---

## 1. 入口

| 位置 | 行为 |
|------|------|
| 群聊页眉右侧 `记事板 N` | 打开当前群的任务板；`N` 是未完成任务数 |
| 输入框上方工具栏 `＋任务` | 打开任务板并直接进入“新建任务”表单；非群聊频道置灰 |
| 聊天流里的任务卡 | 点击打开对应任务详情 |

任务卡只在任务第一次出现在客户端时插入一条，之后**不会**因为勾选、评论、改名而反复刷聊天流。

---

## 2. 界面

任务板是 `ChatScreen` 内的浮层，不开新的 `GuiScreen`，所以打开时聊天输入、频道切换、未读状态都不受影响。点击面板外或右上角 `×` 关闭。

### 列表

- 未完成任务在前，已完成任务在后，已删除任务最后。
- 每张卡显示：标题、`已完成/总数` 进度、评论数、创建者、状态色条。
- 顶部搜索框按标题/说明/创建者过滤。
- 底部：`＋ 新建任务`、`刷新`。

### 详情

- 顶部 `‹ 返回`。
- 标题、说明（只读展示）。
- 检查项：整行点击切换勾选；悬停显示勾选人和时间。
- 评论列表 + 单行评论输入框 + `发送`。
- 底部：
  - `编辑`：任何群成员都可以改标题、说明、增删检查项。
  - `删除`：发布者或群主可用，第一次点击变成“确认删除”，第二次才真正删除（软删除）。
  - 没有检查项时显示 `标记完成` / `重新打开`。

### 新建/编辑表单

- 标题：单行，最多 40 字。
- 说明：多行，最多 500 字；`Enter` 换行，`Ctrl+Enter` 保存。
- 检查项：逐行输入，可 `＋ 添加` / `× 删除`，最多由配置决定（默认 20）。
- 编辑时保留已有检查项的 id 与勾选状态；只改文本、增删行。

---

## 3. 行为规则

### 完成

- 任务**有检查项**时：所有项勾上 → 自动 `completed=true`；取消任意一项 → 自动回到进行中。
- 任务**没有检查项**时：任何成员可在详情里手动标记完成或重新打开。
- 完成/重开都会写 `completedAt` 并广播给在线成员。

### 权限

只校验“是不是这个群的成员”。没有负责人字段。

| 操作 | 谁可以做 |
|------|----------|
| 发布任务 | 任意群成员 |
| 编辑标题/说明/检查项 | 任意群成员 |
| 勾选/取消检查项 | 任意群成员 |
| 评论 | 任意群成员 |
| 标记完成/重开（无检查项） | 任意群成员 |
| 删除 | 发布者或群主 |

删除是软删除：任务从列表消失，聊天卡显示“任务已删除”，历史仍保留直到归档清理。

### 并发

- 创建/勾选/评论/删除都由服务端串行处理。
- 编辑（UPDATE）带 `rev`：如果别人先改了，服务端返回 `ERROR_STALE`，客户端自动拉一次快照，避免用旧内容覆盖新内容。
- 勾选传“目标状态”而不是“切换”，重复包不会把状态翻回去。
- 评论是追加语义，不校验 `rev`，连续发两条不会互相顶掉。

---

## 4. 限额

默认值（可在配置里改）：

| 项 | 默认 |
|----|------|
| 每群任务数 | 100 |
| 每任务检查项 | 20 |
| 每任务评论 | 100 |
| 标题 | 40 字 |
| 说明 | 500 字 |
| 检查项文本 | 60 字 |
| 评论 | 200 字 |
| 已完成任务归档 | 30 天 |

超出每群任务数时，优先淘汰：已删除 → 已完成 → 最久未更新。归档只清理完成超过配置天数的任务。

---

## 5. 配置

`QuantumHueConfig` 的 `Task Board` 段：

| 配置键 | 默认 | 说明 |
|--------|------|------|
| `Enabled` | `true` | 是否显示群任务板入口 |
| `Max Tasks Per Group` | `100` | 每群保留任务上限（10–500） |
| `Max Items Per Task` | `20` | 单任务检查项上限（1–50） |
| `Max Comments Per Task` | `100` | 单任务评论上限（10–500） |
| `Archive Days` | `30` | 已完成任务保留天数（1–365） |

服务端与客户端读取同一份配置；客户端改完配置后需要重新打开任务板/聊天界面让布局刷新。

---

## 6. 持久化

- 路径：`<world>/quantumhue/tasks.json`
- 结构：

```json
{
  "schema": 1,
  "nextTaskId": 12,
  "groups": {
    "g1": {
      "tasks": [
        {
          "id": "t12",
          "groupId": "g1",
          "title": "准备下界合金",
          "desc": "周二前凑齐",
          "creatorUuid": "...",
          "creatorName": "Meowmel",
          "items": [
            { "id": "i1", "text": "挖 8 组远古残骸", "done": true,
              "doneByUuid": "...", "doneByName": "A", "doneAt": 1700000000000 }
          ],
          "comments": [
            { "id": "c1", "authorUuid": "...", "authorName": "B",
              "text": "我这边有多的", "at": 1700000000000 }
          ],
          "completed": false,
          "deleted": false,
          "completedAt": 0,
          "createdAt": 1700000000000,
          "updatedAt": 1700000000000,
          "rev": 3
        }
      ]
    }
  }
}
```

- 保存：写 `tasks.json.tmp`，再 `Files.move(..., ATOMIC_MOVE)`；不支持原子移动时回退 `REPLACE_EXISTING`。
- 触发：修改后标记 dirty，`ServerTickEvent` 每 100 tick（约 5 秒）检查一次；`WorldEvent.Save` 强制落盘。
- 加载：`WorldEvent.Load`（主世界、非 remote）读取；损坏字段尽量容错，缺省 `rev=1`。

---

## 7. 网络协议

新增两个包，注册在 `PacketHandler`：

### `TaskActionPacket`（C→S）

| action | 值 | 主要字段 |
|--------|----|----------|
| `CREATE` | 0 | `requestId, groupId, title, desc, itemTexts` |
| `UPDATE` | 1 | `requestId, groupId, taskId, rev, title, desc, itemIds, itemTexts` |
| `CHECK` | 2 | `requestId, groupId, taskId, itemId, done` |
| `COMMENT` | 3 | `requestId, groupId, taskId, comment` |
| `DELETE` | 4 | `requestId, groupId, taskId` |
| `SYNC_REQ` | 5 | `requestId, groupId` |
| `COMPLETE` | 6 | `requestId, groupId, taskId, rev, done` |

### `TaskSyncPacket`（S→C）

| type | 值 | 内容 |
|------|----|------|
| `SUMMARY` | 0 | 群内任务摘要列表（登录时） |
| `SNAPSHOT` | 1 | 完整任务列表（打开面板/刷新） |
| `PATCH` | 2 | 单个任务变更（在线广播） |
| `ACK` | 3 | 操作者回执，带完整任务 |
| `ERROR` | 4 | 错误码 + 文案 |

错误码：`1` 非群成员、`2` 任务不存在、`3` rev 过期、`4` 参数非法、`5` 超限。

任务/摘要用 Gson 序列化成 UTF-8 JSON，再以 `int 长度 + bytes` 写入 `ByteBuf`，避免 1.12.2 `writeUTF8String` 的 32767 字节上限。

---

## 8. 代码结构

```
task/model/      Task, TaskItem, TaskComment, TaskSummary, TaskLimits
task/server/     TaskDatabase, TaskService, TaskEvents
task/client/     TaskStore
chat/packets/    TaskActionPacket, TaskSyncPacket
chat/            TaskPanel, TaskCardRenderer, TaskTextArea
```

- `TaskService`：服务端唯一写入口；所有操作先校验群成员，再改内存模型并广播。
- `TaskDatabase`：`tasks.json` 读写、id 分配、裁剪、归档。
- `TaskStore`：客户端缓存；处理 SUMMARY/SNAPSHOT/PATCH/ACK/ERROR；任务首次出现时插入聊天卡。
- `TaskPanel`：列表/详情/表单三视图，画在 `ChatScreen` 上。
- `TaskCardRenderer`：聊天流任务卡；完整任务缺失时回退到 `TaskSummary`。
- `TaskEvents`：世界加载/保存、登录摘要、5 秒防抖。

---

## 9. 构建

```bash
export GRADLE_USER_HOME="C:/Users/Meowmel/AppData/Local/Temp/qh-gradle-home"
export JDK16="$GRADLE_USER_HOME/jdks/azul_systems__inc_-16-amd64-windows/zulu16.32.15-ca-fx-jdk16.0.2-win_x64"
./gradlew build --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.java.installations.paths="$JDK16"
```

产物：`build/libs/quantumhue-1.1.0.jar`。

---

## 10. 当前边界

- 不做负责人/指派/转交；所有成员平权。
- 不绑定坐标、维度、传送、商店或邮件奖励。
- 评论只留在任务详情，不进入聊天流；任务卡只显示评论数量。
- 任务卡在创建后只发一次；离线期间创建的任务，登录后可能只有摘要而没有聊天卡，打开面板即可看到完整内容。
- 没有独立的推送通知；完成时会在群里追加一条系统消息。
