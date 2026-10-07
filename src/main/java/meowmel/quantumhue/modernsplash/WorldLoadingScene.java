package meowmel.quantumhue.modernsplash;

import static meowmel.quantumhue.modernsplash.SplashTheme.ACCENT;
import static meowmel.quantumhue.modernsplash.SplashTheme.GOLD;
import static meowmel.quantumhue.modernsplash.SplashTheme.LINE;
import static meowmel.quantumhue.modernsplash.SplashTheme.SHELL_PANEL;
import static meowmel.quantumhue.modernsplash.SplashTheme.TEXT;
import static meowmel.quantumhue.modernsplash.SplashTheme.TEXT_DIM;

/**
 * The screen shown while a world is loading and the player is about to enter it.
 *
 * <p>Vanilla paints it as an 8x8 tiling of {@code options_background.png} with two lines of text
 * and a two-tone progress bar.  This paints the shared {@link Backdrop} instead — the same wash,
 * board and grid as the boot splash and the main menu — and then a scene: a Dyson sphere's upper
 * limb across the bottom of the frame, with a satellite crossing its orbit from the left edge to
 * the right.
 *
 * <p>The satellite's position along that orbit <em>is</em> the progress indicator, and the arc it
 * has already covered is lit behind it.  A bar would say how far along the load is; a satellite
 * crossing its orbit says the same thing and belongs in the fiction.
 *
 * <p>The sphere is the same {@link DysonSphere} the boot splash and the main menu use, seen from
 * far closer: the camera sits near its equator looking at the lid, so all that fits in frame is the
 * top of the shell.
 *
 * <h3>Primitives</h3>
 * Only {@code rect}, {@code line}, {@code lines}, {@code quads}, {@code glow}, {@code blendMode} and
 * the text calls.  The in-game backend deliberately does not implement {@code arc} or the transform
 * stack, so every curve and every tilted shape here is computed into coordinates by hand rather than
 * rotated by the painter — and the offline preview, which does implement everything, is therefore
 * still a faithful stand-in.
 *
 * <h3>Origin</h3>
 * The caller supplies {@code left} and {@code top}, exactly as {@link Backdrop} does, because the
 * two backends disagree about where the origin is: {@code GlStatePainter} draws in GUI space with
 * {@code (0, 0)} at the top-left, while the AWT painter used by the previews draws in the splash's
 * centre-based scene space.
 */
public final class WorldLoadingScene {

    private WorldLoadingScene() {}

    /** Everything the screen needs for one frame. */
    public static final class State {
        public int width;
        public int height;
        /** Left edge of the canvas, in the painter's own coordinates. */
        public float left;
        /** Top edge of the canvas. */
        public float top;
        /** Vanilla's title line, e.g. "Loading world".  May be empty. */
        public String title = "";
        /** Vanilla's detail line, e.g. "Building terrain".  May be empty. */
        public String detail = "";
        /** 0..1; negative means "no progress is known", and the satellite loiters instead. */
        public float progress = -1f;
        /** Clock in seconds, taken as a double so the drift never quantises at high uptimes. */
        public double seconds;
    }

    // ---------------------------------------------------------------- layout
    // Every measurement is a fraction of the canvas, so the scene is identical at any resolution and
    // any GUI scale.  Absolute pixel values anywhere in here would be the bug this project has
    // already shipped twice.

    /** Dyson silhouette radius, as a fraction of the canvas height. */
    private static final float DYSON_R = 1.90f;
    /** Height of the top of the shell.  Everything below is off-screen. */
    private static final float DYSON_LIMB = 0.760f;
    /**
     * Viewport height handed to {@link DysonSphere}, as a fraction of the canvas height.
     *
     * <p>It does not affect the silhouette — {@code DYSON_R} is the on-screen radius — it only sets
     * the camera distance, {@code dist = cot * viewportH/2 / radiusPx}.  The projection divides by
     * {@code dist - az}, so {@code dist} has to stay above 1 or the camera ends up inside the shell
     * and the geometry inverts.  At the true canvas height, {@code DYSON_R = 1.9} would give
     * {@code dist = 0.72}; this value puts it near 4, which is also a pleasingly flat perspective
     * for something this large.
     */
    private static final float DYSON_VIEWPORT = 5.50f;
    /** Tilt of the shell, so the swarm's own rings cross the visible lid. */
    private static final float DYSON_TILT = 24f;

    /** Height of the orbit's own centre. */
    private static final float ORBIT_CY = 0.880f;
    /** Orbit semi-major axis, as a fraction of the canvas width. */
    private static final float ORBIT_RX = 0.520f;
    /** Orbit semi-minor axis, as a fraction of the canvas height. */
    private static final float ORBIT_RY = 0.320f;
    /** Orbit parameter at the left of the crossing, as a fraction of pi. */
    private static final float PHI_FROM = 0.86f;
    /** Orbit parameter at the right of the crossing, as a fraction of pi. */
    private static final float PHI_TO = 0.14f;

    /** Silhouette radius of the miniature sphere that crosses the orbit, as a fraction of height. */
    private static final float MINI_R = 0.052f;
    /**
     * Camera distance for the miniature sphere, again via {@code viewportH}.
     *
     * <p>Only the perspective strength: {@code MINI_R} fixes the on-screen size.  Near 5 rather than
     * the far-away value the true canvas height would give, because a sphere this small with a
     * near-orthographic projection reads as a flat disc of wire.
     */
    private static final float MINI_VIEWPORT = 0.19f;

    /** Inset of the HUD from the screen edge, as a fraction of the short side. */
    private static final float INSET = 0.055f;

    // ---------------------------------------------------------------- reused buffers

    /**
     * The plasma ribbon behind the transit.
     *
     * <p>Reused rather than reallocated: this screen runs while chunk generation is saturating the
     * same thread, so nothing in the draw path may allocate per frame.
     */
    private static final Batch RIBBON = new Batch(64);

    private static final float[] SEGS = new float[1 << 12];
    private static int segCount;

    private static final float[] TRAIL = new float[128];

    private static final DysonSphere.Buffers SPHERE = new DysonSphere.Buffers();

    /**
     * The transit's own copy.
     *
     * <p>{@code DysonSphere.build} resets the buffer it is handed, so sharing one would erase the
     * limb across the bottom on the very frame the transit is drawn.
     */
    private static final DysonSphere.Buffers MINI = new DysonSphere.Buffers();

    public static void draw(SplashPainter p, State s) {
        float w = s.width;
        float h = s.height;
        float x0 = s.left;
        float y0 = s.top;
        float min = Math.min(w, h);

        // The same three layers as every other screen.
        Backdrop.paint(p, x0, y0, w, h, s.seconds);

        float inset = min * INSET;
        p.blendMode(false);

        // 0..1 crossing.  Unknown progress loiters near the left instead of advancing, so the scene
        // is never frozen — the satellite is the indicator, and a parked one would read as a hang.
        float t = s.progress < 0f
                ? 0.02f + 0.14f * (0.5f - 0.5f * (float) Math.cos(s.seconds * 0.75))
                : SplashTimeline.clamp01(s.progress);

        dyson(p, x0, y0, w, h, s.seconds);
        orbit(p, x0, y0, w, h, t);
        transit(p, x0, y0, w, h, s.seconds, t);

        rails(p, x0, y0, w, h, inset);
        hud(p, x0, y0, w, h, inset, s);
    }

    // ---------------------------------------------------------------- the swarm

    /**
     * The Dyson sphere, as its upper limb.
     *
     * <p>Its centre is placed one full silhouette radius below the limb, so the visible part is the
     * lid of the shell and nothing else.  The back half is drawn before the orbit and the front half
     * after it, which is the same painter's-algorithm split the boot splash uses.
     */
    private static void dyson(SplashPainter p, float x0, float y0, float w, float h, double seconds) {
        float cx = x0 + w * 0.5f;
        float r = h * DYSON_R;
        float cy = y0 + h * DYSON_LIMB + r;

        DysonSphere.build((float) (seconds % 3600.0), 1f,
                (float) ((seconds * 6.5) % 360.0), DYSON_TILT,
                cx, cy, r, h * DYSON_VIEWPORT, SPHERE);

        // ---- back of the shell
        p.blendMode(false);
        p.quads(SPHERE.panelBack, SPHERE.panelBackCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], SplashTheme.Layer.SWARM_BACK);
        p.lines(SPHERE.structBack, SPHERE.structBackCount,
                ACCENT[0], ACCENT[1], ACCENT[2], SplashTheme.Layer.RING_BACK * 0.6f, 1f);
        p.blendMode(true);
        p.lines(SPHERE.energyBack, SPHERE.energyBackCount,
                GOLD[0], GOLD[1], GOLD[2], SplashTheme.Layer.ARC_BACK * 0.5f, 1.4f);
        p.blendMode(false);
    }

    /** The front half of the shell, drawn after the orbit so the satellite passes behind it. */
    private static void swarmFront(SplashPainter p) {
        p.blendMode(false);
        p.quads(SPHERE.panelFront, SPHERE.panelFrontCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], SplashTheme.Layer.SWARM_FRONT);
        p.blendMode(true);
        p.lines(SPHERE.energyFront, SPHERE.energyFrontCount,
                GOLD[0], GOLD[1], GOLD[2], SplashTheme.Layer.ARC_FRONT * 0.55f, 1.6f);
        p.lines(SPHERE.structFront, SPHERE.structFrontCount,
                ACCENT[0], ACCENT[1], ACCENT[2], SplashTheme.Layer.RING_FRONT * 0.7f, 1f);
        p.blendMode(false);
    }

    // ---------------------------------------------------------------- orbit

    private static void orbit(SplashPainter p, float x0, float y0, float w, float h, float t) {
        float cx = x0 + w * 0.5f;
        float cy = y0 + h * ORBIT_CY;
        float rx = w * ORBIT_RX;
        float ry = h * ORBIT_RY;

        swimLane(cx, cy, rx, ry, PHI_FROM, PHI_TO, 64);
        flushDim(p, 0.10f, 1f);

        // The covered part of the crossing, lit.  A second reading of the same number the transit
        // is already showing, which is what makes the progress legible at a glance.
        swimLane(cx, cy, rx, ry, PHI_FROM, phi(t), 64);
        flush(p, 0.58f, 1.8f);
    }

    /** Emits the orbit as line segments between two orbit parameters. */
    private static void swimLane(float cx, float cy, float rx, float ry,
                                 float fromPi, float toPi, int steps) {
        segCount = 0;
        float prevX = 0f;
        float prevY = 0f;
        for (int i = 0; i <= steps; i++) {
            float phi = (float) Math.PI * (fromPi + (toPi - fromPi) * i / steps);
            float px = cx + (float) Math.cos(phi) * rx;
            float py = cy - (float) Math.sin(phi) * ry;
            if (i > 0) {
                SEGS[segCount * 4] = prevX;
                SEGS[segCount * 4 + 1] = prevY;
                SEGS[segCount * 4 + 2] = px;
                SEGS[segCount * 4 + 3] = py;
                segCount++;
            }
            prevX = px;
            prevY = py;
        }
    }

    /** Orbit parameter for a crossing fraction, left to right. */
    private static float phi(float t) {
        return PHI_FROM + (PHI_TO - PHI_FROM) * t;
    }

    // ---------------------------------------------------------------- the transit

    /**
     * The thing that crosses the orbit: a second, miniature Dyson sphere.
     *
     * <p>Deliberately the same {@link DysonSphere} as the one across the bottom, four hundred times
     * smaller, rather than an unrelated satellite model.  The eye reads the small one as the large
     * one seen from far away, so the crossing says "the swarm travels" instead of "a prop moves".
     *
     * <p>It gets its own {@link DysonSphere.Buffers}: {@code build} resets whatever it is given, so
     * sharing one buffer would erase the limb across the bottom on the same frame.
     */
    private static void transit(SplashPainter p, float x0, float y0, float w, float h,
                                double seconds, float t) {
        float cx = x0 + w * 0.5f;
        float cy = y0 + h * ORBIT_CY;
        float rx = w * ORBIT_RX;
        float ry = h * ORBIT_RY;

        float px = cx + (float) Math.cos(Math.PI * phi(t)) * rx;
        float py = cy - (float) Math.sin(Math.PI * phi(t)) * ry;
        float r = Math.max(2f, MINI_R * h);

        trail(p, x0, y0, w, h, t, cx, cy, rx, ry, r);

        // The mini sphere, over the trail.
        DysonSphere.build((float) (seconds % 3600.0), 1f,
                (float) ((seconds * 34.0) % 360.0), DYSON_TILT,
                px, py, r, Math.max(4f, h * MINI_VIEWPORT), MINI);

        // A bloom behind it, so it reads as a light source rather than as a ball of wire.
        p.blendMode(true);
        p.glow(px, py, r * 3.0f, ACCENT[0], ACCENT[1], ACCENT[2], 0.18f, 28);
        p.glow(px, py, r * 1.5f, GOLD[0], GOLD[1], GOLD[2], 0.22f, 20);
        p.blendMode(false);

        p.blendMode(false);
        p.quads(MINI.panelBack, MINI.panelBackCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], 0.18f);
        p.lines(MINI.structBack, MINI.structBackCount,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.30f, 1f);
        p.blendMode(true);
        p.lines(MINI.energyBack, MINI.energyBackCount,
                GOLD[0], GOLD[1], GOLD[2], 0.45f, 1.2f);
        p.blendMode(false);
        p.quads(MINI.panelFront, MINI.panelFrontCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], 0.30f);
        p.blendMode(true);
        p.lines(MINI.energyFront, MINI.energyFrontCount,
                GOLD[0], GOLD[1], GOLD[2], 0.65f, 1.4f);
        p.lines(MINI.structFront, MINI.structFrontCount,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.42f, 1f);
        p.blendMode(false);

        // Power link down to the swarm: the reason it is crossing at all.
        p.blendMode(true);
        p.line(px, py + r, px, y0 + h * DYSON_LIMB, ACCENT[0], ACCENT[1], ACCENT[2], 0.16f, 1f);
        p.glow(px, y0 + h * DYSON_LIMB, h * 0.030f, ACCENT[0], ACCENT[1], ACCENT[2], 0.30f, 20);
        p.blendMode(false);

        // Now the near half of the limb, so the transit reads as passing behind it.
        swarmFront(p);
    }

    /**
     * A tapering plasma ribbon behind the transit.
     *
     * <p>Stateless: the orbit is a pure function of {@code t}, so the wake is sampled by asking
     * where the object was a moment ago rather than by keeping a history buffer.  The ribbon is
     * widened along each segment's own normal — offsetting in screen {@code y} instead produces a
     * comb of slivers, which is exactly what the first version of this did.
     */
    private static void trail(SplashPainter p, float x0, float y0, float w, float h, float t,
                              float cx, float cy, float rx, float ry, float r) {
        int n = 16;
        int count = 0;
        for (int i = 0; i <= n; i++) {
            float sn = t - 0.085f * (1f - i / (float) n);
            if (sn < 0f) {
                continue;
            }
            TRAIL[count * 2] = cx + (float) Math.cos(Math.PI * phi(sn)) * rx;
            TRAIL[count * 2 + 1] = cy - (float) Math.sin(Math.PI * phi(sn)) * ry;
            count++;
        }
        if (count < 2) {
            return;
        }

        RIBBON.clear();
        float maxHalf = Math.max(1f, r * 0.34f);
        for (int i = 1; i < count; i++) {
            float ax = TRAIL[(i - 1) * 2];
            float ay = TRAIL[(i - 1) * 2 + 1];
            float bx = TRAIL[i * 2];
            float by = TRAIL[i * 2 + 1];
            float vx = bx - ax;
            float vy = by - ay;
            float len = (float) Math.sqrt(vx * vx + vy * vy);
            if (len < 1e-4f) {
                continue;
            }
            float nx = -vy / len;
            float ny = vx / len;
            float wa = maxHalf * (i - 1) / (float) (count - 1);
            float wb = maxHalf * i / (float) (count - 1);
            if (wb < 0.4f) {
                continue;
            }
            RIBBON.quad(ax + nx * wa, ay + ny * wa,
                    ax - nx * wa, ay - ny * wa,
                    bx - nx * wb, by - ny * wb,
                    bx + nx * wb, by + ny * wb);
        }
        RIBBON.flush(p, GOLD, 0.16f);
    }

    // ---------------------------------------------------------------- chrome

    /** Top and bottom hairlines with corner brackets; the same chrome the boot splash uses. */
    private static void rails(SplashPainter p, float x0, float y0, float w, float h, float inset) {
        float left = x0 + inset;
        float right = x0 + w - inset;
        float top = y0 + inset;
        float bottom = y0 + h - inset;

        p.line(left, top, right, top, LINE[0], LINE[1], LINE[2], 0.22f, 1f);
        p.line(left, bottom, right, bottom, LINE[0], LINE[1], LINE[2], 0.22f, 1f);
        bracket(p, left, top, 1f, 1f);
        bracket(p, right, top, -1f, 1f);
        bracket(p, left, bottom, 1f, -1f);
        bracket(p, right, bottom, -1f, -1f);
    }

    private static void bracket(SplashPainter p, float x, float y, float dx, float dy) {
        float len = 18f;
        p.line(x, y, x + len * dx, y, ACCENT[0], ACCENT[1], ACCENT[2], 0.55f, 1f);
        p.line(x, y, x, y + len * dy, ACCENT[0], ACCENT[1], ACCENT[2], 0.55f, 1f);
    }

    /**
     * Brand, title, detail and a numeric readout.
     *
     * <p>Corner-anchored rather than centred: the crossing owns the middle of the frame, and a
     * caption in the centre would be the one thing the satellite flew through.
     */
    private static void hud(SplashPainter p, float x0, float y0, float w, float h, float inset, State s) {
        p.text(SplashTheme.BRAND + "  //  " + SplashTheme.WORLD_LABEL,
                x0 + inset, y0 + inset + 6f, 1.4f,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.95f);

        // The caller supplies both lines: this class is MC-free and has no way to tell loading a
        // world from saving one, and the same renderer draws both.
        //
        // The offsets are generous because the two backends do not agree on how tall a glyph is:
        // FontRenderer draws 9 units per line, while the AWT preview rasterises at 11 before scaling.
        // Spacing tuned to the game alone overlaps in the preview, which is the tool used to check it.
        if (s.title != null && !s.title.isEmpty()) {
            p.text(s.title, x0 + inset, y0 + inset + 30f, 1.8f, TEXT[0], TEXT[1], TEXT[2], 1f);
        }
        if (s.detail != null && !s.detail.isEmpty()) {
            p.text(s.detail, x0 + inset, y0 + inset + 58f, 1.3f,
                    TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.85f);
        }

        ticks(p, x0, y0, w, h, inset, s.progress, s.seconds);
    }

    /**
     * The corner readout: a row of ticks that fills, and a caption.
     *
     * <p>Not a number.  A percentage in the corner was the one thing on this screen that could fail
     * silently — it depends on the font actually having the glyphs, on the alpha byte surviving
     * {@code FontRenderer}, and on a right-aligned width being computed the same way the text is
     * drawn.  A row of rectangles depends on none of that, and it reads as an instrument rather
     * than as a debug print.
     *
     * <p>When the progress is unknown the row does not sit empty: a window of ticks sweeps along it,
     * so the corner still moves while the integrated server is still starting.
     */
    private static void ticks(SplashPainter p, float x0, float y0, float w, float h,
                              float inset, float progress, double seconds) {
        final int count = 22;
        float tw = Math.min(w * 0.0032f, h * 0.0062f);
        float gap = tw * 0.75f;
        float total = count * tw + (count - 1) * gap;
        float bx = x0 + w - inset - total;
        float by = y0 + inset + 16f;
        float th = Math.max(3f, h * 0.013f);

        int filled = progress < 0f ? -1 : (int) Math.ceil(SplashTimeline.clamp01(progress) * count);
        int head = (int) ((seconds * 7.0) % (count + 6)) - 5;

        for (int i = 0; i < count; i++) {
            boolean on = progress < 0f ? (i >= head && i <= head + 5) : i < filled;
            if (on) {
                p.rect(bx + i * (tw + gap), by, tw, th, GOLD[0], GOLD[1], GOLD[2], 0.95f);
            } else {
                p.rect(bx + i * (tw + gap), by, tw, th, LINE[0], LINE[1], LINE[2], 0.22f);
            }
        }

        float lw = p.textWidth(SplashTheme.ORBIT_LABEL, 1.2f);
        p.text(SplashTheme.ORBIT_LABEL, x0 + w - inset - lw, by + th + 8f, 1.2f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.70f);
    }

    // ---------------------------------------------------------------- helpers

    /** Emits the pending segments in the accent colour, then clears them. */
    private static void flush(SplashPainter p, float alpha, float width) {
        if (segCount > 0) {
            p.lines(SEGS, segCount, ACCENT[0], ACCENT[1], ACCENT[2], alpha, width);
            segCount = 0;
        }
    }

    private static void flushDim(SplashPainter p, float alpha, float width) {
        if (segCount > 0) {
            p.lines(SEGS, segCount, LINE[0], LINE[1], LINE[2], alpha, width);
            segCount = 0;
        }
    }

    /** Growable, reused quad accumulator so a layer costs one draw call, not one per shape. */
    private static final class Batch {
        private float[] quads;
        private int count;

        Batch(int capacity) {
            quads = new float[capacity * 8];
        }

        void clear() {
            count = 0;
        }

        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
            if ((count + 1) * 8 > quads.length) {
                quads = java.util.Arrays.copyOf(quads, quads.length * 2);
            }
            int o = count * 8;
            quads[o] = x0;
            quads[o + 1] = y0;
            quads[o + 2] = x1;
            quads[o + 3] = y1;
            quads[o + 4] = x2;
            quads[o + 5] = y2;
            quads[o + 6] = x3;
            quads[o + 7] = y3;
            count++;
        }

        void flush(SplashPainter p, float[] c, float a) {
            if (count > 0) {
                p.quads(quads, count, c[0], c[1], c[2], a);
            }
        }
    }
}
