package meowmel.quantumhue.menu;

import meowmel.quantumhue.modernsplash.SplashPainter;
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
 * Only the four primitives {@code Backdrop} needs are implemented.  The rest are the splash's
 * foreground vocabulary — sprites, glow, arcs, the transform stack, bitmap text — and are not
 * reachable from a background, so they fail loudly rather than silently drawing nothing.
 */
public final class GlStatePainter implements SplashPainter {

    /** Shared instance: the painter is stateless, the buffers live in the caller. */
    public static final GlStatePainter INSTANCE = new GlStatePainter();

    private GlStatePainter() {}

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

    // ---------------------------------------------------------------- unused

    private static UnsupportedOperationException unused(String method) {
        return new UnsupportedOperationException(
                "GlStatePainter only implements the background primitives; " + method
                        + "() is splash foreground and has no business on a menu screen");
    }

    @Override
    public void sprite(int texture, float x, float y, float w, float h,
                       float u0, float v0, float u1, float v1,
                       float r, float g, float b, float a) {
        throw unused("sprite");
    }

    @Override
    public void glow(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
        throw unused("glow");
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

    @Override
    public void text(String s, float x, float y, float scale, float r, float g, float b, float a) {
        throw unused("text");
    }

    @Override
    public float textWidth(String s, float scale) {
        throw unused("textWidth");
    }

    @Override
    public float textHeight(float scale) {
        throw unused("textHeight");
    }
}
