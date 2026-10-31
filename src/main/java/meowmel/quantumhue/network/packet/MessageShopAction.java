package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.SystemShopCatalog;
import meowmel.quantumhue.market.TradeItem;
import meowmel.quantumhue.market.client.ShopClientStore;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端请求商店数据。
 *
 * <p>商店数据是服务端权威的，客户端不能直接读 {@code ShopDatabase}（专用服务器上根本读不到），
 * 所以界面打开、切换页签、搜索、以及每次买卖之后都由这个包向服务端要数据。
 * 请求按 {@link ShopClientStore#PAGE_SIZE} 分页，服务端回 {@link MessageShopView}。
 */
public class MessageShopAction implements IMessage {

    /** 请求的视图种类。 */
    public enum Mode {
        /** 公开在售列表（支持搜索，分页）。 */
        BROWSE,
        /** 公开收购订单（支持搜索，分页）。 */
        BUY_ORDERS,
        /** 自己的上架（含已过期，供取回；不需要分页）。 */
        OWN,
        /** 系统商店货架。 */
        SYSTEM
    }

    private Mode mode = Mode.BROWSE;
    private String search = "";
    private int offset;

    public MessageShopAction() {}

    public static MessageShopAction browse(String search, int offset) {
        return of(Mode.BROWSE, search, offset);
    }

    public static MessageShopAction buyOrders(String search, int offset) {
        return of(Mode.BUY_ORDERS, search, offset);
    }

    public static MessageShopAction own() {
        return of(Mode.OWN, "", 0);
    }

    public static MessageShopAction system() {
        return of(Mode.SYSTEM, "", 0);
    }

    private static MessageShopAction of(Mode mode, String search, int offset) {
        MessageShopAction message = new MessageShopAction();
        message.mode = mode;
        message.search = search == null ? "" : search;
        message.offset = Math.max(0, offset);
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Mode[] modes = Mode.values();
        mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.BROWSE;
        search = ByteBufUtils.readUTF8String(buf);
        offset = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        ByteBufUtils.writeUTF8String(buf, search == null ? "" : search);
        buf.writeInt(offset);
    }

    public static class Handler implements IMessageHandler<MessageShopAction, IMessage> {

        @Override
        public IMessage onMessage(MessageShopAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> respond(player, message));
            return null;
        }

        private static void respond(EntityPlayerMP player, MessageShopAction request) {
            ShopDatabase database = ShopDatabase.get(player.world);
            long money = EconomyBridge.get(player);
            switch (request.mode) {
                case BROWSE: {
                    List<TradeItem> all = request.search.isEmpty()
                            ? database.getAllListings()
                            : database.searchListings(request.search);
                    sendPage(player, all, request.offset, money);
                    break;
                }
                case BUY_ORDERS: {
                    List<BuyOrder> all = request.search.isEmpty()
                            ? database.getAllBuyOrders()
                            : database.searchBuyOrders(request.search);
                    List<BuyOrder> page = slice(all, request.offset, ShopClientStore.PAGE_SIZE);
                    int next = request.offset + page.size();
                    PacketHandler.sendTo(MessageShopView.buyOrders(page, money, next < all.size()), player);
                    break;
                }
                case OWN:
                    PacketHandler.sendTo(
                            MessageShopView.own(database.getListingsBySeller(player.getUniqueID()), money), player);
                    break;
                case SYSTEM:
                default:
                    PacketHandler.sendTo(
                            MessageShopView.system(SystemShopCatalog.build(database), money), player);
                    break;
            }
        }

        private static void sendPage(EntityPlayerMP player, List<TradeItem> all, int offset, long money) {
            List<TradeItem> page = slice(all, offset, ShopClientStore.PAGE_SIZE);
            int next = offset + page.size();
            PacketHandler.sendTo(MessageShopView.listings(page, money, next < all.size(), offset), player);
        }

        private static <T> List<T> slice(List<T> source, int offset, int size) {
            if (offset >= source.size()) {
                return new ArrayList<>();
            }
            int end = Math.min(source.size(), offset + size);
            return new ArrayList<>(source.subList(offset, end));
        }
    }
}
