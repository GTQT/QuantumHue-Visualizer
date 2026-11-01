package meowmel.quantumhue.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店配置模型，完全由外部通过对外开放 API 注册（见 {@code QuantumHuePageApi}），不再落盘。
 *
 * <p>本类只保留结构和中性的数值初始值，<b>不带任何预置商品、订单池内容</b>：
 * 未注册时商店就是空目录、系统不投放订单。运营内容属于整合包，应该由整合包代码经 API 提供。
 *
 * <p>字段保持公开是为了让注册方直接构造整棵配置树；节奏类数值会被
 * {@code PageConfigs} 在校验时夹到合法区间，缺失的结构会自动补空对象。
 */
public class ShopConfig {

    /** 总开关：关闭后 /market 与系统商店页都不可用。 */
    public boolean enabled = true;

    /** 界面上显示的货币名称；留空时界面回退到“货币”二字。 */
    public String currencyName = "";

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

        /** 系统收购池：系统从玩家手里收购这些物品；默认为空（不投放收购单）。 */
        public List<PoolEntry> buyPool = new ArrayList<>();

        /** 系统出售池：系统向玩家出售这些物品；默认为空（不出售单）。 */
        public List<PoolEntry> sellPool = new ArrayList<>();
    }

    /** 系统订单池里的一条：物品、随机数量区间、基础单价。 */
    public static class PoolEntry {
        public String item = "";
        public int meta = 0;
        public int minCount = 1;
        public int maxCount = 1;
        public long price = 1;

        public PoolEntry() {}

        /** 便捷构造：物品注册名、meta、数量区间、基础单价。 */
        public PoolEntry(String item, int meta, int minCount, int maxCount, long price) {
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

        /** 可自由增删的分类；每个分类里是可直接购买的商品。默认为空目录。 */
        public List<Category> categories = new ArrayList<>();
    }

    public static class Category {
        /** 分类稳定 id，留空由校验器生成。 */
        public String id = "";
        public String name = "";
        /** 分类图标的物品注册名，留空则使用该分类第一件商品。 */
        public String icon = "";
        public int iconMeta = 0;
        public List<Item> items = new ArrayList<>();

        public Category() {}

        /** 便捷构造：id（可留空自动生成）、显示名、图标物品注册名。 */
        public Category(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    public static class Item {
        /** 商品稳定 id（客户端购买时回传），留空由校验器生成。 */
        public String id = "";
        public String item = "";
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

        /** 便捷构造：物品注册名、meta、显示名（可留空）、每份数量、每份单价、库存（-1 无限）。 */
        public Item(String item, int meta, String name, int count, long price, int stock) {
            this.item = item;
            this.meta = meta;
            this.name = name;
            this.count = count;
            this.price = price;
            this.stock = stock;
        }
    }
}
