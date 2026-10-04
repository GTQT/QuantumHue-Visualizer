package meowmel.quantumhue.menu;

/**
 * Geometry for the title screen.
 *
 * <p>Deliberately free of Minecraft imports: {@code tools/MenuPreview.java} renders exactly these
 * rectangles outside the game, which is how the layout is checked for collisions across window
 * sizes.  Only geometry is shared — the handful of draw calls differ per backend by design.
 *
 * <p>Pixel coordinates are screen-relative with the origin at the top-left, matching
 * {@code GuiButton.x/y}.
 *
 * <h3>Composition</h3>
 * The panorama is gone.  A Dyson sphere sits with its centre exactly on the left edge, so only its
 * right half is on screen, and the brand lockup is a small mark in the top-right corner.
 */
public final class MenuLayout {

    // ---------------------------------------------------------------- instrument column

    /**
     * The column is right-aligned: the left of the screen now belongs to the Dyson sphere, and the
     * brand lockup already establishes a right-hand rail, so the navigation joins it.
     */
    public static final int COLUMN_RIGHT_MARGIN = 44;
    /** Height of a row. */
    public static final int ROW_H = 26;
    /** Vertical pitch between rows. */
    public static final int PITCH = 34;
    /** Rows in the column. */
    public static final int ROWS = 4;
    /** Gap between two half-width buttons sharing a row. */
    public static final int HALF_GAP = 12;
    /** How far above the column the "// MAIN" caption sits. */
    public static final float CAPTION_LIFT = 20f;

    // ---------------------------------------------------------------- cradle

    /** Inset of the cradle rails from the screen edge. */
    public static final int INSET = 26;
    /** Corner bracket arm length. */
    public static final int BRACKET = 26;

    // ---------------------------------------------------------------- Dyson sphere

    /**
     * Silhouette diameter of the Dyson sphere, as a fraction of the <b>screen height</b>.
     *
     * <p>At {@code 1.0} the sphere is exactly as tall as the window, so it runs edge to edge
     * vertically and the visible right half reaches {@code height / 2} across — which still leaves
     * the right-aligned instrument column clear at every aspect ratio down to 4:3.
     */
    public static final float SPHERE_DIAMETER_RATIO = 1.0f;
    /** Vertical centre of the sphere, as a fraction of height. */
    public static final float SPHERE_CY_RATIO = 0.50f;

    // ---------------------------------------------------------------- brand lockup

    /** Inset of the brand lockup from the top-right corner. */
    public static final int LOGO_INSET = 28;
    /** Emblem edge length, as a fraction of {@code min(width, height)}. */
    public static final float LOGO_EMBLEM_RATIO = 0.085f;
    /** Wordmark height, as a fraction of {@code min(width, height)}. */
    public static final float LOGO_WORDMARK_RATIO = 0.030f;
    /** Gap between the wordmark and the emblem. */
    public static final int LOGO_GAP = 16;

    /** Aspect of gtqt_title.png — four glyph cells laid out horizontally. */
    public static final float WORDMARK_ASPECT = 4f;

    private MenuLayout() {}

    // ---------------------------------------------------------------- helpers

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int minDim(int screenWidth, int screenHeight) {
        return Math.min(screenWidth, screenHeight);
    }

    // ---------------------------------------------------------------- instrument column

    public static int columnWidth(int screenWidth) {
        return clamp((int) (screenWidth * 0.28f), 176, 320);
    }

    /** Left edge of the right-aligned instrument column. */
    public static int columnX(int screenWidth) {
        return screenWidth - COLUMN_RIGHT_MARGIN - columnWidth(screenWidth);
    }

    public static int halfWidth(int screenWidth) {
        return (columnWidth(screenWidth) - HALF_GAP) / 2;
    }

    public static int rightColumnX(int screenWidth) {
        return columnX(screenWidth) + halfWidth(screenWidth) + HALF_GAP;
    }

    public static int columnTop(int screenHeight) {
        return screenHeight / 2 - (ROWS * PITCH) / 2 + 4;
    }

    public static int columnBottom(int screenHeight) {
        return columnTop(screenHeight) + ROWS * PITCH - (PITCH - ROW_H);
    }

    public static int rowY(int screenHeight, int row) {
        return columnTop(screenHeight) + row * PITCH;
    }

    public static int languageX() {
        return INSET;
    }

    public static int languageY(int screenHeight) {
        return screenHeight - 54;
    }    // ---------------------------------------------------------------- Dyson sphere

    /** Silhouette radius of the swarm, in pixels: half the scaled screen height. */
    public static float sphereRadius(int screenHeight) {
        return screenHeight * SPHERE_DIAMETER_RATIO * 0.5f;
    }

    /** The centre sits on the left edge, so exactly the right half is visible. */
    public static float sphereCx() {
        return 0f;
    }

    public static float sphereCy(int screenHeight) {
        return screenHeight * SPHERE_CY_RATIO;
    }

    // ---------------------------------------------------------------- brand lockup

    public static int emblemSize(int screenWidth, int screenHeight) {
        return Math.round(minDim(screenWidth, screenHeight) * LOGO_EMBLEM_RATIO);
    }

    public static int wordmarkHeight(int screenWidth, int screenHeight) {
        return Math.round(minDim(screenWidth, screenHeight) * LOGO_WORDMARK_RATIO);
    }

    public static int wordmarkWidth(int screenWidth, int screenHeight) {
        return Math.round(wordmarkHeight(screenWidth, screenHeight) * WORDMARK_ASPECT);
    }

    /** Top edge of the lockup. */
    public static int lockupTop() {
        return LOGO_INSET;
    }

    /** Left edge of the emblem square. */
    public static int emblemX(int screenWidth, int screenHeight) {
        return screenWidth - LOGO_INSET - emblemSize(screenWidth, screenHeight);
    }

    /** Left edge of the wordmark, which sits to the left of the emblem. */
    public static int wordmarkX(int screenWidth, int screenHeight) {
        return emblemX(screenWidth, screenHeight) - LOGO_GAP - wordmarkWidth(screenWidth, screenHeight);
    }

    /** Top edge of the wordmark, vertically centred on the emblem. */
    public static int wordmarkY(int screenWidth, int screenHeight) {
        return LOGO_INSET
                + (emblemSize(screenWidth, screenHeight) - wordmarkHeight(screenWidth, screenHeight)) / 2;
    }

    // ---------------------------------------------------------------- vanilla button ids

    /** Row of a vanilla main-menu button id, or -1 for the language picker. */
    public static int rowOf(int id) {
        switch (id) {
            case 1: return 0;
            case 2: return 1;
            case 6:
            case 14: return 2;
            case 0:
            case 4: return 3;
            default: return -1;
        }
    }

    /** True for the buttons that share their row with another button. */
    public static boolean isHalf(int id) {
        return id == 6 || id == 14 || id == 0 || id == 4;
    }

    /** True for the button placed on the right half of a shared row. */
    public static boolean isRightHalf(int id) {
        return id == 14 || id == 4;
    }

    /** Resolved rectangle of a main-menu button: {@code {x, y, width, height}}. */
    public static int[] buttonRect(int id, int screenWidth, int screenHeight) {
        int row = rowOf(id);
        if (row < 0) {
            return new int[] {languageX(), languageY(screenHeight), 20, 20};
        }
        return new int[] {
                isRightHalf(id) ? rightColumnX(screenWidth) : columnX(screenWidth),
                rowY(screenHeight, row),
                isHalf(id) ? halfWidth(screenWidth) : columnWidth(screenWidth),
                ROW_H,
        };
    }
}
