package meowmel.quantumhue.chat;

/**
 * 聊天系统颜色常量 — 匹配 Wiki 深色主题 + 蓝色强调风格
 */
public final class ChatColors {

    private ChatColors() {}

    // 底色
    public static final int PANEL_BG       = 0xFF0E0E16;
    public static final int SIDEBAR_BG     = 0xFF121220;
    public static final int HEADER_BG      = 0xFF101018;
    public static final int INPUT_BG       = 0xFF0C0C18;
    public static final int BAR_BG         = 0xFF101018;

    // 分割线
    public static final int DIVIDER        = 0xFF2A2A44;
    public static final int SIDEBAR_DIVIDER = 0xFF2A2A44;

    // 强调色
    public static final int ACCENT         = 0xFF6688CC;
    public static final int ACCENT_DIM     = 0xFF3A5088;

    // 文字
    public static final int TEXT_PRIMARY   = 0xFFDDDDDD;
    public static final int TEXT_SECONDARY = 0xFF999999;
    public static final int TEXT_HEADER    = 0xFF88AADD;

    // 侧边栏
    public static final int SIDEBAR_HOVER  = 0xFF1C1C34;
    public static final int SIDEBAR_SEL    = 0xFF24244A;
    public static final int SIDEBAR_CAT    = 0xFF161628;

    // 气泡 (自己 / 他人)
    public static final int OWN_BUBBLE     = 0xFF181830;
    public static final int OTHER_BUBBLE   = 0xFF0E0E22;
    public static final int BUBBLE_TEXT    = 0xFFDDDDDD;
    public static final int NAME_TEXT      = 0xFF88AADD;
    public static final int TIME_TEXT      = 0xFF556688;

    // 通知
    public static final int NOTIF_BG       = 0xDD161630;
    public static final int NOTIF_BORDER   = 0xFF6688CC;
    public static final int NOTIF_TEXT     = 0xFF6688CC;
    public static final int RED_DOT        = 0xFFFF4444;

    // 滚动条
    public static final int SCROLLBAR_BG   = 0xFF111122;
    public static final int SCROLLBAR_FG   = 0xFF444466;

    // 输入区
    public static final int INPUT_BORDER   = 0xFF333355;
    public static final int SEND_HOVER     = 0xFF1C1C34;

    // 右键菜单
    public static final int CONTEXT_BG     = 0xDD0E0E16;
    public static final int CONTEXT_HOVER  = 0xFF1C1C34;

    // 防刷屏计数
    public static final int DUPLICATE_TAG  = 0xFF7799CC;
}
