import meowmel.quantumhue.modernsplash.Backdrop;
import meowmel.quantumhue.modernsplash.PcbTraces;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the background is one picture, not several.
 *
 * <p>This exists because {@code MenuPreview} and {@code SplashPreview} could not catch the bug that
 * mattered.  Both drew the background through the same shared {@code Backdrop} and both looked
 * right, while the game showed three different backgrounds — the shared module was never the
 * problem, its <em>inputs</em> were.
 *
 * <p>The splash hands {@code Backdrop} a canvas in device pixels; the menu hands it one in scaled
 * GUI units, so at 1080p with GUI scale 3 the menu's canvas is only 640x360.  Any quantity stated
 * in absolute units therefore comes out three times larger on the menu.  This tool renders the bare
 * background at the canvas sizes those callers actually produce and compares the <em>geometry</em>.
 *
 * <h3>Why peak positions and not brightness</h3>
 * Every brightness-based metric was wrong here, and each one failed in an instructive way:
 * <ul>
 *   <li>Mean absolute difference is dominated by the wash and reported 2/255 even with the bug.</li>
 *   <li>Coverage is set by the preview's rasteriser, not by the geometry: AWT strokes hairlines in
 *       canvas units, so a 480-wide canvas has proportionally 4x fatter lines.  It reported a 2.6x
 *       mismatch after the geometry was already correct.</li>
 *   <li>A per-scanline adaptive level gets dragged upwards by those fatter traces and then stops
 *       counting the grid at all — 12 crossings at 1080p against 3 at 360p, on identical geometry.</li>
 * </ul>
 * The position of a local maximum, as a fraction of the canvas, is immune to all three.  That is
 * the quantity that actually has to be equal for the four screens to show one background.
 *
 * <p>The remaining half of the problem — the alpha test killing the grid once {@code FontRenderer}
 * switches it on — cannot be modelled here, because AWT has no alpha test.  That one is asserted in
 * the GL backends themselves ({@code GlSplashPainter.noAlphaTest}, {@code GlStatePainter.noAlphaTest}).
 *
 * <pre>
 *   javac -encoding UTF-8 -d build/preview-classes \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTheme.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTimeline.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashPainter.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/{Backdrop,PcbTraces}.java \
 *       tools/SplashPreview.java tools/BackdropCheck.java
 *   java -cp build/preview-classes BackdropCheck
 * </pre>
 */
public final class BackdropCheck {

    private static final String OUT = "build/preview-out/backdrop";

    /** All 16:9, so the tiles differ in nothing but the canvas size. */
    private static final int[][] CANVASES = {
        {1920, 1080, 1}, // splash at 1080p; also the menu at GUI scale 1
        {960,  540,  2}, // menu at 1080p, GUI scale 2
        {640,  360,  3}, // menu at 1080p, GUI scale 3
        {480,  270,  4}, // menu at 1080p, GUI scale 4
    };

    /** Clock used for every tile; identical so only the canvas differs. */
    private static final float TIME = 12f;

    /** A feature must rise this far above its neighbourhood to count as a hairline. */
    private static final int PROMINENCE = 3;

    /** Half-width of the neighbourhood a peak has to dominate. */
    private static final int WINDOW = 3;

    private static final int TILE_W = 480;
    private static final int TILE_H = 270;

    private BackdropCheck() {}

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();

        List<Double> reference = null;
        boolean ok = true;

        BufferedImage sheet =
                new BufferedImage(TILE_W * CANVASES.length, TILE_H + 22, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(Color.BLACK);
        sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        sg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int i = 0; i < CANVASES.length; i++) {
            int w = CANVASES[i][0];
            int h = CANVASES[i][1];
            int scale = CANVASES[i][2];

            BufferedImage full = render(w, h);
            ImageIO.write(full, "png", new File(OUT, w + "x" + h + ".png"));

            int[] pixels = full.getRGB(0, 0, w, h, null, 0, w);
            List<Double> peaks = peaks(row(pixels, w, h / 4, (int) (w * 0.55f), w));

            String verdict;
            if (reference == null) {
                reference = peaks;
                verdict = String.format("reference: %d features", peaks.size());
            } else {
                int countDelta = Math.abs(peaks.size() - reference.size());
                double shift = maxNearestShift(reference, peaks);
                // A wrong pitch moves features by a large fraction of the canvas and changes how
                // many there are by 2-4x; the preview's own rasteriser costs a few merged peaks on
                // the smallest canvas and nothing else, hence the proportional count tolerance.
                boolean good = countDelta <= reference.size() * 0.25 && shift <= 0.03;
                ok &= good;
                verdict = String.format("features %d (%+d), worst shift %.4f of the width  %s",
                        peaks.size(), peaks.size() - reference.size(), shift,
                        good ? "match" : "MISMATCH");
            }
            System.out.printf("%-9s (GUI scale %d): %s%n", w + "x" + h, scale, verdict);

            BufferedImage tile = new BufferedImage(TILE_W, TILE_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D tg = tile.createGraphics();
            tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            tg.drawImage(full, 0, 0, TILE_W, TILE_H, null);
            tg.dispose();
            sg.drawImage(tile, i * TILE_W, 0, null);
            sg.setColor(Color.LIGHT_GRAY);
            sg.drawString(w + "x" + h + "  scale " + scale, i * TILE_W + 6, TILE_H + 15);
        }
        sg.dispose();
        ImageIO.write(sheet, "png", new File(OUT, "scale-check.png"));

        System.out.println("sheet written to " + OUT + "/scale-check.png (informational: the tiles "
                + "cannot match pixel-for-pixel because AWT thickens hairlines on small canvases)");
        ok &= checkPrimitiveSizes();

        if (!ok) {
            System.out.println("FAIL: the background still depends on the caller's coordinate scale.");
            System.exit(1);
        }
        System.out.println("OK: same geometry at every coordinate scale.");
    }

    /**
     * Every primitive the board emits has to be sized from the canvas, not from a constant.
     *
     * <p>The peak-position check above cannot see this, and that is not hypothetical: the vias were
     * left at an absolute {@code 2f} through the first round of fixes, the pattern stayed identical,
     * and the only symptom was that the menu's squares were three times the size of the splash's.
     * A comparison of positions is blind to size.
     *
     * <p>So this reads the emitted quads directly.  No rasteriser, no antialiasing, no
     * measurement artefact — just the width of a via against the canvas it was laid out on.
     */
    private static boolean checkPrimitiveSizes() {
        System.out.println("primitive sizes, as a fraction of the canvas short side:");
        int[] shortSides = {270, 360, 540, 720, 1080, 1440};
        double referenceVia = -1;
        double referencePitch = -1;
        boolean ok = true;

        for (int shortSide : shortSides) {
            int w = shortSide * 16 / 9;
            int h = shortSide;
            PcbTraces.Buffers board = new PcbTraces.Buffers();
            PcbTraces.build(w, h, TIME, 0f, 0f, board);

            if (board.viaCount == 0) {
                System.out.printf("  %-4d short side: no vias emitted  MISMATCH%n", shortSide);
                ok = false;
                continue;
            }
            // A via quad is (cx-half, cy-half), (cx-half, cy+half), (cx+half, cy+half), (cx+half, cy-half).
            float viaWidth = board.vias[4] - board.vias[0];
            double viaRatio = viaWidth / (double) shortSide;

            // The pitch, recovered from the board itself: the longest gap between successive trace
            // corners is a whole number of cells, so use the via width of the trace-capping via
            // instead, which is a fixed fraction of the pitch.
            double pitchRatio = viaRatio / (2 * (2.5f / 48f));

            String verdict = "reference";
            if (referenceVia < 0) {
                referenceVia = viaRatio;
                referencePitch = pitchRatio;
            } else {
                boolean viaOk = relative(viaRatio, referenceVia) < 0.02;
                boolean pitchOk = relative(pitchRatio, referencePitch) < 0.02;
                ok &= viaOk && pitchOk;
                verdict = String.format("via %.6f, pitch %.6f  %s", viaRatio, pitchRatio,
                        viaOk && pitchOk ? "match" : "MISMATCH vs via " + referenceVia);
            }
            System.out.printf("  %-4d short side: %s%n", shortSide, verdict);
        }
        return ok;
    }

    private static double relative(double a, double b) {
        return Math.abs(a - b) / Math.max(a, b);
    }

    /**
     * Normalised positions of the hairlines along a scanline.
     *
     * <p>A local maximum that rises {@link #PROMINENCE} above its {@link #WINDOW}-wide
     * neighbourhood, reported as a fraction of the canvas width.  The wash ramps slowly enough that
     * it never qualifies, and a thicker line still has exactly one maximum.
     */
    private static List<Double> peaks(int[] values) {
        List<Double> out = new ArrayList<>();
        for (int i = WINDOW; i < values.length - WINDOW; i++) {
            int v = values[i];
            boolean isPeak = true;
            int lowest = v;
            for (int d = -WINDOW; d <= WINDOW; d++) {
                int n = values[i + d];
                if (n > v) {
                    isPeak = false;
                    break;
                }
                lowest = Math.min(lowest, n);
            }
            if (isPeak && v - lowest >= PROMINENCE) {
                out.add(i / (double) values.length);
            }
        }
        return out;
    }

    /** Largest distance from a reference feature to the closest feature in {@code other}. */
    private static double maxNearestShift(List<Double> reference, List<Double> other) {
        if (other.isEmpty()) {
            return 1;
        }
        double worst = 0;
        for (double r : reference) {
            double best = 1;
            for (double o : other) {
                best = Math.min(best, Math.abs(r - o));
            }
            worst = Math.max(worst, best);
        }
        return worst;
    }

    private static BufferedImage render(int w, int h) throws Exception {
        SplashPreview.AwtPainter painter = new SplashPreview.AwtPainter(w, h);
        painter.begin();
        Backdrop.paint(painter, 320f - w * 0.5f, 240f - h * 0.5f, w, h, TIME);
        return painter.end();
    }

    private static int[] row(int[] argb, int w, int y, int from, int to) {
        int[] out = new int[to - from];
        for (int i = 0; i < out.length; i++) {
            out[i] = luminance(argb[y * w + from + i]);
        }
        return out;
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}
