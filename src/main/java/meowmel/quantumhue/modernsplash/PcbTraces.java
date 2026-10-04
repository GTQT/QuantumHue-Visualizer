package meowmel.quantumhue.modernsplash;

/**
 * A printed-circuit-board routing layer, used as the loading screen's background texture.
 *
 * <p>Orthogonal traces snapped to a 32px grid, each turning a few times with a via at every corner,
 * plus a handful of pulses sliding along them.  The routing is deterministically seeded, so the
 * board is stable for a given window size instead of crawling frame to frame; only the pulses move.
 *
 * <p>Emitted as flat, batched arrays so the caller draws the whole board in three calls — one for
 * the traces, one for the vias, one for the pulses — through whatever {@link SplashPainter} it has.
 * Coordinates are written in the caller's own space (the splash ortho), starting at
 * {@code (originX, originY)}.
 *
 * <p>Free of Minecraft / LWJGL imports so {@code tools/SplashPreview.java} replays it exactly.
 */
public final class PcbTraces {

    private PcbTraces() {}

    // ---------------------------------------------------------------- output

    /** Batched geometry, one flat float array per blend channel. */
    public static final class Buffers {
        /** Traces: x0,y0,x1,y1 quadruples. */
        public float[] lines = new float[1 << 13];
        public int lineCount;

        /** Pulses travelling along the traces; same layout. */
        public float[] pulses = new float[1 << 10];
        public int pulseCount;

        /** Vias: eight floats per quad. */
        public float[] vias = new float[1 << 12];
        public int viaCount;

        public void reset() {
            lineCount = 0;
            pulseCount = 0;
            viaCount = 0;
        }

        private static float[] grow(float[] array, int needed) {
            if (needed <= array.length) {
                return array;
            }
            int size = array.length;
            while (size < needed) {
                size <<= 1;
            }
            return new float[size];
        }

        void line(float x0, float y0, float x1, float y1) {
            lines = grow(lines, (lineCount + 1) * 4);
            int o = lineCount * 4;
            lines[o] = x0;
            lines[o + 1] = y0;
            lines[o + 2] = x1;
            lines[o + 3] = y1;
            lineCount++;
        }

        void pulse(float x0, float y0, float x1, float y1) {
            pulses = grow(pulses, (pulseCount + 1) * 4);
            int o = pulseCount * 4;
            pulses[o] = x0;
            pulses[o + 1] = y0;
            pulses[o + 2] = x1;
            pulses[o + 3] = y1;
            pulseCount++;
        }

        void via(float cx, float cy, float half) {
            vias = grow(vias, (viaCount + 1) * 8);
            int o = viaCount * 8;
            vias[o] = cx - half;
            vias[o + 1] = cy - half;
            vias[o + 2] = cx - half;
            vias[o + 3] = cy + half;
            vias[o + 4] = cx + half;
            vias[o + 5] = cy + half;
            vias[o + 6] = cx + half;
            vias[o + 7] = cy - half;
            viaCount++;
        }
    }

    // ---------------------------------------------------------------- tunables

    /**
     * Via half-size, as a fraction of the pitch.
     *
     * <p>This was an absolute {@code 2f} and it was the last scale-dependent quantity on the board,
     * which is why the menu's board still read as "too big" after the pitch itself was fixed: 2 units
     * is 2 device pixels on the splash but {@code 2 * guiScale} on the menu, so at GUI scale 3 the
     * vias — the brightest, squarest thing on the board — came out three times the size of the
     * splash's.  A position comparison is blind to that: the pattern is identical, the squares are
     * just larger.
     *
     * <p>{@code 1/24} of the pitch reproduces the original exactly at 1080p, where the pitch is 48,
     * so the splash is unchanged.
     */
    private static final float VIA_HALF = 1f / 24f;

    /** Half-size of the larger via that caps a trace; likewise anchored so 1080p is unchanged. */
    private static final float VIA_END_HALF = 2.5f / 48f;

    /**
     * Routing pitch, as a fraction of the canvas's short side.
     *
     * <p>Deliberately not an absolute number of units, and this is the whole reason the board used
     * to look like two different boards.  The splash draws in device pixels while the menu draws in
     * scaled GUI units, so a fixed pitch of 32 hands the very same call the very same trace count
     * over a canvas three times smaller at GUI scale 3 — the traces then span three times as much
     * of the screen and read as bold strokes instead of fine routing.
     *
     * <p>Anchored at 32/720 because 720 is the height the splash was tuned at, so the splash's
     * appearance is unchanged and the menu now lands on the same <em>device-pixel</em> pitch.
     */
    private static final float PITCH_RATIO = 32f / 720f;

    /** How many separate traces are routed. */
    private static final int TRACES = 32;
    /** Maximum corners per trace. */
    private static final int MAX_POINTS = 8;
    /** Pulses in flight at any moment. */
    private static final int PULSES = 10;

    // reused scratch: the traces, so pulses can be placed along them after they are emitted
    private static final float[] TRACE_X = new float[TRACES * MAX_POINTS];
    private static final float[] TRACE_Y = new float[TRACES * MAX_POINTS];
    private static final int[] TRACE_LEN = new int[TRACES];
    private static final int[] TRACE_OFFSET = new int[TRACES];

    /**
     * Routes the board and places the pulses.
     *
     * <p>The geometry itself does not depend on {@code phase} at all — only the pulses do, and
     * {@code phase} is expected to be a small, wrapped value (the callers use a 120-second window).
     * Feeding a raw {@code getSystemTime()} here would be a latent bug: that counter is milliseconds
     * since boot, so on a machine that has been up for a while the float loses all fractional
     * precision and the pulse position quantises to a standstill.
     *
     * @param w       width of the area to cover
     * @param h       height of the area to cover
     * @param phase   seconds within a small repeating window, drives the pulses only
     * @param originX left edge of the area, in the caller's coordinates
     * @param originY top edge of the area, in the caller's coordinates
     */
    public static void build(int w, int h, float phase, float originX, float originY, Buffers out) {
        out.reset();
        // Pitch from the canvas, so the cell count — and therefore the picture — is the same at any
        // resolution and at any GUI scale.  See PITCH_RATIO.
        int pitch = Math.max(6, Math.round(Math.min(w, h) * PITCH_RATIO));
        if (w < pitch * 4 || h < pitch * 4) {
            return;
        }

        Rng rng = new Rng(0x5DEECE66DL);
        int cols = Math.max(2, w / pitch - 1);
        int rows = Math.max(2, h / pitch - 1);

        for (int t = 0; t < TRACES; t++) {
            int count = 0;
            float x = originX + (1 + rng.range(cols)) * (float) pitch;
            float y = originY + (1 + rng.range(rows)) * (float) pitch;
            store(t, count, x, y);
            count++;

            boolean horizontal = rng.range(2) == 0;
            int steps = 3 + rng.range(3);
            for (int s = 0; s < steps && count < MAX_POINTS; s++) {
                int span = (1 + rng.range(4)) * pitch;
                float nx;
                float ny;
                if (rng.range(6) == 0) {
                    // occasional 45 degree jog, the way real boards escape a dense pad field
                    nx = x + (horizontal ? span : span * 0.5f);
                    ny = y + (horizontal ? span * 0.5f : span);
                } else {
                    nx = horizontal ? x + span : x;
                    ny = horizontal ? y : y + span;
                }
                out.line(x, y, nx, ny);
                out.via(x, y, pitch * VIA_HALF);
                x = nx;
                y = ny;
                store(t, count, x, y);
                count++;
                horizontal = !horizontal;
            }
            out.via(x, y, pitch * VIA_END_HALF);
            TRACE_LEN[t] = count;
            TRACE_OFFSET[t] = t * MAX_POINTS;
        }

        for (int p = 0; p < PULSES; p++) {
            int t = (int) (((phase * 0.11f + p * 0.37f) * TRACES) % TRACES);
            int len = TRACE_LEN[t];
            if (len < 2) {
                continue;
            }
            float u = (phase * 0.35f + p * 0.19f) % 1f;
            float scaled = u * (len - 1);
            int seg = Math.min(len - 2, (int) scaled);
            float f = scaled - seg;
            int o0 = TRACE_OFFSET[t] + seg;
            int o1 = o0 + 1;
            float x0 = TRACE_X[o0] + (TRACE_X[o1] - TRACE_X[o0]) * f;
            float y0 = TRACE_Y[o0] + (TRACE_Y[o1] - TRACE_Y[o0]) * f;
            out.pulse(x0, y0, TRACE_X[o1], TRACE_Y[o1]);
        }
    }

    private static void store(int trace, int index, float x, float y) {
        TRACE_X[trace * MAX_POINTS + index] = x;
        TRACE_Y[trace * MAX_POINTS + index] = y;
    }

    /** xorshift64; deterministic so the routing is stable between frames. */
    private static final class Rng {
        private long state;

        Rng(long seed) {
            this.state = seed == 0L ? 1L : seed;
        }

        long next() {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            return state;
        }

        int range(int bound) {
            return (int) ((next() >>> 33) % bound);
        }
    }
}
