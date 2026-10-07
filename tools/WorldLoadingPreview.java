import meowmel.quantumhue.modernsplash.SplashTheme;
import meowmel.quantumhue.modernsplash.WorldLoadingScene;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the world-loading screen outside Minecraft.
 *
 * <p>The screen is reached only by loading a save, which makes it the most expensive screen in the
 * mod to look at and the easiest one to get wrong unnoticed.  The composition is MC-free precisely
 * so this can exist: it draws the same {@link WorldLoadingScene} the game does, through the same
 * {@code SplashPainter} interface.
 *
 * <p>The canvas is warmed up deliberately at two window sizes and two progress values, because the
 * two things that have gone wrong on this project before were both invisible at a single
 * resolution: geometry stated in absolute units, and drawing that depended on the caller's
 * coordinate scale.
 *
 * <pre>
 *   javac -encoding UTF-8 -d build/preview-classes \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTheme.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTimeline.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashPainter.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/Backdrop.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/WorldLoadingScene.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/PcbTraces.java \
 *       tools/SplashPreview.java tools/WorldLoadingPreview.java
 *   java -cp build/preview-classes WorldLoadingPreview
 * </pre>
 */
public final class WorldLoadingPreview {

    private static final String OUT = "build/preview-out/world-loading";

    private WorldLoadingPreview() {}

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();

        int[][] sizes = {{1920, 1080}, {1280, 720}};
        BufferedImage[] shots = new BufferedImage[sizes.length * 2];

        for (int i = 0; i < sizes.length; i++) {
            int w = sizes[i][0];
            int h = sizes[i][1];
            shots[i * 2] = render(w, h, 0.42f);
            shots[i * 2 + 1] = render(w, h, -1f);
            ImageIO.write(shots[i * 2], "png", new File(OUT, w + "x" + h + "-progress.png"));
            ImageIO.write(shots[i * 2 + 1], "png", new File(OUT, w + "x" + h + "-indeterminate.png"));
        }
        contactSheet(shots, sizes, OUT + "/contact-sheet.png");
        System.out.println("world loading preview written to " + OUT);
    }

    private static BufferedImage render(int w, int h, float progress) throws Exception {
        SplashPreview.AwtPainter painter = new SplashPreview.AwtPainter(w, h);
        painter.begin();

        WorldLoadingScene.State state = new WorldLoadingScene.State();
        state.width = w;
        state.height = h;
        // The AWT painter draws in the splash's centre-based scene space.
        state.left = 320f - w * 0.5f;
        state.top = 240f - h * 0.5f;
        state.title = SplashTheme.WORLD_TITLE;
        state.detail = SplashTheme.WORLD_STAGE;
        state.progress = progress;
        state.seconds = 12.0;

        WorldLoadingScene.draw(painter, state);
        return painter.end();
    }

    private static void contactSheet(BufferedImage[] shots, int[][] sizes, String path) {
        int tw = 480;
        int th = tw * 9 / 16;
        BufferedImage sheet = new BufferedImage(tw * 2, (th + 20) * sizes.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int i = 0; i < shots.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = col * tw;
            int y = row * (th + 20);
            g.drawImage(shots[i], x, y, tw, th, null);
            g.setColor(Color.LIGHT_GRAY);
            String label = sizes[row][0] + "x" + sizes[row][1] + (col == 0 ? "  progress" : "  indeterminate");
            g.drawString(label, x + 6, y + th + 14);
        }
        g.dispose();
        try {
            ImageIO.write(sheet, "png", new File(path));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
