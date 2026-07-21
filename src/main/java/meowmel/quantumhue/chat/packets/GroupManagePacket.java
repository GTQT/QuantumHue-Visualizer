package meowmel.quantumhue.chat.packets;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.chat.ChatMessageStore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 群管理包 — 创建 / 邀请 / 加入 / 离开 / 解散
 *
 * Action code:
 *   0 = CREATE   C→S
 *   1 = INVITE   C→S
 *   2 = JOIN     C→S (接受邀请)
 *   3 = LEAVE    C→S
 *   4 = SYNC     S→C (服务端同步群信息给客户端)
 */
public class GroupManagePacket implements IMessage {

    public static final int CREATE = 0;
    public static final int INVITE = 1;
    public static final int JOIN = 2;
    public static final int LEAVE = 3;
    public static final int SYNC = 4;

    private int action;
    private String groupId;
    private String groupName;
    private String targetName;       // 邀请/加入的目标玩家名
    private List<String> memberNames;
    private UUID creatorUUID;

    public GroupManagePacket() {}

    /** 通用构造 */
    public GroupManagePacket(int action, String groupId, String groupName,
                              String targetName, List<String> memberNames, UUID creatorUUID) {
        this.action = action;
        this.groupId = groupId;
        this.groupName = groupName;
        this.targetName = targetName;
        this.memberNames = memberNames;
        this.creatorUUID = creatorUUID;
    }

    /** CREATE 快捷构造 */
    public static GroupManagePacket create(String groupName, UUID creatorUUID) {
        return new GroupManagePacket(CREATE, null, groupName, null, null, creatorUUID);
    }

    /** INVITE 快捷构造 */
    public static GroupManagePacket invite(String groupId, String targetName) {
        return new GroupManagePacket(INVITE, groupId, null, targetName, null, null);
    }

    /** JOIN 快捷构造 */
    public static GroupManagePacket join(String groupId, String playerName) {
        return new GroupManagePacket(JOIN, groupId, null, playerName, null, null);
    }

    /** SYNC 快捷构造（服务端→客户端同步群信息） */
    public static GroupManagePacket createSync(String groupId, String groupName, List<String> memberNames) {
        return new GroupManagePacket(SYNC, groupId, groupName, null, memberNames, null);
    }

    public int getAction() { return action; }
    public String getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public String getTargetName() { return targetName; }
    public List<String> getMemberNames() { return memberNames; }
    public UUID getCreatorUUID() { return creatorUUID; }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        groupId = ByteBufUtils.readUTF8String(buf);
        groupName = ByteBufUtils.readUTF8String(buf);
        targetName = ByteBufUtils.readUTF8String(buf);
        if (creatorUUID != null || action == CREATE) {
            String uuidStr = ByteBufUtils.readUTF8String(buf);
            creatorUUID = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);
        }
        int count = buf.readInt();
        memberNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            memberNames.add(ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, groupId != null ? groupId : "");
        ByteBufUtils.writeUTF8String(buf, groupName != null ? groupName : "");
        ByteBufUtils.writeUTF8String(buf, targetName != null ? targetName : "");
        ByteBufUtils.writeUTF8String(buf, creatorUUID != null ? creatorUUID.toString() : "");
        buf.writeInt(memberNames != null ? memberNames.size() : 0);
        if (memberNames != null) {
            for (String n : memberNames)
                ByteBufUtils.writeUTF8String(buf, n);
        }
    }

    /** 服务端：群管理操作 */
    public static class ServerHandler implements IMessageHandler<GroupManagePacket, IMessage> {
        @Override
        public IMessage onMessage(GroupManagePacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                meowmel.quantumhue.chat.server.ChatServerListener.handleGroupManage(
                        ctx.getServerHandler().player, pkt);
            });
            return null;
        }
    }

    /** 客户端：收到群同步 */
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<GroupManagePacket, IMessage> {
        @Override
        public IMessage onMessage(GroupManagePacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                if (pkt.action == SYNC) {
                    // 服务端同步群成员列表
                    ChatMessageStore.findOrCreateGroupChannel(
                            pkt.groupId, pkt.groupName,
                            pkt.memberNames, new ArrayList<>());
                }
            });
            return null;
        }
    }
}
