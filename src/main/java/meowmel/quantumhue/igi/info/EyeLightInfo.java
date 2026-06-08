package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 显示玩家眼部高度方块的光照等级。
 */
public class EyeLightInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;
        if (world == null || player == null) return "0";

        BlockPos eyePos = new BlockPos(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        return String.valueOf(world.getLightFromNeighbors(eyePos));
    }

    @Override
    public String toString() {
        return getValue();
    }
}
