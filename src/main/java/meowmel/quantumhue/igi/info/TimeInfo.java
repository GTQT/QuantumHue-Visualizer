package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * 显示游戏内时间（tick 和 格式化时间）。
 */
public class TimeInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "00:00";

        long time = world.getWorldTime();
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
