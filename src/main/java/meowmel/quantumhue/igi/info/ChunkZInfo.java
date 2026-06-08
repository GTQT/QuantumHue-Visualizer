package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家所在区块Z坐标。
 */
public class ChunkZInfo implements IInfoElement {
    @Override
    public String getValue() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return "0";
        return String.valueOf((int) player.posZ >> 4);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
