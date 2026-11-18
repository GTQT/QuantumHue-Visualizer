package meowmel.quantumhue.chat.packets;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.task.client.TaskStore;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskSummary;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端的任务同步。SUMMARY 只带列表快照，SNAPSHOT/PATCH/ACK 带完整任务。
 */
public class TaskSyncPacket implements IMessage {

    public static final int SUMMARY = 0;
    public static final int SNAPSHOT = 1;
    public static final int PATCH = 2;
    public static final int ACK = 3;
    public static final int ERROR = 4;

    public static final int ERROR_NOT_MEMBER = 1;
    public static final int ERROR_NOT_FOUND = 2;
    public static final int ERROR_STALE = 3;
    public static final int ERROR_INVALID = 4;
    public static final int ERROR_LIMIT = 5;

    private static final Gson GSON = new Gson();

    public int kind;
    public String groupId = "";
    public String requestId = "";
    public int code;
    public String message = "";
    public long serverTime;
    public List<Task> tasks = new ArrayList<>();
    public List<TaskSummary> summaries = new ArrayList<>();

    public TaskSyncPacket() {
    }

    public static TaskSyncPacket summary(String groupId, List<TaskSummary> summaries) {
        TaskSyncPacket p = new TaskSyncPacket();
        p.kind = SUMMARY;
        p.groupId = groupId;
        p.summaries = summaries == null ? new ArrayList<>() : new ArrayList<>(summaries);
        p.serverTime = System.currentTimeMillis();
        return p;
    }

    public static TaskSyncPacket snapshot(String groupId, List<Task> tasks) {
        TaskSyncPacket p = new TaskSyncPacket();
        p.kind = SNAPSHOT;
        p.groupId = groupId;
        p.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        p.serverTime = System.currentTimeMillis();
        return p;
    }

    public static TaskSyncPacket patch(Task task) {
        TaskSyncPacket p = new TaskSyncPacket();
        p.kind = PATCH;
        p.groupId = task == null ? "" : task.groupId;
        p.tasks = new ArrayList<>();
        if (task != null) {
            p.tasks.add(task);
        }
        p.serverTime = System.currentTimeMillis();
        return p;
    }

    public static TaskSyncPacket ack(String requestId, Task task) {
        TaskSyncPacket p = new TaskSyncPacket();
        p.kind = ACK;
        p.requestId = requestId;
        p.groupId = task == null ? "" : task.groupId;
        p.tasks = new ArrayList<>();
        if (task != null) {
            p.tasks.add(task);
        }
        p.serverTime = System.currentTimeMillis();
        return p;
    }

    public static TaskSyncPacket error(String requestId, int code, String message) {
        TaskSyncPacket p = new TaskSyncPacket();
        p.kind = ERROR;
        p.requestId = requestId;
        p.code = code;
        p.message = message;
        p.serverTime = System.currentTimeMillis();
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readInt();
        groupId = ByteBufUtils.readUTF8String(buf);
        requestId = ByteBufUtils.readUTF8String(buf);
        code = buf.readInt();
        message = ByteBufUtils.readUTF8String(buf);
        serverTime = buf.readLong();
        int taskCount = buf.readInt();
        tasks = new ArrayList<>(Math.max(0, Math.min(taskCount, 256)));
        for (int i = 0; i < taskCount; i++) {
            String json = readJson(buf);
            try {
                Task task = GSON.fromJson(json, Task.class);
                if (task != null) {
                    tasks.add(task);
                }
            } catch (Exception ignored) {
            }
        }
        int summaryCount = buf.readInt();
        summaries = new ArrayList<>(Math.max(0, Math.min(summaryCount, 256)));
        for (int i = 0; i < summaryCount; i++) {
            String json = readJson(buf);
            try {
                TaskSummary summary = GSON.fromJson(json, TaskSummary.class);
                if (summary != null) {
                    summaries.add(summary);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(kind);
        ByteBufUtils.writeUTF8String(buf, safe(groupId));
        ByteBufUtils.writeUTF8String(buf, safe(requestId));
        buf.writeInt(code);
        ByteBufUtils.writeUTF8String(buf, safe(message));
        buf.writeLong(serverTime);
        buf.writeInt(tasks == null ? 0 : tasks.size());
        if (tasks != null) {
            for (Task task : tasks) {
                writeJson(buf, GSON.toJson(task));
            }
        }
        buf.writeInt(summaries == null ? 0 : summaries.size());
        if (summaries != null) {
            for (TaskSummary summary : summaries) {
                writeJson(buf, GSON.toJson(summary));
            }
        }
    }

    private static final int MAX_JSON_BYTES = 1 << 20;

    private static void writeJson(ByteBuf buf, String json) {
        byte[] data = safe(json).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static String readJson(ByteBuf buf) {
        int length = buf.readInt();
        if (length < 0 || length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Invalid task payload length: " + length);
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class ClientHandler implements IMessageHandler<TaskSyncPacket, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(TaskSyncPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> TaskStore.get().handle(message));
            return null;
        }
    }
}
