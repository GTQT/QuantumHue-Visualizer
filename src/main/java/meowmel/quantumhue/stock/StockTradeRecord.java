package meowmel.quantumhue.stock;

import meowmel.quantumhue.stock.StockTradeRecord.Type;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 一条成交记录。开仓时 {@code pnl} 为 0，平仓/强平/保护触发时写入已实现盈亏。
 *
 * <p>记录是给人看的账本，不参与结算；真正决定余额的是持仓与保证金。
 */
public final class StockTradeRecord {

    public enum Type {
        OPEN("开仓"),
        CLOSE("平仓"),
        LIQUIDATE("爆仓"),
        STOP("止损"),
        TAKE("止盈");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        public static Type byOrdinal(int ordinal) {
            Type[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OPEN;
        }
    }

    public long time;
    public String productId;
    public String productName;
    /** +1 多，-1 空。 */
    public int direction;
    public long quantity;
    /** 成交价（分）。 */
    public long price;
    public long fee;
    /** 已实现盈亏（分）；开仓为 0。 */
    public long pnl;
    public Type type;

    public StockTradeRecord() {
        this.type = Type.OPEN;
        this.productId = "";
        this.productName = "";
    }

    public StockTradeRecord(long time, String productId, String productName, int direction,
                            long quantity, long price, long fee, long pnl, Type type) {
        this.time = time;
        this.productId = productId;
        this.productName = productName;
        this.direction = direction;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.pnl = pnl;
        this.type = type;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("t", time);
        tag.setString("p", productId);
        tag.setString("n", productName);
        tag.setByte("d", (byte) direction);
        tag.setLong("q", quantity);
        tag.setLong("pr", price);
        tag.setLong("f", fee);
        tag.setLong("pnl", pnl);
        tag.setByte("ty", (byte) type.ordinal());
        return tag;
    }

    public static StockTradeRecord readFromNbt(NBTTagCompound tag) {
        StockTradeRecord record = new StockTradeRecord();
        record.time = tag.getLong("t");
        record.productId = tag.getString("p");
        record.productName = tag.getString("n");
        record.direction = tag.getByte("d") >= 0 ? 1 : -1;
        record.quantity = tag.getLong("q");
        record.price = tag.getLong("pr");
        record.fee = tag.getLong("f");
        record.pnl = tag.getLong("pnl");
        record.type = Type.byOrdinal(tag.getByte("ty"));
        return record;
    }
}
