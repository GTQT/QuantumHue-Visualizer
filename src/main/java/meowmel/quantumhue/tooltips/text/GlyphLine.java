package meowmel.quantumhue.tooltips.text;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 一行 Tooltip 文字解析后的字形序列。
 *
 * <p>与屏幕坐标无关，只记录相对行首的像素偏移与样式，因此可以跨帧缓存。
 * 支持 {@code §} 格式码、粗体额外 1px 宽度、中文全角宽度与代理对字符。</p>
 */
public final class GlyphLine {

    /** 单个字形。 */
    public static final class Glyph {
        /** 绘制用文本（一个 BMP 字符，或一对代理）。 */
        public final String text;
        /** 相对行首的 x 偏移。 */
        public final int offsetX;
        /** 前进宽度（已包含粗体 +1）。 */
        public final int width;
        /** 解析后的颜色 0xRRGGBB。 */
        public final int color;
        /** 在去格式文本（plain）中的起始下标。 */
        public final int plainIndex;
        /** 在原字符串中的结束下标（不含）。 */
        public final int sourceEnd;
        /** 该字形生效的样式码，如 "§l§o"，没有样式时为空串。 */
        public final String stylePrefix;

        private Glyph(String text, int offsetX, int width, int color,
                      int plainIndex, int sourceEnd, String stylePrefix) {
            this.text = text;
            this.offsetX = offsetX;
            this.width = width;
            this.color = color;
            this.plainIndex = plainIndex;
            this.sourceEnd = sourceEnd;
            this.stylePrefix = stylePrefix;
        }
    }

    /** 原字符串（含格式码）。 */
    public final String source;
    /** 去掉格式码后的纯文本。 */
    public final String plain;
    public final List<Glyph> glyphs;
    /** 按字形宽度累加出的整行宽度。 */
    public final int width;

    private GlyphLine(String source, String plain, List<Glyph> glyphs, int width) {
        this.source = source;
        this.plain = plain;
        this.glyphs = glyphs;
        this.width = width;
    }

    public static GlyphLine parse(String text, FontRenderer font) {
        String source = text == null ? "" : text;
        List<Glyph> glyphs = new ArrayList<>(Math.min(128, Math.max(8, source.length())));
        StringBuilder plain = new StringBuilder(source.length());

        int x = 0;
        int plainIndex = 0;
        int color = 0xFFFFFF;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;

        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);

            if (c == '\u00a7' && i + 1 < source.length()) {
                char code = Character.toLowerCase(source.charAt(i + 1));
                if (code >= '0' && code <= '9') {
                    color = LEGACY_COLORS[code - '0'];
                } else if (code >= 'a' && code <= 'f') {
                    color = LEGACY_COLORS[10 + code - 'a'];
                } else if (code == 'l') {
                    bold = true;
                } else if (code == 'o') {
                    italic = true;
                } else if (code == 'n') {
                    underline = true;
                } else if (code == 'm') {
                    strike = true;
                } else if (code == 'k') {
                    obfuscated = true;
                } else if (code == 'r') {
                    color = 0xFFFFFF;
                    bold = italic = underline = strike = obfuscated = false;
                }
                i += 2;
                continue;
            }

            String glyphText;
            int charWidth;
            if (Character.isHighSurrogate(c) && i + 1 < source.length()
                    && Character.isLowSurrogate(source.charAt(i + 1))) {
                glyphText = source.substring(i, i + 2);
                charWidth = 9;
                i += 2;
            } else {
                glyphText = String.valueOf(c);
                charWidth = font.getCharWidth(c);
                i++;
            }
            if (charWidth < 0) charWidth = 0;
            if (bold) charWidth++;

            String prefix = stylePrefix(bold, italic, underline, strike, obfuscated);
            glyphs.add(new Glyph(glyphText, x, charWidth, color, plainIndex, i, prefix));
            plain.append(glyphText);
            plainIndex += glyphText.length();
            x += charWidth;
        }

        return new GlyphLine(source, plain.toString(), glyphs, x);
    }

    /** 前 count 个字形对应的去格式文本（用于发光叠绘）。 */
    public String plainPrefix(int count) {
        if (count <= 0 || glyphs.isEmpty()) return "";
        if (count >= glyphs.size()) return plain;
        Glyph last = glyphs.get(count - 1);
        int end = last.plainIndex + last.text.length();
        if (end >= plain.length()) return plain;
        return plain.substring(0, end);
    }

    /** 前 count 个字形对应的原始文本（保留格式码，用于正文绘制）。 */
    public String sourcePrefix(int count) {
        if (count <= 0 || glyphs.isEmpty()) return "";
        if (count >= glyphs.size()) return source;
        return source.substring(0, glyphs.get(count - 1).sourceEnd);
    }

    /** plain 下标 [start, end) 对应的原始文本（保留格式码）。 */
    public String sourceRange(int start, int end) {
        if (glyphs.isEmpty() || end <= start) return "";
        int first = glyphIndexForPlain(start);
        if (first >= glyphs.size()) return "";
        int last = glyphIndexForPlain(end - 1);
        if (last >= glyphs.size()) last = glyphs.size() - 1;
        int sourceStart = first == 0 ? 0 : glyphs.get(first - 1).sourceEnd;
        int sourceEnd = glyphs.get(last).sourceEnd;
        if (sourceEnd <= sourceStart) return "";
        return source.substring(sourceStart, sourceEnd);
    }

    /** plain 下标 [start, end) 对应的去格式文本（用于发光/残影着色）。 */
    public String plainRange(int start, int end) {
        if (glyphs.isEmpty() || end <= start) return "";
        int first = glyphIndexForPlain(start);
        if (first >= glyphs.size()) return "";
        int last = glyphIndexForPlain(end - 1);
        if (last >= glyphs.size()) last = glyphs.size() - 1;
        int from = glyphs.get(first).plainIndex;
        int to = glyphs.get(last).plainIndex + glyphs.get(last).text.length();
        if (to <= from) return "";
        return plain.substring(from, Math.min(to, plain.length()));
    }

    /** plain 下标对应字形的行内 x 偏移。 */
    public int offsetXForPlain(int plainIndex) {
        int index = glyphIndexForPlain(plainIndex);
        if (index >= glyphs.size()) return width;
        return glyphs.get(index).offsetX;
    }

    /** 找到第一个 plain 下标 >= plainIndex 的字形下标。 */
    public int glyphIndexForPlain(int plainIndex) {
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph g = glyphs.get(i);
            if (g.plainIndex + g.text.length() > plainIndex) return i;
        }
        return glyphs.size();
    }

    private static String stylePrefix(boolean bold, boolean italic, boolean underline,
                                      boolean strike, boolean obfuscated) {
        if (!bold && !italic && !underline && !strike && !obfuscated) return "";
        StringBuilder sb = new StringBuilder(10);
        if (obfuscated) sb.append('\u00a7').append('k');
        if (bold) sb.append('\u00a7').append('l');
        if (strike) sb.append('\u00a7').append('m');
        if (underline) sb.append('\u00a7').append('n');
        if (italic) sb.append('\u00a7').append('o');
        return sb.toString();
    }

    private static final int[] LEGACY_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
}
