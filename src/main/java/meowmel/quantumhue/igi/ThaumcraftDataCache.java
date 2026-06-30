package meowmel.quantumhue.igi;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端缓存：存储从服务端同步的神秘时代灵气数据。
 */
@SideOnly(Side.CLIENT)
public class ThaumcraftDataCache {

    private static float cachedVis = 0;
    private static float cachedFlux = 0;
    private static boolean hasData = false;
    private static long lastUpdateTime = 0;

    public static void updateAura(float vis, float flux) {
        cachedVis = vis;
        cachedFlux = flux;
        hasData = true;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static float getVis() {
        return cachedVis;
    }

    public static float getFlux() {
        return cachedFlux;
    }

    public static boolean hasData() {
        return hasData;
    }

    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }
}
