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

import java.util.UUID;

/**
 * 私聊消息包 — C→S→Target C
 */
public class ChatPrivatePacket implements IMessage {

    private String targetName;
    private String message;
    private String senderName;
    private UUID senderUUID;

    public ChatPrivatePacket() {}

    public ChatPrivatePacket(String targetName, String message, String senderName, UUID senderUUID) {
        this.targetName = targetName;
        this.message = message;
        this.senderName = senderName;
        this.senderUUID = senderUUID;
    }

    public String getTargetName() { return targetName; }
    public String getMessage() { return message; }
    public String getSenderName() { return senderName; }
    public UUID getSenderUUID() { return senderUUID; }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetName = ByteBufUtils.readUTF8String(buf);
        message = ByteBufUtils.readUTF8String(buf);
        senderName = ByteBufUtils.readUTF8String(buf);
        senderUUID = UUID.fromString(ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, targetName);
        ByteBufUtils.writeUTF8String(buf, message);
        ByteBufUtils.writeUTF8String(buf, senderName);
        ByteBufUtils.writeUTF8String(buf, senderUUID.toString());
    }

    /** 服务端 Handler：转发私聊给目标 */
    public static class ServerHandler implements IMessageHandler<ChatPrivatePacket, IMessage> {
        @Override
        public IMessage onMessage(ChatPrivatePacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayerMP target =
                        FMLCommonHandler.instance().getMinecraftServerInstance()
                                .getPlayerList().getPlayerByUsername(pkt.targetName);
                if (target != null) {
                    // 转发给目标
                    ChatPrivatePacket forward = new ChatPrivatePacket(
                            pkt.senderName,  // target 收到时，sender 是原发送者
                            pkt.message,
                            pkt.senderName,
                            pkt.senderUUID);
                    // 用 senderName 作为 targetName 发给发送者自己（回显）
                    meowmel.quantumhue.network.PacketHandler.sendTo(forward,
                            (net.minecraft.entity.player.EntityPlayerMP) target);
                }
            });
            return null;
        }
    }

    /** 客户端 Handler：收到私聊消息 → 添加到对应频道 */
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<ChatPrivatePacket, IMessage> {
        @Override
        public IMessage onMessage(ChatPrivatePacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                ITextComponent content = new TextComponentString(pkt.message);
                ITextComponent sender = new TextComponentString(pkt.senderName);

                // 找到或创建私聊频道
                ChatMessageStore.findOrCreatePrivateChannel(pkt.senderName, pkt.senderUUID);
                ChatMessageStore.addMessage(content, pkt.senderUUID, sender, false,
                        pkt.senderName, "priv:" + pkt.senderName);
            });
            return null;
        }
    }
}
