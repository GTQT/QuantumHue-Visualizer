package meowmel.quantumhue.wiki;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meowmel.quantumhue.wiki.WikiRenderTypes.*;

/**
 * 两阶段 Markdown 解析器
 *
 * 第一阶段：块级解析 — 识别代码块、表格、块引用、标题、列表、段落
 * 第二阶段：行内样式解析 — 处理 **bold** __bold__ *italic* _italic_ `code`
 *            ~~strikethrough~~ [text](url) ![alt](url)
 *            以及自定义扩展 [#RRGGBB] [/] [item:...] [image:...]
 */
public final class WikiMarkdownParser {

    private static final int LINE_H = 12;
    private static final int ICON_SIZE = 16;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final int LINK_COLOR = 0xFF55AAFF;

    private WikiMarkdownParser() {}

    /* ═══════════════ 第一阶段：块级解析 ═══════════════ */
    public static List<RenderLine> parse(String md, int maxW, FontRenderer fr) {
        List<RenderLine> out = new ArrayList<>();
        if (md == null || md.isEmpty()) return out;

        String[] rawLines = md.split("\r?\n");
        List<BlockData> blocks = buildBlocks(rawLines);
        for (BlockData block : blocks) {
            expandBlock(block, out, maxW, fr);
        }
        return out;
    }

    /* ───── 块级解析：输出中间 BlockData 列表 ───── */
    private static List<BlockData> buildBlocks(String[] rawLines) {
        List<BlockData> blocks = new ArrayList<>();
        boolean inCodeBlock = false;
        StringBuilder codeAccum = new StringBuilder();
        boolean inTable = false;
        List<String[]> tableRows = new ArrayList<>();
        StringBuilder paraAccum = new StringBuilder(); // paragraph 累积
        String paraFirstLine = null; // 用于判断列表前缀

        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            String trimmed = line.trim();

            // ── 代码块 ──
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // 结束代码块
                    blocks.add(new BlockData(BlockType.CODE_BLOCK, codeAccum.toString()));
                    codeAccum.setLength(0);
                    inCodeBlock = false;
                } else {
                    flushPara(paraAccum, blocks);
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                if (codeAccum.length() > 0) codeAccum.append('\n');
                codeAccum.append(line); // 保留原始缩进
                continue;
            }

            // ── 空行 → 刷新段落 ──
            if (trimmed.isEmpty()) {
                if (inTable) { flushTable(tableRows, blocks); inTable = false; }
                flushPara(paraAccum, blocks);
                blocks.add(new BlockData(BlockType.GAP));
                continue;
            }

            // ── 表格 ──
            if (trimmed.startsWith("|")) {
                if (trimmed.matches("^\\|[\\s:-]+\\|$")) continue;
                flushPara(paraAccum, blocks);
                if (!inTable) { inTable = true; tableRows.clear(); }
                String[] cols = trimmed.split("\\|", -1);
                List<String> clean = new ArrayList<>();
                for (int j = 1; j < cols.length - 1; j++) clean.add(cols[j].trim());
                tableRows.add(clean.toArray(new String[0]));
                continue;
            } else if (inTable) {
                flushTable(tableRows, blocks);
                inTable = false;
            }

            // ── 分割线 ──
            if (trimmed.matches("^[-*_]{3,}$")) {
                flushPara(paraAccum, blocks);
                blocks.add(new BlockData(BlockType.HR));
                continue;
            }

            // ── 标题 ──
            Matcher headingMatcher = Pattern.compile("^(#{1,3})\\s+(.*)").matcher(trimmed);
            if (headingMatcher.matches()) {
                flushPara(paraAccum, blocks);
                int level = headingMatcher.group(1).length();
                blocks.add(new BlockData(BlockType.HEADING, headingMatcher.group(2), level));
                continue;
            }

            // ── 块引用 ──
            if (trimmed.startsWith("> ")) {
                flushPara(paraAccum, blocks);
                blocks.add(new BlockData(BlockType.BLOCKQUOTE, trimmed.substring(2)));
                continue;
            }

            // ── 块级 LaTeX $$...$$ ──
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length() > 4) {
                flushPara(paraAccum, blocks);
                String formula = trimmed.substring(2, trimmed.length() - 2).trim();
                if (!formula.isEmpty()) {
                    blocks.add(new BlockData(BlockType.LATEX, formula));
                }
                continue;
            }

            // ── 独立资产行 ──
            if (trimmed.matches("^!?\\[(item|image):.*\\]$")) {
                flushPara(paraAccum, blocks);
                blocks.add(new BlockData(BlockType.ASSET, trimmed));
                continue;
            }

            // ── 列表 ──
            Matcher listMatcher = Pattern.compile("^([-*+]|\\d+\\.)\\s+(.*)").matcher(trimmed);
            if (listMatcher.matches()) {
                flushPara(paraAccum, blocks);
                String bullet = listMatcher.group(1);
                String content = listMatcher.group(2);
                boolean ordered = bullet.matches("\\d+\\.");
                blocks.add(new BlockData(BlockType.LIST_ITEM, content, ordered));
                continue;
            }

            // ── 普通段落文本 ──
            if (paraAccum.length() > 0) paraAccum.append(' ');
            paraAccum.append(line);
            if (paraFirstLine == null) paraFirstLine = line;
        }

        // 收尾
        if (inCodeBlock && codeAccum.length() > 0) {
            blocks.add(new BlockData(BlockType.CODE_BLOCK, codeAccum.toString()));
        }
        if (inTable) flushTable(tableRows, blocks);
        flushPara(paraAccum, blocks);
        return blocks;
    }

    private static void flushPara(StringBuilder acc, List<BlockData> blocks) {
        if (acc.length() == 0) return;
        blocks.add(new BlockData(BlockType.PARAGRAPH, acc.toString()));
        acc.setLength(0);
    }

    private static void flushTable(List<String[]> rows, List<BlockData> blocks) {
        if (rows.isEmpty()) return;
        blocks.add(new BlockData(BlockType.TABLE, rows));
    }

    /* ═══════════════ 第二阶段：展开 Block → RenderLine ═══════════════ */
    private static void expandBlock(BlockData block, List<RenderLine> out, int maxW, FontRenderer fr) {
        switch (block.type) {
            case HEADING: {
                int level = block.intVal;
                List<TextPart> parts = parseInline(block.text);
                if (level == 1) {
                    out.add(new RenderLine(LineType.HEADING, parts, fr.FONT_HEIGHT + 16));
                } else if (level == 2) {
                    out.add(new RenderLine(LineType.SUBHEADING, parts, fr.FONT_HEIGHT + 8));
                } else {
                    out.add(new RenderLine(LineType.SUBSUBHEADING, parts, fr.FONT_HEIGHT + 6));
                }
                break;
            }
            case PARAGRAPH:
                wrapAndAdd(block.text, out, maxW, fr);
                break;
            case LIST_ITEM:
                String bullet = block.ordered ? "  " : "• ";
                wrapAndAdd(bullet + block.text, out, maxW, fr);
                break;
            case TABLE: {
                List<String[]> rows = block.tableRows;
                out.add(new RenderLine(LineType.TABLE_HEADER, joinTab(rows.get(0)), LINE_H + 4));
                for (int i = 1; i < rows.size(); i++) {
                    RenderLine rl = new RenderLine(LineType.TABLE_ROW, joinTab(rows.get(i)), LINE_H + 2);
                    rl.extra = i - 1;
                    out.add(rl);
                }
                break;
            }
            case BLOCKQUOTE: {
                List<TextPart> parts = parseInline(block.text);
                out.add(new RenderLine(LineType.BLOCKQUOTE, parts, LINE_H + 4));
                break;
            }
            case CODE_BLOCK:
                for (String codeLine : block.text.split("\n")) {
                    out.add(new RenderLine(LineType.CODE_BLOCK,
                            new TextPart(PartType.TEXT, codeLine, 0xFFBBBBBB),
                            LINE_H));
                }
                out.add(new RenderLine(LineType.GAP, " ", 4));
                break;
            case ASSET:
                out.add(parseBlockAsset(block.text));
                break;
            case LATEX:
                out.add(new RenderLine(LineType.LATEX_BLOCK, block.text, 0));
                break;
            case HR:
                out.add(new RenderLine(LineType.GAP, " ", 10));
                break;
            case GAP:
                out.add(new RenderLine(LineType.GAP, " ", 6));
                break;
        }
    }

    private static void wrapAndAdd(String text, List<RenderLine> out, int maxW, FontRenderer fr) {
        List<TextPart> parts = parseInline(text);
        List<TextPart> currentLine = new ArrayList<>();
        int curW = 0;

        for (TextPart p : parts) {
            if (p.type == PartType.TEXT) {
                String[] words = p.text.split(" ");
                for (String word : words) {
                    if (word.isEmpty()) continue;
                    // 将单词拆分为可换行片段（西方单词保持完整，CJK 逐字拆分）
                    String[] segments = splitIntoWrappableSegments(word, maxW, fr);
                    for (String seg : segments) {
                        if (seg.isEmpty()) continue;
                        int w = fr.getStringWidth(seg);
                        // 若当前行放不下且已有内容，则换行
                        if (curW + w > maxW && curW > 0) {
                            if (!currentLine.isEmpty())
                                out.add(new RenderLine(LineType.TEXT, new ArrayList<>(currentLine), LINE_H));
                            currentLine.clear();
                            curW = 0;
                        }
                        currentLine.add(new TextPart(PartType.TEXT, seg, p.color));
                        curW += w;
                    }
                }
            } else if (p.type == PartType.LATEX) {
                currentLine.add(p);
                curW += 50;
                if (curW > maxW && currentLine.size() > 1) {
                    TextPart last = currentLine.remove(currentLine.size() - 1);
                    out.add(new RenderLine(LineType.TEXT, new ArrayList<>(currentLine), LINE_H));
                    currentLine.clear();
                    currentLine.add(last);
                    curW = (last.icon != null ? ICON_SIZE + 2 : 40);
                }
            }
        }
        if (!currentLine.isEmpty())
            out.add(new RenderLine(LineType.TEXT, currentLine, LINE_H));
    }

    /* ═══════════════ 行内样式解析 ═══════════════ */
    private static List<TextPart> parseInline(String text) {
        List<TextPart> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            parts.add(new TextPart(PartType.TEXT, "", DEFAULT_COLOR));
            return parts;
        }

        StringBuilder current = new StringBuilder();
        int color = DEFAULT_COLOR;
        int len = text.length();
        int i = 0;

        while (i < len) {
            char c = text.charAt(i);

            // 1. 颜色开始 [#RRGGBB]
            if (c == '[' && i + 9 <= len && text.charAt(i + 1) == '#') {
                String hex = text.substring(i + 2, i + 8);
                if (text.charAt(i + 8) == ']' && hex.matches("[0-9A-Fa-f]{6}")) {
                    flushText(current, color, parts);
                    color = 0xFF000000 | Integer.parseInt(hex, 16);
                    i += 9;
                    continue;
                }
            }

            // 2. 颜色重置 [/]
            if (c == '[' && i + 3 <= len && text.startsWith("[/]", i)) {
                flushText(current, color, parts);
                color = DEFAULT_COLOR;
                i += 3;
                continue;
            }

            // 3. 行内 LaTeX $...$ (优先于 _ * 等标记)
            if (c == '$') {
                int end = text.indexOf('$', i + 1);
                if (end != -1 && end > i + 1 && !(end + 1 < len && text.charAt(end + 1) == '$')) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.LATEX, text.substring(i + 1, end), color));
                    i = end + 1;
                    continue;
                }
            }

            // 3. 行内代码 `code` (优先于自定义扩展)
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end != -1) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§7§l" + text.substring(i + 1, end) + "§r", color));
                    i = end + 1;
                    continue;
                }
            }

            // 4. 删除线 ~~text~~
            if (c == '~' && i + 1 < len && text.charAt(i + 1) == '~') {
                int end = text.indexOf("~~", i + 2);
                if (end != -1) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§m" + text.substring(i + 2, end) + "§r", color));
                    i = end + 2;
                    continue;
                }
            }

            // 5. 内联资产 [item:...] 或 [image:...]
            Matcher assetMatcher = Pattern.compile("^\\[(item|image):(.*?)\\]").matcher(text.substring(i));
            if (assetMatcher.find() && assetMatcher.start() == 0) {
                String type = assetMatcher.group(1);
                String val = assetMatcher.group(2);
                flushText(current, color, parts);
                if ("item".equals(type)) {
                    ItemStack stack = WikiIconResolver.resolve(val).get();
                    if (!stack.isEmpty()) {
                        parts.add(new TextPart(PartType.ICON, stack));
                    }
                } else if ("image".equals(type)) {
                    String[] segs = val.split(":", -1);
                    String domain = segs.length >= 2 ? segs[0] : "minecraft";
                    String path = segs.length >= 2 ? segs[1] : val;
                    int w = 256, h = 256;
                    if (segs.length >= 4) {
                        try { w = Integer.parseInt(segs[2]); h = Integer.parseInt(segs[3]); } catch (Exception ignored) {}
                    }
                    parts.add(new TextPart(PartType.TEXT, " ", DEFAULT_COLOR));
                    parts.add(new TextPart(PartType.TEXT, "§7[图片:" + path + "]§r", color));
                }
                i += assetMatcher.group(0).length();
                continue;
            }

            // 6. 标准 Markdown 图片 ![alt](url)
            Matcher imgMatcher = Pattern.compile("^!\\[(.*?)\\]\\((.*?)\\)").matcher(text.substring(i));
            if (imgMatcher.find() && imgMatcher.start() == 0) {
                flushText(current, color, parts);
                String url = imgMatcher.group(2);
                String[] segs = url.split(":", -1);
                if (segs.length >= 2) {
                    String domain = segs[0];
                    String path = segs[1];
                    int w = segs.length >= 4 ? tryParse(segs[2], 256) : 256;
                    int h = segs.length >= 4 ? tryParse(segs[3], 256) : 256;
                    parts.add(new TextPart(PartType.TEXT, " ", DEFAULT_COLOR));
                    parts.add(new TextPart(PartType.TEXT, "§7[图片:" + path + "]§r", color));
                } else {
                    parts.add(new TextPart(PartType.TEXT, "§7[图片]§r", color));
                }
                i += imgMatcher.group(0).length();
                continue;
            }

            // 7. 标准 Markdown 链接 [text](url)
            Matcher linkMatcher = Pattern.compile("^\\[(.*?)\\]\\((.*?)\\)").matcher(text.substring(i));
            if (linkMatcher.find() && linkMatcher.start() == 0) {
                flushText(current, color, parts);
                String linkText = linkMatcher.group(1);
                parts.add(new TextPart(PartType.TEXT, "§n" + linkText + "§r", LINK_COLOR));
                i += linkMatcher.group(0).length();
                continue;
            }

            // 8. 粗体 **text** (优先匹配双星号)
            if (c == '*' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§l" + text.substring(i + 2, end) + "§r", color));
                    i = end + 2;
                    continue;
                }
            }

            // 9. 粗体 __text__
            if (c == '_' && i + 1 < len && text.charAt(i + 1) == '_') {
                int end = text.indexOf("__", i + 2);
                if (end != -1) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§l" + text.substring(i + 2, end) + "§r", color));
                    i = end + 2;
                    continue;
                }
            }

            // 10. 斜体 *text* (确保不是 ** 的一部分)
            if (c == '*' && (i + 1 >= len || text.charAt(i + 1) != '*')) {
                int end = text.indexOf('*', i + 1);
                if (end != -1 && !(end + 1 < len && text.charAt(end + 1) == '*')) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§o" + text.substring(i + 1, end) + "§r", color));
                    i = end + 1;
                    continue;
                }
            }

            // 11. 斜体 _text_ (确保不是 __ 的一部分)
            if (c == '_' && (i + 1 >= len || text.charAt(i + 1) != '_')) {
                int end = text.indexOf('_', i + 1);
                if (end != -1 && !(end + 1 < len && text.charAt(end + 1) == '_')) {
                    flushText(current, color, parts);
                    parts.add(new TextPart(PartType.TEXT, "§o" + text.substring(i + 1, end) + "§r", color));
                    i = end + 1;
                    continue;
                }
            }

            current.append(c);
            i++;
        }
        flushText(current, color, parts);
        if (parts.isEmpty()) parts.add(new TextPart(PartType.TEXT, "", DEFAULT_COLOR));
        return parts;
    }

    private static void flushText(StringBuilder cur, int color, List<TextPart> out) {
        if (cur.length() == 0) return;
        out.add(new TextPart(PartType.TEXT, cur.toString(), color));
        cur.setLength(0);
    }

    /* ═══════════════ 辅助方法 ═══════════════ */

    private static RenderLine parseBlockAsset(String line) {
        Matcher m = Pattern.compile("^!?\\[(\\w+):(.*?)\\]$").matcher(line);
        if (!m.find()) return new RenderLine(LineType.TEXT, line, LINE_H);
        String type = m.group(1), val = m.group(2);
        if ("item".equals(type)) {
            ItemStack stack = WikiIconResolver.resolve(val).get();
            return stack.isEmpty()
                    ? new RenderLine(LineType.TEXT, line, LINE_H)
                    : new RenderLine(LineType.INLINE_ICON, stack);
        } else if ("image".equals(type)) {
            String[] parts = val.split(":", -1);
            String domain = parts.length >= 2 ? parts[0] : "minecraft";
            String path = parts.length >= 2 ? parts[1] : val;
            int w = 256, h = 256;
            if (parts.length >= 4) {
                try { w = Integer.parseInt(parts[2]); h = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
            }
            return new RenderLine(LineType.INLINE_IMAGE, new ResourceLocation(domain, path), w, h);
        }
        return new RenderLine(LineType.TEXT, line, LINE_H);
    }

    private static String joinTab(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * 将单词拆分为可换行片段：
     * - 西方单词（不含 CJK）保持原样，末尾加空格
     * - 含 CJK 字符的文本逐字拆分（每个字符独立）
     * - 若单个字/单词超过 maxW，则按字符硬切
     */
    private static String[] splitIntoWrappableSegments(String word, int maxW, FontRenderer fr) {
        // 判断是否包含 CJK 字符
        boolean hasCJK = false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (isCJK(c)) { hasCJK = true; break; }
        }

        if (!hasCJK) {
            // 西方单词：末尾加空格，保持单词完整
            return new String[]{word + " "};
        }

        // CJK 文本：逐字拆分
        List<String> segs = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            segs.add(String.valueOf(c));
        }
        return segs.toArray(new String[0]);
    }

    /**
     * 判断是否为 CJK 字符（中日韩统一表意文字及扩展区）
     */
    private static boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || block == Character.UnicodeBlock.KANGXI_RADICALS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private static int tryParse(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    /* ═══════════════ 中间 Block 类型 ═══════════════ */
    private enum BlockType {
        HEADING, PARAGRAPH, LIST_ITEM, TABLE, BLOCKQUOTE, CODE_BLOCK, ASSET, LATEX, HR, GAP
    }

    private static class BlockData {
        final BlockType type;
        final String text;
        final int intVal;
        final boolean ordered;
        final List<String[]> tableRows;

        BlockData(BlockType t) { this(t, "", 0, false, null); }
        BlockData(BlockType t, String txt) { this(t, txt, 0, false, null); }
        BlockData(BlockType t, String txt, int val) { this(t, txt, val, false, null); }
        BlockData(BlockType t, String txt, boolean ord) { this(t, txt, 0, ord, null); }
        BlockData(BlockType t, List<String[]> rows) { this(t, "", 0, false, rows); }

        private BlockData(BlockType t, String txt, int val, boolean ord, List<String[]> rows) {
            type = t; text = txt; intVal = val; ordered = ord; tableRows = rows;
        }
    }
}
