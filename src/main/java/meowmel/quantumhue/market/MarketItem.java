package meowmel.quantumhue.market;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

/**
 * 交易条目的公共字段：一口价商品与收购订单只差各自的附加信息。
 *
 * <p>时间统一用毫秒时间戳存盘，因此服务器重启后剩余时间仍然连续；{@link #isExpired()} 与
 * 展示用的剩余时间都从同一个 {@code expireTime} 推导，不会出现“界面显示已过期但还能买”。
 */
public abstract class MarketItem {

    public UUID id;
    public ItemStack itemStack;
    public long pricePerUnit;
    public int quantity;
    public long createTime;
    public long expireTime;

    protected MarketItem() {
        this.id = UUID.randomUUID();
        this.createTime = System.currentTimeMillis();
    }

    protected MarketItem(UUID id, ItemStack itemStack, long pricePerUnit, int quantity) {
        this.id = id;
        this.itemStack = itemStack.copy();
        this.pricePerUnit = pricePerUnit;
        this.quantity = quantity;
        this.createTime = System.currentTimeMillis();
    }

    public abstract NBTTagCompound writeToNBT();

    public void readFromNBT(NBTTagCompound nbt) {
        this.id = UUID.fromString(nbt.getString("id"));
        this.itemStack = new ItemStack(nbt.getCompoundTag("item"));
        this.pricePerUnit = nbt.getLong("price");
        this.quantity = nbt.getInteger("quantity");
        this.createTime = nbt.getLong("createTime");
        this.expireTime = nbt.getLong("expireTime");
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public long getPricePerUnit() {
        return pricePerUnit;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getTotalValue() {
        return pricePerUnit * quantity;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    public long getRemainingTime() {
        return Math.max(0L, expireTime - System.currentTimeMillis());
    }

    public String getRemainingTimeString() {
        return getRemainingTimeComponent().getFormattedText();
    }

    public ITextComponent getRemainingTimeComponent() {
        long remaining = getRemainingTime();
        if (remaining <= 0) {
            return new TextComponentString("§c已过期");
        }
        long hours = remaining / (1000L * 60L * 60L);
        long minutes = (remaining % (1000L * 60L * 60L)) / (1000L * 60L);
        if (hours >= 24) {
            long days = hours / 24L;
            return new TextComponentString(String.format("§a%s天%s小时", days, hours % 24L));
        }
        if (hours > 0) {
            return new TextComponentString(String.format("§a%s小时%s分钟", hours, minutes));
        }
        return new TextComponentString(String.format("§e%s分钟", minutes));
    }

    /** 设置过期时间（相对当前时间的小时数）。 */
    public void setExpireHours(int hours) {
        this.expireTime = System.currentTimeMillis() + hours * 60L * 60L * 1000L;
    }
}
