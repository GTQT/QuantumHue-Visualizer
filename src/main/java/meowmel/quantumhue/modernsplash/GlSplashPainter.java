package meowmel.quantumhue.modernsplash;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * OpenGL implementation of {@link SplashPainter}.
 *
 * <p>Runs on the splash render thread with the shared drawable current, which is the same
 * environment {@code CustomSplash}'s own texture loader works in.  Two details differ from the
 * texture loader inherited from Forge's SplashProgress:
 *
 * <ul>
 *   <li>These textures are authored power-of-two already, so no padding (and no 4096x4096
 *       allocation) is needed.</li>
 *   <li>{@code GL_LINEAR} + mipmaps instead of {@code GL_NEAREST}, which matters because the
 *       emblem is drawn down to a third of its authored size as a watermark and the glyph strips
 *       shrink further still.</li>
 * </ul>
 */
public final class GlSplashPainter implements SplashPainter {

    private static final String[] LOCATIONS = {
            "quantumhue:textures/gui/title/gtqt_logo.png",
    };

    /** Line height of the vanilla bitmap font, before scaling. */
    private static final float FONT_HEIGHT = 9f;

    private final FontRenderer font;
    private final int[] ids = new int[3];
    private final int[] widths = new int[3];
    private final int[] heights = new int[3];
    private boolean loaded;
    private boolean attempted;

    public GlSplashPainter(FontRenderer font) {
        this.font = font;
    }

    // ---------------------------------------------------------------- resources

    /**
     * Uploads the three textures.  Attempted exactly once: retrying every frame would spam the log
     * and stall the render loop, and the scene draws fine (minus the bitmaps) without them.
     */
    public boolean load() {
        if (attempted) {
            return loaded;
        }
        attempted = true;
        boolean ok = true;
        for (int i = 0; i < LOCATIONS.length; i++) {
            ids[i] = upload(LOCATIONS[i], i);
            ok &= ids[i] != 0;
        }
        loaded = ok;
        if (!ok) {
            CustomSplash.LOGGER.error("[QuantumHue] one or more splash textures are missing; "
                    + "the loading HUD will render without them");
        }
        return loaded;
    }

    public void dispose() {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != 0) {
                glDeleteTextures(ids[i]);
                ids[i] = 0;
            }
        }
        loaded = false;
        attempted = false;
    }

    private int upload(String location, int slot) {
        InputStream stream = null;
        try {
            stream = CustomSplash.open(new ResourceLocation(location), null, true);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("not a readable image");
            }
            int w = image.getWidth();
            int h = image.getHeight();
            if (!isPowerOfTwo(w) || !isPowerOfTwo(h)) {
                throw new IOException("expected power-of-two, got " + w + "x" + h);
            }

            ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                image.getRGB(0, y, w, 1, row, 0, w);
                for (int x = 0; x < w; x++) {
                    int argb = row[x];
                    buffer.put((byte) ((argb >> 16) & 0xFF));
                    buffer.put((byte) ((argb >> 8) & 0xFF));
                    buffer.put((byte) (argb & 0xFF));
                    buffer.put((byte) ((argb >>> 24) & 0xFF));
                }
            }
            buffer.flip();

            widths[slot] = w;
            heights[slot] = h;
            return createTexture(location, w, h, buffer);
        } catch (IOException e) {
            CustomSplash.LOGGER.error("[QuantumHue] splash texture {} could not be loaded", location, e);
            return 0;
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private static int createTexture(String location, int w, int h, ByteBuffer data) {
        glEnable(GL_TEXTURE_2D);
        int name;
        // The loading thread may create textures concurrently; the existing splash loader
        // synchronises on CustomSplash.class for the same reason.
        synchronized (CustomSplash.class) {
            name = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, name);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        boolean mipmapped = false;
        try {
            data.position(0);
            GLU.gluBuild2DMipmaps(GL_TEXTURE_2D, GL_RGBA, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data);
            mipmapped = true;
        } catch (Throwable t) {
            CustomSplash.LOGGER.warn("[QuantumHue] mipmaps unavailable for {}, using linear filtering", location, t);
        }
        if (!mipmapped) {
            data.position(0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                mipmapped ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);

        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
        CustomSplash.checkGLError("splash texture " + location);
        return name;
    }

    private static boolean isPowerOfTwo(int v) {
        return v > 0 && (v & (v - 1)) == 0;
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Turns the alpha test off before every primitive.
     *
     * <p>Not optional, and not the caller's job.  {@code CustomSplash} never touches
     * {@code GL_ALPHA_TEST}, so it starts off on the splash thread and everything below the
     * threshold survives — until the loading HUD draws its first string.  {@code FontRenderer}
     * enables the test at 0.1 and leaves it on, and from that frame onward every hairline dimmer
     * than 0.1 is silently discarded: the whole background grid at 0.075, the progress-bar tracks
     * at 0.13, the tick marks.  That is exactly the "the grid disappears once loading gets going"
     * report, and it is why this cannot be left to the call site.
     */
    private static void noAlphaTest() {
        glDisable(GL_ALPHA_TEST);
    }

    @Override
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (a <= 0.002f || w <= 0f || h <= 0f) {
            return;
        }
        noAlphaTest();
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x, y + h);
        glVertex2f(x + w, y + h);
        glVertex2f(x + w, y);
        glEnd();
    }

    @Override
    public void sprite(int texture, float x, float y, float w, float h,
                       float u0, float v0, float u1, float v1,
                       float r, float g, float b, float a) {
        if (a <= 0.002f || texture < 0 || texture >= ids.length || ids[texture] == 0) {
            return;
        }
        noAlphaTest();
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, ids[texture]);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0);
        glVertex2f(x, y);
        glTexCoord2f(u0, v1);
        glVertex2f(x, y + h);
        glTexCoord2f(u1, v1);
        glVertex2f(x + w, y + h);
        glTexCoord2f(u1, v0);
        glVertex2f(x + w, y);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    @Override
    public void glow(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
        if (a <= 0.002f || radius <= 0f) {
            return;
        }
        noAlphaTest();
        int n = Math.max(6, segments);
        // Additive so overlapping glows accumulate into a bloom instead of flattening out.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_TRIANGLE_FAN);
        glColor4f(r, g, b, a);
        glVertex2f(cx, cy);
        glColor4f(r, g, b, 0f);
        for (int i = 0; i <= n; i++) {
            double angle = i * 2.0 * Math.PI / n;
            glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        glEnd();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float r, float g, float b, float a, float width) {
        if (a <= 0.002f) {
            return;
        }
        noAlphaTest();
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glLineWidth(Math.max(1f, width));
        glBegin(GL_LINES);
        glVertex2f(x0, y0);
        glVertex2f(x1, y1);
        glEnd();
        glLineWidth(1f);
    }

    @Override
    public void lines(float[] xy, int segmentCount, float r, float g, float b, float a, float width) {
        if (a <= 0.002f || segmentCount <= 0) {
            return;
        }
        noAlphaTest();
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glLineWidth(Math.max(1f, width));
        glBegin(GL_LINES);
        for (int i = 0; i < segmentCount; i++) {
            int o = i * 4;
            glVertex2f(xy[o], xy[o + 1]);
            glVertex2f(xy[o + 2], xy[o + 3]);
        }
        glEnd();
        glLineWidth(1f);
    }

    @Override
    public void quads(float[] xy, int quadCount, float r, float g, float b, float a) {
        if (a <= 0.002f || quadCount <= 0) {
            return;
        }
        noAlphaTest();
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        for (int i = 0; i < quadCount; i++) {
            int o = i * 8;
            for (int c = 0; c < 4; c++) {
                glVertex2f(xy[o + c * 2], xy[o + c * 2 + 1]);
            }
        }
        glEnd();
    }

    @Override
    public void blendMode(boolean additive) {
        // Additive accumulation is what makes the swarm's energy arcs read as light rather than
        // as a painted line; everything else stays on straight alpha over the backdrop.
        if (additive) {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        } else {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override
    public void arc(float cx, float cy, float radius, float fromDeg, float toDeg,
                    float r, float g, float b, float a, float width) {
        if (a <= 0.002f || radius <= 0f) {
            return;
        }
        float span = toDeg - fromDeg;
        if (Math.abs(span) < 0.5f) {
            return;
        }
        noAlphaTest();
        int steps = Math.max(6, (int) (Math.abs(span) / 3f));
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glLineWidth(Math.max(1f, width));
        glBegin(GL_LINE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDeg + span * i / steps);
            glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        glEnd();
        glLineWidth(1f);
    }

    // ---------------------------------------------------------------- transform

    @Override
    public void push() {
        glPushMatrix();
    }

    @Override
    public void pop() {
        glPopMatrix();
    }

    @Override
    public void translate(float x, float y) {
        glTranslatef(x, y, 0f);
    }

    @Override
    public void rotate(float degrees) {
        glRotatef(degrees, 0f, 0f, 1f);
    }

    @Override
    public void scale(float sx, float sy) {
        glScalef(sx, sy, 1f);
    }

    // ---------------------------------------------------------------- text

    @Override
    public void text(String s, float x, float y, float scale, float r, float g, float b, float a) {
        if (s == null || s.isEmpty() || a <= 0.004f) {
            return;
        }
        int ai = Math.round(SplashTimeline.clamp01(a) * 255f);
        int ri = Math.round(SplashTimeline.clamp01(r) * 255f);
        int gi = Math.round(SplashTimeline.clamp01(g) * 255f);
        int bi = Math.round(SplashTimeline.clamp01(b) * 255f);
        // FontRenderer only honours the alpha byte when one is actually set, so it is always set.
        int color = (ai << 24) | (ri << 16) | (gi << 8) | bi;

        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale, scale, 1f);
        glEnable(GL_TEXTURE_2D);
        font.drawString(s, 0, 0, color);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
    }

    @Override
    public float textWidth(String s, float scale) {
        return s == null ? 0f : font.getStringWidth(s) * scale;
    }

    @Override
    public float textHeight(float scale) {
        return FONT_HEIGHT * scale;
    }
}
