package meowmel.quantumhue.lottery;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDelivery;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽奖的服务端逻辑。
 *
 * <p>一次抽奖先按权重逐格生成最终符号，再按行结算：每行从最左侧开始数连续相同的符号，
 * 连续 3/4/5 个分别取该符号对应的赔率（某档未配置时回退到更短一档）。所有行的货币奖励合并后
 * 与“基础价 × 本次抽取行数”一次性结算，物品奖励随后按可堆叠数量交付。
 *
 * <p>这里是老虎机的唯一权威：客户端只负责播动画，绝不在本地决定结果。
 */
public final class LotteryService {

    private static final long SPIN_COOLDOWN_MS = 350L;
    private static final Map<UUID, Long> LAST_SPIN = new ConcurrentHashMap<>();

    private LotteryService() {}

    /** 打开/刷新抽奖页：只读奖池与余额。 */
    public static LotterySnapshot request(EntityPlayerMP player) {
        return new LotterySnapshot(LotteryCatalog.build(), EconomyBridge.get(player),
                null, "", "");
    }

    /**
     * 抽一次，{@code rows} 为本次抽取的行数（1..3）；使用默认奖池。
     *
     * <p>失败时返回带 {@code error} 的状态，不会扣钱。
     */
    public static LotterySnapshot spin(EntityPlayerMP player, int rows) {
        return spin(player, "", rows);
    }

    /**
     * 抽一次指定奖池，{@code rows} 为本次抽取的行数（1..3）；空 {@code poolId} 表示默认奖池。
     * 失败时返回带 {@code error} 的状态，不会扣钱。
     */
    public static LotterySnapshot spin(EntityPlayerMP player, String poolId, int rows) {
        LotteryCatalog catalog = LotteryCatalog.build();
        int safeRows = Math.max(1, Math.min(LotteryConfig.MAX_ROWS, rows));
        long money = EconomyBridge.get(player);

        if (!catalog.enabled()) {
            return new LotterySnapshot(catalog, money, null, "", "抽奖暂未开放");
        }
        if (!EconomyBridge.available()) {
            return new LotterySnapshot(catalog, money, null, "", "未检测到货币系统");
        }
        LotteryCatalog.Pool pool = catalog.pool(poolId);
        if (pool == null) {
            return new LotterySnapshot(catalog, money, null, "", "奖池不存在");
        }
        if (!pool.enabled()) {
            return new LotterySnapshot(catalog, money, null, "", "该奖池暂未开放");
        }
        if (pool.symbols().isEmpty()) {
            return new LotterySnapshot(catalog, money, null, "", "该奖池尚未配置");
        }

        UUID playerId = player.getUniqueID();
        long now = System.currentTimeMillis();
        Long last = LAST_SPIN.get(playerId);
        if (last != null && now - last < SPIN_COOLDOWN_MS) {
            return new LotterySnapshot(catalog, money, null, "", "操作太快，请稍后再试");
        }
        LAST_SPIN.put(playerId, now);

        long cost = pool.spinCost(safeRows);
        if (money < cost) {
            return new LotterySnapshot(catalog, money, null, "",
                    String.format("货币不足，本次需要 %s", format(cost)));
        }

        Draw draw = draw(pool, safeRows);
        long balance;
        try {
            balance = Math.addExact(Math.subtractExact(money, cost), draw.result.wonMoney);
        } catch (ArithmeticException overflow) {
            balance = Long.MAX_VALUE;
        }
        if (!EconomyBridge.set(player, balance)) {
            return new LotterySnapshot(catalog, money, null, "", "扣款失败，请稍后再试");
        }

        for (Award award : draw.awards) {
            ShopDelivery.give(player, award.prototype, award.count);
        }
        return new LotterySnapshot(catalog, balance, draw.result, "", "");
    }

    // ================================================================ 抽奖与结算

    private static Draw draw(LotteryCatalog.Pool pool, int rows) {
        List<LotteryCatalog.Symbol> symbols = pool.symbols();
        long totalWeight = 0L;
        for (LotteryCatalog.Symbol symbol : symbols) {
            totalWeight += Math.max(1, symbol.weight);
        }

        int[] grid = new int[rows * LotteryConfig.COLUMNS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = pickSymbol(symbols, totalWeight);
        }

        boolean[] wins = new boolean[rows];
        long wonMoney = 0L;
        List<String> rewardLines = new ArrayList<>();
        List<Award> awards = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            int first = grid[row * LotteryConfig.COLUMNS];
            int run = 1;
            while (run < LotteryConfig.COLUMNS
                    && grid[row * LotteryConfig.COLUMNS + run] == first) {
                run++;
            }
            if (run < 3) {
                continue;
            }
            LotteryConfig.Symbol symbol = symbols.get(first).config;
            LotteryConfig.Payout payout = symbol == null ? null : symbol.payoutFor(run);
            if (payout == null || payout.isEmpty()) {
                continue;
            }

            boolean rowWin = payout.money > 0L;
            wonMoney = saturatingAdd(wonMoney, Math.max(0L, payout.money));
            if (payout.items != null) {
                for (LotteryConfig.Reward reward : payout.items) {
                    ItemStack prototype = resolveItem(reward);
                    if (prototype.isEmpty()) {
                        QuantumHue.LOGGER.warn("[Lottery] 奖品物品不存在，已跳过: {}:{}",
                                reward.item, reward.meta);
                        continue;
                    }
                    int count = Math.max(1, reward.count);
                    awards.add(new Award(prototype, count));
                    rewardLines.add("§b" + prototype.getDisplayName() + " §7×" + count);
                    rowWin = true;
                }
            }
            wins[row] = rowWin;
        }

        return new Draw(new LotteryResult(rows, grid, wins, wonMoney, rewardLines), awards);
    }

    private static int pickSymbol(List<LotteryCatalog.Symbol> symbols, long totalWeight) {
        long roll = ThreadLocalRandom.current().nextLong(Math.max(1L, totalWeight));
        long cursor = 0L;
        for (int i = 0; i < symbols.size(); i++) {
            cursor += Math.max(1, symbols.get(i).weight);
            if (roll < cursor) {
                return i;
            }
        }
        return symbols.size() - 1;
    }

    private static ItemStack resolveItem(LotteryConfig.Reward reward) {
        if (reward == null || reward.item == null || reward.item.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reward.item.trim()));
            return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, Math.max(0, reward.meta));
        } catch (Exception invalid) {
            return ItemStack.EMPTY;
        }
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static final class Draw {
        final LotteryResult result;
        final List<Award> awards;

        Draw(LotteryResult result, List<Award> awards) {
            this.result = result;
            this.awards = awards;
        }
    }

    private static final class Award {
        final ItemStack prototype;
        final int count;

        Award(ItemStack prototype, int count) {
            this.prototype = prototype;
            this.count = count;
        }
    }
}
