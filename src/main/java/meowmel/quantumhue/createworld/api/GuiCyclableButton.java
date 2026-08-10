package meowmel.quantumhue.createworld.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import javax.annotation.Nonnull;

/**
 * 可循环按钮——点击或滚轮都会触发 {@link CycleHandler} 并在 {@link TextSupplier} 刷新文案。
 * <p>A cyclable button: both click and scroll wheel trigger the cycle handler,
 * and the display text is refreshed from the text supplier.</p>
 */
public class GuiCyclableButton extends GuiButton {

    private final CycleHandler handler;
    private final TextSupplier textSupplier;

    public GuiCyclableButton(int id, int x, int y, int width, int height, TextSupplier textSupplier, CycleHandler handler) {
        super(id, x, y, width, height, "");
        this.handler = handler;
        this.textSupplier = textSupplier;
        this.updateText();
    }

    /** 鼠标滚轮切换（由 handleMouseInput 的 mixin 转发） */
    public void mouseScrolled(int delta) {
        if (!this.enabled) {
            return;
        }
        int direction = Integer.signum(delta);
        if (direction == 0) {
            return;
        }
        if (this.handler != null) {
            this.handler.onCycle(direction);
        }
        this.updateText();
    }

    @Override
    public boolean mousePressed(@Nonnull Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            if (this.handler != null) {
                this.handler.onCycle(1);
            }
            this.updateText();
            return true;
        }
        return false;
    }

    /** 用 TextSupplier 刷新显示文案 */
    public void updateText() {
        if (this.textSupplier != null) {
            this.displayString = this.textSupplier.getText();
        }
    }

    @FunctionalInterface
    public interface TextSupplier {
        String getText();
    }

    @FunctionalInterface
    public interface CycleHandler {
        void onCycle(int direction);
    }
}
