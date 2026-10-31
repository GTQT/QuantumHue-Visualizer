package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.page.GuiAnnouncement;
import meowmel.quantumhue.page.GuiShop;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 让客户端打开商店或公告页。
 *
 * <p>命令与登录弹窗都在服务端线程执行，而 {@code displayGuiScreen} 必须在客户端线程调用，
 * 所以由这个包把“要打开哪个页面”送到客户端，再在主线程里创建界面。
 */
public class OpenPagePacket implements IMessage {

    public enum Page {
        SHOP,
        NEWS
    }

    private Page page = Page.NEWS;

    public OpenPagePacket() {}

    private OpenPagePacket(Page page) {
        this.page = page;
    }

    public static OpenPagePacket shop() {
        return new OpenPagePacket(Page.SHOP);
    }

    public static OpenPagePacket news() {
        return new OpenPagePacket(Page.NEWS);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Page[] pages = Page.values();
        page = ordinal >= 0 && ordinal < pages.length ? pages[ordinal] : Page.NEWS;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(page.ordinal());
    }

    public static class Handler implements IMessageHandler<OpenPagePacket, IMessage> {

        @Override
        public IMessage onMessage(OpenPagePacket message, MessageContext ctx) {
            handleClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(OpenPagePacket message) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                if (message.page == Page.SHOP) {
                    if (mc.player != null) {
                        mc.displayGuiScreen(new GuiShop(mc.player));
                    }
                } else {
                    mc.displayGuiScreen(new GuiAnnouncement());
                }
            });
        }
    }
}
