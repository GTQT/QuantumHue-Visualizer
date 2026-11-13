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

/**
 * 服务端提示包 — 目前用于私聊投递结果（对方离线 / 已留言）。
 *
 * <p>只往当前频道塞一条系统消息，不额外弹窗，避免打断玩家操作。</p>
 */
public class ChatNoticePacket implements IMessage {

    private String text;

    public ChatNoticePacket() {}

    public ChatNoticePacket(String text) {
        this.text = text;
    }

    public String getText() { return text; }

    @Override
    public void fromBytes(ByteBuf buf) {
        text = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, text == null ? "" : text);
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<ChatNoticePacket, IMessage> {
        @Override
        public IMessage onMessage(ChatNoticePacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player == null) {
                    return;
                }
                ITextComponent content = new TextComponentString(pkt.text == null ? "" : pkt.text);
                ChatMessageStore.addMessage(content, mc.player.getUniqueID(),
                        new TextComponentString("系统"), true, "系统", "world");
            });
            return null;
        }
    }
}
