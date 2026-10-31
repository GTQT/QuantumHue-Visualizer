package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.TradeItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 上架手持物品。 */
public class MessageSellItem implements IMessage {

    private ItemStack itemStack;
    private long pricePerUnit;
    private int quantity;

    public MessageSellItem() {}

    public MessageSellItem(ItemStack itemStack, long pricePerUnit, int quantity) {
        this.itemStack = itemStack;
        this.pricePerUnit = pricePerUnit;
        this.quantity = quantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        itemStack = ByteBufUtils.readItemStack(buf);
        pricePerUnit = buf.readLong();
        quantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, itemStack);
        buf.writeLong(pricePerUnit);
        buf.writeInt(quantity);
    }

    public static class Handler implements IMessageHandler<MessageSellItem, IMessage> {

        @Override
        public IMessage onMessage(MessageSellItem message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageSellItem message) {
            if (message.itemStack == null || message.itemStack.isEmpty()
                    || message.quantity <= 0 || message.pricePerUnit <= 0) {
                player.sendMessage(new TextComponentString("§c上架参数无效"));
                return;
            }
            if (message.pricePerUnit > 1_000_000_000_000L) {
                player.sendMessage(new TextComponentString("§c单价过高"));
                return;
            }
            ShopDatabase database = ShopDatabase.get(player.world);

            int total = 0;
            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (ItemStack.areItemsEqual(stack, message.itemStack)) {
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
                if (ItemStack.areItemsEqual(stack, message.itemStack)) {
                    int removed = Math.min(stack.getCount(), toRemove);
                    stack.shrink(removed);
                    toRemove -= removed;
                }
            }

            database.addListing(new TradeItem(message.itemStack, message.pricePerUnit, message.quantity,
                    player.getUniqueID(), player.getName()));
            player.sendMessage(new TextComponentString(String.format(
                    "§a✓ 已上架：%1$s ×%2$s，单价 %3$s 货币",
                    message.itemStack.getDisplayName(), message.quantity, message.pricePerUnit)));
        }
    }
}
