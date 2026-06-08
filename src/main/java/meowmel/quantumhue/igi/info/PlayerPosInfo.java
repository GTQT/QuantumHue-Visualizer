package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家当前坐标（X, Y, Z）。
 */
public class PlayerPosInfo implements IInfoElement {
    @Override
    public String getValue() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return "0, 0, 0";

        return String.format("%.0f, %.0f, %.0f", player.posX, player.posY, player.posZ);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
