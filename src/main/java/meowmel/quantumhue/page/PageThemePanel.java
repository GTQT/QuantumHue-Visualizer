package meowmel.quantumhue.page;

import net.minecraft.client.gui.FontRenderer;

/**
 * 商店 / 公告页内嵌的配色设置浮层。
 *
 * <p>是两个页面共用的同一块面板：一行色卡加一条预览，点色卡立刻改主题并写盘，没有“应用”这一步
 * 可以忘。做成浮层而不是另开一个 {@code GuiScreen}，是因为切换屏幕会重新走一遍 {@code initGui}，
 * 商店的搜索框内容、页签数据与滚动位置都会被打回初始值；浮层关掉以后页面原样还在。
 */
final class PageThemePanel {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 188;
    private static final int SWATCH = 18;
    private static final int SWATCH_GAP = 4;

    private static final int ROW_THEME_Y = 46;
    private static final int PREVIEW_Y = 86;
    private static final int PREVIEW_H = 54;
    private static final int BUTTON_Y = 152;
    private static final int BUTTON_H = 18;
    private static final int RESET_W = 72;
    private static final int DONE_W = 88;

    /** 面板底色不跟主题走：它要始终比页面更暗，色卡才看得准。 */
    private static final int PANEL_BG = 0xFF121A24;
    private static final int PRINT_BG = 0xFF0B0F14;

    private boolean open;
    private int panelX;
    private int panelY;

    boolean isOpen() {
        return open;
    }

    /** 在宿主页面中央打开。 */
    void open(PageScreen host) {
        panelX = Math.max(4, (host.width - PANEL_W) / 2);
        panelY = Math.max(4, (host.height - PANEL_H) / 2);
        open = true;
    }

    void close() {
        open = false;
    }

    // ================================================================ 绘制

    void draw(PageScreen host, int mouseX, int mouseY) {
        FontRenderer font = host.font();
        PageThemeSettings settings = PageThemeSettings.get();

        // 盖住整页：浮层是模态的，下面的列表不响应鼠标，也不该抢注意力
        host.fill(0, 0, host.width, host.height, 0xC0080B0F);
        host.fill(panelX - 2, panelY - 2, PANEL_W + 4, PANEL_H + 4, PageTheme.GLOW);
        host.fill(panelX - 1, panelY - 1, PANEL_W + 2, PANEL_H + 2, PageTheme.BORDER);
        host.fillGradient(panelX, panelY, PANEL_W, PANEL_H, PANEL_BG, PageTheme.BG_BOT);
        host.fill(panelX + 1, panelY, PANEL_W - 2, 1, PageTheme.TOP_HIGHLIGHT);
        host.fill(panelX, panelY, 1, PANEL_H, PageTheme.SEPARATOR);
        host.fill(panelX + PANEL_W - 1, panelY, 1, PANEL_H, PageTheme.SEPARATOR);

        font.drawString("界面配色", panelX + 18, panelY + 14, PageTheme.ACCENT, true);
        font.drawString("改完立刻生效，自动保存", panelX + 18, panelY + 26, PageTheme.TEXT_FAINT, false);

        drawSwatches(host, font, settings, mouseX, mouseY);
        drawPreview(host, font, settings);

        boolean resetHover = in(mouseX, mouseY, panelX + 18, panelY + BUTTON_Y, RESET_W, BUTTON_H);
        drawButton(host, font, panelX + 18, panelY + BUTTON_Y, RESET_W, "重置", resetHover,
                PageTheme.ITEM_BG, PageTheme.ITEM_HOVER, PageTheme.TEXT);

        int doneX = panelX + PANEL_W - 18 - DONE_W;
        boolean doneHover = in(mouseX, mouseY, doneX, panelY + BUTTON_Y, DONE_W, BUTTON_H);
        drawButton(host, font, doneX, panelY + BUTTON_Y, DONE_W, "完成", doneHover,
                PageTheme.BTN_PRIMARY, PageTheme.BTN_PRIMARY_HOVER, 0xFFFFFFFF);

        font.drawString("ESC 返回", panelX + 18 + RESET_W + 8, panelY + BUTTON_Y + 5, PageTheme.TEXT_FAINT,
                false);
    }

    private void drawSwatches(PageScreen host, FontRenderer font, PageThemeSettings settings, int mouseX,
                              int mouseY) {
        int y = panelY + ROW_THEME_Y;
        font.drawString("配色方案", panelX + 18, y + 5, PageTheme.TEXT, false);

        for (int i = 0; i < PageThemeSettings.ACCENTS.length; i++) {
            int swatch = PageThemeSettings.ACCENTS[i];
            int sx = swatchLeft() + i * (SWATCH + SWATCH_GAP);
            boolean selected = swatch == settings.accentColor();
            boolean hovered = in(mouseX, mouseY, sx, y, SWATCH, SWATCH);

            host.fill(sx, y, SWATCH, SWATCH, 0xFF000000 | swatch);
            if (selected) {
                // 选中框用白色而不是强调色：强调色正是被比较的对象，用自己框自己没有对比
                host.fill(sx - 2, y - 2, SWATCH + 4, 1, 0xFFFFFFFF);
                host.fill(sx - 2, y + SWATCH + 1, SWATCH + 4, 1, 0xFFFFFFFF);
                host.fill(sx - 2, y, 1, SWATCH, 0xFFFFFFFF);
                host.fill(sx + SWATCH + 1, y, 1, SWATCH, 0xFFFFFFFF);
            } else if (hovered) {
                host.fill(sx, y, SWATCH, 1, 0x66FFFFFF);
                host.fill(sx, y + SWATCH - 1, SWATCH, 1, 0x66FFFFFF);
                host.fill(sx, y, 1, SWATCH, 0x66FFFFFF);
                host.fill(sx + SWATCH - 1, y, 1, SWATCH, 0x66FFFFFF);
            }
        }

        font.drawString(settings.accentName(), panelX + 78, y + 5, PageTheme.ACCENT, false);
    }

    /** 预览条：标题、激活页签、主按钮、滚动条、三档文字各来一眼，免得只看得到一排色卡。 */
    private void drawPreview(PageScreen host, FontRenderer font, PageThemeSettings settings) {
        int x = panelX + 18;
        int y = panelY + PREVIEW_Y;
        int w = PANEL_W - 36;

        host.fill(x, y, w, PREVIEW_H, PRINT_BG);
        host.fill(x, y, w, 1, PageTheme.TOP_HIGHLIGHT);
        host.fill(x, y, 1, PREVIEW_H, PageTheme.SEPARATOR);
        host.fill(x + w - 1, y, 1, PREVIEW_H, PageTheme.SEPARATOR);

        host.fill(x + 10, y + 8, 3, 10, PageTheme.ACCENT);
        font.drawString("示例标题", x + 17, y + 8, PageTheme.TITLE, false);
        font.drawString(settings.accentName() + " 配色预览", x + 17 + font.getStringWidth("示例标题") + 8,
                y + 9, PageTheme.TEXT_FAINT, false);

        host.fill(x + 10, y + 24, 96, 16, PageTheme.TAB_ACTIVE);
        host.fill(x + 10, y + 24, 2, 16, PageTheme.ACCENT);
        font.drawString("页签", x + 18, y + 28, 0xFFFFFFFF, false);

        host.fill(x + 114, y + 24, 74, 16, PageTheme.BTN_PRIMARY);
        font.drawString("按钮", x + 114 + (74 - font.getStringWidth("按钮")) / 2, y + 28, PageTheme.TITLE,
                false);

        host.fill(x + 266, y + 22, PageTheme.SCROLL_W, 22, PageTheme.SCROLL_TRACK);
        host.fill(x + 266, y + 22, PageTheme.SCROLL_W, 12, PageTheme.SCROLL_THUMB);

        font.drawString("正文颜色", x + 10, y + 44, PageTheme.TEXT, false);
        font.drawString("次要文字", x + 60, y + 44, PageTheme.TEXT_DIM, false);
        font.drawString("提示文字", x + 110, y + 44, PageTheme.TEXT_FAINT, false);
    }

    private void drawButton(PageScreen host, FontRenderer font, int x, int y, int w, String label,
                            boolean hovered, int base, int hoverColor, int textColor) {
        host.fill(x, y, w, BUTTON_H, hovered ? hoverColor : base);
        host.fill(x, y, w, 1, hovered ? PageTheme.ACCENT : PageTheme.SEPARATOR);
        font.drawString(label, x + (w - font.getStringWidth(label)) / 2,
                y + (BUTTON_H - font.FONT_HEIGHT) / 2 + 1, textColor, false);
    }

    // ================================================================ 输入

    /** 色卡行右对齐：色卡数量变了也不用改版式。 */
    private int swatchLeft() {
        int total = PageThemeSettings.ACCENTS.length * SWATCH
                + (PageThemeSettings.ACCENTS.length - 1) * SWATCH_GAP;
        return panelX + PANEL_W - 18 - total;
    }

    /** 返回是否消费了这次点击；浮层打开时外面的点击一律吞掉。 */
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        PageThemeSettings settings = PageThemeSettings.get();

        for (int i = 0; i < PageThemeSettings.ACCENTS.length; i++) {
            if (in(mouseX, mouseY, swatchLeft() + i * (SWATCH + SWATCH_GAP), panelY + ROW_THEME_Y, SWATCH,
                    SWATCH)) {
                settings.setAccentColor(PageThemeSettings.ACCENTS[i]);
                return true;
            }
        }
        if (in(mouseX, mouseY, panelX + 18, panelY + BUTTON_Y, RESET_W, BUTTON_H)) {
            settings.reset();
            return true;
        }
        if (in(mouseX, mouseY, panelX + PANEL_W - 18 - DONE_W, panelY + BUTTON_Y, DONE_W, BUTTON_H)) {
            close();
            return true;
        }
        return true;
    }

    /** ESC 收起浮层；返回是否已处理。 */
    boolean keyPressed(int keyCode) {
        if (open && keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
