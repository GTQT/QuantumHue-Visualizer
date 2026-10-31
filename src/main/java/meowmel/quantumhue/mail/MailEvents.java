package meowmel.quantumhue.mail;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 邮件系统的服务端事件：世界加载时套用启动预取结果，玩家登出时移除推送订阅。
 *
 * <p>进入游戏后不再周期联网；需要更新邮件时执行 {@code /quantumhue refresh}。
 */
public class MailEvents {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        MailService.onWorldLoad(world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        MailService.onWorldUnload(world);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MailService.forget(event.player.getUniqueID());
    }
}
