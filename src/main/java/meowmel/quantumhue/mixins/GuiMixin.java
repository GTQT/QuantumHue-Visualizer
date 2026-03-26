package meowmel.quantumhue.mixins;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    /**
     * 拦截所有 drawGradientRect 调用，但只取消来自 GuiMainMenu.drawScreen 的
     */
    @Inject(
            method = "drawGradientRect(IIIIII)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void filterGuiMainMenuGradient(
            int left, int top, int right, int bottom,
            int startColor, int endColor,
            CallbackInfo ci
    ) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            StackTraceElement element = stack[i];
            if ("net.minecraft.client.gui.GuiMainMenu".equals(element.getClassName()) &&
                    "drawScreen".equals(element.getMethodName())) {
                ci.cancel();
                return;
            }
        }
    }
}