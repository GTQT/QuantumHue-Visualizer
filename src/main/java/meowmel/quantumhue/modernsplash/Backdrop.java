package meowmel.quantumhue.modernsplash;

import static meowmel.quantumhue.modernsplash.SplashTheme.GOLD;
import static meowmel.quantumhue.modernsplash.SplashTheme.LINE;
import static meowmel.quantumhue.modernsplash.SplashTheme.SHELL_PANEL;
import static meowmel.quantumhue.modernsplash.SplashTheme.VOID;
import static meowmel.quantumhue.modernsplash.SplashTheme.VOID_LOW;

/**
 * The one background, shared by every screen that shows it.
 *
 * <p>Three layers, bottom to top: an opaque vertical wash, the {@link PcbTraces} board, and the
 * drifting grid.  That is the whole of it — the Dyson swarm, the HUD, the buttons and everything
 * else are foreground and are drawn by their own screen on top.
 *
 * <p>It is called from four places and they must all produce the same picture:
 *
 * <ul>
 *   <li>{@link SplashScene} — the boot splash and its loading HUD, in the splash ortho;</li>
 *   <li>the main menu, in plain GUI coordinates;</li>
 *   <li>every other menu, because {@code ClientHelper.renderPanorama} reaches the same
 *       {@code renderSkybox} replacement the main menu uses;</li>
 *   <li>{@code tools/SplashPreview} and {@code tools/MenuPreview}, so a regression is visible
 *       without launching the game.</li>
 * </ul>
 *
 * <p>Written against {@link SplashPainter} and therefore free of Minecraft / LWJGL imports.  The
 * caller supplies its own origin, so the splash's centre-based ortho and the menu's top-left GUI
 * space both work: only {@code w} and {@code h} have to agree for the two to look identical.
 */
public final class Backdrop {

    private Backdrop() {}

    /**
     * Seconds within which the board's pulses repeat.
     *
     * <p>This is also the wrap window for their phase.  It has to exist: the clock handed in comes
     * from {@code Minecraft.getSystemTime()}, which is milliseconds since boot, so feeding it on
     * unwrapped makes the pulse position quantise to a standstill once the machine has been up for
     * a few hours.
     */
    public static final float PERIOD = 120f;

    /**
     * Grid cell, as a fraction of the canvas's short side.
     *
     * <p>Relative for the same reason {@code PcbTraces.PITCH_RATIO} is: the splash draws in device
     * pixels and the menu in scaled GUI units, so an absolute cell would make the menu's grid
     * three times coarser at GUI scale 3.  64/720 reproduces the pitch the hero shot was tuned at,
     * so the splash does not move.
     */
    private static final float CELL_RATIO = 64f / 720f;

    /** Grid drift, in cells per second.  6/64 is the rate the hero shot was tuned at. */
    private static final float DRIFT_CELLS_PER_SEC = 6f / 64f;

    /** Bands in the vertical wash.  Both painters only need plain rectangles for this. */
    private static final int BANDS = 64;

    /** Reused every frame; the generator is deterministic, so only the pulses move. */
    private static final PcbTraces.Buffers BOARD = new PcbTraces.Buffers();

    /**
     * Paints the whole background.
     *
     * @param left    left edge of the area to cover, in the painter's own coordinates
     * @param top     top edge of the area to cover
     * @param w       width to cover
     * @param h       height to cover
     * @param seconds a monotonically increasing clock.  Taken as a {@code double} on purpose: the
     *                in-game caller feeds {@code getSystemTime() / 1000.0}, milliseconds since boot,
     *                and at a few hours of uptime the float ulp there is already several
     *                milliseconds — enough to freeze the drift.  Each layer wraps internally, so
     *                callers pass the raw clock and never have to window it themselves.
     */
    public static void paint(SplashPainter p, float left, float top, float w, float h, double seconds) {
        wash(p, left, top, w, h);
        board(p, left, top, w, h, seconds);
        grid(p, left, top, w, h, seconds);
    }

    // ---------------------------------------------------------------- layers

    /**
     * The opaque base.
     *
     * <p>Opaque on purpose, and that is a feature rather than a detail: this is what buries
     * whatever the screen underneath had painted — the vanilla panorama, the {@code minecraft.png}
     * title image, the {@code edition.png} ribbon, the two full-screen gradients — without any
     * screen having to cancel them one by one.
     */
    private static void wash(SplashPainter p, float left, float top, float w, float h) {
        p.rect(left, top, w, h, VOID[0], VOID[1], VOID[2], 1f);

        float bandH = h / BANDS;
        for (int i = 0; i < BANDS; i++) {
            float t = i / (float) (BANDS - 1);
            // Squared so the light pools towards the bottom instead of ramping evenly.
            float u = t * t;
            p.rect(left, top + i * bandH, w, bandH + 1f,
                    SplashTimeline.lerp(VOID[0], VOID_LOW[0], u),
                    SplashTimeline.lerp(VOID[1], VOID_LOW[1], u),
                    SplashTimeline.lerp(VOID[2], VOID_LOW[2], u),
                    1f);
        }
    }

    /** The printed circuit board: copper traces, vias on every corner, and the pulses. */
    private static void board(SplashPainter p, float left, float top, float w, float h, double seconds) {
        PcbTraces.build((int) w, (int) h, (float) (seconds % PERIOD), left, top, BOARD);

        p.blendMode(false);
        p.lines(BOARD.lines, BOARD.lineCount, LINE[0], LINE[1], LINE[2], SplashTheme.Layer.PCB_TRACE, 1f);
        p.quads(BOARD.vias, BOARD.viaCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], SplashTheme.Layer.PCB_VIA);
        // Pulses are light travelling down copper, so they accumulate additively.
        p.blendMode(true);
        p.lines(BOARD.pulses, BOARD.pulseCount,
                GOLD[0], GOLD[1], GOLD[2], SplashTheme.Layer.PCB_PULSE, 1.4f);
        p.blendMode(false);
    }

    private static void grid(SplashPainter p, float left, float top, float w, float h, double seconds) {
        float cell = Math.max(8f, Math.min(w, h) * CELL_RATIO);
        float drift = (float) ((seconds * DRIFT_CELLS_PER_SEC * cell) % cell);
        float right = left + w;
        float bottom = top + h;
        float a = SplashTheme.Layer.GRID;

        for (float x = left + drift; x < right; x += cell) {
            p.line(x, top, x, bottom, LINE[0], LINE[1], LINE[2], a, 1f);
        }
        for (float y = top + drift; y < bottom; y += cell) {
            p.line(left, y, right, y, LINE[0], LINE[1], LINE[2], a, 1f);
        }
    }
}
