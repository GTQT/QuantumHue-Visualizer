package meowmel.quantumhue.chat;

/**
 * 聊天系统颜色 — 六个主色来自玩家偏好（{@link ChatThemeSettings}），其余衍生色现场计算。
 *
 * <p>通过 {@link #reload()} 刷新：聊天设置面板改一个色、切一套预设、重置，都会调用它，
 * 因为绘制时直接读这里的静态字段，所以不需要重开界面，改完立刻就能看到。
 *
 * <p>衍生方向按底色亮度自动翻面：暗色主题“更亮 = 更明显”，亮色主题反过来。
 * 白昼预设那种浅底也能得到可读的悬停、选中和滚动条颜色。
 */
public final class ChatColors {

    private ChatColors() {}

    /** 当前底色是否偏亮；亮色主题下部分衍生色反向计算。 */
    public static boolean LIGHT;

    // ===== 缓存（由 reload() 计算） =====

    // 底色
    public static int PANEL_BG, SIDEBAR_BG, HEADER_BG, INPUT_BG, BAR_BG;
    // 分割线
    public static int DIVIDER, SIDEBAR_DIVIDER;
    // 强调
    public static int ACCENT, ACCENT_DIM;
    // 文字
    public static int TEXT_PRIMARY, TEXT_SECONDARY, TEXT_HEADER;
    // 侧边栏
    public static int SIDEBAR_HOVER, SIDEBAR_SEL, SIDEBAR_CAT;
    // 气泡
    public static int OWN_BUBBLE, OTHER_BUBBLE, BUBBLE_TEXT, NAME_TEXT, TIME_TEXT;
    public static int BUBBLE_BORDER_OWN, BUBBLE_BORDER_OTHER;
    // 通知
    public static int NOTIF_BG, NOTIF_BORDER, NOTIF_TEXT, RED_DOT;
    // 滚动条
    public static int SCROLLBAR_BG, SCROLLBAR_FG;
    // 输入区
    public static int INPUT_BORDER, SEND_HOVER;
    // 右键菜单
    public static int CONTEXT_BG, CONTEXT_HOVER;
    // 刷屏计数
    public static int DUPLICATE_TAG;
    // 时间分隔条 / 面板边缘 / 草稿提醒
    public static int TIME_SEP_BG, PANEL_EDGE, SEARCH_MARK, MENTION_MARK;

    static { reload(); }

    /** 按当前偏好重算全部颜色。 */
    public static void reload() {
        ChatThemeSettings settings = ChatThemeSettings.get();

        int accent = 0xFF000000 | settings.accentColor();
        int bg = 0xFF000000 | settings.bgColor();
        int ownBub = 0xFF000000 | settings.ownBubbleColor();
        int otherBub = 0xFF000000 | settings.otherBubbleColor();
        int txtL = 0xFF000000 | settings.textLightColor();
        int txtD = 0xFF000000 | settings.textDimColor();

        boolean light = ChatRender.luminance(bg) > 0.55f;
        LIGHT = light;
        // 暗色主题往亮里走更明显，亮色主题往暗里走
        float d = light ? -1f : 1f;

        PANEL_BG = bg;
        SIDEBAR_BG = shift(bg, 0.055f * d);
        HEADER_BG = bg;
        INPUT_BG = shift(bg, -0.05f * d);
        BAR_BG = bg;

        DIVIDER = mix(accent, bg, light ? 0.40f : 0.28f);
        SIDEBAR_DIVIDER = DIVIDER;

        ACCENT = accent;
        ACCENT_DIM = darken(accent, light ? 0.22f : 0.34f);

        TEXT_PRIMARY = txtL;
        TEXT_SECONDARY = txtD;
        TEXT_HEADER = mix(accent, txtL, 0.50f);

        SIDEBAR_HOVER = shift(bg, 0.14f * d);
        SIDEBAR_SEL = mix(accent, bg, light ? 0.20f : 0.25f);
        SIDEBAR_CAT = shift(bg, 0.04f * d);

        OWN_BUBBLE = ownBub;
        OTHER_BUBBLE = otherBub;
        BUBBLE_TEXT = txtL;
        BUBBLE_BORDER_OWN = mix(ownBub, accent, 0.30f);
        BUBBLE_BORDER_OTHER = mix(otherBub, accent, 0.18f);
        NAME_TEXT = mix(accent, txtL, 0.42f);
        TIME_TEXT = mix(accent, txtL, 0.22f);

        NOTIF_BG = ownBub;
        NOTIF_BORDER = accent;
        NOTIF_TEXT = accent;

        RED_DOT = 0xFFFF4444;

        SCROLLBAR_BG = darken(SIDEBAR_BG, light ? 0.05f : 0.10f);
        SCROLLBAR_FG = mix(accent, txtL, 0.55f);

        INPUT_BORDER = mix(accent, bg, 0.15f);
        SEND_HOVER = SIDEBAR_HOVER;

        CONTEXT_BG = shift(bg, 0.06f * d);
        CONTEXT_HOVER = SIDEBAR_HOVER;

        DUPLICATE_TAG = mix(accent, txtD, 0.45f);

        TIME_SEP_BG = withAlpha(mix(bg, accent, 0.12f), 0x55);
        PANEL_EDGE = mix(accent, bg, 0.50f);
        SEARCH_MARK = light ? darken(accent, 0.10f) : lighten(accent, 0.10f);
        MENTION_MARK = mix(accent, txtL, 0.20f);
    }

    // ===== 颜色工具 =====

    /** amount > 0 变亮，amount < 0 变暗。 */
    private static int shift(int color, float amount) {
        return amount >= 0 ? lighten(color, amount) : darken(color, -amount);
    }

    private static int darken(int c, float a) { return mix(c, 0xFF000000, a); }

    private static int lighten(int c, float a) { return mix(c, 0xFFFFFFFF, a); }

    /** 按 ratio 混合两个 RGB 颜色 (0=纯a, 1=纯b)，Alpha 保持 FF。 */
    public static int mix(int a, int b, float ratio) {
        float inv = 1f - ratio;
        int r = clamp((int) (((a >> 16) & 0xFF) * inv + ((b >> 16) & 0xFF) * ratio));
        int g = clamp((int) (((a >> 8) & 0xFF) * inv + ((b >> 8) & 0xFF) * ratio));
        int bl = clamp((int) ((a & 0xFF) * inv + (b & 0xFF) * ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 换掉 alpha 通道。 */
    public static int withAlpha(int color, int alpha) {
        return ChatRender.withAlpha(color, alpha);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
}
