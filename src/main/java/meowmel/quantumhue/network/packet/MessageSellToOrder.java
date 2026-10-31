package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** 把手里的物品卖给收购订单（含系统订单）。 */
public class MessageSellToOrder implements IMessage {

    private UUID orderId;
    private int quantity;

    public MessageSellToOrder() {}

    public MessageSellToOrder(UUID orderId, int quantity) {
        this.orderId = orderId;
        this.quantity = quantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        orderId = new UUID(buf.readLong(), buf.readLong());
        quantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(orderId.getMostSignificantBits());
        buf.writeLong(orderId.getLeastSignificantBits());
        buf.writeInt(quantity);
    }

    public static class Handler implements IMessageHandler<MessageSellToOrder, IMessage> {

        @Override
        public IMessage onMessage(MessageSellToOrder message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageSellToOrder message) {
            ShopDatabase database = ShopDatabase.get(player.world);
            BuyOrder order = null;
            for (BuyOrder candidate : database.getAllBuyOrders()) {
                if (candidate.id.equals(message.orderId)) {
                    order = candidate;
                    break;
                }
            }
            if (order == null) {
                player.sendMessage(new TextComponentString("§c收购订单不存在或已完成"));
                return;
            }
            if (message.quantity <= 0) {
                return;
            }
            if (order.getRemaining() < message.quantity) {
                player.sendMessage(new TextComponentString(String.format(
                        "§c收购数量不足，剩余：%1$s", order.getRemaining())));
                return;
            }

            int total = 0;
            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (ItemStack.areItemsEqual(stack, order.itemStack)) {
                    total += stack.getCount();
                }
            }
            if (total < message.quantity) {
                player.sendMessage(new TextComponentString(String.format(
                        "§c物品不足，当前持有：%1$s", total)));
                return;
            }

            int toRemove = message.quantity;
            for (int slot = 0; slot < player.inventory.getSizeInventory() && toRemove > 0; slot++) {
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (ItemStack.areItemsEqual(stack, order.itemStack)) {
                    int removed = Math.min(stack.getCount(), toRemove);
                    stack.shrink(removed);
                    toRemove -= removed;
                }
            }

            long earnings;
            try {
                earnings = Math.multiplyExact(order.pricePerUnit, (long) message.quantity);
            } catch (ArithmeticException overflow) {
                player.sendMessage(new TextComponentString("§c价格溢出"));
                return;
            }
            if (EconomyBridge.available()) {
                EconomyBridge.set(player, EconomyBridge.get(player) + earnings);
            }
            database.updateBuyOrderProgress(message.orderId, message.quantity);

            player.sendMessage(new TextComponentString(String.format(
                    "§a✓ 出售成功：%1$s ×%2$s，收入：%3$s 货币",
                    order.itemStack.getDisplayName(), message.quantity, earnings)));

            // 订单进度/移除直接回发一份收购列表视图，不再重发整个商店
            PacketHandler.sendTo(MessageShopView.buyOrders(database.getAllBuyOrders(),
                    EconomyBridge.get(player), false), player);
        }
    }
}
