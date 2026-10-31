package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.OfflineEarningsDatabase;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.ShopDelivery;
import meowmel.quantumhue.market.TradeItem;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** 购买玩家上架的商品（一口价）。 */
public class MessageBuyItem implements IMessage {

    private UUID listingId;
    private int quantity;

    public MessageBuyItem() {}

    public MessageBuyItem(UUID listingId, int quantity) {
        this.listingId = listingId;
        this.quantity = quantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        listingId = new UUID(buf.readLong(), buf.readLong());
        quantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(listingId.getMostSignificantBits());
        buf.writeLong(listingId.getLeastSignificantBits());
        buf.writeInt(quantity);
    }

    public static class Handler implements IMessageHandler<MessageBuyItem, IMessage> {

        @Override
        public IMessage onMessage(MessageBuyItem message, MessageContext ctx) {
            EntityPlayerMP buyer = ctx.getServerHandler().player;
            buyer.server.addScheduledTask(() -> handle(buyer, message));
            return null;
        }

        private static void handle(EntityPlayerMP buyer, MessageBuyItem message) {
            ShopDatabase database = ShopDatabase.get(buyer.world);
            TradeItem listing = database.getListing(message.listingId);
            if (listing == null) {
                buyer.sendMessage(new TextComponentString("§c商品不存在或已下架"));
                return;
            }
            if (message.quantity <= 0) {
                return;
            }
            if (listing.quantity < message.quantity) {
                buyer.sendMessage(new TextComponentString("§c商品库存不足"));
                return;
            }

            long totalPrice;
            try {
                totalPrice = Math.multiplyExact(listing.pricePerUnit, (long) message.quantity);
            } catch (ArithmeticException overflow) {
                buyer.sendMessage(new TextComponentString("§c价格溢出"));
                return;
            }
            long buyerMoney = EconomyBridge.get(buyer);
            if (!EconomyBridge.available()) {
                buyer.sendMessage(new TextComponentString("§c当前没有可用的货币系统，无法购买"));
                return;
            }
            if (buyerMoney < totalPrice) {
                buyer.sendMessage(new TextComponentString(String.format(
                        "§c货币不足，需要：%1$s，当前：%2$s", totalPrice, buyerMoney)));
                return;
            }
            if (!EconomyBridge.set(buyer, buyerMoney - totalPrice)) {
                buyer.sendMessage(new TextComponentString("§c扣款失败，请稍后再试"));
                return;
            }

            boolean systemSeller = BuyOrder.SYSTEM_SELLER_UUID.equals(listing.sellerUUID);
            if (!systemSeller) {
                EntityPlayerMP seller = buyer.server.getPlayerList().getPlayerByUUID(listing.sellerUUID);
                if (seller != null) {
                    EconomyBridge.set(seller, EconomyBridge.get(seller) + totalPrice);
                    seller.sendMessage(new TextComponentString(String.format(
                            "§a✓ 售出商品：%1$s ×%2$s，收入：%3$s 货币",
                            listing.itemStack.getDisplayName(), message.quantity, totalPrice)));
                } else {
                    addOfflineEarnings(buyer.world, listing.sellerUUID, totalPrice,
                            listing.itemStack.getDisplayName(), message.quantity, buyer.getName());
                }
            } else {
                QuantumHue.LOGGER.info("[Shop] 系统商店售出：{} ×{}，回收货币：{}",
                        listing.itemStack.getDisplayName(), message.quantity, totalPrice);
            }

            ShopDelivery.give(buyer, listing.itemStack, message.quantity);
            buyer.sendMessage(new TextComponentString(String.format(
                    "§a✓ 购买成功：%1$s ×%2$s，花费：%3$s 货币",
                    listing.itemStack.getDisplayName(), message.quantity, totalPrice)));

            int remaining = listing.quantity - message.quantity;
            if (remaining <= 0) {
                database.removeListing(message.listingId);
            } else {
                database.updateQuantity(message.listingId, remaining);
            }

            // 只把这条商品的变更推给买家，不再重发整个商店
            PacketHandler.sendTo(MessageShopView.listingUpdate(message.listingId,
                    database.getListing(message.listingId), EconomyBridge.get(buyer)), buyer);
        }

        private static void addOfflineEarnings(World world, UUID sellerUUID, long amount,
                                               String itemName, int quantity, String buyerName) {
            OfflineEarningsDatabase database = OfflineEarningsDatabase.get(world);
            ITextComponent source = new TextComponentString(String.format(
                    "出售：%1$s x%2$s (买家：%3$s)", itemName, quantity, buyerName));
            database.addEarning(sellerUUID, amount, source);
            QuantumHue.LOGGER.info("[Shop] 记录离线收益：玩家 {} 获得 {} 货币 ({})", sellerUUID, amount, source);
        }
    }
}
