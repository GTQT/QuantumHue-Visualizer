package meowmel.quantumhue.market;

import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** 收购订单：系统订单与玩家收购共用一份结构。 */
public class BuyOrder extends MarketItem {

    public static final UUID SYSTEM_SELLER_UUID = new UUID(0L, 0L);

    public int totalBought;
    public boolean isSystemOrder;

    public BuyOrder(ItemStack itemStack, long pricePerUnit, int quantity, boolean isSystemOrder) {
        super(UUID.randomUUID(), itemStack, pricePerUnit, quantity);
        this.totalBought = 0;
        this.isSystemOrder = isSystemOrder;
        setExpireHours(isSystemOrder ? systemHours() : playerHours());
    }

    public BuyOrder(NBTTagCompound nbt) {
        super();
        readFromNBT(nbt);
        this.totalBought = nbt.getInteger("bought");
        this.isSystemOrder = nbt.getBoolean("system");
    }

    private static int systemHours() {
        try {
            return PageConfigs.shop().market.systemOrderHours;
        } catch (Throwable ignored) {
            return 2;
        }
    }

    private static int playerHours() {
        try {
            return PageConfigs.shop().market.playerOrderHours;
        } catch (Throwable ignored) {
            return 72;
        }
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", id.toString());
        nbt.setTag("item", itemStack.writeToNBT(new NBTTagCompound()));
        nbt.setLong("price", pricePerUnit);
        nbt.setInteger("quantity", quantity);
        nbt.setInteger("bought", totalBought);
        nbt.setLong("createTime", createTime);
        nbt.setLong("expireTime", expireTime);
        nbt.setBoolean("system", isSystemOrder);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.totalBought = nbt.getInteger("bought");
        this.isSystemOrder = nbt.getBoolean("system");
    }

    public int getRemaining() {
        return Math.max(0, quantity - totalBought);
    }

    public boolean isCompleted() {
        return totalBought >= quantity;
    }
}
