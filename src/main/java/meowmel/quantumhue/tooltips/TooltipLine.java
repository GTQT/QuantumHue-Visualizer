package meowmel.quantumhue.tooltips;

/**
 * Tooltip 行组件——轻量级组件化行渲染。
 * 每行携带类型标记，渲染时由各自处理，避免字符串匹配判断行类型。
 */
public class TooltipLine {

    public enum Type {
        /** 普通文本行 */
        TEXT,
        /** 空行（Thaumcraft 要素占位） */
        ASPECT_SPACER,
        /** AppleCore 食物信息 */
        FOOD_INFO
    }

    public final String text;
    public final Type type;

    private TooltipLine(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public static TooltipLine text(String text) {
        return new TooltipLine(text, Type.TEXT);
    }

    public static TooltipLine aspectSpacer() {
        return new TooltipLine("    ", Type.ASPECT_SPACER);
    }

    public static TooltipLine foodInfo() {
        return new TooltipLine("", Type.FOOD_INFO);
    }

    /** 检测是否为 Thaumcraft 要素占位行（4 空格） */
    public static boolean isAspectSpacer(String line) {
        String stripped = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(line);
        return stripped != null && stripped.trim().isEmpty() && line.contains("    ");
    }
}
