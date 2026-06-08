package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * 显示当前生物群系温度。
 */
public class BiomeTempInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;
        if (world == null || player == null) return "0.00";

        BlockPos pos = player.getPosition();
        Biome biome = world.getBiome(pos);
        if (biome == null) return "0.00";

        return String.format("%.2f", biome.getTemperature(pos));
    }

    @Override
    public String toString() {
        return getValue();
    }
}
