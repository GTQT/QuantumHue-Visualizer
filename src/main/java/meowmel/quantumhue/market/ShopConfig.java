package meowmel.quantumhue.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店的 JSON 配置模型（{@code config/quantumhue/shop.json}）。
 *
 * <p>之所以把商品、订单池和投放节奏全部搬进 JSON：这些内容属于整合包运营参数，改价、加货、
 * 换分类都不该需要重新编译。字段保留默认值，Gson 反序列化时缺失的字段会自然落回默认值，
 * 所以旧配置文件在升级后仍然能读。
 *
 * <p>字段名保持英文：它们直接就是配置文件里的键，改名等于破坏玩家已有文件。
 */
public class ShopConfig {

    /** 配置里的自说明字段，不参与任何逻辑。 */
    public String _comment = "QuantumHue 商店配置：category.item 里的 item 为注册名，count 为每份数量，price 为每份单价，stock=-1 表示无限库存。";

    /** 总开关：关闭后 /market 与系统商店页都不可用。 */
    public boolean enabled = true;

    /** 界面上显示的货币名称。 */
    public String currencyName = "货币";

    public Market market = new Market();

    public SystemShop systemShop = new SystemShop();

    // ================================================================ 交易市场

    public static class Market {

        /** 玩家上架商品的存活时间（小时）。 */
        public int playerListingHours = 72;

        /** 玩家收购订单的存活时间（小时）。 */
        public int playerOrderHours = 72;

        /** 系统订单投放间隔（分钟，取区间随机值）。 */
        public int orderIntervalMinMinutes = 30;
        public int orderIntervalMaxMinutes = 60;

        /** 每次投放的货物种类数量（取区间随机值）。 */
        public int orderGoodsMin = 3;
        public int orderGoodsMax = 6;

        /** 系统订单存活时间（小时）。 */
        public int systemOrderHours = 2;

        /** 系统收购池：系统从玩家手里收购这些物品。 */
        public List<PoolEntry> buyPool = defaultBuyPool();

        /** 系统出售池：系统向玩家出售这些物品。 */
        public List<PoolEntry> sellPool = defaultSellPool();
    }

    /** 系统订单池里的一条：物品、随机数量区间、基础单价。 */
    public static class PoolEntry {
        public String item = "minecraft:stone";
        public int meta = 0;
        public int minCount = 1;
        public int maxCount = 1;
        public long price = 1;

        public PoolEntry() {}

        PoolEntry(String item, int meta, int minCount, int maxCount, long price) {
            this.item = item;
            this.meta = meta;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.price = price;
        }
    }

    // ================================================================ 系统商店

    public static class SystemShop {

        public boolean enabled = true;

        /** 玩家可自由增删的分类；每个分类里是可直接购买的商品。 */
        public List<Category> categories = defaultCategories();
    }

    public static class Category {
        public String id = "";
        public String name = "新分类";
        /** 分类图标的物品注册名，留空则使用该分类第一件商品。 */
        public String icon = "";
        public int iconMeta = 0;
        public List<Item> items = new ArrayList<>();

        public Category() {}

        Category(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    public static class Item {
        /** 商品稳定 id（客户端购买时回传），留空由加载器生成。 */
        public String id = "";
        public String item = "minecraft:stone";
        public int meta = 0;
        /** 显示名覆盖，留空用物品自身的名字。 */
        public String name = "";
        /** 每份包含的数量。 */
        public int count = 1;
        /** 每份单价。 */
        public long price = 1;
        /** 库存（份），-1 为无限。 */
        public int stock = -1;

        public Item() {}

        Item(String item, int meta, String name, int count, long price, int stock) {
            this.item = item;
            this.meta = meta;
            this.name = name;
            this.count = count;
            this.price = price;
            this.stock = stock;
        }
    }

    // ================================================================ 默认值

    private static List<Category> defaultCategories() {
        List<Category> categories = new ArrayList<>();

        Category building = new Category("building", "建筑方块", "minecraft:stone");
        building.items.add(new Item("minecraft:stone", 0, "", 64, 16, -1));
        building.items.add(new Item("minecraft:planks", 0, "", 64, 16, -1));
        building.items.add(new Item("minecraft:glass", 0, "", 64, 24, -1));
        building.items.add(new Item("minecraft:brick_block", 0, "", 64, 32, -1));
        building.items.add(new Item("minecraft:quartz_block", 0, "", 64, 48, -1));
        building.items.add(new Item("minecraft:glowstone", 0, "", 16, 32, -1));
        categories.add(building);

        Category industry = new Category("industry", "工业材料", "minecraft:iron_ingot");
        industry.items.add(new Item("minecraft:iron_ingot", 0, "", 16, 32, -1));
        industry.items.add(new Item("minecraft:gold_ingot", 0, "", 16, 48, -1));
        industry.items.add(new Item("minecraft:redstone", 0, "", 32, 24, -1));
        industry.items.add(new Item("minecraft:coal", 0, "", 32, 16, -1));
        industry.items.add(new Item("minecraft:dye", 4, "青金石", 16, 32, -1));
        industry.items.add(new Item("minecraft:diamond", 0, "", 4, 64, -1));
        categories.add(industry);

        Category food = new Category("food", "食物", "minecraft:bread");
        food.items.add(new Item("minecraft:bread", 0, "", 16, 16, -1));
        food.items.add(new Item("minecraft:apple", 0, "", 16, 12, -1));
        food.items.add(new Item("minecraft:cooked_beef", 0, "", 16, 24, -1));
        food.items.add(new Item("minecraft:carrot", 0, "", 16, 8, -1));
        food.items.add(new Item("minecraft:potato", 0, "", 16, 8, -1));
        categories.add(food);

        Category utility = new Category("utility", "实用道具", "minecraft:torch");
        utility.items.add(new Item("minecraft:torch", 0, "", 64, 8, -1));
        utility.items.add(new Item("minecraft:crafting_table", 0, "", 1, 8, -1));
        utility.items.add(new Item("minecraft:furnace", 0, "", 1, 16, -1));
        utility.items.add(new Item("minecraft:chest", 0, "", 1, 8, -1));
        utility.items.add(new Item("minecraft:bookshelf", 0, "", 1, 24, -1));
        utility.items.add(new Item("minecraft:ender_pearl", 0, "", 4, 48, -1));
        categories.add(utility);

        return categories;
    }

    private static List<PoolEntry> defaultBuyPool() {
        List<PoolEntry> pool = new ArrayList<>();
        pool.add(new PoolEntry("minecraft:planks", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:cobblestone", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:dirt", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:sand", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:gravel", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:log", 0, 8, 32, 1));
        pool.add(new PoolEntry("minecraft:wool", 0, 8, 32, 1));
        pool.add(new PoolEntry("minecraft:clay", 0, 8, 32, 1));
        pool.add(new PoolEntry("minecraft:netherrack", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:glowstone", 0, 4, 16, 1));
        pool.add(new PoolEntry("minecraft:obsidian", 0, 2, 8, 10));
        return pool;
    }

    private static List<PoolEntry> defaultSellPool() {
        List<PoolEntry> pool = new ArrayList<>();
        pool.add(new PoolEntry("minecraft:planks", 0, 16, 64, 2));
        pool.add(new PoolEntry("minecraft:cobblestone", 0, 16, 64, 2));
        pool.add(new PoolEntry("minecraft:stone", 0, 16, 64, 2));
        pool.add(new PoolEntry("minecraft:brick_block", 0, 8, 32, 3));
        pool.add(new PoolEntry("minecraft:glass", 0, 8, 32, 2));
        pool.add(new PoolEntry("minecraft:torch", 0, 16, 64, 1));
        pool.add(new PoolEntry("minecraft:crafting_table", 0, 1, 4, 15));
        pool.add(new PoolEntry("minecraft:furnace", 0, 1, 4, 20));
        pool.add(new PoolEntry("minecraft:chest", 0, 1, 4, 15));
        pool.add(new PoolEntry("minecraft:bookshelf", 0, 1, 4, 8));
        return pool;
    }
}
