package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 显示玩家朝向（中文八方：北/东北/东/东南/南/西南/西/西北）。
 */
public class FacingInfo implements IInfoElement {
    private static final String[] DIRECTIONS = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};

    @Override
    public String getValue() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return "?";

        float yaw = player.rotationYaw % 360;
        if (yaw < 0) yaw += 360;

        // 每个扇区 45°，从南开始顺时针
        int index = (int) ((yaw + 22.5f) / 45.0f) % 8;
        return DIRECTIONS[index];
    }

    @Override
    public String toString() {
        return getValue();
    }
}
