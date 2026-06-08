package meowmel.quantumhue.igi;

/**
 * 字体颜色与格式化控件。
 * 使用 Minecraft 原生的 § 格式化码，在 FontRenderer 中自动生效。
 * <p>
 * 用法：
 * <pre>
 * .info(TextColor.RED, "红色文字", TextColor.GREEN, "绿色文字", TextColor.RESET)
 * .info(TextColor.GOLD, TextColor.BOLD, "粗体金色", TextColor.RESET)
 * </pre>
 */
public class TextColor implements IInfoElement {
    // === 颜色 ===
    public static final TextColor BLACK       = new TextColor("\u00a70");
    public static final TextColor DARK_BLUE   = new TextColor("\u00a71");
    public static final TextColor DARK_GREEN  = new TextColor("\u00a72");
    public static final TextColor DARK_AQUA   = new TextColor("\u00a73");
    public static final TextColor DARK_RED    = new TextColor("\u00a74");
    public static final TextColor DARK_PURPLE = new TextColor("\u00a75");
    public static final TextColor GOLD        = new TextColor("\u00a76");
    public static final TextColor GRAY        = new TextColor("\u00a77");
    public static final TextColor DARK_GRAY   = new TextColor("\u00a78");
    public static final TextColor BLUE        = new TextColor("\u00a79");
    public static final TextColor GREEN       = new TextColor("\u00a7a");
    public static final TextColor AQUA        = new TextColor("\u00a7b");
    public static final TextColor RED         = new TextColor("\u00a7c");
    public static final TextColor LIGHT_PURPLE= new TextColor("\u00a7d");
    public static final TextColor YELLOW      = new TextColor("\u00a7e");
    public static final TextColor WHITE       = new TextColor("\u00a7f");

    // === 格式 ===
    /** 随机乱码 */
    public static final TextColor OBFUSCATED    = new TextColor("\u00a7k");
    /** 粗体 */
    public static final TextColor BOLD          = new TextColor("\u00a7l");
    /** 删除线 */
    public static final TextColor STRIKETHROUGH = new TextColor("\u00a7m");
    /** 下划线 */
    public static final TextColor UNDERLINE     = new TextColor("\u00a7n");
    /** 斜体 */
    public static final TextColor ITALIC        = new TextColor("\u00a7o");

    /** 重置所有颜色和格式，恢复默认白色。 */
    public static final TextColor RESET = new TextColor("\u00a7r");

    private final String code;

    private static final java.util.Map<String, TextColor> BY_NAME = new java.util.HashMap<>();

    static {
        for (java.lang.reflect.Field field : TextColor.class.getFields()) {
            if (field.getType() == TextColor.class) {
                try {
                    TextColor tc = (TextColor) field.get(null);
                    BY_NAME.put(field.getName().toLowerCase(java.util.Locale.ENGLISH), tc);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private TextColor(String code) {
        this.code = code;
    }

    /**
     * 根据名称查找颜色常量，不区分大小写。
     * 例如 "red"、"GOLD"、"reset"、"bold"。
     *
     * @return 对应的 TextColor，未找到返回 null
     */
    public static TextColor fromName(String name) {
        if (name == null) return null;
        return BY_NAME.get(name.toLowerCase(java.util.Locale.ENGLISH));
    }

    @Override
    public String getValue() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
