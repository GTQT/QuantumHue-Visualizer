package meowmel.quantumhue.task.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 任务板生命周期：世界加载/保存、玩家登录同步、5 秒防抖落盘。
 */
public class TaskEvents {

    private int tickCounter;

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        TaskService.load(world.getSaveHandler().getWorldDirectory());
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        TaskService.save();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            TaskService.sendLoginSummary((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter % 100 == 0) {
            TaskService.maybeSave();
        }
    }
}
