package meowmel.quantumhue.stock.client;

import meowmel.quantumhue.stock.StockBar;
import meowmel.quantumhue.stock.StockSnapshot;
import meowmel.quantumhue.stock.StockSnapshot.PositionView;
import meowmel.quantumhue.stock.StockSnapshot.ProductView;
import meowmel.quantumhue.stock.StockSnapshot.RecordView;

import java.util.List;

/**
 * 客户端缓存。服务端每秒轮询只发价格，K 线每 15 秒才补一次，
 * 所以这里要把新旧数据合并：有 K 线就整体替换，没有就用最新价修补最后一根。
 */
public final class StockClientStore {

    private static volatile StockSnapshot state = new StockSnapshot();
    private static volatile String notice = "";
    private static volatile String error = "";

    private StockClientStore() {}

    public static StockSnapshot snapshot() {
        return state;
    }

    public static void apply(StockSnapshot incoming) {
        if (incoming == null) {
            return;
        }
        if (incoming.products.isEmpty() && incoming.error != null && !incoming.error.isEmpty()) {
            // 启动阶段可能只返回一条错误，不能拿空行情覆盖已有图表。
            error = incoming.error;
            return;
        }
        StockSnapshot current = state;
        if (incoming.full || current.products.isEmpty()) {
            state = incoming;
        } else {
            merge(current, incoming);
        }
        if (incoming.notice != null && !incoming.notice.isEmpty()) {
            notice = notice.isEmpty() ? incoming.notice : notice + "；" + incoming.notice;
        }
        if (incoming.error != null && !incoming.error.isEmpty()) {
            error = incoming.error;
        }
    }

    private static void merge(StockSnapshot current, StockSnapshot incoming) {
        current.sequence = incoming.sequence;
        current.full = false;
        current.wallet = incoming.wallet;
        current.escrow = incoming.escrow;
        current.equity = incoming.equity;
        current.usedMargin = incoming.usedMargin;
        current.available = incoming.available;

        for (ProductView product : incoming.products) {
            if (!incoming.bars) {
                ProductView old = findProduct(current, product.id);
                if (old != null) {
                    product.bars.addAll(old.bars);
                }
                patchLast(product);
            }
        }
        current.products.clear();
        current.products.addAll(incoming.products);

        current.positions.clear();
        current.positions.addAll(incoming.positions);

        if (!incoming.records.isEmpty()) {
            current.records.clear();
            current.records.addAll(incoming.records);
        }
        current.bars = incoming.bars;
    }

    private static void patchLast(ProductView product) {
        List<StockBar> bars = product.bars;
        if (bars.isEmpty()) {
            return;
        }
        StockBar last = bars.get(bars.size() - 1);
        last.close = product.price;
        last.high = Math.max(last.high, product.price);
        last.low = Math.min(last.low, product.price);
    }

    public static ProductView product(String id) {
        return findProduct(state, id);
    }

    private static ProductView findProduct(StockSnapshot snapshot, String id) {
        for (ProductView product : snapshot.products) {
            if (product.id.equals(id)) {
                return product;
            }
        }
        return null;
    }

    public static PositionView position(long id) {
        for (PositionView position : state.positions) {
            if (position.id == id) {
                return position;
            }
        }
        return null;
    }

    public static List<ProductView> products() {
        return state.products;
    }

    public static List<PositionView> positions() {
        return state.positions;
    }

    public static List<RecordView> records() {
        return state.records;
    }

    public static long wallet() {
        return state.wallet;
    }

    public static long escrow() {
        return state.escrow;
    }

    public static long equity() {
        return state.equity;
    }

    public static long usedMargin() {
        return state.usedMargin;
    }

    public static long available() {
        return state.available;
    }

    public static boolean hasData() {
        return !state.products.isEmpty();
    }

    public static String takeNotice() {
        String value = notice;
        notice = "";
        return value;
    }

    public static String takeError() {
        String value = error;
        error = "";
        return value;
    }

    public static void clear() {
        state = new StockSnapshot();
        notice = "";
        error = "";
    }
}
