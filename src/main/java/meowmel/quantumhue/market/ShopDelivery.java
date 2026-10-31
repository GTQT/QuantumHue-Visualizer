package meowmel.quantumhue.market;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * 把商品交到玩家手里。
 *
 * <p>不能用 {@code setCount(total)} 直接塞：一次购买的总数可能超过 64，而超过最大堆叠的
 * {@code ItemStack} 在背包里是非法状态。这里按物品自身的最大堆叠数拆分，背包塞不下就掉在脚边。
 */
public final class ShopDelivery {

    private ShopDelivery() {}

    public static void give(EntityPlayer player, ItemStack prototype, int totalCount) {
        if (player == null || prototype == null || prototype.isEmpty() || totalCount <= 0) {
            return;
        }
        int remaining = totalCount;
        int max = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(max, remaining);
            ItemStack stack = prototype.copy();
            stack.setCount(size);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
            remaining -= size;
        }
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventory.markDirty();
        }
    }
}
