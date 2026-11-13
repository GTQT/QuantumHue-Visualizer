package meowmel.quantumhue.api;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.lottery.LotteryConfig;
import meowmel.quantumhue.market.ShopConfig;
import meowmel.quantumhue.notice.VersionConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * QuantumHue 页面系统的对外开放 API：整合包 / 前置模组用它注册商店内容与整合包版本。
 *
 * <p>商店与整合包版本都不再读写本地 json 文件，运营内容完全从这里进。
 *
 * <p>商店是<b>增量注册</b>模型，不是一次性整棵替换：
 * <ol>
 *   <li>QuantumHue 在游戏加载的初始阶段（{@code FMLPreInitializationEvent}）实例化一个参数不变的默认商店；</li>
 *   <li>各模组在各自的加载阶段用 {@link #addShopCategory} / {@link #addShopItem} /
 *       {@link #addMarketBuyPool} / {@link #addMarketSellPool} 往商店里<b>添加</b>分类与商品——
 *       此时只写进记录表，不实际改动生效配置；</li>
 *   <li>游戏加载完成阶段（{@code FMLLoadCompleteEvent}）由 QuantumHue 从记录表统一构建商店。</li>
 * </ol>
 * 因为是「先记录、后构建」，所以多个模组往同一分类加东西时不必关心彼此的加载顺序：
 * 例如 A 模组 {@code addShopCategory("ore", ...)} 建分类并加铁矿，B 模组 {@code addShopItem("ore", ...)}
 * 加铜矿——无论 A、B 谁先加载，同名分类 {@code "ore"} 最终都会合并成一条。
 *
 * <p>整合包版本只有一个来源，就是 {@link #registerPackVersion(String)} 注册的值（未注册时为内置的 {@code 1.0.0}）；
 * 核心模组（如 GTQTCore）会主动把自己的 {@code VERSION} 注册进来。
 *
 * <p>示例：
 * <pre>{@code
 * // A 模组：建分类并加商品
 * QuantumHuePageApi.addShopCategory("building", "建筑方块", "minecraft:stone");
 * QuantumHuePageApi.addShopItem("building", "minecraft:stone", 0, "", 64, 16, -1);
 * // B 模组：往同一个分类里补商品，不用管 A 是否已加载
 * QuantumHuePageApi.addShopItem("building", "minecraft:planks", 0, "", 64, 16, -1);
 * // 系统订单池
 * QuantumHuePageApi.addMarketBuyPool(new ShopConfig.PoolEntry("minecraft:planks", 0, 16, 64, 1));
 * // 整合包版本
 * QuantumHuePageApi.registerPackVersion("1.9.0");
 * }</pre>
 */
public final class QuantumHuePageApi {

    private QuantumHuePageApi() {}

    // ================================================================ 商店（增量注册）

    /**
     * 添加一个商店分类：加载阶段只记录，游戏加载完成时统一构建。
     *
     * <p>同 {@code id} 的分类会合并，后到的 {@code name}/{@code icon} 只补空、不覆盖已有非空值，
     * 所以建分类和往分类加商品可以由不同模组分别完成，且不依赖彼此的加载顺序。
     * {@code id} 留空时会在构建时自动生成。
     */
    public static void addShopCategory(String id, String name, String icon) {
        PageConfigs.addCategory(id, name, icon, 0);
    }

    /** 同上，额外指定图标 meta。 */
    public static void addShopCategory(String id, String name, String icon, int iconMeta) {
        PageConfigs.addCategory(id, name, icon, iconMeta);
    }

    /**
     * 往指定分类添加一件商品：加载阶段只记录，游戏加载完成时统一构建。
     *
     * <p>{@code categoryId} 对应的分类此刻可以还没被 {@link #addShopCategory} 建出来——会先占位，
     * 构建时按 id 归并，因此不依赖建分类与加商品两者的先后顺序。
     */
    public static void addShopItem(String categoryId, ShopConfig.Item item) {
        PageConfigs.addItem(categoryId, item);
    }

    /**
     * 便捷重载：物品注册名、meta、显示名（可留空用物品自身名字）、每份数量、每份单价、库存（-1 无限）。
     */
    public static void addShopItem(String categoryId, String item, int meta, String name, int count, long price, int stock) {
        PageConfigs.addItem(categoryId, new ShopConfig.Item(item, meta, name, count, price, stock));
    }

    /**
     * 直接用 {@link ItemStack} 往指定分类添加一件商品：注册名、meta、每份数量都从堆栈里取，
     * 显示名留空（用物品自身名字）。加载阶段同样只记录，游戏加载完成时统一构建。
     *
     * <p>调用时物品需已完成注册（在 {@code init} 或更晚阶段调用即可）；若要在 {@code preInit}
     * 就加入尚未注册的物品，请改用上面的 item+meta 字符串重载。
     */
    public static void addShopItem(String categoryId, ItemStack stack, long price, int stock) {
        addShopItem(categoryId, stack, "", price, stock);
    }

    /** 同上，额外指定显示名覆盖（留空用物品自身名字）。 */
    public static void addShopItem(String categoryId, ItemStack stack, String displayName, long price, int stock) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        ResourceLocation key = stack.getItem().getRegistryName();
        if (key == null) {
            return; // 物品尚未注册：与字符串重载二选一，避免落进一条空 item
        }
        ShopConfig.Item item = new ShopConfig.Item(key.toString(), stack.getItemDamage(),
                displayName == null ? "" : displayName, stack.getCount(), price, stock);
        PageConfigs.addItem(categoryId, item);
    }

    /** 添加一条系统收购订单池条目（系统从玩家手里回收的物品）：加载阶段只记录。 */
    public static void addMarketBuyPool(ShopConfig.PoolEntry entry) {
        PageConfigs.addBuyPool(entry);
    }

    /** 添加一条系统出售订单池条目（系统向玩家出售的物品）：加载阶段只记录。 */
    public static void addMarketSellPool(ShopConfig.PoolEntry entry) {
        PageConfigs.addSellPool(entry);
    }

    /** 当前生效的商店配置：加载完成前是默认空实例，加载完成后为据记录表构建的结果。 */
    public static ShopConfig getShopConfig() {
        return PageConfigs.shop();
    }

    // ================================================================ 抽奖（老虎机）

    /**
     * 注册一个抽奖符号（完整对象重载）。
     *
     * <p>符号的 {@code weight} 是每个转轮的相对抽取权重，{@code three/four/five} 分别是左起连续
     * 3/4/5 个相同符号时的奖品；某一档留 {@code null} 会回退到更短一档。奖品可以同时包含货币与物品：
     * <pre>{@code
     * LotteryConfig.Symbol diamond = new LotteryConfig.Symbol(
     *         "diamond", "钻石", "minecraft:diamond", 0, 10,
     *         LotteryConfig.Payout.of(3000).withItem("minecraft:diamond", 0, 1),
     *         LotteryConfig.Payout.of(30000).withItem("minecraft:diamond", 0, 8),
     *         LotteryConfig.Payout.of(300000));
     * QuantumHuePageApi.addLotterySymbol(diamond);
     * }</pre>
     *
     * <p>加载阶段只记录，游戏加载完成时统一构建；注册后的对象会被复制，再修改它不会影响奖池。
     */
    public static void addLotterySymbol(LotteryConfig.Symbol symbol) {
        PageConfigs.addLotterySymbol(symbol);
    }

    /** 便捷重载：注册一个只赔货币的符号。 */
    public static void addLotterySymbol(String id, String name, String icon, int iconMeta, int weight,
                                        long win3, long win4, long win5) {
        PageConfigs.addLotterySymbol(new LotteryConfig.Symbol(id, name, icon, iconMeta, weight,
                LotteryConfig.Payout.of(win3), LotteryConfig.Payout.of(win4),
                LotteryConfig.Payout.of(win5)));
    }

    /**
     * 注册一个可切换的额外奖池。{@code id} 用于抽奖请求与文档定位；重复使用同一个 id 会更新该奖池的
     * 名称/价格。{@code default} 保留给默认奖池，不要用作额外奖池 id。
     * {@code baseSpinCost} 是该奖池每行基础价，抽 1/2/3 行分别扣 1/2/3 倍。
     *
     * <p>额外奖池与默认奖池互不影响；默认奖池的符号仍用无 poolId 的
     * {@link #addLotterySymbol(LotteryConfig.Symbol)} 注册。
     */
    public static void addLotteryPool(String id, String name, String icon, int iconMeta,
                                      long baseSpinCost) {
        PageConfigs.addLotteryPool(id, name, icon, iconMeta, baseSpinCost);
    }

    /** 便捷重载：奖池图标使用默认 meta 0。 */
    public static void addLotteryPool(String id, String name, String icon, long baseSpinCost) {
        PageConfigs.addLotteryPool(id, name, icon, 0, baseSpinCost);
    }

    /** 往指定额外奖池追加一个符号（完整对象重载）。 */
    public static void addLotterySymbol(String poolId, LotteryConfig.Symbol symbol) {
        PageConfigs.addLotterySymbol(poolId, symbol);
    }

    /** 往指定额外奖池追加一个只赔货币的符号。 */
    public static void addLotterySymbol(String poolId, String id, String name, String icon,
                                        int iconMeta, int weight, long win3, long win4, long win5) {
        PageConfigs.addLotterySymbol(poolId, new LotteryConfig.Symbol(id, name, icon, iconMeta,
                weight, LotteryConfig.Payout.of(win3), LotteryConfig.Payout.of(win4),
                LotteryConfig.Payout.of(win5)));
    }

    /** 调整指定奖池的每行基础价；传空串或 {@code default} 时作用于默认奖池。 */
    public static void setLotteryCosts(String poolId, long baseSpinCost) {
        PageConfigs.setLotteryCosts(poolId, baseSpinCost);
    }

    /** 开关指定额外奖池；总开关仍由 {@link #setLotteryEnabled(boolean)} 控制。 */
    public static void setLotteryPoolEnabled(String poolId, boolean enabled) {
        PageConfigs.setLotteryPoolEnabled(poolId, enabled);
    }

    /** 清空指定奖池的符号；传空串或 {@code default} 时作用于默认奖池。 */
    public static void clearLotterySymbols(String poolId) {
        PageConfigs.clearLotterySymbols(poolId);
    }

    /** 删除全部额外奖池；默认奖池不受影响。 */
    public static void clearLotteryPools() {
        PageConfigs.clearLotteryPools();
    }

    /**
     * 调整抽奖价格：{@code baseSpinCost} 是每行价格，实际扣费 = 基础价 × 本次抽取行数
     * （例如基础价 100 时，抽 1/2/3 行分别为 100/200/300）。
     */
    public static void setLotteryCosts(long baseSpinCost) {
        PageConfigs.setLotteryCosts(baseSpinCost);
    }

    /** 开关抽奖；关闭后界面仍能打开，但服务端会拒绝抽奖。 */
    public static void setLotteryEnabled(boolean enabled) {
        PageConfigs.setLotteryEnabled(enabled);
    }

    /** 清空奖池（同时停用空奖池时的内置演示奖池）。 */
    public static void clearLotterySymbols() {
        PageConfigs.clearLotterySymbols();
    }

    /** 当前生效的抽奖配置；加载完成前为空奖池。 */
    public static LotteryConfig getLotteryConfig() {
        return PageConfigs.lottery();
    }

    // ================================================================ 整合包版本

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

    /** 当前生效的整合包版本配置（完全来自 {@link #registerPackVersion(String)} 的注册值，未注册时为内置默认）。 */
    public static VersionConfig getVersionConfig() {
        return PageConfigs.version();
    }
}
