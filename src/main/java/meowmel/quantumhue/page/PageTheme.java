package meowmel.quantumhue.page;

/**
 * 公告页与商店页共用的配色与版式。
 *
 * <p>两个页面以前各自维护一套颜色：公告用一套青蓝、商店用另一套相近但不相同的蓝。统一到这里以后，
 * 边框、标题、页签、滚动条、按钮、输入框只有一份定义，改主题不用再翻两个 900 行的界面类。
 *
 * <p>强调色不再写死：{@link #apply(int)} 会把 {@link PageThemeSettings} 里的一个颜色换算成整套界面色
 * （边框、发光、页签、滚动条、按钮、输入框、设置键），换配色只需要换那一个值。留在常量里的只有
 * 深色底和红/橙/绿的语义色——那些表达的是状态（关闭、出售、警告、成功），不该跟着主题走。
 */
public final class PageTheme {

    private PageTheme() {}

    // ================================================================ 版式

    /** 页面四周留白。 */
    public static final int MARGIN = 10;
    /** 面板之间的留白。 */
    public static final int PAD = 10;
    /** 面板内部的小间距。 */
    public static final int GAP = 8;
    /** 标题栏高度。 */
    public static final int HEADER_H = 28;
    /** 右侧页签列宽度。 */
    public static final int SIDE_COL_W = 120;
    public static final int TAB_H = 28;
    public static final int TAB_GAP = 3;
    /** 滚动条宽度。 */
    public static final int SCROLL_W = 5;
    /** 关闭键尺寸。 */
    public static final int CLOSE_SIZE = 14;

    // ================================================================ 固定的底色与文字

    public static final int DIM = 0x66000000;
    public static final int BG_TOP = 0xF00D141F;
    public static final int BG_BOT = 0xF00A1017;
    public static final int CONTENT_BG = 0x30000000;

    public static final int TITLE = 0xFFE9FBFF;
    public static final int TEXT = 0xFFCFE4EF;
    public static final int TEXT_DIM = 0xFF93AFC0;
    public static final int TEXT_FAINT = 0xFF6E8796;

    // ================================================================ 强调色（{@link #apply(int)} 重算）

    /** 强调色本身，页签指示条、输入框焦点、滚动条拖动块都用它。 */
    public static int ACCENT = 0xFF00E5FF;
    /** 窗口外发光。 */
    public static int GLOW = 0x1A00E5FF;
    /** 窗口描边。 */
    public static int BORDER = 0x6638D6FF;
    /** 窗口顶部的亮线。 */
    public static int TOP_HIGHLIGHT = 0x59BCEFFF;
    /** 标题栏分隔线、激活页签的顶线。 */
    public static int HEADER_LINE = 0x8000E5FF;
    /** 四角括号。 */
    public static int CORNER = 0x8000E5FF;
    /** 细分隔线。 */
    public static int SEPARATOR = 0x3D00E5FF;

    public static int SCROLL_TRACK = 0x2000E5FF;
    public static int SCROLL_THUMB = 0xCC38D6FF;
    public static int SCROLL_HOVER = 0xFF7CF3FF;
    public static int SCROLL_DRAG = 0xFF00E5FF;

    /** 关闭键固定用红：它是“危险操作”，不属于配色。 */
    public static final int CLOSE_BG = 0x60FF5C6E;
    public static final int CLOSE_HOVER = 0xFFFF5C6E;
    public static final int CLOSE_PRESS = 0xFFC9455A;

    /** 关闭键左侧的设置键。 */
    public static int SETTINGS_BG = 0x4000E5FF;
    public static int SETTINGS_HOVER = 0x9E00E5FF;
    public static int SETTINGS_PRESS = 0xD000E5FF;

    public static int TAB_BG = 0x50132030;
    public static int TAB_HOVER = 0x80203040;
    public static int TAB_ACTIVE = 0xFF1B3A4A;

    public static final int ITEM_BG = 0xB00D141F;
    public static int ITEM_HOVER = 0xD0162634;
    public static int ITEM_SELECTED = 0xE01B3A4A;
    public static int ITEM_DIVIDER = 0x33163A4A;

    public static final int FIELD_BG = 0xFF0A1017;
    public static int FIELD_BORDER = 0x6638D6FF;
    public static int FIELD_FOCUS = 0xFF00E5FF;

    public static final int ACTION_BG = 0xC00D141F;
    public static int BTN_PRIMARY = 0xFF1E5A6E;
    public static int BTN_PRIMARY_HOVER = 0xFF2C7E96;
    /** 出售 / 警告 / 交替 / 成功四组按钮是语义色，不随主题变。 */
    public static final int BTN_SELL = 0xFF7A2F3E;
    public static final int BTN_SELL_HOVER = 0xFFA03D50;
    public static final int BTN_WARN = 0xFF7A4A2F;
    public static final int BTN_WARN_HOVER = 0xFFA06438;
    public static final int BTN_ALT = 0xFF3E2F5A;
    public static final int BTN_ALT_HOVER = 0xFF5A4480;
    public static final int BTN_GOOD = 0xFF226633;
    public static final int BTN_GOOD_HOVER = 0xFF338844;

    /** 物品浮层的描边（底色仍是固定的深色板）。 */
    public static int TOOLTIP_BORDER = 0xFF4E7A8A;

    /** 页签激活时左侧指示条的颜色（按顺序取）。分类色各自代表一类内容，不随主题改。 */
    public static final int[] TAB_ACCENTS = {
            0xFF55FFFF, 0xFF55AAFF, 0xFFFF6666, 0xFFFFBB33, 0xFF55FFAA, 0xFFCC88FF
    };

    public static int accent(int index) {
        return TAB_ACCENTS[Math.floorMod(index, TAB_ACCENTS.length)];
    }

    // ================================================================ 换色

    /**
     * 按强调色重算整套界面色。
     *
     * <p>带透明度的色只换 RGB；页签、条目、按钮这种有实底的部分，则把强调色按比例混进深色底
     * （{@code BG_TOP}），于是换成暖色时它们会一起变暖，而不是只剩边框在变色。
     */
    public static void apply(int accent) {
        int base = accent & 0xFFFFFF;
        int bg = BG_TOP & 0xFFFFFF;

        ACCENT = opaque(base);
        GLOW = alpha(0x1A, base);
        BORDER = alpha(0x66, base);
        TOP_HIGHLIGHT = alpha(0x59, lighten(base, 0.55F));
        HEADER_LINE = alpha(0x80, base);
        CORNER = alpha(0x80, base);
        SEPARATOR = alpha(0x3D, base);

        SCROLL_TRACK = alpha(0x20, base);
        SCROLL_THUMB = alpha(0xCC, lighten(base, 0.12F));
        SCROLL_HOVER = alpha(0xFF, lighten(base, 0.42F));
        SCROLL_DRAG = ACCENT;

        SETTINGS_BG = alpha(0x40, base);
        SETTINGS_HOVER = alpha(0x9E, base);
        SETTINGS_PRESS = alpha(0xD0, base);

        TAB_BG = alpha(0x50, mix(bg, base, 0.10F));
        TAB_HOVER = alpha(0x80, mix(bg, base, 0.16F));
        TAB_ACTIVE = alpha(0xFF, mix(bg, base, 0.26F));

        ITEM_HOVER = alpha(0xD0, mix(bg, base, 0.14F));
        ITEM_SELECTED = alpha(0xE0, mix(bg, base, 0.26F));
        ITEM_DIVIDER = alpha(0x33, mix(bg, base, 0.45F));

        FIELD_BORDER = alpha(0x66, lighten(base, 0.12F));
        FIELD_FOCUS = ACCENT;

        BTN_PRIMARY = alpha(0xFF, mix(bg, base, 0.42F));
        BTN_PRIMARY_HOVER = alpha(0xFF, mix(bg, base, 0.58F));

        TOOLTIP_BORDER = alpha(0xFF, mix(bg, base, 0.32F));
    }

    static {
        // 读盘失败、目录不可写都只会回退到默认色，界面不受影响
        apply(PageThemeSettings.get()
                .accentColor());
    }

    // ================================================================ 小工具

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb & 0xFFFFFF;
    }

    private static int alpha(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    /** {@code amount}=0 取 {@code from}，=1 取 {@code to}；只混合 RGB。 */
    private static int mix(int from, int to, float amount) {
        float t = Math.max(0F, Math.min(1F, amount));
        int r = Math.round(red(from) + (red(to) - red(from)) * t);
        int g = Math.round(green(from) + (green(to) - green(from)) * t);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * t);
        return clampChannel(r) << 16 | clampChannel(g) << 8 | clampChannel(b);
    }

    private static int lighten(int rgb, float amount) {
        return mix(rgb, 0xFFFFFF, amount);
    }

    private static int red(int rgb) {
        return rgb >> 16 & 0xFF;
    }

    private static int green(int rgb) {
        return rgb >> 8 & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
