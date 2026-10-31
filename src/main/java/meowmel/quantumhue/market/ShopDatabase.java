package meowmel.quantumhue.market;

import meowmel.quantumhue.QuantumHue;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 商店的服务端权威存档：在售商品、收购订单、系统商店已售数量。
 *
 * <p>客户端永远不直接读它（专用服务器上根本读不到），界面数据由网络包按页推送；
 * 这里只负责一致性与落盘。
 */
public class ShopDatabase extends WorldSavedData {

    private static final String DATA_NAME = "quantumhue_shop_data";

    /** 下架后仍允许取回的保留时长（小时），超过才真正删除。 */
    private static final int LISTING_RETRIEVE_GRACE_HOURS = 24 * 7;

    private final Map<UUID, TradeItem> listings = new ConcurrentHashMap<>();
    /**
     * 卖家 → 其商品 id。内层用 copy-on-write：遍历过程中会顺手剔除失效 id，
     * 普通 ArrayList 会抛 ConcurrentModificationException。
     */
    private final Map<UUID, List<UUID>> playerListings = new ConcurrentHashMap<>();
    private final List<BuyOrder> buyOrders = new CopyOnWriteArrayList<>();
    /**
     * 系统商店已售出的“份数”，键是 {@code categoryId/itemId}，用于让 JSON 里配置的有限库存真正消耗。
     */
    private final Map<String, Integer> systemSold = new ConcurrentHashMap<>();

    public ShopDatabase() {
        super(DATA_NAME);
    }

    public ShopDatabase(String name) {
        super(name);
    }

    public static ShopDatabase get(World world) {
        MapStorage storage = world.getMapStorage();
        ShopDatabase instance = null;
        if (storage != null) {
            instance = (ShopDatabase) storage.getOrLoadData(ShopDatabase.class, DATA_NAME);
        }
        if (instance == null) {
            instance = new ShopDatabase();
            if (storage != null) {
                storage.setData(DATA_NAME, instance);
            }
        }
        return instance;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        listings.clear();
        playerListings.clear();
        buyOrders.clear();
        systemSold.clear();

        NBTTagList listingsList = nbt.getTagList("listings", 10);
        for (NBTBase tag : listingsList) {
            TradeItem tradeItem = new TradeItem((NBTTagCompound) tag);
            if (!tradeItem.isExpired()) {
                listings.put(tradeItem.id, tradeItem);
                playerListings.computeIfAbsent(tradeItem.sellerUUID, key -> new CopyOnWriteArrayList<>())
                        .add(tradeItem.id);
            }
        }

        NBTTagList ordersList = nbt.getTagList("buyOrders", 10);
        for (NBTBase tag : ordersList) {
            BuyOrder order = new BuyOrder((NBTTagCompound) tag);
            if (!order.isCompleted() && !order.isExpired()) {
                buyOrders.add(order);
            }
        }

        NBTTagList soldList = nbt.getTagList("systemSold", 10);
        for (NBTBase tag : soldList) {
            NBTTagCompound entry = (NBTTagCompound) tag;
            String key = entry.getString("key");
            if (!key.isEmpty()) {
                systemSold.put(key, entry.getInteger("sold"));
            }
        }

        QuantumHue.LOGGER.info("[Shop] 加载商店数据：{} 个出售商品，{} 个收购订单，{} 条系统商店售出记录",
                listings.size(), buyOrders.size(), systemSold.size());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList listingsList = new NBTTagList();
        for (TradeItem item : listings.values()) {
            listingsList.appendTag(item.writeToNBT());
        }
        nbt.setTag("listings", listingsList);

        NBTTagList ordersList = new NBTTagList();
        for (BuyOrder order : buyOrders) {
            ordersList.appendTag(order.writeToNBT());
        }
        nbt.setTag("buyOrders", ordersList);

        NBTTagList soldList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : systemSold.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("key", entry.getKey());
            tag.setInteger("sold", entry.getValue());
            soldList.appendTag(tag);
        }
        nbt.setTag("systemSold", soldList);

        return nbt;
    }

    @Override
    public void markDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()
                && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            super.markDirty();
        }
    }

    // ================================================================ 在售商品

    public void addListing(TradeItem item) {
        listings.put(item.id, item);
        playerListings.computeIfAbsent(item.sellerUUID, key -> new CopyOnWriteArrayList<>()).add(item.id);
        markDirty();
    }

    public void removeListing(UUID listingId) {
        TradeItem item = listings.remove(listingId);
        if (item != null) {
            removeFromSellerIndex(item.sellerUUID, listingId);
            markDirty();
        }
    }

    private void removeFromSellerIndex(UUID sellerUUID, UUID listingId) {
        if (sellerUUID == null) {
            return;
        }
        playerListings.computeIfPresent(sellerUUID, (key, ids) -> {
            ids.remove(listingId);
            return ids.isEmpty() ? null : ids;
        });
    }

    public void updateQuantity(UUID listingId, int newQuantity) {
        TradeItem item = listings.get(listingId);
        if (item == null) {
            return;
        }
        item.quantity = newQuantity;
        if (newQuantity <= 0) {
            removeListing(listingId);
        } else {
            markDirty();
        }
    }

    public TradeItem getListing(UUID listingId) {
        return listings.get(listingId);
    }

    public List<TradeItem> getAllListings() {
        List<TradeItem> valid = new ArrayList<>();
        for (TradeItem item : listings.values()) {
            if (!item.isExpired()) {
                valid.add(item);
            }
        }
        return valid;
    }

    public List<TradeItem> searchListings(String searchTerm) {
        String needle = searchTerm.toLowerCase();
        List<TradeItem> result = new ArrayList<>();
        for (TradeItem item : listings.values()) {
            if (!item.isExpired() && matches(item.itemStack, needle)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 玩家的全部上架（含已过期，供取回）。
     *
     * <p>索引里可能残留已经不在 {@code listings} 中的 id，这里顺手丢掉，避免索引无限膨胀。
     */
    public List<TradeItem> getListingsBySeller(UUID sellerUUID) {
        List<UUID> ids = playerListings.get(sellerUUID);
        if (ids == null) {
            return new ArrayList<>();
        }
        List<TradeItem> result = new ArrayList<>(ids.size());
        List<UUID> stale = null;
        for (UUID id : ids) {
            TradeItem item = listings.get(id);
            if (item != null) {
                result.add(item);
            } else {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(id);
            }
        }
        if (stale != null) {
            final List<UUID> dead = stale;
            playerListings.computeIfPresent(sellerUUID, (key, current) -> {
                current.removeAll(dead);
                return current.isEmpty() ? null : current;
            });
        }
        return result;
    }

    // ================================================================ 收购订单

    public void addBuyOrder(BuyOrder order) {
        buyOrders.add(order);
        markDirty();
    }

    public void removeBuyOrder(UUID orderId) {
        buyOrders.removeIf(order -> order.id.equals(orderId));
        markDirty();
    }

    public void updateBuyOrderProgress(UUID orderId, int quantity) {
        for (BuyOrder order : buyOrders) {
            if (!order.id.equals(orderId)) {
                continue;
            }
            order.totalBought += quantity;
            if (order.isCompleted()) {
                removeBuyOrder(orderId);
            } else {
                markDirty();
            }
            return;
        }
    }

    public List<BuyOrder> getAllBuyOrders() {
        List<BuyOrder> valid = new ArrayList<>();
        for (BuyOrder order : buyOrders) {
            if (!order.isCompleted() && !order.isExpired()) {
                valid.add(order);
            }
        }
        return valid;
    }

    public List<BuyOrder> searchBuyOrders(String searchTerm) {
        String needle = searchTerm.toLowerCase();
        List<BuyOrder> result = new ArrayList<>();
        for (BuyOrder order : getAllBuyOrders()) {
            if (matches(order.itemStack, needle)) {
                result.add(order);
            }
        }
        return result;
    }

    public List<BuyOrder> getOrdersForItem(ItemStack stack) {
        List<BuyOrder> result = new ArrayList<>();
        for (BuyOrder order : getAllBuyOrders()) {
            if (ItemStack.areItemsEqual(order.itemStack, stack)) {
                result.add(order);
            }
        }
        return result;
    }

    // ================================================================ 系统商店库存

    /** 已售出的份数。 */
    public int getSystemSold(String key) {
        Integer sold = systemSold.get(key);
        return sold == null ? 0 : sold;
    }

    /** 剩余库存；{@code configuredStock < 0} 表示无限。 */
    public int getSystemStock(String key, int configuredStock) {
        if (configuredStock < 0) {
            return -1;
        }
        return Math.max(0, configuredStock - getSystemSold(key));
    }

    public void addSystemSold(String key, int units) {
        if (units <= 0) {
            return;
        }
        systemSold.merge(key, units, Integer::sum);
        markDirty();
    }

    // ================================================================ 清理

    public int cleanupExpiredListings() {
        long now = System.currentTimeMillis();
        long graceMillis = LISTING_RETRIEVE_GRACE_HOURS * 60L * 60L * 1000L;
        List<UUID> abandoned = new ArrayList<>();
        for (TradeItem item : listings.values()) {
            if (now - item.expireTime > graceMillis) {
                abandoned.add(item.id);
            }
        }
        for (UUID id : abandoned) {
            TradeItem item = listings.remove(id);
            if (item != null) {
                removeFromSellerIndex(item.sellerUUID, id);
            }
        }
        if (!abandoned.isEmpty()) {
            markDirty();
            QuantumHue.LOGGER.info("[Shop] 清理 {} 个超过 {} 小时仍未取回的过期上架",
                    abandoned.size(), LISTING_RETRIEVE_GRACE_HOURS);
        }
        return abandoned.size();
    }

    public int cleanupExpiredOrders() {
        int before = buyOrders.size();
        buyOrders.removeIf(order -> order.isCompleted() || order.isExpired());
        int removed = before - buyOrders.size();
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    public void cleanupAllExpired() {
        int listingsRemoved = cleanupExpiredListings();
        int ordersRemoved = cleanupExpiredOrders();
        QuantumHue.LOGGER.info("[Shop] 清理过期数据：{} 个商品，{} 个订单", listingsRemoved, ordersRemoved);
    }

    private static boolean matches(ItemStack stack, String lowerNeedle) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String name = stack.getDisplayName();
        return name != null && name.toLowerCase().contains(lowerNeedle);
    }
}
