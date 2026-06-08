package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * 显示 MSPT（Milliseconds Per Tick，每 Tick 耗时毫秒数）。
 * 50ms 为 20 TPS 的标准值，仅在单机/局域网游戏中可用。
 */
public class MsptInfo implements IInfoElement {
    @Override
    public String getValue() {
        MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) return "N/A";

        long[] tickTimes = server.tickTimeArray;
        int i = server.getTickCounter() % tickTimes.length;
        long total = 0;
        for (int j = 0; j < tickTimes.length; j++) {
            total += tickTimes[j];
        }
        double avgMs = total / (double) tickTimes.length / 1000000.0;

        return String.format("%.1fms", avgMs);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
