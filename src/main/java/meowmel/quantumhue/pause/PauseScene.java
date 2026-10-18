package meowmel.quantumhue.pause;

import meowmel.quantumhue.modernsplash.Backdrop;
import meowmel.quantumhue.modernsplash.DysonSphere;
import meowmel.quantumhue.modernsplash.PcbTraces;
import meowmel.quantumhue.modernsplash.SplashPainter;
import meowmel.quantumhue.modernsplash.SplashTheme;
import meowmel.quantumhue.modernsplash.SplashTimeline;

/**
 * The pause menu, drawn.
 *
 * <p>Composition (geometry and motion lifted from the EndfieldCraft pause screen, recoloured):
 * <ul>
 *   <li>an opaque backdrop across the <b>whole canvas</b> — a deep wash with a grille over it, so
 *       nothing of the game reads through but the blur behind it still shows as a soft glow;</li>
 *   <li>a large disc: dark base, a recessed well, a ticked rim, an amber inlay ring and five bright
 *       arc segments;</li>
 *   <li>inside the well, a <b>Dyson sphere</b> — this project's stand-in for the source's contour
 *       terrain map, and the same {@link DysonSphere} the splash and the title screen use;</li>
 *   <li>four sheared cards down the left, each leaning a different way;</li>
 *   <li>tiles riding the disc's right-hand arc, in a column that scrolls when there are more of
 *       them than fit;</li>
 *   <li>"back to game" as a combed band in the disc's bottom segment;</li>
 *   <li>the power plate bottom-left, a profile card above and below it, and a console in the
 *       bottom-right corner holding the world readout and the machine telemetry.</li>
 * </ul>
 *
 * <p>There is no close plate: leaving is ESC, and a second way out that only duplicates it was
 * more chrome than affordance.
 *
 * <ul>
 * </ul>
 *
 * <h3>What is copied and what is not</h3>
 * The <em>geometry and motion</em> are the reference's: radii, anchors, the tile arc equation, the
 * staggered entrance, spring hover, press dip, pointer parallax.  The <em>look</em> is not: the
 * reference paints gradients, rounded rectangles and photographic icons with Skija; this draws the
 * project's own flat HUD — hairline frames, one violet accent, one gold accent, hard edges — using
 * nothing but rectangles, quads, batched lines, radial glows and bitmap text.  The icon vocabulary
 * (play, gear, power, bars, pennant, radar) is redrawn from scratch: the reference's icon files are
 * GPL and its tile artwork is Hypergryph's, so nothing is imported.
 *
 * <h3>Why it is written against {@code SplashPainter}</h3>
 * So {@code tools/PausePreview.java} can render the identical picture offline; there is no second
 * implementation of this layout anywhere.  The in-game backend ({@code GlStatePainter}) implements
 * only the primitives used here — no arcs, no transform stack, no textures — which is why every
 * curve below is sampled into a polyline by hand.
 */
public final class PauseScene {

    /** {@link #hitTest} result: nothing under the pointer. */
    public static final int NONE = -1;
    /** Timeline sentinel: the menu was closed by ESC rather than by choosing an entry. */
    public static final int CLOSE = -2;

    private static final float[] TEXT = SplashTheme.TEXT;
    private static final float[] TEXT_DIM = SplashTheme.TEXT_DIM;
    private static final float[] LINE = SplashTheme.LINE;
    private static final float[] ACCENT = SplashTheme.ACCENT;
    private static final float[] GOLD = SplashTheme.GOLD;
    private static final float[] VOID = SplashTheme.VOID;
    private static final float[] VOID_LOW = SplashTheme.VOID_LOW;
    private static final float[] WHITE = SplashTheme.WHITE;
    private static final float[] SHELL = SplashTheme.SHELL_PANEL;

    /** The well's floor: darker than the veil, so the sphere reads as being inside something. */
    private static final float[] WELL_INK = {0.008f, 0.006f, 0.016f};
    /**
     * The grille's own colour: a paler violet than {@link SplashTheme#LINE}, because hairline
     * structural strokes at 0.4 of the screen's brightness read as black seams once they are a
     * pixel wide.
     */
    private static final float[] GRILLE = {0.56f, 0.45f, 0.78f};

    // ---------------------------------------------------------------- scratch
    // Reused every frame.  A menu should not be a garbage generator, and this one runs at 60 Hz.

    private static final int SEGMENTS = 96;
    private static final float[] PTS = new float[(SEGMENTS + 1) * 2];
    private static final float[] SEG = new float[4 * (SEGMENTS + 1)];
    private static final float[] FAN = new float[8 * (SEGMENTS + 1)];
    private static final float[] BOARD = new float[8];
    private static final float[] QUAD = new float[8];
    private static final float[] BAR = new float[8];
    private static final float[] HELPER = new float[2];
    private static final float[] ANCHOR = new float[2];
    private static final DysonSphere.Buffers SPHERE = new DysonSphere.Buffers();
    /** The circuit board shared with the splash, the title screen and the loading screen. */
    private static final PcbTraces.Buffers PCB = new PcbTraces.Buffers();
    private static final Projector.Plane FLAT_PLANE = Projector.Plane.at(0f);
    /** Board y of the instrument row: between the rim and the top of the swarm. */
    private static final float CLOCK_Y = 168f;
    /** Difficulty capsule, hanging off the disc's lower-left rim. */
    private static final Box CAPSULE = new Box(474f, 790f, 156f, 40f);
    /** The lower-right console: world readout and system telemetry in one panel. */
    private static final Box CONSOLE = new Box(1290f, 836f, 520f, 200f);

    private PauseScene() {}

    // ---------------------------------------------------------------- state

    /** Everything one frame needs.  The screen owns it and fills it in; the scene only reads it. */
    public static final class State {
        public PauseLayout layout;
        public PauseTimeline.Frame frame;
        public Projector projector;
        /** Optional; {@link #draw} builds an axis-aligned one (caption, footer, compact list). */
        public Projector flat;
        public PlayerCardData player;

        /** Canvas size in painter units.  The backdrop and the grille cover all of it. */
        public float width;
        public float height;
        /** Normalised frame-rate history, oldest first, for the sparkline. */
        public float[] spark = new float[0];
        public int sparkCount;
        /** Where the tile column is scrolled to, in board units. */
        public float scroll;
        /** Drag offsets for the sphere, in degrees: yaw and tilt on top of the idle motion. */
        public float sphereSpin;
        public float sphereTilt;

        /** Spring values per slot, 0..1.  May be shorter than the slot list. */
        public float[] hover = new float[0];
        public float[] press = new float[0];
        /** Slot index highlighted by the keyboard, or {@link #NONE}. */
        public int focused = NONE;

        /** Wall clock in milliseconds, for the slow rotations and the hover pulse. */
        public long millis;
        /** Canvas origin in the painter's own space: {@code (0,0)} in game, centre-based for AWT. */
        public float left;
        public float top;

        /** Filled by {@link #draw}: the screen-space quad of the skin face, and its alpha. */
        public final float[] face = new float[8];
        public boolean faceVisible;
        public float faceAlpha;

        // Swarm rebuild throttle, private to drawDyson().  Projecting the whole swarm every frame is
        // affordable on the title screen, where nothing else is happening; the pause menu can be open
        // in multiplayer with the game still running, so it rebuilds at 30 Hz instead.

        private long boardBuiltAt;
        private int boardW = -1;
        private int boardH = -1;

    }

    // ---------------------------------------------------------------- frame

    public static void draw(SplashPainter p, State s) {
        if (s == null || s.layout == null || s.frame == null || s.projector == null) {
            return;
        }
        PauseLayout layout = s.layout;
        PauseTimeline.Frame f = s.frame;
        s.faceVisible = false;
        s.faceAlpha = 0f;
        if (s.flat == null) {
            s.flat = new Projector(layout.flat(), 0f, 0f, s.left, s.top);
        }

        drawBackdrop(p, s, f.backdrop());

        if (layout.compact) {
            drawCompact(p, s);
            return;
        }

        float discAlpha = f.discAlpha();
        if (discAlpha > 0.002f) {
            drawGlows(p, s);
            drawFurniture(p, s, discAlpha);
            drawDisc(p, s, discAlpha);
            drawResume(p, s, discAlpha);
            drawRim(p, s, discAlpha);
            drawBearingPointer(p, s, discAlpha);
            drawRingCapsule(p, s, discAlpha);
        }
        drawInstrumentRow(p, s, discAlpha);
        drawSlots(p, s);
        drawScrollBar(p, s);
        drawProfile(p, s);
        drawConsole(p, s);
        drawFooter(p, s);
        drawCaption(p, s);
    }

    // ---------------------------------------------------------------- backdrop

    /**
     * The screen's own floor: the game must not read through, but the blur behind it should still
     * say "there is a world here".
     *
     * <p>Deliberately <b>not</b> {@link meowmel.quantumhue.modernsplash.Backdrop}: that one is
     * tuned for a screen with nothing behind it.  This is a near-opaque wash plus a grille that
     * covers the <em>entire canvas</em> — including the letterbox bands the 16:9 board leaves on a
     * wider window, which is what the first version got wrong.
     *
     * <p>Three flat rectangles rather than a band gradient: adjacent translucent bands darken where
     * they overlap by a pixel, and 24 of them read as a set of horizontal rules across the screen.
     * That artifact is why this no longer draws bands at all.
     */
    private static void drawBackdrop(SplashPainter p, State s, float opacity) {
        float x = s.left;
        float y = s.top;
        float w = s.width;
        float h = s.height;
        if (w <= 1f || h <= 1f) {
            return;
        }

        // 1. the wash.  0.84 leaves the blurred world faintly readable, which is the point: the
        //    menu is opened on top of a place, and a hint of it keeps that true without competing
        //    with anything on the board.
        p.rect(x, y, w, h, VOID[0], VOID[1], VOID[2], 0.84f * opacity);
        // 2. warmth pooled towards the bottom, and a darker crown so the disc sits in a lit hollow
        p.rect(x, y + h * 0.42f, w, h * 0.58f, VOID_LOW[0], VOID_LOW[1], VOID_LOW[2], 0.22f * opacity);
        p.rect(x, y, w, h * 0.24f, VOID[0], VOID[1], VOID[2], 0.18f * opacity);

        // 3. three soft lights, so the field is not a flat fill
        p.blendMode(true);
        p.glow(x + w * 0.20f, y + h * 0.24f, Math.min(w, h) * 0.42f,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.10f * opacity, 32);
        p.glow(x + w * 0.84f, y + h * 0.70f, Math.min(w, h) * 0.36f,
                GOLD[0], GOLD[1], GOLD[2], 0.05f * opacity, 28);
        p.glow(x + w * 0.50f, y + h * 0.50f, Math.min(w, h) * 0.62f,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.06f * opacity, 32);
        p.blendMode(false);

        // 4. the circuit board, the layer every other screen already has
        drawCircuitBoard(p, s, opacity);

        // 5. the grille, over the whole canvas
        float cell = Math.max(26f, Math.min(w, h) / 15f);
        int minor = 0;
        int major = 0;
        for (int i = 0; i < 250; i++) {
            float gx = i * cell;
            if (gx > w + 0.5f) {
                break;
            }
            float[] buffer = i % 4 == 0 ? MAJOR : MINOR;
            int index = i % 4 == 0 ? major++ : minor++;
            buffer[index * 4] = x + gx;
            buffer[index * 4 + 1] = y;
            buffer[index * 4 + 2] = x + gx;
            buffer[index * 4 + 3] = y + h;
        }
        for (int i = 0; i < 250; i++) {
            float gy = i * cell;
            if (gy > h + 0.5f) {
                break;
            }
            float[] buffer = i % 4 == 0 ? MAJOR : MINOR;
            int index = i % 4 == 0 ? major++ : minor++;
            buffer[index * 4] = x;
            buffer[index * 4 + 1] = y + gy;
            buffer[index * 4 + 2] = x + w;
            buffer[index * 4 + 3] = y + gy;
        }
        p.lines(MINOR, minor, GRILLE[0], GRILLE[1], GRILLE[2], 0.11f * opacity, lw(s, 1.1f));
        p.lines(MAJOR, major, GRILLE[0], GRILLE[1], GRILLE[2], 0.20f * opacity, lw(s, 1.4f));

        drawCornerBrackets(p, s, opacity);
        drawEdgeSweep(p, s, opacity);
    }

    /**
     * The PCB layer: copper routing, vias, and light travelling down the traces.
     *
     * <p>Every other screen in the mod shares {@code Backdrop}, which paints this.  The pause menu
     * had to grow its own backdrop — full-canvas coverage, a different veil opacity, and a brighter
     * grille — and the board was the one layer it left behind.  It is drawn between the wash and the
     * grille, the same order {@code Backdrop} uses, and it is throttled: the routing is deterministic
     * for a given size and only the pulses move, so there is nothing to gain from rebuilding it
     * sixty times a second while a world load is saturating the same thread.
     */
    private static void drawCircuitBoard(SplashPainter p, State s, float opacity) {
        int w = Math.round(s.width);
        int h = Math.round(s.height);
        if (w < 32 || h < 32) {
            return;
        }
        boolean stale = w != s.boardW || h != s.boardH || s.millis - s.boardBuiltAt >= 50L;
        if (stale) {
            float phase = (float) ((s.millis / 1000.0) % Backdrop.PERIOD);
            PcbTraces.build(w, h, phase, s.left, s.top, PCB);
            s.boardW = w;
            s.boardH = h;
            s.boardBuiltAt = s.millis;
        }
        if (PCB.lineCount <= 0) {
            return;
        }
        p.blendMode(false);
        p.lines(PCB.lines, PCB.lineCount, LINE[0], LINE[1], LINE[2], 0.24f * opacity, lw(s, 1f));
        p.quads(PCB.vias, PCB.viaCount, SHELL[0], SHELL[1], SHELL[2], 0.28f * opacity);
        p.blendMode(true);
        p.lines(PCB.pulses, PCB.pulseCount, GOLD[0], GOLD[1], GOLD[2], 0.55f * opacity, lw(s, 1.4f));
        p.blendMode(false);
    }
    /**
     * Four L-brackets in the canvas corners.
     *
     * <p>Cheap, and it is what stops the board from looking like it is floating: every other
     * element is positioned relative to the 16:9 board, so on a wider window the outer bands had
     * nothing in them at all.  These belong to the canvas, not the board, so they land in the real
     * corners at any aspect ratio.
     */
    private static void drawCornerBrackets(SplashPainter p, State s, float opacity) {
        float arm = Math.min(s.width, s.height) * 0.030f;
        float inset = arm * 0.75f;
        float left = s.left + inset;
        float right = s.left + s.width - inset;
        float top = s.top + inset;
        float bottom = s.top + s.height - inset;
        int lines = 0;
        lines = bracketSegments(left, top, 1f, 1f, arm, lines);
        lines = bracketSegments(right, top, -1f, 1f, arm, lines);
        lines = bracketSegments(left, bottom, 1f, -1f, arm, lines);
        lines = bracketSegments(right, bottom, -1f, -1f, arm, lines);
        p.lines(SEG, lines, ACCENT[0], ACCENT[1], ACCENT[2], 0.55f * opacity, lw(s, 1.6f));
    }

    private static int bracketSegments(float x, float y, float dx, float dy, float arm, int at) {
        SEG[at * 4] = x;
        SEG[at * 4 + 1] = y;
        SEG[at * 4 + 2] = x + dx * arm;
        SEG[at * 4 + 3] = y;
        at++;
        SEG[at * 4] = x;
        SEG[at * 4 + 1] = y;
        SEG[at * 4 + 2] = x;
        SEG[at * 4 + 3] = y + dy * arm;
        return at + 1;
    }

    /** Grille line buffers, split so the every-fourth line can be brighter in one extra draw call. */
    private static final float[] MINOR = new float[4 * 256];
    private static final float[] MAJOR = new float[4 * 256];

    // ---------------------------------------------------------------- furniture

    /** Two thin rings, a diagonal and a handful of survey crosses — the "instrument board" layer. */
    private static void drawFurniture(SplashPainter p, State s, float discAlpha) {
        float a = discAlpha * 0.55f;
        strokeEllipse(p, s, FLAT_PLANE, PauseLayout.CENTER_X, PauseLayout.CENTER_Y, 735f,
                LINE, 0.20f * a, lw(s, 1.2f), 72);
        strokeEllipse(p, s, FLAT_PLANE, PauseLayout.CENTER_X, PauseLayout.CENTER_Y, 855f,
                LINE, 0.20f * a, lw(s, 1.2f), 72);

        project(s, 155f, 970f, FLAT_PLANE, HELPER, 0);
        project(s, 1580f, 40f, FLAT_PLANE, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                LINE[0], LINE[1], LINE[2], 0.12f * a, lw(s, 1.1f));

        float[][] crosses = {{200f, 116f}, {512f, 96f}, {1260f, 150f}, {1760f, 330f},
                {130f, 800f}, {1410f, 952f}, {1170f, 260f}};
        float r = 9f * s.layout.scale;
        for (float[] point : crosses) {
            project(s, point[0], point[1], FLAT_PLANE, HELPER, 0);
            p.line(HELPER[0] - r, HELPER[1], HELPER[0] + r, HELPER[1],
                    LINE[0], LINE[1], LINE[2], 0.65f * a, lw(s, 1.3f));
            p.line(HELPER[0], HELPER[1] - r, HELPER[0], HELPER[1] + r,
                    LINE[0], LINE[1], LINE[2], 0.65f * a, lw(s, 1.3f));
        }
    }

    // ---------------------------------------------------------------- disc

    private static void drawDisc(SplashPainter p, State s, float alpha) {
        PauseTimeline.Frame f = s.frame;
        Projector.Plane plane = Projector.Plane.disc(0f, f.discScale(), f.discTilt());
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;

        fillEllipse(p, s, plane, cx, cy, PauseLayout.OUTER_R + 10f, VOID, 0.94f * alpha, SEGMENTS);
        strokeEllipse(p, s, plane, cx, cy, PauseLayout.OUTER_R + 9f,
                LINE, 0.40f * alpha, lw(s, 1.6f), SEGMENTS);

        fillEllipse(p, s, plane, cx, cy, PauseLayout.INNER_R, WELL_INK, 0.97f * alpha, SEGMENTS);
        gridInWell(p, s, plane, alpha * 0.9f);
        drawOrbits(p, s, plane, cx, cy, alpha);

        drawDyson(p, s, plane, alpha);

        strokeEllipse(p, s, plane, cx, cy, 466f, GOLD, 0.50f * alpha, lw(s, 1.4f), SEGMENTS);
        strokeEllipse(p, s, plane, cx, cy, 468f, LINE, 0.34f * alpha, lw(s, 1.1f), SEGMENTS);
        strokeEllipse(p, s, plane, cx, cy, 497f, LINE, 0.90f * alpha, lw(s, 2f), SEGMENTS);
        strokeEllipse(p, s, plane, cx, cy, 448f, LINE, 0.75f * alpha, lw(s, 1.7f), SEGMENTS);
    }

    /** Ruled lines across the well, each a chord so the circle does the clipping for us. */
    private static void gridInWell(SplashPainter p, State s, Projector.Plane plane, float alpha) {
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;
        float r = PauseLayout.INNER_R - 6f;
        int lines = 0;
        for (float offset = -r + 58f; offset < r && lines < SEGMENTS - 1; offset += 58f) {
            float half = (float) Math.sqrt(Math.max(0f, r * r - offset * offset));
            project(s, cx - half, cy + offset, plane, SEG, lines * 4);
            project(s, cx + half, cy + offset, plane, SEG, lines * 4 + 2);
            lines++;
            project(s, cx + offset, cy - half, plane, SEG, lines * 4);
            project(s, cx + offset, cy + half, plane, SEG, lines * 4 + 2);
            lines++;
        }
        p.lines(SEG, lines, GRILLE[0], GRILLE[1], GRILLE[2], 0.22f * alpha, lw(s, 1.1f));
    }

    /**
     * The well's own reticle: two thin orbits and one bright sweep across the floor.
     *
     * <p>The reference puts a long yellow diagonal through its hero object, and it is the single
     * cheapest thing on that screen that makes the middle look *placed* rather than dropped in.
     * Drawn before the swarm so the sphere reads on top of the line.
     */
    private static void drawOrbits(SplashPainter p, State s, Projector.Plane plane,
                                   float cx, float cy, float alpha) {
        strokeEllipse(p, s, plane, cx, cy, 340f, LINE, 0.30f * alpha, lw(s, 1.2f), 72);
        strokeEllipse(p, s, plane, cx, cy, 392f, LINE, 0.18f * alpha, lw(s, 1f), 72);

        double from = Math.toRadians(-118.0);
        double to = Math.toRadians(62.0);
        project(s, cx + (float) Math.cos(from) * 432f, cy + (float) Math.sin(from) * 432f,
                plane, HELPER, 0);
        project(s, cx + (float) Math.cos(to) * 432f, cy + (float) Math.sin(to) * 432f,
                plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                GOLD[0], GOLD[1], GOLD[2], 0.34f * alpha, lw(s, 2f));

        // A sweep that never stops turning.  The world is frozen while this screen is up, so the
        // board has to supply its own motion or it reads as a still image of a menu.
        float sweep = (s.millis * 0.045f) % 360f;
        for (int i = 0; i < 4; i++) {
            float edge = sweep - i * 7.5f;
            strokeArc(p, s, plane, cx, cy, 430f, edge - 7.5f, edge,
                    GOLD, (0.40f - i * 0.085f) * alpha, lw(s, 3.6f - i * 0.6f), 8);
        }
    }
    /**
     * The Dyson swarm in the well — this screen's replacement for the reference's contour map.
     *
     * <p>Same {@link DysonSphere} as the splash and the title screen, seen small and close.  It is
     * driven by the wall clock, so it keeps turning while the game is paused: a frozen sphere in the
     * middle of a frozen world would read as a crash.
     */
    private static void drawDyson(SplashPainter p, State s, Projector.Plane plane, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        project(s, PauseLayout.CENTER_X, PauseLayout.CENTER_Y, plane, HELPER, 0);

        float scale = s.layout.scale;
        float radius = 262f * scale * s.frame.discScale();
        float viewport = s.layout.designH * scale;
        if (radius < 4f || viewport < 8f) {
            return;
        }
        float time = (s.millis % 3_600_000L) / 1000f;
        // The idle spin keeps running, the drag is an offset on top of it: let go and the sphere
        // carries on turning from wherever you left it instead of snapping back.
        float spin = (s.millis * 0.0075f) % 360f + s.sphereSpin;
        float tilt = 18f + s.frame.discTilt() * 0.6f + s.projector.pitchDegrees() + s.sphereTilt;

        // Unconditional.  The swarm was throttled to 30 Hz to save work while paused, but a wireframe
        // at 30 Hz reads as choppy in a 100+ fps window, and the throttle is what made the sphere
        // look like it was not responding to the drag at all.  The title screen has always rebuilt it
        // every frame; one sphere is not the expensive part of this menu.
        DysonSphere.build(time, alpha, spin, tilt, HELPER[0], HELPER[1], radius, viewport, SPHERE);

        p.blendMode(false);
        p.quads(SPHERE.panelBack, SPHERE.panelBackCount, SHELL[0], SHELL[1], SHELL[2], 0.18f * alpha);
        p.lines(SPHERE.structBack, SPHERE.structBackCount, ACCENT[0], ACCENT[1], ACCENT[2], 0.60f * alpha, lw(s, 1.2f));
        p.blendMode(true);
        p.lines(SPHERE.energyBack, SPHERE.energyBackCount, GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, lw(s, 1.6f));
        p.blendMode(false);
        p.quads(SPHERE.panelFront, SPHERE.panelFrontCount, SHELL[0], SHELL[1], SHELL[2], 0.26f * alpha);
        p.blendMode(true);
        p.lines(SPHERE.energyFront, SPHERE.energyFrontCount, GOLD[0], GOLD[1], GOLD[2], 0.95f * alpha, lw(s, 1.8f));
        p.lines(SPHERE.structFront, SPHERE.structFrontCount, ACCENT[0], ACCENT[1], ACCENT[2], 0.80f * alpha, lw(s, 1.2f));
        p.blendMode(false);

        p.blendMode(true);
        p.glow(HELPER[0], HELPER[1], radius * 1.35f, ACCENT[0], ACCENT[1], ACCENT[2], 0.09f * alpha, 32);
        p.blendMode(false);
    }

    /** The rim: 96 ticks, five bright arc segments and three diamond markers. */
    private static void drawRim(SplashPainter p, State s, float alpha) {
        PauseTimeline.Frame f = s.frame;
        Projector.Plane plane = Projector.Plane.disc(PauseLayout.TOP_DEPTH, f.discScale(), f.discTilt());
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;

        int count = SEGMENTS;
        int lines = 0;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI / (count * 0.5);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float inner = i % 5 == 0 ? 437f : 441f;
            project(s, cx + cos * inner, cy + sin * inner, plane, SEG, lines * 4);
            project(s, cx + cos * 445f, cy + sin * 445f, plane, SEG, lines * 4 + 2);
            lines++;
        }
        p.lines(SEG, lines, LINE[0], LINE[1], LINE[2], 0.50f * alpha, lw(s, 1.2f));

        // Structure stays pale, one sector carries the accent.  Five gold segments had no
        // hierarchy: with everything accented, nothing is.
        float[][] arcs = {{-40.5f, 30.5f}, {-4f, 48f}, {138f, 30f}, {174f, 30f}, {210f, 24f}};
        for (int i = 0; i < arcs.length; i++) {
            boolean accent = i == 1;
            strokeArc(p, s, plane, cx, cy, 480f, arcs[i][0], arcs[i][0] + arcs[i][1],
                    accent ? GOLD : TEXT, (accent ? 0.95f : 0.75f) * alpha, lw(s, 6f), 24);
        }

        float[] marks = {-121f, 0f, 122f};
        for (float mark : marks) {
            double angle = Math.toRadians(mark);
            project(s, cx + (float) Math.cos(angle) * 495f, cy + (float) Math.sin(angle) * 495f,
                    plane, HELPER, 0);
            float r = 5f * s.layout.scale;
            float w = lw(s, 1.6f);
            p.line(HELPER[0] - r, HELPER[1], HELPER[0], HELPER[1] - r, GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, w);
            p.line(HELPER[0], HELPER[1] - r, HELPER[0] + r, HELPER[1], GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, w);
            p.line(HELPER[0] + r, HELPER[1], HELPER[0], HELPER[1] + r, GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, w);
            p.line(HELPER[0], HELPER[1] + r, HELPER[0] - r, HELPER[1], GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, w);
        }
    }

    // ---------------------------------------------------------------- resume band

    /**
     * "Back to game": the segment of the disc below {@link PauseLayout#RESUME_CHORD}.
     *
     * <p>Filled strip by strip rather than as a fan — a fan from the disc's centre would cover the
     * whole sector, and the band is only the part below the chord.  The comb of radial teeth is the
     * reference's hatch pattern, and it is clipped by construction: every tooth lives between the
     * chord and the arc it was derived from.
     */
    /**
     * The arc band, split into two halves: "back to game" on the left and "save and quit" on the
     * right.  Same geometry as before, drawn twice over complementary angular ranges, so the two
     * halves keep their own hover, their own spring and their own press offset.
     */
    private static void drawResume(SplashPainter p, State s, float discAlpha) {
        float alpha = s.frame.controlsAlpha() * discAlpha;
        if (alpha <= 0.004f) {
            return;
        }
        int left = indexOfStyle(s, PauseLayout.Style.RESUME, 0);
        int right = indexOfStyle(s, PauseLayout.Style.RESUME, 1);
        if (left < 0 && right < 0) {
            return;
        }
        Projector.Plane plane = resumePlane(s);
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;
        float chord = PauseLayout.RESUME_CHORD;
        float radius = PauseLayout.RESUME_R;
        float start = (float) Math.toDegrees(Math.asin(chord / radius));

        // the chord, shared by both halves
        float halfChord = (float) Math.sqrt(radius * radius - chord * chord);
        project(s, cx - halfChord, cy + chord, plane, HELPER, 0);
        project(s, cx + halfChord, cy + chord, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                LINE[0], LINE[1], LINE[2], 0.60f * alpha, lw(s, 1.4f));

        // a degree and a half of daylight either side of the divider, so the band reads as two
        // plates rather than one long one with two labels dropped on it
        drawResumeHalf(p, s, right, plane, start, 88.5f, alpha, false);
        drawResumeHalf(p, s, left, plane, 91.5f, 180f - start, alpha, true);

        // the divider between the halves, from the chord up to the rim
        project(s, cx, cy + chord, plane, HELPER, 0);
        project(s, cx, cy + radius, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                ACCENT[0], ACCENT[1], ACCENT[2], 0.55f * alpha, lw(s, 1.8f));
    }

    /** Canvas x of the divider between the two arc halves. */
    private static float resumeDividerX(State s) {
        project(s, PauseLayout.CENTER_X, PauseLayout.CENTER_Y + PauseLayout.RESUME_CHORD,
                resumePlane(s), HELPER, 0);
        return HELPER[0];
    }

    private static Projector.Plane resumePlane(State s) {
        return Projector.Plane.disc(PauseLayout.RESUME_DEPTH, s.frame.discScale(), s.frame.discTilt());
    }

    private static void drawResumeHalf(SplashPainter p, State s, int index, Projector.Plane plane,
                                       float from, float to, float alpha, boolean withHint) {
        if (index < 0) {
            return;
        }
        PauseLayout.Slot slot = s.layout.slots.get(index);
        float hover = slot.entry.enabled ? hoverOf(s, index) : 0f;
        float press = pressOf(s, index);
        if (s.focused == index) {
            hover = Math.max(hover, 0.85f);
        }
        float a = alpha * (slot.entry.enabled ? 1f : 0.4f);

        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;
        float chord = PauseLayout.RESUME_CHORD;
        float radius = PauseLayout.RESUME_R - press * 2f;

        int strips = 24;
        for (int i = 0; i < strips; i++) {
            double a0 = Math.toRadians(from + (to - from) * i / strips);
            double a1 = Math.toRadians(from + (to - from) * (i + 1) / strips);
            project(s, cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius,
                    plane, FAN, i * 8);
            project(s, cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius,
                    plane, FAN, i * 8 + 2);
            project(s, cx + (float) Math.cos(a1) * radius, cy + chord, plane, FAN, i * 8 + 4);
            project(s, cx + (float) Math.cos(a0) * radius, cy + chord, plane, FAN, i * 8 + 6);
        }
        p.quads(FAN, strips,
                SplashTimeline.lerp(VOID[0], ACCENT[0], 0.10f + hover * 0.28f),
                SplashTimeline.lerp(VOID[1], ACCENT[1], 0.10f + hover * 0.28f),
                SplashTimeline.lerp(VOID[2], ACCENT[2], 0.10f + hover * 0.28f),
                (0.62f + 0.22f * hover) * a);

        int teeth = 17;
        int lines = 0;
        for (int i = 0; i <= teeth && lines < SEGMENTS; i++) {
            double angle = Math.toRadians(from + (to - from) * i / teeth);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            project(s, cx + cos * (radius - 22f), cy + sin * (radius - 22f), plane, SEG, lines * 4);
            project(s, cx + cos * radius, cy + sin * radius, plane, SEG, lines * 4 + 2);
            lines++;
        }
        p.lines(SEG, lines, GOLD[0], GOLD[1], GOLD[2], (0.34f + 0.45f * hover) * a, lw(s, 1.2f));

        strokeArc(p, s, plane, cx, cy, radius, from, to,
                hover > 0.4f ? GOLD : ACCENT, (0.60f + 0.40f * hover) * a, lw(s, 2.4f), 22);

        // label centred in this half, at the mean angle of its own range
        float mid = (from + to) * 0.5f;
        double m = Math.toRadians(mid);
        float labelRadius = (chord + radius) * 0.5f - 4f;
        project(s, cx + (float) Math.cos(m) * labelRadius, cy + (float) Math.sin(m) * labelRadius,
                plane, HELPER, 0);
        float scale = s.layout.scale;
        float textScale = 30f * scale / 9f;
        float width = p.textWidth(slot.entry.label, textScale);
        float max = 220f * scale;
        if (width > max && width > 0.01f) {
            textScale *= max / width;
            width = max;
        }
        icon(p, slot.entry.action, HELPER[0] - width * 0.5f - 24f * scale,
                HELPER[1] + 5f * scale, 20f * scale, TEXT, (0.55f + 0.45f * hover) * a);
        label(p, s, slot.entry.label, HELPER[0] - width * 0.5f, HELPER[1], textScale, TEXT, a);
        if (withHint) {
            p.text("ESC", HELPER[0] + width * 0.5f + 14f * scale, HELPER[1] + 4f * scale,
                    20f * scale / 9f, GOLD[0], GOLD[1], GOLD[2], 0.85f * a);
        }
    }
    // ---------------------------------------------------------------- slots

    private static void drawGlows(SplashPainter p, State s) {
        Projector.Plane plane = Projector.Plane.at(34f);
        p.blendMode(true);
        for (int i = 0; i < s.layout.slots.size(); i++) {
            PauseLayout.Slot slot = s.layout.slots.get(i);
            if (slot.style != PauseLayout.Style.CARD && slot.style != PauseLayout.Style.TILE) {
                continue;
            }
            float alpha = slotAlpha(s, slot) * (slot.entry.enabled ? 1f : 0.45f);
            if (alpha <= 0.01f) {
                continue;
            }
            project(s, slot.box.cx(), slot.box.cy() - scrollOf(s, slot), plane, HELPER, 0);
            p.glow(HELPER[0], HELPER[1], slot.box.w * 0.95f * s.layout.scale,
                    SHELL[0], SHELL[1], SHELL[2], 0.07f * alpha, 24);
        }
        p.blendMode(false);
    }

    private static void drawSlots(SplashPainter p, State s) {
        PauseLayout layout = s.layout;
        boolean clip = layout.maxScroll > 0.5f;
        if (clip) {
            clipScrollArea(p, s);
        }
        for (int i = 0; i < layout.slots.size(); i++) {
            PauseLayout.Slot slot = layout.slots.get(i);
            if (slot.style == PauseLayout.Style.TILE) {
                drawSlot(p, s, i, slot);
            }
        }
        if (clip) {
            p.unclip();
        }
        for (int i = 0; i < layout.slots.size(); i++) {
            PauseLayout.Slot slot = layout.slots.get(i);
            if (slot.style != PauseLayout.Style.TILE && slot.style != PauseLayout.Style.RESUME) {
                drawSlot(p, s, i, slot);
            }
        }
    }

    /** Clips to the projected bounding box of the scroll band. */
    private static void clipScrollArea(SplashPainter p, State s) {
        Box band = s.layout.scrollArea;
        float[] quad = BOARD;
        layoutQuad(s.projector, band, Projector.Plane.at(34f), quad);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, quad[i * 2]);
            maxX = Math.max(maxX, quad[i * 2]);
            minY = Math.min(minY, quad[i * 2 + 1]);
            maxY = Math.max(maxY, quad[i * 2 + 1]);
        }
        p.clip(minX, minY, maxX - minX, maxY - minY);
    }

    private static void drawSlot(SplashPainter p, State s, int index, PauseLayout.Slot slot) {
        float alpha = slotAlpha(s, slot);
        if (alpha <= 0.004f) {
            return;
        }
        float baseAlpha = alpha;
        float hover = hoverOf(s, index);
        float press = pressOf(s, index);
        if (s.focused == index) {
            hover = Math.max(hover, 0.85f);
        }
        if (!slot.entry.enabled) {
            hover = 0f;
            alpha *= 0.45f;
        }

        Box visual = visualBox(s, index, slot, hover, press);
        Projector.Plane plane = Projector.Plane.at(slot.style == PauseLayout.Style.POWER ? 30f : 34f);

        cardQuad(slot, visual, BOARD);
        projectQuad(s, BOARD, QUAD, plane);

        boolean card = slot.style == PauseLayout.Style.CARD;
        if (slot.style == PauseLayout.Style.POWER) {
            drawPower(p, s, slot, visual, plane, alpha, hover, press);
            return;
        }
        if (slot.style == PauseLayout.Style.CORNER) {
            drawCornerCard(p, s, slot, visual, plane, baseAlpha, hover, press);
            return;
        }

        p.quads(QUAD, 1, (hover > 0.02f ? VOID_LOW : VOID)[0],
                (hover > 0.02f ? VOID_LOW : VOID)[1], (hover > 0.02f ? VOID_LOW : VOID)[2],
                ((card ? 0.80f : 0.72f) + 0.14f * hover) * alpha);
        if (hover > 0.02f) {
            p.quads(QUAD, 1, GOLD[0], GOLD[1], GOLD[2], 0.06f * hover * alpha);
        }
        frame(p, QUAD, hover > 0.4f ? ACCENT : GRILLE, (0.60f + 0.40f * hover) * alpha,
                lw(s, hover > 0.4f ? 2.2f : 1.6f));

        barQuad(BOARD, (card ? 5f : 4f) + hover * 2f, BAR);
        projectQuad(s, BAR, BAR, plane);
        p.quads(BAR, 1, hover > 0.4f ? GOLD[0] : ACCENT[0],
                hover > 0.4f ? GOLD[1] : ACCENT[1],
                hover > 0.4f ? GOLD[2] : ACCENT[2], (0.78f + 0.22f * hover) * alpha);

        if (hover > 0.05f) {
            cornerTicks(p, s, QUAD, hover, alpha);
        }
        drawSlotText(p, s, slot, visual, plane, alpha, hover);
    }

    private static void drawSlotText(SplashPainter p, State s, PauseLayout.Slot slot, Box visual,
                                     Projector.Plane plane, float alpha, float hover) {
        float scale = s.layout.scale;
        boolean card = slot.style == PauseLayout.Style.CARD;
        float h = visual.h;

        // Every measurement inside a plate is a fraction of its own height.  That is what lets the
        // card column hold four tall cards or six short ones without the label sliding off the
        // bottom of the shorter variant.
        boolean corner = slot.style == PauseLayout.Style.CORNER;
        float codeSize = card ? Math.min(32f, h * 0.22f) : 19f;
        float iconSize = card ? Math.min(52f, h * 0.36f) : 30f;
        float labelSize = card ? Math.min(27f, h * 0.26f) : 20f;
        float iconY = visual.y + h * (card ? 0.38f : 0.34f);
        float labelY = visual.y + h * (card ? 0.62f : 0.66f);

        if (!corner) {
            // the two corner plates are anchors rather than list items, so they carry no index
            project(s, visual.x + (card ? 16f : 14f), visual.y + (card ? 10f : 8f), plane, HELPER, 0);
            p.text(codeOf(s, slot), HELPER[0], HELPER[1], codeSize * scale / 9f,
                    hover > 0.4f ? GOLD[0] : TEXT_DIM[0],
                    hover > 0.4f ? GOLD[1] : TEXT_DIM[1],
                    hover > 0.4f ? GOLD[2] : TEXT_DIM[2], (0.60f + 0.40f * hover) * alpha);
        }

        project(s, visual.cx(), iconY, plane, HELPER, 0);
        icon(p, slot.entry.action, HELPER[0], HELPER[1], iconSize * scale,
                hover > 0.4f ? GOLD : LINE, (0.62f + 0.38f * hover) * alpha);

        project(s, visual.cx(), labelY, plane, HELPER, 0);
        float textScale = labelSize * scale / 9f;
        float width = p.textWidth(slot.entry.label, textScale);
        float max = (visual.w - 34f) * scale;
        if (width > max && width > 0.01f) {
            textScale *= max / width;
            width = max;
        }
        label(p, s, slot.entry.label, HELPER[0] - width * 0.5f, HELPER[1], textScale,
                TEXT, alpha * 0.95f);
    }
    /**
     * Plate index, assigned by <b>slot</b> rather than by the button it came from: the four cards are
     * 01-04 and the tiles continue from 05.  Using the button's own position would number the cards
     * by their order in vanilla's list, which is not the order they appear in.
     */
    private static String codeOf(State s, PauseLayout.Slot slot) {
        int n = slot.style == PauseLayout.Style.TILE
                ? s.layout.cardCount + slot.order + 1
                : slot.order + 1;
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    /**
     * The two corner plates — GTQT Core's market and announcement.
     *
     * <p>They are deliberately a <b>different card family</b> from the navigation column: a wide
     * banner instead of a tall card, a gold two-pixel frame instead of a violet hairline, a header
     * tab riding the top edge that names the destination, and a rail along the bottom.  That is the
     * shape the reference gives its news strip, and the point of it is hierarchy: these two are
     * places to go, not rows in a list.
     *
     * <p>When GTQT Core is missing they are still drawn — the composition needs the two corners —
     * but dimmed, with {@code NOT INSTALLED} where the provenance normally sits, and they never
     * take a hover.
     */
    private static void drawCornerCard(SplashPainter p, State s, PauseLayout.Slot slot, Box visual,
                                       Projector.Plane plane, float alpha, float hover, float press) {
        boolean enabled = slot.entry.enabled;
        float a = alpha * (enabled ? 1f : 0.62f);
        if (!enabled) {
            hover = 0f;
        }
        float scale = s.layout.scale;

        cardQuad(slot, visual, BOARD);
        projectQuad(s, BOARD, QUAD, plane);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], (0.88f + 0.08f * hover) * a);
        if (hover > 0.02f) {
            p.quads(QUAD, 1, GOLD[0], GOLD[1], GOLD[2], 0.07f * hover * a);
        }
        frame(p, QUAD, GOLD, (enabled ? 0.75f : 0.45f) * (1f + hover) * a, lw(s, 2.2f));
        if (hover > 0.05f) {
            cornerTicks(p, s, QUAD, hover, a);
        }

        Box rail = new Box(visual.x + 14f, visual.bottom() - 12f, visual.w - 28f, 2f);
        layoutQuad(s.projector, rail, plane, QUAD);
        p.quads(QUAD, 1, GOLD[0], GOLD[1], GOLD[2], 0.30f * a);

        // the header tab, the one element that says what this plate is before the label is read
        Box tab = new Box(visual.x + 14f, visual.y - 16f, 124f, 26f);
        layoutQuad(s.projector, tab, plane, QUAD);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.96f * a);
        frame(p, QUAD, GOLD, 0.9f * a, lw(s, 1.3f));
        project(s, tab.x + 9f, tab.y + 8f, plane, HELPER, 0);
        p.text(slot.order == 0 ? "MARKET" : "NEWS", HELPER[0], HELPER[1], 12f * scale / 9f,
                GOLD[0], GOLD[1], GOLD[2], a);

        project(s, visual.x + 36f, visual.cy(), plane, HELPER, 0);
        icon(p, slot.entry.action, HELPER[0], HELPER[1], 38f * scale,
                hover > 0.4f ? GOLD : TEXT, (enabled ? 0.9f : 0.55f) * a);

        String note = enabled ? "GTQT CORE" : "NOT INSTALLED";
        float noteScale = 12f * scale / 9f;
        float noteWidth = p.textWidth(note, noteScale) / scale;

        // The label is centred in whatever room the icon and the provenance leave behind, so one
        // routine reads correctly on the narrow market banner and the wide announcement one.
        float from = visual.x + 72f;
        float to = visual.right() - 20f - noteWidth;
        float labelScale = Math.min(28f, visual.h * 0.30f);
        float labelWidth = p.textWidth(slot.entry.label, labelScale * scale / 9f) / scale;
        float labelX = from + Math.max(0f, (to - from - labelWidth) * 0.5f);
        project(s, labelX, visual.cy() - labelScale * 0.5f * scale, plane, HELPER, 0);
        textFit(p, s, slot.entry.label, HELPER[0], HELPER[1], Math.max(80f, to - from), labelScale,
                TEXT, a);

        project(s, visual.right() - 20f - noteWidth, visual.cy() - 6f * scale, plane, HELPER, 0);
        p.text(note, HELPER[0], HELPER[1], noteScale,
                enabled ? TEXT_DIM[0] : GOLD[0], enabled ? TEXT_DIM[1] : GOLD[1],
                enabled ? TEXT_DIM[2] : GOLD[2], (enabled ? 0.8f : 0.9f) * a);
    }
    private static void drawPower(SplashPainter p, State s, PauseLayout.Slot slot, Box visual,
                                  Projector.Plane plane, float alpha, float hover, float press) {
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.78f * alpha);
        frame(p, QUAD, hover > 0.3f ? GOLD : GRILLE, (0.65f + 0.35f * hover) * alpha, lw(s, 2f));

        project(s, visual.cx(), visual.cy(), plane, HELPER, 0);
        float r = 17f * s.layout.scale * (1f - 0.04f * press);
        float[] colour = hover > 0.3f ? GOLD : TEXT;
        float a = (0.70f + 0.30f * hover) * alpha;
        strokeArcScreen(p, HELPER[0], HELPER[1], r, 118f, 422f, colour, a, lw(s, 2.6f), 24);
        p.line(HELPER[0], HELPER[1] - r * 1.5f, HELPER[0], HELPER[1] - r * 0.1f,
                colour[0], colour[1], colour[2], a, lw(s, 2.6f));

        // The plate has its own label now ("关闭MC"), short enough to sit under the glyph, so it is
        // always shown rather than saved for a hover tooltip.
        project(s.flat, visual.cx(), visual.y + 54f, FLAT_PLANE, HELPER, 0);
        float labelScale = 15f * s.layout.scale / 9f;
        float labelWidth = p.textWidth(slot.entry.label, labelScale);
        p.text(slot.entry.label, HELPER[0] - labelWidth * 0.5f, HELPER[1], labelScale,
                hover > 0.3f ? GOLD[0] : TEXT_DIM[0],
                hover > 0.3f ? GOLD[1] : TEXT_DIM[1],
                hover > 0.3f ? GOLD[2] : TEXT_DIM[2], (0.80f + 0.20f * hover) * alpha);

        // the tooltip plate stays for the long-form hints some entries carry
        if (hover > 0.05f) {
            project(s.flat, visual.cx() - 110f, visual.y - 34f, FLAT_PLANE, HELPER, 0);
            project(s.flat, visual.cx() + 110f, visual.y - 6f, FLAT_PLANE, ANCHOR, 0);
            p.rect(HELPER[0], HELPER[1], ANCHOR[0] - HELPER[0], ANCHOR[1] - HELPER[1],
                    VOID_LOW[0], VOID_LOW[1], VOID_LOW[2], 0.90f * hover * alpha);
            project(s.flat, visual.cx() - 100f, visual.y - 30f, FLAT_PLANE, HELPER, 0);
            textFit(p, s, slot.entry.label, HELPER[0], HELPER[1], 200f, 17f, TEXT, hover * alpha);
        }
    }

    // ---------------------------------------------------------------- scroll bar

    /** A three-unit rail just right of the tile column, only when there is something to scroll. */
    private static void drawScrollBar(SplashPainter p, State s) {
        PauseLayout layout = s.layout;
        if (layout.maxScroll <= 0.5f) {
            return;
        }
        float alpha = s.frame.controlsAlpha();
        if (alpha <= 0.004f) {
            return;
        }
        Box band = layout.scrollArea;
        // the rail belongs beside the tiles, not at the band's own right edge — that edge exists
        // only to clip them, and it is much wider than the column itself
        float railX = band.x;
        for (int i = 0; i < layout.slots.size(); i++) {
            PauseLayout.Slot slot = layout.slots.get(i);
            if (slot.style == PauseLayout.Style.TILE) {
                railX = Math.max(railX, slot.box.right());
            }
        }
        railX += 12f;

        float trackH = band.h * 0.86f;
        float trackTop = band.y + (band.h - trackH) * 0.5f;
        Projector.Plane plane = Projector.Plane.at(34f);

        Box track = new Box(railX, trackTop, 3f, trackH);
        layoutQuad(s.projector, track, plane, QUAD);
        p.quads(QUAD, 1, LINE[0], LINE[1], LINE[2], 0.22f * alpha);

        float ratio = Math.min(1f, band.h / (band.h + layout.maxScroll));
        float thumbH = Math.max(28f, trackH * ratio);
        float travel = trackH - thumbH;
        float t = layout.maxScroll <= 0f ? 0f : PauseTimeline.clamp01(s.scroll / layout.maxScroll);
        Box thumb = new Box(railX - 0.5f, trackTop + travel * t, 4f, thumbH);
        layoutQuad(s.projector, thumb, plane, QUAD);
        p.quads(QUAD, 1, GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha);
    }

    /**
     * The readout row across the top of the disc.
     *
     * <p>Three groups, separated by diamonds: where the player is facing, the real-world date and
     * time, and the in-world day, time and weather.  It started as just the date; the row is the
     * natural home for the other two, and one full row reads as an instrument strip where three
     * separate stamps would read as clutter.
     */
    private static void drawInstrumentRow(SplashPainter p, State s, float discAlpha) {
        PlayerCardData d = s.player;
        if (d == null) {
            return;
        }
        float alpha = s.frame.contentAlpha() * discAlpha;
        if (alpha <= 0.01f) {
            return;
        }
        Projector.Plane plane = Projector.Plane.disc(8f, s.frame.discScale(), s.frame.discTilt());
        float scale = s.layout.scale;

        int bearing = Math.round(((d.yaw + 180f) % 360f + 360f) % 360f);
        String heading = String.format(java.util.Locale.ROOT, "%03d   %s", bearing, cardinal(bearing));
        String middle = d.clock;

        // Two groups, not three.  The day/time/weather group moved into the console, because with
        // it the row was wider than the disc at that height — its ends stuck out into the corners
        // the market and news banners are meant to occupy.
        float headScale = 19f * scale / 9f;
        float midScale = 23f * scale / 9f;
        float gap = 32f * scale;
        float w1 = p.textWidth(heading, headScale);
        float w2 = p.textWidth(middle, midScale);
        float total = w1 + w2 + gap;

        project(s, PauseLayout.CENTER_X, CLOCK_Y, plane, HELPER, 0);
        float x = HELPER[0] - total * 0.5f;
        float y = HELPER[1];
        float midY = y + 10f * scale;

        label(p, s, heading, x, y + 3f * scale, headScale, TEXT_DIM, alpha * 0.95f);
        x += w1;
        diamond(p, s, x + gap * 0.5f, midY, scale, alpha);
        x += gap;
        label(p, s, middle, x, y, midScale, TEXT, alpha * 0.95f);

        float left = HELPER[0] - total * 0.5f;
        float end = HELPER[0] + total * 0.5f;
        p.line(left - 78f * scale, midY, left - 22f * scale, midY,
                GRILLE[0], GRILLE[1], GRILLE[2], 0.55f * alpha, lw(s, 1.2f));
        p.line(end + 22f * scale, midY, end + 78f * scale, midY,
                GRILLE[0], GRILLE[1], GRILLE[2], 0.55f * alpha, lw(s, 1.2f));
        chipDot(p, left - 86f * scale, midY, 2.4f * scale, GOLD[0], GOLD[1], GOLD[2], 0.9f * alpha);
        chipDot(p, end + 86f * scale, midY, 2.4f * scale, GOLD[0], GOLD[1], GOLD[2], 0.9f * alpha);
    }

    private static String cardinal(int bearing) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[((bearing + 22) % 360) / 45];
    }

    private static void diamond(SplashPainter p, State s, float cx, float cy, float scale, float alpha) {
        float r = 4f * scale;
        float w = lw(s, 1.1f);
        p.line(cx, cy - r, cx + r, cy, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * alpha, w);
        p.line(cx + r, cy, cx, cy + r, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * alpha, w);
        p.line(cx, cy + r, cx - r, cy, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * alpha, w);
        p.line(cx - r, cy, cx, cy - r, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * alpha, w);
    }

    /**
     * The system panel, lower right.
     *
     * <p>The bottom-right quadrant had nothing in it, which is what made the board feel lopsided:
     * the profile card balances the lower left and the world panel the upper right.  This is the
     * fourth corner, and it earns its place by showing live client numbers — frame rate with a
     * history sparkline, heap use, loaded chunks and entities.  Everything on it is measured, none
     * of it is decoration.
     */
    private static void systemRow(SplashPainter p, State s, Projector.Plane plane, Box box,
                                  float leftX, float rightX, float y, String label, String value,
                                  float alpha) {
        float scale = s.layout.scale;
        project(s, leftX, y, plane, HELPER, 0);
        p.text(label, HELPER[0], HELPER[1], 15f * scale / 9f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], alpha);
        float valueScale = 15f * scale / 9f;
        project(s, rightX - p.textWidth(value, valueScale) / scale, y, plane, HELPER, 0);
        p.text(value, HELPER[0], HELPER[1], valueScale, TEXT[0], TEXT[1], TEXT[2], alpha * 0.9f);
    }

    /** Frame-rate history as one polyline, oldest at the left. */
    private static void drawSparkline(SplashPainter p, State s, Box box, Projector.Plane plane, float alpha) {
        layoutQuad(s.projector, box, plane, QUAD);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.55f * alpha);
        int n = Math.min((int) (s.sparkCount * s.frame.countUp()), s.spark.length);
        if (n < 2) {
            return;
        }
        int segments = Math.min(n - 1, SEGMENTS);
        float step = box.w / (float) Math.max(1, s.sparkCount - 1);
        float baseY = box.bottom() - 2f;
        float height = box.h - 4f;
        for (int i = 0; i < segments; i++) {
            float t0 = PauseTimeline.clamp01(s.spark[i]);
            float t1 = PauseTimeline.clamp01(s.spark[i + 1]);
            project(s, box.x + i * step, baseY - t0 * height, plane, SEG, i * 4);
            project(s, box.x + (i + 1) * step, baseY - t1 * height, plane, SEG, i * 4 + 2);
        }
        p.lines(SEG, segments, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * alpha, lw(s, 1.4f));
    }

    /** The session clock under the caption: how long this instance has been running. */
    private static String sessionText(PlayerCardData d) {
        long seconds = Math.max(0L, d.sessionSeconds);
        return String.format(java.util.Locale.ROOT, "SESSION  %02d:%02d:%02d",
                seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L);
    }
    // ---------------------------------------------------------------- profile card

    private static void drawProfile(SplashPainter p, State s) {
        PauseLayout layout = s.layout;
        if (!layout.showsProfile()) {
            return;
        }
        float alpha = s.frame.badgeAlpha();
        if (alpha <= 0.004f) {
            return;
        }
        Projector projector = layout.compact ? s.flat : s.projector;
        Projector.Plane plane = layout.compact ? FLAT_PLANE : Projector.Plane.at(34f);
        Box box = layout.profile;
        layoutQuad(projector, box, plane, QUAD);

        p.quads(QUAD, 1, VOID_LOW[0], VOID_LOW[1], VOID_LOW[2], 0.85f * alpha);
        frame(p, QUAD, GRILLE, 0.65f * alpha, lw(s, 1.6f));
        cornerTicks(p, s, QUAD, 0.9f, alpha);

        // face slot: the chrome paints the skin into this quad, the scene only reserves it
        float faceSize = layout.compact ? Math.min(64f, box.h - 24f) : 70f;
        Box face = new Box(box.x + 18f, box.y + (layout.compact ? 12f : 18f), faceSize, faceSize);
        layoutQuad(projector, face, plane, s.face);
        s.faceVisible = s.player != null && layout.scale > 0.15f;
        s.faceAlpha = alpha;
        layoutQuad(projector, face, plane, QUAD);
        frame(p, QUAD, GRILLE, 0.45f * alpha, lw(s, 1.2f));

        PlayerCardData player = s.player;
        if (player == null) {
            return;
        }
        float scale = layout.scale;
        float textX = layout.compact ? box.x + 92f : box.x + 104f;
        project(projector, textX, box.y + (layout.compact ? 12f : 18f), plane, HELPER, 0);
        textFit(p, s, player.name, HELPER[0], HELPER[1], box.right() - textX - 16f, 25f, TEXT, alpha);

        float count = s.frame.countUp();
        String levelText = Integer.toString(tick(player.level, count));
        project(projector, textX, box.y + (layout.compact ? 40f : 46f), plane, HELPER, 0);
        p.text(levelText, HELPER[0], HELPER[1], 40f * scale / 9f,
                WHITE[0], WHITE[1], WHITE[2], alpha);
        float levelWidth = p.textWidth(levelText, 40f * scale / 9f);
        p.text("LV", HELPER[0] + levelWidth + 6f * scale, HELPER[1] + 16f * scale,
                14f * scale / 9f, GOLD[0], GOLD[1], GOLD[2], 0.90f * alpha);

        project(projector, box.right() - 18f, box.y + (layout.compact ? 46f : 52f), plane, HELPER, 0);
        float modeScale = 16f * scale / 9f;
        p.text(player.mode, HELPER[0] - p.textWidth(player.mode, modeScale), HELPER[1],
                modeScale, GOLD[0], GOLD[1], GOLD[2], 0.95f * alpha);

        float barY = box.bottom() - 22f;
        Box bar = new Box(textX, barY, Math.max(40f, box.right() - 18f - textX), 5f);
        layoutQuad(projector, bar, plane, QUAD);
        p.quads(QUAD, 1, LINE[0], LINE[1], LINE[2], 0.34f * alpha);
        Box filled = new Box(bar.x, bar.y,
                bar.w * PauseTimeline.clamp01(player.experience) * count, bar.h);
        if (filled.w > 0.5f) {
            layoutQuad(projector, filled, plane, QUAD);
            p.quads(QUAD, 1, GOLD[0], GOLD[1], GOLD[2], 0.95f * alpha);
        }

        // vitals, just under the card, aligned to the card's own projected left edge: the card is
        // drawn through the perspective, so its corner is not at box.x on screen
        project(projector, box.x, box.bottom(), plane, HELPER, 0);
        HELPER[1] += 14f * scale;
        label(p, s, String.format(java.util.Locale.ROOT, "HP %.0f/%.0f    FOOD %d/20    ARM %d",
                player.health * count, player.maxHealth, Math.round(player.food * count),
                Math.round(player.armor * count)),
                HELPER[0], HELPER[1], 13f * scale / 9f, TEXT_DIM, alpha * 0.9f);

        // deaths, just above the card: the band between it and the last card was empty
        project(projector, box.x, box.y, plane, HELPER, 0);
        HELPER[1] -= 20f * scale;
        label(p, s, deathsText(player.deaths, count), HELPER[0], HELPER[1],
                13f * scale / 9f, TEXT_DIM, alpha * 0.9f);
    }

    // ---------------------------------------------------------------- world readout

    /**
     * The console: one panel in the bottom-right corner carrying the world readout and the system
     * telemetry, with the version chip as the tab riding its top edge.
     *
     * <p>The world readout used to live in the top-right corner.  That corner now belongs to the
     * announcement plate, and folding the readout in here is the better home anyway: everything the
     * board knows about the machine and the world is now one console in one place, next to the
     * player card instead of diagonally opposite it.
     */
    private static void drawConsole(SplashPainter p, State s) {
        float alpha = s.frame.badgeAlpha();
        if (alpha <= 0.004f) {
            return;
        }
        float scale = s.layout.scale;
        Projector.Plane plane = Projector.Plane.at(34f);
        Box box = CONSOLE;
        layoutQuad(s.projector, box, plane, QUAD);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.82f * alpha);
        frame(p, QUAD, GOLD, 0.55f * alpha, lw(s, 1.6f));
        cornerTicks(p, s, QUAD, 0.7f, alpha);

        Box tab = new Box(box.x + 10f, box.y - 24f, 172f, 30f);
        layoutQuad(s.projector, tab, plane, QUAD);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.96f * alpha);
        frame(p, QUAD, GOLD, 0.85f * alpha, lw(s, 1.3f));
        project(s, tab.x + 9f, tab.y + 9f, plane, HELPER, 0);
        p.text("MINECRAFT / 1.12.2", HELPER[0], HELPER[1], 14f * scale / 9f,
                GOLD[0], GOLD[1], GOLD[2], 0.95f * alpha);

        project(s, box.x + 286f, box.y + 14f, plane, HELPER, 0);
        project(s, box.x + 286f, box.bottom() - 14f, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                GRILLE[0], GRILLE[1], GRILLE[2], 0.32f * alpha, lw(s, 1.1f));

        drawWorldColumn(p, s, plane, box, alpha);
        drawSystemColumn(p, s, plane, box, alpha);
    }

    private static void drawWorldColumn(SplashPainter p, State s, Projector.Plane plane,
                                        Box box, float alpha) {
        PlayerCardData player = s.player;
        if (player == null) {
            return;
        }
        project(s, box.x + 16f, box.y + 16f, plane, HELPER, 0);
        textFit(p, s, player.world, HELPER[0], HELPER[1], 252f, 24f, TEXT, alpha);
        project(s, box.x + 16f, box.y + 44f, plane, HELPER, 0);
        textFit(p, s, player.connection, HELPER[0], HELPER[1], 252f, 18f, TEXT, alpha * 0.85f);
        project(s, box.x + 16f, box.y + 66f, plane, HELPER, 0);
        textFit(p, s, player.position, HELPER[0], HELPER[1], 252f, 16f, TEXT_DIM, alpha * 0.9f);
        project(s, box.x + 16f, box.y + 86f, plane, HELPER, 0);
        textFit(p, s, player.dimension, HELPER[0], HELPER[1], 252f, 16f, ACCENT, alpha * 0.9f);
        project(s, box.x + 16f, box.y + 108f, plane, HELPER, 0);
        textFit(p, s, player.biome, HELPER[0], HELPER[1], 252f, 16f, TEXT_DIM, alpha * 0.9f);
        project(s, box.x + 16f, box.y + 130f, plane, HELPER, 0);
        textFit(p, s, String.format(java.util.Locale.ROOT, "DAY %d   %s   %s",
                tick(player.day, s.frame.countUp()), player.worldClock, player.weather),
                HELPER[0], HELPER[1], 252f, 16f, GOLD, alpha * 0.9f);

        float[][] colours = {{TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2]},
                {GOLD[0], GOLD[1], GOLD[2]}, {LINE[0], LINE[1], LINE[2]}};
        for (int i = 0; i < 3; i++) {
            Box seg = new Box(box.x + 16f + i * 74f, box.bottom() - 14f, 66f, 3f);
            layoutQuad(s.projector, seg, plane, QUAD);
            p.quads(QUAD, 1, colours[i][0], colours[i][1], colours[i][2],
                    (i == 0 ? 0.9f : 0.5f) * alpha);
        }
    }

    private static void drawSystemColumn(SplashPainter p, State s, Projector.Plane plane,
                                         Box box, float alpha) {
        PlayerCardData d = s.player;
        if (d == null) {
            return;
        }
        float scale = s.layout.scale;
        project(s, box.x + 300f, box.y + 10f, plane, HELPER, 0);
        p.text("SYSTEM", HELPER[0], HELPER[1], 15f * scale / 9f,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.9f * alpha);

        float count = s.frame.countUp();
        project(s, box.x + 300f, box.y + 32f, plane, HELPER, 0);
        p.text("FPS", HELPER[0], HELPER[1], 16f * scale / 9f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], alpha);
        String fps = Integer.toString(tick(d.fps, count));
        float fpsScale = 34f * scale / 9f;
        project(s, box.right() - 16f - p.textWidth(fps, fpsScale) / scale, box.y + 26f, plane, HELPER, 0);
        p.text(fps, HELPER[0], HELPER[1], fpsScale,
                d.fps >= 55 ? TEXT[0] : GOLD[0], d.fps >= 55 ? TEXT[1] : GOLD[1],
                d.fps >= 55 ? TEXT[2] : GOLD[2], alpha);

        Box spark = new Box(box.x + 300f, box.y + 62f, box.w - 316f, 24f);
        drawSparkline(p, s, spark, plane, alpha);

        project(s, box.x + 300f, box.y + 100f, plane, HELPER, 0);
        p.text("MEM", HELPER[0], HELPER[1], 14f * scale / 9f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], alpha);
        int percent = d.memMaxMb <= 0L ? 0
                : Math.round(PauseTimeline.clamp01(d.memUsedMb / (float) d.memMaxMb) * 100f * count);
        // No " MB" suffix: the row is labelled MEM, and the three characters it costs are what made
        // the value collide with that label once the percentage was added.
        String mem = tick(d.memUsedMb, count) + " / " + d.memMaxMb + "   " + percent + "%";
        float memScale = 13f * scale / 9f;
        project(s, box.right() - 16f - p.textWidth(mem, memScale) / scale, box.y + 100f, plane, HELPER, 0);
        p.text(mem, HELPER[0], HELPER[1], memScale, TEXT[0], TEXT[1], TEXT[2], alpha * 0.9f);
        // Segmented, and drawn with strokes rather than fills: a filled rectangle in this exact
        // place has twice failed to appear in game, while every line on the same panel renders.
        // Eight cells, an outline for the capacity, and a thick stroke for the charge.
        Box track = new Box(box.x + 300f, box.y + 118f, box.w - 316f, 10f);
        float used = d.memMaxMb <= 0L ? 0f
                : PauseTimeline.clamp01(d.memUsedMb / (float) d.memMaxMb) * count;
        if (Float.isNaN(used)) {
            used = 0f;
        }
        int cells = 8;
        float gap = 2.5f;
        float cellWidth = (track.w - gap * (cells - 1)) / cells;
        float[] charge = used > 0.85f ? GOLD : ACCENT;
        for (int cell = 0; cell < cells; cell++) {
            float x0 = track.x + cell * (cellWidth + gap);
            float x1 = x0 + cellWidth;
            float fill = PauseTimeline.clamp01(used * cells - cell);
            project(s, x0, track.y, plane, PTS, 0);
            project(s, x1, track.y, plane, PTS, 2);
            project(s, x1, track.bottom(), plane, PTS, 4);
            project(s, x0, track.bottom(), plane, PTS, 6);
            project(s, x0, track.y, plane, PTS, 8);
            p.lines(PTS, 5, LINE[0], LINE[1], LINE[2], 0.42f * alpha, lw(s, 1.3f));
            if (fill <= 0f) {
                continue;
            }
            float right = x0 + cellWidth * fill;
            project(s, x0, track.y + track.h * 0.5f, plane, PTS, 0);
            project(s, right, track.y + track.h * 0.5f, plane, PTS, 2);
            p.lines(PTS, 2, charge[0], charge[1], charge[2], 0.95f * alpha, lw(s, 4.2f));
        }
        // the exact watermark, so a partial cell still reads as a precise amount
        project(s, track.x + track.w * used, track.y - 3f, plane, PTS, 0);
        project(s, track.x + track.w * used, track.bottom() + 3f, plane, PTS, 2);
        p.lines(PTS, 2, GOLD[0], GOLD[1], GOLD[2], 0.95f * alpha, lw(s, 1.8f));
        systemRow(p, s, plane, box, box.x + 300f, box.right() - 16f, box.y + 134f,
                "PLAYERS", Integer.toString(tick(d.players, count)), alpha);
        systemRow(p, s, plane, box, box.x + 300f, box.right() - 16f, box.y + 156f,
                "ENTITIES", Integer.toString(tick(d.entities, count)), alpha);
        systemRow(p, s, plane, box, box.x + 300f, box.right() - 16f, box.y + 178f,
                "VIEW", tick(d.viewDistance, count) + " CHUNKS", alpha);
    }
    // ---------------------------------------------------------------- footer

    private static void drawFooter(SplashPainter p, State s) {
        PauseLayout layout = s.layout;
        if (!layout.showsFurniture()) {
            return;
        }
        float alpha = s.frame.badgeAlpha();
        if (alpha <= 0.004f) {
            return;
        }
        Box box = layout.footer();
        PlayerCardData player = s.player;
        String left = player == null ? "" : player.connection + "  //  " + player.dimension;
        String right = player == null ? "" : player.hint;
        float scale = layout.scale;
        float textScale = 15f * scale / 9f;

        project(s.flat, box.x, box.y + 8f, FLAT_PLANE, HELPER, 0);
        label(p, s, left, HELPER[0], HELPER[1], textScale, TEXT_DIM, 0.90f * alpha);
        project(s.flat, box.right() - p.textWidth(right, textScale) / layout.scale, box.y + 8f,
                FLAT_PLANE, HELPER, 0);
        label(p, s, right, HELPER[0], HELPER[1], textScale, TEXT_DIM, 0.90f * alpha);
    }

    private static void drawCaption(SplashPainter p, State s) {
        float alpha = s.frame.controlsAlpha();
        if (alpha <= 0.004f) {
            return;
        }
        // One header line: the title and the session clock side by side.  The clock used to sit on
        // a second line, which is exactly where the market banner now has to put its header tab,
        // and moving it inline bought the banner the 20% of height it gained.
        float scale = s.layout.scale;
        project(s.flat, 50f, 44f, FLAT_PLANE, HELPER, 0);
        float headScale = 25f * scale / 9f;
        label(p, s, "// PAUSED", HELPER[0], HELPER[1], headScale, ACCENT, 0.95f * alpha);
        float headWidth = p.textWidth("// PAUSED", headScale);
        if (s.player != null && s.player.sessionSeconds > 0L) {
            project(s.flat, 50f, 58f, FLAT_PLANE, HELPER, 0);
            p.text(sessionText(s.player), HELPER[0] + headWidth + 26f * scale,
                    HELPER[1] + 9f * scale, 13f * scale / 9f,
                    TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.85f * alpha);
        }
        project(s.flat, 50f, 74f, FLAT_PLANE, HELPER, 0);
        project(s.flat, 340f, 74f, FLAT_PLANE, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1], ACCENT[0], ACCENT[1], ACCENT[2],
                0.55f * alpha, lw(s, 1.4f));
    }

    // ---------------------------------------------------------------- compact

    /** Narrow or small windows: no disc, no sphere — a two-column list and a wide resume bar. */
    private static void drawCompact(SplashPainter p, State s) {
        drawCaption(p, s);

        // The list scrolls inside the band; the resume bar sits below it and must not be clipped
        // with it, so the two are drawn in separate passes.
        boolean clip = s.layout.maxScroll > 0.5f;
        if (clip) {
            Box band = s.layout.scrollArea;
            layoutQuad(s.flat, band, FLAT_PLANE, QUAD);
            p.clip(QUAD[0], QUAD[1], QUAD[4] - QUAD[0], QUAD[5] - QUAD[1]);
        }
        for (int i = 0; i < s.layout.slots.size(); i++) {
            PauseLayout.Slot slot = s.layout.slots.get(i);
            if (slot.style == PauseLayout.Style.TILE) {
                drawCompactSlot(p, s, i, slot);
            }
        }
        if (clip) {
            p.unclip();
        }
        for (int i = 0; i < s.layout.slots.size(); i++) {
            PauseLayout.Slot slot = s.layout.slots.get(i);
            if (slot.style == PauseLayout.Style.RESUME) {
                drawCompactSlot(p, s, i, slot);
            }
        }
        drawScrollBar(p, s);
        drawProfile(p, s);
    }

    private static void drawCompactSlot(SplashPainter p, State s, int index, PauseLayout.Slot slot) {
        float alpha = slotAlpha(s, slot);
        if (alpha <= 0.004f) {
            return;
        }
        boolean resume = slot.style == PauseLayout.Style.RESUME;
        float hover = slot.entry.enabled ? hoverOf(s, index) : 0f;
        float press = pressOf(s, index);
        if (s.focused == index) {
            hover = Math.max(hover, 0.85f);
        }
        if (!slot.entry.enabled) {
            alpha *= 0.45f;
        }
        float scale = s.layout.scale;
        Box visual = resume ? slot.box : visualBox(s, index, slot, hover, press);
        layoutQuad(s.flat, visual, FLAT_PLANE, QUAD);
        if (resume) {
            p.quads(QUAD, 1, ACCENT[0] * 0.30f, ACCENT[1] * 0.30f, ACCENT[2] * 0.30f,
                    (0.74f + 0.14f * hover) * alpha);
            frame(p, QUAD, ACCENT, (0.60f + 0.40f * hover) * alpha, lw(s, 1.8f));
        } else {
            p.quads(QUAD, 1, (hover > 0.02f ? VOID_LOW : VOID)[0],
                    (hover > 0.02f ? VOID_LOW : VOID)[1],
                    (hover > 0.02f ? VOID_LOW : VOID)[2], (0.76f + 0.14f * hover) * alpha);
            frame(p, QUAD, hover > 0.4f ? ACCENT : GRILLE, (0.6f + 0.4f * hover) * alpha, lw(s, 1.6f));
        }

        project(s.flat, visual.x + 14f, visual.cy() - 8f, FLAT_PLANE, HELPER, 0);
        if (resume) {
            float textScale = 29f * scale / 9f;
            float width = p.textWidth(slot.entry.label, textScale);
            label(p, s, slot.entry.label, HELPER[0] + (visual.w * scale - width) * 0.5f,
                    HELPER[1], textScale, TEXT, alpha);
            p.text("ESC", HELPER[0] + (visual.w - 68f) * scale, HELPER[1] + 3f * scale,
                    20f * scale / 9f, GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha);
        } else {
            textFit(p, s, slot.entry.label, HELPER[0], HELPER[1], visual.w - 60f, 24f, TEXT, alpha);
            project(s.flat, visual.right() - 34f, visual.cy() - 9f, FLAT_PLANE, HELPER, 0);
            icon(p, slot.entry.action, HELPER[0], HELPER[1] + 4f * scale, 20f * scale,
                    hover > 0.4f ? GOLD : LINE, (0.6f + 0.4f * hover) * alpha);
        }
    }
    // ---------------------------------------------------------------- new instruments
    // Difficulty capsule, perimeter run light, spawn bearing, play time.  All four are fed by real
    // client state; none of them is decoration for its own sake.

    /** Rounds a readout during the count-up, so every number starts at zero and settles. */
    private static int tick(long value, float countUp) {
        return (int) Math.round(value * PauseTimeline.clamp01(countUp));
    }

    private static String deathsText(int deaths, float countUp) {
        return String.format(java.util.Locale.ROOT, "DEATHS  %d", tick(deaths, countUp));
    }

    /**
     * A capsule riding the disc's lower-left rim, showing the world's difficulty.
     *
     * <p>This is the reference's ring-mounted pill — a readout attached to the ring rather than
     * floating on the board.  Difficulty is the right tenant for it: it is constant, it is a single
     * word, and it is the one bit of world state a pause screen should never hide.
     */
    private static void drawRingCapsule(SplashPainter p, State s, float discAlpha) {
        PlayerCardData d = s.player;
        if (d == null || d.difficulty == null || d.difficulty.isEmpty()) {
            return;
        }
        float alpha = s.frame.controlsAlpha() * discAlpha;
        if (alpha <= 0.004f) {
            return;
        }
        float scale = s.layout.scale;
        Projector.Plane plane = Projector.Plane.disc(20f, s.frame.discScale(), s.frame.discTilt());
        Box box = CAPSULE;
        layoutQuad(s.projector, box, plane, QUAD);
        p.quads(QUAD, 1, VOID[0], VOID[1], VOID[2], 0.92f * alpha);
        frame(p, QUAD, d.hardcore ? GOLD : GRILLE, (d.hardcore ? 0.95f : 0.7f) * alpha, lw(s, 1.5f));

        // a stub tying the capsule to the rim it hangs off
        project(s, box.x + box.w * 0.5f, box.y, plane, HELPER, 0);
        project(s, box.x + box.w * 0.5f, box.y - 18f, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                GRILLE[0], GRILLE[1], GRILLE[2], 0.5f * alpha, lw(s, 1.2f));

        project(s, box.x + 12f, box.y + 9f, plane, HELPER, 0);
        p.text("DIFFICULTY", HELPER[0], HELPER[1], 11f * scale / 9f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], alpha * 0.9f);

        String value = d.hardcore ? d.difficulty + "  /  HARDCORE" : d.difficulty;
        float valueScale = 16f * scale / 9f;
        float width = p.textWidth(value, valueScale);
        float max = (box.w - 24f) * scale;
        if (width > max && width > 0.01f) {
            valueScale *= max / width;
            width = max;
        }
        project(s, box.x + 12f, box.y + 22f, plane, HELPER, 0);
        label(p, s, value, HELPER[0], HELPER[1], valueScale,
                d.hardcore ? GOLD : TEXT, alpha);
        chipDot(p, box.right() - 14f * scale, box.y + 12f * scale, 3f * scale,
                d.hardcore ? GOLD[0] : ACCENT[0], d.hardcore ? GOLD[1] : ACCENT[1],
                d.hardcore ? GOLD[2] : ACCENT[2], 0.9f * alpha);
    }

    /**
     * A chevron on the rim pointing at the world spawn, with the distance under it.
     *
     * <p>The one genuinely navigational thing a pause screen can offer: which way is home, and how
     * far.  It rotates with the player, so it also gives the ring a reason to exist beyond framing.
     */
    private static void drawBearingPointer(SplashPainter p, State s, float discAlpha) {
        PlayerCardData d = s.player;
        if (d == null || !d.hasSpawn) {
            return;
        }
        float alpha = s.frame.contentAlpha() * discAlpha;
        if (alpha <= 0.01f) {
            return;
        }
        float scale = s.layout.scale;
        Projector.Plane plane = Projector.Plane.disc(PauseLayout.TOP_DEPTH * 0.5f,
                s.frame.discScale(), s.frame.discTilt());
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;

        float facing = ((d.yaw + 180f) % 360f + 360f) % 360f;
        double angle = Math.toRadians(-90.0 + (d.spawnBearing - facing));

        float tipR = 512f;
        float baseR = 486f;
        float spread = (float) Math.toRadians(5.5);
        project(s, cx + (float) Math.cos(angle) * tipR, cy + (float) Math.sin(angle) * tipR,
                plane, HELPER, 0);
        project(s, cx + (float) Math.cos(angle - spread) * baseR,
                cy + (float) Math.sin(angle - spread) * baseR, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, lw(s, 1.6f));
        project(s, cx + (float) Math.cos(angle + spread) * baseR,
                cy + (float) Math.sin(angle + spread) * baseR, plane, ANCHOR, 0);
        p.line(HELPER[0], HELPER[1], ANCHOR[0], ANCHOR[1],
                GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha, lw(s, 1.6f));

        String distance = d.spawnDistance >= 1000f
                ? String.format(java.util.Locale.ROOT, "%.1fk", d.spawnDistance / 1000f)
                : Integer.toString(tick(Math.round(d.spawnDistance), s.frame.countUp()));
        float textScale = 13f * scale / 9f;
        float width = p.textWidth(distance, textScale);
        project(s, cx + (float) Math.cos(angle) * (baseR - 18f),
                cy + (float) Math.sin(angle) * (baseR - 18f), plane, HELPER, 0);
        label(p, s, distance, HELPER[0] - width * 0.5f, HELPER[1] - 5f * scale, textScale,
                TEXT_DIM, alpha * 0.9f);
    }

    /**
     * A light that runs around the canvas border.
     *
     * <p>It belongs to the canvas, not the board, so it traces the real window edge — which is also
     * why it reads as "the frame is powered" instead of as another board element.  The tail is five
     * chunks of decreasing alpha: one polyline cannot fade, and a dozen arcs would be a dozen draws.
     */
    private static void drawEdgeSweep(SplashPainter p, State s, float opacity) {
        float w = s.width;
        float h = s.height;
        if (w <= 8f || h <= 8f) {
            return;
        }
        float inset = Math.min(w, h) * 0.020f;
        float x0 = s.left + inset;
        float y0 = s.top + inset;
        float x1 = s.left + w - inset;
        float y1 = s.top + h - inset;
        float pw = x1 - x0;
        float ph = y1 - y0;
        float perimeter = 2f * (pw + ph);
        if (perimeter <= 1f) {
            return;
        }
        float head = (s.millis % 11000L) / 11000f * perimeter;
        float tail = perimeter * 0.20f;
        int chunks = 5;
        int steps = 7;
        for (int c = 0; c < chunks; c++) {
            float from = head - tail * (c + 1) / chunks;
            float to = head - tail * c / chunks;
            for (int i = 0; i < steps; i++) {
                float d0 = from + (to - from) * i / steps;
                float d1 = from + (to - from) * (i + 1) / steps;
                perimeterPoint(d0, x0, y0, x1, y1, pw, ph, perimeter, SEG, i * 4);
                perimeterPoint(d1, x0, y0, x1, y1, pw, ph, perimeter, SEG, i * 4 + 2);
            }
            float alpha = (0.42f - c * 0.07f) * opacity;
            p.lines(SEG, steps, GOLD[0], GOLD[1], GOLD[2], alpha, lw(s, 1.6f));
        }
    }

    /** Point at {@code d} along a clockwise path starting at the top-left corner. */
    private static void perimeterPoint(float d, float x0, float y0, float x1, float y1,
                                       float pw, float ph, float perimeter,
                                       float[] out, int offset) {
        float t = d % perimeter;
        if (t < 0f) {
            t += perimeter;
        }
        if (t < pw) {
            out[offset] = x0 + t;
            out[offset + 1] = y0;
        } else if (t < pw + ph) {
            out[offset] = x1;
            out[offset + 1] = y0 + (t - pw);
        } else if (t < 2f * pw + ph) {
            out[offset] = x1 - (t - pw - ph);
            out[offset + 1] = y1;
        } else {
            out[offset] = x0;
            out[offset + 1] = y1 - (t - 2f * pw - ph);
        }
    }
    // ---------------------------------------------------------------- hit testing

    /**
     * What is under the pointer.  Entries are only hittable once they have finished fading in, so a
     * click during the entrance cannot hit something that is not really there yet.
     *
     * @return a slot index, or {@link #NONE}
     */
    public static int hitTest(State s, double x, double y) {
        if (s == null || s.layout == null || s.frame == null || s.projector == null) {
            return NONE;
        }
        if (s.flat == null) {
            s.flat = new Projector(s.layout.flat(), 0f, 0f, s.left, s.top);
        }
        if (s.frame.controlsAlpha() <= 0.8f) {
            return NONE;
        }
        PauseLayout layout = s.layout;

        for (int i = layout.slots.size() - 1; i >= 0; i--) {
            PauseLayout.Slot slot = layout.slots.get(i);
            if (slotAlpha(s, slot) <= 0.8f) {
                continue;
            }
            if (slot.style == PauseLayout.Style.RESUME) {
                // Both halves occupy the same band, so the slot that owns the pointer's side is the
                // one that answers.  Returning this slot unconditionally is what made the right
                // half unreachable and the left half fire the wrong action.
                if (!insideResume(s, x, y)) {
                    continue;
                }
                int wanted = x < resumeDividerX(s) ? 0 : 1;
                if (slot.order == wanted) {
                    return i;
                }
                continue;
            }
            Box scrolled = slot.style == PauseLayout.Style.TILE
                    ? PauseLayout.scrolledTile(slot.box, s.scroll) : slot.box;
            if (slot.style == PauseLayout.Style.TILE && !insideBand(s, scrolled)) {
                continue;
            }
            Projector.Plane plane = Projector.Plane.at(
                    slot.style == PauseLayout.Style.POWER ? 30f : 34f);
            cardQuad(slot, scrolled, BOARD);
            projectQuad(s, BOARD, QUAD, plane);
            if (Projector.insidePolygon(QUAD, 4, x, y)) {
                return i;
            }
        }
        return NONE;
    }

    /** True when the point is over the sphere itself, which can be dragged to spin it. */
    public static boolean insideSphere(State s, double x, double y) {
        if (s == null || s.layout == null || s.frame == null || s.projector == null) {
            return false;
        }
        project(s, PauseLayout.CENTER_X, PauseLayout.CENTER_Y, Projector.Plane.at(34f), HELPER, 0);
        float radius = 262f * s.layout.scale * s.frame.discScale() * 1.08f;
        float dx = (float) x - HELPER[0];
        float dy = (float) y - HELPER[1];
        return dx * dx + dy * dy <= radius * radius;
    }

    /** True when the point is inside the scrolling band, in board space. */
    public static boolean insideBand(State s, double x, double y) {
        Box band = s.layout.scrollArea;
        Projector projector = s.layout.compact ? s.flat : s.projector;
        Projector.Plane plane = s.layout.compact ? FLAT_PLANE : Projector.Plane.at(34f);
        layoutQuad(projector, band, plane, QUAD);
        return Projector.insidePolygon(QUAD, 4, x, y);
    }

    /** True when a board-space box still overlaps the scrolling band. */
    private static boolean insideBand(State s, Box box) {
        Box band = s.layout.scrollArea;
        return box.bottom() > band.y - 4f && box.y < band.bottom() + 4f;
    }

    private static boolean insideResume(State s, double x, double y) {
        Projector.Plane plane = Projector.Plane.disc(PauseLayout.RESUME_DEPTH,
                s.frame.discScale(), s.frame.discTilt());
        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;
        float radius = PauseLayout.RESUME_R;
        float start = (float) Math.toDegrees(Math.asin(PauseLayout.RESUME_CHORD / radius));
        // The pointer decides which half by angle: the divider runs straight down from the centre,
        // so a single sweep test cannot answer "which of the two".
        // The divider is where the band's centre projects, not board x = 960: the pointer arrives in
        // canvas units, and on any canvas narrower than the 1920-wide design space the two are not
        // the same number.  That comparison against the board constant is why the left half answered
        // everywhere and the right half never answered at all.
        boolean left = x < resumeDividerX(s);
        float from = left ? 90f : start;
        float to = left ? 180f - start : 90f;
        // The polygon starts at the middle of the chord, not at the arc: the band is bounded below
        // by that straight edge, and an arc-only polygon closes diagonally across it — which is why
        // only a sliver near the divider used to answer a click.
        int points = 1;
        project(s, cx, cy + PauseLayout.RESUME_CHORD, plane, PTS, 0);
        for (int i = 0; i < 17; i++) {
            double angle = Math.toRadians(from + (to - from) * i / 16.0);
            project(s, cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius, plane, PTS, points * 2);
            points++;
        }
        return Projector.insidePolygon(PTS, points, x, y);
    }

    // ---------------------------------------------------------------- slot helpers

    /** Alpha the timeline gives a slot, before the disabled dimming. */
    private static float slotAlpha(State s, PauseLayout.Slot slot) {
        PauseTimeline.Frame f = s.frame;
        switch (slot.style) {
            case CARD:
            case CORNER:
                return f.cardAlpha(slot.order);
            case TILE:
                return f.tileAlpha(slot.order);
            case POWER:
                return f.badgeAlpha();
            case RESUME:
            default:
                return f.controlsAlpha();
        }
    }

    private static float hoverOf(State s, int index) {
        return index >= 0 && index < s.hover.length ? s.hover[index] : 0f;
    }

    private static float pressOf(State s, int index) {
        return index >= 0 && index < s.press.length ? s.press[index] : 0f;
    }

    private static float scrollOf(State s, PauseLayout.Slot slot) {
        return slot.style == PauseLayout.Style.TILE ? s.scroll : 0f;
    }

    /** The slot of that style with that order, or -1. */
    private static int indexOfStyle(State s, PauseLayout.Style style, int order) {
        for (int i = 0; i < s.layout.slots.size(); i++) {
            PauseLayout.Slot slot = s.layout.slots.get(i);
            if (slot.style == style && slot.order == order) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfStyle(State s, PauseLayout.Style style) {
        for (int i = 0; i < s.layout.slots.size(); i++) {
            if (s.layout.slots.get(i).style == style) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The rectangle a slot is actually drawn at: the entrance slide, the column's scroll, the hover
     * lift and drift, then the press dip.
     */
    private static Box visualBox(State s, int index, PauseLayout.Slot slot, float hover, float press) {
        float slide;
        if (slot.style == PauseLayout.Style.CARD) {
            slide = s.frame.cardSlide(slot.order);
        } else if (slot.style == PauseLayout.Style.TILE) {
            slide = -s.frame.cardSlide(1);
        } else {
            slide = 0f;
        }
        float lift = hover * 4f - press * 3f;
        float direction = slot.style == PauseLayout.Style.TILE && slot.box.w < 320f ? 1f : -1f;
        Box box;
        if (slot.style == PauseLayout.Style.TILE) {
            // Scroll first, then the hover offsets: the scroll moves the tile *along the arc*, so the
            // column's inner edge keeps hugging the disc no matter where the list is.
            box = PauseLayout.scrolledTile(slot.box, scrollOf(s, slot))
                    .translate(slide + direction * hover * 2f, -lift);
        } else {
            box = slot.box.translate(slide + direction * hover * 2f, -lift - scrollOf(s, slot));
        }
        return press > 0.001f ? box.scaleAboutCentre(1f - press * 0.03f) : box;
    }

    /**
     * Corner set of a plate: a quadrilateral sheared horizontally, alternating per index.
     *
     * <p>This is the reference's card silhouette family reduced to what four corners can express —
     * the shape there is a rounded parallelogram with slanted top and bottom edges, and each index
     * leans a different way.  A per-index shear reproduces the leaning without copying any path
     * data.
     */
    private static void cardQuad(PauseLayout.Slot slot, Box box, float[] out) {
        float shear;
        float inset;
        if (slot.style == PauseLayout.Style.CARD) {
            switch (slot.order) {
                case 0: shear = 22f; inset = 3f; break;
                case 1: shear = 8f; inset = 2f; break;
                case 2: shear = -14f; inset = 2f; break;
                default: shear = -24f; inset = 4f; break;
            }
        } else if (slot.style == PauseLayout.Style.TILE) {
            shear = slot.order % 2 == 0 ? 7f : -7f;
            inset = 2f;
        } else if (slot.style == PauseLayout.Style.CORNER) {
            shear = slot.order == 0 ? 16f : -16f;
            inset = 3f;
        } else {
            shear = 6f;
            inset = 2f;
        }
        // The shear shortens one of the two horizontal edges rather than sliding the bottom edge
        // past the plate's own bounds: a corner outside the box would stick out of the card, and
        // the label (centred on the box) would then read as overhanging it.
        out[0] = box.x + inset;
        out[1] = box.y + inset;
        out[2] = box.right() - inset;
        out[3] = box.y + inset;
        out[4] = box.right() - inset + Math.min(0f, shear);
        out[5] = box.bottom() - inset;
        out[6] = box.x + inset + Math.max(0f, shear);
        out[7] = box.bottom() - inset;
    }

    /** The leading bar of a plate: its left edge, extruded inwards. */
    private static void barQuad(float[] quad, float width, float[] out) {
        float x0 = quad[0];
        float y0 = quad[1];
        float x1 = quad[6];
        float y1 = quad[7];
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            out[0] = x0; out[1] = y0; out[2] = x0; out[3] = y0;
            out[4] = x0; out[5] = y0; out[6] = x0; out[7] = y0;
            return;
        }
        float nx = -dy / len;
        float ny = dx / len;
        if ((quad[4] - x0) * nx + (quad[5] - y0) * ny < 0f) {
            nx = -nx;
            ny = -ny;
        }
        out[0] = x0;
        out[1] = y0;
        out[2] = x1;
        out[3] = y1;
        out[4] = x1 + nx * width;
        out[5] = y1 + ny * width;
        out[6] = x0 + nx * width;
        out[7] = y0 + ny * width;
    }

    /** Projects a board-space quad into another array. */
    private static void projectQuad(State s, float[] board, float[] out, Projector.Plane plane) {
        project(s, board[0], board[1], plane, out, 0);
        project(s, board[2], board[3], plane, out, 2);
        project(s, board[4], board[5], plane, out, 4);
        project(s, board[6], board[7], plane, out, 6);
    }

    private static void layoutQuad(Projector projector, Box box, Projector.Plane plane, float[] out) {
        projector.project(box.x, box.y, plane, out, 0);
        projector.project(box.right(), box.y, plane, out, 2);
        projector.project(box.right(), box.bottom(), plane, out, 4);
        projector.project(box.x, box.bottom(), plane, out, 6);
    }

    // ---------------------------------------------------------------- primitives

    private static void project(State s, float x, float y, Projector.Plane plane, float[] out, int offset) {
        s.projector.project(x, y, plane, out, offset);
    }

    private static void project(Projector projector, float x, float y, Projector.Plane plane,
                                float[] out, int offset) {
        projector.project(x, y, plane, out, offset);
    }

    /**
     * Hairline width in canvas units.
     *
     * <p>Every stroke used to be a flat {@code 1.0}, which is one device pixel at GUI scale 1 — on a
     * 2560-wide window that is a hairline nobody can see.  Widths are now stated in board units and
     * multiplied by the board's own scale, with a floor of 1.2 canvas units so a small window still
     * shows a line at all.
     */
    private static float lw(State s, float base) {
        return Math.max(1.2f, base * Math.max(s.layout.scale, 0.85f));
    }

    /** Outline of a quad, one hairline per side. */
    private static void frame(SplashPainter p, float[] quad, float[] colour, float alpha, float width) {
        if (alpha <= 0.004f) {
            return;
        }
        SEG[0] = quad[0]; SEG[1] = quad[1]; SEG[2] = quad[2]; SEG[3] = quad[3];
        SEG[4] = quad[2]; SEG[5] = quad[3]; SEG[6] = quad[4]; SEG[7] = quad[5];
        SEG[8] = quad[4]; SEG[9] = quad[5]; SEG[10] = quad[6]; SEG[11] = quad[7];
        SEG[12] = quad[6]; SEG[13] = quad[7]; SEG[14] = quad[0]; SEG[15] = quad[1];
        p.lines(SEG, 4, colour[0], colour[1], colour[2], alpha, width);
    }

    /** The project's "armed" mark: four L-shaped corner ticks just outside the plate. */
    private static void cornerTicks(SplashPainter p, State s, float[] quad, float strength, float alpha) {
        float off = 3f * s.layout.scale;
        float len = 8f * s.layout.scale;
        int lines = 0;
        for (int i = 0; i < 4; i++) {
            int a = i * 2;
            int b = ((i + 1) % 4) * 2;
            int c = ((i + 3) % 4) * 2;
            float cx = quad[a];
            float cy = quad[a + 1];
            float ux = quad[b] - cx;
            float uy = quad[b + 1] - cy;
            float vx = quad[c] - cx;
            float vy = quad[c + 1] - cy;
            float ul = (float) Math.sqrt(ux * ux + uy * uy);
            float vl = (float) Math.sqrt(vx * vx + vy * vy);
            if (ul < 0.001f || vl < 0.001f) {
                continue;
            }
            ux /= ul; uy /= ul; vx /= vl; vy /= vl;
            float sx = cx + (ux + vx) * off;
            float sy = cy + (uy + vy) * off;
            SEG[lines * 4] = sx; SEG[lines * 4 + 1] = sy;
            SEG[lines * 4 + 2] = sx + ux * len; SEG[lines * 4 + 3] = sy + uy * len;
            lines++;
            SEG[lines * 4] = sx; SEG[lines * 4 + 1] = sy;
            SEG[lines * 4 + 2] = sx + vx * len; SEG[lines * 4 + 3] = sy + vy * len;
            lines++;
        }
        p.lines(SEG, lines, GOLD[0], GOLD[1], GOLD[2], 0.85f * strength * alpha, lw(s, 1.8f));
    }

    private static void fillEllipse(SplashPainter p, State s, Projector.Plane plane, float cx, float cy,
                                    float radius, float[] colour, float alpha, int segments) {
        if (alpha <= 0.004f) {
            return;
        }
        int n = Math.min(segments, SEGMENTS);
        project(s, cx, cy, plane, HELPER, 0);
        for (int i = 0; i <= n; i++) {
            double angle = Math.PI * 2.0 * i / n;
            project(s, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius,
                    plane, PTS, i * 2);
        }
        for (int i = 0; i < n; i++) {
            FAN[i * 8] = HELPER[0];
            FAN[i * 8 + 1] = HELPER[1];
            FAN[i * 8 + 2] = PTS[i * 2];
            FAN[i * 8 + 3] = PTS[i * 2 + 1];
            FAN[i * 8 + 4] = PTS[(i + 1) * 2];
            FAN[i * 8 + 5] = PTS[(i + 1) * 2 + 1];
            FAN[i * 8 + 6] = HELPER[0];
            FAN[i * 8 + 7] = HELPER[1];
        }
        p.quads(FAN, n, colour[0], colour[1], colour[2], alpha);
    }

    private static void strokeEllipse(SplashPainter p, State s, Projector.Plane plane, float cx, float cy,
                                      float radius, float[] colour, float alpha, float width, int segments) {
        strokeArc(p, s, plane, cx, cy, radius, 0f, 360f, colour, alpha, width, segments);
    }

    private static void strokeArc(SplashPainter p, State s, Projector.Plane plane, float cx, float cy,
                                  float radius, float fromDeg, float toDeg, float[] colour,
                                  float alpha, float width, int segments) {
        if (alpha <= 0.004f || radius <= 0f) {
            return;
        }
        int n = Math.min(segments, SEGMENTS);
        for (int i = 0; i < n; i++) {
            double a0 = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / n);
            double a1 = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 1) / n);
            project(s, cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius,
                    plane, SEG, i * 4);
            project(s, cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius,
                    plane, SEG, i * 4 + 2);
        }
        p.lines(SEG, n, colour[0], colour[1], colour[2], alpha, width);
    }

    /** Screen-space arc, for glyphs that are not on the board. */
    private static void strokeArcScreen(SplashPainter p, float cx, float cy, float radius,
                                        float fromDeg, float toDeg, float[] colour, float alpha,
                                        float width, int segments) {
        if (alpha <= 0.004f || radius <= 0f) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / segments);
            double a1 = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 1) / segments);
            SEG[i * 4] = cx + (float) Math.cos(a0) * radius;
            SEG[i * 4 + 1] = cy + (float) Math.sin(a0) * radius;
            SEG[i * 4 + 2] = cx + (float) Math.cos(a1) * radius;
            SEG[i * 4 + 3] = cy + (float) Math.sin(a1) * radius;
        }
        p.lines(SEG, segments, colour[0], colour[1], colour[2], alpha, width);
    }

    // ---------------------------------------------------------------- text

    /**
     * A label: one draw only.
     *
     * <p>An earlier version drew every string twice, a hair apart, to fake a bold face.  It does
     * thicken the strokes, but it also smears every edge and doubles the vertex count for text that
     * is already the busiest thing on the screen, so labels are now a single pass and weight is
     * bought with size instead.
     */
    private static void label(SplashPainter p, State s, String text, float x, float y,
                              float scale, float[] colour, float alpha) {
        if (text == null || text.isEmpty() || alpha <= 0.004f) {
            return;
        }
        p.text(text, x, y, scale, colour[0], colour[1], colour[2], alpha);
    }

    private static void textFit(SplashPainter p, State s, String text, float x, float y,
                                float maxBoardWidth, float size, float[] colour, float alpha) {
        if (text == null || text.isEmpty() || alpha <= 0.004f) {
            return;
        }
        float scale = size * s.layout.scale / 9f;
        float width = p.textWidth(text, scale);
        float max = maxBoardWidth * s.layout.scale;
        if (width > max && width > 0.01f) {
            scale *= max / width;
        }
        label(p, s, text, x, y, scale, colour, alpha);
    }

    // ---------------------------------------------------------------- icons
    // The reference's icon vocabulary, redrawn with lines and rectangles.  Each glyph is described on
    // a 24-unit grid and scaled to `size`; none of it is a copy of the original SVG.

    private static void icon(SplashPainter p, PauseAction action, float cx, float cy, float size,
                             float[] colour, float alpha) {
        if (alpha <= 0.01f || size <= 2f) {
            return;
        }
        float u = size / 24f;
        float r = colour[0];
        float g = colour[1];
        float b = colour[2];
        float w = Math.max(1.4f, size / 14f);
        switch (action) {
            case RESUME:
                p.line(cx - 6f * u, cy - 8f * u, cx + 8f * u, cy, r, g, b, alpha, w);
                p.line(cx + 8f * u, cy, cx - 6f * u, cy + 8f * u, r, g, b, alpha, w);
                p.line(cx - 6f * u, cy + 8f * u, cx - 6f * u, cy - 8f * u, r, g, b, alpha, w);
                break;
            case DISCONNECT:
                strokeArcScreen(p, cx, cy, size * 0.34f, 118f, 422f, colour, alpha, w, 16);
                p.line(cx, cy - size * 0.5f, cx, cy, r, g, b, alpha, w);
                break;
            case OPTIONS:
            case MOD_OPTIONS:
                strokeArcScreen(p, cx, cy, size * 0.30f, 0f, 360f, colour, alpha, w, 20);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2.0 * i / 8.0;
                    float ca = (float) Math.cos(a);
                    float sa = (float) Math.sin(a);
                    p.line(cx + ca * size * 0.30f, cy + sa * size * 0.30f,
                            cx + ca * size * 0.46f, cy + sa * size * 0.46f, r, g, b, alpha, w);
                }
                break;
            case ADVANCEMENTS:
                p.line(cx - size * 0.28f, cy - size * 0.44f, cx - size * 0.28f, cy + size * 0.44f,
                        r, g, b, alpha, w);
                p.line(cx - size * 0.28f, cy - size * 0.40f, cx + size * 0.40f, cy - size * 0.18f,
                        r, g, b, alpha, w);
                p.line(cx + size * 0.40f, cy - size * 0.18f, cx - size * 0.28f, cy + size * 0.04f,
                        r, g, b, alpha, w);
                break;
            case STATISTICS: {
                float[] heights = {0.22f, 0.36f, 0.50f};
                for (int i = 0; i < 3; i++) {
                    float bx = cx - size * 0.34f + i * size * 0.26f;
                    p.line(bx, cy + size * 0.36f, bx, cy + size * 0.36f - size * heights[i],
                            r, g, b, alpha, Math.max(3f, size / 6f));
                }
                break;
            }
            case WORLD_OPTIONS:
                chipDot(p, cx, cy, size * 0.09f, r, g, b, alpha);
                strokeArcScreen(p, cx, cy, size * 0.26f, 140f, 400f, colour, alpha, w, 12);
                strokeArcScreen(p, cx, cy, size * 0.42f, 145f, 395f, colour, alpha, w, 14);
                break;
            case SHOP: {
                // price tag: a plate with a clipped corner and a hole
                float hw = size * 0.34f;
                float hh = size * 0.26f;
                p.line(cx - hw, cy - hh, cx + hw * 0.55f, cy - hh, r, g, b, alpha, w);
                p.line(cx + hw * 0.55f, cy - hh, cx + hw, cy - hh * 0.45f, r, g, b, alpha, w);
                p.line(cx + hw, cy - hh * 0.45f, cx + hw, cy + hh, r, g, b, alpha, w);
                p.line(cx + hw, cy + hh, cx - hw, cy + hh, r, g, b, alpha, w);
                p.line(cx - hw, cy + hh, cx - hw, cy - hh, r, g, b, alpha, w);
                strokeArcScreen(p, cx - hw * 0.30f, cy + hh * 0.05f, size * 0.09f,
                        0f, 360f, colour, alpha, w, 10);
                break;
            }
            case NEWS: {
                // a page with ruled lines and a bar down its left edge
                float hw = size * 0.30f;
                float hh = size * 0.36f;
                boxScreen(p, cx, cy, hw, hh, r, g, b, alpha, w);
                p.line(cx - hw + size * 0.06f, cy - hh + size * 0.07f,
                        cx - hw + size * 0.06f, cy + hh - size * 0.07f, r, g, b, alpha, w);
                for (int i = 0; i < 3; i++) {
                    float y = cy - hh * 0.45f + i * hh * 0.45f;
                    p.line(cx - hw * 0.42f, y, cx + hw * 0.72f, y, r, g, b, alpha, w);
                }
                break;
            }
            case DOCS: {
                // closed book: cover outline, spine, and three ruled lines on the page
                float hw = size * 0.30f;
                float hh = size * 0.34f;
                boxScreen(p, cx, cy, hw, hh, r, g, b, alpha, w);
                p.line(cx - hw * 0.35f, cy - hh, cx - hw * 0.35f, cy + hh, r, g, b, alpha, w);
                for (int i = 0; i < 3; i++) {
                    float y = cy - hh * 0.45f + i * hh * 0.45f;
                    p.line(cx - hw * 0.10f, y, cx + hw * 0.70f, y, r, g, b, alpha, w);
                }
                break;
            }
            case QQ: {
                // two overlapping speech bubbles: a conversation, not a single message
                float hw = size * 0.26f;
                float hh = size * 0.18f;
                boxScreen(p, cx - size * 0.08f, cy - size * 0.10f, hw, hh, r, g, b, alpha, w);
                boxScreen(p, cx + size * 0.10f, cy + size * 0.12f, hw, hh, r, g, b, alpha, w);
                break;
            }
            case DISPLAY: {
                float hw = size * 0.38f;
                float hh = size * 0.26f;
                boxScreen(p, cx, cy - size * 0.05f, hw, hh, r, g, b, alpha, w);
                p.line(cx, cy + hh - size * 0.05f, cx, cy + hh + size * 0.12f, r, g, b, alpha, w);
                p.line(cx - hw * 0.45f, cy + hh + size * 0.12f, cx + hw * 0.45f,
                        cy + hh + size * 0.12f, r, g, b, alpha, w);
                break;
            }
            case CONTROLS: {
                float hw = size * 0.40f;
                float hh = size * 0.24f;
                boxScreen(p, cx, cy, hw, hh, r, g, b, alpha, w);
                for (int i = -1; i <= 1; i++) {
                    chipDot(p, cx + i * size * 0.19f, cy + size * 0.09f, size * 0.035f, r, g, b, alpha);
                }
                break;
            }
            case SOUND: {
                float x0 = cx - size * 0.40f;
                float y0 = cy - size * 0.13f;
                float x1 = x0 + size * 0.16f;
                float y1 = cy + size * 0.13f;
                p.line(x0, y0, x1, y0, r, g, b, alpha, w);
                p.line(x0, y0, x0, y1, r, g, b, alpha, w);
                p.line(x0, y1, x1, y1, r, g, b, alpha, w);
                p.line(x1, y0, x1 + size * 0.18f, cy - size * 0.34f, r, g, b, alpha, w);
                p.line(x1 + size * 0.18f, cy - size * 0.34f, x1 + size * 0.18f,
                        cy + size * 0.34f, r, g, b, alpha, w);
                p.line(x1 + size * 0.18f, cy + size * 0.34f, x1, y1, r, g, b, alpha, w);
                strokeArcScreen(p, cx, cy, size * 0.26f, -52f, 52f, colour, alpha, w, 8);
                strokeArcScreen(p, cx, cy, size * 0.38f, -52f, 52f, colour, alpha, w, 10);
                break;
            }
            case CHAT: {
                float hw = size * 0.36f;
                float hh = size * 0.22f;
                boxScreen(p, cx, cy - size * 0.04f, hw, hh, r, g, b, alpha, w);
                p.line(cx - hw + size * 0.12f, cy + hh - size * 0.04f,
                        cx - hw + size * 0.12f, cy + hh + size * 0.16f, r, g, b, alpha, w);
                p.line(cx - hw + size * 0.12f, cy + hh + size * 0.16f,
                        cx - hw + size * 0.02f, cy + hh - size * 0.04f, r, g, b, alpha, w);
                break;
            }
            case LANGUAGE:
                strokeArcScreen(p, cx, cy, size * 0.38f, 0f, 360f, colour, alpha, w, 20);
                p.line(cx - size * 0.38f, cy, cx + size * 0.38f, cy, r, g, b, alpha, w);
                strokeEllipseScreen(p, cx, cy, size * 0.16f, size * 0.38f, colour, alpha, w, 18);
                break;
            case PACKS: {
                float hw = size * 0.28f;
                float hh = size * 0.20f;
                for (int i = 1; i >= 0; i--) {
                    float ox = i * size * 0.09f;
                    float oy = -i * size * 0.09f;
                    boxScreen(p, cx + ox, cy + oy, hw, hh, r, g, b, alpha, w);
                }
                break;
            }
            case SKIN:
                strokeArcScreen(p, cx, cy - size * 0.20f, size * 0.17f, 0f, 360f, colour, alpha, w, 14);
                strokeArcScreen(p, cx, cy + size * 0.62f, size * 0.42f, 218f, 322f, colour, alpha, w, 12);
                break;
            case DEBUG:
                strokeArcScreen(p, cx - size * 0.07f, cy - size * 0.07f, size * 0.25f,
                        0f, 360f, colour, alpha, w, 18);
                p.line(cx + size * 0.11f, cy + size * 0.11f, cx + size * 0.38f, cy + size * 0.38f,
                        r, g, b, alpha, w);
                break;            default:
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2.0 * i / 6.0;
                    chipDot(p, cx + (float) Math.cos(a) * size * 0.30f,
                            cy + (float) Math.sin(a) * size * 0.30f, size * 0.08f, r, g, b, alpha);
                }
                break;
        }
    }

    private static void boxScreen(SplashPainter p, float cx, float cy, float hw, float hh,
                                  float r, float g, float b, float alpha, float w) {
        p.line(cx - hw, cy - hh, cx + hw, cy - hh, r, g, b, alpha, w);
        p.line(cx - hw, cy + hh, cx + hw, cy + hh, r, g, b, alpha, w);
        p.line(cx - hw, cy - hh, cx - hw, cy + hh, r, g, b, alpha, w);
        p.line(cx + hw, cy - hh, cx + hw, cy + hh, r, g, b, alpha, w);
    }

    private static void strokeEllipseScreen(SplashPainter p, float cx, float cy, float rx, float ry,
                                            float[] colour, float alpha, float width, int segments) {
        if (alpha <= 0.004f || rx <= 0f || ry <= 0f) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            SEG[i * 4] = cx + (float) Math.cos(a0) * rx;
            SEG[i * 4 + 1] = cy + (float) Math.sin(a0) * ry;
            SEG[i * 4 + 2] = cx + (float) Math.cos(a1) * rx;
            SEG[i * 4 + 3] = cy + (float) Math.sin(a1) * ry;
        }
        p.lines(SEG, segments, colour[0], colour[1], colour[2], alpha, width);
    }

    private static void chipDot(SplashPainter p, float cx, float cy, float radius,
                                float r, float g, float b, float alpha) {
        p.rect(cx - radius, cy - radius, radius * 2f, radius * 2f, r, g, b, alpha);
    }
}
