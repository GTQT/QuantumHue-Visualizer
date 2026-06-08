package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;

/**
 * 显示当前玩家名称。
 */
public class PlayerNameInfo implements IInfoElement {
    @Override
    public String getValue() {
        return Minecraft.getMinecraft().player != null
                ? Minecraft.getMinecraft().player.getName()
                : "???";
    }

    @Override
    public String toString() {
        return getValue();
    }
}
