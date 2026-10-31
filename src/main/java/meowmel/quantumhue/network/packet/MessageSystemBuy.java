package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.ShopDelivery;
import meowmel.quantumhue.market.SystemShopCatalog;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 从系统商店购买。
 *
 * <p>只传分类 / 商品 id 和份数，价格与库存一律以服务端当前配置为准：客户端发来的任何数字都不可信。
 * 购买成功后回发一份新货架，客户端看到的库存立刻减少。
 */
public class MessageSystemBuy implements IMessage {

    private String categoryId = "";
    private String itemId = "";
    private int quantity;

    public MessageSystemBuy() {}

    public MessageSystemBuy(String categoryId, String itemId, int quantity) {
        this.categoryId = categoryId == null ? "" : categoryId;
        this.itemId = itemId == null ? "" : itemId;
        this.quantity = quantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        categoryId = ByteBufUtils.readUTF8String(buf);
        itemId = ByteBufUtils.readUTF8String(buf);
        quantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, categoryId == null ? "" : categoryId);
        ByteBufUtils.writeUTF8String(buf, itemId == null ? "" : itemId);
        buf.writeInt(quantity);
    }

    public static class Handler implements IMessageHandler<MessageSystemBuy, IMessage> {

        @Override
        public IMessage onMessage(MessageSystemBuy message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageSystemBuy message) {
            ShopDatabase database = ShopDatabase.get(player.world);
            SystemShopCatalog catalog = SystemShopCatalog.build(database);
            SystemShopCatalog.Entry entry = catalog.find(message.categoryId, message.itemId);
            if (entry == null) {
                player.sendMessage(new TextComponentString("§c该商品已不在系统商店中"));
                return;
            }
            int quantity = message.quantity;
            if (quantity <= 0) {
                return;
            }
            // 客户端送来的份数不可信：先按"一次最多拿满一背包"限幅，再做溢出安全的乘算
            int maxUnits = Math.max(1, 2304 / Math.max(1, entry.count));
            if (quantity > maxUnits) {
                player.sendMessage(new TextComponentString(String.format(
                        "§c一次最多购买 %1$s 份", maxUnits)));
                return;
            }
            if (entry.stock >= 0 && quantity > entry.stock) {
                player.sendMessage(new TextComponentString(String.format(
                        "§c库存不足，剩余：%1$s 份", entry.stock)));
                return;
            }
            long total;
            try {
                total = Math.multiplyExact(entry.price, (long) quantity);
            } catch (ArithmeticException overflow) {
                player.sendMessage(new TextComponentString("§c价格溢出"));
                return;
            }
            if (!EconomyBridge.available()) {
                player.sendMessage(new TextComponentString("§c当前没有可用的货币系统，无法购买"));
                return;
            }
            long money = EconomyBridge.get(player);
            if (money < total) {
                player.sendMessage(new TextComponentString(String.format(
                        "§c货币不足，需要：%1$s，当前：%2$s", total, money)));
                return;
            }
            if (!EconomyBridge.set(player, money - total)) {
                player.sendMessage(new TextComponentString("§c扣款失败，请稍后再试"));
                return;
            }

            ItemStack prototype = entry.stack.copy();
            prototype.setCount(1);
            ShopDelivery.give(player, prototype, entry.count * quantity);
            database.addSystemSold(SystemShopCatalog.stockKey(entry.categoryId, entry.id), quantity);

            player.sendMessage(new TextComponentString(String.format(
                    "§a✓ 已购买：%1$s ×%2$s，花费：%3$s 货币",
                    entry.displayName, entry.count * quantity, total)));

            PacketHandler.sendTo(MessageShopView.system(
                    SystemShopCatalog.build(database), EconomyBridge.get(player)), player);
        }
    }
}
