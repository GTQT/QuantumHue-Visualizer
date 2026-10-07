package meowmel.quantumhue.mixins;

import meowmel.quantumhue.menu.WorldLoadingChrome;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiNotification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * Replaces the screen shown while a world loads and the player is about to enter it.
 *
 * <p>That screen is {@link LoadingScreenRenderer#setLoadingProgress(int)}: vanilla clears the
 * loading framebuffer, sets up a GUI ortho, tiles {@code options_background.png} over the whole
 * thing, then draws two strings and a progress bar.  All of that content lives in one method, and
 * Forge left a lever inside it — {@code FMLClientHandler.handleLoadingScreen} — whose return value
 * decides whether vanilla draws its content at all.
 *
 * <h3>Why that lever and not an overwrite</h3>
 * Returning {@code true} makes vanilla jump straight to its own teardown: the framebuffer is still
 * unbound and blitted, and the matrices are still left exactly as vanilla leaves them.  Overwriting
 * the method would mean reproducing that framebuffer dance by hand, which is the part most likely to
 * break.  The redirect is also evaluated in place, so our content is drawn into the loading
 * framebuffer at exactly the point vanilla's would have been, with the ortho and the
 * {@code translate(0, 0, -200)} already applied.
 *
 * <h3>The one case that must not be swallowed</h3>
 * {@code handleLoadingScreen} returns {@code true} on its own when the current screen is a
 * {@link GuiNotification} — Forge's "a mod failed to load" panel, which it draws itself.  That
 * frame is the one time the original must be called, or the error panel becomes invisible.
 *
 * <h3>The build warning, and why it is silenced rather than tolerated</h3>
 * The annotation processor emitted, on every build:
 * <pre>
 *   warning: Unable to locate method mapping for @At(INVOKE.&lt;target&gt;)
 *   'Lnet/minecraftforge/fml/client/FMLClientHandler;handleLoadingScreen(...)Z'
 * </pre>
 * The target is correct and must not be rewritten.  {@code FMLClientHandler} is a Forge class, so it
 * is absent from Minecraft's obfuscation mappings and there is nothing to rewrite — for a Forge
 * symbol, "no mapping found" is the right answer precisely because Forge names are not obfuscated.
 * When the processor cannot map a target it leaves it verbatim, which is what the runtime bytecode
 * contains.
 *
 * <p>Verified rather than assumed: the project's own generated refmap keeps class names intact and
 * only rewrites members ({@code Lnet/minecraft/client/gui/GuiScreen;func_146278_c(I)V}), so
 * {@code Lnet/minecraft/client/gui/ScaledResolution;} is the production spelling too; and
 * {@code javap} on the patched class shows the call site as
 * {@code invokevirtual FMLClientHandler.handleLoadingScreen:(Lnet/minecraft/client/gui/ScaledResolution;)Z},
 * matching the target character for character.
 *
 * <p>This is in fact the safest possible shape for an {@code @At} INVOKE: because nothing is
 * rewritten, nothing can be rewritten wrongly.  An earlier attempt at this project targeted an MC
 * method whose owner the processor <em>did</em> rewrite, producing an owner that matched no
 * instruction in dev or production and failing silently; that class of failure cannot happen here.
 *
 * <p>So the warning is not a symptom and it is not suppressed by hiding it — the processor has a
 * documented opt-out for exactly this case.  The message is emitted as
 * {@code IMessagerEx.printMessage(..., SuppressedBy.MAPPING)}, and
 * {@code AnnotatedMixins.shouldSuppress} returns early when the element — or any enclosing element,
 * walking out to the class — carries {@code @SuppressWarnings} with the token
 * {@code SuppressedBy.MAPPING.getToken()}, which is the string {@code "mapping"}.  Suppressing
 * {@code "mapping"} silences only the "no obfuscation data for this member" family; every other
 * mixin diagnostic still comes through.  {@code @At(remap = false)} would also silence it, by
 * declaring the target unmappable, but it says less about why and leaves the target looking
 * remappable everywhere else.
 */
@SuppressWarnings("mapping")
@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenRendererMixin {

    @Shadow
    private String message;

    @Shadow
    private String currentlyDisplayedText;

    /** The value handed to the current call, which the redirect has no other way to see. */
    @Unique
    private int quantumhue$progress = -1;

    @Inject(method = "setLoadingProgress", at = @At("HEAD"))
    private void quantumhue$captureProgress(int progress, CallbackInfo ci) {
        this.quantumhue$progress = progress;
    }

    @Redirect(method = "setLoadingProgress",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/client/FMLClientHandler;"
                            + "handleLoadingScreen(Lnet/minecraft/client/gui/ScaledResolution;)Z"))
    private boolean quantumhue$worldLoadingScreen(FMLClientHandler handler, ScaledResolution resolution) {
        Minecraft mc = Minecraft.getMinecraft();

        // Forge's own failure panel draws itself through this call; let it through untouched.
        if (mc.currentScreen instanceof GuiNotification) {
            try {
                return handler.handleLoadingScreen(resolution);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!WorldLoadingChrome.enabled()) {
            return false;
        }

        WorldLoadingChrome.draw(mc, resolution, this.currentlyDisplayedText, this.message,
                this.quantumhue$progress);
        // Tell vanilla its content is handled, so the dirt, the strings and the two-tone bar are
        // skipped and only its framebuffer teardown runs.
        return true;
    }
}
