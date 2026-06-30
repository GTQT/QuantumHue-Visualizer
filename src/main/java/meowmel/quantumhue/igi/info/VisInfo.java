package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import meowmel.quantumhue.igi.ThaumcraftDataCache;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 本地灵气（Vis）显示。
 * 数据由服务端同步至客户端缓存。
 */
@SideOnly(Side.CLIENT)
public class VisInfo implements IInfoElement {

    @Override
    public String getValue() {
        if (!ThaumcraftDataCache.hasData()) {
            return "N/A";
        }
        return String.format("%.1f", ThaumcraftDataCache.getVis());
    }
}
