package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * 显示MC游戏时间（12小时制 + 昼夜指示）。
 * 格式："10:44PM(白)" 或 "6:30AM(夜)"
 */
public class McTimeFormattedInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "00:00AM";

        long time = world.getWorldTime();
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);

        String ampm;
        int displayHour;
        if (hours == 0) {
            displayHour = 12;
            ampm = "AM";
        } else if (hours < 12) {
            displayHour = hours;
            ampm = "AM";
        } else if (hours == 12) {
            displayHour = 12;
            ampm = "PM";
        } else {
            displayHour = hours - 12;
            ampm = "PM";
        }

        // 判断昼/夜 (6:00-18:00 为白天)
        String dayNight = (hours >= 6 && hours < 18) ? "白" : "夜";

        return String.format("%d:%02d%s(%s)", displayHour, minutes, ampm, dayNight);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
