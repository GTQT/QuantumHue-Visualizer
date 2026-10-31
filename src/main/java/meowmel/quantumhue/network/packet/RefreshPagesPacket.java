package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.config.PageBootstrap;
import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 让客户端重新执行一次启动预取（更新日志 / 公告 / 社群服务）。
 *
 * <p>{@code /quantumhue refresh} 在服务端执行；游戏运行期间客户端不主动联网，所以由这个包
 * 通知客户端在后台重新拉取并刷新本地缓存。
 */
public class RefreshPagesPacket implements IMessage {

    public RefreshPagesPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<RefreshPagesPacket, IMessage> {

        @Override
        public IMessage onMessage(RefreshPagesPacket message, MessageContext ctx) {
            handleClient();
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient() {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                PageConfigs.reloadLocal();
                PageBootstrap.refreshAsync();
            });
        }
    }
}
