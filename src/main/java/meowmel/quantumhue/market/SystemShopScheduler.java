package meowmel.quantumhue.market;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 系统订单投放器。
 *
 * <p>每过一个随机区间就从经 API 注册的 {@code market.buyPool / sellPool} 里抽若干种货物，
 * 生成带过期时间的系统收购单与系统出售单，并广播一条公告。
 *
 * <p>节奏参数不再写成 static final：那样改配置只能重启，现在每次投放都重新读，命令重载后立刻生效。
 */
public class SystemShopScheduler {

    /** 一分钟的 tick 数；不依赖 GregTech 的常量，商店在纯原版环境也能跑。 */
    private static final int TICKS_PER_MINUTE = 1200;
    private static final String SYSTEM_NAME = "§6§l系统";
    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    private final Random random = new Random();
    private int nextIntervalTicks;
    private int currentTick;

    public SystemShopScheduler() {
        this.nextIntervalTicks = randomInterval();
    }

    public void tick(World world) {
        currentTick++;
        if (currentTick >= nextIntervalTicks) {
            currentTick = 0;
            nextIntervalTicks = randomInterval();
            executeRandomTask(world);
        }
    }

    private void executeRandomTask(World world) {
        ShopConfig.Market config = market();
        ShopDatabase database = ShopDatabase.get(world);

        int goodsMin = config.orderGoodsMin;
        int goodsMax = Math.max(goodsMin, config.orderGoodsMax);
        int buyCount = goodsMin + random.nextInt(goodsMax - goodsMin + 1);
        int sellCount = goodsMin + random.nextInt(goodsMax - goodsMin + 1);

        List<BuyOrder> buyOrders = spawnBuyOrders(database, config.buyPool, buyCount, config.systemOrderHours);
        List<TradeItem> sellOrders = spawnSellOrders(database, config.sellPool, sellCount, config.systemOrderHours);

        QuantumHue.LOGGER.info("[Shop] 系统商店投放：收购 {} 类，出售 {} 类", buyOrders.size(), sellOrders.size());
        broadcastSpawnMessage(world, buyOrders, sellOrders);
    }

    private List<BuyOrder> spawnBuyOrders(ShopDatabase database, List<ShopConfig.PoolEntry> pool,
                                          int count, int expireHours) {
        List<BuyOrder> orders = new ArrayList<>();
        for (ShopConfig.PoolEntry entry : selectRandom(pool, count)) {
            ItemStack stack = resolve(entry);
            if (stack.isEmpty()) {
                continue;
            }
            int quantity = quantity(entry);
            stack.setCount(quantity);
            long price = randomPrice(entry.price);
            BuyOrder order = new BuyOrder(stack, price, quantity, true);
            order.setExpireHours(expireHours);
            database.addBuyOrder(order);
            orders.add(order);
        }
        return orders;
    }

    private List<TradeItem> spawnSellOrders(ShopDatabase database, List<ShopConfig.PoolEntry> pool,
                                            int count, int expireHours) {
        List<TradeItem> listings = new ArrayList<>();
        for (ShopConfig.PoolEntry entry : selectRandom(pool, count)) {
            ItemStack stack = resolve(entry);
            if (stack.isEmpty()) {
                continue;
            }
            int quantity = quantity(entry);
            stack.setCount(quantity);
            long price = randomPrice(entry.price);
            TradeItem listing = new TradeItem(stack, price, quantity, SYSTEM_UUID, SYSTEM_NAME);
            listing.setExpireHours(expireHours);
            database.addListing(listing);
            listings.add(listing);
        }
        return listings;
    }

    private void broadcastSpawnMessage(World world, List<BuyOrder> buyOrders, List<TradeItem> sellOrders) {
        if (world.getMinecraftServer() == null) {
            return;
        }
        if (buyOrders.isEmpty() && sellOrders.isEmpty()) {
            return;
        }
        List<ITextComponent> messages = new ArrayList<>();
        if (!buyOrders.isEmpty()) {
            messages.add(new TextComponentString("§6§l=== 系统收购 ==="));
            for (BuyOrder order : buyOrders) {
                messages.add(new TextComponentString(String.format(
                        "§7收购 §b%1$s §7x%2$s §7@ §e%3$s§7货币/个 §8(%4$s§8)",
                        order.itemStack.getTextComponent(), order.quantity, order.pricePerUnit,
                        order.getRemainingTimeComponent())));
            }
        }
        if (!sellOrders.isEmpty()) {
            messages.add(new TextComponentString("§6§l=== 系统出售 ==="));
            for (TradeItem listing : sellOrders) {
                messages.add(new TextComponentString(String.format(
                        "§7出售 §b%1$s §7x%2$s §7@ §e%3$s§7货币/个 §8(%4$s§8)",
                        listing.itemStack.getTextComponent(), listing.quantity, listing.pricePerUnit,
                        listing.getRemainingTimeComponent())));
            }
        }
        for (EntityPlayerMP player : world.getMinecraftServer().getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            for (ITextComponent message : messages) {
                player.sendMessage(message);
            }
        }
    }

    private List<ShopConfig.PoolEntry> selectRandom(List<ShopConfig.PoolEntry> pool, int count) {
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShopConfig.PoolEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private ItemStack resolve(ShopConfig.PoolEntry entry) {
        try {
            ResourceLocation key = new ResourceLocation(entry.item.trim());
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item == null) {
                QuantumHue.LOGGER.warn("[Shop] 订单池里有未知物品: {}:{}", entry.item, entry.meta);
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, 1, Math.max(0, entry.meta));
        } catch (Throwable invalid) {
            return ItemStack.EMPTY;
        }
    }

    private int quantity(ShopConfig.PoolEntry entry) {
        int min = Math.max(1, entry.minCount);
        int max = Math.max(min, entry.maxCount);
        return min + random.nextInt(max - min + 1);
    }

    private long randomPrice(long basePrice) {
        double multiplier = 0.8D + random.nextDouble() * 0.4D;
        return Math.max(1L, Math.round(basePrice * multiplier));
    }

    private int randomInterval() {
        ShopConfig.Market config = market();
        int minMinutes = Math.max(1, config.orderIntervalMinMinutes);
        int maxMinutes = Math.max(minMinutes, config.orderIntervalMaxMinutes);
        int minTicks = minMinutes * TICKS_PER_MINUTE;
        int maxTicks = maxMinutes * TICKS_PER_MINUTE;
        return minTicks + random.nextInt(maxTicks - minTicks + 1);
    }

    private static ShopConfig.Market market() {
        try {
            return PageConfigs.shop().market;
        } catch (Throwable ignored) {
            return new ShopConfig.Market();
        }
    }
}
