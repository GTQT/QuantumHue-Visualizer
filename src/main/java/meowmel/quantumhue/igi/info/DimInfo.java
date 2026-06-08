package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;

/**
 * 显示玩家所在维度信息。
 */
public class DimInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null) return "未知";

        DimensionType dimType = world.provider.getDimensionType();

        // 使用维度名称
        String dimName;
        try {
            dimName = dimType.getName();
        } catch (Exception e) {
            dimName = "DIM-" + world.provider.getDimension();
        }

        return dimName;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
