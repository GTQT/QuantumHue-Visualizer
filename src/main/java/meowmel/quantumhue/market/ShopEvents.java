package meowmel.quantumhue.market;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.OpenPagePacket;
import meowmel.quantumhue.notice.VersionState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端的商店 / 公告事件：世界加载时建立订单投放器、周期性清理过期数据、玩家登录时结算与推送公告。
 *
 * <p>所有世界数据都从 {@link ShopDatabase} / {@link OfflineEarningsDatabase} 取，调度器只在主世界
 * （dim 0）建立——多维度各跑一份会让订单成倍投放。
 */
public class ShopEvents {

    private final Map<Integer, SystemShopScheduler> schedulers = new HashMap<>();
    private final Map<Integer, Integer> worldTickCounter = new HashMap<>();

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        int dim = world.provider.getDimension();
        worldTickCounter.put(dim, 0);
        schedulers.put(dim, new SystemShopScheduler());
        QuantumHue.LOGGER.info("[Shop] 商店事件处理器已加载");
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        int dim = world.provider.getDimension();
        worldTickCounter.remove(dim);
        schedulers.remove(dim);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.END) {
            return;
        }
        int dim = event.world.provider.getDimension();
        if (dim != 0) {
            return;
        }
        int tickCount = worldTickCounter.getOrDefault(dim, 0) + 1;
        worldTickCounter.put(dim, tickCount);

        SystemShopScheduler scheduler = schedulers.get(dim);
        if (scheduler != null) {
            scheduler.tick(event.world);
        }

        // 每十分钟清理一次，过期条目不会在存档里无界增长
        if (tickCount % 12000 == 0) {
            ShopDatabase.get(event.world).cleanupAllExpired();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        World world = player.world;
        if (world.isRemote) {
            return;
        }
        settleOfflineEarnings(player, world);
        checkExpiredListings(player, world);
        sendVersionMessage(player);
        sendAnnouncement(player);
    }

    // ================================================================ 登录结算

    private void settleOfflineEarnings(EntityPlayerMP player, World world) {
        OfflineEarningsDatabase database = OfflineEarningsDatabase.get(world);
        List<OfflineEarning> earnings = database.getAndClearEarnings(player.getUniqueID());
        if (earnings.isEmpty()) {
            return;
        }
        long total = 0L;
        for (OfflineEarning earning : earnings) {
            total += earning.amount;
            player.sendMessage(new TextComponentString(String.format(
                    "§a✓ 离线收益：+%1$s 货币 (§7%2$s§a)", earning.amount, earning.sourceComponent())));
        }
        long money = EconomyBridge.get(player);
        EconomyBridge.set(player, money + total);
        player.sendMessage(new TextComponentString(String.format(
                "§6§l=== 离线收益结算 === §r§7共计：§e+%1$s 货币", total)));
    }

    private void checkExpiredListings(EntityPlayerMP player, World world) {
        ShopDatabase database = ShopDatabase.get(world);
        List<TradeItem> listings = database.getListingsBySeller(player.getUniqueID());
        long expired = 0L;
        for (TradeItem listing : listings) {
            if (listing.isExpired()) {
                expired++;
            }
        }
        if (expired > 0L) {
            player.sendMessage(new TextComponentString("§6§l=== 商品过期提醒 ==="));
            player.sendMessage(new TextComponentString(String.format(
                    "§e你有 §c%1$s§e 个商品已过期，请到商店取回！", expired)));
            player.sendMessage(new TextComponentString("§7使用 §b/market§7 命令打开商店"));
        }
    }

    // ================================================================ 公告与版本

    private void sendAnnouncement(EntityPlayerMP player) {
        if (!QuantumHueConfig.notice.showOnLogin) {
            return;
        }
        PacketHandler.sendTo(OpenPagePacket.news(), player);
    }

    /**
     * 版本提示要等异步检查落定后再发，否则玩家只会看到“检查失败”。
     */
    private void sendVersionMessage(EntityPlayerMP player) {
        if (!QuantumHueConfig.notice.checkVersionOnLogin) {
            return;
        }
        // 没配整合包版本就不存在“新旧”，发一句“已是最新版”反而误导
        if (!VersionState.hasCurrentVersion()) {
            QuantumHue.LOGGER.debug("[Notice] 取不到整合包版本（GTQTCore 与 API 注册配置都没有），跳过登录版本提示");
            return;
        }
        VersionState.whenSettled(() -> {
            if (player.connection == null) {
                return;
            }
            player.getServerWorld().addScheduledTask(() -> {
                if (player.connection == null) {
                    return;
                }
                player.sendMessage(versionComponent());
            });
        });
    }

    private ITextComponent versionComponent() {
        String current = VersionState.currentVersion();
        if (!VersionState.isChecked()) {
            return new TextComponentString(
                    "§e[更新检查]§r 版本检查失败。§7使用 §b/quantumhue refresh§7 重新获取");
        }
        String remote = VersionState.remoteVersion();
        if (!VersionState.hasNewer()) {
            return new TextComponentString(String.format(
                    "§a[更新检查]§r 当前已是最新版 §a%1$s", current));
        }
        return new TextComponentString(String.format(
                "§e[更新检查]§r 发现新版本！当前: §c%1$s§r, 最新: §a%2$s§r。", current, remote));
    }
}
