package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.TradeItem;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** 下架自己的商品（已过期的商品同样走这里取回）。 */
public class MessageRemoveListing implements IMessage {

    private UUID listingId;

    public MessageRemoveListing() {}

    public MessageRemoveListing(UUID listingId) {
        this.listingId = listingId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        listingId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(listingId.getMostSignificantBits());
        buf.writeLong(listingId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<MessageRemoveListing, IMessage> {

        @Override
        public IMessage onMessage(MessageRemoveListing message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageRemoveListing message) {
            ShopDatabase database = ShopDatabase.get(player.world);
            TradeItem listing = database.getListing(message.listingId);
            if (listing == null) {
                player.sendMessage(new TextComponentString("§c商品不存在或已被移除"));
                return;
            }
            if (!listing.sellerUUID.equals(player.getUniqueID())) {
                player.sendMessage(new TextComponentString("§c只能下架自己的商品"));
                return;
            }
            meowmel.quantumhue.market.ShopDelivery.give(player, listing.itemStack, listing.quantity);
            database.removeListing(message.listingId);

            if (listing.isExpired()) {
                player.sendMessage(new TextComponentString(String.format(
                        "§e✓ 已取回过期商品：%1$s ×%2$s", listing.itemStack.getDisplayName(), listing.quantity)));
            } else {
                player.sendMessage(new TextComponentString(String.format(
                        "§a✓ 已下架：%1$s ×%2$s", listing.itemStack.getDisplayName(), listing.quantity)));
            }

            PacketHandler.sendTo(MessageShopView.listingUpdate(message.listingId, null,
                    EconomyBridge.get(player)), player);
        }
    }
}
