package meowmel.quantumhue.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.mail.MailConfig;
import meowmel.quantumhue.mail.MailService;
import meowmel.quantumhue.market.ShopConfig;
import meowmel.quantumhue.notice.NoticeConfig;
import meowmel.quantumhue.notice.RemoteTextFetcher;
import meowmel.quantumhue.notice.VersionConfig;
import meowmel.quantumhue.notice.VersionState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@code config/quantumhue/*.json} 的加载入口。
 *
 * <p>商店和公告的运营内容都从这里读：商品分类、每类商品、系统订单池、公告页签、远程地址。
 * 首次运行会把带默认值的完整文件写到磁盘上，玩家照着改即可；之后的读取不会覆盖
 * 玩家文件（读失败也只是回退到内存默认值并打日志）。
 *
 * <p>{@code version.json} 只存当前<b>整合包</b>版本号，与模组自身的版本无关，也没有代码默认值。
 *
 * <p>公告地址、页签、邮件目录这些运营参数全部内置在 jar 里（{@link NoticeConfig} / {@link MailConfig}），
 * 不读 {@code notice.json} / {@code mail.json}，所以不会被玩家改本地文件影响。
 *
 * <p>两侧都会加载：客户端用公告与界面文案，服务端用商品目录、订单池与邮件端点。
 */
public final class PageConfigs {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Pages");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile File directory;
    private static volatile ShopConfig shop = new ShopConfig();
    /** 内置参数：不读磁盘，恒为 {@link NoticeConfig} 里的默认值。 */
    private static volatile NoticeConfig notice = new NoticeConfig();
    private static volatile VersionConfig version = new VersionConfig();
    /** 内置参数：不读磁盘，恒为 {@link MailConfig} 里的默认值。 */
    private static volatile MailConfig mail = new MailConfig();
    /** 完整重载计数；客户端内存视图用它判断要不要强制重新拉取。 */
    private static volatile long reloadEpoch;

    private PageConfigs() {}

    /** 由 {@code FMLPreInitializationEvent} 调用；重复调用只会重新加载。 */
    public static void init(File configDir) {
        if (configDir != null) {
            directory = new File(configDir, Tags.MOD_ID);
        }
        load();
    }

    public static ShopConfig shop() {
        return shop;
    }

    public static NoticeConfig notice() {
        return notice;
    }

    /** 当前整合包版本（{@code version.json}）。 */
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

    /**
     * 把带默认值（并已补齐分类/商品 id）的配置写到磁盘。
     *
     * <p>正式流程由 {@link #init(File)} 在文件缺失时自动调用；这里公开出来是为了在不开游戏的情况下
     * 也能生成一份可读的模板（开发工具 {@code tools} 与文档使用）。
     */
    public static void writeDefaultFiles(File configDir) {
        if (configDir == null) {
            return;
        }
        File dir = new File(configDir, Tags.MOD_ID);
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[Pages] 无法创建配置目录 {}", dir.getAbsolutePath());
            return;
        }
        ShopConfig shopDefaults = new ShopConfig();
        VersionConfig versionDefaults = new VersionConfig();
        normalize(shopDefaults);
        normalize(versionDefaults);
        write(new File(dir, "shop.json"), shopDefaults);
        write(new File(dir, "version.json"), versionDefaults);
    }

    /** 重新读盘；同时丢弃远程响应与版本视图，玩家改完配置后重新预取即可生效。 */
    public static synchronized void reload() {
        RemoteTextFetcher.invalidateAll();
        VersionState.reset();
        MailService.invalidate();
        load();
        reloadEpoch++;
    }

    /**
     * 只重读磁盘文件，不清远程缓存。
     *
     * <p>界面每次打开都会调用它：玩家改完 {@code shop.json} 再打开商店就能看到新价格，
     * 而不必执行命令；同时不会把已经拉到的远程公告清掉。
     */
    public static synchronized void reloadLocal() {
        load();
    }

    private static synchronized void load() {
        // 公告与邮件的运营参数内置在代码里，不读盘；这里只加载玩家可编辑的两份配置
        ShopConfig loadedShop = loadFile("shop.json", ShopConfig.class, new ShopConfig());
        VersionConfig loadedVersion = loadFile("version.json", VersionConfig.class, new VersionConfig());
        normalize(loadedShop);
        normalize(loadedVersion);
        shop = loadedShop;
        version = loadedVersion;
    }

    private static <T> T loadFile(String fileName, Class<T> type, T defaults) {
        File dir = directory;
        if (dir == null) {
            return defaults;
        }
        File file = new File(dir, fileName);
        try {
            if (!file.exists()) {
                if (!dir.exists() && !dir.mkdirs()) {
                    LOGGER.warn("[Pages] 无法创建配置目录 {}", dir.getAbsolutePath());
                }
                write(file, defaults);
                LOGGER.info("[Pages] 已生成默认配置 {}", file.getAbsolutePath());
                return defaults;
            }
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                T parsed = GSON.fromJson(reader, type);
                if (parsed == null) {
                    LOGGER.warn("[Pages] {} 内容为空，使用默认值", file.getName());
                    return defaults;
                }
                return parsed;
            }
        } catch (Exception failure) {
            LOGGER.error("[Pages] 读取 {} 失败，使用默认值", file.getName(), failure);
            return defaults;
        }
    }

    private static void write(File file, Object value) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        } catch (Exception failure) {
            LOGGER.error("[Pages] 写入默认配置 {} 失败", file.getName(), failure);
        }
    }

    // ================================================================ 校验

    private static void normalize(ShopConfig config) {
        if (config == null) {
            return;
        }
        if (config.currencyName == null || config.currencyName.trim().isEmpty()) {
            config.currencyName = "货币";
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

    /** 整合包版本只做去空白：没有代码默认值，留空就是“版本未知”。 */
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
