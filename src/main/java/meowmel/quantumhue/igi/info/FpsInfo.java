package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;
import net.minecraft.client.Minecraft;

/**
 * 显示当前FPS。
 */
public class FpsInfo implements IInfoElement {
    @Override
    public String getValue() {
        return String.valueOf(Minecraft.getDebugFPS());
    }

    @Override
    public String toString() {
        return getValue();
    }
}
