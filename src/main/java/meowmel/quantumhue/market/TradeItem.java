package meowmel.quantumhue.market;

import meowmel.quantumhue.config.PageConfigs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** 玩家（或系统）挂出的一口价商品。 */
public class TradeItem extends MarketItem {

    public UUID sellerUUID;
    public String sellerName;

    public TradeItem(ItemStack itemStack, long pricePerUnit, int quantity, UUID sellerUUID, String sellerName) {
        super(UUID.randomUUID(), itemStack, pricePerUnit, quantity);
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        setExpireHours(listingHours());
    }

    public TradeItem(NBTTagCompound nbt) {
        super();
        readFromNBT(nbt);
    }

    private static int listingHours() {
        try {
            return PageConfigs.shop().market.playerListingHours;
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
        nbt.setString("sellerUUID", sellerUUID == null ? new UUID(0L, 0L).toString() : sellerUUID.toString());
        nbt.setString("sellerName", sellerName == null ? "" : sellerName);
        nbt.setLong("createTime", createTime);
        nbt.setLong("expireTime", expireTime);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.sellerUUID = UUID.fromString(nbt.getString("sellerUUID"));
        this.sellerName = nbt.getString("sellerName");
    }
}
