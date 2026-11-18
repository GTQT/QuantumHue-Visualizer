package meowmel.quantumhue.stock;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端发给客户端的只读快照。
 *
 * <p>{@link #full} 为真时表示这是打开页签时的整包（包含全部 K 线）；
 * {@link #bars} 为真表示本次带 K 线，客户端应整体替换，否则只按最新价修补最后一根。
 * 这样每秒轮询只需要传价格和账户数字，不会把 500 根 K 线重复发一遍。
 */
public final class StockSnapshot {

    public long sequence;
    public boolean full;
    public boolean bars;
    public long wallet;
    public long escrow;
    public long equity;
    public long usedMargin;
    public long available;
    public String notice = "";
    public String error = "";
    public final List<ProductView> products = new ArrayList<>();
    public final List<PositionView> positions = new ArrayList<>();
    public final List<RecordView> records = new ArrayList<>();

    /** 品种行情。 */
    public static final class ProductView {
        public String id;
        public String name;
        public long price;
        /** 相对上一根收盘价的涨跌幅，单位 0.01%。 */
        public int change;
        public final List<StockBar> bars = new ArrayList<>();
    }

    /** 持仓。 */
    public static final class PositionView {
        public long id;
        public String productId;
        public String productName;
        public int direction;
        public long quantity;
        public long entryPrice;
        public long markPrice;
        public long margin;
        public long unrealized;
        public long openedAt;
        public int protectionMode;
        public long stopPrice;
        public long takePrice;
        public long stopAmount;
        public long takeAmount;
    }

    /** 成交记录。 */
    public static final class RecordView {
        public int type;
        public long time;
        public String productName;
        public int direction;
        public long quantity;
        public long price;
        public long fee;
        public long pnl;
    }

    // ================================================================ 序列化

    public void writeTo(ByteBuf buf) {
        buf.writeLong(sequence);
        buf.writeBoolean(full);
        buf.writeBoolean(bars);
        buf.writeLong(wallet);
        buf.writeLong(escrow);
        buf.writeLong(equity);
        buf.writeLong(usedMargin);
        buf.writeLong(available);
        ByteBufUtils.writeUTF8String(buf, notice == null ? "" : notice);
        ByteBufUtils.writeUTF8String(buf, error == null ? "" : error);

        buf.writeShort(products.size());
        for (ProductView product : products) {
            ByteBufUtils.writeUTF8String(buf, product.id);
            ByteBufUtils.writeUTF8String(buf, product.name);
            buf.writeLong(product.price);
            buf.writeInt(product.change);
            buf.writeBoolean(bars);
            if (bars) {
                buf.writeShort(product.bars.size());
                for (StockBar bar : product.bars) {
                    bar.write(buf);
                }
            }
        }

        buf.writeShort(positions.size());
        for (PositionView position : positions) {
            buf.writeLong(position.id);
            ByteBufUtils.writeUTF8String(buf, position.productId);
            ByteBufUtils.writeUTF8String(buf, position.productName);
            buf.writeByte(position.direction);
            buf.writeLong(position.quantity);
            buf.writeLong(position.entryPrice);
            buf.writeLong(position.markPrice);
            buf.writeLong(position.margin);
            buf.writeLong(position.unrealized);
            buf.writeLong(position.openedAt);
            buf.writeByte(position.protectionMode);
            buf.writeLong(position.stopPrice);
            buf.writeLong(position.takePrice);
            buf.writeLong(position.stopAmount);
            buf.writeLong(position.takeAmount);
        }

        buf.writeShort(records.size());
        for (RecordView record : records) {
            buf.writeByte(record.type);
            buf.writeLong(record.time);
            ByteBufUtils.writeUTF8String(buf, record.productName);
            buf.writeByte(record.direction);
            buf.writeLong(record.quantity);
            buf.writeLong(record.price);
            buf.writeLong(record.fee);
            buf.writeLong(record.pnl);
        }
    }

    public static StockSnapshot readFrom(ByteBuf buf) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.sequence = buf.readLong();
        snapshot.full = buf.readBoolean();
        snapshot.bars = buf.readBoolean();
        snapshot.wallet = buf.readLong();
        snapshot.escrow = buf.readLong();
        snapshot.equity = buf.readLong();
        snapshot.usedMargin = buf.readLong();
        snapshot.available = buf.readLong();
        snapshot.notice = ByteBufUtils.readUTF8String(buf);
        snapshot.error = ByteBufUtils.readUTF8String(buf);

        int productCount = buf.readUnsignedShort();
        for (int i = 0; i < productCount; i++) {
            ProductView product = new ProductView();
            product.id = ByteBufUtils.readUTF8String(buf);
            product.name = ByteBufUtils.readUTF8String(buf);
            product.price = buf.readLong();
            product.change = buf.readInt();
            boolean hasBars = buf.readBoolean();
            if (hasBars) {
                int barCount = buf.readUnsignedShort();
                for (int b = 0; b < barCount; b++) {
                    product.bars.add(StockBar.read(buf));
                }
            }
            snapshot.products.add(product);
        }

        int positionCount = buf.readUnsignedShort();
        for (int i = 0; i < positionCount; i++) {
            PositionView position = new PositionView();
            position.id = buf.readLong();
            position.productId = ByteBufUtils.readUTF8String(buf);
            position.productName = ByteBufUtils.readUTF8String(buf);
            position.direction = buf.readByte();
            position.quantity = buf.readLong();
            position.entryPrice = buf.readLong();
            position.markPrice = buf.readLong();
            position.margin = buf.readLong();
            position.unrealized = buf.readLong();
            position.openedAt = buf.readLong();
            position.protectionMode = buf.readByte();
            position.stopPrice = buf.readLong();
            position.takePrice = buf.readLong();
            position.stopAmount = buf.readLong();
            position.takeAmount = buf.readLong();
            snapshot.positions.add(position);
        }

        int recordCount = buf.readUnsignedShort();
        for (int i = 0; i < recordCount; i++) {
            RecordView record = new RecordView();
            record.type = buf.readByte();
            record.time = buf.readLong();
            record.productName = ByteBufUtils.readUTF8String(buf);
            record.direction = buf.readByte();
            record.quantity = buf.readLong();
            record.price = buf.readLong();
            record.fee = buf.readLong();
            record.pnl = buf.readLong();
            snapshot.records.add(record);
        }
        return snapshot;
    }

    /** 供服务端聊天提示复用。 */
    public static String plain(ITextComponent component) {
        return component == null ? "" : component.getUnformattedText();
    }
}
