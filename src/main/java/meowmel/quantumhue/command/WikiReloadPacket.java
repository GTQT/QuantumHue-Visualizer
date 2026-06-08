package meowmel.quantumhue.command;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.wiki.WikiContent;
import meowmel.quantumhue.wiki.WikiJsonLoader;
import meowmel.quantumhue.wiki.WikiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class WikiReloadPacket implements IMessage {

    public WikiReloadPacket() {}

    @Override public void fromBytes(ByteBuf buf) {}
    @Override public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<WikiReloadPacket, IMessage> {
        @Override
        public IMessage onMessage(WikiReloadPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                // 从 jar 资源覆盖复制到 config/wiki
                WikiJsonLoader.reloadFromAssets();
                // 清除缓存，下次打开时重新加载
                WikiContent.reload();
                // 如果当前正在打开 wiki 界面，刷新它
                if (Minecraft.getMinecraft().currentScreen instanceof WikiScreen) {
                    Minecraft.getMinecraft().displayGuiScreen(new WikiScreen());
                }
            });
            return null;
        }
    }
}
