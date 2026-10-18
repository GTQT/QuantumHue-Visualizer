package meowmel.quantumhue.pause;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the pause menu goes.
 *
 * <p>All geometry is stated on a <b>1920x1080 board</b> and then scaled uniformly onto whatever
 * canvas the window gives us; {@link #scale}, {@link #offsetX} and {@link #offsetY} are the only
 * places those two spaces meet.  Nothing here is allowed to be an absolute pixel count for a
 * specific window size — the project has shipped that bug twice already.
 *
 * <h3>Composition</h3>
 * A big disc fills the middle: its centre is the board centre and its outer rim is 498 units.  The
 * four primary actions are cards down the left edge, the secondary actions are tiles riding the
 * disc's right-hand arc (their x is the circle's own equation, so they lean with its curve), "back
 * to game" is the segment at the bottom of the disc, and "save and quit" is the power plate in the
 * bottom-left corner.  On a narrow or small window all of that collapses to {@link #compact()}: a
 * two-column list, no disc.
 *
 * <p>The numbers are lifted from the EndfieldCraft pause screen, whose layout this is: the disc
 * radius, the four card anchors, the 535-unit tile arc, the resume chord and the corner furniture
 * are its measurements, not guesses.  What is <em>not</em> lifted is the look — see
 * {@code PauseScene}.
 */
public final class PauseLayout {

    // ---------------------------------------------------------------- board

    public static final float DESIGN_W = 1920f;
    public static final float DESIGN_H = 1080f;

    /** Compact board width; the height follows the window's aspect ratio. */
    private static final float COMPACT_W = 800f;

    // ---------------------------------------------------------------- disc

    public static final float CENTER_X = 960f;
    public static final float CENTER_Y = 540f;
    /** Silhouette radius of the disc. */
    public static final float OUTER_R = 498f;
    /** Radius of the inner well the Dyson sphere sits in. */
    public static final float INNER_R = 447f;
    /** Depth of the rim in the projection, so it is drawn a little in front of the well. */
    public static final float TOP_DEPTH = 50f;
    public static final float BASE_DEPTH = -112f;
    public static final float FLOOR_DEPTH = -130f;
    /** "Back to game" is the segment below this chord, at this radius. */
    public static final float RESUME_R = 425f;
    public static final float RESUME_CHORD = 278f;
    public static final float RESUME_DEPTH = 26f;

    public static final Box RIM = new Box(CENTER_X - OUTER_R - 22f, CENTER_Y - OUTER_R - 22f,
            2f * (OUTER_R + 22f), 2f * (OUTER_R + 22f));
    public static final Box WELL = new Box(CENTER_X - INNER_R, CENTER_Y - INNER_R,
            2f * INNER_R, 2f * INNER_R);

    // ---------------------------------------------------------------- furniture


    /**
     * GTQT Core's market, anchored top-left.
     *
     * <p>Offset from the card column on purpose: it is one row higher, and the disc rim has already
     * curved away that far, so its left edge has to step right by the same amount as the row pitch
     * for the five plates to read as one staircase along the arc.
     */
    public static final Box SHOP_CARD = new Box(236f, 92f, 292f, 132f);
    /**
     * GTQT Core's announcement, anchored top-right.
     *
     * <p>Wider than the market on purpose: the market hangs off a single card column, while this
     * corner is backed by two tile columns, so its right edge lines up with the far tile column and
     * the banner reads as one block over two.
     */
    public static final Box NEWS_CARD = new Box(1345f, 92f, 420f, 132f);
    public static final Box PROFILE = new Box(210f, 858f, 430f, 126f);
    public static final Box WORLD = new Box(1405f, 70f, 390f, 166f);
    public static final Box FOOTER = new Box(50f, 1043f, 1820f, 32f);
    public static final Box POWER = new Box(92f, 866f, 76f, 76f);

    // ---------------------------------------------------------------- cards / tiles

    /**
     * Horizontal anchor per card position; the column bulges leftwards as it descends.
     *
     * <p>Six entries exist now (GTQT Core adds a market and an announcement card), so the column
     * has to stay readable at either count.  The pitch and the height below are derived from how
     * many cards are actually present, which is why an install without GTQT Core still gets the
     * original four tall cards.
     */
    private static final float[] CARD_X = {183f, 130f, 122f, 155f};
    public static final float CARD_W = 292f;
    /**
     * Distance from the disc rim to a column's inner edge.
     *
     * <p>Derived from the tile column rather than picked by eye: the tiles stand on a 535-unit arc
     * against a 498-unit disc, which leaves roughly 45 units between them over the card heights.
     */
    public static final float COLUMN_GAP = 50f;
    public static final float CARD_H = 148f;
    public static final float CARD_TOP = 224f;
    public static final float CARD_PITCH = 142f;
    public static final int CARD_SLOTS = 4;
    /** Bottom of the card column, in board units. */
    private static final float CARD_BOTTOM = 798f;

    public static final float TILE_W = 154f;
    public static final float TILE_H = 144f;
    public static final float TILE_TOP = 250f;
    public static final float TILE_PITCH = 140f;
    /** Radius of the circle the tile column rides. */
    public static final float TILE_ARC_R = 535f;
    /** Top of the window the tile column scrolls inside, in board units. */
    public static final float TILE_BAND_TOP = 232f;
    /**
     * Height of that window.
     *
     * <p>Four rows of tiles fit exactly; a fifth makes the column scroll, which is what keeps the
     * size of a tile independent of how many buttons other mods hang on the pause menu.
     */
    public static final float TILE_BAND = 596f;

    public enum Style { CARD, CORNER, TILE, RESUME, POWER }

    /** One placed entry: its board rectangle and how it should be drawn. */
    public static final class Slot {
        public final PauseEntry entry;
        public final Box box;
        public final Style style;
        public final int order;

        Slot(PauseEntry entry, Box box, Style style, int order) {
            this.entry = entry;
            this.box = box;
            this.style = style;
            this.order = order;
        }
    }

    public final boolean compact;
    public final float designW;
    public final float designH;
    /** Board units -> canvas units. */
    public final float scale;
    /** Canvas-space origin of the board's top-left corner. */
    public final float offsetX;
    public final float offsetY;
    public final List<Slot> slots;
    public final Box profile;
    /** The band the scrolling tile column lives in, in board units. */
    public final Box scrollArea;
    /** How far that column can scroll, in board units; {@code 0} when everything fits. */
    public final float maxScroll;
    public final int tileCount;
    /** Cards actually present, 0..{@link #CARD_SLOTS}. */
    public final int cardCount;

    private PauseLayout(boolean compact, float designW, float designH, float scale,
                        float offsetX, float offsetY, List<Slot> slots, Box profile,
                        Box scrollArea, float maxScroll, int tileCount, int cardCount) {
        this.compact = compact;
        this.designW = designW;
        this.designH = designH;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.slots = slots;
        this.profile = profile;
        this.scrollArea = scrollArea;
        this.maxScroll = maxScroll;
        this.tileCount = tileCount;
        this.cardCount = cardCount;
    }

    /**
     * @param guiWidth    canvas width in GUI units
     * @param guiHeight   canvas height in GUI units
     * @param pixelWidth  window width in physical pixels
     * @param pixelHeight window height in physical pixels
     */
    public static PauseLayout create(int guiWidth, int guiHeight, int pixelWidth, int pixelHeight,
                                     List<PauseEntry> entries) {
        boolean compact = pixelWidth < 1000 || pixelHeight < 600
                || (pixelHeight > 0 && (float) pixelWidth / pixelHeight < 1.35f);
        return compact ? compact(guiWidth, guiHeight, entries) : desktop(guiWidth, guiHeight, entries);
    }

    // ---------------------------------------------------------------- desktop

    private static PauseLayout desktop(int width, int height, List<PauseEntry> entries) {
        float scale = Math.min(width / DESIGN_W, height / DESIGN_H);
        float offsetX = (width - DESIGN_W * scale) * 0.5f;
        float offsetY = (height - DESIGN_H * scale) * 0.5f;

        // Cards first: the number of primaries decides the column's pitch and height, and their
        // preferred order decides which position each one takes.  Positions are therefore always a
        // contiguous 0..n-1 run — an install without GTQT Core simply starts the run two entries
        // later and still fills the column top to bottom.
        List<Integer> primary = new ArrayList<Integer>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).action.isPrimary()) {
                primary.add(i);
            }
        }
        primary.sort(new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Integer.compare(entries.get(a).action.cardOrder(),
                        entries.get(b).action.cardOrder());
            }
        });
        int cardCount = Math.min(CARD_SLOTS, primary.size());
        float cardPitch = Math.min(CARD_PITCH, (CARD_BOTTOM - CARD_TOP) / Math.max(1f, (float) cardCount));
        float cardH = Math.min(CARD_H, cardPitch + (CARD_H - CARD_PITCH));
        int[] cardPosition = new int[entries.size()];
        java.util.Arrays.fill(cardPosition, -1);
        for (int i = 0; i < cardCount; i++) {
            cardPosition[primary.get(i)] = i;
        }

        int tileCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            PauseAction action = entries.get(i).action;
            if (cardPosition[i] < 0 && action != PauseAction.RESUME && action != PauseAction.DISCONNECT
                    && action != PauseAction.SHOP && action != PauseAction.NEWS
                    && action != PauseAction.QUIT_GAME) {
                tileCount++;
            }
        }
        int rows = (tileCount + 1) / 2;
        // No squeezing any more: the column scrolls instead, so the tiles keep one size and one
        // pitch no matter how many buttons another mod hangs on the pause menu.
        float pitch = TILE_PITCH;
        float tileH = TILE_H;
        float contentH = rows <= 0 ? 0f : (rows - 1) * pitch + tileH;
        float maxScroll = Math.max(0f, contentH - TILE_BAND);

        List<Slot> slots = new ArrayList<Slot>(entries.size());
        int tile = 0;
        for (int i = 0; i < entries.size(); i++) {
            PauseEntry entry = entries.get(i);
            if (entry.action == PauseAction.RESUME) {
                slots.add(new Slot(entry, resumeBounds(), Style.RESUME, 0));
            } else if (entry.action == PauseAction.DISCONNECT) {
                // the other half of the arc band: "save and quit" sits beside "back to game"
                slots.add(new Slot(entry, resumeBounds(), Style.RESUME, 1));
            } else if (entry.action == PauseAction.QUIT_GAME) {
                slots.add(new Slot(entry, POWER, Style.POWER, 0));
            } else if (entry.action == PauseAction.SHOP) {
                slots.add(new Slot(entry, SHOP_CARD, Style.CORNER, 0));
            } else if (entry.action == PauseAction.NEWS) {
                slots.add(new Slot(entry, NEWS_CARD, Style.CORNER, 1));
            } else if (cardPosition[i] >= 0) {
                slots.add(new Slot(entry, cardBox(cardPosition[i], cardPitch, cardH),
                        Style.CARD, cardPosition[i]));
            } else {
                slots.add(new Slot(entry, tileBox(tile, pitch, tileH), Style.TILE, tile));
                tile++;
            }
        }
        return new PauseLayout(false, DESIGN_W, DESIGN_H, scale, offsetX, offsetY, slots,
                PROFILE, new Box(CENTER_X + 5f, TILE_BAND_TOP, 900f, TILE_BAND),
                maxScroll, tileCount, cardCount);
    }

    private static Box cardBox(int position, float pitch, float height) {
        return new Box(CARD_X[position], CARD_TOP + position * pitch, CARD_W, height);
    }

    private static Box tileBox(int index, float pitch, float height) {
        int row = index / 2;
        float y = TILE_TOP + row * pitch;
        return new Box(arcX(y + height * 0.5f) + (index % 2) * (TILE_W + 4f), y, TILE_W, height);
    }

    /**
     * Board x of the tile column's inner edge at a given board y.
     *
     * <p>The column presses against the disc, so its left edge is the arc, not a straight line.  This
     * is used both when the layout is built and every frame while the column is scrolled: a tile
     * that is only translated leaves the arc behind, which is why the inner edge used to drift into
     * the disc the further you scrolled.
     */
    public static float arcX(float y) {
        float dy = y - CENTER_Y;
        float dx = (float) Math.sqrt(Math.max(0f, TILE_ARC_R * TILE_ARC_R - dy * dy));
        return CENTER_X + 5f + dx;
    }

    /**
     * Where a tile actually sits once the column has scrolled.
     *
     * <p>Vertical offset only, but the horizontal offset is re-derived from the arc at the new y, so
     * the left edge stays on the disc at every scroll position.
     */
    public static Box scrolledTile(Box unscrolled, float scroll) {
        float y = unscrolled.y - scroll;
        float column = unscrolled.x - arcX(unscrolled.y + unscrolled.h * 0.5f);
        return new Box(arcX(y + unscrolled.h * 0.5f) + column, y, unscrolled.w, unscrolled.h);
    }

    /** Board rectangle of the "back to game" segment: the part of the disc below the chord. */
    public static Box resumeBounds() {
        float halfWidth = (float) Math.sqrt(RESUME_R * RESUME_R - RESUME_CHORD * RESUME_CHORD);
        return new Box(CENTER_X - halfWidth, CENTER_Y + RESUME_CHORD, halfWidth * 2f, RESUME_R - RESUME_CHORD);
    }

    // ---------------------------------------------------------------- compact

    private static PauseLayout compact(int width, int height, List<PauseEntry> entries) {
        float scale = width / COMPACT_W;
        float designH = scale <= 0f ? DESIGN_H : height / scale;
        float top = designH < 570f ? 96f : 194f;
        float areaH = Math.max(64f, designH - top - 140f);

        int count = 0;
        for (PauseEntry entry : entries) {
            if (entry.action != PauseAction.RESUME) {
                count++;
            }
        }
        int rows = (count + 1) / 2;
        float pitch = 116f;
        float tileH = 112f;
        float contentH = rows <= 0 ? 0f : rows * pitch;
        float maxScroll = Math.max(0f, contentH - areaH);

        List<Slot> slots = new ArrayList<Slot>(entries.size());
        int index = 0;
        for (PauseEntry entry : entries) {
            if (entry.action == PauseAction.RESUME) {
                slots.add(new Slot(entry, new Box(36f, designH - 116f, 728f, 88f), Style.RESUME, 0));
            } else {
                slots.add(new Slot(entry, new Box(36f + (index % 2) * 370f,
                        top + 8f + (index / 2) * pitch, 364f, tileH), Style.TILE, index));
                index++;
            }
        }
        return new PauseLayout(true, COMPACT_W, designH, scale, 0f, 0f, slots,
                new Box(36f, 86f, 650f, 88f),
                new Box(28f, top, 744f, areaH), maxScroll, count, 0);
    }

    // ---------------------------------------------------------------- queries

    /** The profile card is dropped on very short windows, where it would overlap the list. */
    public boolean showsProfile() {
        return !compact || designH >= 570f;
    }

    /**
     * A copy of this layout that projects as a plain 2D board.
     *
     * <p>Used for the close plate, the caption and the footer: those three are screen furniture and
     * must not lean with the board, or the close button would sit at a different angle every time
     * the pointer moved.  The reference does exactly this with its {@code flatLayout}.
     */
    public PauseLayout flat() {
        return new PauseLayout(true, designW, designH, scale, offsetX, offsetY,
                java.util.Collections.<Slot>emptyList(), profile, scrollArea, 0f, 0,
                cardCount);
    }

    public boolean showsFurniture() {
        return !compact;
    }

    public Box world() {
        return WORLD;
    }

    public Box footer() {
        return FOOTER;
    }

    /** Board-space rectangle of the slot at {@code index}, or {@code null}. */
    public Box boxOf(int index) {
        return index < 0 || index >= slots.size() ? null : slots.get(index).box;
    }

    // ---------------------------------------------------------------- self-check

    /**
     * Structural self-check, run once per layout build.
     *
     * <p>This exists because the pause menu's button list is not ours: vanilla builds seven, Forge
     * adds one, and every other mod in the pack may add more.  A role that ends up with no card, two
     * cards on the same plate, or a slot that lands off the board is not a crash — it is a menu that
     * silently loses a button, which is far worse to find later.  Everything suspect is reported by
     * name so one line of log says exactly which entry misbehaved.
     *
     * @return human-readable problems; empty means the layout is sound
     */
    public List<String> validate(List<PauseEntry> entries) {
        List<String> problems = new ArrayList<String>();
        if (entries.size() != slots.size()) {
            problems.add("entries=" + entries.size() + " but slots=" + slots.size()
                    + " — some entry lost its plate");
        }
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Object, Boolean>());
        boolean[] cardTaken = new boolean[CARD_SLOTS];
        boolean[] tileTaken = new boolean[Math.max(1, tileCount)];
        int resumes = 0;
        int disconnects = 0;
        for (Slot slot : slots) {
            if (!seen.add(slot.entry)) {
                problems.add("entry #" + slot.entry.index + " (" + slot.entry.label
                        + ") was placed twice");
            }
            switch (slot.style) {
                case CARD:
                    if (slot.order < 0 || slot.order >= CARD_SLOTS || cardTaken[slot.order]) {
                        problems.add("card slot " + slot.order + " claimed twice (entry #"
                                + slot.entry.index + " " + slot.entry.label + ")");
                    } else {
                        cardTaken[slot.order] = true;
                    }
                    break;
                case TILE:
                    if (slot.order < 0 || slot.order >= tileTaken.length || tileTaken[slot.order]) {
                        problems.add("tile slot " + slot.order + " claimed twice (entry #"
                                + slot.entry.index + " " + slot.entry.label + ")");
                    } else {
                        tileTaken[slot.order] = true;
                    }
                    break;
                case RESUME:
                    resumes++;
                    break;
                case POWER:
                    disconnects++;
                    break;
                default:
                    break;
            }
            if (slot.style != Style.RESUME) {
                Box box = slot.box;
                // Tiles below the fold are legal: the column scrolls, so a slot whose only offence
                // is being below the band is not a defect.
                boolean scrolls = slot.style == Style.TILE;
                if (box.x < 0f || box.right() > designW
                        || (!scrolls && (box.y < 0f || box.bottom() > designH))) {
                    problems.add("entry #" + slot.entry.index + " (" + slot.entry.label
                            + ") sits off the board: " + box);
                }
            }
        }
        if (resumes > 2) {
            problems.add("expected at most two arc halves, found " + resumes);
        }
        if (disconnects > 1) {
            problems.add("expected at most one power plate, found " + disconnects);
        }
        // Positions are assigned as a contiguous run from zero, so a gap in the middle means an
        // entry was placed out of order — an unused slot at the end is normal when GTQT Core is
        // absent and the column only holds four cards.
        for (int i = 0; i < cardCount; i++) {
            if (!cardTaken[i]) {
                problems.add("card position " + (i + 1) + " is empty while later positions are used");
            }
        }
        // Duplicate plates: the intended card tuck is a few units of overlap, so only a *substantial*
        // overlap counts as two entries fighting over the same space.
        for (int a = 0; a < slots.size(); a++) {
            for (int b = a + 1; b < slots.size(); b++) {
                Slot first = slots.get(a);
                Slot second = slots.get(b);
                if (first.style == Style.RESUME || second.style == Style.RESUME) {
                    continue;
                }
                if (overlapArea(first.box, second.box) > 0.25f * Math.min(area(first.box), area(second.box))) {
                    problems.add("entries #" + first.entry.index + " (" + first.entry.label + ") and #"
                            + second.entry.index + " (" + second.entry.label + ") overlap");
                }
            }
        }
        if (maxScroll < 0f) {
            problems.add("negative scroll range");
        }
        return problems;
    }

    private static float area(Box box) {
        return Math.max(1f, box.w) * Math.max(1f, box.h);
    }

    private static float overlapArea(Box a, Box b) {
        float w = Math.min(a.right(), b.right()) - Math.max(a.x, b.x);
        float h = Math.min(a.bottom(), b.bottom()) - Math.max(a.y, b.y);
        return w <= 0f || h <= 0f ? 0f : w * h;
    }
}
