package meowmel.quantumhue.task.client;

import meowmel.quantumhue.chat.ChatMessageStore;
import meowmel.quantumhue.chat.packets.TaskActionPacket;
import meowmel.quantumhue.chat.packets.TaskSyncPacket;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskSummary;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端任务缓存。列表用 SUMMARY，详情用 SNAPSHOT/PATCH；所有写操作先发请求，等服务端 ACK。
 */
@SideOnly(Side.CLIENT)
public final class TaskStore {

    private static final TaskStore INSTANCE = new TaskStore();

    private final Map<String, Map<String, Task>> tasks = new HashMap<>();
    private final Map<String, Map<String, TaskSummary>> summaries = new HashMap<>();
    private final Set<String> carded = new HashSet<>();
    private int requestCounter;
    private String lastNotice = "";
    private long lastNoticeAt;
    private boolean lastNoticeError;
    private String lastCreatedTaskId = "";
    private String lastErrorGroupId = "";

    private TaskStore() {
    }

    public static TaskStore get() {
        return INSTANCE;
    }

    public void reset() {
        tasks.clear();
        summaries.clear();
        carded.clear();
        lastNotice = "";
        lastNoticeAt = 0L;
        lastNoticeError = false;
        lastCreatedTaskId = "";
    }

    public synchronized void handle(TaskSyncPacket packet) {
        if (packet == null) {
            return;
        }
        switch (packet.kind) {
            case TaskSyncPacket.SUMMARY:
                applySummaries(packet.groupId, packet.summaries);
                break;
            case TaskSyncPacket.SNAPSHOT:
                applySnapshot(packet.groupId, packet.tasks);
                break;
            case TaskSyncPacket.PATCH:
                for (Task task : packet.tasks) {
                    applyTask(task, true);
                }
                break;
            case TaskSyncPacket.ACK:
                for (Task task : packet.tasks) {
                    boolean wasNew = applyTask(task, true);
                    if (wasNew && task != null && task.creatorUuid != null
                            && task.creatorUuid.equals(localUuid())) {
                        lastCreatedTaskId = task.id;
                    }
                }
                break;
            case TaskSyncPacket.ERROR:
                lastNotice = packet.message == null ? "任务操作失败" : packet.message;
                lastNoticeAt = System.currentTimeMillis();
                lastNoticeError = true;
                lastErrorGroupId = packet.groupId;
                if (packet.code == TaskSyncPacket.ERROR_STALE && packet.groupId != null
                        && !packet.groupId.isEmpty()) {
                    requestSnapshot(packet.groupId);
                }
                break;
            default:
                break;
        }
    }

    private void applySummaries(String groupId, List<TaskSummary> list) {
        if (groupId == null) {
            return;
        }
        Map<String, TaskSummary> map = new LinkedHashMap<>();
        if (list != null) {
            for (TaskSummary summary : list) {
                if (summary != null && summary.id != null) {
                    map.put(summary.id, summary);
                }
            }
        }
        summaries.put(groupId, map);
    }

    private void applySnapshot(String groupId, List<Task> list) {
        if (groupId == null) {
            return;
        }
        Map<String, Task> taskMap = new LinkedHashMap<>();
        Map<String, TaskSummary> summaryMap = new LinkedHashMap<>();
        if (list != null) {
            for (Task task : list) {
                if (task == null || task.id == null) {
                    continue;
                }
                normalize(task);
                taskMap.put(task.id, task);
                summaryMap.put(task.id, task.toSummary());
            }
        }
        tasks.put(groupId, taskMap);
        summaries.put(groupId, summaryMap);
    }

    /**
     * @return 这个任务此前不在缓存里（用于判断是否要往聊天流里放任务卡）。
     */
    private boolean applyTask(Task task, boolean allowCard) {
        if (task == null || task.groupId == null || task.id == null) {
            return false;
        }
        normalize(task);
        Map<String, Task> map = tasks.get(task.groupId);
        if (map == null) {
            map = new LinkedHashMap<>();
            tasks.put(task.groupId, map);
        }
        Task previous = map.get(task.id);
        map.put(task.id, task);
        Map<String, TaskSummary> summaryMap = summaries.get(task.groupId);
        if (summaryMap == null) {
            summaryMap = new LinkedHashMap<>();
            summaries.put(task.groupId, summaryMap);
        }
        summaryMap.put(task.id, task.toSummary());

        if (previous != null && !previous.completed && task.completed && !task.deleted) {
            ChatMessageStore.addTaskSystem(task.groupId, "✅ 任务「" + task.title + "」已完成");
        }
        if (allowCard && previous == null) {
            String key = task.groupId + "/" + task.id;
            if (!carded.contains(key) && !ChatMessageStore.hasTaskCard(task.groupId, task.id)) {
                ChatMessageStore.addTaskCard(task.groupId, task.id, task.title, task.creatorName);
                carded.add(key);
            }
        }
        return previous == null;
    }

    private static void normalize(Task task) {
        if (task.items == null) {
            task.items = new ArrayList<>();
        }
        if (task.comments == null) {
            task.comments = new ArrayList<>();
        }
        if (task.title == null) {
            task.title = "";
        }
        if (task.desc == null) {
            task.desc = "";
        }
    }

    private static String localUuid() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        return mc.player == null ? "" : mc.player.getUniqueID().toString();
    }

    public synchronized void requestSummary(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        send(TaskActionPacket.syncRequest(nextRequestId(), groupId));
    }

    public synchronized void requestSnapshot(String groupId) {
        requestSummary(groupId);
    }

    public synchronized void createTask(String groupId, String title, String desc, List<String> items) {
        send(TaskActionPacket.create(nextRequestId(), groupId, title, desc, items));
    }

    public synchronized void updateTask(Task task, String title, String desc,
                                        List<String> itemIds, List<String> itemTexts) {
        if (task == null) {
            return;
        }
        send(TaskActionPacket.update(nextRequestId(), task.groupId, task.id, task.rev,
                title, desc, itemIds, itemTexts));
    }

    public synchronized void checkItem(Task task, String itemId, boolean done) {
        if (task == null) {
            return;
        }
        send(TaskActionPacket.check(nextRequestId(), task.groupId, task.id, task.rev, itemId, done));
    }

    public synchronized void completeTask(Task task, boolean done) {
        if (task == null) {
            return;
        }
        send(TaskActionPacket.complete(nextRequestId(), task.groupId, task.id, task.rev, done));
    }

    public synchronized void addComment(Task task, String text) {
        if (task == null) {
            return;
        }
        send(TaskActionPacket.comment(nextRequestId(), task.groupId, task.id, task.rev, text));
    }

    public synchronized void deleteTask(Task task) {
        if (task == null) {
            return;
        }
        send(TaskActionPacket.delete(nextRequestId(), task.groupId, task.id));
    }

    private void send(TaskActionPacket packet) {
        PacketHandler.sendToServer(packet);
    }

    private String nextRequestId() {
        requestCounter++;
        return "q" + requestCounter + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public synchronized List<TaskSummary> getSummaries(String groupId) {
        Map<String, TaskSummary> map = summaries.get(groupId);
        if (map == null) {
            return Collections.emptyList();
        }
        List<TaskSummary> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<TaskSummary>() {
            @Override
            public int compare(TaskSummary a, TaskSummary b) {
                if (a.deleted != b.deleted) {
                    return a.deleted ? 1 : -1;
                }
                if (a.completed != b.completed) {
                    return a.completed ? 1 : -1;
                }
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return list;
    }

    public synchronized Task getTask(String groupId, String taskId) {
        Map<String, Task> map = tasks.get(groupId);
        return map == null ? null : map.get(taskId);
    }

    public synchronized TaskSummary getSummary(String groupId, String taskId) {
        Map<String, TaskSummary> map = summaries.get(groupId);
        return map == null ? null : map.get(taskId);
    }

    public synchronized int openCount(String groupId) {
        Map<String, TaskSummary> map = summaries.get(groupId);
        if (map == null) {
            return 0;
        }
        int n = 0;
        for (TaskSummary summary : map.values()) {
            if (!summary.completed && !summary.deleted) {
                n++;
            }
        }
        return n;
    }

    public synchronized String getLastNotice() {
        return lastNotice;
    }

    public synchronized long getLastNoticeAt() {
        return lastNoticeAt;
    }

    public synchronized boolean isLastNoticeError() {
        return lastNoticeError;
    }

    public synchronized void clearNotice() {
        lastNotice = "";
        lastNoticeAt = 0L;
        lastNoticeError = false;
    }

    public synchronized String consumeCreatedTaskId() {
        String id = lastCreatedTaskId;
        lastCreatedTaskId = "";
        return id;
    }

    public synchronized String getLastErrorGroupId() {
        return lastErrorGroupId;
    }
}
