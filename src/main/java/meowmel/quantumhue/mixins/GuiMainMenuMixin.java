package meowmel.quantumhue.mixins;

import meowmel.quantumhue.api.utils.ClientHelper;
import meowmel.quantumhue.menu.GtqtMenuChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin {

    /**
     * Deletes vanilla's six-face panorama cube and draws the mod's own backdrop in its place.
     *
     * <p>The original body is not edited but cancelled at HEAD, so the
     * {@code textures/gui/title/background/panorama_*.png} cube is never even bound.
     *
     * <p>This is not only the title screen's background. {@link ClientHelper#renderWorldBackground}
     * routes every other menu through {@link ClientHelper#renderPanorama}, which calls this very
     * method through {@link GuiMainMenuAccessor} — so the replacement has to be self-contained.
     * It deliberately does not touch the GL matrices: vanilla pushed and restored a perspective
     * projection here, whereas all callers are already inside their own GUI ortho and expect to
     * still be in it when this returns.
     */
    @Inject(method = "renderSkybox", at = @At("HEAD"), cancellable = true)
    private void quantumhue$replaceSkybox(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!GtqtMenuChrome.enabled()) {
            return;
        }
        GuiScreen self = (GuiScreen) (Object) this;
        GtqtMenuChrome.drawBackground(Minecraft.getMinecraft(), self.width, self.height);
        ci.cancel();
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ClientHelper.MENU_INSTANCE = (GuiMainMenu) (Object) this;
    }
}
