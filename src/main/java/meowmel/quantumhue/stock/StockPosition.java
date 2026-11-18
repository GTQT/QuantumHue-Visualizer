package meowmel.quantumhue.stock;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 一笔持仓。数量可被部分平仓，保证金按剩余数量同比保留。
 *
 * <p>同一品种允许多空同时存在（双向持仓），每笔持仓独立计算保证金与强平线；
 * 这样“锁仓”不会把两边的风险互相抵消，结算也简单。
 */
public final class StockPosition {

    public long id;
    public String productId;
    public String productName;
    /** +1 多，-1 空。 */
    public int direction;
    /** 剩余张数。 */
    public long quantity;
    /** 加权开仓价（分）。 */
    public long entryPrice;
    /** 这笔持仓占用的保证金（分）。 */
    public long margin;
    public long openedAt;

    public int protectionMode = StockConfig.PROTECTION_NONE;
    /** 价格模式：止损价（分），0 表示未设。 */
    public long stopPrice;
    /** 价格模式：止盈价（分），0 表示未设。 */
    public long takePrice;
    /** 盈亏模式：止损金额（正数，分），0 表示未设。 */
    public long stopAmount;
    /** 盈亏模式：止盈金额（正数，分），0 表示未设。 */
    public long takeAmount;

    public StockPosition() {
        this.productId = "";
        this.productName = "";
    }

    /** 当前标记价下的浮动盈亏（分）。 */
    public long unrealized(long markPrice) {
        return (long) direction * (markPrice - entryPrice) * quantity;
    }

    /** 部分平仓时释放的保证金；平掉最后一笔时直接取余数，避免累计误差。 */
    public long releaseMargin(long closeQuantity) {
        if (closeQuantity >= quantity) {
            return margin;
        }
        if (closeQuantity <= 0L || quantity <= 0L) {
            return 0L;
        }
        return margin * closeQuantity / quantity;
    }

    public boolean hasProtection() {
        return protectionMode == StockConfig.PROTECTION_PRICE
                ? stopPrice > 0L || takePrice > 0L
                : protectionMode == StockConfig.PROTECTION_PROFIT
                && (stopAmount > 0L || takeAmount > 0L);
    }

    public void clearProtection() {
        protectionMode = StockConfig.PROTECTION_NONE;
        stopPrice = 0L;
        takePrice = 0L;
        stopAmount = 0L;
        takeAmount = 0L;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("id", id);
        tag.setString("p", productId);
        tag.setString("n", productName);
        tag.setByte("d", (byte) direction);
        tag.setLong("q", quantity);
        tag.setLong("e", entryPrice);
        tag.setLong("m", margin);
        tag.setLong("o", openedAt);
        tag.setByte("pm", (byte) protectionMode);
        tag.setLong("sp", stopPrice);
        tag.setLong("tp", takePrice);
        tag.setLong("sa", stopAmount);
        tag.setLong("ta", takeAmount);
        return tag;
    }

    public static StockPosition readFromNbt(NBTTagCompound tag) {
        StockPosition position = new StockPosition();
        position.id = tag.getLong("id");
        position.productId = tag.getString("p");
        position.productName = tag.getString("n");
        position.direction = tag.getByte("d") >= 0 ? 1 : -1;
        position.quantity = tag.getLong("q");
        position.entryPrice = tag.getLong("e");
        position.margin = tag.getLong("m");
        position.openedAt = tag.getLong("o");
        position.protectionMode = tag.getByte("pm");
        position.stopPrice = tag.getLong("sp");
        position.takePrice = tag.getLong("tp");
        position.stopAmount = tag.getLong("sa");
        position.takeAmount = tag.getLong("ta");
        return position;
    }
}
