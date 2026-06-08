package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家所在区块X坐标。
 */
public class ChunkXInfo implements IInfoElement {
    @Override
    public String getValue() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return "0";
        return String.valueOf((int) player.posX >> 4);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
