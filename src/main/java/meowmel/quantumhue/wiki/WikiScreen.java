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

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WikiScreen extends GuiScreen {

    private static final int BG           = 0xFF0E0E16;
    private static final int SIDEBAR_BG   = 0xFF121220;
    private static final int SIDEBAR_HOVER = 0xFF1C1C34;
    private static final int SIDEBAR_SEL  = 0xFF24244A;
    private static final int HEADER_BG    = 0xFF101018;
    private static final int DIVIDER      = 0xFF2A2A44;
    private static final int ACCENT       = 0xFF6688CC;
    private static final int ACCENT_DIM   = 0xFF3A5088;
    private static final int TXT          = 0xFFDDDDDD;
    private static final int TXT_DIM      = 0xFF999999;
    private static final int TXT_HEAD     = 0xFF88AADD;
    private static final int TXT_SUB      = 0xFF7799CC;
    private static final int DIAG_BG      = 0xFF0A0A18;
    private static final int DIAG_BORDER  = 0xFF333355;
    private static final int DIAG_TXT     = 0xFF88CCAA;
    private static final int TBL_HEADER   = 0xFF181830;
    private static final int TBL_ROW_A    = 0xFF111122;
    private static final int TBL_ROW_B    = 0xFF0E0E1C;
    private static final int TBL_BORDER   = 0xFF2A2A44;
    private static final int CAT_BG       = 0xFF161628;
    private static final int CAT_TOGGLE   = 0xFF667799;
    private static final int SCROLLBAR_BG = 0xFF111122;
    private static final int SCROLLBAR_FG = 0xFF444466;
    private static final int SEARCH_BG    = 0xFF0C0C18;
    private static final int SEARCH_BORDER = 0xFF333355;
    private static final int LOCKED_BG    = 0xFF0A0A12;
    private static final int LOCKED_TXT   = 0xFF444455;
    private static final int HINT_TXT     = 0xFF665588;
    private static final int TOAST_BG     = 0xDD161630;
    private static final int TOAST_BORDER = 0xFF6688CC;

    private static final int SIDEBAR_W = 170;
    private static final int HEADER_H  = 28;
    private static final int SEARCH_H  = 22;
    private static final int CAT_H     = 22;
    private static final int ENTRY_H   = 20;
    private static final int PAD       = 14;
    private static final int SCROLLBAR = 5;
    private static final int LINE_H    = 12;

    private final List<WikiCategory> categories;
    private WikiPage activePage;
    private float scroll = 0;
    private float scrollTarget = 0;
    private float maxScroll = 0;
    private float sidebarScroll = 0;
    private float sidebarScrollTarget = 0;
    private float sidebarMaxScroll = 0;
    private List<RenderLine> lines = new ArrayList<>();
    private boolean dirty = true;
    private GuiTextField searchField;
    private String lastSearch = "";
    private String toastMessage = "";
    private int toastTimer = 0;

    public WikiScreen() {
        this.categories = WikiContent.getCategories();
        // Scan player inventory to discover pages
        if (Minecraft.getMinecraft().player != null) {
            WikiDiscoveryScanner.scanPlayer(Minecraft.getMinecraft().player);
        }
        // Expand first category and select first page
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

    // ── Draw ────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mx, int my, float pt) {
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * 0.3f;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.5f) sidebarScroll = sidebarScrollTarget;

        if (dirty) { rebuildLines(); dirty = false; }

        Gui.drawRect(0, 0, width, height, BG);
        drawSidebar(mx, my);
        drawPageHeader();

        int cx = SIDEBAR_W + 1;
        int cy = HEADER_H;
        int cr = width - SCROLLBAR;
        int cb = height;

        enableScissor(cx, cy, cr, cb);

        if (activePage != null && !WikiDiscovery.isDiscovered(activePage)) {
            drawLockedContent(cx, cy, cr);
        } else {
            drawPageContent(cx, cy, cr);
        }

        disableScissor();
        drawScrollbar(cy, cb, scroll, maxScroll, cr);

        // Discovery counter in sidebar footer
        FontRenderer fr = mc.fontRenderer;
        String counter = WikiDiscovery.discoveredCount() + "/" + WikiDiscovery.totalCount() + " discovered";
        int tw = fr.getStringWidth(counter);
        fr.drawStringWithShadow(counter, (SIDEBAR_W - tw) / 2, height - 12, TXT_DIM);

        // Toast notification
        if (toastTimer > 0) {
            toastTimer--;
            int toastW = fr.getStringWidth(toastMessage) + 20;
            int toastX = (width - toastW) / 2;
            int toastY = 4;
            int alpha = toastTimer > 20 ? 0xDD : (int)(0xDD * (toastTimer / 20f));
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

        // Header
        Gui.drawRect(0, 0, SIDEBAR_W, HEADER_H, HEADER_BG);
        Gui.drawRect(0, HEADER_H - 1, SIDEBAR_W, HEADER_H, DIVIDER);
        String title = "Wiki";
        fr.drawStringWithShadow(title, (SIDEBAR_W - fr.getStringWidth(title)) / 2, 10, ACCENT);

        // Search box
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

        // Categories + entries (scrollable)
        int listTop = HEADER_H + 2 + SEARCH_H + 4;
        enableScissor(0, listTop, SIDEBAR_W, height);

        int y = listTop - (int) sidebarScroll;
        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        boolean hasSearch = !query.isEmpty();

        for (WikiCategory cat : categories) {
            List<WikiPage> visiblePages = hasSearch ? filterPages(cat, query) : cat.pages;
            if (hasSearch && visiblePages.isEmpty()) continue;

            // Category header
            boolean catHover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + CAT_H && my >= listTop;
            Gui.drawRect(0, y, SIDEBAR_W, y + CAT_H, catHover ? SIDEBAR_HOVER : CAT_BG);
            Gui.drawRect(0, y + CAT_H - 1, SIDEBAR_W, y + CAT_H, DIVIDER);

            // Toggle arrow
            boolean open = hasSearch || !cat.collapsed;
            String arrow = open ? "\u25BC" : "\u25B6";
            fr.drawStringWithShadow(arrow, 6, y + (CAT_H - 8) / 2, CAT_TOGGLE);

            // Category item icon
            ItemStack catIcon = cat.icon.get();
            if (catIcon != null && !catIcon.isEmpty()) {
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.pushMatrix();
                float s = 0.6f;
                int ix = 18;
                int iy = y + (CAT_H - 10) / 2;
                GlStateManager.translate(ix, iy, 0);
                GlStateManager.scale(s, s, 1);
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

            // Entries
            for (WikiPage page : visiblePages) {
                boolean sel = page == activePage;
                boolean hover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + ENTRY_H && my >= listTop;
                boolean locked = !WikiDiscovery.isDiscovered(page);

                if (locked) {
                    if (hover) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, 0xFF0F0F1A);
                    }
                    // Lock icon
                    fr.drawStringWithShadow("\u2B29", 14, y + (ENTRY_H - 8) / 2, 0xFF333344);
                    String lockedLabel = "???";
                    fr.drawStringWithShadow(lockedLabel, 26, y + (ENTRY_H - 8) / 2, LOCKED_TXT);
                } else {
                    if (sel) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + ENTRY_H, ACCENT);
                    } else if (hover) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + ENTRY_H, SIDEBAR_HOVER);
                    }

                    // Entry item icon
                    ItemStack pageIcon = page.icon.get();
                    if (pageIcon != null && !pageIcon.isEmpty()) {
                        GlStateManager.enableDepth();
                        RenderHelper.enableGUIStandardItemLighting();
                        GlStateManager.pushMatrix();
                        float s = 0.55f;
                        int ix = 14;
                        int iy2 = y + (ENTRY_H - 9) / 2;
                        GlStateManager.translate(ix, iy2, 0);
                        GlStateManager.scale(s, s, 1);
                        ri.renderItemAndEffectIntoGUI(pageIcon, 0, 0);
                        GlStateManager.popMatrix();
                        RenderHelper.disableStandardItemLighting();
                        GlStateManager.disableDepth();
                    }

                    int tc = sel ? TXT : (hover ? 0xFFBBBBBB : TXT_DIM);
                    String label = page.title;
                    if (fr.getStringWidth(label) > SIDEBAR_W - 36) {
                        while (fr.getStringWidth(label + "...") > SIDEBAR_W - 36 && label.length() > 1) {
                            label = label.substring(0, label.length() - 1);
                        }
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
            // Render item icon in header
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

    private void drawPageContent(int cx, int cy, int cr) {
        FontRenderer fr = mc.fontRenderer;
        int x = cx + PAD;
        int maxW = cr - cx - PAD * 2;
        int y = cy + PAD - (int) scroll;

        for (RenderLine ln : lines) {
            // Skip off-screen lines for performance
            if (y + 20 < cy - 200 && ln.type != LineType.TABLE_ROW) {
                y += ln.height;
                continue;
            }
            if (y > height + 200) break;

            switch (ln.type) {
                case HEADING:
                    y += 6;
                    fr.drawStringWithShadow(ln.text, x, y, TXT_HEAD);
                    y += fr.FONT_HEIGHT + 2;
                    Gui.drawRect(x, y, x + Math.min(fr.getStringWidth(ln.text) + 20, maxW), y + 1, ACCENT_DIM);
                    y += 8;
                    break;

                case SUBHEADING:
                    y += 4;
                    fr.drawStringWithShadow(ln.text, x, y, TXT_SUB);
                    y += fr.FONT_HEIGHT + 4;
                    break;

                case TEXT:
                    fr.drawStringWithShadow(ln.text, x, y, TXT);
                    y += LINE_H;
                    break;

                case TABLE_HEADER: {
                    String[] cols = ln.text.split("\t");
                    int colW = maxW / cols.length;
                    Gui.drawRect(x, y, x + maxW, y + LINE_H + 4, TBL_HEADER);
                    Gui.drawRect(x, y + LINE_H + 3, x + maxW, y + LINE_H + 4, TBL_BORDER);
                    for (int c = 0; c < cols.length; c++) {
                        fr.drawStringWithShadow(cols[c].trim(), x + c * colW + 4, y + 2, ACCENT);
                    }
                    y += LINE_H + 4;
                    break;
                }

                case TABLE_ROW: {
                    String[] cols = ln.text.split("\t");
                    int colW = maxW / Math.max(cols.length, 1);
                    int bg = ln.extra % 2 == 0 ? TBL_ROW_A : TBL_ROW_B;
                    Gui.drawRect(x, y, x + maxW, y + LINE_H + 2, bg);
                    for (int c = 0; c < cols.length; c++) {
                        fr.drawStringWithShadow(cols[c].trim(), x + c * colW + 4, y + 2, TXT_DIM);
                    }
                    y += LINE_H + 2;
                    break;
                }

                case DIAGRAM_START:
                    y += 4;
                    Gui.drawRect(x, y, x + maxW, y + ln.extra, DIAG_BG);
                    Gui.drawRect(x, y, x + maxW, y + 1, DIAG_BORDER);
                    Gui.drawRect(x, y + ln.extra - 1, x + maxW, y + ln.extra, DIAG_BORDER);
                    Gui.drawRect(x, y, x + 1, y + ln.extra, DIAG_BORDER);
                    Gui.drawRect(x + maxW - 1, y, x + maxW, y + ln.extra, DIAG_BORDER);
                    y += 6;
                    break;

                case DIAGRAM_LINE:
                    fr.drawStringWithShadow(ln.text, x + 8, y, DIAG_TXT);
                    y += LINE_H;
                    break;

                case DIAGRAM_END:
                    y += 6;
                    break;

                case GAP:
                    y += 10;
                    break;
            }
        }

        int total = y + (int) scroll - cy;
        int viewH = height - cy;
        maxScroll = Math.max(0, total - viewH + PAD);
    }

    private void drawLockedContent(int cx, int cy, int cr) {
        FontRenderer fr = mc.fontRenderer;
        int centerX = (cx + cr) / 2;
        int y = cy + 60;

        String lock = "\u2715 Locked";
        fr.drawStringWithShadow(lock, centerX - fr.getStringWidth(lock) / 2, y, LOCKED_TXT);
        y += 20;

        String title = activePage.title;
        fr.drawStringWithShadow(title, centerX - fr.getStringWidth(title) / 2, y, 0xFF555566);
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

    // ── Line builder ────────────────────────────────────────────────────

    private void rebuildLines() {
        lines.clear();
        if (activePage == null) return;

        FontRenderer fr = mc.fontRenderer;
        int maxW = width - SIDEBAR_W - 1 - PAD * 2 - SCROLLBAR;

        for (WikiPage.Section sec : activePage.sections) {
            switch (sec.type) {
                case HEADING:
                    lines.add(new RenderLine(LineType.HEADING, sec.text, fr.FONT_HEIGHT + 16));
                    break;

                case SUBHEADING:
                    lines.add(new RenderLine(LineType.SUBHEADING, sec.text, fr.FONT_HEIGHT + 8));
                    break;

                case TEXT:
                    for (String w : wrap(fr, sec.text, maxW)) {
                        lines.add(new RenderLine(LineType.TEXT, w, LINE_H));
                    }
                    break;

                case TABLE: {
                    String[] rows = sec.text.split("\n");
                    for (int r = 0; r < rows.length; r++) {
                        if (r == 0) {
                            lines.add(new RenderLine(LineType.TABLE_HEADER, rows[r], LINE_H + 4));
                        } else {
                            RenderLine rl = new RenderLine(LineType.TABLE_ROW, rows[r], LINE_H + 2);
                            rl.extra = r - 1;
                            lines.add(rl);
                        }
                    }
                    break;
                }

                case DIAGRAM: {
                    String[] dLines = sec.text.split("\n");
                    int dh = 12 + dLines.length * LINE_H;
                    RenderLine start = new RenderLine(LineType.DIAGRAM_START, "", 0);
                    start.extra = dh;
                    lines.add(start);
                    for (String dl : dLines) {
                        lines.add(new RenderLine(LineType.DIAGRAM_LINE, dl, LINE_H));
                    }
                    lines.add(new RenderLine(LineType.DIAGRAM_END, "", 6));
                    break;
                }

                case GAP:
                    lines.add(new RenderLine(LineType.GAP, "", 10));
                    break;
            }
        }
    }

    private static List<String> wrap(FontRenderer fr, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (maxW <= 10) { out.add(text); return out; }
        for (String word : text.split(" ")) {
            if (out.isEmpty()) { out.add(word); continue; }
            String last = out.get(out.size() - 1);
            String test = last + " " + word;
            if (fr.getStringWidth(test) > maxW) {
                out.add(word);
            } else {
                out.set(out.size() - 1, test);
            }
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    // ── Input ───────────────────────────────────────────────────────────

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

            // Category header click
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
                if (searchField.getText().isEmpty()) {
                    mc.displayGuiScreen(null);
                } else {
                    searchField.setText("");
                }
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

        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        } else if (key == Keyboard.KEY_UP) {
            scrollTarget = Math.max(0, scrollTarget - 50);
        } else if (key == Keyboard.KEY_DOWN) {
            scrollTarget = Math.min(maxScroll, scrollTarget + 50);
        } else if (key == Keyboard.KEY_HOME) {
            scrollTarget = 0;
        } else if (key == Keyboard.KEY_END) {
            scrollTarget = maxScroll;
        } else if (key == Keyboard.KEY_PRIOR) {
            scrollTarget = Math.max(0, scrollTarget - 200);
        } else if (key == Keyboard.KEY_NEXT) {
            scrollTarget = Math.min(maxScroll, scrollTarget + 200);
        } else if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_F) {
            searchField.setFocused(true);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<WikiPage> filterPages(WikiCategory cat, String query) {
        List<WikiPage> result = new ArrayList<>();
        for (WikiPage p : cat.pages) {
            if (p.title.toLowerCase(Locale.ROOT).contains(query) ||
                    p.id.toLowerCase(Locale.ROOT).contains(query)) {
                result.add(p);
            }
        }
        return result;
    }

    private void enableScissor(int l, int t, int r, int b) {
        double s = mc.displayWidth / (double) width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)(l * s), (int)((height - b) * s), (int)((r - l) * s), (int)((b - t) * s));
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private enum LineType {
        HEADING, SUBHEADING, TEXT,
        TABLE_HEADER, TABLE_ROW,
        DIAGRAM_START, DIAGRAM_LINE, DIAGRAM_END,
        GAP
    }

    private static class RenderLine {
        final LineType type;
        final String text;
        int height;
        int extra; // row index for tables, diagram height for DIAGRAM_START

        RenderLine(LineType type, String text, int height) {
            this.type = type;
            this.text = text;
            this.height = height;
        }
    }
}