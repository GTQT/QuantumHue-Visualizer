package meowmel.quantumhue.modernsplash;

/**
 * The drawing surface the splash composition is written against.
 *
 * <p>Two implementations exist: {@link GlSplashPainter} draws with OpenGL inside the game, and
 * {@code tools/SplashPreview.java} draws with Java2D outside it.  Because the composition
 * ({@link SplashScene}) is written once against this interface, the offline preview cannot drift
 * away from what the game actually renders.
 *
 * <p>Everything here is deliberately free of Minecraft / LWJGL / AWT imports.
 *
 * <p>Coordinates match the splash ortho: {@code (320, 240)} is the screen centre and
 * <b>y grows downwards</b>.  Colours are straight (non-premultiplied) RGBA in 0..1.
 */
public interface SplashPainter {

    int TEX_LOGO = 0;

    /** Filled axis-aligned rectangle, {@code (x, y)} is the top-left corner. */
    void rect(float x, float y, float w, float h, float r, float g, float b, float a);

    /** Textured rectangle; {@code v0} samples the top edge, {@code v1} the bottom edge. */
    void sprite(int texture, float x, float y, float w, float h,
                float u0, float v0, float u1, float v1,
                float r, float g, float b, float a);

    /** Soft radial falloff centred on {@code (cx, cy)}; {@code segments} trades quality for speed. */
    void glow(float cx, float cy, float radius, float r, float g, float b, float a, int segments);

    /** Hairline segment. */
    void line(float x0, float y0, float x1, float y1, float r, float g, float b, float a, float width);

    /**
     * Batched line segments, used by the Dyson swarm.  {@code xy} holds {@code segmentCount}
     * consecutive {@code x0, y0, x1, y1} quadruples so a few hundred segments cost one draw call
     * instead of a few hundred.
     */
    void lines(float[] xy, int segmentCount, float r, float g, float b, float a, float width);

    /**
     * Batched convex quads, used by the Dyson swarm's collector panels.  {@code xy} holds
     * {@code quadCount} consecutive blocks of eight floats (four corners, clockwise or
     * counter-clockwise).
     */
    void quads(float[] xy, int quadCount, float r, float g, float b, float a);

    /** {@code true} switches to additive blending (energy, glows); {@code false} restores alpha. */
    void blendMode(boolean additive);

    /** Circular arc in degrees, 0 = +x, increasing clockwise on screen. */
    void arc(float cx, float cy, float radius, float fromDeg, float toDeg,
             float r, float g, float b, float a, float width);

    // ---- transform stack, used to spin / pop the gear around its optical centre
    void push();

    void pop();

    void translate(float x, float y);

    void rotate(float degrees);

    void scale(float sx, float sy);

    // ---- bitmap text

    /** Draws {@code s} with the surface's fixed-width bitmap font; {@code (x, y)} is the top-left. */
    void text(String s, float x, float y, float scale, float r, float g, float b, float a);

    /** Width of {@code s} in device pixels at {@code scale}. */
    float textWidth(String s, float scale);

    /** Line height of the bitmap font in device pixels at {@code scale}. */
    float textHeight(float scale);
}
