package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.lottery.LotteryCatalog;
import meowmel.quantumhue.lottery.LotteryResult;
import meowmel.quantumhue.lottery.LotterySnapshot;
import meowmel.quantumhue.lottery.client.LotteryClientStore;
import meowmel.quantumhue.page.GuiShop;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 服务端 → 客户端的抽奖状态：奖池快照、余额、提示，以及（抽奖时）最终结果。
 *
 * <p>客户端收到后写入 {@link LotteryClientStore}；已经打开的商店界面会据此刷新并播放动画。
 */
public class MessageLotteryView implements IMessage {

    private LotteryCatalog catalog = LotteryCatalog.empty();
    private long playerMoney;
    private String notice = "";
    private String error = "";
    private LotteryResult result;

    public MessageLotteryView() {}

    public static MessageLotteryView of(LotterySnapshot snapshot) {
        MessageLotteryView message = new MessageLotteryView();
        if (snapshot != null) {
            message.catalog = snapshot.catalog;
            message.playerMoney = snapshot.money;
            message.notice = snapshot.notice;
            message.error = snapshot.error;
            message.result = snapshot.result;
        }
        return message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        catalog.write(buf);
        buf.writeLong(playerMoney);
        ByteBufUtils.writeUTF8String(buf, notice == null ? "" : notice);
        ByteBufUtils.writeUTF8String(buf, error == null ? "" : error);
        buf.writeBoolean(result != null);
        if (result != null) {
            result.write(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        catalog = LotteryCatalog.read(buf);
        playerMoney = buf.readLong();
        notice = ByteBufUtils.readUTF8String(buf);
        error = ByteBufUtils.readUTF8String(buf);
        result = buf.readBoolean() ? LotteryResult.read(buf) : null;
    }

    public static class Handler implements IMessageHandler<MessageLotteryView, IMessage> {

        @Override
        public IMessage onMessage(MessageLotteryView message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(MessageLotteryView message) {
            LotteryClientStore.apply(message.catalog, message.playerMoney,
                    message.notice, message.error, message.result);
            if (Minecraft.getMinecraft().currentScreen instanceof GuiShop) {
                ((GuiShop) Minecraft.getMinecraft().currentScreen).onLotteryDataUpdated();
            }
        }
    }
}
