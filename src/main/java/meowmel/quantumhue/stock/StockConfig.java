package meowmel.quantumhue.stock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 模拟交易终端的全部可调参数与内置品种。
 *
 * <p>这里不读磁盘配置：品种与费率随模组一起发布，服务端与客户端读同一份常量。
 * 需要改参数时直接改这里，重新构建即可；存档里的旧持仓按开仓时已经写入的保证金继续结算，
 * 不会因为费率调整而凭空变化。
 */
public final class StockConfig {

    private StockConfig() {}

    // ================================================================ 行情节奏

    /** 每秒的服务端 tick 数。 */
    public static final int TICKS_PER_SECOND = 20;
    /** 一根 K 线的秒数。 */
    public static final int SECONDS_PER_BAR = 15;
    /** 一根 K 线占用的 world tick 数。 */
    public static final int BAR_TICKS = TICKS_PER_SECOND * SECONDS_PER_BAR;
    /** 每根 K 线占用的毫秒数。 */
    public static final long BAR_MILLIS = SECONDS_PER_BAR * 1000L;
    /** 服务端保留的历史 K 线根数。 */
    public static final int HISTORY_BARS = 240;
    /** 打开页签时同步给客户端的最大根数。 */
    public static final int SYNC_BARS = 100;
    /** 首次生成世界时预填的历史根数。 */
    public static final int SEED_BARS = 90;

    // ================================================================ 交易参数

    /** 可选杠杆档位。 */
    public static final int[] LEVERAGES = {5, 10, 20, 50, 100};
    /** 默认杠杆。 */
    public static final int DEFAULT_LEVERAGE = 20;
    /** 单品种单方向的最大持仓（张）。 */
    public static final long MAX_POSITION = 1_000_000L;
    /** 单账户最多同时持有的持仓笔数，避免把快照撑得过大。 */
    public static final int MAX_POSITIONS = 64;
    /** 手续费：名义价值的 0.055%，即 55 / 100000。 */
    public static final long FEE_NUMERATOR = 55L;
    public static final long FEE_DENOMINATOR = 100_000L;
    /** 滑点：0.02%，即 2 / 10000。 */
    public static final long SLIPPAGE_NUMERATOR = 2L;
    public static final long SLIPPAGE_DENOMINATOR = 10_000L;
    /** 维持保证金率：初始保证金的 1/2。 */
    public static final long MAINTENANCE_NUMERATOR = 1L;
    public static final long MAINTENANCE_DENOMINATOR = 2L;
    /** 同一玩家的操作冷却。 */
    public static final long COOLDOWN_MS = 250L;
    /** 每个账户保留的成交记录条数。 */
    public static final int MAX_RECORDS = 50;
    /** 每个账户暂存的离线通知条数。 */
    public static final int MAX_NOTICES = 20;
    /** 保护单价格模式 / 盈亏模式。 */
    public static final int PROTECTION_NONE = 0;
    public static final int PROTECTION_PRICE = 1;
    public static final int PROTECTION_PROFIT = 2;

    // ================================================================ 品种

    public static final String COMPOSITE_ID = "composite";

    private static final List<StockProduct> PRODUCTS = Collections.unmodifiableList(Arrays.asList(
            new StockProduct(COMPOSITE_ID, "综合经济指数", 10_000L, 0.0D, 0.0D, true),
            new StockProduct("circuit", "电路板指数", 12_800L, 0.0022D, 0.30D, false),
            new StockProduct("energy", "能源指数", 8_640L, 0.0018D, 0.25D, false),
            new StockProduct("ore", "矿石指数", 9_420L, 0.0020D, 0.25D, false),
            new StockProduct("agri", "农业指数", 6_150L, 0.0026D, 0.20D, false)
    ));

    /** 全部品种，综合指数在第一位。 */
    public static List<StockProduct> products() {
        return PRODUCTS;
    }

    /** 参与综合指数合成的品种。 */
    public static List<StockProduct> components() {
        List<StockProduct> components = new ArrayList<>();
        for (StockProduct product : PRODUCTS) {
            if (!product.composite) {
                components.add(product);
            }
        }
        return components;
    }

    public static StockProduct product(String id) {
        if (id == null) {
            return null;
        }
        for (StockProduct product : PRODUCTS) {
            if (product.id.equals(id)) {
                return product;
            }
        }
        return null;
    }

    /** 张数是否在允许范围内。 */
    public static boolean validQuantity(long quantity) {
        return quantity > 0L && quantity <= MAX_POSITION;
    }

    /** 杠杆是否属于可选档位。 */
    public static boolean validLeverage(int leverage) {
        for (int value : LEVERAGES) {
            if (value == leverage) {
                return true;
            }
        }
        return false;
    }

    /** 向上取整的整数除法，除数必须为正。 */
    public static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }
}
