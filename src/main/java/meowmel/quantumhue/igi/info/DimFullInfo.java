package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;

/**
 * 显示维度名称和ID，格式："主世界 (0)"。
 */
public class DimFullInfo implements IInfoElement {
    @Override
    public String getValue() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return "未知";

        int dimId = world.provider.getDimension();
        String name;
        try {
            name = world.provider.getDimensionType().getName();
        } catch (Exception e) {
            name = "DIM-" + dimId;
        }
        return name + " (" + dimId + ")";
    }

    @Override
    public String toString() {
        return getValue();
    }
}
