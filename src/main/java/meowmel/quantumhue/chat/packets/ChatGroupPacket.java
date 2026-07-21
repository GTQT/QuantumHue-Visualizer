package meowmel.quantumhue.chat.packets;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.chat.ChatMessageStore;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
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
 * 群聊消息包 — C→S→群内所有成员
 */
public class ChatGroupPacket implements IMessage {

    private String groupId;
    private String message;
    private String senderName;
    private UUID senderUUID;
    private List<String> memberNames;
    private String groupName;         // 仅群创建/同步时用
    private int action;               // 0=消息, 1=同步成员列表

    public ChatGroupPacket() {}

    /** 群消息 */
    public ChatGroupPacket(String groupId, String message, String senderName, UUID senderUUID) {
        this.groupId = groupId;
        this.message = message;
        this.senderName = senderName;
        this.senderUUID = senderUUID;
        this.action = 0;
    }

    /** 同步群信息（成员列表等） */
    public ChatGroupPacket(String groupId, String groupName, List<String> memberNames, int action) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.memberNames = memberNames;
        this.action = action;
    }

    public String getGroupId() { return groupId; }
    public String getMessage() { return message; }
    public String getSenderName() { return senderName; }
    public UUID getSenderUUID() { return senderUUID; }
    public List<String> getMemberNames() { return memberNames; }
    public String getGroupName() { return groupName; }
    public int getAction() { return action; }

    @Override
    public void fromBytes(ByteBuf buf) {
        groupId = ByteBufUtils.readUTF8String(buf);
        action = buf.readInt();
        if (action == 0) {
            message = ByteBufUtils.readUTF8String(buf);
            senderName = ByteBufUtils.readUTF8String(buf);
            senderUUID = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        } else {
            groupName = ByteBufUtils.readUTF8String(buf);
            int count = buf.readInt();
            memberNames = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                memberNames.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, groupId);
        buf.writeInt(action);
        if (action == 0) {
            ByteBufUtils.writeUTF8String(buf, message);
            ByteBufUtils.writeUTF8String(buf, senderName);
            ByteBufUtils.writeUTF8String(buf, senderUUID.toString());
        } else {
            ByteBufUtils.writeUTF8String(buf, groupName != null ? groupName : "");
            buf.writeInt(memberNames != null ? memberNames.size() : 0);
            if (memberNames != null) {
                for (String n : memberNames)
                    ByteBufUtils.writeUTF8String(buf, n);
            }
        }
    }

    /** 服务端：广播给群内所有成员 */
    public static class ServerHandler implements IMessageHandler<ChatGroupPacket, IMessage> {
        @Override
        public IMessage onMessage(ChatGroupPacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                // 群消息转发：由 ChatServerListener 处理
                meowmel.quantumhue.chat.server.ChatServerListener.handleGroupMessage(
                        ctx.getServerHandler().player, pkt);
            });
            return null;
        }
    }

    /** 客户端：收到群消息 → 添加到对应频道 */
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<ChatGroupPacket, IMessage> {
        @Override
        public IMessage onMessage(ChatGroupPacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                if (pkt.action == 1) {
                    // 同步群信息
                    ChatMessageStore.findOrCreateGroupChannel(
                            pkt.groupId, pkt.groupName, pkt.memberNames,
                            new ArrayList<>());  // UUID 列表后续补
                } else {
                    ITextComponent content = new TextComponentString(pkt.message);
                    ITextComponent sender = new TextComponentString(pkt.senderName);
                    String chId = "group:" + pkt.groupId;
                    ChatMessageStore.findOrCreateGroupChannel(pkt.groupId, pkt.groupName,
                            null, null);
                    ChatMessageStore.addMessage(content, pkt.senderUUID, sender, false,
                            pkt.senderName, chId);
                }
            });
            return null;
        }
    }
}
