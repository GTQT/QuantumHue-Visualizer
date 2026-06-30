package meowmel.quantumhue.network;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.igi.ThaumcraftDataCache;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 神秘时代灵气数据同步消息。
 * <ul>
 *   <li>客户端 → 服务端：isRequest=true，请求当前灵气/咒波值</li>
 *   <li>服务端 → 客户端：isRequest=false，返回灵气/咒波值</li>
 * </ul>
 */
public class ThaumcraftAuraMessage implements IMessage {

    private boolean isRequest;
    private float vis;
    private float flux;

    public ThaumcraftAuraMessage() {
        this.isRequest = true;
    }

    public ThaumcraftAuraMessage(float vis, float flux) {
        this.isRequest = false;
        this.vis = vis;
        this.flux = flux;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isRequest = buf.readBoolean();
        if (!isRequest) {
            vis = buf.readFloat();
            flux = buf.readFloat();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(isRequest);
        if (!isRequest) {
            buf.writeFloat(vis);
            buf.writeFloat(flux);
        }
    }

    /**
     * 服务端处理器：读取灵气/咒波数据并返回给客户端
     */
    public static class ServerHandler implements IMessageHandler<ThaumcraftAuraMessage, IMessage> {

        @Override
        public IMessage onMessage(ThaumcraftAuraMessage message, MessageContext ctx) {
            if (!message.isRequest) return null;

            final EntityPlayerMP player = ctx.getServerHandler().player;
            IThreadListener mainThread = (WorldServer) player.world;
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        float vis = thaumcraft.api.aura.AuraHelper.getVis(
                                player.world, player.getPosition());
                        float flux = thaumcraft.api.aura.AuraHelper.getFlux(
                                player.world, player.getPosition());
                        PacketHandler.sendTo(new ThaumcraftAuraMessage(vis, flux), player);
                    } catch (Exception e) {
                        // Thaumcraft not loaded or error accessing aura
                    }
                }
            });
            return null;
        }
    }

    /**
     * 客户端处理器：缓存灵气/咒波数据
     */
    public static class ClientHandler implements IMessageHandler<ThaumcraftAuraMessage, IMessage> {

        @Override
        public IMessage onMessage(ThaumcraftAuraMessage message, MessageContext ctx) {
            if (message.isRequest) return null;
            ThaumcraftDataCache.updateAura(message.vis, message.flux);
            return null;
        }
    }
}
