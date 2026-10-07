package meowmel.quantumhue.menu;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.modernsplash.SplashTheme;
import meowmel.quantumhue.modernsplash.WorldLoadingScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Draws {@link WorldLoadingScene} into the game.
 *
 * <p>Thin on purpose: the composition lives in the MC-free scene so {@code tools/SplashPreview}
 * renders the identical picture, and everything here is the plumbing that scene cannot do — the
 * clock, the config gate, and taking the strings off vanilla's private fields.
 */
public final class WorldLoadingChrome {

    /** Reused every frame rather than reallocated; the scene only reads it. */
    private static final WorldLoadingScene.State STATE = new WorldLoadingScene.State();

    private WorldLoadingChrome() {}

    public static boolean enabled() {
        try {
            return QuantumHueConfig.worldLoading.enabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * @param title    vanilla's title line, may be null
     * @param detail   vanilla's detail line, may be null
     * @param progress 0..100, or negative when the caller has no value yet
     */
    public static void draw(Minecraft mc, ScaledResolution resolution,
                            String title, String detail, int progress) {
        // The same renderer also draws "Saving world" when the player quits to the title screen, and
        // announcing a Dyson connection there would be wrong.  mc.world is null exactly while a
        // world is still being loaded, which separates the two cases without pattern-matching on
        // localised text.
        boolean loading = mc.world == null;

        STATE.width = resolution.getScaledWidth();
        STATE.height = resolution.getScaledHeight();
        // GlStatePainter draws in GUI space, so the canvas starts at the top-left corner.
        STATE.left = 0f;
        STATE.top = 0f;
        STATE.title = loading ? SplashTheme.WORLD_TITLE : (title == null ? "" : title);
        STATE.detail = loading ? SplashTheme.WORLD_STAGE : (detail == null ? "" : detail);
        // Vanilla's own bar is driven by an int percentage, and it is -1 before the first report.
        STATE.progress = progress < 0 ? -1f : progress / 100f;
        STATE.seconds = Minecraft.getSystemTime() / 1000.0;

        WorldLoadingScene.draw(GlStatePainter.get(mc), STATE);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
