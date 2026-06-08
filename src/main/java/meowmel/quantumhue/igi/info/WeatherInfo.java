package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * 显示当前天气状态。
 */
public class WeatherInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "未知";

        if (world.isRaining() && world.isThundering()) {
            return "雷雨";
        } else if (world.isRaining()) {
            return "雨天";
        } else {
            return "晴天";
        }
    }

    @Override
    public String toString() {
        return getValue();
    }
}
