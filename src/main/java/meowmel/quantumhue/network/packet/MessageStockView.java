package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.page.GuiShop;
import meowmel.quantumhue.stock.StockSnapshot;
import meowmel.quantumhue.stock.client.StockClientStore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 服务端 → 客户端的行情/账户快照。
 */
public final class MessageStockView implements IMessage {

    public StockSnapshot snapshot = new StockSnapshot();

    public MessageStockView() {}

    public MessageStockView(StockSnapshot snapshot) {
        this.snapshot = snapshot == null ? new StockSnapshot() : snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        snapshot = StockSnapshot.readFrom(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        snapshot.writeTo(buf);
    }

    public static final class Handler implements IMessageHandler<MessageStockView, IMessage> {

        @Override
        public IMessage onMessage(MessageStockView message, MessageContext ctx) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                StockClientStore.apply(message.snapshot);
                if (minecraft.currentScreen instanceof GuiShop) {
                    ((GuiShop) minecraft.currentScreen).onStockDataUpdated();
                }
            });
            return null;
        }
    }
}
