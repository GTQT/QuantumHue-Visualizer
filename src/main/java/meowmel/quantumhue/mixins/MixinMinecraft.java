package meowmel.quantumhue.mixins;

import meowmel.quantumhue.QuantumHueConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
/**
 * Logic from SmoothScrollingEverywhere:
 * <a href=
 * "https://github.com/shedaniel/SmoothScrollingEverywhere/blob/forge-1.12.2/src/main/java/me/shedaniel/smoothscrollingeverywhere/mixin/MixinMinecraft.java">...</a>
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Shadow public GameSettings gameSettings;

    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void getLimitFramerate(CallbackInfoReturnable<Integer> info) {
        if (QuantumHueConfig.smoothScrolling.unlimitFps)
            info.setReturnValue(this.gameSettings.limitFramerate);
    }
}