package meowmel.quantumhue.lottery;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖（老虎机）的服务端权威配置。
 *
 * <p>与商店一样，内容由附属模组在加载阶段通过 {@code QuantumHuePageApi} 注册，加载完成时统一构建。
 * 老虎机固定 5 列、最多 3 行：界面提供“抽一行 / 抽两行 / 抽三行”三个按钮，每次抽奖按
 * “基础价 × 本次抽取行数”扣费；每行独立结算，左起连续 3 个相同符号即中奖。
 *
 * <p>奖池由一组 {@link Symbol} 组成：{@code weight} 是每个转轮的抽取权重（相对概率），
 * {@code three/four/five} 分别是 3/4/5 连的奖品。某个长度的赔率为 {@code null} 时回退到更短的赔率，
 * 所以只配 3 连也能玩。{@link Payout} 同时支持货币与物品奖励。
 */
public class LotteryConfig {

    public static final int MAX_ROWS = 3;
    public static final int COLUMNS = 5;

    /** 抽奖总开关；关闭后界面仍可打开，但会显示“暂未开放”。 */
    public boolean enabled = true;
    /** 默认奖池的每行基础价格；实际价格 = baseSpinCost × 本次抽取行数。 */
    public long baseSpinCost = 100L;
    /** 默认奖池的符号，顺序同时也是网络下标，构建后不再变化。 */
    public List<Symbol> symbols = new ArrayList<>();
    /** 额外奖池；默认奖池由上面的 {@link #symbols} 与 {@link #baseSpinCost} 表示。 */
    public List<Pool> pools = new ArrayList<>();

    /** 默认奖池的本次抽奖总价；行数会被夹到 1..3，溢出时返回 {@link Long#MAX_VALUE}。 */
    public long spinCost(int rows) {
        return defaultPool().spinCost(rows);
    }

    /** 由默认字段拼出的奖池；每次返回新对象，符号列表直接引用 {@link #symbols}。 */
    public Pool defaultPool() {
        Pool pool = new Pool();
        pool.id = "default";
        pool.name = "默认奖池";
        pool.icon = "minecraft:chest";
        pool.iconMeta = 0;
        pool.enabled = true;
        pool.baseSpinCost = baseSpinCost;
        pool.symbols = symbols;
        return pool;
    }

    /**
     * 供构建快照使用的完整奖池列表：默认奖池在前，之后是额外奖池。
     *
     * <p>默认奖池没有符号时不会出现在“额外奖池已存在”的列表里，避免界面多出一个空奖池；
     * 但如果一个奖池都没有，则保留空的默认奖池，让界面能显示“尚未配置”。
     */
    public List<Pool> resolvedPools() {
        List<Pool> result = new ArrayList<>();
        boolean hasDefault = symbols != null && !symbols.isEmpty();
        if (hasDefault || pools == null || pools.isEmpty()) {
            result.add(defaultPool());
        }
        if (pools != null) {
            for (Pool pool : pools) {
                if (pool != null) {
                    result.add(pool);
                }
            }
        }
        return result;
    }

    /** 按 id 找奖池；空 id 指默认奖池，找不到返回 {@code null}。 */
    public Pool pool(String id) {
        if (id == null || id.trim().isEmpty()) {
            return defaultPool();
        }
        for (Pool pool : resolvedPools()) {
            if (id.trim().equals(pool.id)) {
                return pool;
            }
        }
        return null;
    }

    /** 默认奖池 id；没有任何奖池时返回空串。 */
    public String defaultPoolId() {
        List<Pool> resolved = resolvedPools();
        return resolved.isEmpty() ? "" : resolved.get(0).id;
    }

    /** 配一个可直接开玩的演示奖池；只有完全没有注册符号时才会被 {@code PageConfigs} 采用。 */
    public static List<Symbol> defaultSymbols() {
        List<Symbol> symbols = new ArrayList<>();
        symbols.add(money("coal", "煤炭", "minecraft:coal", 30, 300L, 3_000L, 30_000L));
        symbols.add(money("redstone", "红石", "minecraft:redstone", 25, 400L, 4_000L, 40_000L));
        symbols.add(money("iron", "铁锭", "minecraft:iron_ingot", 25, 500L, 5_000L, 50_000L));
        symbols.add(money("gold", "金锭", "minecraft:gold_ingot", 20, 800L, 8_000L, 80_000L));
        symbols.add(money("lapis", "青金石", "minecraft:dye", 18, 1_000L, 10_000L, 100_000L)
                .withIconMeta(4));
        symbols.add(money("diamond", "钻石", "minecraft:diamond", 10, 3_000L, 30_000L, 300_000L));
        symbols.add(money("emerald", "绿宝石", "minecraft:emerald", 8, 5_000L, 50_000L, 500_000L));
        symbols.add(money("nether_star", "下界之星", "minecraft:nether_star", 2,
                20_000L, 200_000L, 2_000_000L));
        return symbols;
    }

    private static Symbol money(String id, String name, String icon, int weight,
                                long three, long four, long five) {
        return new Symbol(id, name, icon, 0, weight, new Payout(three), new Payout(four), new Payout(five));
    }

    /** 一个可独立选择、独立计价的奖池。默认奖池由 {@link LotteryConfig} 的顶层字段表示。 */
    public static class Pool {
        public String id = "";
        public String name = "";
        public String icon = "minecraft:chest";
        public int iconMeta;
        public boolean enabled = true;
        public long baseSpinCost = 100L;
        public List<Symbol> symbols = new ArrayList<>();

        public Pool() {}

        public Pool(String id, String name, String icon, int iconMeta,
                    long baseSpinCost, List<Symbol> symbols) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.iconMeta = iconMeta;
            this.baseSpinCost = baseSpinCost;
            if (symbols != null) {
                this.symbols = symbols;
            }
        }

        public long spinCost(int rows) {
            int safeRows = Math.max(1, Math.min(MAX_ROWS, rows));
            try {
                return Math.multiplyExact(Math.max(0L, baseSpinCost), (long) safeRows);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        public Pool copy() {
            Pool copy = new Pool(id, name, icon, iconMeta, baseSpinCost, new ArrayList<>());
            copy.enabled = enabled;
            if (symbols != null) {
                for (Symbol symbol : symbols) {
                    if (symbol != null) {
                        copy.symbols.add(symbol.copy());
                    }
                }
            }
            return copy;
        }
    }

    /** 奖池符号：显示名、图标、抽取权重与 3/4/5 连赔率。 */
    public static class Symbol {
        public String id = "";
        public String name = "";
        public String icon = "";
        public int iconMeta;
        /** 相对权重，必须大于 0；构建时会被夹到 1..100000。 */
        public int weight = 1;
        public Payout three;
        public Payout four;
        public Payout five;

        public Symbol() {}

        public Symbol(String id, String name, String icon, int iconMeta, int weight,
                      Payout three, Payout four, Payout five) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.iconMeta = iconMeta;
            this.weight = weight;
            this.three = three;
            this.four = four;
            this.five = five;
        }

        public Symbol withIconMeta(int meta) {
            this.iconMeta = meta;
            return this;
        }

        /** 取该符号在 {@code matchCount}（3/4/5）连时的赔率；缺失时回退到更短的连击。 */
        public Payout payoutFor(int matchCount) {
            if (matchCount >= 5 && five != null) {
                return five;
            }
            if (matchCount >= 4 && four != null) {
                return four;
            }
            if (matchCount >= 3 && three != null) {
                return three;
            }
            return null;
        }

        public Symbol copy() {
            Symbol copy = new Symbol(id, name, icon, iconMeta, weight,
                    three == null ? null : three.copy(),
                    four == null ? null : four.copy(),
                    five == null ? null : five.copy());
            return copy;
        }
    }

    /** 一次中奖的奖品：货币 + 若干物品。 */
    public static class Payout {
        public long money;
        public List<Reward> items = new ArrayList<>();

        public Payout() {}

        public Payout(long money) {
            this.money = money;
        }

        public static Payout of(long money) {
            return new Payout(money);
        }

        /** 追加一项物品奖励，便于链式注册。 */
        public Payout withItem(String item, int meta, int count) {
            if (items == null) {
                items = new ArrayList<>();
            }
            items.add(new Reward(item, meta, count));
            return this;
        }

        public Payout copy() {
            Payout copy = new Payout(money);
            if (items != null) {
                for (Reward reward : items) {
                    if (reward != null) {
                        copy.items.add(reward.copy());
                    }
                }
            }
            return copy;
        }

        public boolean isEmpty() {
            return money <= 0L && (items == null || items.isEmpty());
        }
    }

    /** 一项物品奖励。 */
    public static class Reward {
        public String item = "";
        public int meta;
        public int count = 1;

        public Reward() {}

        public Reward(String item, int meta, int count) {
            this.item = item;
            this.meta = meta;
            this.count = count;
        }

        public Reward copy() {
            return new Reward(item, meta, count);
        }
    }
}
