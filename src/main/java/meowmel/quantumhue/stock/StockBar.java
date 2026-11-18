package meowmel.quantumhue.stock;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 一根 15 秒 OHLCV。价格单位是“分”，成交量单位是“张”。
 *
 * <p>结构故意做得很小：整页同步 100 根 × 5 个品种仍在 15KB 左右，
 * 量化后可以塞进一个普通 Forge 包。
 */
public final class StockBar {

    /** K 线开始时间（毫秒时间戳）。 */
    public long time;
    public long open;
    public long high;
    public long low;
    public long close;
    public long volume;

    public StockBar() {}

    public StockBar(long time, long open, long high, long low, long close, long volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public StockBar copy() {
        return new StockBar(time, open, high, low, close, volume);
    }

    public void write(ByteBuf buf) {
        buf.writeLong(time);
        buf.writeInt((int) open);
        buf.writeInt((int) high);
        buf.writeInt((int) low);
        buf.writeInt((int) close);
        buf.writeInt((int) volume);
    }

    public static StockBar read(ByteBuf buf) {
        return new StockBar(buf.readLong(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt());
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("t", time);
        tag.setLong("o", open);
        tag.setLong("h", high);
        tag.setLong("l", low);
        tag.setLong("c", close);
        tag.setLong("v", volume);
        return tag;
    }

    public static StockBar readFromNbt(NBTTagCompound tag) {
        return new StockBar(tag.getLong("t"), tag.getLong("o"), tag.getLong("h"),
                tag.getLong("l"), tag.getLong("c"), tag.getLong("v"));
    }
}
