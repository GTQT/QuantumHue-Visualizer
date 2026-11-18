package meowmel.quantumhue.stock;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 服务端行情引擎：对数价格均值回归 + 共同因子 + 品种噪声 + 低频跳变。
 *
 * <p>每秒推进一次价格，每 15 秒收一根 OHLCV。综合指数不独立随机，而是由其余四个品种
 * 按权重实时合成，因此它和成分品种之间天然相关，拿它做“大盘”不会出现走势互相打架。
 *
 * <p>所有价格内部用 double 计算、对外取整到“分”；随机源是普通 {@link Random}，
 * 不要求跨存档可复现，只要求走势看起来合理。
 */
public final class StockMarket {

    /** 每秒向锚点回归的比例。 */
    private static final double REVERSION = 0.004D;
    /** 共同因子占比；其余是品种自己的噪声。 */
    private static final double COMMON_FACTOR = 0.60D;
    /** 每秒发生一次跳变的概率。 */
    private static final double JUMP_CHANCE = 0.0012D;
    /** 锚点每秒随机游走的对数步长。 */
    private static final double ANCHOR_STEP = 0.00035D;

    private static final class State {
        final StockProduct product;
        double price;
        double anchor;
        double previousPrice;
        final List<StockBar> bars = new ArrayList<>();
        StockBar current;

        State(StockProduct product) {
            this.product = product;
            this.price = product.basePrice;
            this.anchor = product.basePrice;
            this.previousPrice = product.basePrice;
        }
    }

    private final Map<String, State> states = new LinkedHashMap<>();
    private final Random random = new Random();
    private long ticksInBar;
    private long barStart;
    private boolean seeded;
    private boolean barClosed;

    public StockMarket() {
        for (StockProduct product : StockConfig.products()) {
            states.put(product.id, new State(product));
        }
        barStart = alignedNow();
    }

    // ================================================================ 推进

    /**
     * 推进一个 world tick。
     *
     * @return 本次是否更新了价格（每秒一次），服务端据此检查保护单与强平
     */
    public boolean tick() {
        seedIfNeeded();
        ticksInBar++;
        boolean priceStep = ticksInBar % StockConfig.TICKS_PER_SECOND == 0;
        if (priceStep) {
            updatePrices();
        }
        if (ticksInBar >= StockConfig.BAR_TICKS) {
            closeBar();
            ticksInBar = 0L;
            barClosed = true;
        }
        return priceStep;
    }

    /** 取走“刚收完一根 K 线”的标记。 */
    public boolean consumeBarClosed() {
        boolean closed = barClosed;
        barClosed = false;
        return closed;
    }

    private void updatePrices() {
        double common = random.nextGaussian();
        for (State state : states.values()) {
            if (!state.product.composite) {
                advance(state, common);
            }
        }
        updateComposite();
        for (State state : states.values()) {
            touchCurrent(state);
        }
    }

    private void advance(State state, double common) {
        StockProduct product = state.product;
        double base = product.basePrice;
        double logPrice = Math.log(state.price);
        double logAnchor = Math.log(state.anchor);

        double revert = (logAnchor - logPrice) * REVERSION;
        double sigma = product.barVolatility / Math.sqrt(StockConfig.SECONDS_PER_BAR);
        double shock = sigma * (COMMON_FACTOR * common
                + Math.sqrt(1.0D - COMMON_FACTOR * COMMON_FACTOR) * random.nextGaussian());
        if (random.nextDouble() < JUMP_CHANCE) {
            double size = 0.002D + 0.006D * random.nextDouble();
            shock += random.nextBoolean() ? size : -size;
        }

        logPrice += revert + shock;
        logAnchor += random.nextGaussian() * ANCHOR_STEP;

        double minAnchor = Math.log(base * 0.55D);
        double maxAnchor = Math.log(base * 1.90D);
        logAnchor = Math.max(minAnchor, Math.min(maxAnchor, logAnchor));

        double minPrice = Math.log(base * 0.30D);
        double maxPrice = Math.log(base * 3.00D);
        logPrice = Math.max(minPrice, Math.min(maxPrice, logPrice));

        state.anchor = Math.exp(logAnchor);
        state.price = Math.exp(logPrice);
    }

    private void updateComposite() {
        State composite = states.get(StockConfig.COMPOSITE_ID);
        if (composite == null) {
            return;
        }
        double index = 0.0D;
        for (State state : states.values()) {
            StockProduct product = state.product;
            if (!product.composite) {
                index += product.weight * (state.price / product.basePrice) * 10_000.0D;
            }
        }
        composite.price = index;
    }

    private void touchCurrent(State state) {
        long price = Math.round(state.price);
        if (state.current == null) {
            state.current = new StockBar(barStart, price, price, price, price, 0L);
        }
        StockBar bar = state.current;
        bar.close = price;
        bar.high = Math.max(bar.high, price);
        bar.low = Math.min(bar.low, price);

        double move = state.previousPrice <= 0.0D ? 0.0D
                : Math.abs(state.price - state.previousPrice) / state.previousPrice;
        bar.volume += 100L + random.nextInt(900) + (long) (move * 400_000.0D);
        state.previousPrice = state.price;
    }

    private void closeBar() {
        for (State state : states.values()) {
            if (state.current != null) {
                state.current.time = barStart;
                state.bars.add(state.current);
                while (state.bars.size() > StockConfig.HISTORY_BARS) {
                    state.bars.remove(0);
                }
            }
            long price = Math.round(state.price);
            state.current = new StockBar(barStart + StockConfig.BAR_MILLIS,
                    price, price, price, price, 0L);
        }
        barStart += StockConfig.BAR_MILLIS;
    }

    // ================================================================ 初始化

    public void seedIfNeeded() {
        if (seeded) {
            return;
        }
        seeded = true;
        for (State state : states.values()) {
            state.price = state.product.basePrice;
            state.anchor = state.product.basePrice;
            state.previousPrice = state.product.basePrice;
            state.bars.clear();
            state.current = null;
        }
        barStart = alignedNow() - StockConfig.SEED_BARS * StockConfig.BAR_MILLIS;
        ticksInBar = 0L;

        for (int bar = 0; bar < StockConfig.SEED_BARS; bar++) {
            for (int second = 0; second < StockConfig.SECONDS_PER_BAR; second++) {
                updatePrices();
            }
            closeBar();
        }
        // 历史走完后把整条时间轴平移到“现在”，当前 K 线才是正在走的那根
        long now = alignedNow();
        long shift = now - barStart;
        for (State state : states.values()) {
            for (StockBar bar : state.bars) {
                bar.time += shift;
            }
            if (state.current != null) {
                state.current.time += shift;
            }
        }
        barStart = now;
        ticksInBar = 0L;
    }

    private static long alignedNow() {
        long now = System.currentTimeMillis();
        return now - Math.floorMod(now, StockConfig.BAR_MILLIS);
    }

    // ================================================================ 查询

    /** 最新标记价（分）。 */
    public long markPrice(String productId) {
        State state = states.get(productId);
        return state == null ? 0L : Math.round(state.price);
    }

    /** 相对上一根已收盘 K 线的涨跌幅，单位 0.01%（92 表示 +0.92%）。 */
    public int changePct100(String productId) {
        State state = states.get(productId);
        if (state == null) {
            return 0;
        }
        long previous = state.bars.isEmpty()
                ? state.product.basePrice
                : state.bars.get(state.bars.size() - 1).close;
        if (previous <= 0L) {
            return 0;
        }
        long last = Math.round(state.price);
        return (int) Math.round((last - previous) * 10_000.0D / previous);
    }

    /**
     * 取出最近的 K 线，包含正在走的那根。
     *
     * @param limit 最多取多少根已收盘 K 线
     */
    public List<StockBar> viewBars(String productId, int limit) {
        List<StockBar> view = new ArrayList<>();
        State state = states.get(productId);
        if (state == null) {
            return view;
        }
        int from = Math.max(0, state.bars.size() - Math.max(1, limit));
        for (int i = from; i < state.bars.size(); i++) {
            view.add(state.bars.get(i).copy());
        }
        if (state.current != null) {
            view.add(state.current.copy());
        }
        return view;
    }

    public int barCount(String productId) {
        State state = states.get(productId);
        return state == null ? 0 : state.bars.size();
    }

    /** 当前未收盘 K 线的起始时间（毫秒）。 */
    public long currentBarStart() {
        return barStart;
    }

    // ================================================================ 存档

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("seeded", seeded);
        tag.setLong("ticks", ticksInBar);
        tag.setLong("barStart", barStart);

        NBTTagList list = new NBTTagList();
        for (State state : states.values()) {
            NBTTagCompound productTag = new NBTTagCompound();
            productTag.setString("id", state.product.id);
            productTag.setDouble("price", state.price);
            productTag.setDouble("anchor", state.anchor);
            productTag.setDouble("prev", state.previousPrice);
            if (state.current != null) {
                productTag.setTag("current", state.current.writeToNbt());
            }
            NBTTagList barList = new NBTTagList();
            for (StockBar bar : state.bars) {
                barList.appendTag(bar.writeToNbt());
            }
            productTag.setTag("bars", barList);
            list.appendTag(productTag);
        }
        tag.setTag("products", list);
        return tag;
    }

    public void readFromNbt(NBTTagCompound tag) {
        states.clear();
        for (StockProduct product : StockConfig.products()) {
            states.put(product.id, new State(product));
        }
        ticksInBar = tag.getLong("ticks");
        barStart = tag.getLong("barStart");
        seeded = tag.getBoolean("seeded");

        NBTTagList list = tag.getTagList("products", 10);
        for (NBTBase base : list) {
            NBTTagCompound productTag = (NBTTagCompound) base;
            StockProduct product = StockConfig.product(productTag.getString("id"));
            if (product == null) {
                continue;
            }
            State state = states.get(product.id);
            state.price = productTag.getDouble("price");
            state.anchor = productTag.getDouble("anchor");
            state.previousPrice = productTag.getDouble("prev");
            if (state.price <= 0.0D) {
                state.price = product.basePrice;
            }
            if (state.anchor <= 0.0D) {
                state.anchor = product.basePrice;
            }
            if (productTag.hasKey("current")) {
                state.current = StockBar.readFromNbt(productTag.getCompoundTag("current"));
            }
            NBTTagList barList = productTag.getTagList("bars", 10);
            for (NBTBase barBase : barList) {
                state.bars.add(StockBar.readFromNbt((NBTTagCompound) barBase));
            }
            while (state.bars.size() > StockConfig.HISTORY_BARS) {
                state.bars.remove(0);
            }
        }

        if (!seeded || states.isEmpty()) {
            seeded = false;
            seedIfNeeded();
            return;
        }

        // 服务器停机期间行情不走：把整条时间轴平移到当前时刻，图表时间才不会停在昨天
        long now = alignedNow();
        long shift = now - barStart;
        for (State state : states.values()) {
            for (StockBar bar : state.bars) {
                bar.time += shift;
            }
            if (state.current != null) {
                state.current.time += shift;
            }
        }
        barStart = now;
        if (ticksInBar < 0L || ticksInBar >= StockConfig.BAR_TICKS) {
            ticksInBar = 0L;
        }
    }
}
