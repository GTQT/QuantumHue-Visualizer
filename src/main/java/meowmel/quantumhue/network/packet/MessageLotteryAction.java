package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.lottery.LotteryService;
import meowmel.quantumhue.lottery.LotterySnapshot;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求抽奖数据或执行抽奖操作。
 *
 * <p>结果始终由服务端生成，客户端只发送意图；服务端回 {@link MessageLotteryView}。
 */
public class MessageLotteryAction implements IMessage {

    public enum Mode {
        /** 打开/刷新页面：只读奖池与余额。 */
        REQUEST,
        /** 抽一次；{@link #rows} 是本次抽取的行数（1..3）。 */
        SPIN
    }

    private Mode mode = Mode.REQUEST;
    private String poolId = "";
    private int rows = 1;

    public MessageLotteryAction() {}

    public static MessageLotteryAction request() {
        return of(Mode.REQUEST, "", 1);
    }

    public static MessageLotteryAction spin(int rows) {
        return of(Mode.SPIN, "", rows);
    }

    public static MessageLotteryAction spin(String poolId, int rows) {
        return of(Mode.SPIN, poolId, rows);
    }

    private static MessageLotteryAction of(Mode mode, String poolId, int rows) {
        MessageLotteryAction message = new MessageLotteryAction();
        message.mode = mode;
        message.poolId = poolId == null ? "" : poolId.trim();
        message.rows = Math.max(1, Math.min(3, rows));
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Mode[] modes = Mode.values();
        mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.REQUEST;
        poolId = ByteBufUtils.readUTF8String(buf);
        rows = Math.max(1, Math.min(3, buf.readByte() & 0xFF));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        ByteBufUtils.writeUTF8String(buf, poolId == null ? "" : poolId);
        buf.writeByte(rows);
    }

    public static class Handler implements IMessageHandler<MessageLotteryAction, IMessage> {

        @Override
        public IMessage onMessage(MessageLotteryAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> respond(player, message));
            return null;
        }

        private static void respond(EntityPlayerMP player, MessageLotteryAction request) {
            LotterySnapshot snapshot;
            try {
                if (request.mode == Mode.SPIN) {
                    snapshot = LotteryService.spin(player, request.poolId, request.rows);
                } else {
                    snapshot = LotteryService.request(player);
                }
            } catch (Throwable failure) {
                meowmel.quantumhue.QuantumHue.LOGGER.error("[Lottery] 抽奖操作失败", failure);
                snapshot = LotteryService.request(player);
            }
            PacketHandler.sendTo(MessageLotteryView.of(snapshot), player);
        }
    }
}
