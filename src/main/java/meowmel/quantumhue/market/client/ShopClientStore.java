package meowmel.quantumhue.market.client;

import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.SystemShopCatalog;
import meowmel.quantumhue.market.TradeItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 客户端持有的商店视图。
 *
 * <p>商店数据是服务端权威的（{@code ShopDatabase} 是 {@code WorldSavedData}），客户端不应该也无法直接
 * 读它，所以界面只从这里取数。数据由服务端通过 {@code MessageShopView} 分批推送：每次推送可能带
 * {@code more=true}，客户端据此继续请求下一页，避免一次性发送整包。
 *
 * <p>所有列表都是不可变快照，读取不需要加锁或复制；写入只发生在客户端主线程（网络包调度回主线程
 * 之后），因此这里不引入额外的同步开销。
 */
public final class ShopClientStore {

    /** 一页最多包含多少条记录，服务端与客户端共用同一个值，保证分页边界一致。 */
    public static final int PAGE_SIZE = 64;

    private static volatile List<TradeItem> listings = Collections.emptyList();
    private static volatile List<BuyOrder> buyOrders = Collections.emptyList();
    private static volatile List<TradeItem> ownListings = Collections.emptyList();
    private static volatile SystemShopCatalog systemShop = SystemShopCatalog.empty();
    private static volatile long playerMoney;
    /** 是否已经收到过至少一次服务端数据；界面用它区分“还没同步”和“确实是空的”。 */
    private static volatile boolean synced;
    /** 上一批浏览数据是否还有后续页需要请求。 */
    private static volatile boolean pendingMore;

    private ShopClientStore() {}

    // ================================================================ 写入

    public static synchronized void beginListings(long money, List<TradeItem> firstPage, boolean more) {
        listings = immutable(firstPage);
        playerMoney = money;
        synced = true;
        pendingMore = more;
    }

    public static synchronized void appendListings(List<TradeItem> page, boolean more) {
        List<TradeItem> merged = new ArrayList<>(listings.size() + page.size());
        merged.addAll(listings);
        merged.addAll(page);
        listings = Collections.unmodifiableList(merged);
        pendingMore = more;
    }

    public static synchronized void setBuyOrders(long money, List<BuyOrder> orders) {
        buyOrders = Collections.unmodifiableList(new ArrayList<>(orders));
        playerMoney = money;
        synced = true;
        pendingMore = false;
    }

    public static synchronized void setOwnListings(long money, List<TradeItem> own) {
        ownListings = immutable(own);
        playerMoney = money;
        synced = true;
        pendingMore = false;
    }

    public static synchronized void setSystemShop(long money, SystemShopCatalog catalog) {
        systemShop = catalog == null ? SystemShopCatalog.empty() : catalog;
        playerMoney = money;
        synced = true;
        pendingMore = false;
    }

    /** 单条记录的增量更新：避免服务端每次买卖都重发整个商店。 */
    public static synchronized void updateListing(UUID id, TradeItem updated) {
        List<TradeItem> next = new ArrayList<>(listings.size());
        boolean replaced = false;
        for (TradeItem item : listings) {
            if (item.id.equals(id)) {
                if (updated != null) {
                    next.add(updated);
                }
                replaced = true;
            } else {
                next.add(item);
            }
        }
        if (!replaced && updated != null) {
            next.add(updated);
        }
        listings = Collections.unmodifiableList(next);
        if (updated == null) {
            ownListings = withoutId(ownListings, id);
        } else {
            ownListings = withListing(ownListings, id, updated);
        }
    }

    public static synchronized void setPlayerMoney(long money) {
        playerMoney = money;
    }

    // ================================================================ 读取

    public static List<TradeItem> listings() {
        return listings;
    }

    public static List<BuyOrder> buyOrders() {
        return buyOrders;
    }

    public static List<TradeItem> ownListings() {
        return ownListings;
    }

    public static SystemShopCatalog systemShop() {
        return systemShop;
    }

    public static long playerMoney() {
        return playerMoney;
    }

    public static boolean isSynced() {
        return synced;
    }

    /** @return 上一批数据是否还有后续页需要请求 */
    public static boolean hasPendingPage() {
        return pendingMore;
    }

    // ================================================================ 查询辅助

    public static List<TradeItem> filterListings(String searchTerm) {
        return filterTradeItems(listings, searchTerm);
    }

    public static List<BuyOrder> filterBuyOrders(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return buyOrders;
        }
        String needle = searchTerm.toLowerCase();
        List<BuyOrder> result = new ArrayList<>();
        for (BuyOrder order : buyOrders) {
            if (matches(order.getItemStack().getDisplayName(), needle)) {
                result.add(order);
            }
        }
        return result;
    }

    /** 与服务端 {@code ShopDatabase.searchListings} 使用相同的匹配规则。 */
    public static List<TradeItem> filterTradeItems(List<TradeItem> source, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return source;
        }
        String needle = searchTerm.toLowerCase();
        List<TradeItem> result = new ArrayList<>();
        for (TradeItem item : source) {
            if (matches(item.getItemStack().getDisplayName(), needle)) {
                result.add(item);
            }
        }
        return result;
    }

    private static boolean matches(String displayName, String lowerNeedle) {
        return displayName != null && displayName.toLowerCase().contains(lowerNeedle);
    }

    private static List<TradeItem> immutable(List<TradeItem> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<TradeItem> withoutId(List<TradeItem> source, UUID id) {
        List<TradeItem> next = new ArrayList<>(source.size());
        for (TradeItem item : source) {
            if (!item.id.equals(id)) {
                next.add(item);
            }
        }
        return Collections.unmodifiableList(next);
    }

    private static List<TradeItem> withListing(List<TradeItem> source, UUID id, TradeItem updated) {
        List<TradeItem> next = new ArrayList<>(source.size() + 1);
        boolean found = false;
        for (TradeItem item : source) {
            if (item.id.equals(id)) {
                next.add(updated);
                found = true;
            } else {
                next.add(item);
            }
        }
        if (!found) {
            next.add(updated);
        }
        return Collections.unmodifiableList(next);
    }
}
