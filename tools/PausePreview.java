import meowmel.quantumhue.pause.PauseEntry;
import meowmel.quantumhue.pause.PauseAction;
import meowmel.quantumhue.pause.PauseLayout;
import meowmel.quantumhue.pause.PauseScene;
import meowmel.quantumhue.pause.PauseTimeline;
import meowmel.quantumhue.pause.PlayerCardData;
import meowmel.quantumhue.pause.Projector;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.MultipleGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the pause menu outside Minecraft.
 *
 * <p>The screen itself is reached by pressing ESC in a loaded world, which makes it the easiest place
 * in the mod to ship a layout bug unnoticed: there is no way to see it at 4:3 while a blur is running
 * without launching the game twice.  The composition is MC-free precisely so this can exist — it
 * draws the same {@link PauseScene} the game does, through the same {@code SplashPainter} interface,
 * against a mocked blurred world.
 *
 * <p>It checks the things that have gone wrong on this project before: geometry stated in absolute
 * units (hence four window sizes, including the compact one) and drawing that depends on the caller's
 * coordinate scale (hence the same scene at two GUI scales' worth of canvas).
 *
 * <pre>
 *   javac -encoding UTF-8 -d build/preview-pause \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTheme.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashTimeline.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashPainter.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/DysonSphere.java \
 *       src/main/java/meowmel/quantumhue/modernsplash/SplashScene.java \
 *       src/main/java/meowmel/quantumhue/pause/*.java \
 *       tools/SplashPreview.java tools/PausePreview.java
 *   java -cp build/preview-pause PausePreview
 * </pre>
 */
public final class PausePreview {

    private static final String OUT = "build/preview-out/pause";

    /** 1.2 is {@link PauseTimeline#SPEED}: one second of wall clock is 1200 timeline units. */
    private static final float UNITS_PER_SECOND = 1000f * PauseTimeline.SPEED;

    private PausePreview() {}

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        int[][] sizes = {{1920, 1080}, {1280, 720}, {854, 480}, {2560, 1440}};
        String[] names = {"1920x1080", "1280x720", "854x480-compact", "2560x1440"};

        for (int i = 0; i < sizes.length; i++) {
            render(sizes[i][0], sizes[i][1], names[i]);
        }
        System.out.println("pause preview written to " + OUT);
    }

    private static void render(int w, int h, String name) throws Exception {
        SplashPreview.AwtPainter painter = new SplashPreview.AwtPainter(w, h);

        // A "settled" frame at GUI scale 1: the layout is expressed for a 1920x1080 canvas.
        frames(painter, w, h).get(0).hashCode();

        float[] times = {0.10f, 0.30f, 0.45f, 0.62f, 0.80f, 0.95f, 0.95f, 0.95f};
        String[] labels = {"disc growing", "cards flying in", "tiles arriving", "badge pop", "settled",
                "hover", "scrolled", "sphere dragged"};
        BufferedImage[] shots = new BufferedImage[times.length];
        for (int i = 0; i < times.length; i++) {
            boolean hovered = i == 5;
            float scroll = i == 6 ? 1f : 0f;
            float[] drag = i == 7 ? new float[] {70f, 21f} : new float[] {0f, 0f};
            shots[i] = draw(painter, w, h, times[i], hovered, 0f, scroll, drag);
            ImageIO.write(shots[i], "png", new File(OUT, name + "-" + (i + 1) + "-" + slug(labels[i]) + ".png"));
        }

        // closing
        shots[shots.length - 1] = draw(painter, w, h, 0.95f, false, 0.22f, 0f,
                new float[] {0f, 0f});
        ImageIO.write(shots[shots.length - 1], "png", new File(OUT, name + "-9-closing.png"));

        contactSheet(shots, labels, OUT + "/" + name + "-contact-sheet.png", 4);
    }

    private static BufferedImage draw(SplashPreview.AwtPainter painter, int w, int h,
                                      float seconds, boolean hovered, float closingAfter,
                                      float scrollFraction, float[] sphereDrag) {
        long origin = 1_000_000_000L;
        PauseTimeline timeline = new PauseTimeline(origin, false);
        long now = origin + (long) (seconds * 1_000_000_000L);
        if (closingAfter > 0f) {
            timeline.close(now);
            now += (long) (closingAfter * 1_000_000_000L);
        }

        PauseLayout layout = PauseLayout.create(w, h, w, h, entries());
        PauseScene.State state = new PauseScene.State();
        state.layout = layout;
        state.frame = timeline.sample(now);
        state.projector = new Projector(layout, 0f, 0f, 320f - w / 2f, 240f - h / 2f);
        state.player = player();
        state.millis = (long) (seconds * 1000f);
        state.left = 320f - w / 2f;
        state.top = 240f - h / 2f;
        state.width = w;
        state.height = h;
        state.scroll = layout.maxScroll * scrollFraction;
        state.sphereSpin = sphereDrag[0];
        state.sphereTilt = sphereDrag[1];
        state.spark = new float[48];
        state.sparkCount = 48;
        for (int i = 0; i < 48; i++) {
            float t = i / 47f;
            state.spark[i] = 0.55f + 0.28f * (float) Math.sin(t * 9.0) + 0.10f * (float) Math.sin(t * 27.0);
        }
        state.hover = new float[layout.slots.size()];
        state.press = new float[layout.slots.size()];
        if (hovered) {
            for (int i = 0; i < layout.slots.size(); i++) {
                if (layout.slots.get(i).style == PauseLayout.Style.CARD && layout.slots.get(i).order == 1) {
                    state.hover[i] = 1f;
                }
                if (layout.slots.get(i).style == PauseLayout.Style.TILE && layout.slots.get(i).order == 1) {
                    state.hover[i] = 1f;
                    state.press[i] = 1f;
                }
            }
        }

        painter.begin();
        background(painter, w, h, seconds);
        PauseScene.draw(painter, state);
        if (state.faceVisible) {
            facePlaceholder(painter, state.face, w, h, state.faceAlpha);
        }
        return painter.end();
    }

    /**
     * Stands in for the player's skin, which only the in-game chrome can sample.  Drawn straight into
     * image space, so the scene's centre-based origin has to be taken back off first.
     */
    private static void facePlaceholder(SplashPreview.AwtPainter painter, float[] quad,
                                        int w, int h, float alpha) {
        Graphics2D gc = painter.detach();
        float dx = w / 2f - 320f;
        float dy = h / 2f - 240f;
        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
        path.moveTo(quad[0] + dx, quad[1] + dy);
        for (int i = 1; i < 4; i++) {
            path.lineTo(quad[i * 2] + dx, quad[i * 2 + 1] + dy);
        }
        path.closePath();
        gc.setColor(new Color(126, 71, 164, Math.round(90 * alpha)));
        gc.fill(path);
        gc.setColor(new Color(150, 120, 200, Math.round(140 * alpha)));
        gc.setStroke(new java.awt.BasicStroke(1f));
        gc.draw(path);
        gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        gc.setColor(new Color(200, 190, 230, Math.round(170 * alpha)));
        gc.drawString("SKIN", quad[0] + dx + 8f, quad[1] + dy + 20f);
    }

    /** Stand-in for the blurred world the menu is opened over. */
    private static void background(SplashPreview.AwtPainter painter, int w, int h, float t) {
        Graphics2D gc = painter.detach();
        gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int y = 0; y < h; y += 2) {
            float k = y / (float) h;
            gc.setColor(new Color(Math.round(18 + 26 * k), Math.round(30 + 40 * k),
                    Math.round(34 + 22 * k)));
            gc.fillRect(0, y, w, 2);
        }
        // out-of-focus lights: a plausible "world behind a blur", not a screenshot
        float[][] blobs = {{0.18f, 0.22f, 0.30f, 0.55f, 0.62f, 0.45f},
                {0.72f, 0.30f, 0.24f, 0.35f, 0.55f, 0.60f},
                {0.45f, 0.78f, 0.34f, 0.60f, 0.42f, 0.30f},
                {0.88f, 0.72f, 0.22f, 0.50f, 0.36f, 0.28f}};
        for (float[] blob : blobs) {
            float cx = blob[0] * w;
            float cy = blob[1] * h;
            float r = blob[2] * Math.min(w, h);
            gc.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), Math.max(4f, r),
                    new float[] {0f, 1f},
                    new Color[] {new Color(blob[3], blob[4], blob[5], 0.55f),
                            new Color(blob[3], blob[4], blob[5], 0f)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            gc.fillOval(Math.round(cx - r), Math.round(cy - r), Math.round(r * 2), Math.round(r * 2));
        }
        gc.setPaint(null);
    }

    /** The seven entries a 1.12.2 pause menu actually has, ids and labels as vanilla builds them. */
    private static List<PauseEntry> entries() {
        List<PauseEntry> entries = new ArrayList<PauseEntry>();
        entries.add(new PauseEntry(0, 1, "退出游戏", PauseAction.DISCONNECT, true, null));
        // the two GTQT Core cards, which only exist when that mod is installed
        entries.add(new PauseEntry(entries.size(), -3000, "商店", PauseAction.SHOP, true, null));
        entries.add(new PauseEntry(entries.size(), -3001, "公告", PauseAction.NEWS, true, null));
        entries.add(new PauseEntry(1, 4, "Back to Game", PauseAction.RESUME, true, null));
        entries.add(new PauseEntry(2, 0, "Options...", PauseAction.OPTIONS, true, null));
        entries.add(new PauseEntry(3, 12, "Mod Options...", PauseAction.MOD_OPTIONS, true, null));
        entries.add(new PauseEntry(4, 7, "Open to LAN", PauseAction.WORLD_OPTIONS, true, null));
        entries.add(new PauseEntry(5, 5, "Advancements", PauseAction.ADVANCEMENTS, true, null));
        entries.add(new PauseEntry(6, 6, "Statistics", PauseAction.STATISTICS, true, null));
        // The eight vanilla sub-screen shortcuts the pause menu offers, in the same order the screen
        // appends them, so the preview shows the real column rather than an invented one.
        String[] labels = {"Video Settings...", "Controls...", "Music & Sounds...", "Chat Settings...",
                "Language...", "Resource Packs...", "Skin Customization...", "Snooper Settings..."};
        PauseAction[] actions = {PauseAction.DISPLAY, PauseAction.CONTROLS, PauseAction.SOUND,
                PauseAction.CHAT, PauseAction.LANGUAGE, PauseAction.PACKS, PauseAction.SKIN,
                PauseAction.DEBUG};
        entries.add(new PauseEntry(entries.size(), -4000, "文档", PauseAction.DOCS, true, null));
        entries.add(new PauseEntry(entries.size(), -4001, "QQ群", PauseAction.QQ, true, null));
        entries.add(new PauseEntry(entries.size(), -4002, "GitHub", PauseAction.QQ, true, null));        for (int i = 0; i < labels.length; i++) {
            entries.add(new PauseEntry(entries.size(), -2000 - i, labels[i], actions[i], true, null));
        }
        entries.add(new PauseEntry(entries.size(), -3000, "关闭MC", PauseAction.QUIT_GAME, true, null));

        return entries;
    }

    private static PlayerCardData player() {
        PlayerCardData player = new PlayerCardData();
        player.name = "MeowmelMuku";
        player.level = 42;
        player.experience = 0.62f;
        player.mode = "Survival";
        player.world = "GTQT :: New World";
        player.connection = "Single-player";
        player.position = "X 128  Y 64  Z -332";
        player.dimension = "Overworld";
        player.biome = "Plains";
        player.hint = "ESC  to  close";
        player.clock = "2026-10-07   WED   14:32";
        player.fps = 87;
        player.memUsedMb = 5210;
        player.memMaxMb = 8192;
        player.players = 3;
        player.viewDistance = 12;
        player.entities = 612;
        player.health = 17f;
        player.maxHealth = 20f;
        player.food = 15;
        player.armor = 9;
        player.day = 42L;
        player.worldClock = "06:15";
        player.weather = "CLEAR";
        player.yaw = 32f;
        player.sessionSeconds = 2533L;
        player.difficulty = "NORMAL";
        player.hardcore = false;
        player.deaths = 7;
        player.hasSpawn = true;
        player.spawnBearing = 287f;
        player.spawnDistance = 1240f;
        return player;
    }

    // ---------------------------------------------------------------- diagnostics

    private static List<String> frames(SplashPreview.AwtPainter painter, int w, int h) {
        List<String> out = new ArrayList<String>();
        out.add(w + "x" + h);
        return out;
    }

    private static String slug(String text) {
        return text.replace(' ', '-');
    }

    private static void contactSheet(BufferedImage[] frames, String[] labels, String path, int cols) {
        int fw = frames[0].getWidth();
        int fh = frames[0].getHeight();
        int scale = Math.max(1, 1800 / (cols * fw) + 1);
        int cw = fw / scale;
        int ch = fh / scale;
        int label = 20;
        int rows = (frames.length + cols - 1) / cols;

        BufferedImage sheet = new BufferedImage(cols * cw, rows * (ch + label), BufferedImage.TYPE_INT_RGB);
        Graphics2D gc = sheet.createGraphics();
        gc.setColor(new Color(12, 10, 18));
        gc.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gc.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        for (int i = 0; i < frames.length; i++) {
            int cx = (i % cols) * cw;
            int cy = (i / cols) * (ch + label);
            gc.drawImage(frames[i], cx, cy, cw, ch, null);
            gc.setColor(new Color(190, 180, 220));
            gc.drawString(labels[i], cx + 8, cy + ch + 15);
        }
        gc.dispose();
        try {
            ImageIO.write(sheet, "png", new File(path));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
