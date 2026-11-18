package meowmel.quantumhue.stock;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 把行情引擎接到服务端生命周期上。
 *
 * <p>只在主世界推进：其它维度的 tick 不参与，存档也统一落在主世界，
 * 这样玩家跨维度时持仓与行情不会出现两份。
 */
public final class StockEvents {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld() != null && !event.getWorld().isRemote
                && event.getWorld().provider.getDimension() == 0) {
            StockDatabase.get(event.getWorld());
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote
                || event.world.provider.getDimension() != 0) {
            return;
        }
        if (event.world.getMinecraftServer() != null) {
            StockService.tick(event.world.getMinecraftServer());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            StockService.onLogin((EntityPlayerMP) event.player);
        }
    }
}
