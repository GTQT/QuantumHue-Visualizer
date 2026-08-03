package meowmel.quantumhue.chat;

import meowmel.quantumhue.QuantumHueConfig;

/**
 * 聊天系统颜色 — 从 Config 读取主色，衍生色自动计算
 *
 * 通过 {@link #reload()} 刷新（Config 变更后调用）。
 * 配色围绕 7 个主色参数，其余 ~25 个颜色由混合/明暗计算得出。
 */
public final class ChatColors {

    private ChatColors() {}

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

    static { reload(); }

    /** 从 Config 重算全部颜色 */
    public static void reload() {
        QuantumHueConfig.ChatConfig cfg = QuantumHueConfig.chat;

        int accent  = parse(cfg.accentColor);
        int bg      = parse(cfg.bgColor);
        int ownBub  = parse(cfg.ownBubbleColor);
        int otherBub= parse(cfg.otherBubbleColor);
        int txtL    = parse(cfg.textLight);
        int txtD    = parse(cfg.textDim);

        PANEL_BG       = bg;
        SIDEBAR_BG     = lighten(bg, 0.08f);
        HEADER_BG      = bg;
        INPUT_BG       = darken(bg, 0.05f);
        BAR_BG         = bg;

        DIVIDER        = mix(accent, bg, 0.25f);
        SIDEBAR_DIVIDER = DIVIDER;

        ACCENT         = accent;
        ACCENT_DIM     = darken(accent, 0.30f);

        TEXT_PRIMARY   = txtL;
        TEXT_SECONDARY = txtD;
        TEXT_HEADER    = mix(accent, txtL, 0.50f);

        SIDEBAR_HOVER  = lighten(bg, 0.20f);
        SIDEBAR_SEL    = mix(accent, bg, 0.25f);
        SIDEBAR_CAT    = lighten(bg, 0.05f);

        OWN_BUBBLE     = ownBub;
        OTHER_BUBBLE   = otherBub;
        BUBBLE_TEXT    = txtL;
        NAME_TEXT      = mix(accent, txtL, 0.40f);
        TIME_TEXT      = mix(accent, txtL, 0.25f);

        NOTIF_BG       = ownBub;
        NOTIF_BORDER   = accent;
        NOTIF_TEXT     = accent;

        RED_DOT        = 0xFFFF4444;

        SCROLLBAR_BG   = darken(SIDEBAR_BG, 0.10f);
        SCROLLBAR_FG   = lighten(SIDEBAR_BG, 0.60f);

        INPUT_BORDER   = mix(accent, bg, 0.12f);
        SEND_HOVER     = SIDEBAR_HOVER;

        CONTEXT_BG     = lighten(bg, 0.05f);
        CONTEXT_HOVER  = SIDEBAR_HOVER;

        DUPLICATE_TAG  = lighten(accent, 0.10f);
    }

    // ===== 颜色工具 =====

    private static int parse(String hex) {
        try { return 0xFF000000 | Integer.parseInt(hex.trim(), 16); }
        catch (Exception e) { return 0xFF000000; }
    }

    private static int darken(int c, float a) { return mix(c, 0xFF000000, a); }
    private static int lighten(int c, float a) { return mix(c, 0xFFFFFFFF, a); }

    /** 按 ratio 混合两个 RGB 颜色 (0=纯a, 1=纯b)，Alpha 保持 FF */
    private static int mix(int a, int b, float ratio) {
        float inv = 1f - ratio;
        int r = clamp((int)(((a >> 16) & 0xFF) * inv + ((b >> 16) & 0xFF) * ratio));
        int g = clamp((int)(((a >> 8)  & 0xFF) * inv + ((b >> 8)  & 0xFF) * ratio));
        int bl= clamp((int)(( a        & 0xFF) * inv + ( b        & 0xFF) * ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
}
