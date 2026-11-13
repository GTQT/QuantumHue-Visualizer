package meowmel.quantumhue.chat;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.api.utils.MouseWheel;
import meowmel.quantumhue.chat.ChatChannel.ChannelType;
import meowmel.quantumhue.chat.ChatMessageStore.ChatMessage;
import meowmel.quantumhue.chat.packets.ChatGroupPacket;
import meowmel.quantumhue.chat.packets.ChatPrivatePacket;
import meowmel.quantumhue.chat.packets.GroupManagePacket;
import meowmel.quantumhue.chat.packets.TpRequestPacket;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * QuantumHue 聊天主界面 — 半屏半透明面板
 *
 * ┌──────────┬───────────────┐ ┌──────────────┐
 * │ SIDEBAR  │  Header       │ │              │
 * │ 170px    │──────────────│ │   游戏世界    │
 * │          │  消息气泡     │ │   可见        │
 * │          │              │ │              │
 * │          │──────────────│ │              │
 * │          │  Input bar   │ │              │
 * └──────────┴───────────────┘ └──────────────┘
 * ←───────── panelW ─────────→
 */
public class ChatScreen extends GuiScreen {

    // ===== 布局常量 =====
    /**
     * 频道栏宽度：优先用聊天设置里的值，同时保证消息区至少还能放下 160px，
     * 免得面板本身很窄时侧栏把主区域挤没。
     */
    private int sidebarW() {
        int preferred = ChatThemeSettings.get().sidebarWidth();
        int limit = panelW > 0 ? Math.max(80, panelW - 160) : 250;
        return MathHelper.clamp(preferred, 80, Math.min(250, limit));
    }
    private static final int HEADER_H = 28;
    private static final int BAR_H = 26;
    private static final int TOOLBAR_H = 26;
    private static final int PAD = 8;
    private static final int AVATAR = 16;
    private static final int BUBBLE_PAD_X = 6;
    private static final int BUBBLE_PAD_Y = 4;
    private static final int GAP = 4;
    private static final int NAME_H = AVATAR;
    private static final int SCROLLBAR_W = 5;
    private static final int INPUT_H = 14;
    private static final int SIDEBAR_ITEM_H = 20;
    private static final int SIDEBAR_CAT_H = 22;
    private static final int TIME_SEP_H = 14;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 打开 / 关闭的滑入动画时长，跟随聊天设置。 */
    private int animationMs() {
        return Math.max(1, ChatThemeSettings.get().animationMs());
    }

    // ===== 状态 =====
    private final String initialText;
    private int panelW;
    private GuiTextField inputField;

    // 滚动
    private float scrollOffset, scrollTarget, maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;

    // 侧边栏滚动
    private float sidebarScroll, sidebarScrollTarget, sidebarMaxScroll;

    // 动画
    private long animStart;
    private boolean closing;

    // 频道
    private ChatChannel activeChannel;
    private List<ChatChannel> channels;

    // 侧边栏折叠
    private boolean privateExpanded = true;
    private boolean groupExpanded = true;

    // 右键菜单
    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private static final int CTX_W = 80;
    private static final int CTX_ITEM_H = 16;

    // 头像右键菜单
    private int avatarContextIndex = -1;
    private int avatarContextX, avatarContextY;
    private static final int AVATAR_CTX_W = 80;

    // 气泡位置追踪 (用于头像点击检测)
    private final List<int[]> bubbleRects = new ArrayList<>();
    // 消息气泡矩形 (x, y, w, h, index) — 用于气泡右键命中检测
    private final List<int[]> messageBubbleRects = new ArrayList<>();

    // 创建群聊
    private boolean creatingGroup;
    private GuiTextField groupNameField;

    // 历史记录轮播
    private String historyBuffer = "";
    private int historyPos = -1;

    // 表情选择器
    private boolean showEmojiPicker;

    // 聊天设置浮层
    private final ChatSettingsPanel settingsPanel = new ChatSettingsPanel();

    // 搜索
    private GuiTextField searchField;
    private boolean searchOpen;
    /** 按回车后下一帧要滚到的消息下标（-1 表示没有）。 */
    private int pendingScrollToIndex = -1;
    private int searchCursor = -1;

    // Tab 补全
    private String tabBase = "";
    private String tabToken = "";
    private String tabSuffix = "";
    private List<String> tabCandidates = new ArrayList<>();
    private int tabIndex = -1;

    // ===== @ 候选弹窗 =====
    private final List<String> mentionCandidates = new ArrayList<>();
    private int mentionIndex;
    private int mentionStart = -1;

    public ChatScreen(String initialText) {
        this.initialText = initialText != null ? initialText : "";
    }

    private int panelRight() { return panelW; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // 面板宽度与输入框位置：从聊天设置读取（读取失败时回落到 Forge 配置）
        updateLayout();

        historyPos = mc.ingameGUI.getChatGUI().getSentMessages().size();
        ChatMessageStore.setScreenOpen(true);
        clearMention();
        animStart = System.currentTimeMillis();
        closing = false;
        firstRender = true;

        channels = ChatMessageStore.getChannels();
        activeChannel = ChatMessageStore.getActiveChannel();

        int inputX = sidebarW() + PAD;
        int inputW = panelRight() - sidebarW() - PAD * 2 - 36;
        int inputY = height - BAR_H + (BAR_H - INPUT_H) / 2;

        inputField = new GuiTextField(0, mc.fontRenderer, inputX, inputY + 3, inputW, INPUT_H);
        inputField.setMaxStringLength(256);
        inputField.setEnableBackgroundDrawing(false);
        inputField.setTextColor(ChatColors.TEXT_PRIMARY);
        inputField.setCanLoseFocus(false);
        inputField.setText(initialText);
        inputField.setFocused(true);

        groupNameField = new GuiTextField(1, mc.fontRenderer,
                width / 2 - 80, height / 2 - 10, 160, 16);
        groupNameField.setMaxStringLength(32);
        groupNameField.setEnableBackgroundDrawing(false);
        groupNameField.setTextColor(ChatColors.TEXT_PRIMARY);

        searchField = new GuiTextField(2, mc.fontRenderer,
                panelRight() - 240, 6, 160, 14);
        searchField.setMaxStringLength(64);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(ChatColors.TEXT_PRIMARY);
        searchField.setCanLoseFocus(true);
        updateLayout();
    }

    /** 重算面板宽度与输入框位置：在设置里拖布局滑条时每帧都会走一遍。 */
    private void updateLayout() {
        QuantumHueConfig.ChatConfig cfg = QuantumHueConfig.chat;
        panelW = MathHelper.clamp(width * ChatThemeSettings.get().panelWidthPercent() / 100,
                cfg.panelMinWidth, Math.min(cfg.panelMaxWidth, width));
        if (inputField != null) {
            inputField.x = sidebarW() + PAD;
            inputField.y = height - BAR_H + (BAR_H - INPUT_H) / 2 + 3;
            inputField.width = panelRight() - sidebarW() - PAD * 2 - 36;
        }
        if (searchField != null) {
            searchField.x = Math.max(sidebarW(), panelRight() - 240);
            searchField.y = 6;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ChatMessageStore.setScreenOpen(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        inputField.updateCursorCounter();
        if (searchOpen && searchField != null) searchField.updateCursorCounter();
        if (creatingGroup) groupNameField.updateCursorCounter();
    }

    // ===== 渲染入口 =====

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateLayout();
        float anim = ChatAnimation.progress(animStart, animationMs(), closing);
        int slideX = (int) ((anim - 1.0f) * panelW);
        ChatThemeSettings theme = ChatThemeSettings.get();

        // 平滑滚动
        scrollOffset += (scrollTarget - scrollOffset) * 0.3f;
        if (Math.abs(scrollTarget - scrollOffset) < 0.5f) scrollOffset = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * 0.3f;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.5f) sidebarScroll = sidebarScrollTarget;

        // 面板向左滑入
        GlStateManager.pushMatrix();
        GlStateManager.translate(slideX, 0, 0);

        // === 半透明面板背景 ===
        int panelBg = ChatColors.PANEL_BG;
        int alpha = (int) (theme.panelAlpha() * anim);
        Gui.drawRect(0, 0, panelRight(), height, ((alpha << 24) | (panelBg & 0x00FFFFFF)));
        // 右边缘亮线
        Gui.drawRect(panelRight() - 1, 0, panelRight(), height, ChatColors.PANEL_EDGE);

        drawSidebar(mouseX - slideX, mouseY);
        drawHeader(mouseX, mouseY);
        drawMessages(mouseX, mouseY);
        drawToolbar(mouseX, mouseY);
        drawInputBar(mouseX, mouseY);
        drawMentionPopup();
        drawContextMenu(mouseX, mouseY);
        drawAvatarContextMenu(mouseX, mouseY);
        drawEmojiPicker(mouseX - slideX, mouseY);

        if (creatingGroup) drawCreateGroupDialog(mouseX, mouseY);

        GlStateManager.popMatrix();

        // 设置浮层是屏幕级模态，不跟随滑入动画
        if (settingsPanel.isOpen()) settingsPanel.draw(mouseX, mouseY);

        if (closing && System.currentTimeMillis() - animStart >= animationMs()) {
            mc.displayGuiScreen(null);
        }
    }

    // ===== 侧边栏 =====

    private void drawSidebar(int mx, int my) {
        int surface = ChatThemeSettings.get().panelAlpha();
        Gui.drawRect(0, 0, sidebarW(), height, ChatRender.withAlpha(ChatColors.SIDEBAR_BG, surface));
        Gui.drawRect(sidebarW() - 1, 0, sidebarW(), height, ChatColors.SIDEBAR_DIVIDER);

        // 标题
        Gui.drawRect(0, 0, sidebarW(), HEADER_H, ChatRender.withAlpha(ChatColors.HEADER_BG, surface));
        Gui.drawRect(0, HEADER_H - 1, sidebarW(), HEADER_H, ChatColors.DIVIDER);
        mc.fontRenderer.drawStringWithShadow("§lChat",
                (sidebarW() - mc.fontRenderer.getStringWidth("Chat")) / 2, 10, ChatColors.ACCENT);

        int listTop = HEADER_H + 4;
        int listBot = height;
        enableScissor(0, listTop, sidebarW(), listBot, width, height);

        int y = listTop - (int) sidebarScroll;
        FontRenderer fr = mc.fontRenderer;

        // 世界频道
        {
            int bg = isActiveWorld() ? ChatColors.SIDEBAR_SEL
                    : (mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_ITEM_H ? ChatColors.SIDEBAR_HOVER : 0);
            if (bg != 0) Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_ITEM_H, bg);
            fr.drawStringWithShadow("🌐 " + ChatChannel.world().getDisplayName(), 6, y + 4, ChatColors.TEXT_PRIMARY);
            y += SIDEBAR_ITEM_H;
        }
        y += 2;

        // 私聊分类
        {
            boolean catHover = mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_CAT_H;
            Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_CAT_H, catHover ? ChatColors.SIDEBAR_HOVER : ChatColors.SIDEBAR_CAT);
            Gui.drawRect(0, y + SIDEBAR_CAT_H - 1, sidebarW(), y + SIDEBAR_CAT_H, ChatColors.DIVIDER);
            fr.drawStringWithShadow((privateExpanded ? "▼" : "▶") + " 私聊", 6, y + 5, ChatColors.TEXT_SECONDARY);
            y += SIDEBAR_CAT_H;

            if (privateExpanded) {
                for (ChatChannel ch : channels) {
                    if (!ch.isPrivate()) continue;
                    boolean sel = ch == activeChannel, hov = mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_ITEM_H;
                    if (sel) {
                        Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + SIDEBAR_ITEM_H, ChatColors.ACCENT);
                    } else if (hov) Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_HOVER);
                    if (ch.getUnreadCount() > 0 && !sel)
                        Gui.drawRect(4, y + 7, 8, y + 11, ChatColors.RED_DOT);
                    fr.drawStringWithShadow(ch.getDisplayName(), 14, y + 4,
                            sel ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
                    y += SIDEBAR_ITEM_H;
                }
            }
        }
        y += 2;

        // 群聊分类
        {
            boolean catHover = mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_CAT_H;
            Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_CAT_H, catHover ? ChatColors.SIDEBAR_HOVER : ChatColors.SIDEBAR_CAT);
            Gui.drawRect(0, y + SIDEBAR_CAT_H - 1, sidebarW(), y + SIDEBAR_CAT_H, ChatColors.DIVIDER);
            fr.drawStringWithShadow((groupExpanded ? "▼" : "▶") + " 群聊", 6, y + 5, ChatColors.TEXT_SECONDARY);
            y += SIDEBAR_CAT_H;

            if (groupExpanded) {
                for (ChatChannel ch : channels) {
                    if (!ch.isGroup()) continue;
                    boolean sel = ch == activeChannel, hov = mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_ITEM_H;
                    if (sel) {
                        Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + SIDEBAR_ITEM_H, ChatColors.ACCENT);
                    } else if (hov) Gui.drawRect(0, y, sidebarW(), y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_HOVER);
                    if (ch.getUnreadCount() > 0 && !sel)
                        Gui.drawRect(4, y + 7, 8, y + 11, ChatColors.RED_DOT);
                    fr.drawStringWithShadow(ch.getDisplayName(), 14, y + 4,
                            sel ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
                    y += SIDEBAR_ITEM_H;
                }
            }
        }
        y += 4;

        // [+ 创建群聊]
        {
            boolean hov = mx >= 0 && mx < sidebarW() && my >= y && my < y + SIDEBAR_ITEM_H;
            int bg = hov ? ChatColors.SIDEBAR_HOVER : 0;
            if (bg != 0) Gui.drawRect(2, y, sidebarW() - 2, y + SIDEBAR_ITEM_H, bg);
            fr.drawStringWithShadow("§7[+ 创建群聊]", 8, y + 4, hov ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
            y += SIDEBAR_ITEM_H;
        }

        disableScissor();

        // 侧边栏滚动条
        float contentH = y - listTop + (int) sidebarScroll;
        sidebarMaxScroll = Math.max(0, contentH - (height - listTop));
        if (sidebarMaxScroll > 0) {
            int trackH = height - listTop;
            int thumbH = Math.max(16, (int) (trackH * (float) trackH / (trackH + sidebarMaxScroll)));
            thumbH = Math.min(thumbH, trackH);
            float sf = sidebarScroll / sidebarMaxScroll;
            int thumbY = listTop + (int) (sf * (trackH - thumbH));
            int barX = sidebarW() - 4;
            Gui.drawRect(barX, listTop, barX + 3, height, ChatColors.SCROLLBAR_BG);
            Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, ChatColors.SCROLLBAR_FG);
        }
    }

    private boolean isActiveWorld() { return activeChannel != null && activeChannel.isWorld(); }

    // ===== 页眉 =====

    private void drawHeader(int mx, int my) {
        int surface = ChatThemeSettings.get().panelAlpha();
        Gui.drawRect(sidebarW(), 0, panelRight(), HEADER_H, ChatRender.withAlpha(ChatColors.HEADER_BG, surface));
        Gui.drawRect(sidebarW(), HEADER_H - 1, panelRight(), HEADER_H, ChatColors.DIVIDER);

        String title = activeChannel != null ? activeChannel.getDisplayName() : "聊天";
        if (activeChannel != null && activeChannel.isGroup())
            title = "👥 " + title + " (" + activeChannel.getMemberNames().size() + ")";
        mc.fontRenderer.drawStringWithShadow(title, sidebarW() + PAD,
                (HEADER_H - 8) / 2, ChatColors.TEXT_HEADER);

        // 时间显示（实时时钟）/ 搜索框
        if (searchOpen) {
            int fieldW = Math.max(80, Math.min(170, panelRight() - sidebarW() - 260));
            int fx = panelRight() - 58 - 26 - 10 - fieldW;
            searchField.x = fx;
            searchField.y = 6;
            searchField.width = fieldW;
            searchField.height = 14;
            searchField.setTextColor(ChatColors.TEXT_PRIMARY);
            Gui.drawRect(fx - 4, 4, fx + fieldW + 4, 20, ChatRender.withAlpha(ChatColors.INPUT_BG, surface));
            Gui.drawRect(fx - 4, 4, fx + fieldW + 4, 5, ChatColors.INPUT_BORDER);
            if (searchField.isFocused())
                Gui.drawRect(fx - 4, 4, fx + fieldW + 4, 5, ChatColors.ACCENT);
            searchField.drawTextBox();
            if (searchField.getText().isEmpty())
                mc.fontRenderer.drawStringWithShadow("搜索消息…", fx + 1, 8, ChatColors.TEXT_SECONDARY);
            else {
                int matches = countMatches();
                String count = matches + " 条";
                mc.fontRenderer.drawStringWithShadow(count, fx + fieldW + 8, 8,
                        matches > 0 ? ChatColors.ACCENT : ChatColors.TEXT_SECONDARY);
            }
        } else {
            String timeStr = java.time.LocalTime.now().format(TIME_FMT);
            int timeW = mc.fontRenderer.getStringWidth(timeStr);
            int timeX = panelRight() - 58 - timeW - 12; // 清空按钮左侧
            mc.fontRenderer.drawStringWithShadow(timeStr, timeX, (HEADER_H - 8) / 2, ChatColors.TIME_TEXT);
        }

        // 清空按钮
        String clearLabel = "清空";
        int clearW = mc.fontRenderer.getStringWidth(clearLabel);
        int clearX = panelRight() - 58, clearY = 6;
        boolean hoverClear = mx >= clearX - 2 && mx < clearX + clearW + 2 && my >= clearY && my < clearY + 14;
        if (hoverClear) Gui.drawRect(clearX - 2, clearY, clearX + clearW + 2, clearY + 14, ChatColors.SIDEBAR_HOVER);
        mc.fontRenderer.drawStringWithShadow(clearLabel, clearX, clearY + 3,
                hoverClear ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);

        // 关闭按钮
        int cx = panelRight() - 20, cy = 6;
        boolean hover = mx >= cx && mx < cx + 14 && my >= cy && my < cy + 14;
        Gui.drawRect(cx, cy, cx + 14, cy + 14, hover ? ChatColors.SIDEBAR_HOVER : 0);
        mc.fontRenderer.drawStringWithShadow("✕", cx + 4, cy + 3, ChatColors.TEXT_SECONDARY);
    }

    // ===== 消息气泡 =====

    private void drawMessages(int mx, int my) {
        bubbleRects.clear();
        messageBubbleRects.clear();
        ChatThemeSettings theme = ChatThemeSettings.get();
        List<ChatMessage> messages = ChatMessageStore.getActiveMessages();
        List<Integer> visible = visibleMessageIndices(messages);
        String query = searchQuery();
        boolean searching = searchOpen && !query.isEmpty();
        int msgTop = HEADER_H + 2;
        int msgBot = height - BAR_H - TOOLBAR_H - 6;
        int contentW = panelRight() - sidebarW();

        if (visible.isEmpty()) {
            String hint = searching ? "没有匹配的消息" : "暂无消息";
            int hw = mc.fontRenderer.getStringWidth(hint);
            mc.fontRenderer.drawStringWithShadow(hint,
                    sidebarW() + (contentW - hw) / 2,
                    msgBot / 2, ChatColors.TEXT_SECONDARY);
            maxScroll = 0;
            return;
        }

        int areaH = msgBot - msgTop;
        int gap = theme.messageGap();
        boolean timeSeps = theme.timeSeparators() && !searching;

        int totalH = 0;
        String lastTimeKey = null;
        for (int idx : visible) {
            ChatMessage msg = messages.get(idx);
            totalH += getMsgHeight(msg) + gap;
            if (timeSeps && !msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastTimeKey == null || !key.equals(lastTimeKey)) { lastTimeKey = key; totalH += TIME_SEP_H + gap; }
            }
        }

        maxScroll = Math.max(0, totalH - areaH);

        if (firstRender) { scrollOffset = maxScroll; scrollTarget = maxScroll; firstRender = false; }
        else if (scrollToBottom) { scrollTarget = maxScroll; }
        scrollTarget = MathHelper.clamp(scrollTarget, 0, maxScroll);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        enableScissor(sidebarW(), msgTop, panelRight(), msgBot, width, height);
        int contentY = msgTop - (int) scrollOffset;
        lastTimeKey = null;
        int pendingY = -1;

        for (int idx : visible) {
            ChatMessage msg = messages.get(idx);
            if (timeSeps && !msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastTimeKey == null || !key.equals(lastTimeKey)) {
                    lastTimeKey = key;
                    if (contentY + TIME_SEP_H > msgTop && contentY < msgBot)
                        drawTimeSeparator(msg.time(), contentY);
                    contentY += TIME_SEP_H + gap;
                }
            }

            int h = getMsgHeight(msg);
            int screenY = contentY;
            contentY += h + gap;

            if (screenY + h > msgTop && screenY < msgBot)
                drawBubble(msg, screenY, idx);

            if (idx == pendingScrollToIndex) pendingY = screenY;
        }
        disableScissor();

        if (pendingScrollToIndex >= 0) {
            if (pendingY >= 0) {
                scrollToBottom = false;
                scrollTarget = MathHelper.clamp(pendingY - msgTop - 24, 0, maxScroll);
            }
            pendingScrollToIndex = -1;
        }

        // 主滚动条
        if (maxScroll > 0) {
            int trackX = panelRight() - SCROLLBAR_W;
            int thumbH = Math.max(16, (int) (areaH * (float) areaH / (areaH + maxScroll)));
            thumbH = Math.min(thumbH, areaH);
            float sf = scrollOffset / maxScroll;
            int thumbY = msgTop + (int) (sf * (areaH - thumbH));
            Gui.drawRect(trackX, msgTop, trackX + SCROLLBAR_W, msgBot, ChatColors.SCROLLBAR_BG);
            Gui.drawRect(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, ChatColors.SCROLLBAR_FG);
        }

        // 回到底部
        if (scrollOffset < maxScroll - 20) {
            String arrow = "↓";
            int aw = mc.fontRenderer.getStringWidth(arrow);
            int ax = panelRight() - 18, ay = msgBot - 20;
            Gui.drawRect(ax - 2, ay - 2, ax + aw + 2, ay + 12, ChatColors.ACCENT_DIM);
            mc.fontRenderer.drawStringWithShadow(arrow, ax, ay, ChatColors.TEXT_PRIMARY);
        }
    }

    private String searchQuery() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<Integer> visibleMessageIndices(List<ChatMessage> messages) {
        List<Integer> result = new ArrayList<>();
        String query = searchQuery();
        for (int i = 0; i < messages.size(); i++) {
            if (query.isEmpty() || matchesQuery(messages.get(i), query)) result.add(i);
        }
        return result;
    }

    private boolean matchesQuery(ChatMessage msg, String query) {
        if (query.isEmpty()) return false;
        if (msg.content().getUnformattedText().toLowerCase(Locale.ROOT).contains(query)) return true;
        String sender = msg.rawPlayerName();
        if (sender == null && msg.senderName() != null)
            sender = msg.senderName().getUnformattedText();
        return sender != null && sender.toLowerCase(Locale.ROOT).contains(query);
    }

    private int countMatches() {
        String query = searchQuery();
        if (query.isEmpty()) return 0;
        int count = 0;
        for (ChatMessage msg : ChatMessageStore.getActiveMessages())
            if (matchesQuery(msg, query)) count++;
        return count;
    }

    /** 判断这条消息有没有点到自己：@名字 或独立出现的名字（前后不是字母数字）。 */
    /** 判断是否 ASCII 单词字符；中文/标点都算边界。 */
    private static boolean isWordChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_');
    }

    private boolean mentionsMe(ChatMessage msg) {
        if (!ChatThemeSettings.get().highlightMentions() || msg.isOwn() || msg.isSystem()) return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return false;
        String name = minecraft.player.getName();
        String text = msg.content().getUnformattedText();
        int from = 0;
        while (true) {
            int at = text.indexOf(name, from);
            if (at < 0) return false;
            boolean leftOk = at == 0 || !isWordChar(text.charAt(at - 1));
            int end = at + name.length();
            boolean rightOk = end >= text.length() || !isWordChar(text.charAt(end));
            if (leftOk && rightOk) return true;
            from = at + 1;
        }
    }

    /** 气泡文本统一换行宽度 (getMsgHeight 和 drawBubble 共用) */
    private int textWrapWidth() {
        ChatThemeSettings theme = ChatThemeSettings.get();
        int avatarSpace = theme.showAvatars() ? AVATAR + GAP : 0;
        // 气泡可用区: 面板宽度 - 侧边栏 - 两侧 padding - 头像 - 气泡内边距，再乘气泡宽度档位
        int available = panelRight() - sidebarW() - PAD * 2 - avatarSpace - BUBBLE_PAD_X * 2;
        return Math.max(60, available * theme.bubbleWidthPercent() / 100);
    }

    private int getMsgHeight(ChatMessage msg) {
        int wrapW = textWrapWidth();
        String text = EmojiRegistry.replaceShortcodes(msg.content().getFormattedText());
        if (msg.isSystem()) {
            List<String> lines = mc.fontRenderer.listFormattedStringToWidth(text, wrapW);
            return lines.size() * mc.fontRenderer.FONT_HEIGHT + 4;
        }
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(text, wrapW);
        return lines.size() * mc.fontRenderer.FONT_HEIGHT + ChatThemeSettings.get().bubblePadY() * 2 + NAME_H;
    }

    private void drawBubble(ChatMessage msg, int baseY, int index) {
        int r = panelRight();
        int contentW = r - sidebarW();
        boolean own = msg.isOwn();

        if (msg.isSystem()) {
            int wrapW = contentW - PAD * 2;
            List<String> lines = mc.fontRenderer.listFormattedStringToWidth(
                    msg.content().getFormattedText(), wrapW);
            int yy = baseY + 2;
            for (String line : lines) {
                int lw = mc.fontRenderer.getStringWidth(line);
                mc.fontRenderer.drawStringWithShadow(line,
                        sidebarW() + (contentW - lw) / 2, yy, ChatColors.TEXT_SECONDARY);
                yy += mc.fontRenderer.FONT_HEIGHT;
            }
            return;
        }

        // 统一换行宽度（短码 → 占位符，使换行计算感知表情宽度）
        int wrapW = textWrapWidth();
        String renderedText = EmojiRegistry.replaceShortcodes(msg.content().getFormattedText());
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(renderedText, wrapW);

        // 最长行实际宽度
        int textW = 0;
        for (String line : lines)
            textW = Math.max(textW, mc.fontRenderer.getStringWidth(line));

        int padY = ChatThemeSettings.get().bubblePadY();
        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * mc.fontRenderer.FONT_HEIGHT + padY * 2;

        // 头像和气泡位置（关闭头像时把这块空间还给气泡）
        ChatThemeSettings theme = ChatThemeSettings.get();
        boolean avatars = theme.showAvatars();
        int avatarX, bubbleX;
        if (own) {
            avatarX = r - PAD - AVATAR;
            // 气泡右缘贴头像左边-4, 左缘 = 右缘 - bubbleW
            bubbleX = avatars ? avatarX - GAP - bubbleW : r - PAD - bubbleW;
            // 不超出左边界
            if (bubbleX < sidebarW() + PAD) bubbleX = sidebarW() + PAD;
        } else {
            avatarX = sidebarW() + PAD;
            // 气泡左缘在头像右边+4
            bubbleX = avatars ? avatarX + AVATAR + GAP : avatarX;
            // 不超出右边界
            if (bubbleX + bubbleW > r - PAD) bubbleX = r - PAD - bubbleW;
        }

        // 名字 — 独立于气泡宽度，只在面板边缘处截断
        if (msg.senderName() != null && !msg.senderName().getUnformattedText().isEmpty()) {
            String name = msg.senderName().getUnformattedText();
            int nameW = mc.fontRenderer.getStringWidth(name);
            int nameX;
            if (own) {
                // 右对齐：名字右端贴气泡右缘，长名向左延伸
                nameX = bubbleX + bubbleW - BUBBLE_PAD_X - nameW;
                if (nameX < sidebarW() + PAD) {
                    // 超出面板左边界 → 截断
                    nameX = sidebarW() + PAD;
                    name = mc.fontRenderer.trimStringToWidth(name, bubbleX + bubbleW - BUBBLE_PAD_X - nameX);
                }
            } else {
                // 左对齐：名字左端贴气泡左缘，长名向右延伸
                nameX = bubbleX + BUBBLE_PAD_X;
                int maxW = r - PAD - nameX;
                if (nameW > maxW) name = mc.fontRenderer.trimStringToWidth(name, maxW);
            }
            mc.fontRenderer.drawStringWithShadow(name, nameX, baseY, ChatColors.NAME_TEXT);
        }

        int bubbleY = baseY + NAME_H;

        // 气泡背景（描边 + 圆角填充）
        int radius = theme.cornerRadius();
        ChatRender.roundRect(bubbleX - 1, bubbleY - 1, bubbleX + bubbleW + 1, bubbleY + bubbleH + 1,
                own ? ChatColors.BUBBLE_BORDER_OWN : ChatColors.BUBBLE_BORDER_OTHER, radius + 1);
        ChatRender.roundRect(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
                own ? ChatColors.OWN_BUBBLE : ChatColors.OTHER_BUBBLE, radius);

        // 气泡文字 — FontRenderer 绘制文本（占位符留空），随后覆盖表情贴图
        for (int li = 0; li < lines.size(); li++) {
            int lineX = bubbleX + BUBBLE_PAD_X;
            int lineY = bubbleY + padY + li * mc.fontRenderer.FONT_HEIGHT;
            mc.fontRenderer.drawStringWithShadow(lines.get(li), lineX, lineY, ChatColors.BUBBLE_TEXT);
            renderEmojiOverlays(lines.get(li), lineX, lineY);
        }

        // 头像（可在设置里关闭，关掉后消息只留名字和气泡）
        if (avatars) {
            drawPlayerHead(msg.senderUUID(), avatarX, baseY);
            bubbleRects.add(new int[]{avatarX, baseY, AVATAR, AVATAR, index});
        }
        // 记录气泡矩形供右键命中检测
        messageBubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        // @我 / 搜索命中：在圆角气泡外套一圈方框，成本低、一眼能看见
        if (mentionsMe(msg))
            outline(bubbleX - 2, bubbleY - 2, bubbleW + 4, bubbleH + 4, ChatColors.MENTION_MARK);
        if (searchOpen && !searchQuery().isEmpty() && matchesQuery(msg, searchQuery()))
            outline(bubbleX - 3, bubbleY - 3, bubbleW + 6, bubbleH + 6, ChatColors.SEARCH_MARK);

        // 防刷屏计数
        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int lw = mc.fontRenderer.getStringWidth(label);
            mc.fontRenderer.drawStringWithShadow(label,
                    own ? bubbleX - lw - 3 : bubbleX + bubbleW + 3,
                    bubbleY + (bubbleH - mc.fontRenderer.FONT_HEIGHT) / 2, ChatColors.DUPLICATE_TAG);
        }
    }

    private void drawTimeSeparator(LocalTime time, int y) {
        String text = time.format(TIME_FMT);
        int tw = mc.fontRenderer.getStringWidth(text);
        int tx = sidebarW() + (panelRight() - sidebarW() - tw) / 2;
        ChatRender.roundRect(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, ChatColors.TIME_SEP_BG,
                Math.min(4, ChatThemeSettings.get().cornerRadius() + 2));
        mc.fontRenderer.drawStringWithShadow(text, tx, y + 3, ChatColors.TIME_TEXT);
    }

    // ===== 输入栏 =====

    // ===== 快捷工具栏 =====

    private static final String LABEL_COORD = "分享坐标";
    private static final String LABEL_ITEM = "分享物品";
    private static final int TOOLBAR_BTN_PAD = 10;

    private void drawToolbar(int mx, int my) {
        int toolTop = height - BAR_H - TOOLBAR_H, r = panelRight();
        int surface = ChatThemeSettings.get().panelAlpha();
        Gui.drawRect(sidebarW(), toolTop, r, toolTop + TOOLBAR_H, ChatRender.withAlpha(ChatColors.BAR_BG, surface));
        Gui.drawRect(sidebarW(), toolTop, r, toolTop + 1, ChatColors.DIVIDER);

        int y = toolTop + (TOOLBAR_H - mc.fontRenderer.FONT_HEIGHT) / 2;
        int x = sidebarW() + PAD;
        int gap = 6;

        int coordW = mc.fontRenderer.getStringWidth(LABEL_COORD) + TOOLBAR_BTN_PAD;
        int itemW = mc.fontRenderer.getStringWidth(LABEL_ITEM) + TOOLBAR_BTN_PAD;

        // 分享坐标
        boolean hoverCoord = mx >= x && mx < x + coordW && my >= toolTop && my < toolTop + TOOLBAR_H;
        int coordBg = hoverCoord ? ChatColors.ACCENT_DIM : ChatColors.SIDEBAR_HOVER;
        Gui.drawRect(x, toolTop + 3, x + coordW, toolTop + TOOLBAR_H - 3, coordBg);
        mc.fontRenderer.drawStringWithShadow(LABEL_COORD, x + TOOLBAR_BTN_PAD / 2, y,
                hoverCoord ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
        int coordEnd = x + coordW;
        x = coordEnd + gap;

        // 分享物品
        boolean hoverItem = mx >= x && mx < x + itemW && my >= toolTop && my < toolTop + TOOLBAR_H;
        int itemBg = hoverItem ? ChatColors.ACCENT_DIM : ChatColors.SIDEBAR_HOVER;
        Gui.drawRect(x, toolTop + 3, x + itemW, toolTop + TOOLBAR_H - 3, itemBg);
        mc.fontRenderer.drawStringWithShadow(LABEL_ITEM, x + TOOLBAR_BTN_PAD / 2, y,
                hoverItem ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);

        // 右侧：搜索 + 设置
        int themeW = mc.fontRenderer.getStringWidth("设置") + TOOLBAR_BTN_PAD;
        int searchW = mc.fontRenderer.getStringWidth("搜索") + TOOLBAR_BTN_PAD;
        int themeX = r - PAD - themeW;
        int searchX = themeX - gap - searchW;

        boolean hoverSearch = mx >= searchX && mx < searchX + searchW && my >= toolTop && my < toolTop + TOOLBAR_H;
        Gui.drawRect(searchX, toolTop + 3, searchX + searchW, toolTop + TOOLBAR_H - 3,
                searchOpen ? ChatColors.ACCENT_DIM : (hoverSearch ? ChatColors.SIDEBAR_HOVER : 0));
        mc.fontRenderer.drawStringWithShadow("搜索", searchX + TOOLBAR_BTN_PAD / 2, y,
                searchOpen ? ChatColors.TEXT_PRIMARY : (hoverSearch ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY));

        boolean hoverTheme = mx >= themeX && mx < themeX + themeW && my >= toolTop && my < toolTop + TOOLBAR_H;
        Gui.drawRect(themeX, toolTop + 3, themeX + themeW, toolTop + TOOLBAR_H - 3,
                hoverTheme ? ChatColors.ACCENT_DIM : ChatColors.SIDEBAR_HOVER);
        mc.fontRenderer.drawStringWithShadow("设置", themeX + TOOLBAR_BTN_PAD / 2, y,
                hoverTheme ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
    }

    private void handleToolbarClick(int mx, int my) {
        int toolTop = height - BAR_H - TOOLBAR_H;
        if (my < toolTop || my >= toolTop + TOOLBAR_H) return;

        int x = sidebarW() + PAD;
        int gap = 6;
        int coordW = mc.fontRenderer.getStringWidth(LABEL_COORD) + TOOLBAR_BTN_PAD;
        int itemW = mc.fontRenderer.getStringWidth(LABEL_ITEM) + TOOLBAR_BTN_PAD;

        // 右侧按钮先处理：不依赖玩家实体
        int themeW = mc.fontRenderer.getStringWidth("设置") + TOOLBAR_BTN_PAD;
        int searchW = mc.fontRenderer.getStringWidth("搜索") + TOOLBAR_BTN_PAD;
        int themeX = panelRight() - PAD - themeW;
        int searchX = themeX - gap - searchW;
        if (mx >= themeX && mx < themeX + themeW) {
            settingsPanel.open(this);
            return;
        }
        if (mx >= searchX && mx < searchX + searchW) {
            toggleSearch();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (mx >= x && mx < x + coordW) {
            // 分享坐标
            String dim = mc.player.world.provider.getDimensionType().getName();
            int px = (int) mc.player.posX, py = (int) mc.player.posY, pz = (int) mc.player.posZ;
            sendShareMessage(mc.player.getName() + " 分享了位于 " + dim + " 的坐标 X：" + px + " Y：" + py + " Z：" + pz);
        } else if (mx >= x + coordW + gap && mx < x + coordW + gap + itemW) {
            // 分享物品
            net.minecraft.item.ItemStack held = mc.player.getHeldItemMainhand();
            if (!held.isEmpty())
                sendShareMessage(mc.player.getName() + " 分享了物品 " + held.getDisplayName());
        }
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        clearMention();
        if (searchOpen) {
            searchField.setFocused(true);
            searchField.setCursorPosition(searchField.getText().length());
            searchField.setSelectionPos(searchField.getText().length());
            inputField.setFocused(false);
        } else {
            searchField.setText("");
            searchField.setFocused(false);
            inputField.setFocused(true);
            scrollToBottom = true;
        }
    }

    private void sendShareMessage(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        ChannelType type = activeChannel != null ? activeChannel.getType() : ChannelType.WORLD;

        if (type == ChannelType.WORLD) {
            mc.player.sendChatMessage(text);
        } else if (type == ChannelType.PRIVATE) {
            String partner = activeChannel.getPartnerName();
            PacketHandler.sendToServer(new ChatPrivatePacket(partner, text,
                    mc.player.getName(), mc.player.getUniqueID()));
            ChatMessageStore.addMessage(new TextComponentString(text), mc.player.getUniqueID(),
                    new TextComponentString(mc.player.getName()), false, mc.player.getName(),
                    "priv:" + partner);
        } else if (type == ChannelType.GROUP) {
            PacketHandler.sendToServer(new ChatGroupPacket(activeChannel.getGroupId(), text,
                    mc.player.getName(), mc.player.getUniqueID()));
            ChatMessageStore.addMessage(new TextComponentString(text), mc.player.getUniqueID(),
                    new TextComponentString(mc.player.getName()), false, mc.player.getName(),
                    "group:" + activeChannel.getGroupId());
        }

        mc.ingameGUI.getChatGUI().addToSentMessages(text);
    }

    private void drawInputBar(int mx, int my) {
        int barTop = height - BAR_H, r = panelRight();
        int surface = ChatThemeSettings.get().panelAlpha();
        Gui.drawRect(sidebarW(), barTop, r, height, ChatRender.withAlpha(ChatColors.BAR_BG, surface));
        Gui.drawRect(sidebarW(), barTop, r, barTop + 1, ChatColors.DIVIDER);

        int inputX = sidebarW() + PAD;
        // 为表情按钮和发送按钮留出空间
        int inputW = r - sidebarW() - PAD * 2 - 36;
        int inputY = barTop + (BAR_H - INPUT_H) / 2;

        // 输入框背景
        Gui.drawRect(inputX - 1, inputY - 1, inputX + inputW + 1, inputY, ChatColors.INPUT_BORDER);
        Gui.drawRect(inputX - 1, inputY, inputX + inputW + 1, inputY + INPUT_H,
                ChatRender.withAlpha(ChatColors.INPUT_BG, surface));

        boolean hoverInput = mx >= inputX && mx < inputX + inputW && my >= inputY && my < inputY + INPUT_H;
        if (hoverInput || inputField.isFocused()) {
            Gui.drawRect(inputX - 1, inputY - 1, inputX + inputW + 1, inputY, ChatColors.ACCENT);
            Gui.drawRect(inputX - 1, inputY - 1, inputX, inputY + INPUT_H, ChatColors.ACCENT);
            Gui.drawRect(inputX + inputW, inputY - 1, inputX + inputW + 1, inputY + INPUT_H, ChatColors.ACCENT);
            Gui.drawRect(inputX - 1, inputY + INPUT_H, inputX + inputW + 1, inputY + INPUT_H + 1, ChatColors.ACCENT);
        }

        if (inputField.getText().isEmpty())
            mc.fontRenderer.drawStringWithShadow(getPlaceholder(), inputX + 2, inputY + 3, ChatColors.TEXT_SECONDARY);

        // 更新输入框宽度（上面计算时已预留空间）
        inputField.width = inputW;
        inputField.setTextColor(ChatColors.TEXT_PRIMARY);
        inputField.drawTextBox();

        // 表情按钮
        int emojiBtnX = inputX + inputW + 3;
        int emojiBtnY = barTop + (BAR_H - 14) / 2;
        boolean hoverEmoji = mx >= emojiBtnX - 2 && mx < emojiBtnX + 14 && my >= emojiBtnY - 2 && my < emojiBtnY + 14;
        int emojiBg = showEmojiPicker ? ChatColors.ACCENT_DIM
                : (hoverEmoji ? ChatColors.SIDEBAR_HOVER : 0);
        if (emojiBg != 0)
            Gui.drawRect(emojiBtnX - 2, emojiBtnY - 2, emojiBtnX + 14, emojiBtnY + 14, emojiBg);
        mc.fontRenderer.drawStringWithShadow("☺", emojiBtnX, emojiBtnY + 2,
                showEmojiPicker ? ChatColors.ACCENT : ChatColors.TEXT_SECONDARY);

        // 发送按钮
        int sendX = r - PAD - 14, sendY = barTop + (BAR_H - 12) / 2;
        boolean hoverSend = mx >= sendX - 2 && mx < sendX + 14 && my >= sendY - 2 && my < sendY + 14;
        Gui.drawRect(sendX - 2, sendY - 2, sendX + 14, sendY + 14, hoverSend ? ChatColors.SEND_HOVER : 0);
        mc.fontRenderer.drawStringWithShadow("▶", sendX, sendY + 2, ChatColors.ACCENT);
    }

    private String getPlaceholder() {
        if (activeChannel == null || activeChannel.isWorld()) return "输入消息… Tab 补全 @玩家 / :表情";
        if (activeChannel.isPrivate()) return "私聊 " + activeChannel.getDisplayName() + "...";
        return "群聊 " + activeChannel.getDisplayName() + "...";
    }

    // ===== 消息右键菜单 =====

    private void drawContextMenu(int mx, int my) {
        if (contextMsgIndex < 0) return;
        int pRight = panelRight();
        int itemCount = 2; // 复制文字 + 引用回复
        int menuH = itemCount * CTX_ITEM_H + 2;
        int menuX = Math.min(contextX, pRight - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < HEADER_H) menuY = contextY + 4;

        Gui.drawRect(menuX, menuY, menuX + CTX_W, menuY + menuH, ChatColors.CONTEXT_BG);
        Gui.drawRect(menuX, menuY, menuX + CTX_W, menuY + 1, ChatColors.DIVIDER);
        Gui.drawRect(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, ChatColors.DIVIDER);

        // "复制文字"
        boolean hover1 = mx >= menuX && mx < menuX + CTX_W && my >= menuY && my < menuY + CTX_ITEM_H;
        if (hover1) Gui.drawRect(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, ChatColors.CONTEXT_HOVER);
        mc.fontRenderer.drawStringWithShadow("复制文字", menuX + 6, menuY + 3, ChatColors.TEXT_PRIMARY);

        // 分隔 + "引用回复"
        Gui.drawRect(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, ChatColors.DIVIDER);
        boolean hover2 = mx >= menuX && mx < menuX + CTX_W && my >= menuY + CTX_ITEM_H + 1 && my < menuY + menuH;
        if (hover2) Gui.drawRect(menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, ChatColors.CONTEXT_HOVER);
        mc.fontRenderer.drawStringWithShadow("引用回复", menuX + 6, menuY + CTX_ITEM_H + 3, ChatColors.TEXT_PRIMARY);
    }

    private void handleContextMenuClick(int mx, int my) {
        int itemCount = 2;
        int menuH = itemCount * CTX_ITEM_H + 2;
        int pRight = panelRight();
        int menuX = Math.min(contextX, pRight - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < HEADER_H) menuY = contextY + 4;

        List<ChatMessage> msgs = ChatMessageStore.getActiveMessages();
        ChatMessage msg = (contextMsgIndex >= 0 && contextMsgIndex < msgs.size())
                ? msgs.get(contextMsgIndex) : null;

        if (mx >= menuX && mx < menuX + CTX_W) {
            if (my >= menuY && my < menuY + CTX_ITEM_H) {
                // 复制文字
                if (msg != null) setClipboardString(msg.content().getUnformattedText());
            } else if (my >= menuY + CTX_ITEM_H + 1 && my < menuY + menuH) {
                // 引用回复
                if (msg != null) {
                    String sender = msg.rawPlayerName() != null ? msg.rawPlayerName()
                            : msg.senderName().getUnformattedText();
                    String quote = "> @" + sender + ": " + trimQuote(msg.content().getUnformattedText()) + " ";
                    inputField.writeText(quote);
                }
            }
        }
        contextMsgIndex = -1;
    }

    // ===== 头像右键菜单 =====

    private void drawAvatarContextMenu(int mx, int my) {
        if (avatarContextIndex < 0) return;
        // @提及 + 发起私聊 (+ 邀请进群 if group) + 请求传送
        int itemCount = activeChannel != null && activeChannel.isGroup() ? 4 : 3;
        int itemH = CTX_ITEM_H;
        int menuH = itemCount * itemH + 2;
        int pRight = panelRight();
        int menuX = Math.min(avatarContextX, pRight - AVATAR_CTX_W - 2);
        int menuY = avatarContextY - menuH;
        if (menuY < HEADER_H) menuY = avatarContextY + 4;

        Gui.drawRect(menuX, menuY, menuX + AVATAR_CTX_W, menuY + menuH, ChatColors.CONTEXT_BG);
        Gui.drawRect(menuX, menuY, menuX + AVATAR_CTX_W, menuY + 1, ChatColors.DIVIDER);
        Gui.drawRect(menuX, menuY + menuH - 1, menuX + AVATAR_CTX_W, menuY + menuH, ChatColors.DIVIDER);

        int yOff = 0;
        // "@提及"
        drawAvatarMenuItem(menuX, menuY + yOff, itemH, mx, my, "@提及");
        yOff += itemH;
        // "发起私聊"
        drawDivider(menuX, menuY + yOff);
        drawAvatarMenuItem(menuX, menuY + yOff + 1, itemH, mx, my, "发起私聊");
        yOff += itemH + 1;
        // "邀请进群" (群聊时)
        if (itemCount >= 4) {
            drawDivider(menuX, menuY + yOff);
            drawAvatarMenuItem(menuX, menuY + yOff + 1, itemH, mx, my, "邀请进群");
            yOff += itemH + 1;
        }
        // "请求传送"
        drawDivider(menuX, menuY + yOff);
        drawAvatarMenuItem(menuX, menuY + yOff + 1, itemH, mx, my, "请求传送");
    }

    private void drawAvatarMenuItem(int menuX, int y, int itemH, int mx, int my, String label) {
        boolean hover = mx >= menuX && mx < menuX + AVATAR_CTX_W && my >= y && my < y + itemH;
        if (hover) Gui.drawRect(menuX + 1, y, menuX + AVATAR_CTX_W - 1, y + itemH, ChatColors.CONTEXT_HOVER);
        mc.fontRenderer.drawStringWithShadow(label, menuX + 6, y + 3, ChatColors.TEXT_PRIMARY);
    }

    private void drawDivider(int menuX, int y) {
        Gui.drawRect(menuX + 4, y, menuX + AVATAR_CTX_W - 4, y + 1, ChatColors.DIVIDER);
    }

    private void handleAvatarContextClick(int mx, int my) {
        int itemH = CTX_ITEM_H;
        boolean isGroup = activeChannel != null && activeChannel.isGroup();
        int itemCount = isGroup ? 4 : 3;
        int menuH = itemCount * itemH + 2;
        int pRight = panelRight();
        int menuX = Math.min(avatarContextX, pRight - AVATAR_CTX_W - 2);
        int menuY = avatarContextY - menuH;
        if (menuY < HEADER_H) menuY = avatarContextY + 4;

        List<ChatMessage> msgs = ChatMessageStore.getActiveMessages();
        ChatMessage msg = (avatarContextIndex >= 0 && avatarContextIndex < msgs.size())
                ? msgs.get(avatarContextIndex) : null;
        if (msg == null) { avatarContextIndex = -1; return; }
        String name = msg.rawPlayerName();

        if (mx < menuX || mx >= menuX + AVATAR_CTX_W) { avatarContextIndex = -1; return; }

        // 计算点击的是哪一项
        int relY = my - menuY;
        int yOff = 0;
        // 0: @提及
        if (relY >= yOff && relY < yOff + itemH) {
            inputField.writeText("@" + name + " ");
            avatarContextIndex = -1; return;
        }
        yOff += itemH + 1; // +1 for divider
        // 1: 发起私聊
        if (relY >= yOff && relY < yOff + itemH) {
            UUID partnerUuid = msg.senderUUID();
            ChatChannel priv = ChatMessageStore.findOrCreatePrivateChannel(name, partnerUuid);
            switchToChannel(priv.getId());
            avatarContextIndex = -1; return;
        }
        yOff += itemH + 1;
        // 2: 邀请进群 (if group)
        if (isGroup && relY >= yOff && relY < yOff + itemH) {
            PacketHandler.sendToServer(GroupManagePacket.invite(activeChannel.getGroupId(), name));
            avatarContextIndex = -1; return;
        }
        if (isGroup) yOff += itemH + 1;
        // 3 (or 2): 请求传送
        if (relY >= yOff && relY < yOff + itemH) {
            PacketHandler.sendToServer(new TpRequestPacket(name,
                    mc.player.getName(), mc.player.getUniqueID()));
            avatarContextIndex = -1; return;
        }

        avatarContextIndex = -1;
    }

    /**
     * 处理表情选择器的鼠标点击
     * @return true 表示事件已消费
     */
    private boolean handleEmojiPickerClick(int mx, int my) {
        int cols = EmojiRegistry.PICKER_COLS;
        int rows = EmojiRegistry.PICKER_ROWS;
        int cell = EmojiRegistry.PICKER_CELL;
        int pad = 4;
        int gridW = cols * cell + pad * 2;
        int gridH = rows * cell + pad * 2;
        int gridX = sidebarW() + PAD;
        int gridY = height - BAR_H - TOOLBAR_H - gridH - 4;

        // 点击选择器外部 → 关闭
        if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridY + gridH) {
            showEmojiPicker = false;
            return false;
        }

        // 点击格子 → 插入短码
        int col = (mx - gridX - pad) / cell;
        int row = (my - gridY - pad) / cell;
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            int idx = row * cols + col;
            if (idx < EmojiRegistry.COUNT) {
                String sc = EmojiRegistry.getShortcodeByIndex(idx) + " ";
                inputField.writeText(sc);
                refreshMentionPopup();
            }
        }
        // 不关选择器，可以连续点击
        return true;
    }

    // ===== 创建群聊对话框 =====

    private void drawCreateGroupDialog(int mx, int my) {
        Gui.drawRect(0, 0, width, height, 0x80000000); // 全屏遮罩

        int dx = width / 2 - 90, dy = height / 2 - 25;
        Gui.drawRect(dx, dy, dx + 180, dy + 50, ChatColors.PANEL_BG);
        Gui.drawRect(dx, dy, dx + 180, dy + 1, ChatColors.ACCENT);
        Gui.drawRect(dx, dy + 49, dx + 180, dy + 50, ChatColors.ACCENT);

        mc.fontRenderer.drawStringWithShadow("创建群聊",
                width / 2 - mc.fontRenderer.getStringWidth("创建群聊") / 2, dy + 4, ChatColors.TEXT_HEADER);

        Gui.drawRect(dx + 5, dy + 20, dx + 175, dy + 36, ChatColors.INPUT_BG);
        Gui.drawRect(dx + 5, dy + 20, dx + 175, dy + 21, ChatColors.ACCENT_DIM);
        if (groupNameField.getText().isEmpty() && !groupNameField.isFocused())
            mc.fontRenderer.drawStringWithShadow("输入群名...", dx + 8, dy + 24, ChatColors.TEXT_SECONDARY);
        groupNameField.x = dx + 8;
        groupNameField.y = dy + 24;
        groupNameField.drawTextBox();

        int btnY = dy + 38;
        // 创建按钮 (居中偏左)
        int createX = dx + 40, createW = mc.fontRenderer.getStringWidth("创建") + 12;
        Gui.drawRect(createX, btnY, createX + createW, btnY + 10, ChatColors.ACCENT_DIM);
        mc.fontRenderer.drawStringWithShadow("创建", createX + 6, btnY + 1, ChatColors.TEXT_PRIMARY);
        // 取消按钮 (居中偏右)
        int cancelX = dx + 110, cancelW = mc.fontRenderer.getStringWidth("取消") + 12;
        mc.fontRenderer.drawStringWithShadow("取消", cancelX + 6, btnY + 1,
                (mx >= cancelX && mx < cancelX + cancelW && my >= btnY && my < btnY + 10)
                        ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
    }

    // ===== 玩家头像 =====

    private void drawPlayerHead(UUID uuid, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) return;

        ResourceLocation skin;
        NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
        if (info != null) skin = info.getLocationSkin();
        else skin = mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getLocationSkin();

        mc.getTextureManager().bindTexture(skin);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, AVATAR, AVATAR, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, AVATAR, AVATAR, 64, 64);
    }

    // ===== 表情渲染 =====

    /** 在已渲染的文字行之上覆盖表情贴图 */
    private void renderEmojiOverlays(String line, int lineX, int lineY) {
        FontRenderer fr = mc.fontRenderer;
        int emojiSize = EmojiRegistry.getEmojiSize();
        // 表情垂直居中（相对于字体行高 FONT_HEIGHT）
        int emojiY = lineY + (fr.FONT_HEIGHT - emojiSize) / 2;
        // 高表情微调上移 1px，防止与上行文字间距不匀
        if (emojiSize > fr.FONT_HEIGHT + 1) emojiY -= 1;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // 跳过格式码
            if (c == '§' && i + 1 < line.length()) { i++; continue; }
            if (!EmojiRegistry.isPlaceholder(c)) continue;

            ResourceLocation tex = EmojiRegistry.getTexture(c);
            if (tex == null) continue;

            int emojiX = lineX + fr.getStringWidth(line.substring(0, i));
            mc.getTextureManager().bindTexture(tex);
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
            Gui.drawScaledCustomSizeModalRect(
                    emojiX, emojiY, 0, 0, 72, 72, emojiSize, emojiSize, 72, 72);
        }
    }

    // ===== 表情选择器 =====

    private void drawEmojiPicker(int mx, int my) {
        if (!showEmojiPicker) return;

        int cols = EmojiRegistry.PICKER_COLS;
        int rows = EmojiRegistry.PICKER_ROWS;
        int cell = EmojiRegistry.PICKER_CELL;
        int pad = 4;
        int gridW = cols * cell + pad * 2;
        int gridH = rows * cell + pad * 2;

        // 定位：输入栏上方
        int gridX = sidebarW() + PAD;
        int gridY = height - BAR_H - TOOLBAR_H - gridH - 4;

        // 背景
        Gui.drawRect(gridX, gridY, gridX + gridW, gridY + gridH, ChatColors.CONTEXT_BG);
        Gui.drawRect(gridX, gridY, gridX + gridW, gridY + 1, ChatColors.ACCENT);
        Gui.drawRect(gridX, gridY + gridH - 1, gridX + gridW, gridY + gridH, ChatColors.ACCENT);
        Gui.drawRect(gridX, gridY, gridX + 1, gridY + gridH, ChatColors.ACCENT);
        Gui.drawRect(gridX + gridW - 1, gridY, gridX + gridW, gridY + gridH, ChatColors.ACCENT);

        // 表情格子
        int imgSize = EmojiRegistry.getEmojiSize();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= EmojiRegistry.COUNT) break;

                int cx = gridX + pad + col * cell;
                int cy = gridY + pad + row * cell;

                // Hover 高亮
                boolean hover = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell;
                if (hover) Gui.drawRect(cx, cy, cx + cell, cy + cell, ChatColors.SIDEBAR_HOVER);

                // 表情贴图
                ResourceLocation tex = EmojiRegistry.getTextureByIndex(idx);
                if (tex != null) {
                    mc.getTextureManager().bindTexture(tex);
                    GlStateManager.enableBlend();
                    GlStateManager.color(1, 1, 1, 1);
                    int inset = (cell - imgSize) / 2;
                    Gui.drawScaledCustomSizeModalRect(
                            cx + inset, cy + inset, 0, 0, 72, 72, imgSize, imgSize, 72, 72);
                }
            }
        }

        // 悬停时底部显示短码提示
        int hoverCol = (mx - gridX - pad) / cell;
        int hoverRow = (my - gridY - pad) / cell;
        if (hoverRow >= 0 && hoverRow < rows && hoverCol >= 0 && hoverCol < cols
                && mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            int idx = hoverRow * cols + hoverCol;
            if (idx < EmojiRegistry.COUNT) {
                String tip = EmojiRegistry.getShortcodeByIndex(idx);
                int tipW = mc.fontRenderer.getStringWidth(tip);
                int tipX = gridX + (gridW - tipW) / 2;
                int tipY = gridY + gridH + 2;
                Gui.drawRect(tipX - 3, tipY - 1, tipX + tipW + 3, tipY + mc.fontRenderer.FONT_HEIGHT + 1,
                        ChatColors.CONTEXT_BG);
                mc.fontRenderer.drawStringWithShadow(tip, tipX, tipY, ChatColors.TEXT_SECONDARY);
            }
        }
    }

    // ===== 输入处理 =====

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // 设置浮层打开时是模态：ESC 关浮层，其余按键一律吞掉
        if (settingsPanel.isOpen()) {
            settingsPanel.keyPressed(keyCode);
            return;
        }

        if (creatingGroup) {
            if (keyCode == Keyboard.KEY_ESCAPE) { creatingGroup = false; return; }
            if (keyCode == Keyboard.KEY_RETURN) {
                String name = groupNameField.getText().trim();
                if (!name.isEmpty()) {
                    PacketHandler.sendToServer(GroupManagePacket.create(name,
                            Minecraft.getMinecraft().player.getUniqueID()));
                    creatingGroup = false;
                }
                return;
            }
            groupNameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }

        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl && keyCode == Keyboard.KEY_F) { toggleSearch(); return; }

        // 搜索模式：按键先给搜索框
        if (searchOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) { toggleSearch(); return; }
            if (keyCode == Keyboard.KEY_RETURN) { jumpToNextMatch(); return; }
            String before = searchField.getText();
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (!before.equals(searchField.getText())) {
                searchCursor = -1;
                pendingScrollToIndex = firstMatchIndex();
            }
            return;
        }

        // @ 候选弹窗优先吃掉方向键 / Tab / Enter
        if (mentionPopupVisible()) {
            if (keyCode == Keyboard.KEY_UP) {
                mentionIndex = (mentionIndex - 1 + mentionCandidates.size()) % mentionCandidates.size();
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                mentionIndex = (mentionIndex + 1) % mentionCandidates.size();
                return;
            }
            if (keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_RETURN) {
                applyMention(mentionCandidates.get(mentionIndex));
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                clearMention();
                return;
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (showEmojiPicker) { showEmojiPicker = false; return; }
            if (inputField.getText().isEmpty()) {
                closing = true;
                animStart = System.currentTimeMillis();
            } else {
                inputField.setText("");
            }
            clearMention();
            return;
        }

        if (keyCode == Keyboard.KEY_TAB) { completeTab(); return; }

        if (keyCode == Keyboard.KEY_RETURN) { sendMessage(); return; }
        if (keyCode == Keyboard.KEY_UP) { navigateHistory(-1); refreshMentionPopup(); return; }
        if (keyCode == Keyboard.KEY_DOWN) { navigateHistory(1); refreshMentionPopup(); return; }

        inputField.textboxKeyTyped(typedChar, keyCode);
        // 手动编辑后，之前那轮 Tab 候选作废
        if (tabIndex >= 0 && !inputField.getText().equals(tabBase + tabToken + tabSuffix)) tabIndex = -1;
        refreshMentionPopup();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (settingsPanel.isOpen()) return;
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (mx > panelRight()) return; // 面板外忽略

            if (mx < sidebarW())
                sidebarScrollTarget = MathHelper.clamp(sidebarScrollTarget - MouseWheel.pixels(dw, 42f), 0, sidebarMaxScroll);
            else {
                scrollToBottom = false;
                scrollTarget = MathHelper.clamp(scrollTarget - MouseWheel.pixels(dw, 48f), 0, maxScroll);
            }
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        if (creatingGroup) {
            int dx = width / 2 - 90, dy = height / 2 - 25;
            if (my >= dy + 38 && my < dy + 48) {
                int createW = mc.fontRenderer.getStringWidth("创建") + 12;
                int cancelW = mc.fontRenderer.getStringWidth("取消") + 12;
                if (mx >= dx + 40 && mx < dx + 40 + createW) {
                    String name = groupNameField.getText().trim();
                    if (!name.isEmpty()) {
                        PacketHandler.sendToServer(GroupManagePacket.create(name,
                                Minecraft.getMinecraft().player.getUniqueID()));
                        creatingGroup = false;
                    }
                    return;
                }
                if (mx >= dx + 110 && mx < dx + 110 + cancelW) { creatingGroup = false; return; }
            }
            return;
        }

        // 设置浮层优先吃掉点击（点面板外则关闭浮层）
        if (settingsPanel.isOpen()) {
            settingsPanel.mouseClicked(mx, my, btn);
            return;
        }

        // @ 候选弹窗
        if (btn == 0 && handleMentionClick(mx, my)) {
            return;
        }

        // 面板外点击 → 关闭
        if (mx > panelRight()) {
            onGuiClosed();
            mc.displayGuiScreen(null);
            return;
        }

        // 表情选择器点击
        if (showEmojiPicker && btn == 0) {
            if (handleEmojiPickerClick(mx, my)) return;
        }

        // 头像右键菜单
        if (avatarContextIndex >= 0) {
            handleAvatarContextClick(mx, my);
            return;
        }

        if (contextMsgIndex >= 0) {
            handleContextMenuClick(mx, my);
            return;
        }

        // 右键头像 → 弹出 "发起私聊 / 邀请进群"
        if (btn == 1) {
            for (int[] r : bubbleRects) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    ChatMessage msg = ChatMessageStore.getActiveMessages().get(r[4]);
                    if (msg != null && !msg.isOwn() && !msg.isSystem()
                            && msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()) {
                        avatarContextIndex = r[4];
                        avatarContextX = mx;
                        avatarContextY = my;
                        return;
                    }
                }
            }
        }

        // 右键消息区域 → 查找命中的气泡
        if (btn == 1 && mx > sidebarW() && my > HEADER_H && my < height - BAR_H - TOOLBAR_H) {
            for (int[] r : messageBubbleRects) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    contextMsgIndex = r[4];
                    contextX = mx;
                    contextY = my;
                    return;
                }
            }
            contextMsgIndex = -1;
            return;
        }

        if (btn == 0 && mx < sidebarW()) { handleSidebarClick(mx, my); return; }

        // 工具栏点击
        if (btn == 0 && my >= height - BAR_H - TOOLBAR_H && my < height - BAR_H) {
            handleToolbarClick(mx, my); return;
        }

        // 表情按钮点击 (toggle)
        if (btn == 0) {
            int inputX = sidebarW() + PAD;
            int inputW = panelRight() - sidebarW() - PAD * 2 - 36;
            int emojiBtnX = inputX + inputW + 3;
            int emojiBtnY = height - BAR_H + (BAR_H - 14) / 2;
            if (mx >= emojiBtnX - 2 && mx < emojiBtnX + 14 && my >= emojiBtnY - 2 && my < emojiBtnY + 14) {
                showEmojiPicker = !showEmojiPicker;
                return;
            }
        }

        int sendX = panelRight() - PAD - 14, sendY = height - BAR_H + (BAR_H - 12) / 2;
        if (btn == 0 && mx >= sendX - 2 && mx < sendX + 14 && my >= sendY - 2 && my < sendY + 14) {
            sendMessage(); return;
        }
        // 点击非表情按钮/选择器区域时关闭选择器
        if (btn == 0 && showEmojiPicker) showEmojiPicker = false;

        // 搜索状态下点击输入栏，回到正常输入
        if (btn == 0 && searchOpen && my >= height - BAR_H + 2) {
            toggleSearch();
            return;
        }

        // 清空按钮
        String clearLabel = "清空";
        int clearW = mc.fontRenderer.getStringWidth(clearLabel);
        int clearX = panelRight() - 58, clearY = 6;
        if (btn == 0 && mx >= clearX - 2 && mx < clearX + clearW + 2 && my >= clearY && my < clearY + 14) {
            ChatMessageStore.clearActiveChannel();
            scrollOffset = 0; scrollTarget = 0; maxScroll = 0;
            return;
        }

        int cx = panelRight() - 20, cy = 6;
        if (btn == 0 && mx >= cx && mx < cx + 14 && my >= cy && my < cy + 14) {
            onGuiClosed();
            mc.displayGuiScreen(null);
            return;
        }

        if (btn == 0 && scrollOffset < maxScroll - 20
                && mx >= panelRight() - 18 && mx < panelRight() - 4
                && my >= height - BAR_H - 20 && my < height - BAR_H - 4) {
            scrollToBottom = true;
            scrollTarget = maxScroll;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (settingsPanel.isOpen() && settingsPanel.mouseDragged(mouseX, mouseY)) return;
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (settingsPanel.isOpen()) {
            settingsPanel.mouseReleased();
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void handleSidebarClick(int mx, int my) {
        int listTop = HEADER_H + 4;
        int y = listTop - (int) sidebarScroll;

        if (my >= y && my < y + SIDEBAR_ITEM_H) { switchToChannel("world"); return; }
        y += SIDEBAR_ITEM_H + 2;

        if (my >= y && my < y + SIDEBAR_CAT_H) { privateExpanded = !privateExpanded; return; }
        y += SIDEBAR_CAT_H;

        if (privateExpanded) {
            for (ChatChannel ch : channels) {
                if (!ch.isPrivate()) continue;
                if (my >= y && my < y + SIDEBAR_ITEM_H) { switchToChannel(ch.getId()); return; }
                y += SIDEBAR_ITEM_H;
            }
        }
        y += 2;

        if (my >= y && my < y + SIDEBAR_CAT_H) { groupExpanded = !groupExpanded; return; }
        y += SIDEBAR_CAT_H;

        if (groupExpanded) {
            for (ChatChannel ch : channels) {
                if (!ch.isGroup()) continue;
                if (my >= y && my < y + SIDEBAR_ITEM_H) { switchToChannel(ch.getId()); return; }
                y += SIDEBAR_ITEM_H;
            }
        }
        y += 4;

        if (my >= y && my < y + SIDEBAR_ITEM_H) {
            creatingGroup = true;
            groupNameField.setText("");
            groupNameField.setFocused(true);
        }
    }

    private void switchToChannel(String channelId) {
        ChatMessageStore.setActiveChannel(channelId);
        activeChannel = ChatMessageStore.getActiveChannel();
        scrollToBottom = true;
        scrollTarget = 0;
        contextMsgIndex = -1;
        clearMention();
    }

    // ===== 发送 =====

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ChannelType type = activeChannel != null ? activeChannel.getType() : ChannelType.WORLD;

        if (text.startsWith("/")) {
            mc.player.sendChatMessage(text);
        } else if (type == ChannelType.WORLD) {
            mc.player.sendChatMessage(text);
        } else if (type == ChannelType.PRIVATE) {
            String partner = activeChannel.getPartnerName();
            PacketHandler.sendToServer(new ChatPrivatePacket(partner, text, mc.player.getName(), mc.player.getUniqueID()));
            ChatMessageStore.addMessage(new TextComponentString(text), mc.player.getUniqueID(),
                    new TextComponentString(mc.player.getName()), false, mc.player.getName(), "priv:" + partner);
            ChatMessageStore.incrementPendingEcho(text);
        } else if (type == ChannelType.GROUP) {
            PacketHandler.sendToServer(new ChatGroupPacket(activeChannel.getGroupId(), text,
                    mc.player.getName(), mc.player.getUniqueID()));
            ChatMessageStore.addMessage(new TextComponentString(text), mc.player.getUniqueID(),
                    new TextComponentString(mc.player.getName()), false, mc.player.getName(),
                    "group:" + activeChannel.getGroupId());
            ChatMessageStore.incrementPendingEcho(text);
        }

        mc.ingameGUI.getChatGUI().addToSentMessages(text);
        inputField.setText("");
        clearMention();
        scrollToBottom = true;
    }

    private void navigateHistory(int delta) {
        List<String> sent = mc.ingameGUI.getChatGUI().getSentMessages();
        int size = sent.size();
        int newPos = MathHelper.clamp(historyPos + delta, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) { historyPos = size; inputField.setText(historyBuffer); }
            else {
                if (historyPos == size) historyBuffer = inputField.getText();
                inputField.setText(sent.get(newPos));
                historyPos = newPos;
            }
        }
    }

    // ===== Tab 补全 =====

    private void completeTab() {
        String text = inputField.getText();
        int cursor = MathHelper.clamp(inputField.getCursorPosition(), 0, text.length());

        // 连续按 TAB：在同一轮候选里循环
        if (tabIndex >= 0 && !tabCandidates.isEmpty() && text.equals(tabBase + tabToken + tabSuffix)
                && cursor == (tabBase + tabToken).length()) {
            tabIndex = (tabIndex + 1) % tabCandidates.size();
            applyTabCandidate();
            return;
        }

        // 取光标前的一个词
        int start = cursor;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        tabBase = text.substring(0, start);
        tabToken = text.substring(start, cursor);
        tabSuffix = text.substring(cursor);
        // 允许 "文字@玩家" / "文字:表情" 这种没有空格的写法
        int mark = Math.max(tabToken.lastIndexOf('@'), tabToken.lastIndexOf(':'));
        if (mark > 0) {
            tabBase += tabToken.substring(0, mark);
            tabToken = tabToken.substring(mark);
        }
        tabCandidates = buildTabCandidates(tabToken);
        tabIndex = tabCandidates.isEmpty() ? -1 : 0;
        if (tabIndex < 0) return;
        applyTabCandidate();
    }

    private void applyTabCandidate() {
        tabToken = tabCandidates.get(tabIndex);
        String replaced = tabBase + tabToken;
        inputField.setText(replaced + tabSuffix);
        inputField.setCursorPosition(replaced.length());
        inputField.setSelectionPos(replaced.length());
    }

    private List<String> buildTabCandidates(String token) {
        List<String> candidates = new ArrayList<>();
        if (token.startsWith(":")) {
            String prefix = token.toLowerCase(Locale.ROOT);
            for (String code : EmojiRegistry.getAllShortcodes()) {
                if (code.toLowerCase(Locale.ROOT).startsWith(prefix)) candidates.add(code);
            }
            Collections.sort(candidates);
            return candidates;
        }
        if (!token.isEmpty() && !token.startsWith("@")) return candidates;
        if (mc.getConnection() == null) return candidates;
        String prefix = token.startsWith("@") ? token.substring(1).toLowerCase(Locale.ROOT) : "";
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null || name.equalsIgnoreCase(mc.player.getName())) continue;
            if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                candidates.add("@" + name);
            }
        }
        Collections.sort(candidates);
        return candidates;
    }

    // ===== @ 候选弹窗 =====

    private void refreshMentionPopup() {
        mentionCandidates.clear();
        mentionIndex = 0;
        mentionStart = -1;
        if (!inputField.isFocused() || searchOpen || settingsPanel.isOpen() || mc.player == null) {
            return;
        }
        String text = inputField.getText();
        int cursor = inputField.getCursorPosition();
        int start = cursor;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == ' ' || c == '\n' || c == '\t') break;
            start--;
        }
        if (start >= cursor) {
            return;
        }
        String token = text.substring(start, cursor);
        if (!token.startsWith("@") || token.length() > 24) {
            return;
        }
        String prefix = token.substring(1).toLowerCase(Locale.ROOT);
        String local = mc.player.getName();
        List<String> names = new ArrayList<>();
        if (mc.getConnection() != null) {
            for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
                if (info == null || info.getGameProfile() == null) continue;
                String name = info.getGameProfile().getName();
                if (name == null || name.equalsIgnoreCase(local)) continue;
                if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            return;
        }
        Collections.sort(names);
        mentionStart = start;
        mentionCandidates.addAll(names.subList(0, Math.min(6, names.size())));
    }

    private boolean mentionPopupVisible() {
        return mentionStart >= 0 && !mentionCandidates.isEmpty();
    }

    private int mentionPopupWidth() {
        int width = 0;
        for (String name : mentionCandidates) {
            width = Math.max(width, mc.fontRenderer.getStringWidth("@" + name));
        }
        return Math.max(90, width + 16);
    }

    private int mentionPopupHeight() {
        return mentionCandidates.size() * 12 + 4;
    }

    private int mentionPopupX() {
        return inputField.x;
    }

    private int mentionPopupY() {
        return inputField.y - mentionPopupHeight() - 4;
    }

    private void drawMentionPopup() {
        if (!mentionPopupVisible()) {
            return;
        }
        int x = mentionPopupX();
        int y = mentionPopupY();
        int w = mentionPopupWidth();
        int h = mentionPopupHeight();
        Gui.drawRect(x, y, x + w, y + h, ChatColors.CONTEXT_BG);
        Gui.drawRect(x, y, x + w, y + 1, ChatColors.ACCENT);
        for (int i = 0; i < mentionCandidates.size(); i++) {
            int rowY = y + 2 + i * 12;
            boolean selected = i == mentionIndex;
            if (selected) {
                Gui.drawRect(x + 1, rowY, x + w - 1, rowY + 12,
                        ChatRender.withAlpha(ChatColors.ACCENT, 0x33));
            }
            mc.fontRenderer.drawStringWithShadow("@" + mentionCandidates.get(i), x + 6, rowY + 2,
                    selected ? ChatColors.TEXT_PRIMARY : ChatColors.TEXT_SECONDARY);
        }
    }

    private boolean handleMentionClick(int mx, int my) {
        if (!mentionPopupVisible()) {
            return false;
        }
        int x = mentionPopupX();
        int y = mentionPopupY();
        int w = mentionPopupWidth();
        int h = mentionPopupHeight();
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        int index = (my - y - 2) / 12;
        if (index >= 0 && index < mentionCandidates.size()) {
            applyMention(mentionCandidates.get(index));
        }
        return true;
    }

    private void applyMention(String name) {
        String text = inputField.getText();
        int cursor = inputField.getCursorPosition();
        if (mentionStart < 0 || mentionStart > cursor) {
            clearMention();
            return;
        }
        String before = text.substring(0, mentionStart);
        String after = text.substring(cursor);
        String insert = "@" + name + " ";
        inputField.setText(before + insert + after);
        int pos = before.length() + insert.length();
        inputField.setCursorPosition(pos);
        inputField.setSelectionPos(pos);
        clearMention();
    }

    private void clearMention() {
        mentionCandidates.clear();
        mentionIndex = 0;
        mentionStart = -1;
    }

    // ===== 搜索辅助 =====

    private int firstMatchIndex() {
        List<ChatMessage> messages = ChatMessageStore.getActiveMessages();
        String query = searchQuery();
        if (query.isEmpty()) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (matchesQuery(messages.get(i), query)) return i;
        }
        return -1;
    }

    private void jumpToNextMatch() {
        List<ChatMessage> messages = ChatMessageStore.getActiveMessages();
        String query = searchQuery();
        if (query.isEmpty() || messages.isEmpty()) return;
        int size = messages.size();
        for (int step = 1; step <= size; step++) {
            int idx = (searchCursor + step) % size;
            if (matchesQuery(messages.get(idx), query)) {
                searchCursor = idx;
                pendingScrollToIndex = idx;
                return;
            }
        }
    }

    private String trimQuote(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.length() > 40) flat = flat.substring(0, 40) + "…";
        return flat;
    }

    private void outline(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 1, color);
        Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color);
        Gui.drawRect(x + w - 1, y, x + w, y + h, color);
    }

    private String timeKey(LocalTime t) {
        int m = (t.getMinute() / 5) * 5;
        return String.format("%02d:%02d", t.getHour(), m);
    }

    private void enableScissor(int l, int t, int r, int b, int w, int h) {
        double s = mc.displayWidth / (double) w;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (l * s), (int) ((h - b) * s), (int) ((r - l) * s), (int) ((b - t) * s));
    }

    private void disableScissor() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
}
