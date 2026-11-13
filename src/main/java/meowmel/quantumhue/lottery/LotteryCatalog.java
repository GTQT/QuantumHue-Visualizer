package meowmel.quantumhue.lottery;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 抽奖奖池的“可展示快照”。
 *
 * <p>服务端每次响应都会按当前 {@link LotteryConfig} 重建：把图标注册名解析成 {@link ItemStack}，
 * 把 3/4/5 连赔率格式化成浮层文本。客户端只读网络传来的快照，不读配置、也不查物品注册表。
 *
 * <p>快照包含多个可切换的奖池；默认奖池排在最前。符号顺序就是抽奖结果里的下标，构建后不再变化。
 * 物品注册名无效时保留符号但图标为空，这样服务端与客户端的符号下标始终一致。
 */
public final class LotteryCatalog {

    /** 一个奖池的展示信息。 */
    public static final class Pool {
        public final String id;
        public final String name;
        public final ItemStack icon;
        public final boolean enabled;
        public final long baseSpinCost;
        private final List<Symbol> symbols;

        Pool(String id, String name, ItemStack icon, boolean enabled,
             long baseSpinCost, List<Symbol> symbols) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.icon = icon == null ? ItemStack.EMPTY : icon;
            this.enabled = enabled;
            this.baseSpinCost = Math.max(0L, baseSpinCost);
            this.symbols = Collections.unmodifiableList(symbols);
        }

        public long spinCost(int rows) {
            int safeRows = Math.max(1, Math.min(LotteryConfig.MAX_ROWS, rows));
            try {
                return Math.multiplyExact(baseSpinCost, (long) safeRows);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        public List<Symbol> symbols() {
            return symbols;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean isEmpty() {
            return symbols.isEmpty();
        }
    }

    /** 一个奖池符号的展示信息。{@code config} 只在服务端构建时有值。 */
    public static final class Symbol {
        public final LotteryConfig.Symbol config;
        public final String id;
        public final String name;
        public final ItemStack icon;
        public final int weight;
        public final List<String> paytable;

        Symbol(LotteryConfig.Symbol config, String id, String name, ItemStack icon,
               int weight, List<String> paytable) {
            this.config = config;
            this.id = id;
            this.name = name;
            this.icon = icon == null ? ItemStack.EMPTY : icon;
            this.weight = weight;
            this.paytable = Collections.unmodifiableList(paytable);
        }

        /** 浮层里显示的单行赔率；缺失时返回空串。 */
        public String payoutLine(int matchCount) {
            int index = matchCount - 3;
            return index >= 0 && index < paytable.size() ? paytable.get(index) : "";
        }
    }

    private final boolean enabled;
    private final List<Pool> pools;
    private final String defaultPoolId;

    private LotteryCatalog(boolean enabled, List<Pool> pools) {
        this.enabled = enabled;
        this.pools = Collections.unmodifiableList(pools);
        this.defaultPoolId = pools.isEmpty() ? "" : pools.get(0).id;
    }

    /** 按当前配置构建快照；服务端专用。 */
    public static LotteryCatalog build() {
        LotteryConfig config = PageConfigs.lottery();
        if (config == null) {
            return empty();
        }

        List<Pool> views = new ArrayList<>();
        for (LotteryConfig.Pool pool : config.resolvedPools()) {
            if (pool == null) {
                continue;
            }
            List<Symbol> symbols = buildSymbols(pool.symbols);
            ItemStack icon = resolveIcon(pool.icon, pool.iconMeta);
            String name = pool.name == null || pool.name.trim().isEmpty()
                    ? (icon.isEmpty() ? pool.id : icon.getDisplayName())
                    : pool.name.trim();
            views.add(new Pool(pool.id, name, icon, pool.enabled,
                    pool.baseSpinCost, symbols));
        }

        boolean anySymbol = false;
        for (Pool pool : views) {
            if (!pool.isEmpty()) {
                anySymbol = true;
                break;
            }
        }
        if (!anySymbol) {
            return empty();
        }
        List<Pool> filled = new ArrayList<>(views.size());
        for (Pool pool : views) {
            if (!pool.isEmpty()) {
                filled.add(pool);
            }
        }
        return new LotteryCatalog(config.enabled, filled);
    }

    private static List<Symbol> buildSymbols(List<LotteryConfig.Symbol> source) {
        List<Symbol> views = new ArrayList<>();
        if (source == null) {
            return views;
        }
        for (LotteryConfig.Symbol symbol : source) {
            if (symbol == null) {
                continue;
            }
            ItemStack icon = resolveIcon(symbol.icon, symbol.iconMeta);
            String name = symbol.name == null || symbol.name.trim().isEmpty()
                    ? (icon.isEmpty() ? symbol.id : icon.getDisplayName())
                    : symbol.name.trim();
            List<String> lines = new ArrayList<>(3);
            lines.add(payoutLine(3, symbol.payoutFor(3)));
            lines.add(payoutLine(4, symbol.payoutFor(4)));
            lines.add(payoutLine(5, symbol.payoutFor(5)));
            views.add(new Symbol(symbol, symbol.id, name, icon, symbol.weight, lines));
        }
        return views;
    }

    public static LotteryCatalog empty() {
        return new LotteryCatalog(false, new ArrayList<>());
    }

    public boolean enabled() {
        return enabled;
    }

    public List<Pool> pools() {
        return pools;
    }

    /** 按 id 找奖池；空 id 指默认奖池，找不到返回 {@code null}。 */
    public Pool pool(String id) {
        if (id == null || id.trim().isEmpty()) {
            return defaultPool();
        }
        for (Pool pool : pools) {
            if (id.trim().equals(pool.id)) {
                return pool;
            }
        }
        return null;
    }

    public Pool defaultPool() {
        return pools.isEmpty() ? null : pools.get(0);
    }

    public String defaultPoolId() {
        return defaultPoolId;
    }

    /** 默认奖池的每行基础价；兼容旧的单奖池调用。 */
    public long baseSpinCost() {
        Pool pool = defaultPool();
        return pool == null ? 0L : pool.baseSpinCost;
    }

    /** 默认奖池的本次总价；兼容旧的单奖池调用。 */
    public long spinCost(int rows) {
        Pool pool = defaultPool();
        return pool == null ? 0L : pool.spinCost(rows);
    }

    /** 默认奖池的符号；兼容旧的单奖池调用。 */
    public List<Symbol> symbols() {
        Pool pool = defaultPool();
        return pool == null ? Collections.emptyList() : pool.symbols();
    }

    public boolean isEmpty() {
        return pools.isEmpty();
    }

    // ================================================================ 序列化

    public void write(ByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(pools.size());
        for (Pool pool : pools) {
            ByteBufUtils.writeUTF8String(buf, pool.id == null ? "" : pool.id);
            ByteBufUtils.writeUTF8String(buf, pool.name == null ? "" : pool.name);
            ByteBufUtils.writeItemStack(buf, pool.icon);
            buf.writeBoolean(pool.enabled);
            buf.writeLong(pool.baseSpinCost);
            buf.writeInt(pool.symbols.size());
            for (Symbol symbol : pool.symbols) {
                ByteBufUtils.writeUTF8String(buf, symbol.id == null ? "" : symbol.id);
                ByteBufUtils.writeUTF8String(buf, symbol.name == null ? "" : symbol.name);
                ByteBufUtils.writeItemStack(buf, symbol.icon);
                buf.writeInt(symbol.weight);
                buf.writeInt(symbol.paytable.size());
                for (String line : symbol.paytable) {
                    ByteBufUtils.writeUTF8String(buf, line == null ? "" : line);
                }
            }
        }
    }

    public static LotteryCatalog read(ByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int poolCount = Math.min(32, Math.max(0, buf.readInt()));
        List<Pool> pools = new ArrayList<>(poolCount);
        for (int p = 0; p < poolCount; p++) {
            String id = ByteBufUtils.readUTF8String(buf);
            String name = ByteBufUtils.readUTF8String(buf);
            ItemStack icon = ByteBufUtils.readItemStack(buf);
            boolean poolEnabled = buf.readBoolean();
            long base = buf.readLong();
            int size = Math.min(256, Math.max(0, buf.readInt()));
            List<Symbol> symbols = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String symbolId = ByteBufUtils.readUTF8String(buf);
                String symbolName = ByteBufUtils.readUTF8String(buf);
                ItemStack symbolIcon = ByteBufUtils.readItemStack(buf);
                int weight = buf.readInt();
                int lineCount = Math.min(32, Math.max(0, buf.readInt()));
                List<String> lines = new ArrayList<>(lineCount);
                for (int line = 0; line < lineCount; line++) {
                    lines.add(ByteBufUtils.readUTF8String(buf));
                }
                symbols.add(new Symbol(null, symbolId, symbolName, symbolIcon, weight, lines));
            }
            pools.add(new Pool(id, name, icon, poolEnabled, base, symbols));
        }
        return new LotteryCatalog(enabled, pools);
    }

    // ================================================================ 构建辅助

    private static String payoutLine(int matchCount, LotteryConfig.Payout payout) {
        if (payout == null) {
            return "";
        }
        StringBuilder line = new StringBuilder();
        if (payout.money > 0L) {
            line.append("§e").append(format(payout.money)).append(" §7货币");
        }
        if (payout.items != null) {
            for (LotteryConfig.Reward reward : payout.items) {
                if (reward == null || reward.item == null || reward.item.trim().isEmpty()) {
                    continue;
                }
                ItemStack stack = resolveIcon(reward.item, reward.meta);
                String itemName = stack.isEmpty() ? reward.item : stack.getDisplayName();
                if (line.length() > 0) {
                    line.append(" §8+ ");
                }
                line.append("§f").append(itemName).append(" §7×").append(reward.count);
            }
        }
        return line.toString();
    }

    private static ItemStack resolveIcon(String registryName, int meta) {
        if (registryName == null || registryName.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName.trim()));
            return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, Math.max(0, meta));
        } catch (Exception invalid) {
            return ItemStack.EMPTY;
        }
    }

    private static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
