package meowmel.quantumhue.task.server;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.chat.packets.TaskActionPacket;
import meowmel.quantumhue.chat.packets.TaskSyncPacket;
import meowmel.quantumhue.chat.server.ChatServerListener;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskComment;
import meowmel.quantumhue.task.model.TaskItem;
import meowmel.quantumhue.task.model.TaskLimits;
import meowmel.quantumhue.task.model.TaskSummary;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 任务板服务端。所有修改都在服务端线程执行，内存模型 + 5 秒防抖落盘。
 */
public final class TaskService {

    private static TaskDatabase database;

    private TaskService() {
    }

    public static synchronized void load(File worldDir) {
        File file = new File(new File(worldDir, "quantumhue"), "tasks.json");
        database = new TaskDatabase(file);
        database.load();
        long archiveMs = Math.max(1, QuantumHueConfig.taskBoard.archiveDays) * 24L * 60L * 60L * 1000L;
        database.archiveOld(System.currentTimeMillis(), archiveMs);
    }

    public static synchronized void save() {
        if (database != null) {
            database.save();
        }
    }

    public static synchronized void maybeSave() {
        if (database != null && database.isDirty()
                && System.currentTimeMillis() - database.lastSave() > 5000L) {
            database.save();
        }
    }

    public static synchronized void handle(EntityPlayerMP player, TaskActionPacket pkt) {
        if (player == null || pkt == null || database == null) {
            return;
        }
        String groupId = pkt.groupId == null ? "" : pkt.groupId;
        if (!ChatServerListener.isGroupMember(groupId, player.getUniqueID())) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_MEMBER, "你不在这个群里");
            return;
        }
        switch (pkt.action) {
            case TaskActionPacket.CREATE:
                create(player, groupId, pkt);
                break;
            case TaskActionPacket.UPDATE:
                update(player, groupId, pkt);
                break;
            case TaskActionPacket.CHECK:
                check(player, groupId, pkt);
                break;
            case TaskActionPacket.COMPLETE:
                complete(player, groupId, pkt);
                break;
            case TaskActionPacket.COMMENT:
                comment(player, groupId, pkt);
                break;
            case TaskActionPacket.DELETE:
                delete(player, groupId, pkt);
                break;
            case TaskActionPacket.SYNC_REQ:
                sendSnapshot(player, groupId);
                break;
            default:
                sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "未知操作");
                break;
        }
    }

    private static void create(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        String title = clean(pkt.title);
        if (title.isEmpty()) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "任务标题不能为空");
            return;
        }
        long now = System.currentTimeMillis();
        Task task = new Task();
        task.id = database.allocateTaskId();
        task.groupId = groupId;
        task.title = limit(title, TaskLimits.MAX_TITLE);
        task.desc = limit(clean(pkt.desc), TaskLimits.MAX_DESC);
        task.creatorUuid = player.getUniqueID().toString();
        task.creatorName = player.getName();
        task.createdAt = now;
        task.updatedAt = now;
        task.rev = 1;

        List<String> items = pkt.itemTexts == null ? new ArrayList<>() : pkt.itemTexts;
        int maxItems = Math.max(1, QuantumHueConfig.taskBoard.maxItemsPerTask);
        int n = 0;
        for (String raw : items) {
            if (n >= maxItems) {
                break;
            }
            String text = limit(clean(raw), TaskLimits.MAX_ITEM_TEXT);
            if (text.isEmpty()) {
                continue;
            }
            n++;
            task.items.add(new TaskItem("i" + n, text));
        }
        task.recomputeCompletion(now);
        database.put(task);
        database.pruneGroup(groupId, Math.max(10, QuantumHueConfig.taskBoard.maxTasksPerGroup));
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    private static void update(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        Task task = database.get(groupId, pkt.taskId);
        if (task == null || task.deleted) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "任务不存在或已删除");
            return;
        }
        if (pkt.rev != task.rev) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_STALE, "任务已被其他人修改");
            return;
        }
        String title = clean(pkt.title);
        if (title.isEmpty()) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "任务标题不能为空");
            return;
        }
        long now = System.currentTimeMillis();
        task.title = limit(title, TaskLimits.MAX_TITLE);
        task.desc = limit(clean(pkt.desc), TaskLimits.MAX_DESC);

        List<String> ids = pkt.itemIds == null ? new ArrayList<>() : pkt.itemIds;
        List<String> texts = pkt.itemTexts == null ? new ArrayList<>() : pkt.itemTexts;
        List<TaskItem> next = new ArrayList<>();
        int nextNum = database.nextItemNumber(task);
        int maxItems = Math.max(1, QuantumHueConfig.taskBoard.maxItemsPerTask);
        for (int i = 0; i < texts.size() && next.size() < maxItems; i++) {
            String text = limit(clean(texts.get(i)), TaskLimits.MAX_ITEM_TEXT);
            if (text.isEmpty()) {
                continue;
            }
            String id = i < ids.size() ? ids.get(i) : "";
            TaskItem old = task.findItem(id);
            TaskItem item = old != null ? old : new TaskItem("i" + (nextNum++), text);
            item.text = text;
            next.add(item);
        }
        task.items = next;
        task.recomputeCompletion(now);
        task.rev++;
        task.updatedAt = now;
        database.put(task);
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    private static void check(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        Task task = database.get(groupId, pkt.taskId);
        if (task == null || task.deleted) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "任务不存在或已删除");
            return;
        }
        TaskItem item = task.findItem(pkt.itemId);
        if (item == null) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "检查项不存在");
            return;
        }
        long now = System.currentTimeMillis();
        item.done = pkt.done;
        if (pkt.done) {
            item.doneByUuid = player.getUniqueID().toString();
            item.doneByName = player.getName();
            item.doneAt = now;
        } else {
            item.doneByUuid = "";
            item.doneByName = "";
            item.doneAt = 0L;
        }
        task.recomputeCompletion(now);
        task.rev++;
        task.updatedAt = now;
        database.put(task);
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    private static void complete(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        Task task = database.get(groupId, pkt.taskId);
        if (task == null || task.deleted) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "任务不存在或已删除");
            return;
        }
        if (task.totalCount() > 0) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "有检查项的任务请逐项勾选");
            return;
        }
        if (pkt.rev != task.rev) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_STALE, "任务已被其他人修改");
            return;
        }
        long now = System.currentTimeMillis();
        task.completed = pkt.done;
        task.completedAt = pkt.done ? now : 0L;
        task.rev++;
        task.updatedAt = now;
        database.put(task);
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    private static void comment(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        Task task = database.get(groupId, pkt.taskId);
        if (task == null || task.deleted) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "任务不存在或已删除");
            return;
        }
        // 评论是追加语义，不要求 rev；否则连续发两条评论时第二条会被误判为过期。
        String text = limit(clean(pkt.comment), TaskLimits.MAX_COMMENT_TEXT);
        if (text.isEmpty()) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "评论不能为空");
            return;
        }
        if (task.comments.size() >= Math.max(10, QuantumHueConfig.taskBoard.maxCommentsPerTask)) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_LIMIT, "评论太多了");
            return;
        }
        long now = System.currentTimeMillis();
        task.comments.add(new TaskComment("c" + database.nextCommentNumber(task),
                player.getUniqueID().toString(), player.getName(), text, now));
        task.rev++;
        task.updatedAt = now;
        database.put(task);
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    private static void delete(EntityPlayerMP player, String groupId, TaskActionPacket pkt) {
        Task task = database.get(groupId, pkt.taskId);
        if (task == null || task.deleted) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_NOT_FOUND, "任务不存在或已删除");
            return;
        }
        UUID uuid = player.getUniqueID();
        boolean creator = uuid.toString().equals(task.creatorUuid);
        if (!creator && !ChatServerListener.isGroupOwner(groupId, uuid)) {
            sendError(player, pkt.requestId, TaskSyncPacket.ERROR_INVALID, "只有发布者或群主可以删除");
            return;
        }
        task.deleted = true;
        task.rev++;
        task.updatedAt = System.currentTimeMillis();
        database.put(task);
        database.markDirty();

        PacketHandler.sendTo(TaskSyncPacket.ack(pkt.requestId, task), player);
        broadcast(groupId, task, player.getUniqueID());
    }

    public static synchronized void sendLoginSummary(EntityPlayerMP player) {
        if (player == null || database == null) {
            return;
        }
        for (String groupId : ChatServerListener.getGroupsForPlayer(player.getUniqueID())) {
            sendSummary(player, groupId);
        }
    }

    public static synchronized void sendSummary(EntityPlayerMP player, String groupId) {
        if (player == null || database == null || groupId == null) {
            return;
        }
        if (!ChatServerListener.isGroupMember(groupId, player.getUniqueID())) {
            return;
        }
        List<TaskSummary> summaries = new ArrayList<>();
        for (Task task : database.all(groupId)) {
            summaries.add(task.toSummary());
        }
        PacketHandler.sendTo(TaskSyncPacket.summary(groupId, summaries), player);
    }

    public static synchronized void sendSnapshot(EntityPlayerMP player, String groupId) {
        if (player == null || database == null) {
            return;
        }
        if (!ChatServerListener.isGroupMember(groupId, player.getUniqueID())) {
            return;
        }
        PacketHandler.sendTo(TaskSyncPacket.snapshot(groupId, database.all(groupId)), player);
    }

    private static void broadcast(String groupId, Task task, UUID except) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        Set<UUID> members = ChatServerListener.getGroupMemberUuids(groupId);
        for (UUID uuid : members) {
            if (except != null && except.equals(uuid)) {
                continue;
            }
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
            if (online != null) {
                PacketHandler.sendTo(TaskSyncPacket.patch(task), online);
            }
        }
    }

    private static void sendError(EntityPlayerMP player, String requestId, int code, String message) {
        PacketHandler.sendTo(TaskSyncPacket.error(requestId, code, message), player);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
