package meowmel.quantumhue.chat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端事件监听 — 世界切换时触发聊天历史保存/加载，并驱动 HUD 预览。
 */
@SideOnly(Side.CLIENT)
public class ChatClientListener {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        String key;
        if (mc.world == null || mc.player == null) {
            key = null;
        } else if (mc.getIntegratedServer() != null) {
            key = "SP:" + mc.getIntegratedServer().getFolderName();
        } else if (mc.getCurrentServerData() != null) {
            key = "MP:" + mc.getCurrentServerData().serverIP;
        } else {
            key = "world";
        }
        ChatMessageStore.setCurrentWorld(key);
        ChatHud.tick(mc);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        ChatHud.render(Minecraft.getMinecraft(), event.getResolution());
    }
}
