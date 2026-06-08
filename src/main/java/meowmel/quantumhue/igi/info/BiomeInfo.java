package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * 显示玩家当前所在的生物群系。
 */
public class BiomeInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;
        if (world == null || player == null) return "未知";

        BlockPos pos = player.getPosition();
        Biome biome = world.getBiome(pos);
        return biome != null ? biome.getBiomeName() : "未知";
    }

    @Override
    public String toString() {
        return getValue();
    }
}
