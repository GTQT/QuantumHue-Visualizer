package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import meowmel.quantumhue.igi.ThaumcraftDataCache;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 本地咒波（Flux）显示。
 * 数据由服务端同步至客户端缓存。
 */
@SideOnly(Side.CLIENT)
public class FluxInfo implements IInfoElement {

    @Override
    public String getValue() {
        if (!ThaumcraftDataCache.hasData()) {
            return "N/A";
        }
        return String.format("%.1f", ThaumcraftDataCache.getFlux());
    }
}
