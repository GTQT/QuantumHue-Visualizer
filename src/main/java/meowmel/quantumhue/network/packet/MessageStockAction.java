package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.stock.StockService;
import meowmel.quantumhue.stock.StockSnapshot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 → 服务端的交易请求。
 *
 * <p>协议上只有“请求/提议”，真正的校验、扣款与成交都在 {@link StockService} 里完成；
 * 客户端发来的数字一律当作不可信输入。
 */
public final class MessageStockAction implements IMessage {

    public enum Mode {
        REQUEST,
        POLL,
        OPEN,
        CLOSE,
        SET_PROTECTION,
        CLEAR_PROTECTION;

        public static Mode byOrdinal(int ordinal) {
            Mode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REQUEST;
        }
    }

    public Mode mode = Mode.REQUEST;
    public String productId = "";
    public long positionId;
    public int direction;
    public long quantity;
    public int leverage;
    public int protectionMode;
    public long stopPrice;
    public long takePrice;
    public long stopAmount;
    public long takeAmount;

    public MessageStockAction() {}

    public static MessageStockAction request() {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.REQUEST;
        return action;
    }

    public static MessageStockAction poll() {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.POLL;
        return action;
    }

    public static MessageStockAction open(String productId, int direction, long quantity, int leverage) {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.OPEN;
        action.productId = productId;
        action.direction = direction;
        action.quantity = quantity;
        action.leverage = leverage;
        return action;
    }

    public static MessageStockAction close(long positionId, long quantity) {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.CLOSE;
        action.positionId = positionId;
        action.quantity = quantity;
        return action;
    }

    public static MessageStockAction protect(long positionId, int protectionMode, long stopPrice,
                                            long takePrice, long stopAmount, long takeAmount) {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.SET_PROTECTION;
        action.positionId = positionId;
        action.protectionMode = protectionMode;
        action.stopPrice = stopPrice;
        action.takePrice = takePrice;
        action.stopAmount = stopAmount;
        action.takeAmount = takeAmount;
        return action;
    }

    public static MessageStockAction clearProtection(long positionId) {
        MessageStockAction action = new MessageStockAction();
        action.mode = Mode.CLEAR_PROTECTION;
        action.positionId = positionId;
        return action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = Mode.byOrdinal(buf.readByte());
        productId = ByteBufUtils.readUTF8String(buf);
        positionId = buf.readLong();
        direction = buf.readByte();
        quantity = buf.readLong();
        leverage = buf.readInt();
        protectionMode = buf.readByte();
        stopPrice = buf.readLong();
        takePrice = buf.readLong();
        stopAmount = buf.readLong();
        takeAmount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        ByteBufUtils.writeUTF8String(buf, productId == null ? "" : productId);
        buf.writeLong(positionId);
        buf.writeByte(direction);
        buf.writeLong(quantity);
        buf.writeInt(leverage);
        buf.writeByte(protectionMode);
        buf.writeLong(stopPrice);
        buf.writeLong(takePrice);
        buf.writeLong(stopAmount);
        buf.writeLong(takeAmount);
    }

    public static final class Handler implements IMessageHandler<MessageStockAction, IMessage> {

        @Override
        public IMessage onMessage(MessageStockAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageStockAction message) {
            StockSnapshot snapshot;
            try {
                switch (message.mode) {
                    case POLL:
                        snapshot = StockService.poll(player);
                        break;
                    case OPEN:
                        snapshot = StockService.open(player, message.productId, message.direction,
                                message.quantity, message.leverage);
                        break;
                    case CLOSE:
                        snapshot = StockService.close(player, message.positionId, message.quantity);
                        break;
                    case SET_PROTECTION:
                        snapshot = StockService.setProtection(player, message.positionId,
                                message.protectionMode, message.stopPrice, message.takePrice,
                                message.stopAmount, message.takeAmount);
                        break;
                    case CLEAR_PROTECTION:
                        snapshot = StockService.clearProtection(player, message.positionId);
                        break;
                    case REQUEST:
                    default:
                        snapshot = StockService.request(player);
                        break;
                }
            } catch (Throwable failure) {
                QuantumHue.LOGGER.error("[Stock] 处理交易请求失败", failure);
                snapshot = StockService.request(player);
            }
            PacketHandler.sendTo(new MessageStockView(snapshot), player);
        }
    }
}
