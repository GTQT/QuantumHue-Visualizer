package meowmel.quantumhue.api;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.market.ShopConfig;
import meowmel.quantumhue.notice.VersionConfig;

/**
 * QuantumHue 页面系统的对外开放 API：整合包 / 前置模组用它注册商店与版本配置。
 *
 * <p>商店配置（{@link ShopConfig}）与整合包版本（{@link VersionConfig}）都不再读写本地 json
 * 文件，运营内容完全从这里进。建议在各自的 {@code FMLPreInitializationEvent}（或更早）里
 * 整份注册；注册是整棵替换，后注册的覆盖先注册的。
 *
 * <p>商店默认是空目录（没有分类、没有订单池）；版本未注册时为内置的 {@code 1.0.0}，
 * 且装了 GTQTCore 时当前版本以它的 {@code VERSION} 为准，这里的注册值只是兜底。
 *
 * <p>示例：
 * <pre>{@code
 * ShopConfig shop = new ShopConfig();
 * shop.currencyName = "欧元";
 * ShopConfig.Category building = new ShopConfig.Category("building", "建筑方块", "minecraft:stone");
 * building.items.add(new ShopConfig.Item("minecraft:stone", 0, "", 64, 16, -1));
 * shop.systemShop.categories.add(building);
 * shop.market.buyPool.add(new ShopConfig.PoolEntry("minecraft:planks", 0, 16, 64, 1));
 * QuantumHuePageApi.registerShopConfig(shop);
 * QuantumHuePageApi.registerPackVersion("1.9.0");
 * }</pre>
 */
public final class QuantumHuePageApi {

    private QuantumHuePageApi() {}

    /**
     * 注册商店配置：整份替换当前生效的配置。
     *
     * <p>传 {@code null} 回退到空目录。id 留空的商品 / 分类会自动生成，越界的节奏参数会被夹到合法区间。
     */
    public static void registerShopConfig(ShopConfig config) {
        PageConfigs.setShop(config);
    }

    /** 当前生效的商店配置（可直接读取校验后的结果）。 */
    public static ShopConfig getShopConfig() {
        return PageConfigs.shop();
    }

    /**
     * 注册当前<b>整合包</b>版本号（与模组自身的版本无关）。
     *
     * <p>传空串表示“版本未知”：页面不显示版本号，也不做更新比较。
     */
    public static void registerPackVersion(String version) {
        PageConfigs.setPackVersion(version);
    }

    /** 以整份配置对象注册当前整合包版本；传 {@code null} 回退到内置默认 {@code 1.0.0}。 */
    public static void registerVersionConfig(VersionConfig config) {
        PageConfigs.setPackVersion(config == null ? VersionConfig.DEFAULT_VERSION : config.resolved());
    }

    /** 当前生效的整合包版本配置（GTQTCore 存在与否不影响这里读到的是兜底值）。 */
    public static VersionConfig getVersionConfig() {
        return PageConfigs.version();
    }
}
