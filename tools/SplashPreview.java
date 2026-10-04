import meowmel.quantumhue.modernsplash.SplashPainter;
import meowmel.quantumhue.modernsplash.SplashScene;
import meowmel.quantumhue.modernsplash.SplashTimeline;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders {@link SplashScene} outside Minecraft so the splash can be reviewed without booting the
 * game.  It implements the same {@link SplashPainter} contract as the in-game GL backend, and the
 * composition code is literally the same class, so this is a faithful preview of the layout,
 * timing and colour -- not a re-implementation.
 *
 * <p>Compile and run (the mod sources it needs are free of Minecraft imports):
 * <pre>
 *   javac -encoding UTF-8 -d build/preview \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashPainter.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTheme.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTimeline.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashScene.java \
 *       tools/SplashPreview.java
 *   java -cp build/preview SplashPreview
 * </pre>
 */
public final class SplashPreview {

    private static final String TEX_DIR = "src/main/resources/assets/quantumhue/textures/gui/title/";

    public static void main(String[] args) throws Exception {
        int w = args.length > 0 ? Integer.parseInt(args[0]) : 1280;
        int h = args.length > 1 ? Integer.parseInt(args[1]) : 720;
        String outDir = args.length > 2 ? args[2] : "build/preview-out";
        new File(outDir).mkdirs();

        AwtPainter painter = new AwtPainter(w, h);
        float[] times = {0.25f, 0.9f, 1.5f, 1.9f, 2.8f, 4.5f, 9.0f, 13.0f, 15.6f, 18.5f};

        BufferedImage[] frames = new BufferedImage[times.length];
        SplashTimeline timeline = new SplashTimeline();
        for (int i = 0; i < times.length; i++) {
            SplashTimeline.Frame f = timeline.frame(times[i]);
            painter.begin();
            SplashScene.draw(painter, state(w, h, f));
            frames[i] = painter.end();
            ImageIO.write(frames[i], "png", new File(outDir, String.format("frame-%05.2f.png", times[i])));
        }

        contactSheet(frames, times, outDir + "/contact-sheet.png", 3);
        System.out.println("preview written to " + outDir);
    }

    /** Mock loading state; in game these come from FML's ProgressManager and the JVM heap. */
    private static SplashScene.State state(int w, int h, SplashTimeline.Frame f) {
        SplashScene.State s = new SplashScene.State();
        s.width = w;
        s.height = h;
        s.intro = f;
        s.uiAlpha = f.ui;

        s.bar0 = bar("Constructing Mods", "coremods : gregtech, gtqtcore, mixinbooter", 3, 7);
        s.bar1 = bar("Pre-initializing", "applying access transformers", 1, 7);
        s.bar2 = bar("Loading Resources", "assets/quantumhue/textures", 5, 7);

        s.showMemory = true;
        s.memUsedMb = 4304;
        s.memMaxMb = 8192;
        s.showTimer = true;
        s.startupSeconds = 42.7f;
        s.estimateSeconds = 96f;
        return s;
    }

    private static SplashScene.Bar bar(String title, String message, int step, int steps) {
        SplashScene.Bar b = new SplashScene.Bar();
        b.title = title;
        b.message = message;
        b.step = step;
        b.steps = steps;
        return b;
    }

    /** Lays the frames out as a grid so a whole run can be judged from one image. */
    private static void contactSheet(BufferedImage[] frames, float[] times, String path, int cols) {
        int fw = frames[0].getWidth();
        int fh = frames[0].getHeight();
        int scale = Math.max(1, 1600 / (cols * fw) + 1);
        int cw = fw / scale;
        int ch = fh / scale;
        int label = 22;
        int rows = (frames.length + cols - 1) / cols;

        BufferedImage sheet = new BufferedImage(cols * cw, rows * (ch + label), BufferedImage.TYPE_INT_RGB);
        Graphics2D gc = sheet.createGraphics();
        gc.setColor(new Color(12, 10, 18));
        gc.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        for (int i = 0; i < frames.length; i++) {
            int cx = (i % cols) * cw;
            int cy = (i / cols) * (ch + label);
            gc.drawImage(frames[i], cx, cy, cw, ch, null);
            gc.setColor(new Color(190, 180, 220));
            gc.drawString(String.format("t = %5.2fs", times[i]), cx + 8, cy + ch + 16);
        }
        gc.dispose();
        try {
            ImageIO.write(sheet, "png", new File(path));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ AWT backend

    static final class AwtPainter implements SplashPainter {

        private final int width;
        private final int height;
        private final BufferedImage[] textures = new BufferedImage[1];
        private final AffineTransform base = new AffineTransform();
        private final Deque<AffineTransform> stack = new ArrayDeque<>();

        private BufferedImage canvas;
        private Graphics2D gc;
        private AffineTransform current;

        AwtPainter(int width, int height) throws Exception {
            this.width = width;
            this.height = height;
            textures[TEX_LOGO] = ImageIO.read(new File(TEX_DIR + "gtqt_logo.png"));
            // scene space -> image space: the scene's origin is the screen centre at (320, 240)
            base.translate(-(320.0 - width / 2.0), -(240.0 - height / 2.0));
        }

        void begin() {
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            gc = canvas.createGraphics();
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gc.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            current = new AffineTransform(base);
            stack.clear();
        }

        BufferedImage end() {
            gc.dispose();
            return canvas;
        }

        /**
         * Hands the canvas over to plain image-space drawing.
         *
         * <p>{@code MenuPreview} shares this painter for the background only and draws its own
         * foreground with raw {@code Graphics2D}, so the scene-space transform has to come back off
         * first — otherwise everything it draws afterwards lands offset by the scene origin.
         */
        Graphics2D detach() {
            gc.setTransform(new AffineTransform());
            gc.setComposite(AlphaComposite.SrcOver);
            return gc;
        }

        private static Color color(float r, float g, float b, float a) {
            return new Color(clamp(r), clamp(g), clamp(b), clamp(a));
        }

        private static float clamp(float v) {
            return v < 0f ? 0f : (v > 1f ? 1f : v);
        }

        private void apply() {
            gc.setTransform(current);
        }

        @Override
        public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
            if (a <= 0.002f || w <= 0f || h <= 0f) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            gc.fill(new Rectangle2D.Float(x, y, w, h));
        }

        @Override
        public void sprite(int texture, float x, float y, float w, float h,
                           float u0, float v0, float u1, float v1,
                           float r, float g, float b, float a) {
            BufferedImage tex = textures[texture];
            if (a <= 0.002f || tex == null) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);

            int sx = Math.round(u0 * tex.getWidth());
            int sy = Math.round(v0 * tex.getHeight());
            int sw = Math.max(1, Math.round((u1 - u0) * tex.getWidth()));
            int sh = Math.max(1, Math.round((v1 - v0) * tex.getHeight()));

            BufferedImage tinted = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tinted.createGraphics();
            tg.drawImage(tex.getSubimage(sx, sy, sw, sh), 0, 0, null);
            // glColor4f multiplies the texel, so the preview must multiply too.  Replacing the
            // colour instead (SrcAtop) would flatten the emblem to a white silhouette.
            boolean neutral = r > 0.999f && g > 0.999f && b > 0.999f;
            if (!neutral) {
                tg.setComposite(AlphaComposite.SrcOver);
                tg.drawImage(new RescaleOp(new float[] {r, g, b, 1f}, new float[] {0f, 0f, 0f, 0f}, null)
                        .filter(tinted, null), 0, 0, null);
            }
            tg.dispose();

            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(a)));
            gc.drawImage(tinted,
                    Math.round(x), Math.round(y), Math.round(w), Math.round(h), null);
        }

        @Override
        public void glow(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
            if (a <= 0.002f || radius <= 0f) {
                return;
            }
            apply();
            // Java2D has no cheap additive bloom; a radial gradient reads close enough for review.
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setPaint(new RadialGradientPaint(
                    new Point2D.Float(cx, cy), radius,
                    new float[] {0f, 1f},
                    new Color[] {color(r, g, b, a), color(r, g, b, 0f)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            gc.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
            gc.setPaint(null);
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float r, float g, float b, float a, float width) {
            if (a <= 0.002f) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            gc.setStroke(new BasicStroke(Math.max(0.6f, width), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            gc.draw(new Line2D.Float(x0, y0, x1, y1));
        }

        @Override
        public void lines(float[] xy, int segmentCount, float r, float g, float b, float a, float width) {
            if (a <= 0.002f || segmentCount <= 0) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            gc.setStroke(new BasicStroke(Math.max(0.6f, width), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            Path2D.Float path = new Path2D.Float();
            for (int i = 0; i < segmentCount; i++) {
                int o = i * 4;
                path.moveTo(xy[o], xy[o + 1]);
                path.lineTo(xy[o + 2], xy[o + 3]);
            }
            gc.draw(path);
        }

        @Override
        public void quads(float[] xy, int quadCount, float r, float g, float b, float a) {
            if (a <= 0.002f || quadCount <= 0) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            Path2D.Float path = new Path2D.Float();
            for (int i = 0; i < quadCount; i++) {
                int o = i * 8;
                path.moveTo(xy[o], xy[o + 1]);
                for (int c = 1; c < 4; c++) {
                    path.lineTo(xy[o + c * 2], xy[o + c * 2 + 1]);
                }
                path.closePath();
            }
            gc.fill(path);
        }

        @Override
        public void blendMode(boolean additive) {
            // Java2D has no additive composite.  Additive passes are approximated by drawing at
            // full strength over the backdrop, so the preview is slightly dimmer than the game on
            // the swarm's energy arcs -- everything else matches.
        }

        @Override
        public void arc(float cx, float cy, float radius, float fromDeg, float toDeg,
                        float r, float g, float b, float a, float width) {
            float span = toDeg - fromDeg;
            if (a <= 0.002f || radius <= 0f || Math.abs(span) < 0.5f) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            gc.setStroke(new BasicStroke(Math.max(0.6f, width), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

            int steps = Math.max(6, (int) (Math.abs(span) / 3f));
            Path2D.Float path = new Path2D.Float();
            for (int i = 0; i <= steps; i++) {
                double angle = Math.toRadians(fromDeg + span * i / steps);
                float px = cx + (float) Math.cos(angle) * radius;
                float py = cy + (float) Math.sin(angle) * radius;
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            gc.draw(path);
        }

        @Override
        public void push() {
            stack.push(new AffineTransform(current));
        }

        @Override
        public void pop() {
            if (!stack.isEmpty()) {
                current = stack.pop();
            }
        }

        @Override
        public void translate(float x, float y) {
            current.concatenate(AffineTransform.getTranslateInstance(x, y));
        }

        @Override
        public void rotate(float degrees) {
            current.concatenate(AffineTransform.getRotateInstance(Math.toRadians(degrees)));
        }

        @Override
        public void scale(float sx, float sy) {
            current.concatenate(AffineTransform.getScaleInstance(sx, sy));
        }

        @Override
        public void text(String s, float x, float y, float scale, float r, float g, float b, float a) {
            if (s == null || s.isEmpty() || a <= 0.004f) {
                return;
            }
            apply();
            gc.setComposite(AlphaComposite.SrcOver);
            gc.setColor(color(r, g, b, a));
            gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, Math.round(11f * scale))));
            FontMetrics fm = gc.getFontMetrics();
            gc.drawString(s, x, y + fm.getAscent());
        }

        @Override
        public float textWidth(String s, float scale) {
            if (s == null) {
                return 0f;
            }
            gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, Math.round(11f * scale))));
            return gc.getFontMetrics().stringWidth(s);
        }

        @Override
        public float textHeight(float scale) {
            gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, Math.round(11f * scale))));
            return gc.getFontMetrics().getHeight();
        }
    }
}
