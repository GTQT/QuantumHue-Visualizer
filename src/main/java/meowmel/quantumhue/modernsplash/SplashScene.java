package meowmel.quantumhue.modernsplash;

import java.util.Locale;

import static meowmel.quantumhue.modernsplash.SplashTheme.*;

/**
 * The whole splash composition: backdrop, animated emblem, Dyson swarm, wordmark, and the
 * loading HUD.
 *
 * <p>Written once against {@link SplashPainter}, so the in-game OpenGL renderer and the offline
 * preview tool draw the identical scene.  No Minecraft / LWJGL imports.
 *
 * <p>Screen space follows the splash ortho: centre {@code (320, 240)}, <b>y grows downwards</b>,
 * visible area {@code [320-w/2, 320+w/2] x [240-h/2, 240+h/2]}.
 */
public final class SplashScene {

    private SplashScene() {}

    /** One FML loading bar, flattened so the scene stays free of Forge types. */
    public static final class Bar {
        public String title = "";
        public String message = "";
        public int step;
        public int steps = 1;
    }

    /** Everything the scene needs for one frame. */
    public static final class State {
        public int width;
        public int height;
        /** Animation clock; never null. */
        public SplashTimeline.Frame intro;
        /** Loading HUD weight, 0..1. */
        public float uiAlpha;

        /** Stacked progress bars, top to bottom; null entries are skipped. */
        public Bar bar0;
        public Bar bar1;
        public Bar bar2;

        public boolean showMemory;
        public int memUsedMb;
        public int memMaxMb;

        public boolean showTimer;
        public float startupSeconds;
        public float estimateSeconds;
    }

    // ---------------------------------------------------------------- entry point

    public static void draw(SplashPainter p, State s) {
        float w = s.width;
        float h = s.height;
        float left = 320f - w * 0.5f;
        float right = 320f + w * 0.5f;
        float top = 240f - h * 0.5f;
        float bottom = 240f + h * 0.5f;
        float minDim = Math.min(w, h);
        SplashTimeline.Frame f = s.intro;

        // The shared background: wash + PCB board + drifting grid.  The Dyson swarm and everything
        // below are foreground and sit on top of it.
        Backdrop.paint(p, left, top, w, h, f.elapsed);

        // The emblem lives on z = 0, so the swarm splits cleanly into a back half and a front half
        // and the mark ends up genuinely enclosed rather than merely overlaid.
        heroSlot(minDim, HERO);
        steadySlot(minDim, STEADY);
        float sphereCx = SplashTimeline.lerp(HERO.cx, STEADY.cx, f.steady);
        float sphereCy = SplashTimeline.lerp(HERO.cy, STEADY.cy, f.steady);
        float sphereR = SplashTimeline.lerp(HERO.size, STEADY.size, f.steady) * SPHERE_RATIO;
        DysonSphere.build(f.elapsed, f.sphereReveal, f.sphereSpin, f.sphereTilt,
                sphereCx, sphereCy, sphereR, h, SPHERE);

        // ---- back of the swarm, behind the emblem
        p.blendMode(false);
        if (f.hero > 0.002f) {
            heroBloom(p, f, HERO, f.hero);
            reticle(p, HERO.cx, HERO.cy, HERO.size, f, f.hero);
        }
        if (f.steady > 0.002f) {
            p.glow(STEADY.cx, STEADY.cy, STEADY.size * 0.85f,
                    ACCENT[0], ACCENT[1], ACCENT[2], 0.10f * f.steady, 32);
        }
        sphereBack(p, f);

        // ---- the emblem itself.  sphereBack leaves additive blending on for its energy arcs, so
        // the mark has to reset to straight alpha first or it would be composited as light.
        p.blendMode(false);
        if (f.hero > 0.002f) {
            p.push();
            p.translate(HERO.cx, HERO.cy);
            p.scale(f.logoScale, f.logoScale);
            p.translate(-HERO.cx, -HERO.cy);
            spriteLogo(p, HERO, f.logoAlpha * f.hero);
            p.pop();
        }
        if (f.steady > 0.002f) {
            spriteLogo(p, STEADY, STEADY_LOGO_ALPHA * f.steady);
        }

        // ---- front of the swarm
        sphereFront(p, f);

        if (f.hero > 0.002f) {
            screenBrackets(p, left, right, top, bottom, f.brackets * f.hero);
        }

        if (s.uiAlpha > 0.002f) {
            hud(p, s, left, right, top, bottom);
        }
        if (f.hero > 0.002f) {
            scanSweep(p, left, right, top, bottom, f);
        }
        if (f.veil > 0.002f) {
            p.rect(left, top, w, h, 0f, 0f, 0f, f.veil);
        }
    }

    // ---------------------------------------------------------------- emblem slots
    // The two compositions the emblem appears in.  The swarm is anchored to whichever is current,
    // interpolated across the handoff so it never jumps.

    /** A resolved emblem quad. */
    private static final class Slot {
        float cx;
        float cy;
        float size;
    }

    private static final Slot HERO = new Slot();
    private static final Slot STEADY = new Slot();
    private static final DysonSphere.Buffers SPHERE = new DysonSphere.Buffers();

    /** Silhouette radius of the swarm, as a multiple of the emblem's quad size. */
    private static final float SPHERE_RATIO = 0.66f;

    /**
     * The hero shot is nothing but the emblem inside its swarm, so the emblem can take most of the
     * frame.  Its quad is 0.68 of the short side, which puts the swarm's silhouette at 0.45 of the
     * short side and leaves a comfortable margin at every aspect ratio we care about.
     */
    private static void heroSlot(float minDim, Slot out) {
        out.size = minDim * 0.68f;
        out.cx = 320f;
        out.cy = 240f;
    }

    /**
     * In the loading screen the HUD owns the top rail, the bottom rail and the bottom corners, so
     * the emblem sits above centre, scaled to stay clear of the progress block.
     */
    private static void steadySlot(float minDim, Slot out) {
        out.size = minDim * 0.34f;
        out.cx = 320f;
        out.cy = 240f - minDim * 0.16f;
    }

    private static void spriteLogo(SplashPainter p, Slot slot, float alpha) {
        p.sprite(SplashPainter.TEX_LOGO, slot.cx - slot.size * 0.5f, slot.cy - slot.size * 0.5f,
                slot.size, slot.size, 0f, 0f, 1f, 1f, 1f, 1f, 1f, alpha);
    }

    // ---------------------------------------------------------------- Dyson swarm

    private static void sphereBack(SplashPainter p, SplashTimeline.Frame f) {
        if (f.sphereReveal <= 0.002f) {
            return;
        }
        float a = f.sphereAlpha * f.sphereReveal;
        p.blendMode(false);
        p.quads(SPHERE.panelBack, SPHERE.panelBackCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], 0.11f * a);
        p.lines(SPHERE.structBack, SPHERE.structBackCount,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.14f * a, 1f);
        p.blendMode(true);
        p.lines(SPHERE.energyBack, SPHERE.energyBackCount,
                GOLD[0], GOLD[1], GOLD[2], 0.42f * a, 1.4f);
    }

    private static void sphereFront(SplashPainter p, SplashTimeline.Frame f) {
        if (f.sphereReveal <= 0.002f) {
            return;
        }
        float a = f.sphereAlpha * f.sphereReveal;
        p.blendMode(false);
        p.quads(SPHERE.panelFront, SPHERE.panelFrontCount,
                SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], 0.22f * a);
        p.blendMode(true);
        p.lines(SPHERE.energyFront, SPHERE.energyFrontCount,
                GOLD[0], GOLD[1], GOLD[2], 0.75f * a, 1.6f);
        p.lines(SPHERE.structFront, SPHERE.structFrontCount,
                ACCENT[0], ACCENT[1], ACCENT[2], 0.22f * a, 1f);
        p.blendMode(false);
    }

    // ---------------------------------------------------------------- hero (intro)

    private static void heroBloom(SplashPainter p, SplashTimeline.Frame f, Slot slot, float k) {
        if (f.bloom <= 0.01f) {
            return;
        }
        // single soft bloom behind the emblem — the only "glow" in the whole design
        p.glow(slot.cx, slot.cy, slot.size * 0.80f,
                ACCENT[0], ACCENT[1], ACCENT[2], f.bloom * 0.24f * k, 48);
        p.glow(slot.cx, slot.cy, slot.size * 0.34f,
                GOLD[0], GOLD[1], GOLD[2], f.bloom * 0.09f * k, 32);
    }

    // ---------------------------------------------------------------- backdrop
    // The background itself now lives in Backdrop, shared with the main menu and every other screen.
    // SplashScene only keeps what is drawn on top of it.

    /** A single bright band travelling down the screen, keeping the hold phase alive. */
    private static void scanSweep(SplashPainter p, float left, float right, float top, float bottom,
                                  SplashTimeline.Frame f) {
        float y = top + (bottom - top) * f.sweep;
        float a = f.hero * 0.9f;
        p.rect(left, y - 30f, right - left, 60f, ACCENT[0], ACCENT[1], ACCENT[2], 0.030f * a);
        p.line(left, y, right, y, ACCENT[0], ACCENT[1], ACCENT[2], 0.14f * a, 1f);
    }

    // ---------------------------------------------------------------- hero (intro)

    /**
     * Technical reticle around the emblem: four quadrant arcs growing outwards, a tick ring, and
     * a hairline traced exactly on the artwork's golden coil.
     */
    private static void reticle(SplashPainter p, float cx, float cy, float gear,
                                SplashTimeline.Frame f, float k) {
        float a = f.reticleAlpha * k;
        if (a <= 0.01f) {
            return;
        }
        float outer = gear * 0.635f;

        p.push();
        p.translate(cx, cy);
        p.rotate(f.reticleSpin);
        // four quadrant arcs, all growing from the same reveal value -> reads as one instrument
        for (int q = 0; q < 4; q++) {
            float base = q * 90f + 20f;
            p.arc(0f, 0f, outer, base, base + 50f * f.reticle,
                    ACCENT[0], ACCENT[1], ACCENT[2], a * (q % 2 == 0 ? 0.75f : 0.45f), 1.6f);
        }
        for (int q = 0; q < 4; q++) {
            float base = q * 90f;
            p.line((float) Math.cos(Math.toRadians(base)) * (outer + 6f),
                    (float) Math.sin(Math.toRadians(base)) * (outer + 6f),
                    (float) Math.cos(Math.toRadians(base)) * (outer + 18f) * f.reticle,
                    (float) Math.sin(Math.toRadians(base)) * (outer + 18f) * f.reticle,
                    ACCENT[0], ACCENT[1], ACCENT[2], a * 0.55f, 1f);
        }
        arcTickRing(p, outer - 12f, 96, a * f.reticle * 0.8f);
        p.pop();

        p.push();
        p.translate(cx, cy);
        p.rotate(-f.reticleSpin * 1.7f);
        p.arc(0f, 0f, gear * 0.505f, 18f, 18f + 190f * f.reticle, GOLD[0], GOLD[1], GOLD[2], a * 0.45f, 1.2f);
        p.arc(0f, 0f, gear * 0.505f, 198f, 198f + 190f * f.reticle, GOLD[0], GOLD[1], GOLD[2], a * 0.30f, 1.2f);
        p.pop();

        p.arc(cx, cy, gear * EMBLEM_COIL, 0f, 360f, GOLD[0], GOLD[1], GOLD[2], a * 0.18f, 1f);
    }

    private static void arcTickRing(SplashPainter p, float radius, int ticks, float alpha) {
        for (int i = 0; i < ticks; i++) {
            float ang = (float) Math.toRadians(i * 360.0 / ticks);
            boolean major = (i % 6) == 0;
            float len = (major ? 9f : 4f) * Math.max(0.2f, alpha);
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            p.line(ca * radius, sa * radius, ca * (radius + len), sa * (radius + len),
                    ACCENT[0], ACCENT[1], ACCENT[2], alpha * (major ? 0.55f : 0.26f), 1f);
        }
    }

    // ---------------------------------------------------------------- steady state

    // ---------------------------------------------------------------- loading HUD

    private static void hud(SplashPainter p, State s, float left, float right,
                            float top, float bottom) {
        float a = s.uiAlpha;
        float inset = Math.min(INSET, Math.min(s.width, s.height) * 0.085f);
        float railTop = top + RAIL_OFFSET;
        float railBottom = bottom - RAIL_OFFSET;

        p.line(left + inset, railTop, right - inset, railTop, LINE[0], LINE[1], LINE[2], 0.22f * a, 1f);
        p.line(left + inset, railBottom, right - inset, railBottom, LINE[0], LINE[1], LINE[2], 0.22f * a, 1f);
        screenBrackets(p, left, right, top, bottom, a);

        // header: brand on the left, launch timer on the right
        p.text(BRAND, left + inset, railTop - 24f, 2f, ACCENT[0], ACCENT[1], ACCENT[2], 0.95f * a);
        float brandW = p.textWidth(BRAND, 2f);
        p.text("// " + BOOT_LABEL, left + inset + brandW + 12f, railTop - 21f, 1.5f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.70f * a);

        if (s.showTimer) {
            String timer = timerText(s);
            p.text(timer, right - inset - p.textWidth(timer, 1.5f), railTop - 21f, 1.5f,
                    TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.85f * a);
        }

        progressBlock(p, s, left + inset, railBottom, inset);

        if (s.showMemory) {
            memoryBlock(p, s, right - inset, railBottom, inset);
        }
    }

    private static String timerText(State s) {
        String t = "T+" + clock(s.startupSeconds);
        if (s.estimateSeconds > 0.5f) {
            t += "  ETA " + clock(s.estimateSeconds);
        }
        return t;
    }

    private static String clock(float seconds) {
        int total = (int) Math.max(0f, seconds);
        int m = total / 60;
        int sec = total % 60;
        return String.format(Locale.ROOT, "%02d:%02d", m, sec);
    }

    private static void progressBlock(SplashPainter p, State s, float x, float railBottom, float inset) {
        float a = s.uiAlpha;
        float barW = Math.min(BAR_BLOCK, s.width * 0.36f);
        float top = railBottom - BAR_ROW * 3f;

        Bar[] bars = {s.bar0, s.bar1, s.bar2};
        int row = 0;
        for (Bar b : bars) {
            if (b == null) {
                continue;
            }
            float y = top + row * BAR_ROW;

            String label = clip(p, b.title.toUpperCase(Locale.ROOT), barW * 0.62f, 1.5f);
            p.text(label, x, y, 1.5f, TEXT[0], TEXT[1], TEXT[2], 0.80f * a);

            String count = b.step + " / " + b.steps;
            p.text(count, x + barW - p.textWidth(count, 1.5f), y, 1.5f,
                    TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.85f * a);

            String msg = clip(p, b.message, barW, 1.35f);
            p.text(msg, x, y + 17f, 1.35f, TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.70f * a);

            float trackY = y + 36f;
            p.rect(x, trackY, barW, BAR_TRACK, LINE[0], LINE[1], LINE[2], 0.13f * a);
            float frac = SplashTimeline.clamp01((b.step + 1f) / (b.steps + 1f));
            float fill = barW * frac;
            if (fill > 0.5f) {
                p.rect(x, trackY, fill, BAR_TRACK, ACCENT[0], ACCENT[1], ACCENT[2], 0.85f * a);
                p.glow(x + fill, trackY + BAR_TRACK * 0.5f, 14f,
                        GOLD[0], GOLD[1], GOLD[2], 0.60f * a, 16);
            }
            row++;
        }
    }

    private static void memoryBlock(SplashPainter p, State s, float right, float railBottom, float inset) {
        float a = s.uiAlpha;
        float blockW = Math.min(MEM_BLOCK, s.width * 0.24f);
        float x = right - blockW;
        float top = railBottom - BAR_ROW * 3f;

        p.text(MEM_LABEL, x, top, 1.5f, TEXT[0], TEXT[1], TEXT[2], 0.80f * a);

        int max = Math.max(1, s.memMaxMb);
        int used = Math.max(0, Math.min(s.memUsedMb, max));
        String value = used + " / " + max + " MB";
        p.text(value, right - p.textWidth(value, 1.5f), top, 1.5f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.85f * a);

        float frac = used / (float) max;
        p.rect(x, top + 36f, blockW, BAR_TRACK, LINE[0], LINE[1], LINE[2], 0.13f * a);

        float[] color = frac < 0.75f ? ACCENT : (frac < 0.88f ? GOLD : new float[] {0.90f, 0.28f, 0.32f});
        p.rect(x, top + 36f, blockW * frac, BAR_TRACK, color[0], color[1], color[2], 0.85f * a);
        p.glow(x + blockW * frac, top + 36f + BAR_TRACK * 0.5f, 14f,
                color[0], color[1], color[2], 0.55f * a, 16);

        String pct = Math.round(frac * 100f) + "%";
        p.text(pct, right - p.textWidth(pct, 1.5f), top + 52f, 1.5f,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], 0.70f * a);
    }

    // ---------------------------------------------------------------- chrome

    private static void screenBrackets(SplashPainter p, float left, float right,
                                       float top, float bottom, float k) {
        if (k <= 0.01f) {
            return;
        }
        float inset = 22f;
        float len = BRACKET * SplashTimeline.clamp01(k);
        float a = 0.34f * SplashTimeline.clamp01(k);
        bracket(p, left + inset, top + inset, len, ACCENT, a);
        bracket(p, right - inset, top + inset, -len, ACCENT, a);
        bracket(p, left + inset, bottom - inset, len, ACCENT, a);
        bracket(p, right - inset, bottom - inset, -len, ACCENT, a);
    }

    /** L-shaped corner mark; {@code dx} flips it horizontally. */
    private static void bracket(SplashPainter p, float x, float y, float dx, float[] color, float a) {
        p.line(x, y, x + dx, y, color[0], color[1], color[2], a, 1.4f);
        p.line(x, y, x, y + Math.abs(dx), color[0], color[1], color[2], a, 1.4f);
    }

    private static String clip(SplashPainter p, String text, float maxWidth, float scale) {
        if (text == null) {
            return "";
        }
        if (p.textWidth(text, scale) <= maxWidth) {
            return text;
        }
        int n = text.length();
        while (n > 1 && p.textWidth(text.substring(0, n) + "...", scale) > maxWidth) {
            n--;
        }
        return text.substring(0, n) + "...";
    }
}
