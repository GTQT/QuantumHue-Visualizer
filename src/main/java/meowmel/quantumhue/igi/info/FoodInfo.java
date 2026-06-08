package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家当前饥饿值 / 饱和度。
 */
public class FoodInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return "0";

        int foodLevel = player.getFoodStats().getFoodLevel();
        float saturation = player.getFoodStats().getSaturationLevel();
        return String.format("%d / %.0f", foodLevel, saturation);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
