import meowmel.quantumhue.modernsplash.Backdrop;
import meowmel.quantumhue.modernsplash.DysonSphere;
import meowmel.quantumhue.modernsplash.SplashTheme;
import meowmel.quantumhue.menu.MenuLayout;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the title screen outside Minecraft, using the exact geometry from {@link MenuLayout} and
 * the exact swarm from {@link DysonSphere} that the game uses.
 *
 * <p>Its job is collision and composition checking: the sphere is anchored to the left edge and
 * half off-screen, so this is how the overlap between the swarm, the instrument column and the
 * brand lockup is verified at several window sizes.
 *
 * <pre>
 *   javac -encoding UTF-8 -d build/preview \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTheme.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTimeline.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashPainter.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/Backdrop.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/DysonSphere.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/PcbTraces.java \
 *       src/main/java/meowmel/quantumhue/menu/MenuLayout.java \
 *       tools/SplashPreview.java tools/MenuPreview.java
 *   java -cp build/preview MenuPreview
 * </pre>
 *
 * <p>The background is not drawn here: it is {@link Backdrop}, through {@code SplashPreview}'s AWT
 * painter, so this sheet cannot show a background the game does not render.
 */
public final class MenuPreview {

    private static final String TITLE_DIR = "src/main/resources/assets/quantumhue/textures/gui/title/";
    private static final String OUT = "build/preview-out/menu";

    /** Fixed clock so repeated runs produce identical images; override with the first CLI arg. */
    private static float TIME = 12.0f;

    private static final float[] ACCENT = SplashTheme.ACCENT;
    private static final float[] LINE = SplashTheme.LINE;
    private static final float[] GOLD = SplashTheme.GOLD;
    private static final float[] TEXT = SplashTheme.TEXT;
    private static final float[] TEXT_DIM = SplashTheme.TEXT_DIM;
    private static final float[] SHELL_PANEL = SplashTheme.SHELL_PANEL;

    private static final int[][] BUTTONS = {{1, 0}, {2, 1}, {6, 2}, {14, 3}, {0, 4}, {4, 5}, {5, 6}};
    private static final String[] LABELS = {
            "Singleplayer", "Multiplayer", "Mods", "Minecraft Realms", "Options...", "Quit Game", ""
    };
    private static final String[] CODES = {"01", "02", "03", "04", "05", "06", null};

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            TIME = Float.parseFloat(args[0]);
        }
        new File(OUT).mkdirs();
        BufferedImage emblem = ImageIO.read(new File(TITLE_DIR + "gtqt_logo.png"));

        int[][] sizes = {{1280, 720}, {1920, 1080}, {854, 480}, {2560, 1440}};
        BufferedImage[] shots = new BufferedImage[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            shots[i] = render(sizes[i][0], sizes[i][1], emblem);
            ImageIO.write(shots[i], "png", new File(OUT, sizes[i][0] + "x" + sizes[i][1] + ".png"));
        }
        contactSheet(shots, sizes, OUT + "/contact-sheet.png");
        System.out.println("menu preview written to " + OUT);
    }

    private static BufferedImage render(int w, int h, BufferedImage emblem) throws Exception {
        // The background is not re-implemented here: it is Backdrop, the same three layers the game
        // paints, drawn through the same AWT SplashPainter the splash preview uses.  A background
        // change can therefore never show up in the game without showing up here.
        SplashPreview.AwtPainter painter = new SplashPreview.AwtPainter(w, h);
        painter.begin();
        Backdrop.paint(painter, 320f - w * 0.5f, 240f - h * 0.5f, w, h, TIME);

        Graphics2D gc = painter.detach();
        gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 4. Dyson sphere, half off the left edge
        DysonSphere.Buffers buffers = new DysonSphere.Buffers();
        DysonSphere.build(TIME, 1f, TIME * 7.5f, 21f + 6f * (float) Math.sin(TIME * 0.13f),
                MenuLayout.sphereCx(), MenuLayout.sphereCy(h), MenuLayout.sphereRadius(h), h, buffers);

        quads(gc, buffers.panelBack, buffers.panelBackCount, SHELL_PANEL, SplashTheme.Layer.SWARM_BACK);
        lines(gc, buffers.structBack, buffers.structBackCount, ACCENT, SplashTheme.Layer.RING_BACK, 1f);
        lines(gc, buffers.energyBack, buffers.energyBackCount, GOLD, SplashTheme.Layer.ARC_BACK, 1.4f);
        quads(gc, buffers.panelFront, buffers.panelFrontCount, SHELL_PANEL, SplashTheme.Layer.SWARM_FRONT);
        lines(gc, buffers.energyFront, buffers.energyFrontCount, GOLD, SplashTheme.Layer.ARC_FRONT, 1.6f);
        lines(gc, buffers.structFront, buffers.structFrontCount, ACCENT, SplashTheme.Layer.RING_FRONT, 1f);

        // 5. cradle
        gc.setComposite(AlphaComposite.SrcOver);
        int inset = MenuLayout.INSET;
        gc.setColor(rgba(LINE, SplashTheme.Layer.RAIL));
        gc.fillRect(inset, inset, w - inset * 2, 1);
        gc.fillRect(inset, h - inset - 1, w - inset * 2, 1);
        bracket(gc, inset, inset, 1, 1);
        bracket(gc, w - inset, inset, -1, 1);
        bracket(gc, inset, h - inset, 1, -1);
        bracket(gc, w - inset, h - inset, -1, -1);

        int colX = MenuLayout.columnX(w);
        int colTop = MenuLayout.columnTop(h);
        int colBottom = MenuLayout.columnBottom(h);
        gc.setColor(rgba(ACCENT, 0.35f));
        gc.fillRect(colX - 14, colTop, 1, colBottom - colTop);
        gc.setColor(rgba(GOLD, 0.60f));
        gc.fillRect(colX - 14, colTop, 10, 1);
        gc.setColor(rgba(GOLD, 0.25f));
        gc.fillRect(colX - 14, colBottom - 1, 10, 1);

        // 5. brand lockup, top-right
        int emblemSize = MenuLayout.emblemSize(w, h);
        int emblemX = MenuLayout.emblemX(w, h);
        int top = MenuLayout.lockupTop();

        Graphics2D eg = (Graphics2D) gc.create();
        eg.drawImage(emblem, emblemX, top, emblemSize, emblemSize, null);
        eg.dispose();

        int railY = top + emblemSize + 10;
        gc.setColor(rgba(ACCENT, 0.45f));
        gc.fillRect(emblemX, railY, emblemSize, 1);

        textLeft(gc, "// MAIN", colX - 14, MenuLayout.columnTop(h) - MenuLayout.CAPTION_LIFT,
                1f, ACCENT, 0.85f);

        // 6. buttons
        for (int i = 0; i < BUTTONS.length; i++) {
            int id = BUTTONS[i][0];
            if (id == 5) {
                // the language picker is removed in game, so it must not be drawn here either
                continue;
            }
            int[] r = MenuLayout.buttonRect(id, w, h);
            plate(gc, r[0], r[1], r[2], r[3], i == 1, LABELS[i], CODES[i]);
        }

        return painter.end();
    }

    // ---------------------------------------------------------------- primitives

    private static void lines(Graphics2D gc, float[] xy, int count, float[] c, float a, float width) {
        if (count <= 0) {
            return;
        }
        gc.setComposite(AlphaComposite.SrcOver);
        gc.setColor(rgba(c, a));
        gc.setStroke(new BasicStroke(Math.max(0.6f, width), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            path.moveTo(xy[o], xy[o + 1]);
            path.lineTo(xy[o + 2], xy[o + 3]);
        }
        gc.draw(path);
    }

    private static void quads(Graphics2D gc, float[] xy, int count, float[] c, float a) {
        if (count <= 0) {
            return;
        }
        gc.setComposite(AlphaComposite.SrcOver);
        gc.setColor(rgba(c, a));
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            path.moveTo(xy[o], xy[o + 1]);
            for (int corner = 1; corner < 4; corner++) {
                path.lineTo(xy[o + corner * 2], xy[o + corner * 2 + 1]);
            }
            path.closePath();
        }
        gc.fill(path);
    }

    private static void plate(Graphics2D gc, int x, int y, int w, int h, boolean hover, String label, String code) {
        gc.setComposite(AlphaComposite.SrcOver);
        gc.setColor(new Color(hover ? 0x1E1438 : 0x120B22));
        gc.fillRect(x, y, w, h);

        if (hover) {
            // additive halo in game; layered outlines here so the same three rings read
            for (int i = 3; i >= 1; i--) {
                gc.setColor(rgba(GOLD, 0.11f / i));
                frame(gc, x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2);
            }
            gc.setColor(new Color(0xFF, 0xD2, 0x4A, 0x1C));
            gc.fillRect(x + 1, y + 1, w - 2, h - 2);
        }

        gc.setColor(hover ? rgba(ACCENT, 1f) : rgba(LINE, 0.38f));
        gc.fillRect(x, y, w, 1);
        gc.fillRect(x, y + h - 1, w, 1);
        gc.fillRect(x, y, 1, h);
        gc.fillRect(x + w - 1, y, 1, h);
        gc.setColor(hover ? rgba(GOLD, 1f) : rgba(ACCENT, 0.70f));
        gc.fillRect(x, y + 1, hover ? 4 : 2, h - 2);

        if (hover) {
            // the pulse is frozen at its mean here; in game it breathes at ~0.9Hz
            gc.setColor(rgba(GOLD, 0.85f));
            selectionBrackets(gc, x, y, w, h);
        }

        if (label == null || label.isEmpty()) {
            return;
        }
        textLeft(gc, label, x + 12, y + (h - 9) / 2f, 1f, TEXT, hover ? 1f : 0.82f);
        if (code != null) {
            float cw = width(gc, code, 1f);
            textLeft(gc, code, x + w - 8 - cw, y + (h - 9) / 2f, 1f,
                    hover ? GOLD : TEXT_DIM, hover ? 1f : 0.55f);
        }
    }

    private static void frame(Graphics2D gc, int x0, int y0, int x1, int y1) {
        gc.fillRect(x0, y0, x1 - x0, 1);
        gc.fillRect(x0, y1 - 1, x1 - x0, 1);
        gc.fillRect(x0, y0 + 1, 1, y1 - y0 - 2);
        gc.fillRect(x1 - 1, y0 + 1, 1, y1 - y0 - 2);
    }

    private static void selectionBrackets(Graphics2D gc, int x, int y, int w, int h) {
        final int off = 2;
        final int len = 6;
        gc.fillRect(x - off, y - off, len, 1);
        gc.fillRect(x - off, y - off, 1, len);
        gc.fillRect(x + w + off - len, y - off, len, 1);
        gc.fillRect(x + w + off - 1, y - off, 1, len);
        gc.fillRect(x - off, y + h + off - 1, len, 1);
        gc.fillRect(x - off, y + h + off - len, 1, len);
        gc.fillRect(x + w + off - len, y + h + off - 1, len, 1);
        gc.fillRect(x + w + off - 1, y + h + off - len, 1, len);
    }

    private static void bracket(Graphics2D gc, int x, int y, int dx, int dy) {
        gc.setComposite(AlphaComposite.SrcOver);
        gc.setColor(rgba(ACCENT, 0.34f));
        gc.fillRect(Math.min(x, x + dx * MenuLayout.BRACKET), y, MenuLayout.BRACKET, 1);
        gc.fillRect(x, Math.min(y, y + dy * MenuLayout.BRACKET), 1, MenuLayout.BRACKET);
    }

    // ---------------------------------------------------------------- text

    private static Font font(float scale) {
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, Math.round(11f * scale)));
    }

    private static float width(Graphics2D gc, String s, float scale) {
        gc.setFont(font(scale));
        return gc.getFontMetrics().stringWidth(s);
    }

    private static void textLeft(Graphics2D gc, String s, float x, float y, float scale,
                                 float[] c, float a) {
        gc.setComposite(AlphaComposite.SrcOver);
        gc.setFont(font(scale));
        gc.setColor(rgba(c, a));
        FontMetrics fm = gc.getFontMetrics();
        gc.drawString(s, x, y + fm.getAscent());
    }

    private static Color rgba(float[] c, float a) {
        return new Color(clamp255(Math.round(c[0] * 255)), clamp255(Math.round(c[1] * 255)),
                clamp255(Math.round(c[2] * 255)), clamp255(Math.round(a * 255)));
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void contactSheet(BufferedImage[] shots, int[][] sizes, String path) throws Exception {
        int scale = 3;
        int cw = shots[0].getWidth() / scale;
        int ch = shots[0].getHeight() / scale;
        int label = 24;
        BufferedImage sheet = new BufferedImage(cw * 2, (ch + label) * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(10, 8, 16));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        for (int i = 0; i < shots.length; i++) {
            int cx = (i % 2) * cw;
            int cy = (i / 2) * (ch + label);
            g.drawImage(shots[i], cx, cy, cw, ch, null);
            g.setColor(new Color(190, 180, 220));
            g.drawString(sizes[i][0] + " x " + sizes[i][1], cx + 8, cy + ch + 17);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(path));
    }
}
