package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * 显示MC游戏日期，格式："第X年第Y天"。
 */
public class McDateInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "第1年第1天";

        long totalDays = world.getTotalWorldTime() / 24000;
        int year = (int) (totalDays / 365) + 1;
        int dayOfYear = (int) (totalDays % 365) + 1;
        return "第" + year + "年第" + dayOfYear + "天";
    }

    @Override
    public String toString() {
        return getValue();
    }
}
