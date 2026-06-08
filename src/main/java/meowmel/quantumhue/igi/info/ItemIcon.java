package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.item.ItemStack;

/**
 * 小物品图标控件。
 * 在HUD行中渲染一个ItemStack图标，图标大小与该行字体高度一致。
 * <p>
 * 使用示例：
 * <pre>
 * .info("主手: ", new ItemIcon(player.getHeldItemMainhand()), " 物品名")
 * </pre>
 */
public class ItemIcon implements IInfoElement {
    private final ItemStack stack;

    public ItemIcon(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * 获取要渲染的物品栈。
     */
    public ItemStack getStack() {
        return stack;
    }

    /**
     * 返回空字符串作为文本占位（图标通过独立渲染通道绘制）。
     */
    @Override
    public String getValue() {
        return "";
    }
}
