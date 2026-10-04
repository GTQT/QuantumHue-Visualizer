package meowmel.quantumhue.modernsplash;

/**
 * The look: one palette and one set of metrics shared by the splash screen and the main menu, so
 * both screens read as the same product.
 *
 * <p>Aesthetic target: hard sci-fi instrumentation — near-black violet void, hairline chrome,
 * one violet accent and one gold accent, nothing else.  No bevels, no gradients-on-buttons, no
 * drop shadows except a single soft bloom behind the emblem.
 */
public final class SplashTheme {

    private SplashTheme() {}

    // ---------------------------------------------------------------- palette

    /** Void, top of the backdrop. */
    public static final float[] VOID = {0.027f, 0.020f, 0.047f};
    /** Void, bottom of the backdrop — slightly warmer so the emblem sits in a pool of light. */
    public static final float[] VOID_LOW = {0.070f, 0.043f, 0.106f};

    /** Structural hairlines. */
    public static final float[] LINE = {0.44f, 0.31f, 0.64f};
    /** Violet accent, used for progress fill and active marks. */
    public static final float[] ACCENT = {0.66f, 0.37f, 0.86f};
    /** Dimmed accent for inert fills. */
    public static final float[] ACCENT_DIM = {0.36f, 0.21f, 0.50f};
    /** Gold accent, reserved for the leading edge of progress and the glyph flash. */
    public static final float[] GOLD = {0.97f, 0.78f, 0.24f};

    /** Primary readout text. */
    public static final float[] TEXT = {0.90f, 0.88f, 0.95f};
    /** Secondary / label text. */
    public static final float[] TEXT_DIM = {0.54f, 0.49f, 0.64f};
    /** Pure white, for the emblem and the title glyphs (they are tinted at draw time). */
    public static final float[] WHITE = {1f, 1f, 1f};

    /**
     * Pale violet for the Dyson swarm's collector panels and for the PCB vias.
     *
     * <p>Shared rather than duplicated because the same colour is used by three painter paths now
     * ({@link Backdrop}, {@link SplashScene}'s swarm, and the menu's swarm).
     */
    public static final float[] SHELL_PANEL = {0.72f, 0.62f, 0.90f};

    // ---------------------------------------------------------------- strings
    // The in-game splash font is ASCII only, so every HUD string here stays Latin.
    // The Chinese product name lives in gtqt_subtitle.png as glyph art.

    public static final String BRAND = "GTQT";
    public static final String BRAND_FULL = "GREG TECH : QUANTUM TRANSITION";
    public static final String BOOT_LABEL = "BOOT SEQUENCE";
    public static final String MEM_LABEL = "HEAP";

    // ---------------------------------------------------------------- layer strengths

    /**
     * Alpha of every backdrop layer, in one place.
     *
     * <p>These live here rather than in the two painters because the main menu and the offline
     * previews ({@code tools/MenuPreview.java}, {@code tools/SplashPreview.java}) each have their
     * own draw call: the class that knows the palette is MC-free, the classes that own the GL state
     * are not.  Keeping the numbers in the shared, MC-free half is what stops the preview from
     * showing a picture the game never renders.
     *
     * <p>The first set of values was far too conservative: at a trace alpha of 0.045 the 99th
     * percentile of the PCB-only half of the screen was the background colour itself, so the board
     * was not dim — it was absent, and the screen read as empty rather than as dark.  Chrome is
     * meant to be quiet, not invisible.  Measured on {@code tools/MenuPreview}, about 3% of that
     * half now carries visible routing.
     */
    public static final class Layer {
        private Layer() {}

        /** Slow drift grid. */
        public static final float GRID = 0.075f;
        /** PCB copper traces. */
        public static final float PCB_TRACE = 0.26f;
        /** PCB vias on the traces. */
        public static final float PCB_VIA = 0.30f;
        /** PCB signal pulses, drawn additive. */
        public static final float PCB_PULSE = 0.60f;

        /** Swarm panels behind the shell's equator. */
        public static final float SWARM_BACK = 0.085f;
        /** Swarm panels in front of it. */
        public static final float SWARM_FRONT = 0.13f;
        /** Structural great-circle rings, behind / in front. */
        public static final float RING_BACK = 0.30f;
        public static final float RING_FRONT = 0.42f;
        /** Travelling energy arcs, behind / in front — the brightest thing in the backdrop. */
        public static final float ARC_BACK = 0.60f;
        public static final float ARC_FRONT = 0.85f;

        /** The screen-edge rails and the instrument column spine. */
        public static final float RAIL = 0.32f;
    }

    // ---------------------------------------------------------------- metrics

    /** Inset of every HUD element from the screen edge. */
    public static final float INSET = 40f;
    /** Vertical offset of the top / bottom hairline from the screen edge. */
    public static final float RAIL_OFFSET = 46f;
    /** Length of the corner brackets. */
    public static final float BRACKET = 26f;
    /** Height of a progress bar's track. */
    public static final float BAR_TRACK = 6f;
    /** Vertical pitch between two stacked progress bars. */
    public static final float BAR_ROW = 46f;
    /** Width of the progress bar block, before clamping to the window. */
    public static final float BAR_BLOCK = 460f;
    /** Width of the memory readout block. */
    public static final float MEM_BLOCK = 300f;

    /**
     * Alpha of the emblem once the loading HUD has taken over.
     *
     * <p>Full strength on purpose.  The emblem sits above centre and the HUD owns the rails and the
     * bottom corners, so nothing overlaps it — dimming it to act as a "watermark" only made the
     * mark look washed out for no legibility gain.
     */
    public static final float STEADY_LOGO_ALPHA = 1.0f;

    /** Aspect of gtqt_title.png (1024x256: 4 cells of 256). */
    public static final float TITLE_ASPECT = 4f;
    /** Aspect of gtqt_subtitle.png (1024x128: cell 128, 8 cells of width). */
    public static final float SUB_ASPECT = 8f;

    // Measured from the generated artwork so the bloom and reticle land on the gear rather than
    // on guessed coordinates:
    //   purple gear bbox x[113..909] y[38..928] -> optical centre (0.499, 0.4717) of the texture
    //   golden coil annulus median radius 0.1907 of the texture width
    public static final float EMBLEM_CX = 0.499f;
    public static final float EMBLEM_CY = 0.4717f;
    public static final float EMBLEM_COIL = 0.19f;
}
