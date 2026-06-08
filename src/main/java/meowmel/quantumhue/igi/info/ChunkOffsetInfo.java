package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家在区块内的偏移量，格式："X Z"。
 */
public class ChunkOffsetInfo implements IInfoElement {
    @Override
    public String getValue() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return "0 0";
        int offX = (int) player.posX & 15;
        int offZ = (int) player.posZ & 15;
        return " X: "+offX + " Z: " + offZ;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
