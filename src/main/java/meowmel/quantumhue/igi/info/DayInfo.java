package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * 显示当前游戏天数。
 */
public class DayInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "0";

        long totalTime = world.getTotalWorldTime();
        int days = (int) (totalTime / 24000);
        return String.valueOf(days);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
