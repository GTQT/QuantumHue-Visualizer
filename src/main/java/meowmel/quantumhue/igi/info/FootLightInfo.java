package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 显示玩家脚下方块的光照等级（blockLight + skyLight 合计）。
 */
public class FootLightInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;
        if (world == null || player == null) return "0";

        BlockPos pos = player.getPosition();
        return String.valueOf(world.getLightFromNeighbors(pos));
    }

    @Override
    public String toString() {
        return getValue();
    }
}
