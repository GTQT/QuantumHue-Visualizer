package meowmel.quantumhue.igi;

import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.ThaumcraftAuraMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端定时向服务端请求神秘时代灵气数据。
 * 每秒发送一次请求。
 */
@SideOnly(Side.CLIENT)
public class ThaumcraftAuraUpdater {

    private static final long REQUEST_INTERVAL_MS = 1000;
    private long lastRequestTime = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Loader.isModLoaded("thaumcraft")) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastRequestTime >= REQUEST_INTERVAL_MS) {
            lastRequestTime = now;
            PacketHandler.sendToServer(new ThaumcraftAuraMessage());
        }
    }
}
