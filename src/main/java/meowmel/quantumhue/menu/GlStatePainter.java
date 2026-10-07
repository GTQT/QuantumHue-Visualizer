package meowmel.quantumhue.menu;

import meowmel.quantumhue.modernsplash.SplashPainter;
import meowmel.quantumhue.modernsplash.SplashTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * {@link SplashPainter} for the in-game menu screens, in plain GUI coordinates
 * ({@code (0, 0)} top-left, y growing downwards) — the space {@code EntityRenderer.setupOverlayRendering}
 * has already established, so this never touches the projection inside the GUI frame.
 *
 * <h3>Why not reuse {@code GlSplashPainter}</h3>
 * That one is written for the splash thread and calls raw {@code glEnable} / {@code glDisable} /
 * {@code glBlendFunc}.  Driving it from the GUI frame would desynchronise {@link GlStateManager}'s
 * cache: the cache would still believe {@code GL_TEXTURE_2D} is enabled after a raw
 * {@code glDisable}, so the next {@code enableTexture2D()} would be dropped as a no-op and every
 * subsequent {@code FontRenderer} draw would come out untextured.  Everything here therefore goes
 * through {@code GlStateManager}.
 *
 * <h3>Scope</h3>
 * The background primitives plus bitmap text — enough to draw a HUD through the same MC-free
 * composition the splash uses.  The rest are the splash's foreground vocabulary — sprites, glow,
 * arcs, the transform stack — and are not reachable from a menu or a loading HUD, so they fail
 * loudly rather than silently drawing nothing.
 */
public final class GlStatePainter implements SplashPainter {

    /** Line height of the vanilla bitmap font, before scaling. */
    private static final float FONT_HEIGHT = 9f;

    private static GlStatePainter cached;

    private final FontRenderer font;

    private GlStatePainter(FontRenderer font) {
        this.font = font;
    }

    /**
     * The painter for this client, bound to its font.
     *
     * <p>Cached because the painter is otherwise stateless while the callers are per-frame; it is
     * rebuilt only if the client's {@code FontRenderer} is ever replaced.
     */
    public static GlStatePainter get(Minecraft mc) {
        FontRenderer f = mc == null ? null : mc.fontRenderer;
        if (cached == null || cached.font != f) {
            cached = new GlStatePainter(f);
        }
        return cached;
    }

    // ---------------------------------------------------------------- used

    /**
     * Turns the alpha test off before every primitive.
     *
     * <p>Same reason as the splash backend's: {@code FontRenderer} enables the test at a threshold
     * of 0.1 and leaves it on, and every hairline dimmer than that is then silently discarded — the
     * background grid sits at 0.075.  Asserting it here rather than trusting the caller is what
     * makes the background render the same regardless of what drew before it.
     */
    private static void noAlphaTest() {
        GlStateManager.disableAlpha();
    }

    @Override
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (a <= 0.002f || w <= 0f || h <= 0f) {
            return;
        }
        noAlphaTest();
        blendMode(false);
        GlStateManager.disableTexture2D();
        GlStateManager.color(r, g, b, a);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buffer.pos(x, y + h, 0.0D).endVertex();
        buffer.pos(x + w, y + h, 0.0D).endVertex();
        buffer.pos(x + w, y, 0.0D).endVertex();
        buffer.pos(x, y, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float r, float g, float b, float a, float width) {
        if (a <= 0.002f) {
            return;
        }
        noAlphaTest();
        blendMode(false);
        GlStateManager.disableTexture2D();
        GlStateManager.color(r, g, b, a);
        GlStateManager.glLineWidth(Math.max(1f, width));
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        buffer.pos(x0, y0, 0.0D).endVertex();
        buffer.pos(x1, y1, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.glLineWidth(1f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void lines(float[] xy, int segmentCount, float r, float g, float b, float a, float width) {
        if (a <= 0.002f || segmentCount <= 0) {
            return;
        }
        // Gui.drawRect() ends with disableBlend(), so blending cannot be assumed to be on here.
        noAlphaTest();
        blendMode(false);
        GlStateManager.disableTexture2D();
        GlStateManager.color(r, g, b, a);
        GlStateManager.glLineWidth(Math.max(1f, width));
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        for (int i = 0; i < segmentCount; i++) {
            int o = i * 4;
            buffer.pos(xy[o], xy[o + 1], 0.0D).endVertex();
            buffer.pos(xy[o + 2], xy[o + 3], 0.0D).endVertex();
        }
        tessellator.draw();
        GlStateManager.glLineWidth(1f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void quads(float[] xy, int quadCount, float r, float g, float b, float a) {
        if (a <= 0.002f || quadCount <= 0) {
            return;
        }
        noAlphaTest();
        blendMode(false);
        GlStateManager.disableTexture2D();
        GlStateManager.color(r, g, b, a);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        for (int i = 0; i < quadCount; i++) {
            int o = i * 8;
            for (int corner = 0; corner < 4; corner++) {
                buffer.pos(xy[o + corner * 2], xy[o + corner * 2 + 1], 0.0D).endVertex();
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void blendMode(boolean additive) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                additive ? GlStateManager.DestFactor.ONE : GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    /**
     * Additive radial falloff, built as a triangle fan.
     *
     * <p>{@code GL_TRIANGLE_FAN} is a legitimate draw mode for the Tessellator — it is passed
     * straight through to {@code glDrawArrays} — which is what lets the same HUD glow the boot
     * splash uses also be reachable from a GUI frame without raw GL calls.
     */
    @Override
    public void glow(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
        if (a <= 0.002f || radius <= 0f) {
            return;
        }
        int n = Math.max(6, segments);
        noAlphaTest();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(cx, cy, 0.0D).color(r, g, b, a).endVertex();
        for (int i = 0; i <= n; i++) {
            double angle = i * 2.0 * Math.PI / n;
            buffer.pos(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius, 0.0D)
                    .color(r, g, b, 0f).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------- text

    @Override
    public void text(String s, float x, float y, float scale, float r, float g, float b, float a) {
        if (s == null || s.isEmpty() || a <= 0.004f) {
            return;
        }
        if (font == null) {
            throw new IllegalStateException("GlStatePainter has no FontRenderer; text is unavailable");
        }
        int ai = Math.round(SplashTimeline.clamp01(a) * 255f);
        int ri = Math.round(SplashTimeline.clamp01(r) * 255f);
        int gi = Math.round(SplashTimeline.clamp01(g) * 255f);
        int bi = Math.round(SplashTimeline.clamp01(b) * 255f);
        // FontRenderer only honours the alpha byte when one is actually set, so it is always set.
        int color = (ai << 24) | (ri << 16) | (gi << 8) | bi;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.enableTexture2D();
        font.drawString(s, 0, 0, color);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public float textWidth(String s, float scale) {
        return s == null || font == null ? 0f : font.getStringWidth(s) * scale;
    }

    @Override
    public float textHeight(float scale) {
        return FONT_HEIGHT * scale;
    }

    // ---------------------------------------------------------------- unused

    private static UnsupportedOperationException unused(String method) {
        return new UnsupportedOperationException(
                "GlStatePainter only implements the background primitives and bitmap text; " + method
                        + "() is splash foreground and has no business on a menu or loading screen");
    }

    @Override
    public void sprite(int texture, float x, float y, float w, float h,
                       float u0, float v0, float u1, float v1,
                       float r, float g, float b, float a) {
        throw unused("sprite");
    }

    @Override
    public void arc(float cx, float cy, float radius, float fromDeg, float toDeg,
                    float r, float g, float b, float a, float width) {
        throw unused("arc");
    }

    @Override
    public void push() {
        throw unused("push");
    }

    @Override
    public void pop() {
        throw unused("pop");
    }

    @Override
    public void translate(float x, float y) {
        throw unused("translate");
    }

    @Override
    public void rotate(float degrees) {
        throw unused("rotate");
    }

    @Override
    public void scale(float sx, float sy) {
        throw unused("scale");
    }
}
