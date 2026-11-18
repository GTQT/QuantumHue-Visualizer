package meowmel.quantumhue.chat.packets;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.task.server.TaskService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 -> 服务端的任务操作。勾选传目标状态，重复包天然幂等；rev 用于挡旧编辑。
 */
public class TaskActionPacket implements IMessage {

    public static final int CREATE = 0;
    public static final int UPDATE = 1;
    public static final int CHECK = 2;
    public static final int COMMENT = 3;
    public static final int DELETE = 4;
    public static final int SYNC_REQ = 5;
    public static final int COMPLETE = 6;

    public int action;
    public String requestId = "";
    public String groupId = "";
    public String taskId = "";
    public int rev;
    public String title = "";
    public String desc = "";
    public List<String> itemIds = new ArrayList<>();
    public List<String> itemTexts = new ArrayList<>();
    public String itemId = "";
    public boolean done;
    public String comment = "";

    public TaskActionPacket() {
    }

    public static TaskActionPacket create(String requestId, String groupId, String title, String desc,
                                          List<String> items) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = CREATE;
        p.requestId = requestId;
        p.groupId = groupId;
        p.title = title;
        p.desc = desc;
        p.itemTexts = items == null ? new ArrayList<>() : new ArrayList<>(items);
        return p;
    }

    public static TaskActionPacket update(String requestId, String groupId, String taskId, int rev,
                                          String title, String desc, List<String> itemIds,
                                          List<String> itemTexts) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = UPDATE;
        p.requestId = requestId;
        p.groupId = groupId;
        p.taskId = taskId;
        p.rev = rev;
        p.title = title;
        p.desc = desc;
        p.itemIds = itemIds == null ? new ArrayList<>() : new ArrayList<>(itemIds);
        p.itemTexts = itemTexts == null ? new ArrayList<>() : new ArrayList<>(itemTexts);
        return p;
    }

    public static TaskActionPacket check(String requestId, String groupId, String taskId, int rev,
                                         String itemId, boolean done) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = CHECK;
        p.requestId = requestId;
        p.groupId = groupId;
        p.taskId = taskId;
        p.rev = rev;
        p.itemId = itemId;
        p.done = done;
        return p;
    }

    public static TaskActionPacket complete(String requestId, String groupId, String taskId, int rev,
                                            boolean done) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = COMPLETE;
        p.requestId = requestId;
        p.groupId = groupId;
        p.taskId = taskId;
        p.rev = rev;
        p.done = done;
        return p;
    }

    public static TaskActionPacket comment(String requestId, String groupId, String taskId, int rev,
                                           String text) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = COMMENT;
        p.requestId = requestId;
        p.groupId = groupId;
        p.taskId = taskId;
        p.rev = rev;
        p.comment = text;
        return p;
    }

    public static TaskActionPacket delete(String requestId, String groupId, String taskId) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = DELETE;
        p.requestId = requestId;
        p.groupId = groupId;
        p.taskId = taskId;
        return p;
    }

    public static TaskActionPacket syncRequest(String requestId, String groupId) {
        TaskActionPacket p = new TaskActionPacket();
        p.action = SYNC_REQ;
        p.requestId = requestId;
        p.groupId = groupId;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        requestId = ByteBufUtils.readUTF8String(buf);
        groupId = ByteBufUtils.readUTF8String(buf);
        taskId = ByteBufUtils.readUTF8String(buf);
        rev = buf.readInt();
        title = ByteBufUtils.readUTF8String(buf);
        desc = ByteBufUtils.readUTF8String(buf);
        int idCount = buf.readInt();
        itemIds = new ArrayList<>(Math.max(0, Math.min(idCount, 64)));
        for (int i = 0; i < idCount; i++) {
            itemIds.add(ByteBufUtils.readUTF8String(buf));
        }
        int textCount = buf.readInt();
        itemTexts = new ArrayList<>(Math.max(0, Math.min(textCount, 64)));
        for (int i = 0; i < textCount; i++) {
            itemTexts.add(ByteBufUtils.readUTF8String(buf));
        }
        itemId = ByteBufUtils.readUTF8String(buf);
        done = buf.readBoolean();
        comment = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, safe(requestId));
        ByteBufUtils.writeUTF8String(buf, safe(groupId));
        ByteBufUtils.writeUTF8String(buf, safe(taskId));
        buf.writeInt(rev);
        ByteBufUtils.writeUTF8String(buf, safe(title));
        ByteBufUtils.writeUTF8String(buf, safe(desc));
        buf.writeInt(itemIds == null ? 0 : itemIds.size());
        if (itemIds != null) {
            for (String id : itemIds) {
                ByteBufUtils.writeUTF8String(buf, safe(id));
            }
        }
        buf.writeInt(itemTexts == null ? 0 : itemTexts.size());
        if (itemTexts != null) {
            for (String text : itemTexts) {
                ByteBufUtils.writeUTF8String(buf, safe(text));
            }
        }
        ByteBufUtils.writeUTF8String(buf, safe(itemId));
        buf.writeBoolean(done);
        ByteBufUtils.writeUTF8String(buf, safe(comment));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class ServerHandler implements IMessageHandler<TaskActionPacket, IMessage> {
        @Override
        public IMessage onMessage(final TaskActionPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    TaskService.handle(player, message);
                }
            });
            return null;
        }
    }
}
