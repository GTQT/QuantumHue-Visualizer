package meowmel.quantumhue.market;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 系统商店的“货架快照”。
 *
 * <p>货架内容来自 {@code shop.json} 的 {@code systemShop.categories}，剩余库存来自
 * {@link ShopDatabase} 的售出记录。服务端每次请求都会重建一次快照再发给客户端，所以客户端不需要
 * 读配置，也不会出现“本地配置和服务端不一致，买了服务端没有的东西”。
 */
public final class SystemShopCatalog {

    public static final class Entry {
        public final String categoryId;
        public final String id;
        public final String displayName;
        public final ItemStack stack;
        public final long price;
        public final int count;
        /** -1 表示无限库存。 */
        public final int stock;

        Entry(String categoryId, String id, String displayName, ItemStack stack,
              long price, int count, int stock) {
            this.categoryId = categoryId;
            this.id = id;
            this.displayName = displayName;
            this.stack = stack;
            this.price = price;
            this.count = count;
            this.stock = stock;
        }

        public boolean soldOut() {
            return stock == 0;
        }

        /** 展示用单价文本。 */
        public String priceText() {
            return String.format("§7单价 §e%s §8/ 每份 %s 个", price, count);
        }
    }

    public static final class Category {
        public final String id;
        public final String name;
        public final ItemStack icon;
        public final List<Entry> entries;

        Category(String id, String name, ItemStack icon, List<Entry> entries) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.entries = Collections.unmodifiableList(entries);
        }
    }

    private final List<Category> categories;

    public SystemShopCatalog(List<Category> categories) {
        this.categories = Collections.unmodifiableList(categories);
    }

    public static SystemShopCatalog empty() {
        return new SystemShopCatalog(new ArrayList<>());
    }

    public List<Category> categories() {
        return categories;
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }

    public Category category(String categoryId) {
        for (Category category : categories) {
            if (category.id.equals(categoryId)) {
                return category;
            }
        }
        return null;
    }

    public Entry find(String categoryId, String itemId) {
        Category category = category(categoryId);
        if (category == null) {
            return null;
        }
        for (Entry entry : category.entries) {
            if (entry.id.equals(itemId)) {
                return entry;
            }
        }
        return null;
    }

    // ================================================================ 构建

    /** 按当前配置 + 存档里的售出记录重建货架。 */
    public static SystemShopCatalog build(ShopDatabase database) {
        ShopConfig.SystemShop config;
        try {
            config = PageConfigs.shop().systemShop;
        } catch (Throwable ignored) {
            return empty();
        }
        if (config == null || !config.enabled) {
            return empty();
        }
        List<Category> categories = new ArrayList<>();
        for (ShopConfig.Category categoryConfig : config.categories) {
            List<Entry> entries = new ArrayList<>();
            ItemStack icon = resolve(categoryConfig.icon, categoryConfig.iconMeta, 1);
            for (ShopConfig.Item item : categoryConfig.items) {
                ItemStack stack = resolve(item.item, item.meta, 1);
                if (stack.isEmpty()) {
                    QuantumHue.LOGGER.warn("[Shop] 系统商店配置里有未知物品: {}:{}", item.item, item.meta);
                    continue;
                }
                if (icon.isEmpty()) {
                    icon = stack;
                }
                String displayName = item.name == null || item.name.trim().isEmpty()
                        ? stack.getDisplayName()
                        : item.name.trim();
                String stockKey = categoryConfig.id + "/" + item.id;
                int stock = database == null ? item.stock : database.getSystemStock(stockKey, item.stock);
                ItemStack display = stack.copy();
                display.setCount(Math.max(1, Math.min(item.count, Math.max(1, stack.getMaxStackSize()))));
                entries.add(new Entry(categoryConfig.id, item.id, displayName, display,
                        item.price, item.count, stock));
            }
            if (entries.isEmpty()) {
                continue;
            }
            if (icon.isEmpty()) {
                icon = entries.get(0).stack;
            }
            categories.add(new Category(categoryConfig.id, categoryConfig.name, icon, entries));
        }
        return new SystemShopCatalog(categories);
    }

    /** 库存键，服务端扣库存与客户端展示必须用同一个。 */
    public static String stockKey(String categoryId, String itemId) {
        return categoryId + "/" + itemId;
    }

    private static ItemStack resolve(String registryName, int meta, int count) {
        if (registryName == null || registryName.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation key = new ResourceLocation(registryName.trim());
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, count), Math.max(0, meta));
        } catch (Throwable invalid) {
            return ItemStack.EMPTY;
        }
    }

    // ================================================================ 网络

    public void write(ByteBuf buf) {
        buf.writeInt(categories.size());
        for (Category category : categories) {
            ByteBufUtils.writeUTF8String(buf, category.id);
            ByteBufUtils.writeUTF8String(buf, category.name);
            ByteBufUtils.writeItemStack(buf, category.icon);
            buf.writeInt(category.entries.size());
            for (Entry entry : category.entries) {
                ByteBufUtils.writeUTF8String(buf, entry.id);
                ByteBufUtils.writeUTF8String(buf, entry.displayName);
                ByteBufUtils.writeItemStack(buf, entry.stack);
                buf.writeLong(entry.price);
                buf.writeInt(entry.count);
                buf.writeInt(entry.stock);
            }
        }
    }

    public static SystemShopCatalog read(ByteBuf buf) {
        int categoryCount = buf.readInt();
        if (categoryCount < 0 || categoryCount > 512) {
            return empty();
        }
        List<Category> categories = new ArrayList<>(categoryCount);
        for (int c = 0; c < categoryCount; c++) {
            String categoryId = ByteBufUtils.readUTF8String(buf);
            String categoryName = ByteBufUtils.readUTF8String(buf);
            ItemStack icon = ByteBufUtils.readItemStack(buf);
            int entryCount = buf.readInt();
            if (entryCount < 0 || entryCount > 4096) {
                return empty();
            }
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int e = 0; e < entryCount; e++) {
                String entryId = ByteBufUtils.readUTF8String(buf);
                String displayName = ByteBufUtils.readUTF8String(buf);
                ItemStack stack = ByteBufUtils.readItemStack(buf);
                long price = buf.readLong();
                int count = buf.readInt();
                int stock = buf.readInt();
                entries.add(new Entry(categoryId, entryId, displayName, stack, price, count, stock));
            }
            categories.add(new Category(categoryId, categoryName, icon, entries));
        }
        return new SystemShopCatalog(categories);
    }
}
