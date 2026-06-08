package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * 显示当前服务端 TPS（Ticks Per Second）。
 * 仅在单机/局域网游戏中可用，远程服务器显示 "N/A"。
 */
public class TpsInfo implements IInfoElement {
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
        double tps = Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));

        return String.format("%.1f", tps);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
