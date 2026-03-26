package meowmel.quantumhue.mixins;

import meowmel.quantumhue.api.utils.ClientHelper;
import meowmel.quantumhue.mixininterface.IGuiMainMenuMixin;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract  class GuiMainMenuMixin implements IGuiMainMenuMixin {

    @Shadow
    private float panoramaTimer;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.panoramaTimer = ((GuiMainMenuAccessor) ClientHelper.MENU_INSTANCE).getPanoramaTimer();
        ClientHelper.MENU_INSTANCE = (GuiMainMenu) (Object) this;
    }

    @Unique
    @Override
    public void clearMyBackground$tickPanoramaTimer(float partialTicks) {
        this.panoramaTimer += partialTicks;
    }
}