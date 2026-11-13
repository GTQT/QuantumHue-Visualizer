package meowmel.quantumhue.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * 聊天界面内嵌的“聊天设置”浮层。
 *
 * <p>与商店 / 公告页的 {@code PageThemePanel} 同一种做法：做成浮层而不是另开 {@code GuiScreen}，
 * 关掉以后聊天滚动位置、输入框草稿、正在看的频道都还在原处。
 *
 * <p>三页：
 * <ul>
 *   <li>配色 — 七套完整预设 + 六个颜色角色 + RGB 滑块 + 实时预览；点一下立刻生效，松手写盘。</li>
 *   <li>外观 — 面板不透明度、消息密度、气泡圆角、时间分隔线、@提及高亮。</li>
 *   <li>布局 — 面板宽度、侧栏宽度、气泡宽度、显示头像、动画速度。</li>
 * </ul>
 * 面板自身固定深色，不跟主题走：色卡要在中性的底上看才准。
 */
final class ChatSettingsPanel {

    // ===== 版式 =====
    private static final int BASE_W = 352;
    private static final int BASE_H = 270;
    private static final int PAD = 14;

    private static final int TITLE_Y = 8;
    private static final int SUB_Y = 19;
    private static final int TABS_Y = 31;
    private static final int TAB_H = 18;
    private static final int TAB_W = 72;
    private static final int TAB_GAP = 4;

    // 配色页
    private static final int PRESET_Y = 54;
    private static final int PRESET_W = 20;
    private static final int PRESET_H = 16;
    private static final int PRESET_GAP = 3;
    private static final int ROLES_Y = 76;
    private static final int ROLE_H = 17;
    private static final int ROLES_W = 120;
    private static final int PREVIEW_GAP = 12;
    private static final int PREVIEW_H = 102;

    // RGB 滑块
    private static final int SLIDER_START_Y = 184;
    private static final int SLIDER_ROW_H = 14;
    private static final int SLIDER_H = 9;

    // 外观页
    private static final int APPEAR_Y = 58;
    private static final int APPEAR_ROW_H = 24;

    // 底部按钮
    private static final int BUTTON_H = 18;
    private static final int FOOTER_GAP = 10;

    // 固定色
    private static final int PANEL_BG = 0xFF121A24;
    private static final int HEADER_BG = 0xFF0C121A;
    private static final int CARD_BG = 0xFF080D13;
    private static final int BORDER = 0xFF2A3644;
    private static final int TEXT = 0xFFE6EBF2;
    private static final int TEXT_DIM = 0xFF8B97A8;
    private static final int TEXT_FAINT = 0xFF5D6877;

    private ChatScreen host;
    private FontRenderer font;
    private int panelW = BASE_W;
    private int panelH = BASE_H;
    private int panelX;
    private int panelY;
    private boolean open;

    private int tab;
    private int role = ChatThemeSettings.ROLE_ACCENT;
    /** 正在拖的滑块：0/1/2 = RGB，3 = 不透明度，-1 = 没在拖。 */
    private int dragging = -1;

    private long copiedAt = -1L;
    private long importedAt = -1L;

    // ================================================================ 生命周期

    boolean isOpen() {
        return open;
    }

    void open(ChatScreen host) {
        this.host = host;
        this.font = Minecraft.getMinecraft().fontRenderer;
        panelW = Math.min(BASE_W, Math.max(300, host.width - 8));
        panelH = Math.min(BASE_H, host.height - 4);
        panelX = Math.max(2, (host.width - panelW) / 2);
        panelY = Math.max(2, (host.height - panelH) / 2);
        open = true;
        tab = 0;
        role = ChatThemeSettings.ROLE_ACCENT;
        dragging = -1;
        copiedAt = -1L;
        importedAt = -1L;
    }

    void close() {
        if (dragging >= 0) {
            dragging = -1;
            ChatThemeSettings.get()
                    .save();
        }
        open = false;
    }

    // ================================================================ 绘制

    void draw(int mouseX, int mouseY) {
        ChatThemeSettings settings = ChatThemeSettings.get();
        int accent = ChatColors.ACCENT;

        // 模态遮罩：下面的聊天列表不响应鼠标
        Gui.drawRect(0, 0, host.width, host.height, 0xC0080B0F);

        // 外发光 + 边框 + 主体
        Gui.drawRect(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2,
                ChatRender.withAlpha(accent, 0x22));
        Gui.drawRect(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + 30, HEADER_BG);
        Gui.drawRect(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2,
                ChatRender.withAlpha(accent, 0x66));

        text("聊天设置", panelX + PAD, panelY + TITLE_Y, accent);
        text("配色、外观、布局与通知即时生效，自动保存", panelX + PAD + 64, panelY + TITLE_Y + 1, TEXT_FAINT);

        drawTabs(mouseX, mouseY, accent);
        if (tab == 0) {
            drawColorPage(settings, mouseX, mouseY, accent);
        } else if (tab == 1) {
            drawAppearancePage(settings, mouseX, mouseY, accent);
        } else if (tab == 2) {
            drawLayoutPage(settings, mouseX, mouseY, accent);
        } else {
            drawNotifyPage(settings, mouseX, mouseY, accent);
        }
        drawFooter(mouseX, mouseY, accent);
    }

    private void drawTabs(int mouseX, int mouseY, int accent) {
        String[] labels = { "配色", "外观", "布局", "通知" };
        for (int i = 0; i < labels.length; i++) {
            int tx = panelX + PAD + i * (TAB_W + TAB_GAP);
            int ty = panelY + TABS_Y;
            boolean selected = tab == i;
            boolean hovered = in(mouseX, mouseY, tx, ty, TAB_W, TAB_H);
            Gui.drawRect(tx, ty, tx + TAB_W, ty + TAB_H, selected ? CHAT_TAB_ACTIVE : HEADER_BG);
            Gui.drawRect(tx, ty, tx + TAB_W, ty + 1, selected ? accent : BORDER);
            if (hovered && !selected) {
                Gui.drawRect(tx, ty, tx + TAB_W, ty + TAB_H, 0x14FFFFFF);
            }
            text(labels[i], tx + (TAB_W - font.getStringWidth(labels[i])) / 2, ty + 5,
                    selected ? TEXT : TEXT_DIM);
        }
    }

    private static final int CHAT_TAB_ACTIVE = 0xFF1B2634;

    // ---------------------------------------------------------------- 配色页

    private void drawColorPage(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        drawPresets(settings, mouseX, mouseY, accent);
        drawRoles(settings, mouseX, mouseY, accent);
        drawPreview(settings);
        drawSliders(settings, mouseX, mouseY, accent);
    }

    private void drawPresets(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        int y = panelY + PRESET_Y;
        text("预设", panelX + PAD, y + 4, TEXT_DIM);
        int startX = panelX + PAD + 34;
        int selected = settings.presetIndex();
        for (int i = 0; i < ChatThemeSettings.PRESETS.length; i++) {
            ChatThemeSettings.Preset preset = ChatThemeSettings.PRESETS[i];
            int sx = startX + i * (PRESET_W + PRESET_GAP);
            boolean hovered = in(mouseX, mouseY, sx, y, PRESET_W, PRESET_H);
            // 三个色块拼一条：底色 + 强调色 + 我的气泡
            Gui.drawRect(sx, y, sx + PRESET_W, y + PRESET_H, 0xFF000000 | preset.bg);
            Gui.drawRect(sx + 3, y + 3, sx + PRESET_W - 3, y + 7, 0xFF000000 | preset.accent);
            Gui.drawRect(sx + 3, y + 8, sx + 6, y + PRESET_H - 3, 0xFF000000 | preset.other);
            Gui.drawRect(sx + 7, y + 8, sx + 11, y + PRESET_H - 3, 0xFF000000 | preset.own);
            if (selected == i) {
                // 当前方案用白色框：强调色正是被比较的对象
                frame(sx - 1, y - 1, PRESET_W + 2, PRESET_H + 2, 0xFFFFFFFF);
            } else if (hovered) {
                frame(sx - 1, y - 1, PRESET_W + 2, PRESET_H + 2, 0x66FFFFFF);
            }
        }
        int nameX = startX + ChatThemeSettings.PRESETS.length * (PRESET_W + PRESET_GAP) + 8;
        if (nameX + 30 < panelX + panelW - PAD) {
            String name = settings.presetName();
            text(name, nameX, y + 4, accent);
        }
    }

    private void drawRoles(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        for (int i = 0; i < ChatThemeSettings.ROLE_COUNT; i++) {
            int ry = panelY + ROLES_Y + i * ROLE_H;
            int rx = panelX + PAD;
            boolean selected = role == i;
            boolean hovered = in(mouseX, mouseY, rx, ry, ROLES_W, ROLE_H - 1);
            if (selected) {
                Gui.drawRect(rx, ry, rx + ROLES_W, ry + ROLE_H - 1, ChatRender.withAlpha(accent, 0x2A));
                Gui.drawRect(rx, ry, rx + 2, ry + ROLE_H - 1, accent);
            } else if (hovered) {
                Gui.drawRect(rx, ry, rx + ROLES_W, ry + ROLE_H - 1, 0x14FFFFFF);
            }
            int color = settings.color(i);
            Gui.drawRect(rx + 8, ry + 3, rx + 18, ry + 13, 0xFF000000);
            Gui.drawRect(rx + 9, ry + 4, rx + 17, ry + 12, 0xFF000000 | color);
            text(ChatThemeSettings.ROLE_NAMES[i], rx + 24, ry + 4, selected ? TEXT : TEXT_DIM);
        }
    }

    private void drawPreview(ChatThemeSettings settings) {
        int x = panelX + PAD + ROLES_W + PREVIEW_GAP;
        int y = panelY + ROLES_Y;
        int w = panelX + panelW - PAD - x;
        int h = PREVIEW_H;
        if (w < 60) {
            return;
        }

        Gui.drawRect(x, y, x + w, y + h, CARD_BG);
        Gui.drawRect(x, y, x + w, y + 1, BORDER);
        // 里面是一块缩小的聊天窗：底色带面板透明度，气泡用当前配色
        int alpha = settings.panelAlpha();
        int bodyTop = y + 13;
        Gui.drawRect(x + 4, bodyTop, x + w - 4, y + h - 4,
                ChatRender.withAlpha(ChatColors.PANEL_BG, alpha));
        Gui.drawRect(x + 4, y + 4, x + w - 4, bodyTop, ChatRender.withAlpha(ChatColors.SIDEBAR_BG, alpha));
        Gui.drawRect(x + 4, bodyTop - 1, x + w - 4, bodyTop, ChatColors.DIVIDER);
        text("聊天预览", x + 8, y + 5, ChatColors.TEXT_HEADER);
        text("✕", x + w - 14, y + 5, ChatColors.TEXT_SECONDARY);

        int radius = settings.cornerRadius();
        // 时间分隔
        String time = "20:24";
        int tw = font.getStringWidth(time);
        int timeX = x + (w - tw) / 2;
        Gui.drawRect(timeX - 4, y + 19, timeX + tw + 4, y + 28, ChatColors.TIME_SEP_BG);
        text(time, timeX, y + 20, ChatColors.TIME_TEXT);

        // 别人的消息（左）+ 被 @ 时的强调描边
        int bubbleY = y + 34;
        int otherW = Math.min(w - 24, font.getStringWidth("晚上开荒吗？") + 12);
        text("阿明", x + 8, bubbleY - 10, ChatColors.NAME_TEXT);
        Gui.drawRect(x + 8, bubbleY, x + 8 + otherW, bubbleY + 16,
                ChatColors.BUBBLE_BORDER_OTHER);
        ChatRender.roundRect(x + 9, bubbleY + 1, x + 8 + otherW - 1, bubbleY + 15,
                ChatColors.OTHER_BUBBLE, radius);
        font.drawStringWithShadow("晚上开荒吗？", x + 12, bubbleY + 4, ChatColors.BUBBLE_TEXT);

        // 自己的消息（右）
        int ownW = Math.min(w - 24, font.getStringWidth("马上到！") + 12);
        int ownX = x + w - 8 - ownW;
        int ownY = bubbleY + 28;
        text("我", ownX + ownW - font.getStringWidth("我"), ownY - 10, ChatColors.NAME_TEXT);
        ChatRender.roundRect(ownX, ownY, ownX + ownW, ownY + 16, ChatColors.OWN_BUBBLE, radius);
        font.drawStringWithShadow("马上到！", ownX + 6, ownY + 4, ChatColors.BUBBLE_TEXT);

        // 输入栏
        int inputY = y + h - 20;
        Gui.drawRect(x + 4, inputY, x + w - 4, y + h - 4, ChatRender.withAlpha(ChatColors.BAR_BG, alpha));
        Gui.drawRect(x + 4, inputY, x + w - 4, inputY + 1, ChatColors.DIVIDER);
        Gui.drawRect(x + 8, inputY + 4, x + w - 8, inputY + 12, ChatRender.withAlpha(ChatColors.INPUT_BG, alpha));
        Gui.drawRect(x + 8, inputY + 4, x + w - 8, inputY + 5, ChatColors.INPUT_BORDER);
        text("输入消息…", x + 10, inputY + 4, ChatColors.TEXT_SECONDARY);
    }

    private void drawSliders(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        int color = settings.color(role);
        String[] labels = { "R", "G", "B" };
        int[] values = { (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF };
        int base = color;

        for (int i = 0; i < 3; i++) {
            int rowY = panelY + SLIDER_START_Y + i * SLIDER_ROW_H;
            int trackX = sliderTrackX();
            int trackW = sliderTrackW();
            text(labels[i], panelX + PAD, rowY + 1, TEXT_DIM);

            // 轨道：固定另外两个通道，只让本通道从 0 渐变到 255
            int shift = i == 0 ? 16 : (i == 1 ? 8 : 0);
            int low = (base & ~(0xFF << shift)) | (0 << shift);
            int high = (base & ~(0xFF << shift)) | (255 << shift);
            ChatRender.gradientH(trackX, rowY, trackX + trackW, rowY + SLIDER_H,
                    withAlphaForTrack(low, accent), withAlphaForTrack(high, accent));
            frame(trackX - 1, rowY - 1, trackW + 2, SLIDER_H + 2, BORDER);

            int hx = trackX + (int) (trackW * values[i] / 255f);
            Gui.drawRect(hx - 1, rowY - 1, hx + 1, rowY + SLIDER_H + 1, 0xFF000000);
            Gui.drawRect(hx - 1, rowY, hx, rowY + SLIDER_H, 0xFFFFFFFF);
            Gui.drawRect(hx, rowY, hx + 1, rowY + SLIDER_H, 0xFFFFFFFF);

            String hex = String.format("%02X", values[i]);
            text(hex, trackX + trackW + 8, rowY + 1, TEXT);
        }

        // 放在滑块下方，避免压住预览卡片
        String full = String.format("#%06X", color);
        text("色值 " + full, panelX + PAD, panelY + SLIDER_START_Y + 3 * SLIDER_ROW_H, accent);
    }

    /** 滑块轨道颜色太艳会盖掉把手，压一点亮度。 */
    private static int withAlphaForTrack(int color, int accent) {
        return ChatRender.lerp(0xFF000000 | (color & 0xFFFFFF), ChatRender.withAlpha(accent, 0xFF), 0.10f);
    }

    // ---------------------------------------------------------------- 外观页

    private void drawAppearancePage(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        int x = panelX + PAD;
        int valueX = panelX + 150;

        // 面板不透明度
        int rowY = panelY + APPEAR_Y;
        text("面板不透明度", x, rowY + 3, TEXT);
        int trackX = valueX;
        int trackW = panelX + panelW - PAD - valueX - 34;
        Gui.drawRect(trackX, rowY, trackX + trackW, rowY + SLIDER_H, 0xFF06090D);
        frame(trackX - 1, rowY - 1, trackW + 2, SLIDER_H + 2, BORDER);
        int fill = (int) (trackW * (settings.panelOpacity() - 40) / 60f);
        fill = Math.max(0, Math.min(trackW, fill));
        Gui.drawRect(trackX, rowY, trackX + fill, rowY + SLIDER_H, ChatRender.withAlpha(accent, 0xAA));
        int handleX = Math.max(1, Math.min(trackW - 1, fill));
        Gui.drawRect(trackX + handleX - 1, rowY - 1, trackX + handleX + 1, rowY + SLIDER_H + 1,
                0xFFFFFFFF);
        text(settings.panelOpacity() + "%", trackX + trackW + 8, rowY + 1, TEXT);

        // 消息密度
        rowY += APPEAR_ROW_H;
        text("消息密度", x, rowY + 3, TEXT);
        drawSegments(valueX, rowY, ChatThemeSettings.DENSITY_NAMES, settings.density(), mouseX, mouseY,
                accent);

        // 气泡圆角
        rowY += APPEAR_ROW_H;
        text("气泡圆角", x, rowY + 3, TEXT);
        drawSegments(valueX, rowY, ChatThemeSettings.CORNER_NAMES, settings.corner(), mouseX, mouseY,
                accent);

        // 时间分隔线
        rowY += APPEAR_ROW_H;
        text("时间分隔线", x, rowY + 3, TEXT);
        drawToggle(valueX, rowY, settings.timeSeparators(), mouseX, mouseY, accent);

        // @提及高亮
        rowY += APPEAR_ROW_H;
        text("@提及高亮", x, rowY + 3, TEXT);
        drawToggle(valueX, rowY, settings.highlightMentions(), mouseX, mouseY, accent);
        int hintY = rowY + 20;
        text("别人 @ 你时，消息气泡会描一圈强调色", x, hintY, TEXT_FAINT);

        // 色码提示
        String code = settings.exportCode();
        text("色码 " + code, x, panelY + APPEAR_Y + 4 * APPEAR_ROW_H + 34, TEXT_FAINT);
    }

    // ---------------------------------------------------------------- 通知页

    private static final int[] HUD_LINE_OPTIONS = { 3, 5, 8 };
    private static final String[] HUD_LINE_LABELS = { "3 行", "5 行", "8 行" };

    private void drawNotifyPage(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        int x = panelX + PAD;
        int valueX = panelX + 150;

        int rowY = panelY + APPEAR_Y;
        text("HUD 消息", x, rowY + 3, TEXT);
        drawToggle(valueX, rowY, settings.hudEnabled(), mouseX, mouseY, accent);
        text("关闭聊天后，左下角保留最近几条消息", x, rowY + 20, TEXT_FAINT);

        rowY += APPEAR_ROW_H + 10;
        text("显示行数", x, rowY + 3, TEXT);
        drawSegments(valueX, rowY, HUD_LINE_LABELS, hudLineLevel(settings.hudLines()), mouseX, mouseY,
                accent);
        text("行数越多越占地方，5 行差不多", x, rowY + 20, TEXT_FAINT);

        rowY += APPEAR_ROW_H + 10;
        text("提示音", x, rowY + 3, TEXT);
        drawToggle(valueX, rowY, settings.hudSound(), mouseX, mouseY, accent);
        text("只有私聊和被 @ 才会响，冷却 0.9 秒", x, rowY + 20, TEXT_FAINT);

        rowY += APPEAR_ROW_H + 12;
        text("HUD 只做预览；完整频道记录仍按 T 打开聊天界面", x, rowY, TEXT_FAINT);
    }

    private int hudLineLevel(int lines) {
        int best = 0;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < HUD_LINE_OPTIONS.length; i++) {
            int gap = Math.abs(HUD_LINE_OPTIONS[i] - lines);
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    private void handleNotifyClick(ChatThemeSettings settings, int mouseX, int mouseY) {
        int valueX = panelX + 150;
        int rowY = panelY + APPEAR_Y;

        if (in(mouseX, mouseY, valueX, rowY, 40, 14)) {
            settings.setHudEnabled(!settings.hudEnabled());
            settings.save();
            return;
        }

        rowY += APPEAR_ROW_H + 10;
        int segW = 44;
        for (int i = 0; i < HUD_LINE_OPTIONS.length; i++) {
            if (in(mouseX, mouseY, valueX + i * (segW + 4), rowY, segW, 14)) {
                settings.setHudLines(HUD_LINE_OPTIONS[i]);
                settings.save();
                return;
            }
        }

        rowY += APPEAR_ROW_H + 10;
        if (in(mouseX, mouseY, valueX, rowY, 40, 14)) {
            settings.setHudSound(!settings.hudSound());
            settings.save();
        }
    }

    // ---------------------------------------------------------------- 布局页

    private static final String[] ANIMATION_NAMES = { "快", "标准", "慢" };

    private void drawLayoutPage(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        int x = panelX + PAD;
        int valueX = panelX + 150;
        int trackW = panelX + panelW - PAD - valueX - 34;
        int rowY = panelY + APPEAR_Y;

        // 面板宽度（相对于游戏窗口）
        text("面板宽度", x, rowY + 3, TEXT);
        drawValueSlider(valueX, rowY, trackW, (settings.panelWidthPercent() - 30) / 60f,
                settings.panelWidthPercent() + "%", accent);

        // 侧栏宽度
        rowY += APPEAR_ROW_H;
        text("侧栏宽度", x, rowY + 3, TEXT);
        drawValueSlider(valueX, rowY, trackW, (settings.sidebarWidth() - 80) / 170f,
                settings.sidebarWidth() + "px", accent);

        // 气泡宽度
        rowY += APPEAR_ROW_H;
        text("气泡宽度", x, rowY + 3, TEXT);
        drawValueSlider(valueX, rowY, trackW, (settings.bubbleWidthPercent() - 45) / 55f,
                settings.bubbleWidthPercent() + "%", accent);

        // 显示头像
        rowY += APPEAR_ROW_H;
        text("显示头像", x, rowY + 3, TEXT);
        drawToggle(valueX, rowY, settings.showAvatars(), mouseX, mouseY, accent);

        // 动画速度
        rowY += APPEAR_ROW_H;
        text("动画速度", x, rowY + 3, TEXT);
        drawSegments(valueX, rowY, ANIMATION_NAMES, settings.animationLevel(), mouseX, mouseY, accent);

        text("改宽度会即时重排；气泡 100% 表示铺满可用宽度", x, rowY + 20, TEXT_FAINT);
    }

    private void drawValueSlider(int x, int y, int trackW, float ratio, String value, int accent) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        Gui.drawRect(x, y, x + trackW, y + SLIDER_H, 0xFF06090D);
        frame(x - 1, y - 1, trackW + 2, SLIDER_H + 2, BORDER);
        int fill = (int) (trackW * ratio);
        Gui.drawRect(x, y, x + fill, y + SLIDER_H, ChatRender.withAlpha(accent, 0xAA));
        int handleX = Math.max(1, Math.min(trackW - 1, fill));
        Gui.drawRect(x + handleX - 1, y - 1, x + handleX + 1, y + SLIDER_H + 1, 0xFFFFFFFF);
        text(value, x + trackW + 8, y + 1, TEXT);
    }

    private void drawSegments(int x, int y, String[] labels, int selected, int mouseX, int mouseY,
                              int accent) {
        int segW = 44;
        for (int i = 0; i < labels.length; i++) {
            int sx = x + i * (segW + 4);
            boolean on = selected == i;
            boolean hovered = in(mouseX, mouseY, sx, y, segW, 14);
            Gui.drawRect(sx, y, sx + segW, y + 14, on ? ChatRender.withAlpha(accent, 0x55)
                    : (hovered ? 0x22FFFFFF : 0xFF1A2431));
            if (on) {
                frame(sx, y, segW, 14, accent);
            }
            text(labels[i], sx + (segW - font.getStringWidth(labels[i])) / 2, y + 3, on ? TEXT : TEXT_DIM);
        }
    }

    private void drawToggle(int x, int y, boolean on, int mouseX, int mouseY, int accent) {
        boolean hovered = in(mouseX, mouseY, x, y, 40, 14);
        Gui.drawRect(x, y, x + 40, y + 14, on ? ChatRender.withAlpha(accent, 0x55)
                : (hovered ? 0x22FFFFFF : 0xFF1A2431));
        if (on) {
            frame(x, y, 40, 14, accent);
        }
        String label = on ? "开" : "关";
        text(label, x + (40 - font.getStringWidth(label)) / 2, y + 3, on ? TEXT : TEXT_DIM);
    }

    // ---------------------------------------------------------------- 底栏

    private void drawFooter(int mouseX, int mouseY, int accent) {
        int y = panelY + panelH - FOOTER_GAP - BUTTON_H;
        int copyW = 72;
        int resetW = 72;
        int importW = 72;
        int doneW = 64;

        boolean copied = copiedAt > 0 && System.currentTimeMillis() - copiedAt < 1200L;
        drawButton(panelX + PAD, y, copyW, copied ? "已复制" : "复制色码",
                in(mouseX, mouseY, panelX + PAD, y, copyW, BUTTON_H),
                copied ? ChatRender.withAlpha(accent, 0xAA) : 0xFF1A2431, accent);

        drawButton(panelX + PAD + copyW + 6, y, resetW, "恢复默认",
                in(mouseX, mouseY, panelX + PAD + copyW + 6, y, resetW, BUTTON_H), 0xFF1A2431, accent);

        int importX = panelX + PAD + 2 * (copyW + 6);
        boolean imported = importedAt > 0 && System.currentTimeMillis() - importedAt < 1500L;
        drawButton(importX, y, importW, imported ? "已导入" : "粘贴色码",
                in(mouseX, mouseY, importX, y, importW, BUTTON_H),
                imported ? ChatRender.withAlpha(accent, 0xAA) : 0xFF1A2431, accent);

        int doneX = panelX + panelW - PAD - doneW;
        drawButton(doneX, y, doneW, "完成", in(mouseX, mouseY, doneX, y, doneW, BUTTON_H),
                ChatRender.withAlpha(accent, 0xDD), 0xFFFFFFFF);
    }

    private void drawButton(int x, int y, int w, String label, boolean hovered, int bg, int textColor) {
        Gui.drawRect(x, y, x + w, y + BUTTON_H, hovered ? 0xFF28374A : bg);
        Gui.drawRect(x, y, x + w, y + 1, hovered ? TEXT : BORDER);
        text(label, x + (w - font.getStringWidth(label)) / 2, y + 5, textColor);
    }

    // ================================================================ 输入

    /** 返回是否消费；浮层打开时一切点击都归它。 */
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        ChatThemeSettings settings = ChatThemeSettings.get();
        int accent = ChatColors.ACCENT;

        // 点面板外 → 关闭（等同于“完成”）
        if (!in(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            close();
            return true;
        }

        // 页签
        for (int i = 0; i < 4; i++) {
            if (in(mouseX, mouseY, panelX + PAD + i * (TAB_W + TAB_GAP), panelY + TABS_Y, TAB_W, TAB_H)) {
                tab = i;
                return true;
            }
        }

        // 底栏按钮
        int buttonY = panelY + panelH - FOOTER_GAP - BUTTON_H;
        int copyW = 72;
        int resetW = 72;
        int doneW = 64;
        if (in(mouseX, mouseY, panelX + PAD, buttonY, copyW, BUTTON_H)) {
            ChatScreen.setClipboardString(settings.exportCode());
            copiedAt = System.currentTimeMillis();
            return true;
        }
        if (in(mouseX, mouseY, panelX + PAD + copyW + 6, buttonY, resetW, BUTTON_H)) {
            resetAll(settings);
            return true;
        }
        int importX = panelX + PAD + 2 * (copyW + 6);
        if (in(mouseX, mouseY, importX, buttonY, 72, BUTTON_H)) {
            String clip = ChatScreen.getClipboardString();
            if (clip != null && settings.importCode(clip)) {
                importedAt = System.currentTimeMillis();
            }
            return true;
        }
        int doneX = panelX + panelW - PAD - doneW;
        if (in(mouseX, mouseY, doneX, buttonY, doneW, BUTTON_H)) {
            close();
            return true;
        }

        if (tab == 0) {
            handleColorClick(settings, mouseX, mouseY, accent);
        } else if (tab == 1) {
            handleAppearanceClick(settings, mouseX, mouseY);
        } else if (tab == 2) {
            handleLayoutClick(settings, mouseX, mouseY);
        } else {
            handleNotifyClick(settings, mouseX, mouseY);
        }
        return true;
    }

    private void handleColorClick(ChatThemeSettings settings, int mouseX, int mouseY, int accent) {
        // 预设
        int startX = panelX + PAD + 34;
        for (int i = 0; i < ChatThemeSettings.PRESETS.length; i++) {
            int sx = startX + i * (PRESET_W + PRESET_GAP);
            if (in(mouseX, mouseY, sx, panelY + PRESET_Y, PRESET_W, PRESET_H)) {
                settings.applyPreset(i);
                return;
            }
        }

        // 角色
        for (int i = 0; i < ChatThemeSettings.ROLE_COUNT; i++) {
            int ry = panelY + ROLES_Y + i * ROLE_H;
            if (in(mouseX, mouseY, panelX + PAD, ry, ROLES_W, ROLE_H - 1)) {
                role = i;
                return;
            }
        }

        // RGB 滑块
        for (int i = 0; i < 3; i++) {
            int rowY = panelY + SLIDER_START_Y + i * SLIDER_ROW_H;
            int trackX = sliderTrackX();
            int trackW = sliderTrackW();
            if (in(mouseX, mouseY, trackX - 2, rowY - 2, trackW + 4, SLIDER_H + 4)) {
                dragging = i;
                applySlider(settings, i, mouseX);
                return;
            }
        }
    }

    private void handleAppearanceClick(ChatThemeSettings settings, int mouseX, int mouseY) {
        int valueX = panelX + 150;
        int rowY = panelY + APPEAR_Y;

        // 不透明度滑块
        int trackW = panelX + panelW - PAD - valueX - 34;
        if (in(mouseX, mouseY, valueX - 2, rowY - 2, trackW + 4, SLIDER_H + 4)) {
            dragging = 3;
            applyOpacity(settings, mouseX);
            return;
        }

        // 消息密度
        rowY += APPEAR_ROW_H;
        for (int i = 0; i < ChatThemeSettings.DENSITY_NAMES.length; i++) {
            if (in(mouseX, mouseY, valueX + i * 48, rowY, 44, 14)) {
                settings.setDensity(i);
                settings.save();
                return;
            }
        }

        // 气泡圆角
        rowY += APPEAR_ROW_H;
        for (int i = 0; i < ChatThemeSettings.CORNER_NAMES.length; i++) {
            if (in(mouseX, mouseY, valueX + i * 48, rowY, 44, 14)) {
                settings.setCorner(i);
                settings.save();
                return;
            }
        }

        // 时间分隔线
        rowY += APPEAR_ROW_H;
        if (in(mouseX, mouseY, valueX, rowY, 40, 14)) {
            settings.setTimeSeparators(!settings.timeSeparators());
            settings.save();
            return;
        }

        // @提及高亮
        rowY += APPEAR_ROW_H;
        if (in(mouseX, mouseY, valueX, rowY, 40, 14)) {
            settings.setHighlightMentions(!settings.highlightMentions());
            settings.save();
        }
    }

    private void handleLayoutClick(ChatThemeSettings settings, int mouseX, int mouseY) {
        int valueX = panelX + 150;
        int trackW = panelX + panelW - PAD - valueX - 34;
        int rowY = panelY + APPEAR_Y;

        // 三个宽度滑块
        for (int id = 4; id <= 6; id++) {
            if (in(mouseX, mouseY, valueX - 2, rowY - 2, trackW + 4, SLIDER_H + 4)) {
                dragging = id;
                applyLayoutSlider(settings, id, mouseX);
                return;
            }
            rowY += APPEAR_ROW_H;
        }

        // 显示头像
        if (in(mouseX, mouseY, valueX, rowY, 40, 14)) {
            settings.setShowAvatars(!settings.showAvatars());
            settings.save();
            return;
        }

        // 动画速度
        rowY += APPEAR_ROW_H;
        for (int i = 0; i < ANIMATION_NAMES.length; i++) {
            if (in(mouseX, mouseY, valueX + i * 48, rowY, 44, 14)) {
                settings.setAnimationLevel(i);
                settings.save();
                return;
            }
        }
    }

    private void applyLayoutSlider(ChatThemeSettings settings, int slider, int mouseX) {
        int valueX = panelX + 150;
        int trackW = panelX + panelW - PAD - valueX - 34;
        float ratio = (mouseX - valueX) / (float) trackW;
        ratio = Math.max(0f, Math.min(1f, ratio));
        if (slider == 4) {
            settings.setPanelWidthPercent(30 + Math.round(60f * ratio));
        } else if (slider == 5) {
            settings.setSidebarWidth(80 + Math.round(170f * ratio));
        } else {
            settings.setBubbleWidthPercent(45 + Math.round(55f * ratio));
        }
    }

    /** 拖动中：实时改色但不写盘，松手才落盘（避免拖一次滑块写几十次文件）。 */
    boolean mouseDragged(int mouseX, int mouseY) {
        if (!open || dragging < 0) {
            return false;
        }
        ChatThemeSettings settings = ChatThemeSettings.get();
        if (dragging < 3) {
            applySlider(settings, dragging, mouseX);
        } else if (dragging == 3) {
            applyOpacity(settings, mouseX);
        } else {
            applyLayoutSlider(settings, dragging, mouseX);
        }
        return true;
    }

    /** 松手落盘；返回是否消费。 */
    boolean mouseReleased() {
        if (!open || dragging < 0) {
            return false;
        }
        dragging = -1;
        ChatThemeSettings.get()
                .save();
        return true;
    }

    private void applySlider(ChatThemeSettings settings, int channel, int mouseX) {
        int trackX = sliderTrackX();
        int trackW = sliderTrackW();
        int value = Math.round(255f * (mouseX - trackX) / trackW);
        value = value < 0 ? 0 : Math.min(value, 255);
        int shift = channel == 0 ? 16 : (channel == 1 ? 8 : 0);
        int color = settings.color(role);
        int updated = (color & ~(0xFF << shift)) | (value << shift);
        settings.setColor(role, updated);
    }

    private void applyOpacity(ChatThemeSettings settings, int mouseX) {
        int valueX = panelX + 150;
        int trackW = panelX + panelW - PAD - valueX - 34;
        int percent = 40 + Math.round(60f * (mouseX - valueX) / trackW);
        settings.setPanelOpacity(percent);
    }

    private void resetAll(ChatThemeSettings settings) {
        settings.resetToConfig();
        settings.setPanelOpacity(80);
        settings.setDensity(1);
        settings.setCorner(1);
        settings.setTimeSeparators(true);
        settings.setHighlightMentions(true);
        settings.setHudEnabled(true);
        settings.setHudLines(5);
        settings.setHudSound(true);
        settings.save();
        ChatColors.reload();
    }

    /** ESC 关闭；打开时吞掉其它按键，避免输入框在遮罩后面收到字符。 */
    boolean keyPressed(int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
        }
        return true;
    }

    // ================================================================ 工具

    private int sliderTrackX() {
        return panelX + PAD + 14;
    }

    private int sliderTrackW() {
        return panelX + panelW - PAD - 52 - sliderTrackX();
    }

    private void text(String value, int x, int y, int color) {
        font.drawStringWithShadow(value, x, y, color);
    }

    private void frame(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 1, color);
        Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color);
        Gui.drawRect(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
