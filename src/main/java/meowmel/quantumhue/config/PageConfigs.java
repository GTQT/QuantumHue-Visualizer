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
import java.util.List;
import java.util.Set;

/**
 * 商店与整合包版本的内存配置仓库。
 *
 * <p>配置内容不再来自本地 json 文件：商店运营参数与整合包版本号完全由外部通过
 * {@code meowmel.quantumhue.api.QuantumHuePageApi} 对外开放 API 注册进来
 * （入口在调用 {@link #init(File)} 之前或之后注册都可以，取值永远是懒读最新）。
 * 未注册时商店为空目录；唯一保留的代码默认值是 {@link VersionConfig} 的版本号 {@code 1.0.0}。
 *
 * <p>注册进来的商店配置会做一轮结构校验：补齐缺失的分类/商品 id、把数值夹到合法区间，
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

    // ================================================================ API 注册入口

    /**
     * 注册商店配置：整份替换当前生效的配置，{@code null} 视为回退到空目录。
     *
     * <p>供 {@code meowmel.quantumhue.api.QuantumHuePageApi} 调用；注册时会做一轮结构校验。
     */
    public static synchronized void setShop(ShopConfig config) {
        shop = config == null ? new ShopConfig() : config;
        normalize(shop);
    }

    /**
     * 注册整合包版本号：只改版本号，{@code null} 按空串处理（表示版本未知）。
     *
     * <p>装了 GTQTCore 时当前版本以它的 {@code VERSION} 为准，这里注册的值只是兜底。
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
