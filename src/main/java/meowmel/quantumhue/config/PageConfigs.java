package meowmel.quantumhue.config;

import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.mail.MailConfig;
import meowmel.quantumhue.mail.MailService;
import meowmel.quantumhue.market.ShopConfig;
import meowmel.quantumhue.notice.RemoteTextFetcher;
import meowmel.quantumhue.notice.VersionConfig;
import meowmel.quantumhue.notice.VersionState;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商店与整合包版本的内存配置仓库。
 *
 * <p>配置内容不再来自本地 json 文件：商店与整合包版本完全由外部通过
 * {@code meowmel.quantumhue.api.QuantumHuePageApi} 对外开放 API 注册进来。
 * 商店采用「加载期记录、加载完成期构建」的增量模型：各模组在加载阶段调用
 * {@link #addCategory}/{@link #addItem}/{@link #addBuyPool}/{@link #addSellPool} 只写进记录表，
 * 到 {@code FMLLoadCompleteEvent} 由 {@link #buildShopFromRegistry()} 统一落进 {@link #shop}；
 * 这样多个模组往同一分类加商品时不必关心彼此的加载顺序（按分类 id 合并）。
 * 未注册时商店为空目录；唯一保留的代码默认值是 {@link VersionConfig} 的版本号 {@code 1.0.0}。
 *
 * <p>构建时会做一轮结构校验：补齐缺失的分类/商品 id、把数值夹到合法区间，
 * 因此 API 侧只需要填业务内容，不必担心越界值。
 *
 * <p>公告的远程地址、超时、镜像、本机代理端口不在这里，只有一份：{@code quantumhue.cfg} 的
 * {@code Notice} 段（{@code QuantumHueConfig.notice}），使用方直接读它；
 * 页签固定为更新日志/公告/社群服务/邮件，不可配置。邮件参数仍内置在 {@link MailConfig}。
 *
 * <p>两侧都会加载：客户端用货币名等展示参数，服务端用商品目录、订单池与邮件端点。
 */
public final class PageConfigs {

    private static volatile File directory;
    private static volatile ShopConfig shop = new ShopConfig();
    private static volatile VersionConfig version = new VersionConfig();
    /** 内置参数：不读磁盘，恒为 {@link MailConfig} 里的默认值。 */
    private static volatile MailConfig mail = new MailConfig();
    /** 完整重载计数；客户端内存视图用它判断要不要强制重新拉取。 */
    private static volatile long reloadEpoch;

    /** 分类注册记录：categoryId -> 槽位，保留首次出现顺序，并按 id 跨模组合并。 */
    private static final LinkedHashMap<String, CategorySlot> pendingCategories = new LinkedHashMap<>();
    /** 系统收购订单池的注册记录。 */
    private static final List<ShopConfig.PoolEntry> pendingBuyPool = new ArrayList<>();
    /** 系统出售订单池的注册记录。 */
    private static final List<ShopConfig.PoolEntry> pendingSellPool = new ArrayList<>();
    /** 商店是否已据记录表构建过；构建后的晚到注册会据此即时重建。 */
    private static volatile boolean shopBuilt;

    private PageConfigs() {}

    /**
     * 由 {@code FMLPreInitializationEvent} 调用。
     *
     * <p>不再读取任何 json，这里只记录配置目录位置（供 {@code theme.json} 等客户端文件定位）。
     */
    public static void init(File configDir) {
        if (configDir != null) {
            directory = new File(configDir, Tags.MOD_ID);
        }
    }

    /** 当前生效的商店配置；外部未注册时为空目录。 */
    public static ShopConfig shop() {
        return shop;
    }

    /** 当前整合包版本；外部未注册时为内置默认 {@code 1.0.0}。 */
    public static VersionConfig version() {
        return version;
    }

    public static MailConfig mail() {
        return mail;
    }

    /** 每次完整 {@link #reload()} 自增；客户端内存视图据此失效。 */
    public static long reloadEpoch() {
        return reloadEpoch;
    }

    /** 配置文件的目录，未初始化时为 {@code null}。 */
    public static File directory() {
        return directory;
    }

    // ================================================================ 增量注册（加载期只记录，加载完成期构建）

    /**
     * 在加载初始阶段实例化商店：把 {@link #shop} 置为参数不变的默认空实例，供加载完成前的只读方回退。
     * 由 {@code FMLPreInitializationEvent} 调用；不记录任何内容，也不清空记录表，
     * 因此不受模组加载顺序影响（先于本模组注册的其它模组不会被清掉）。
     */
    public static synchronized void prepareShop() {
        if (!shopBuilt) {
            shop = new ShopConfig();
        }
    }

    /**
     * 记录一个商店分类：加载阶段只进记录表，不做实际添加。同 id 分类按合并处理，
     * 晚到的 {@code name}/{@code icon} 只补空、不覆盖已有的非空值，所以 A 建分类、B 补商品谁先谁后都行。
     */
    public static synchronized void addCategory(String id, String name, String icon, int iconMeta) {
        CategorySlot slot = slotFor(id);
        if (!isBlank(name)) {
            slot.name = name.trim();
        }
        if (!isBlank(icon)) {
            slot.icon = icon.trim();
            slot.iconMeta = iconMeta;
        }
        if (shopBuilt) {
            buildShopFromRegistry();
        }
    }

    /**
     * 记录一件商品，归入 {@code categoryId} 对应分类：加载阶段只进记录表。
     * 分类此刻还不存在也没关系，先占位，稍后由其它模组的 {@link #addCategory} 补全，构建时按 id 归并。
     */
    public static synchronized void addItem(String categoryId, ShopConfig.Item item) {
        if (item == null) {
            return;
        }
        slotFor(categoryId).items.add(item);
        if (shopBuilt) {
            buildShopFromRegistry();
        }
    }

    /** 记录一条系统收购订单池条目：加载阶段只进记录表。 */
    public static synchronized void addBuyPool(ShopConfig.PoolEntry entry) {
        if (entry == null) {
            return;
        }
        pendingBuyPool.add(entry);
        if (shopBuilt) {
            buildShopFromRegistry();
        }
    }

    /** 记录一条系统出售订单池条目：加载阶段只进记录表。 */
    public static synchronized void addSellPool(ShopConfig.PoolEntry entry) {
        if (entry == null) {
            return;
        }
        pendingSellPool.add(entry);
        if (shopBuilt) {
            buildShopFromRegistry();
        }
    }

    /**
     * 据记录表构建商店：在 {@code FMLLoadCompleteEvent}（游戏加载完成阶段）调用，此时所有模组
     * 加载期的注册都已进入记录表，统一落进 {@link #shop} 并做结构校验。
     *
     * <p>记录表构建后不清空，所以加载完成后的晚到注册（{@link #addCategory} 等）会据此即时重建；
     * 对同一记录集，重建是幂等且顺序稳定的（分类/商品都保留首次出现顺序）。
     */
    public static synchronized void buildShopFromRegistry() {
        ShopConfig config = new ShopConfig();
        for (Map.Entry<String, CategorySlot> entry : pendingCategories.entrySet()) {
            CategorySlot slot = entry.getValue();
            ShopConfig.Category category = new ShopConfig.Category(entry.getKey(), slot.name, slot.icon);
            category.iconMeta = slot.iconMeta;
            category.items.addAll(slot.items);
            config.systemShop.categories.add(category);
        }
        config.market.buyPool.addAll(pendingBuyPool);
        config.market.sellPool.addAll(pendingSellPool);
        normalize(config);
        shop = config;
        shopBuilt = true;
    }

    /** 取（或按需创建）某分类的注册槽位；空白 id 归到空串槽位，构建时由校验器补出分类 id。 */
    private static CategorySlot slotFor(String id) {
        String key = id == null ? "" : id.trim();
        CategorySlot slot = pendingCategories.get(key);
        if (slot == null) {
            slot = new CategorySlot();
            pendingCategories.put(key, slot);
        }
        return slot;
    }

    /** 分类注册槽位：攒下该分类的显示信息与商品，等加载完成时统一构建成 {@link ShopConfig.Category}。 */
    private static final class CategorySlot {
        String name = "";
        String icon = "";
        int iconMeta = 0;
        final List<ShopConfig.Item> items = new ArrayList<>();
    }

    /**
     * 注册整合包版本号：只改版本号，{@code null} 按空串处理（表示版本未知）。
     *
     * <p>这是整合包版本的唯一写入来路；读取统一走 {@link #version()}，不再反射探测其它模组。
     */
    public static synchronized void setPackVersion(String versionString) {
        VersionConfig current = new VersionConfig();
        current.version = versionString;
        normalize(current);
        version = current;
    }

    // ================================================================ 刷新

    /** 丢弃远程响应与版本视图；配置已不走磁盘，这里不再重读任何文件。 */
    public static synchronized void reload() {
        RemoteTextFetcher.invalidateAll();
        VersionState.reset();
        MailService.invalidate();
        reloadEpoch++;
    }

    // ================================================================ 校验

    private static void normalize(ShopConfig config) {
        if (config == null) {
            return;
        }
        if (config.market == null) {
            config.market = new ShopConfig.Market();
        }
        ShopConfig.Market market = config.market;
        market.playerListingHours = clamp(market.playerListingHours, 1, 24 * 365);
        market.playerOrderHours = clamp(market.playerOrderHours, 1, 24 * 365);
        market.systemOrderHours = clamp(market.systemOrderHours, 1, 24 * 365);
        market.orderIntervalMinMinutes = clamp(market.orderIntervalMinMinutes, 1, 60 * 24 * 7);
        market.orderIntervalMaxMinutes = clamp(market.orderIntervalMaxMinutes,
                market.orderIntervalMinMinutes, 60 * 24 * 7);
        market.orderGoodsMin = clamp(market.orderGoodsMin, 1, 64);
        market.orderGoodsMax = clamp(market.orderGoodsMax, market.orderGoodsMin, 64);
        market.buyPool = normalizePool(market.buyPool);
        market.sellPool = normalizePool(market.sellPool);

        if (config.systemShop == null) {
            config.systemShop = new ShopConfig.SystemShop();
        }
        config.systemShop.categories = normalizeCategories(config.systemShop.categories);
    }

    private static List<ShopConfig.PoolEntry> normalizePool(List<ShopConfig.PoolEntry> pool) {
        List<ShopConfig.PoolEntry> result = new ArrayList<>();
        if (pool == null) {
            return result;
        }
        for (ShopConfig.PoolEntry entry : pool) {
            if (entry == null || isBlank(entry.item)) {
                continue;
            }
            entry.minCount = clamp(entry.minCount, 1, 64);
            entry.maxCount = clamp(entry.maxCount, entry.minCount, 64);
            entry.price = Math.max(0L, entry.price);
            result.add(entry);
        }
        return result;
    }

    private static List<ShopConfig.Category> normalizeCategories(List<ShopConfig.Category> categories) {
        List<ShopConfig.Category> result = new ArrayList<>();
        if (categories == null) {
            return result;
        }
        Set<String> categoryIds = new HashSet<>();
        for (ShopConfig.Category category : categories) {
            if (category == null) {
                continue;
            }
            if (isBlank(category.name)) {
                category.name = "分类 " + (result.size() + 1);
            }
            category.id = uniqueId(categoryIds, category.id, "category");
            category.items = normalizeItems(category.items);
            result.add(category);
        }
        return result;
    }

    private static List<ShopConfig.Item> normalizeItems(List<ShopConfig.Item> items) {
        List<ShopConfig.Item> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        Set<String> ids = new HashSet<>();
        for (ShopConfig.Item item : items) {
            if (item == null || isBlank(item.item)) {
                continue;
            }
            item.meta = Math.max(0, item.meta);
            item.count = clamp(item.count, 1, 64 * 64);
            item.price = Math.max(0L, item.price);
            item.stock = item.stock < 0 ? -1 : item.stock;
            String fallback = item.item + "#" + item.meta;
            item.id = uniqueId(ids, item.id, fallback);
            result.add(item);
        }
        return result;
    }

    /** 整合包版本只做去空白：内置默认 1.0.0，注册成空串就是“版本未知”。 */
    private static void normalize(VersionConfig config) {
        if (config == null) {
            return;
        }
        config.version = config.version == null ? "" : config.version.trim();
    }

    // ================================================================ 小工具

    private static String uniqueId(Set<String> used, String candidate, String fallback) {
        String base = candidate;
        if (base == null || base.trim().isEmpty()) {
            base = fallback;
        }
        base = base.trim();
        String id = base;
        int suffix = 2;
        while (!used.add(id)) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
