package meowmel.quantumhue.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiScreen extends GuiScreen {
    private static final int BG = 0xFF0E0E16;
    private static final int SIDEBAR_BG = 0xFF121220;
    private static final int SIDEBAR_HOVER = 0xFF1C1C34;
    private static final int SIDEBAR_SEL = 0xFF24244A;
    private static final int HEADER_BG = 0xFF101018;
    private static final int DIVIDER = 0xFF2A2A44;
    private static final int ACCENT = 0xFF6688CC;
    private static final int ACCENT_DIM = 0xFF3A5088;
    private static final int TXT = 0xFFDDDDDD;
    private static final int TXT_DIM = 0xFF999999;
    private static final int TXT_HEAD = 0xFF88AADD;
    private static final int TXT_SUB = 0xFF7799CC;
    private static final int TBL_HEADER = 0xFF181830;
    private static final int TBL_ROW_A = 0xFF111122;
    private static final int TBL_ROW_B = 0xFF0E0E1C;
    private static final int TBL_BORDER = 0xFF2A2A44;
    private static final int CAT_BG = 0xFF161628;
    private static final int CAT_TOGGLE = 0xFF667799;
    private static final int SCROLLBAR_BG = 0xFF111122;
    private static final int SCROLLBAR_FG = 0xFF444466;
    private static final int SEARCH_BG = 0xFF0C0C18;
    private static final int SEARCH_BORDER = 0xFF333355;
    private static final int LOCKED_BG = 0xFF0A0A12;
    private static final int LOCKED_TXT = 0xFF444455;
    private static final int HINT_TXT = 0xFF665588;
    private static final int TOAST_BG = 0xDD161630;
    private static final int TOAST_BORDER = 0xFF6688CC;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private static final int SIDEBAR_W = 170;
    private static final int HEADER_H = 28;
    private static final int SEARCH_H = 22;
    private static final int CAT_H = 22;
    private static final int ENTRY_H = 20;
    private static final int PAD = 14;
    private static final int SCROLLBAR = 5;
    private static final int LINE_H = 12;
    private static final int ICON_SIZE = 16;

    private final List<WikiCategory> categories;
    private WikiPage activePage;
    private float scroll = 0, scrollTarget = 0, maxScroll = 0;
    private float sidebarScroll = 0, sidebarScrollTarget = 0, sidebarMaxScroll = 0;
    private final List<RenderLine> lines = new ArrayList<>();
    private boolean dirty = true;
    private GuiTextField searchField;
    private String lastSearch = "", toastMessage = "";
    private int toastTimer = 0;

    public WikiScreen() {
        this.categories = WikiContent.getCategories();
        if (Minecraft.getMinecraft().player != null) {
            WikiDiscoveryScanner.scanPlayer(Minecraft.getMinecraft().player);
        }
        if (!categories.isEmpty()) {
            categories.get(0).collapsed = false;
            if (!categories.get(0).pages.isEmpty()) {
                activePage = categories.get(0).pages.get(0);
            }
        }
    }

    public static void open() {
        Minecraft.getMinecraft().displayGuiScreen(new WikiScreen());
    }

    private static List<RenderLine> parseMarkdown(String md, int maxW, FontRenderer fr) {
        List<RenderLine> out = new ArrayList<>();
        if (md == null || md.isEmpty()) return out;
        String[] rawLines = md.split("\r?\n");
        boolean inTable = false;
        List<String[]> tableRows = new ArrayList<>();

        for (String line : rawLines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (inTable) {
                    flushTable(tableRows, out);
                    tableRows.clear();
                    inTable = false;
                }
                out.add(new RenderLine(LineType.GAP, " ", 6));
                continue;
            }
            if (line.startsWith("|")) {
                if (line.matches("^\\|[\\s:-]+\\|$")) continue;
                if (!inTable) {
                    inTable = true;
                    tableRows.clear();
                }
                String[] cols = line.split("\\|", -1);
                List<String> clean = new ArrayList<>();
                for (int i = 1; i < cols.length - 1; i++) clean.add(cols[i].trim());
                tableRows.add(clean.toArray(new String[0]));
                continue;
            } else if (inTable) {
                flushTable(tableRows, out);
                tableRows.clear();
                inTable = false;
            }

            if (line.startsWith("### ")) {
                out.add(new RenderLine(LineType.SUBSUBHEADING, parseInlineStyles(line.substring(4)), fr.FONT_HEIGHT + 6));
            } else if (line.startsWith("## ")) {
                out.add(new RenderLine(LineType.SUBHEADING, parseInlineStyles(line.substring(3)), fr.FONT_HEIGHT + 8));
            } else if (line.startsWith("# ")) {
                out.add(new RenderLine(LineType.HEADING, parseInlineStyles(line.substring(2)), fr.FONT_HEIGHT + 16));
            } else if (line.equals("---") || line.equals("***") || line.equals("___")) {
                out.add(new RenderLine(LineType.GAP, " ", 10));
            } else if (line.matches("^!?\\[(item|image):.*\\]$")) {
                out.add(parseInlineAsset(line));
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") || line.matches("^\\d+\\.\\s.*")) {
                String prefix = line.matches("^\\d+\\.\\s.*") ? line.replaceFirst("^\\d+\\.\\s*", "") : line.substring(2);
                out.addAll(wrapText("• " + prefix, maxW, fr));
            } else {
                out.addAll(wrapText(line, maxW, fr));
            }
        }
        if (inTable) flushTable(tableRows, out);
        return out;
    }

    private static RenderLine parseInlineAsset(String line) {
        Matcher m = Pattern.compile("^!?\\[(\\w+):(.*?)\\]$").matcher(line);
        if (!m.find()) return new RenderLine(LineType.TEXT, line, LINE_H);
        String type = m.group(1), val = m.group(2);
        if ("item".equals(type)) {
            return new RenderLine(LineType.INLINE_ICON, WikiIconResolver.resolve(val).get());
        } else if ("image".equals(type)) {
            String[] parts = val.split(":", -1);
            String domain = parts.length >= 2 ? parts[0] : "minecraft";
            String path = parts.length >= 2 ? parts[1] : val;
            int w = 256, h = 256;
            if (parts.length >= 4) {
                try {
                    w = Integer.parseInt(parts[2]);
                    h = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ignored) {
                }
            }
            return new RenderLine(LineType.INLINE_IMAGE, new ResourceLocation(domain, path), w, h);
        }
        return new RenderLine(LineType.TEXT, line, LINE_H);
    }

    private static List<RenderLine> wrapText(String text, int maxW, FontRenderer fr) {
        List<TextPart> parts = parseInlineStyles(text);
        List<RenderLine> lines = new ArrayList<>();
        List<TextPart> currentParts = new ArrayList<>();
        int curW = 0;

        for (TextPart p : parts) {
            if (p.type == PartType.TEXT) {
                String[] words = p.text.split(" ");
                for (String word : words) {
                    if (word.isEmpty()) continue;
                    int w = fr.getStringWidth(word + " ");
                    if (curW + w > maxW && curW > 0) {
                        if (!currentParts.isEmpty())
                            lines.add(new RenderLine(LineType.TEXT, new ArrayList<>(currentParts), LINE_H));
                        currentParts.clear();
                        curW = 0;
                    }
                    currentParts.add(new TextPart(PartType.TEXT, word + " ", p.color));
                    curW += w;
                }
            } else {
                currentParts.add(p);
                curW += (p.type == PartType.ICON ? ICON_SIZE + 2 : 40);
                if (curW > maxW && currentParts.size() > 1) {
                    TextPart last = currentParts.remove(currentParts.size() - 1);
                    lines.add(new RenderLine(LineType.TEXT, new ArrayList<>(currentParts), LINE_H));
                    currentParts.clear();
                    currentParts.add(last);
                    curW = (last.type == PartType.ICON ? ICON_SIZE + 2 : 40);
                }
            }
        }
        if (!currentParts.isEmpty()) lines.add(new RenderLine(LineType.TEXT, currentParts, LINE_H));
        if (lines.isEmpty())
            lines.add(new RenderLine(LineType.TEXT, new TextPart(PartType.TEXT, " ", DEFAULT_COLOR), LINE_H));
        return lines;
    }

    /**
     * 🔑 核心修复：稳定、防冲突的内联样式解析器
     */
    private static List<TextPart> parseInlineStyles(String text) {
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
            if (c == '[' && i + 9 <= len && text.charAt(i + 1) == '#' && text.charAt(i + 8) == ']') {
                String hex = text.substring(i + 2, i + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    if (current.length() > 0) {
                        parts.add(new TextPart(PartType.TEXT, current.toString(), color));
                        current.setLength(0);
                    }
                    color = Integer.decode("#" + hex);
                    i += 9;
                    continue;
                }
            }

            // 2. 颜色重置 [/]
            if (c == '[' && i + 3 <= len && text.startsWith("[/]", i)) {
                if (current.length() > 0) {
                    parts.add(new TextPart(PartType.TEXT, current.toString(), color));
                    current.setLength(0);
                }
                color = DEFAULT_COLOR;
                i += 3;
                continue;
            }

            // 3. 粗体 **text** (优先匹配，避免与斜体冲突)
            if (c == '*' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    if (current.length() > 0) {
                        parts.add(new TextPart(PartType.TEXT, current.toString(), color));
                        current.setLength(0);
                    }
                    parts.add(new TextPart(PartType.TEXT, "§l" + text.substring(i + 2, end) + "§r", color));
                    i = end + 2;
                    continue;
                }
            }

            // 4. 斜体 *text* (确保不是 ** 的一部分)
            if (c == '*' && (i + 1 >= len || text.charAt(i + 1) != '*')) {
                int end = text.indexOf('*', i + 1);
                if (end != -1 && end > i + 1) {
                    boolean isDouble = (end + 1 < len && text.charAt(end + 1) == '*');
                    if (!isDouble) {
                        if (current.length() > 0) {
                            parts.add(new TextPart(PartType.TEXT, current.toString(), color));
                            current.setLength(0);
                        }
                        parts.add(new TextPart(PartType.TEXT, "§o" + text.substring(i + 1, end) + "§r", color));
                        i = end + 1;
                        continue;
                    }
                }
            }

            // 5. 代码 `text`
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end != -1) {
                    if (current.length() > 0) {
                        parts.add(new TextPart(PartType.TEXT, current.toString(), color));
                        current.setLength(0);
                    }
                    parts.add(new TextPart(PartType.TEXT, "§7§l" + text.substring(i + 1, end) + "§r", color));
                    i = end + 1;
                    continue;
                }
            }

            current.append(c);
            i++;
        }
        if (current.length() > 0) {
            parts.add(new TextPart(PartType.TEXT, current.toString(), color));
        }
        if (parts.isEmpty()) parts.add(new TextPart(PartType.TEXT, "", DEFAULT_COLOR));
        return parts;
    }

    private static void flushTable(List<String[]> rows, List<RenderLine> out) {
        if (rows.isEmpty()) return;
        out.add(new RenderLine(LineType.TABLE_HEADER, String.join("\t", rows.get(0)), LINE_H + 4));
        for (int i = 1; i < rows.size(); i++) {
            RenderLine rl = new RenderLine(LineType.TABLE_ROW, String.join("\t", rows.get(i)), LINE_H + 2);
            rl.extra = i - 1;
            out.add(rl);
        }
    }

    private static List<String> wrap(FontRenderer fr, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (maxW <= 10) {
            out.add(text);
            return out;
        }
        for (String word : text.split(" ")) {
            if (out.isEmpty()) {
                out.add(word);
                continue;
            }
            String last = out.get(out.size() - 1);
            String test = last + " " + word;
            if (fr.getStringWidth(test) > maxW) out.add(word);
            else out.set(out.size() - 1, test);
        }
        if (out.isEmpty()) out.add(" ");
        return out;
    }

    private static List<WikiPage> filterPages(WikiCategory cat, String query) {
        List<WikiPage> result = new ArrayList<>();
        for (WikiPage p : cat.pages)
            if (p.title.toLowerCase(Locale.ROOT).contains(query) || p.id.toLowerCase(Locale.ROOT).contains(query))
                result.add(p);
        return result;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        searchField = new GuiTextField(0, mc.fontRenderer, 6, HEADER_H + 4, SIDEBAR_W - 12, SEARCH_H - 4);
        searchField.setMaxStringLength(50);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(TXT);
        dirty = true;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * 0.3f;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.5f) sidebarScroll = sidebarScrollTarget;

        if (dirty) {
            rebuildLines();
            dirty = false;
        }

        Gui.drawRect(0, 0, width, height, BG);
        drawSidebar(mx, my);
        drawPageHeader();

        int cx = SIDEBAR_W + 1, cy = HEADER_H, cr = width - SCROLLBAR, cb = height;
        enableScissor(cx, cy, cr, cb);

        if (activePage != null && !WikiDiscovery.isDiscovered(activePage)) {
            drawLockedContent(cx, cy, cr);
        } else {
            drawPageContent(cx, cy, cr);
        }
        disableScissor();
        drawScrollbar(cy, cb, scroll, maxScroll, cr);

        FontRenderer fr = mc.fontRenderer;
        String counter = WikiDiscovery.discoveredCount() + "/ " + WikiDiscovery.totalCount() + " discovered";
        fr.drawStringWithShadow(counter, (SIDEBAR_W - fr.getStringWidth(counter)) / 2, height - 12, TXT_DIM);

        if (toastTimer > 0) {
            toastTimer--;
            int toastW = fr.getStringWidth(toastMessage) + 20;
            int toastX = (width - toastW) / 2, toastY = 4;
            int alpha = toastTimer > 20 ? 0xDD : (int) (0xDD * (toastTimer / 20f));
            if (alpha > 0) {
                Gui.drawRect(toastX, toastY, toastX + toastW, toastY + 18, TOAST_BG);
                Gui.drawRect(toastX, toastY, toastX + toastW, toastY + 1, TOAST_BORDER);
                Gui.drawRect(toastX, toastY + 17, toastX + toastW, toastY + 18, TOAST_BORDER);
                fr.drawStringWithShadow(toastMessage, toastX + 10, toastY + 5, ACCENT);
            }
        }
        super.drawScreen(mx, my, pt);
    }

    private void drawSidebar(int mx, int my) {
        FontRenderer fr = mc.fontRenderer;
        RenderItem ri = mc.getRenderItem();
        Gui.drawRect(0, 0, SIDEBAR_W, height, SIDEBAR_BG);
        Gui.drawRect(SIDEBAR_W, 0, SIDEBAR_W + 1, height, DIVIDER);
        Gui.drawRect(0, 0, SIDEBAR_W, HEADER_H, HEADER_BG);
        Gui.drawRect(0, HEADER_H - 1, SIDEBAR_W, HEADER_H, DIVIDER);
        fr.drawStringWithShadow("Wiki", (SIDEBAR_W - fr.getStringWidth("Wiki")) / 2, 10, ACCENT);

        int searchTop = HEADER_H + 2;
        Gui.drawRect(4, searchTop, SIDEBAR_W - 4, searchTop + SEARCH_H, SEARCH_BG);
        Gui.drawRect(4, searchTop, SIDEBAR_W - 4, searchTop + 1, SEARCH_BORDER);
        Gui.drawRect(4, searchTop + SEARCH_H - 1, SIDEBAR_W - 4, searchTop + SEARCH_H, SEARCH_BORDER);
        Gui.drawRect(4, searchTop, 5, searchTop + SEARCH_H, SEARCH_BORDER);
        Gui.drawRect(SIDEBAR_W - 5, searchTop, SIDEBAR_W - 4, searchTop + SEARCH_H, SEARCH_BORDER);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            fr.drawStringWithShadow("Search...", 8, searchTop + 7, 0xFF555566);
        }
        searchField.drawTextBox();

        int listTop = HEADER_H + 2 + SEARCH_H + 4;
        enableScissor(0, listTop, SIDEBAR_W, height);
        int y = listTop - (int) sidebarScroll;
        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        boolean hasSearch = !query.isEmpty();

        for (WikiCategory cat : categories) {
            List<WikiPage> visiblePages = hasSearch ? filterPages(cat, query) : cat.pages;
            if (hasSearch && visiblePages.isEmpty()) continue;

            boolean catHover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + CAT_H && my >= listTop;
            Gui.drawRect(0, y, SIDEBAR_W, y + CAT_H, catHover ? SIDEBAR_HOVER : CAT_BG);
            Gui.drawRect(0, y + CAT_H - 1, SIDEBAR_W, y + CAT_H, DIVIDER);
            boolean open = hasSearch || !cat.collapsed;
            fr.drawStringWithShadow(open ? "\u25BC" : "\u25B6", 6, y + (CAT_H - 8) / 2, CAT_TOGGLE);

            ItemStack catIcon = cat.icon.get();
            if (catIcon != null && !catIcon.isEmpty()) {
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.pushMatrix();
                GlStateManager.translate(18, y + (CAT_H - 10) / 2, 0);
                GlStateManager.scale(0.6f, 0.6f, 1);
                ri.renderItemAndEffectIntoGUI(catIcon, 0, 0);
                GlStateManager.popMatrix();
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
            }
            fr.drawStringWithShadow(cat.name, 30, y + (CAT_H - 8) / 2, TXT);
            String count = "(" + visiblePages.size() + ")";
            fr.drawStringWithShadow(count, SIDEBAR_W - 8 - fr.getStringWidth(count), y + (CAT_H - 8) / 2, TXT_DIM);
            y += CAT_H;
            if (!open) continue;

            for (WikiPage page : visiblePages) {
                boolean sel = page == activePage;
                boolean hover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + ENTRY_H && my >= listTop;
                boolean locked = !WikiDiscovery.isDiscovered(page);
                if (locked) {
                    if (hover) Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, 0xFF0F0F1A);
                    fr.drawStringWithShadow("\u2B29", 14, y + (ENTRY_H - 8) / 2, 0xFF333344);
                    fr.drawStringWithShadow("???", 26, y + (ENTRY_H - 8) / 2, LOCKED_TXT);
                } else {
                    if (sel) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + ENTRY_H, ACCENT);
                    } else if (hover) Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, SIDEBAR_HOVER);
                    ItemStack pageIcon = page.icon.get();
                    if (pageIcon != null && !pageIcon.isEmpty()) {
                        GlStateManager.enableDepth();
                        RenderHelper.enableGUIStandardItemLighting();
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(14, y + (ENTRY_H - 9) / 2, 0);
                        GlStateManager.scale(0.55f, 0.55f, 1);
                        ri.renderItemAndEffectIntoGUI(pageIcon, 0, 0);
                        GlStateManager.popMatrix();
                        RenderHelper.disableStandardItemLighting();
                        GlStateManager.disableDepth();
                    }
                    int tc = sel ? TXT : (hover ? 0xFFBBBBBB : TXT_DIM);
                    String label = page.title;
                    if (fr.getStringWidth(label) > SIDEBAR_W - 36) {
                        while (fr.getStringWidth(label + "...") > SIDEBAR_W - 36 && label.length() > 1)
                            label = label.substring(0, label.length() - 1);
                        label += "...";
                    }
                    fr.drawStringWithShadow(label, 26, y + (ENTRY_H - 8) / 2, tc);
                }
                y += ENTRY_H;
            }
        }
        sidebarMaxScroll = Math.max(0, y + (int) sidebarScroll - height);
        disableScissor();
    }

    private void drawPageHeader() {
        FontRenderer fr = mc.fontRenderer;
        int left = SIDEBAR_W + 1;
        Gui.drawRect(left, 0, width, HEADER_H, HEADER_BG);
        Gui.drawRect(left, HEADER_H - 1, width, HEADER_H, DIVIDER);
        if (activePage != null) {
            ItemStack hIcon = activePage.icon.get();
            if (hIcon != null && !hIcon.isEmpty()) {
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(hIcon, left + PAD, 6);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
            }
            fr.drawStringWithShadow(activePage.title, left + PAD + 20, 10, TXT_HEAD);
        }
    }

    private void drawLockedContent(int cx, int cy, int cr) {
        FontRenderer fr = mc.fontRenderer;
        int centerX = (cx + cr) / 2, y = cy + 60;
        fr.drawStringWithShadow("\u2715 Locked", centerX - fr.getStringWidth("\u2715 Locked") / 2, y, LOCKED_TXT);
        y += 20;
        fr.drawStringWithShadow(activePage.title, centerX - fr.getStringWidth(activePage.title) / 2, y, 0xFF555566);
        y += 16;
        if (!activePage.discoveryHint.isEmpty()) {
            for (String line : wrap(fr, activePage.discoveryHint, cr - cx - PAD * 4)) {
                fr.drawStringWithShadow(line, centerX - fr.getStringWidth(line) / 2, y, HINT_TXT);
                y += LINE_H;
            }
        }
    }

    public void showToast(String msg) {
        toastMessage = msg;
        toastTimer = 60;
    }

    private void drawScrollbar(int top, int bot, float scrollVal, float maxVal, int barX) {
        if (maxVal <= 0) return;
        int h = bot - top;
        Gui.drawRect(barX, top, barX + SCROLLBAR, bot, SCROLLBAR_BG);
        float vf = (float) h / (h + maxVal);
        int thumbH = Math.max(16, (int) (h * vf));
        float sf = scrollVal / maxVal;
        int thumbY = top + (int) (sf * (h - thumbH));
        Gui.drawRect(barX + 1, thumbY, barX + SCROLLBAR - 1, thumbY + thumbH, SCROLLBAR_FG);
    }

    private void enableScissor(int l, int t, int r, int b) {
        double s = mc.displayWidth / (double) width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (l * s), (int) ((height - b) * s), (int) ((r - l) * s), (int) ((b - t) * s));
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawPageContent(int cx, int cy, int cr) {
        FontRenderer fr = mc.fontRenderer;
        int startX = cx + PAD, maxW = cr - cx - PAD * 2, y = cy + PAD - (int) scroll;
        RenderItem ri = mc.getRenderItem();

        for (RenderLine ln : lines) {
            int x = startX; // 🔑 关键修复：每行重置 X 坐标，防止标题缩进

            if (y + ln.height < cy - 200 && ln.type != LineType.TABLE_ROW) {
                y += ln.height;
                continue;
            }
            if (y > height + 200) break;

            switch (ln.type) {
                case HEADING:
                    y += 6;
                    for (TextPart p : ln.parts) {
                        if (p.type == PartType.TEXT) {
                            fr.drawStringWithShadow(p.text, x, y, TXT_HEAD);
                            x += fr.getStringWidth(p.text);
                        }
                    }
                    y += fr.FONT_HEIGHT + 2;
                    Gui.drawRect(startX, y, startX + Math.min(fr.getStringWidth(ln.parts.get(0).text) + 20, maxW), y + 1, ACCENT_DIM);
                    y += 8;
                    break;
                case SUBHEADING:
                    y += 4;
                    for (TextPart p : ln.parts) {
                        if (p.type == PartType.TEXT) {
                            fr.drawStringWithShadow(p.text, x, y, TXT_SUB);
                            x += fr.getStringWidth(p.text);
                        }
                    }
                    y += fr.FONT_HEIGHT + 4;
                    break;
                case SUBSUBHEADING:
                    y += 4;
                    for (TextPart p : ln.parts) {
                        if (p.type == PartType.TEXT) {
                            fr.drawStringWithShadow("§l" + p.text, x, y, TXT_SUB);
                            x += fr.getStringWidth(p.text);
                        }
                    }
                    y += fr.FONT_HEIGHT + 4;
                    break;
                case TEXT:
                    drawLineSegments(ln.parts, x, y, maxW, fr, ri);
                    y += LINE_H;
                    break;
                case INLINE_ICON:
                    if (ln.icon != null && !ln.icon.isEmpty()) {
                        GlStateManager.enableDepth();
                        RenderHelper.enableGUIStandardItemLighting();
                        ri.renderItemAndEffectIntoGUI(ln.icon, x + (maxW - ICON_SIZE) / 2, y);
                        RenderHelper.disableStandardItemLighting();
                        GlStateManager.disableDepth();
                    }
                    y += ICON_SIZE + 4;
                    break;
                case INLINE_IMAGE:
                    if (ln.image != null) {
                        mc.getTextureManager().bindTexture(ln.image);
                        GlStateManager.color(1f, 1f, 1f, 1f);
                        int displayW = Math.min(ln.imageW, maxW);
                        int displayH = ln.imageH * displayW / ln.imageW;
                        Gui.drawModalRectWithCustomSizedTexture(x + (maxW - displayW) / 2, y, 0, 0, displayW, displayH, ln.imageW, ln.imageH);
                        y += displayH + 4;
                    } else {
                        y += 10;
                    }
                    break;
                case TABLE_HEADER:
                case TABLE_ROW: {
                    String tableData = ln.text != null ? ln.text : "";
                    String[] cols = tableData.split("\t");
                    int colCount = Math.max(cols.length, 1), colW = maxW / colCount;
                    int bg = (ln.type == LineType.TABLE_HEADER) ? TBL_HEADER : (ln.extra % 2 == 0 ? TBL_ROW_A : TBL_ROW_B);
                    Gui.drawRect(x, y, x + maxW, y + LINE_H + 4, bg);
                    Gui.drawRect(x, y + LINE_H + 3, x + maxW, y + LINE_H + 4, TBL_BORDER);
                    for (int c = 0; c < cols.length; c++) {
                        int textColor = (ln.type == LineType.TABLE_HEADER) ? ACCENT : TXT_DIM;
                        fr.drawStringWithShadow(cols[c].trim(), x + c * colW + 4, y + 2, textColor);
                    }
                    y += LINE_H + 4;
                    break;
                }
                case GAP:
                    y += 10;
                    break;
            }
        }
        maxScroll = Math.max(0, y + (int) scroll - cy - (height - cy) + PAD);
    }

    private void drawLineSegments(List<TextPart> parts, int x, int y, int maxW, FontRenderer fr, RenderItem ri) {
        int curX = x;
        GlStateManager.disableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        for (TextPart p : parts) {
            if (p.type == PartType.TEXT) {
                fr.drawString(p.text, curX, y, p.color);
                curX += fr.getStringWidth(p.text);
            } else if (p.type == PartType.ICON && p.icon != null && !p.icon.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(curX, y - 2, 0);
                GlStateManager.scale(0.8f, 0.8f, 1f);
                ri.renderItemAndEffectIntoGUI(p.icon, 0, 0);
                GlStateManager.popMatrix();
                curX += ICON_SIZE + 2;
            }
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
    }

    private void rebuildLines() {
        lines.clear();
        if (activePage == null) return;
        String content = activePage.markdownContent;
        if (content == null || content.isEmpty()) return;
        FontRenderer fr = mc.fontRenderer;
        int maxW = width - SIDEBAR_W - 1 - PAD * 2 - SCROLLBAR;
        lines.addAll(parseMarkdown(content, maxW, fr));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            if (mx < SIDEBAR_W) {
                sidebarScrollTarget -= dw * 0.35f;
                sidebarScrollTarget = Math.max(0, Math.min(sidebarMaxScroll, sidebarScrollTarget));
            } else {
                scrollTarget -= dw * 0.4f;
                scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            }
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        searchField.mouseClicked(mx, my, btn);
        if (btn != 0 || mx >= SIDEBAR_W) return;
        int listTop = HEADER_H + 2 + SEARCH_H + 4;
        if (my < listTop) return;
        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        boolean hasSearch = !query.isEmpty();
        int y = listTop - (int) sidebarScroll;
        for (WikiCategory cat : categories) {
            List<WikiPage> visible = hasSearch ? filterPages(cat, query) : cat.pages;
            if (hasSearch && visible.isEmpty()) continue;
            if (my >= y && my < y + CAT_H) {
                if (!hasSearch) cat.collapsed = !cat.collapsed;
                return;
            }
            y += CAT_H;
            boolean open = hasSearch || !cat.collapsed;
            if (!open) continue;
            for (WikiPage page : visible) {
                if (my >= y && my < y + ENTRY_H) {
                    activePage = page;
                    scroll = 0;
                    scrollTarget = 0;
                    maxScroll = 0;
                    dirty = true;
                    return;
                }
                y += ENTRY_H;
            }
        }
    }

    @Override
    protected void keyTyped(char ch, int key) throws IOException {
        if (searchField.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE) {
                if (searchField.getText().isEmpty()) mc.displayGuiScreen(null);
                else searchField.setText("");
                return;
            }
            searchField.textboxKeyTyped(ch, key);
            String current = searchField.getText();
            if (!current.equals(lastSearch)) {
                lastSearch = current;
                sidebarScrollTarget = 0;
                sidebarScroll = 0;
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
        else if (key == Keyboard.KEY_UP) scrollTarget = Math.max(0, scrollTarget - 50);
        else if (key == Keyboard.KEY_DOWN) scrollTarget = Math.min(maxScroll, scrollTarget + 50);
        else if (key == Keyboard.KEY_HOME) scrollTarget = 0;
        else if (key == Keyboard.KEY_END) scrollTarget = maxScroll;
        else if (key == Keyboard.KEY_PRIOR) scrollTarget = Math.max(0, scrollTarget - 200);
        else if (key == Keyboard.KEY_NEXT) scrollTarget = Math.min(maxScroll, scrollTarget + 200);
        else if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_F) searchField.setFocused(true);
    }

    private enum LineType {HEADING, SUBHEADING, SUBSUBHEADING, TEXT, TABLE_HEADER, TABLE_ROW, INLINE_ICON, INLINE_IMAGE, GAP}

    enum PartType {TEXT, ICON}

    static class TextPart {
        final PartType type;
        final String text;
        final int color;
        final ItemStack icon;

        TextPart(PartType t, String txt, int col) {
            type = t;
            text = txt;
            color = col;
            icon = null;
        }

        TextPart(PartType t, ItemStack i) {
            type = t;
            text = null;
            color = 0;
            icon = i;
        }
    }

    static class RenderLine {
        final LineType type;
        final String text;
        List<TextPart> parts = new ArrayList<>();
        int height, extra;
        ItemStack icon;
        ResourceLocation image;
        int imageW, imageH;

        RenderLine(LineType t, String txt, int h) {
            type = t;
            text = txt;
            height = h;
        }

        RenderLine(LineType t, TextPart p, int h) {
            type = t;
            parts.add(p);
            text = null;
            height = h;
        }

        RenderLine(LineType t, List<TextPart> p, int h) {
            type = t;
            parts = p;
            text = null;
            height = h;
        }

        RenderLine(LineType t, ItemStack i) {
            type = t;
            icon = i;
            text = null;
            height = ICON_SIZE;
        }

        RenderLine(LineType t, ResourceLocation img, int w, int h) {
            type = t;
            image = img;
            imageW = w;
            imageH = h;
            text = null;
            height = h + 4;
        }
    }
}