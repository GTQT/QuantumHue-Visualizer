import meowmel.quantumhue.modernsplash.SplashPainter;
import meowmel.quantumhue.pause.PauseAction;
import meowmel.quantumhue.pause.PauseEntry;
import meowmel.quantumhue.pause.PauseLayout;
import meowmel.quantumhue.pause.PauseScene;
import meowmel.quantumhue.pause.PauseTimeline;
import meowmel.quantumhue.pause.PlayerCardData;
import meowmel.quantumhue.pause.Projector;

import java.util.ArrayList;
import java.util.List;

/**
 * Probes {@link PauseScene#hitTest} on a grid, so the click map can be checked without the game.
 *
 * <p>Written after "back to game / save and quit" stopped responding: a hit test is invisible to
 * the preview images, and the two halves of the arc share the same band, so the only way to be sure
 * both are reachable is to ask the scene where a click lands.
 */
public final class PauseHitProbe {

    public static void main(String[] args) {
        // Two canvases on purpose: the design space is 1920x1080, but the pointer arrives in GUI
        // units, which are usually narrower.  A hit test that mixes the two passes at 1920 and
        // fails on every real window — which is exactly what happened to the arc's right half.
        probe(1920, 1080, 1920, 1080);
        System.out.println();
        // the case that actually shipped broken: a 1920x1080 window at GUI scale 2 gives a 960x540
        // canvas but keeps the desktop layout, so board units and canvas units differ by 2x
        probe(960, 540, 1920, 1080);
    }

    private static void probe(int width, int height, int pixelWidth, int pixelHeight) {
        System.out.println("=== canvas " + width + "x" + height + " (pixels "
                + pixelWidth + "x" + pixelHeight + ") ===");
        PauseScene.State state = new PauseScene.State();
        state.layout = PauseLayout.create(width, height, pixelWidth, pixelHeight, entries());
        state.width = width;
        state.height = height;
        state.left = 0f;
        state.top = 0f;
        state.projector = new Projector(state.layout, 0f, 0f, 0f, 0f);
        state.frame = new PauseTimeline(0L, false).sample(4_000_000_000L);
        state.player = player();
        int slots = state.layout.slots.size();
        state.hover = new float[slots];
        state.press = new float[slots];

        System.out.println("slots: " + slots + "   problems: " + state.layout.validate(entries()));
        for (int i = 0; i < slots; i++) {
            PauseLayout.Slot slot = state.layout.slots.get(i);
            System.out.printf("  %2d %-6s order=%2d  %-28s %s%n", i, slot.style, slot.order,
                    slot.entry.label, slot.box);
        }

        System.out.println();
        System.out.println("arc band probes (mid-band):");
        for (int x = 0; x <= width; x += Math.max(20, width / 24)) {
            describe(state, x, Math.round(height * 0.86f));
        }
        System.out.println();
        System.out.println("sphere grab zone:");
        int centreX = Math.round(width * 0.5f);
        int centreY = Math.round(height * 0.5f);
        for (int offset : new int[] {0, 120, 220, 300, 420}) {
            boolean grab = PauseScene.insideSphere(state, centreX + offset, centreY);
            System.out.printf("  (centre + %3d) -> %s%n", offset, grab ? "GRABBABLE" : "-");
        }

        System.out.println();
        System.out.println("tile centres:");
        for (int i = 0; i < slots; i++) {
            PauseLayout.Slot slot = state.layout.slots.get(i);
            if (slot.style != PauseLayout.Style.TILE) {
                continue;
            }
            describe(state, Math.round(slot.box.cx() * state.layout.scale + state.layout.offsetX),
                    Math.round(slot.box.cy() * state.layout.scale + state.layout.offsetY));
        }
    }

    private static void describe(PauseScene.State state, int x, int y) {
        int hit = PauseScene.hitTest(state, x, y);
        String name = hit < 0 ? "NONE"
                : state.layout.slots.get(hit).entry.label + " [" + state.layout.slots.get(hit).style
                        + "/" + state.layout.slots.get(hit).order + "]";
        System.out.printf("  (%4d,%4d) -> %-40s %s%n", x, y, name,
                hit == PauseScene.NONE ? "" : state.layout.slots.get(hit).entry.action);
    }

    private static List<PauseEntry> entries() {
        List<PauseEntry> entries = new ArrayList<PauseEntry>();
        entries.add(new PauseEntry(0, 4, "回到游戏", PauseAction.RESUME, true, null));
        entries.add(new PauseEntry(1, 0, "Options...", PauseAction.OPTIONS, true, null));
        entries.add(new PauseEntry(2, 12, "Mod Options...", PauseAction.MOD_OPTIONS, true, null));
        entries.add(new PauseEntry(3, 5, "Advancements", PauseAction.ADVANCEMENTS, true, null));
        entries.add(new PauseEntry(4, 6, "Statistics", PauseAction.STATISTICS, true, null));
        entries.add(new PauseEntry(5, 7, "Open to LAN", PauseAction.WORLD_OPTIONS, true, null));
        entries.add(new PauseEntry(6, 1, "退出游戏", PauseAction.DISCONNECT, true, null));
        entries.add(new PauseEntry(7, -3000, "关闭MC", PauseAction.QUIT_GAME, true, null));
        entries.add(new PauseEntry(8, -3001, "商店", PauseAction.SHOP, true, null));
        entries.add(new PauseEntry(9, -3002, "公告", PauseAction.NEWS, true, null));
        String[] labels = {"文档", "QQ群", "GitHub", "Video Settings...", "Controls...",
                "Music & Sounds...", "Chat Settings...", "Language...", "Resource Packs...",
                "Skin Customisation...", "Snooper Settings..."};
        PauseAction[] actions = {PauseAction.DOCS, PauseAction.QQ, PauseAction.QQ, PauseAction.DISPLAY,
                PauseAction.CONTROLS, PauseAction.SOUND, PauseAction.CHAT, PauseAction.LANGUAGE,
                PauseAction.PACKS, PauseAction.SKIN, PauseAction.DEBUG};
        for (int i = 0; i < labels.length; i++) {
            entries.add(new PauseEntry(entries.size(), -2000 - i, labels[i], actions[i], true, null));
        }
        return entries;
    }

    private static PlayerCardData player() {
        PlayerCardData data = new PlayerCardData();
        data.name = "MeowmelMuku";
        data.level = 42;
        data.mode = "Survival";
        data.world = "GTQT :: New World";
        data.connection = "Single-player";
        data.position = "X 128   Y 64   Z -332";
        data.dimension = "Overworld";
        data.biome = "Plains";
        data.clock = "2026-10-07   WED   14:32";
        data.fps = 144;
        data.memUsedMb = 1568;
        data.memMaxMb = 5461;
        data.players = 1;
        data.entities = 18;
        data.viewDistance = 12;
        data.health = 17f;
        data.maxHealth = 20f;
        data.food = 15;
        data.armor = 9;
        data.day = 42;
        data.worldClock = "06:15";
        data.weather = "CLEAR";
        data.deaths = 7;
        data.sessionSeconds = 2533L;
        data.difficulty = "NORMAL";
        data.hasSpawn = true;
        data.spawnBearing = 212f;
        data.spawnDistance = 48;
        return data;
    }

    private PauseHitProbe() {}
}
