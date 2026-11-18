package meowmel.quantumhue.tooltips.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 解析 Tooltip 行内的触发标签。
 *
 * <p>标签本身不参与显示与换行，只负责给后面的文字打上特效位：
 * <pre>
 *   [效果]警告文本[/效果]      发光 + 高亮条 + 故障
 *   [发光]柔和文本[/发光]
 *   [高亮]重点文本[/高亮]
 *   [故障]乱码文本[/故障]
 * </pre>
 * 省略结束标签时，效果持续到行尾；英文别名 {@code effect/glow/highlight/glitch}
 * 等价。未知的方括号内容（例如 {@code [Shift]}）按普通文本保留。</p>
 */
public final class TooltipTextMarkup {

    private TooltipTextMarkup() {
    }

    public static TooltipTextLine parse(String raw) {
        if (raw == null || raw.isEmpty()) return TooltipTextLine.plain("");

        StringBuilder out = new StringBuilder(raw.length());
        List<TooltipTextLine.Span> spans = new ArrayList<>(2);
        int plain = 0;
        int flags = 0;
        int spanStart = 0;

        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);

            if (c == '[') {
                int close = raw.indexOf(']', i + 1);
                if (close > i + 1) {
                    Integer delta = resolveTag(raw.substring(i + 1, close));
                    if (delta != null) {
                        addSpan(spans, spanStart, plain, flags);
                        flags = delta > 0 ? flags | delta : flags & ~(-delta);
                        spanStart = plain;
                        i = close + 1;
                        continue;
                    }
                }
            }

            if (c == '\u00a7' && i + 1 < raw.length()) {
                out.append(c).append(raw.charAt(i + 1));
                i += 2;
                continue;
            }

            out.append(c);
            plain++;
            i++;
        }

        addSpan(spans, spanStart, plain, flags);
        return TooltipTextLine.of(out.toString(), spans);
    }

    /** 去掉 § 格式码后的可见字符数。 */
    public static int visibleLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                i++;
            } else {
                count++;
            }
        }
        return count;
    }

    /** 去掉 § 格式码后的可见文本。 */
    public static String plainText(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void addSpan(List<TooltipTextLine.Span> spans, int start, int end, int flags) {
        if (flags != 0 && end > start) {
            spans.add(new TooltipTextLine.Span(start, end, flags));
        }
    }

    /**
     * @return 正数表示打开对应特效位，负数表示关闭；不是合法标签时返回 null
     */
    private static Integer resolveTag(String token) {
        String key = token.trim().toLowerCase(Locale.ROOT);
        boolean close = key.startsWith("/");
        if (close) key = key.substring(1).trim();

        int mask;
        switch (key) {
            case "效果":
            case "特效":
            case "effect":
            case "fx":
                mask = TooltipTextLine.FX_ALL;
                break;
            case "发光":
            case "glow":
                mask = TooltipTextLine.FX_GLOW;
                break;
            case "高亮":
            case "highlight":
            case "hl":
                mask = TooltipTextLine.FX_HIGHLIGHT;
                break;
            case "故障":
            case "乱码":
            case "glitch":
                mask = TooltipTextLine.FX_GLITCH;
                break;
            default:
                return null;
        }
        return close ? -mask : mask;
    }
}
