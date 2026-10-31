package meowmel.quantumhue.mail;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 邮件奖励：一个物品 + 元数据 + 数量。
 *
 * <p>只接受注册名，不接受命令、脚本或任意 NBT；解析失败时返回 {@link ItemStack#EMPTY}，由服务端
 * 跳过该条奖励并记录日志。真正发放时还会再按背包上限拆分。
 */
public class MailReward {

    public String item = "";
    public int meta;
    public int count = 1;

    public boolean normalize() {
        if (item == null) {
            item = "";
        }
        item = item.trim();
        if (item.isEmpty() || item.length() > 128) {
            return false;
        }
        if (meta < 0) {
            meta = 0;
        }
        if (count < 1) {
            count = 1;
        }
        if (count > 4096) {
            count = 4096;
        }
        return true;
    }

    /** 解析成物品堆；注册名无效时返回 EMPTY。 */
    public ItemStack resolve() {
        if (item == null || item.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation key = new ResourceLocation(item.trim());
            Item found = ForgeRegistries.ITEMS.getValue(key);
            if (found == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(found, count, Math.max(0, meta));
        } catch (Throwable invalid) {
            return ItemStack.EMPTY;
        }
    }
}
