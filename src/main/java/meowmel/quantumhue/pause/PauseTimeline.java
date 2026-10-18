package meowmel.quantumhue.pause;

/**
 * The pause menu's open and close choreography, expressed as a function of time.
 *
 * <p>Two clocks: {@code opening} counts up from the moment the screen appeared, {@code exit} counts
 * up from the moment it was told to close ({@code -1} until then).  Every visual property is derived
 * from those two numbers, so the whole animation is a pure function of time and can be sampled by
 * the offline preview at any instant.
 *
 * <h3>The order that gives it its feel</h3>
 * <ol>
 *   <li>the veil fades in almost at once;</li>
 *   <li>the disc grows from 2% and slams flat — it is the only thing on screen, tilting 40 degrees
 *       back to level;</li>
 *   <li>the cards fly in from 36 units to the left, staggered 45 ms apart;</li>
 *   <li>the tiles fade in along the arc, 25 ms apart;</li>
 *   <li>the profile badge pops after a deliberate gap — a beat of rest, then the readouts;</li>
 *   <li>the controls (resume, close) come last.</li>
 * </ol>
 * Closing reverses it in ~280 ms: the veil holds for 380 units, then everything leaves together.
 *
 * <p>Times are in the same "units" as the source: milliseconds times {@code SPEED}.  They are not
 * milliseconds because the whole sequence was tuned against one global rate; changing
 * {@link #SPEED} speeds up or slows down every stage in proportion.
 */
public final class PauseTimeline {

    /** Global rate.  Times below are {@code ms * SPEED}. */
    public static final float SPEED = 1.2f;

    /** Once closing, the screen stays up this long before the action actually fires. */
    private static final long SETTLE_MS = 500L;

    private final long openedAt;
    private final boolean reducedMotion;
    private long closedAt = -1L;

    public PauseTimeline(long nowNanos, boolean reducedMotion) {
        this.openedAt = nowNanos;
        this.reducedMotion = reducedMotion;
    }

    public void close(long nowNanos) {
        if (!closing()) {
            closedAt = nowNanos;
        }
    }

    public boolean closing() {
        return closedAt >= 0L;
    }

    public boolean finished(long nowNanos) {
        return closing() && (reducedMotion || elapsed(nowNanos, closedAt) >= SETTLE_MS);
    }

    public Frame sample(long nowNanos) {
        float opening = reducedMotion ? 900f : elapsed(closing() ? closedAt : nowNanos, openedAt);
        float exit = !closing() ? -1f : reducedMotion ? SETTLE_MS : elapsed(nowNanos, closedAt);
        return new Frame(opening, exit);
    }

    private static float elapsed(long now, long origin) {
        return (float) ((now - origin) / 1_000_000d) * SPEED;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float range(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1f : 0f;
        }
        return clamp01((value - start) / (end - start));
    }

    public static float easeOut(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeInOut(float t) {
        float v = clamp01(t);
        return v * v * (3f - 2f * v);
    }

    /** One frame's worth of animation values. */
    public static final class Frame {

        public final float opening;
        public final float exit;

        Frame(float opening, float exit) {
            this.opening = opening;
            this.exit = exit;
        }

        private boolean open() {
            return exit < 0f;
        }

        /** Veil over the world; fades in immediately and holds the screen until the very end. */
        public float backdrop() {
            return open() ? 1f : 1f - range(exit, 380f, 500f);
        }

        public float discScale() {
            float grow = 0.02f + 0.98f * easeOut(range(opening, 58f, 558f));
            return open() ? grow : grow * (1f - 0.98f * easeInOut(range(exit, 150f, 340f)));
        }

        /** Degrees; 40 while opening, 51.5 while closing, 0 at rest. */
        public float discTilt() {
            if (open()) {
                return 40f * (1f - easeInOut(range(opening, 234f, 382f)));
            }
            return 51.5f * easeInOut(range(exit, 150f, 340f));
        }

        public float discAlpha() {
            return range(opening, 58f, 115f) * (open() ? 1f : 1f - range(exit, 290f, 350f));
        }

        public float contentAlpha() {
            return range(opening, 355f, 510f) * (open() ? 1f : 1f - range(exit, 175f, 285f));
        }

        public float cardAlpha(int order) {
            float start = order <= 0 ? 340f : order == 1 ? 355f : order == 2 ? 457f : 505f;
            float in = easeOut(range(opening, start, start + 150f));
            return in * (open() ? 1f : 1f - range(exit, 50f, 150f + Math.min(order, 2) * 50f));
        }

        /** Horizontal offset in board units: the cards start 36 units to the left. */
        public float cardSlide(int order) {
            if (open()) {
                return (1f - easeOut(range(opening, 340f + order * 45f, 890f))) * 36f;
            }
            return -80f * easeOut(range(exit, 50f, 220f));
        }

        public float tileAlpha(int order) {
            int stagger = Math.min(order, 8) * 25;
            float in = range(opening, 370f + stagger, 470f + stagger);
            return in * (open() ? 1f
                    : 1f - range(exit, Math.max(35f, 140f - order * 18f), 250f - Math.min(order, 8) * 15f));
        }

        /** The profile badge: pops, blinks out for one frame of rest, then comes back. */
        public float badgeAlpha() {
            float alpha = opening < 625f ? range(opening, 565f, 605f)
                    : opening < 670f ? 0f : range(opening, 670f, 710f);
            return alpha * (open() ? 1f : 1f - range(exit, 75f, 150f));
        }

        public float controlsAlpha() {
            return range(opening, 450f, 620f) * (open() ? 1f : 1f - range(exit, 60f, 160f));
        }

        /**
         * 0..1 as the readouts spin up.
         *
         * <p>Every number on the board is multiplied by this while the screen opens, so the
         * instruments appear to self-test: the values run up from zero and settle on the truth at
         * the same instant the controls finish arriving.  It never runs backwards — on the way out
         * the numbers are already correct and there is nothing to unwind.
         */
        public float countUp() {
            return open() ? easeOut(range(opening, 380f, 660f)) : 1f;
        }
    }
}
