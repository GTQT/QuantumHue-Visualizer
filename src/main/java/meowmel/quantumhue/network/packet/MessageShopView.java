package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.SystemShopCatalog;
import meowmel.quantumhue.market.TradeItem;
import meowmel.quantumhue.market.client.ShopClientStore;
import meowmel.quantumhue.page.GuiShop;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端的商店视图数据。
 *
 * <p>只发送被请求的那一页、单条记录的增量，或系统商店的货架快照；客户端回执到
 * {@link ShopClientStore} 后，界面再从那里取数。
 */
public class MessageShopView implements IMessage {

    public enum Kind {
        /** 浏览列表的第一页：替换现有列表。 */
        LISTINGS_FIRST,
        /** 浏览列表的后续页：追加。 */
        LISTINGS_MORE,
        /** 收购订单（一次性给全）。 */
        BUY_ORDERS,
        /** 自己的上架（含过期）。 */
        OWN,
        /** 单条记录的增量更新（买卖/下架后）。 */
        LISTING_UPDATE,
        /** 系统商店货架快照。 */
        SYSTEM_CATALOG,
        /** 只有余额变化（例如系统商店购买后）。 */
        MONEY
    }

    private Kind kind = Kind.LISTINGS_FIRST;
    private final List<TradeItem> listings = new ArrayList<>();
    private final List<BuyOrder> buyOrders = new ArrayList<>();
    private SystemShopCatalog catalog = SystemShopCatalog.empty();
    private long playerMoney;
    private boolean more;
    /** 仅 LISTING_UPDATE 使用：为 true 时表示该记录已被移除。 */
    private boolean removed;
    private UUID updateId;

    public MessageShopView() {}

    // ================================================================ 构造

    public static MessageShopView listings(List<TradeItem> page, long money, boolean more, int offset) {
        MessageShopView message = new MessageShopView();
        message.kind = offset <= 0 ? Kind.LISTINGS_FIRST : Kind.LISTINGS_MORE;
        message.listings.addAll(page);
        message.playerMoney = money;
        message.more = more;
        return message;
    }

    public static MessageShopView buyOrders(List<BuyOrder> orders, long money, boolean more) {
        MessageShopView message = new MessageShopView();
        message.kind = Kind.BUY_ORDERS;
        message.buyOrders.addAll(orders);
        message.playerMoney = money;
        message.more = more;
        return message;
    }

    public static MessageShopView own(List<TradeItem> own, long money) {
        MessageShopView message = new MessageShopView();
        message.kind = Kind.OWN;
        message.listings.addAll(own);
        message.playerMoney = money;
        return message;
    }

    public static MessageShopView system(SystemShopCatalog catalog, long money) {
        MessageShopView message = new MessageShopView();
        message.kind = Kind.SYSTEM_CATALOG;
        message.catalog = catalog == null ? SystemShopCatalog.empty() : catalog;
        message.playerMoney = money;
        return message;
    }

    public static MessageShopView money(long money) {
        MessageShopView message = new MessageShopView();
        message.kind = Kind.MONEY;
        message.playerMoney = money;
        return message;
    }

    /** 单条记录的增量更新；{@code updated} 为 null 表示该记录已不存在。 */
    public static MessageShopView listingUpdate(UUID id, TradeItem updated, long money) {
        MessageShopView message = new MessageShopView();
        message.kind = Kind.LISTING_UPDATE;
        message.updateId = id;
        message.removed = updated == null;
        if (updated != null) {
            message.listings.add(updated);
        }
        message.playerMoney = money;
        return message;
    }

    // ================================================================ 序列化

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeLong(playerMoney);
        buf.writeBoolean(more);
        buf.writeBoolean(removed);
        buf.writeBoolean(updateId != null);
        if (updateId != null) {
            buf.writeLong(updateId.getMostSignificantBits());
            buf.writeLong(updateId.getLeastSignificantBits());
        }
        buf.writeInt(listings.size());
        for (TradeItem item : listings) {
            ByteBufUtils.writeTag(buf, item.writeToNBT());
        }
        buf.writeInt(buyOrders.size());
        for (BuyOrder order : buyOrders) {
            ByteBufUtils.writeTag(buf, order.writeToNBT());
        }
        catalog.write(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Kind[] kinds = Kind.values();
        kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.LISTINGS_FIRST;
        playerMoney = buf.readLong();
        more = buf.readBoolean();
        removed = buf.readBoolean();
        if (buf.readBoolean()) {
            updateId = new UUID(buf.readLong(), buf.readLong());
        }
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            listings.add(new TradeItem(ByteBufUtils.readTag(buf)));
        }
        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            buyOrders.add(new BuyOrder(ByteBufUtils.readTag(buf)));
        }
        catalog = SystemShopCatalog.read(buf);
    }

    // ================================================================ 客户端处理

    public static class Handler implements IMessageHandler<MessageShopView, IMessage> {

        @Override
        public IMessage onMessage(MessageShopView message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(MessageShopView message) {
            switch (message.kind) {
                case LISTINGS_FIRST:
                    ShopClientStore.beginListings(message.playerMoney, message.listings, message.more);
                    break;
                case LISTINGS_MORE:
                    ShopClientStore.appendListings(message.listings, message.more);
                    break;
                case BUY_ORDERS:
                    ShopClientStore.setBuyOrders(message.playerMoney, message.buyOrders);
                    break;
                case OWN:
                    ShopClientStore.setOwnListings(message.playerMoney, message.listings);
                    break;
                case LISTING_UPDATE: {
                    ShopClientStore.setPlayerMoney(message.playerMoney);
                    TradeItem updated = message.removed || message.listings.isEmpty()
                            ? null : message.listings.get(0);
                    ShopClientStore.updateListing(message.updateId, updated);
                    break;
                }
                case SYSTEM_CATALOG:
                    ShopClientStore.setSystemShop(message.playerMoney, message.catalog);
                    break;
                case MONEY:
                default:
                    ShopClientStore.setPlayerMoney(message.playerMoney);
                    break;
            }
            notifyOpenScreen();
        }

        /** 数据落库后让已经打开的商店界面刷新（界面没打开时是空操作）。 */
        @SideOnly(Side.CLIENT)
        private static void notifyOpenScreen() {
            if (Minecraft.getMinecraft().currentScreen instanceof GuiShop) {
                ((GuiShop) Minecraft.getMinecraft().currentScreen).onShopDataUpdated();
            }
        }
    }
}
