package meowmel.quantumhue.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static meowmel.quantumhue.wiki.WikiRenderTypes.*;

/**
 * Wiki 渲染器 — 所有绘制逻辑集中于此
 */
public final class WikiRenderer {

    private WikiRenderer() {}

    /* 颜色常量 */
    public static final int BG = 0xFF0E0E16;
    public static final int SIDEBAR_BG = 0xFF121220;
    public static final int SIDEBAR_HOVER = 0xFF1C1C34;
    public static final int SIDEBAR_SEL = 0xFF24244A;
    public static final int HEADER_BG = 0xFF101018;
    public static final int DIVIDER = 0xFF2A2A44;
    public static final int ACCENT = 0xFF6688CC;
    public static final int ACCENT_DIM = 0xFF3A5088;
    public static final int TXT = 0xFFDDDDDD;
    public static final int TXT_DIM = 0xFF999999;
    public static final int TXT_HEAD = 0xFF88AADD;
    public static final int TXT_SUB = 0xFF7799CC;
    public static final int TBL_HEADER_BG = 0xFF181830;
    public static final int TBL_ROW_A = 0xFF111122;
    public static final int TBL_ROW_B = 0xFF0E0E1C;
    public static final int TBL_BORDER = 0xFF2A2A44;
    public static final int CAT_BG = 0xFF161628;
    public static final int CAT_TOGGLE = 0xFF667799;
    public static final int SCROLLBAR_BG = 0xFF111122;
    public static final int SCROLLBAR_FG = 0xFF444466;
    public static final int SEARCH_BG = 0xFF0C0C18;
    public static final int SEARCH_BORDER = 0xFF333355;
    public static final int TOAST_BG = 0xDD161630;
    public static final int TOAST_BORDER = 0xFF6688CC;
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;
    public static final int CODE_BG = 0xFF0A0A18;
    public static final int QUOTE_LINE = 0xFF556688;
    public static final int QUOTE_BG = 0xFF0E0E22;

    /* 布局常量 */
    public static final int SIDEBAR_W = 170;
    public static final int HEADER_H = 28;
    public static final int SEARCH_H = 22;
    public static final int CAT_H = 22;
    public static final int ENTRY_H = 20;
    public static final int PAD = 14;
    public static final int SCROLLBAR_W = 5;
    public static final int LINE_H = 12;
    public static final int ICON_SIZE = 16;

    /* ═══════════════ 侧边栏 ═══════════════ */
    public static void drawSidebar(Minecraft mc, int mx, int my, int width, int height,
                                   List<WikiCategory> categories, WikiPage activePage,
                                   GuiTextField searchField, float sidebarScroll) {
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
        enableScissor(mc, 0, listTop, SIDEBAR_W, height, width, height);
        int y = listTop - (int) sidebarScroll;
        String query = searchField.getText().toLowerCase(java.util.Locale.ROOT).trim();
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
                y += ENTRY_H;
            }
        }
        disableScissor();
    }

    public static float computeSidebarMaxScroll(List<WikiCategory> categories, GuiTextField searchField,
                                                 int height, float sidebarScroll) {
        int listTop = HEADER_H + 2 + SEARCH_H + 4;
        int y = listTop;
        String query = searchField.getText().toLowerCase(java.util.Locale.ROOT).trim();
        boolean hasSearch = !query.isEmpty();

        for (WikiCategory cat : categories) {
            List<WikiPage> visiblePages = hasSearch ? filterPages(cat, query) : cat.pages;
            if (hasSearch && visiblePages.isEmpty()) continue;
            y += CAT_H;
            boolean open = hasSearch || !cat.collapsed;
            if (!open) continue;
            y += visiblePages.size() * ENTRY_H;
        }
        return Math.max(0, y - height + (int) sidebarScroll);
    }

    /* ═══════════════ 页眉 ═══════════════ */
    public static void drawPageHeader(Minecraft mc, int width, WikiPage activePage) {
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

    /* ═══════════════ 内容区域 ═══════════════ */
    public static float drawPageContent(Minecraft mc, List<RenderLine> lines, float scroll,
                                        int cx, int cy, int cr, int width, int height) {
        FontRenderer fr = mc.fontRenderer;
        int startX = cx + PAD, maxW = cr - cx - PAD * 2, y = cy + PAD - (int) scroll;
        RenderItem ri = mc.getRenderItem();

        for (RenderLine ln : lines) {
            int x = startX;

            if (y + ln.height < cy - 200 && ln.type != LineType.TABLE_ROW) {
                y += ln.type == LineType.LATEX_BLOCK ? Math.max(ln.height, 40) : ln.height;
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
                    drawLineParts(mc, ln.parts, x, y, maxW, fr, ri);
                    y += LINE_H;
                    break;
                case BLOCKQUOTE:
                    Gui.drawRect(x, y, x + 2, y + ln.height, QUOTE_LINE);
                    Gui.drawRect(x + 2, y, x + maxW, y + ln.height, QUOTE_BG);
                    drawLineParts(mc, ln.parts, x + 6, y, maxW - 6, fr, ri);
                    y += ln.height;
                    break;
                case CODE_BLOCK:
                    Gui.drawRect(x, y, x + maxW, y + ln.height, CODE_BG);
                    if (!ln.parts.isEmpty() && ln.parts.get(0).text != null) {
                        fr.drawString(ln.parts.get(0).text, x + 4, y, 0xFFBBBBBB);
                    }
                    y += ln.height;
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
                        int displayH = ln.imageH * displayW / Math.max(ln.imageW, 1);
                        Gui.drawModalRectWithCustomSizedTexture(x + (maxW - displayW) / 2, y, 0, 0, displayW, displayH, ln.imageW, ln.imageH);
                        y += displayH + 4;
                    } else {
                        y += 10;
                    }
                    break;
                case LATEX_BLOCK: {
                    String latex = ln.text;
                    if (latex != null && !latex.isEmpty()) {
                        int[] tex = WikiLatexRenderer.INSTANCE.getOrCreateTexture(latex, 0xFFFFFFFF, WikiLatexRenderer.BLOCK_SCALE);
                        if (tex != null) {
                            int texId = tex[0], texW = tex[1], texH = tex[2];
                            int displayW = Math.min(texW, maxW);
                            int displayH = texH * displayW / Math.max(texW, 1);
                            WikiLatexRenderer.INSTANCE.renderLatex(x + (maxW - displayW) / 2, y, displayW, displayH, texId);
                            y += displayH + 8;
                        } else {
                            fr.drawStringWithShadow("§c[公式解析失败: " + latex + "]", x, y, 0xFFFF5555);
                            y += LINE_H + 4;
                        }
                    } else {
                        y += 10;
                    }
                    break;
                }
                case TABLE_HEADER:
                case TABLE_ROW: {
                    String data = ln.text != null ? ln.text : "";
                    String[] cols = data.split("\t");
                    int colCount = Math.max(cols.length, 1), colW = maxW / colCount;
                    int bg = (ln.type == LineType.TABLE_HEADER) ? TBL_HEADER_BG : (ln.extra % 2 == 0 ? TBL_ROW_A : TBL_ROW_B);
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
        return Math.max(0, y + (int) scroll - cy - (height - cy) + PAD);
    }

    /* ═══════════════ 行内片段绘制 ═══════════════ */
    private static void drawLineParts(Minecraft mc, List<TextPart> parts, int x, int y,
                                      int maxW, FontRenderer fr, RenderItem ri) {
        int curX = x;
        GlStateManager.disableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        for (TextPart p : parts) {
            if (p.type == PartType.TEXT) {
                fr.drawString(p.text, curX, y, p.color);
                curX += fr.getStringWidth(p.text);
            } else if (p.type == PartType.LATEX && p.text != null && !p.text.isEmpty()) {
                int[] tex = WikiLatexRenderer.INSTANCE.getOrCreateTexture(p.text, p.color, WikiLatexRenderer.INLINE_SCALE);
                if (tex != null) {
                    int texId = tex[0], texW = tex[1], texH = tex[2];
                    int targetH = fr.FONT_HEIGHT + 2;
                    int displayH = Math.min(texH, targetH);
                    int displayW = texW * displayH / Math.max(texH, 1);
                    if (curX + displayW > x + maxW) {
                        displayW = x + maxW - curX;
                        displayH = texH * displayW / Math.max(texW, 1);
                    }
                    if (displayW > 0 && displayH > 0) {
                        WikiLatexRenderer.INSTANCE.renderLatex(curX, y + (fr.FONT_HEIGHT - displayH) / 2, displayW, displayH, texId);
                        curX += displayW + 2;
                    } else {
                        curX += 10;
                    }
                } else {
                    fr.drawString("§c[?]§r", curX, y, p.color);
                    curX += fr.getStringWidth("[?]");
                }
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

    /* ═══════════════ 滚动条 ═══════════════ */
    public static void drawScrollbar(int top, int bot, float scrollVal, float maxVal, int barX) {
        if (maxVal <= 0) return;
        int h = bot - top;
        Gui.drawRect(barX, top, barX + SCROLLBAR_W, bot, SCROLLBAR_BG);
        float vf = (float) h / (h + maxVal);
        int thumbH = Math.max(16, (int) (h * vf));
        float sf = scrollVal / maxVal;
        int thumbY = top + (int) (sf * (h - thumbH));
        Gui.drawRect(barX + 1, thumbY, barX + SCROLLBAR_W - 1, thumbY + thumbH, SCROLLBAR_FG);
    }

    /* ═══════════════ 裁剪工具 ═══════════════ */
    public static void enableScissor(Minecraft mc, int l, int t, int r, int b, int width, int height) {
        double s = mc.displayWidth / (double) width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (l * s), (int) ((height - b) * s), (int) ((r - l) * s), (int) ((b - t) * s));
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /* ═══════════════ Toast 通知 ═══════════════ */
    public static int drawToast(Minecraft mc, int width, String message, int timer) {
        if (timer <= 0) return timer;
        FontRenderer fr = mc.fontRenderer;
        int toastW = fr.getStringWidth(message) + 20;
        int toastX = (width - toastW) / 2, toastY = 4;
        int alpha = timer > 20 ? 0xDD : (int) (0xDD * (timer / 20f));
        if (alpha > 0) {
            Gui.drawRect(toastX, toastY, toastX + toastW, toastY + 18, TOAST_BG);
            Gui.drawRect(toastX, toastY, toastX + toastW, toastY + 1, TOAST_BORDER);
            Gui.drawRect(toastX, toastY + 17, toastX + toastW, toastY + 18, TOAST_BORDER);
            fr.drawStringWithShadow(message, toastX + 10, toastY + 5, ACCENT);
        }
        return timer - 1;
    }

    /* ═══════════════ 搜索过滤 ═══════════════ */
    public static List<WikiPage> filterPages(WikiCategory cat, String query) {
        List<WikiPage> result = new java.util.ArrayList<>();
        for (WikiPage p : cat.pages)
            if (p.title.toLowerCase(java.util.Locale.ROOT).contains(query)
                    || p.id.toLowerCase(java.util.Locale.ROOT).contains(query))
                result.add(p);
        return result;
    }
}
