package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.text.TooltipTextLine;

/**
 * Tooltip 行组件——轻量级组件化行渲染。
 * 每行携带类型标记与行内特效区间，渲染时由各自处理，避免字符串匹配判断行类型。
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
    /** 原始行与特效区间；ASPECT_SPACER/FOOD_INFO 使用纯文本占位。 */
    public final TooltipTextLine styled;

    private TooltipLine(String text, Type type, TooltipTextLine styled) {
        this.text = text;
        this.type = type;
        this.styled = styled;
    }

    public static TooltipLine text(String text) {
        return new TooltipLine(text, Type.TEXT, TooltipTextLine.plain(text));
    }

    public static TooltipLine text(TooltipTextLine line) {
        return new TooltipLine(line.text, Type.TEXT, line);
    }

    public static TooltipLine aspectSpacer() {
        return new TooltipLine("    ", Type.ASPECT_SPACER, TooltipTextLine.plain("    "));
    }

    public static TooltipLine foodInfo() {
        return new TooltipLine("", Type.FOOD_INFO, TooltipTextLine.plain(""));
    }

    /** 检测是否为 Thaumcraft 要素占位行（4 空格） */
    public static boolean isAspectSpacer(String line) {
        String stripped = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(line);
        return stripped != null && stripped.trim().isEmpty() && line.contains("    ");
    }
}
