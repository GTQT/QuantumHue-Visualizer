package meowmel.quantumhue.modernsplash;

/**
 * Animation clock for the splash.  Pure maths, no Minecraft / LWJGL imports, so the offline
 * preview replays exactly the same numbers.
 *
 * <p>The beat is deliberately simple: <b>the emblem pops in</b>, a reticle closes around it, the
 * wordmark snaps in, and the whole thing keeps breathing until the loading HUD takes over.  The
 * nominal 18s runtime is spent mostly in the live hold, which is what keeps it from looking like
 * a still image.
 */
public final class SplashTimeline {

    /** Runtime the phase table below is authored against; the config can stretch it. */
    public static final float NOMINAL_DURATION = 18.0f;

    // ---- phase table, seconds against the nominal 18s runtime
    private static final float VEIL_END = 0.75f;

    private static final float POP_START = 0.35f;
    private static final float POP_END = 1.55f;
    private static final float POP_FADE = 0.60f;
    private static final float FLASH_START = 1.28f;
    private static final float FLASH_END = 1.95f;

    private static final float RETICLE_START = 0.90f;
    private static final float RETICLE_END = 2.60f;

    private static final float BRACKET_START = 2.70f;
    private static final float BRACKET_END = 3.90f;

    /** The Dyson swarm assembles alongside the emblem pop. */
    private static final float SPHERE_START = 0.45f;
    private static final float SPHERE_END = 3.30f;

    // The hero must be gone before the steady composition appears, otherwise both the emblem and
    // the wordmark are on screen at partial alpha and read as a double image.  The 0.2s overlap
    // below happens where both curves are already within a few thousandths of zero.
    private static final float HERO_OUT_START = 13.40f;
    private static final float HERO_OUT_END = 15.50f;
    private static final float STEADY_IN_START = 15.30f;
    private static final float STEADY_IN_END = 17.30f;
    private static final float UI_IN_START = 15.40f;
    private static final float UI_IN_END = 17.60f;

    /** Stateless apart from the reused frame buffer. */
    public SplashTimeline() {}

    // ---------------------------------------------------------------- easing

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Smooth 0..1 ramp; 0 outside [edge0, edge1]. */
    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x < edge0 ? 0f : 1f;
        }
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    /** 0 at both ends, 1 in the middle. */
    public static float bump(float t) {
        return (float) Math.sin(clamp01(t) * Math.PI);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    /** Ease with overshoot — this is the "pop".  {@code overshoot} 0 degenerates to ease-out. */
    public static float easeOutBack(float t, float overshoot) {
        float c = clamp01(t);
        float c1 = 1.70158f + overshoot * 1.35f;
        float c3 = c1 + 1f;
        float u = c - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    // ---------------------------------------------------------------- frame

    /** One evaluated frame; the instance is reused, so read it before asking for the next one. */
    public static final class Frame {
        public float elapsed;
        public float duration;

        /** 1 = fully black overlay (lift off black), 0 = clear. */
        public float veil;
        /** Hero composition weight, 1 -> 0 as the loading HUD takes over. */
        public float hero;
        /** Steady composition weight, 0 -> 1. */
        public float steady;
        /** Loading HUD weight, 0 -> 1. */
        public float ui;

        /** Emblem transform.  Scale only — the mark is never rotated. */
        public float logoAlpha;
        public float logoScale;
        /** Emblem bloom strength. */
        public float bloom;
        /** 0..1 additive white hit at the moment the emblem locks in. */
        public float flash;

        /** 0..1 how far the reticle arcs have swept. */
        public float reticle;
        /** Reticle opacity. */
        public float reticleAlpha;
        /** 0..1 corner-bracket draw-in. */
        public float brackets;
        /** Continuous rotation of the reticle tick ring, in degrees. */
        public float reticleSpin;
        /** 0..1 vertical position of the scan sweep. */
        public float sweep;

        /** 0..1 assembly progress of the Dyson swarm around the emblem. */
        public float sphereReveal;
        /** Continuous rotation of the swarm about the world Y axis, in degrees. */
        public float sphereSpin;
        /** Tilt of the swarm about the world X axis, in degrees. */
        public float sphereTilt;
        /** Overall opacity of the swarm (dimmer once it is only a watermark). */
        public float sphereAlpha;

        public boolean finished;
    }

    public Frame frame(float elapsed) {
        return frame(elapsed, NOMINAL_DURATION);
    }

    /** Reused so the render loop does not allocate once per frame. */
    private final Frame scratch = new Frame();

    public Frame frame(float elapsed, float duration) {
        Frame f = scratch;
        float d = duration > 1f ? duration : 1f;
        float k = d / NOMINAL_DURATION;
        float t = elapsed < 0f ? 0f : elapsed;

        f.elapsed = t;
        f.duration = d;
        f.finished = t >= d;

        f.veil = 1f - smoothstep(0f, VEIL_END * k, t);

        // the pop: scale 0.70 -> 1.00 with a mechanical overshoot, plus a short fade
        float pop = clamp01((t - POP_START * k) / ((POP_END - POP_START) * k));
        f.logoAlpha = smoothstep(0f, POP_FADE * k, t - POP_START * k);
        f.logoScale = lerp(0.70f, 1f, easeOutBack(pop, 0.9f));
        // The emblem never rotates: it is the product mark, and a spinning mark reads as decoration
        // rather than as a logo.  Life comes from the reticle, the bloom and the scan band instead.
        f.flash = bump(clamp01((t - FLASH_START * k) / ((FLASH_END - FLASH_START) * k))) * 0.40f;

        f.reticle = easeOutCubic((t - RETICLE_START * k) / ((RETICLE_END - RETICLE_START) * k));
        f.reticleAlpha = smoothstep(RETICLE_START * k, (RETICLE_START + 0.5f) * k, t);
        f.reticleSpin = t * 9f;
        f.brackets = easeOutCubic((t - BRACKET_START * k) / ((BRACKET_END - BRACKET_START) * k));

        f.hero = 1f - smoothstep(HERO_OUT_START * k, HERO_OUT_END * k, t);
        f.steady = smoothstep(STEADY_IN_START * k, STEADY_IN_END * k, t);
        f.ui = smoothstep(UI_IN_START * k, UI_IN_END * k, t);

        // live motion: bloom breathing and a scan sweep every ~6s.  The grid's drift is derived from
        // the clock inside Backdrop, where it is shared with the main menu.
        float breathe = 0.82f + 0.18f * (float) Math.sin(t * 0.85f);
        f.bloom = f.reticleAlpha * breathe;
        f.sweep = (t / 6.0f) % 1f;

        // the Dyson swarm assembles with the emblem, then never stops turning -- it is structure,
        // not branding, so unlike the mark it is free to rotate
        f.sphereReveal = smoothstep(SPHERE_START * k, SPHERE_END * k, t);
        f.sphereSpin = t * 7.5f;
        f.sphereTilt = 21f + 6f * (float) Math.sin(t * 0.13f);
        // The swarm keeps full strength through the handoff: it does not overlap the loading HUD,
        // so fading it there only made the emblem look weak.
        f.sphereAlpha = 1f;

        return f;
    }
}
