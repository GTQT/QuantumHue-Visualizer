package meowmel.quantumhue.chat;

import meowmel.quantumhue.chat.ChatChannel.ChannelType;
import meowmel.quantumhue.chat.ChatMessageStore.ChatMessage;
import meowmel.quantumhue.chat.packets.ChatGroupPacket;
import meowmel.quantumhue.chat.packets.ChatPrivatePacket;
import meowmel.quantumhue.chat.packets.GroupManagePacket;
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
    private static final int SIDEBAR_W = 115;
    private static final int HEADER_H = 28;
    private static final int BAR_H = 26;
    private static final int TOOLBAR_H = 26;
    private static final int PAD = 8;
    private static final int AVATAR = 16;
    private static final int BUBBLE_PAD_X = 6;
    private static final int BUBBLE_PAD_Y = 4;
    private static final int GAP = 4;
    private static final int NAME_H = AVATAR; // 与头像同高，避免头像底部与气泡重叠
    private static final int SCROLLBAR_W = 5;
    private static final int INPUT_H = 14;
    private static final int SIDEBAR_ITEM_H = 20;
    private static final int SIDEBAR_CAT_H = 22;
    private static final int TIME_SEP_H = 14;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int ANIM_MS = 200;

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
    private static final int CTX_W = 70;
    private static final int CTX_ITEM_H = 16;

    // 头像右键菜单
    private int avatarContextIndex = -1;
    private int avatarContextX, avatarContextY;
    private static final int AVATAR_CTX_W = 70;

    // 气泡位置追踪 (用于头像点击检测)
    private final List<int[]> bubbleRects = new ArrayList<>();

    // 创建群聊
    private boolean creatingGroup;
    private GuiTextField groupNameField;

    // 历史记录轮播
    private String historyBuffer = "";
    private int historyPos = -1;

    public ChatScreen(String initialText) {
        this.initialText = initialText != null ? initialText : "";
    }

    private int panelRight() { return panelW; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // 面板宽度：屏幕宽度的 65%，最小300，最大700
        panelW = MathHelper.clamp(width * 65 / 100, 300, Math.min(700, width));

        historyPos = mc.ingameGUI.getChatGUI().getSentMessages().size();
        ChatMessageStore.setScreenOpen(true);
        animStart = System.currentTimeMillis();
        closing = false;
        firstRender = true;

        channels = ChatMessageStore.getChannels();
        activeChannel = ChatMessageStore.getActiveChannel();

        int inputX = SIDEBAR_W + PAD;
        int inputW = panelRight() - SIDEBAR_W - PAD * 2 - 18;
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
        if (creatingGroup) groupNameField.updateCursorCounter();
    }

    // ===== 渲染入口 =====

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float anim = ChatAnimation.progress(animStart, ANIM_MS, closing);
        int slideX = (int) ((anim - 1.0f) * panelW);

        // 平滑滚动
        scrollOffset += (scrollTarget - scrollOffset) * 0.3f;
        if (Math.abs(scrollTarget - scrollOffset) < 0.5f) scrollOffset = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * 0.3f;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.5f) sidebarScroll = sidebarScrollTarget;

        // 面板向左滑入
        GlStateManager.pushMatrix();
        GlStateManager.translate(slideX, 0, 0);

        // === 半透明面板背景 === (0xCC = 80% 不透明度)
        int panelBg = ChatColors.PANEL_BG;
        int alpha = (int) (0xCC * anim);
        Gui.drawRect(0, 0, panelRight(), height, ((alpha << 24) | (panelBg & 0x00FFFFFF)));
        // 右边缘亮线
        Gui.drawRect(panelRight() - 1, 0, panelRight(), height, ChatColors.DIVIDER);

        drawSidebar(mouseX - slideX, mouseY);
        drawHeader(mouseX, mouseY);
        drawMessages(mouseX, mouseY);
        drawToolbar(mouseX, mouseY);
        drawInputBar(mouseX, mouseY);
        drawContextMenu(mouseX, mouseY);
        drawAvatarContextMenu(mouseX, mouseY);

        if (creatingGroup) drawCreateGroupDialog(mouseX, mouseY);

        GlStateManager.popMatrix();

        if (closing && System.currentTimeMillis() - animStart >= ANIM_MS) {
            mc.displayGuiScreen(null);
        }
    }

    // ===== 侧边栏 =====

    private void drawSidebar(int mx, int my) {
        Gui.drawRect(0, 0, SIDEBAR_W, height, ChatColors.SIDEBAR_BG);
        Gui.drawRect(SIDEBAR_W - 1, 0, SIDEBAR_W, height, ChatColors.SIDEBAR_DIVIDER);

        // 标题
        Gui.drawRect(0, 0, SIDEBAR_W, HEADER_H, ChatColors.HEADER_BG);
        Gui.drawRect(0, HEADER_H - 1, SIDEBAR_W, HEADER_H, ChatColors.DIVIDER);
        mc.fontRenderer.drawStringWithShadow("§lChat",
                (SIDEBAR_W - mc.fontRenderer.getStringWidth("Chat")) / 2, 10, ChatColors.ACCENT);

        int listTop = HEADER_H + 4;
        int listBot = height;
        enableScissor(0, listTop, SIDEBAR_W, listBot, width, height);

        int y = listTop - (int) sidebarScroll;
        FontRenderer fr = mc.fontRenderer;

        // 世界频道
        {
            int bg = isActiveWorld() ? ChatColors.SIDEBAR_SEL
                    : (mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_ITEM_H ? ChatColors.SIDEBAR_HOVER : 0);
            if (bg != 0) Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, bg);
            fr.drawStringWithShadow("🌐 " + ChatChannel.world().getDisplayName(), 6, y + 4, ChatColors.TEXT_PRIMARY);
            y += SIDEBAR_ITEM_H;
        }
        y += 2;

        // 私聊分类
        {
            boolean catHover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_CAT_H;
            Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_CAT_H, catHover ? ChatColors.SIDEBAR_HOVER : ChatColors.SIDEBAR_CAT);
            Gui.drawRect(0, y + SIDEBAR_CAT_H - 1, SIDEBAR_W, y + SIDEBAR_CAT_H, ChatColors.DIVIDER);
            fr.drawStringWithShadow((privateExpanded ? "▼" : "▶") + " 私聊", 6, y + 5, ChatColors.TEXT_SECONDARY);
            y += SIDEBAR_CAT_H;

            if (privateExpanded) {
                for (ChatChannel ch : channels) {
                    if (!ch.isPrivate()) continue;
                    boolean sel = ch == activeChannel, hov = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_ITEM_H;
                    if (sel) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + SIDEBAR_ITEM_H, ChatColors.ACCENT);
                    } else if (hov) Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_HOVER);
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
            boolean catHover = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_CAT_H;
            Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_CAT_H, catHover ? ChatColors.SIDEBAR_HOVER : ChatColors.SIDEBAR_CAT);
            Gui.drawRect(0, y + SIDEBAR_CAT_H - 1, SIDEBAR_W, y + SIDEBAR_CAT_H, ChatColors.DIVIDER);
            fr.drawStringWithShadow((groupExpanded ? "▼" : "▶") + " 群聊", 6, y + 5, ChatColors.TEXT_SECONDARY);
            y += SIDEBAR_CAT_H;

            if (groupExpanded) {
                for (ChatChannel ch : channels) {
                    if (!ch.isGroup()) continue;
                    boolean sel = ch == activeChannel, hov = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_ITEM_H;
                    if (sel) {
                        Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_SEL);
                        Gui.drawRect(0, y, 2, y + SIDEBAR_ITEM_H, ChatColors.ACCENT);
                    } else if (hov) Gui.drawRect(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, ChatColors.SIDEBAR_HOVER);
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
            boolean hov = mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + SIDEBAR_ITEM_H;
            int bg = hov ? ChatColors.SIDEBAR_HOVER : 0;
            if (bg != 0) Gui.drawRect(2, y, SIDEBAR_W - 2, y + SIDEBAR_ITEM_H, bg);
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
            int barX = SIDEBAR_W - 4;
            Gui.drawRect(barX, listTop, barX + 3, height, ChatColors.SCROLLBAR_BG);
            Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, ChatColors.SCROLLBAR_FG);
        }
    }

    private boolean isActiveWorld() { return activeChannel != null && activeChannel.isWorld(); }

    // ===== 页眉 =====

    private void drawHeader(int mx, int my) {
        Gui.drawRect(SIDEBAR_W, 0, panelRight(), HEADER_H, ChatColors.HEADER_BG);
        Gui.drawRect(SIDEBAR_W, HEADER_H - 1, panelRight(), HEADER_H, ChatColors.DIVIDER);

        String title = activeChannel != null ? activeChannel.getDisplayName() : "聊天";
        if (activeChannel != null && activeChannel.isGroup())
            title = "👥 " + title + " (" + activeChannel.getMemberNames().size() + ")";
        mc.fontRenderer.drawStringWithShadow(title, SIDEBAR_W + PAD,
                (HEADER_H - 8) / 2, ChatColors.TEXT_HEADER);

        // 关闭按钮
        int cx = panelRight() - 20, cy = 6;
        boolean hover = mx >= cx && mx < cx + 14 && my >= cy && my < cy + 14;
        Gui.drawRect(cx, cy, cx + 14, cy + 14, hover ? ChatColors.SIDEBAR_HOVER : 0);
        mc.fontRenderer.drawStringWithShadow("✕", cx + 4, cy + 3, ChatColors.TEXT_SECONDARY);
    }

    // ===== 消息气泡 =====

    private void drawMessages(int mx, int my) {
        bubbleRects.clear();
        List<ChatMessage> messages = ChatMessageStore.getActiveMessages();
        int msgTop = HEADER_H + 2;
        int msgBot = height - BAR_H - TOOLBAR_H - 6;
        int contentW = panelRight() - SIDEBAR_W;

        if (messages.isEmpty()) {
            String hint = "暂无消息";
            int hw = mc.fontRenderer.getStringWidth(hint);
            mc.fontRenderer.drawStringWithShadow(hint,
                    SIDEBAR_W + (contentW - hw) / 2,
                    msgBot / 2, ChatColors.TEXT_SECONDARY);
            return;
        }

        int areaH = msgBot - msgTop;

        int totalH = 0;
        String lastTimeKey = null;
        for (ChatMessage msg : messages) {
            totalH += getMsgHeight(msg) + GAP;
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastTimeKey == null || !key.equals(lastTimeKey)) { lastTimeKey = key; totalH += TIME_SEP_H + GAP; }
            }
        }

        maxScroll = Math.max(0, totalH - areaH);

        if (firstRender) { scrollOffset = maxScroll; scrollTarget = maxScroll; firstRender = false; }
        else if (scrollToBottom) { scrollTarget = maxScroll; }
        scrollTarget = MathHelper.clamp(scrollTarget, 0, maxScroll);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        enableScissor(SIDEBAR_W, msgTop, panelRight(), msgBot, width, height);
        int contentY = msgTop - (int) scrollOffset;
        lastTimeKey = null;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastTimeKey == null || !key.equals(lastTimeKey)) {
                    lastTimeKey = key;
                    if (contentY + TIME_SEP_H > msgTop && contentY < msgBot)
                        drawTimeSeparator(msg.time(), contentY);
                    contentY += TIME_SEP_H + GAP;
                }
            }

            int h = getMsgHeight(msg);
            int screenY = contentY;
            contentY += h + GAP;

            if (screenY + h > msgTop && screenY < msgBot)
                drawBubble(msg, screenY, i);
        }
        disableScissor();

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

    /** 气泡文本统一换行宽度 (getMsgHeight 和 drawBubble 共用) */
    private int textWrapWidth() {
        // 气泡可用区: 面板宽度 - 侧边栏 - 两侧padding - 头像 - 头像与气泡间距 - 气泡内边距
        return panelRight() - SIDEBAR_W - PAD * 2 - AVATAR - GAP - BUBBLE_PAD_X * 2;
    }

    private int getMsgHeight(ChatMessage msg) {
        int wrapW = textWrapWidth();
        if (msg.isSystem()) {
            List<String> lines = mc.fontRenderer.listFormattedStringToWidth(
                    msg.content().getFormattedText(), wrapW);
            return lines.size() * mc.fontRenderer.FONT_HEIGHT + 4;
        }
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(
                msg.content().getFormattedText(), wrapW);
        return lines.size() * mc.fontRenderer.FONT_HEIGHT + BUBBLE_PAD_Y * 2 + NAME_H;
    }

    private void drawBubble(ChatMessage msg, int baseY, int index) {
        int r = panelRight();
        int contentW = r - SIDEBAR_W;
        boolean own = msg.isOwn();

        if (msg.isSystem()) {
            int wrapW = contentW - PAD * 2;
            List<String> lines = mc.fontRenderer.listFormattedStringToWidth(
                    msg.content().getFormattedText(), wrapW);
            int yy = baseY + 2;
            for (String line : lines) {
                int lw = mc.fontRenderer.getStringWidth(line);
                mc.fontRenderer.drawStringWithShadow(line,
                        SIDEBAR_W + (contentW - lw) / 2, yy, ChatColors.TEXT_SECONDARY);
                yy += mc.fontRenderer.FONT_HEIGHT;
            }
            return;
        }

        // 统一换行宽度
        int wrapW = textWrapWidth();
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(
                msg.content().getFormattedText(), wrapW);

        // 最长行实际宽度
        int textW = 0;
        for (String line : lines)
            textW = Math.max(textW, mc.fontRenderer.getStringWidth(line));

        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * mc.fontRenderer.FONT_HEIGHT + BUBBLE_PAD_Y * 2;

        // 头像和气泡位置
        int avatarX, bubbleX;
        if (own) {
            avatarX = r - PAD - AVATAR;
            // 气泡右缘贴头像左边-4, 左缘 = 右缘 - bubbleW
            bubbleX = avatarX - GAP - bubbleW;
            // 不超出左边界
            if (bubbleX < SIDEBAR_W + PAD) bubbleX = SIDEBAR_W + PAD;
        } else {
            avatarX = SIDEBAR_W + PAD;
            // 气泡左缘在头像右边+4
            bubbleX = avatarX + AVATAR + GAP;
            // 不超出右边界
            if (bubbleX + bubbleW > r - PAD) bubbleX = r - PAD - bubbleW;
        }

        // 名字
        if (msg.senderName() != null && !msg.senderName().getUnformattedText().isEmpty()) {
            String name = msg.senderName().getUnformattedText();
            String displayName = mc.fontRenderer.trimStringToWidth(name, bubbleW - BUBBLE_PAD_X);
            int nameX = own ? bubbleX + bubbleW - mc.fontRenderer.getStringWidth(displayName) - BUBBLE_PAD_X
                            : bubbleX + BUBBLE_PAD_X;
            mc.fontRenderer.drawStringWithShadow(displayName, nameX, baseY, ChatColors.NAME_TEXT);
        }

        int bubbleY = baseY + NAME_H;

        // 气泡背景
        Gui.drawRect(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
                own ? ChatColors.OWN_BUBBLE : ChatColors.OTHER_BUBBLE);

        // 气泡文字 — 每行都从 bubbleX + BUBBLE_PAD_X 开始
        for (int li = 0; li < lines.size(); li++)
            mc.fontRenderer.drawStringWithShadow(lines.get(li),
                    bubbleX + BUBBLE_PAD_X, bubbleY + BUBBLE_PAD_Y + li * mc.fontRenderer.FONT_HEIGHT,
                    ChatColors.BUBBLE_TEXT);

        // 头像
        drawPlayerHead(msg.senderUUID(), avatarX, baseY);
        bubbleRects.add(new int[]{avatarX, baseY, AVATAR, AVATAR, index});

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
        int tx = SIDEBAR_W + (panelRight() - SIDEBAR_W - tw) / 2;
        Gui.drawRect(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, 0x44161630);
        mc.fontRenderer.drawStringWithShadow(text, tx, y + 3, ChatColors.TIME_TEXT);
    }

    // ===== 输入栏 =====

    // ===== 快捷工具栏 =====

    private static final String LABEL_COORD = "分享坐标";
    private static final String LABEL_ITEM = "分享物品";
    private static final int TOOLBAR_BTN_PAD = 10;

    private void drawToolbar(int mx, int my) {
        int toolTop = height - BAR_H - TOOLBAR_H, r = panelRight();
        Gui.drawRect(SIDEBAR_W, toolTop, r, toolTop + TOOLBAR_H, ChatColors.BAR_BG);
        Gui.drawRect(SIDEBAR_W, toolTop, r, toolTop + 1, ChatColors.DIVIDER);

        int y = toolTop + (TOOLBAR_H - mc.fontRenderer.FONT_HEIGHT) / 2;
        int x = SIDEBAR_W + PAD;
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
    }

    private void handleToolbarClick(int mx, int my) {
        int toolTop = height - BAR_H - TOOLBAR_H;
        if (my < toolTop || my >= toolTop + TOOLBAR_H) return;

        int x = SIDEBAR_W + PAD;
        int gap = 6;
        int coordW = mc.fontRenderer.getStringWidth(LABEL_COORD) + TOOLBAR_BTN_PAD;
        int itemW = mc.fontRenderer.getStringWidth(LABEL_ITEM) + TOOLBAR_BTN_PAD;

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
        Gui.drawRect(SIDEBAR_W, barTop, r, height, ChatColors.BAR_BG);
        Gui.drawRect(SIDEBAR_W, barTop, r, barTop + 1, ChatColors.DIVIDER);

        int inputX = SIDEBAR_W + PAD;
        int inputW = r - SIDEBAR_W - PAD * 2 - 18;
        int inputY = barTop + (BAR_H - INPUT_H) / 2;

        // 输入框背景
        Gui.drawRect(inputX - 1, inputY - 1, inputX + inputW + 1, inputY, ChatColors.INPUT_BORDER);
        Gui.drawRect(inputX - 1, inputY, inputX + inputW + 1, inputY + INPUT_H, ChatColors.INPUT_BG);

        boolean hoverInput = mx >= inputX && mx < inputX + inputW && my >= inputY && my < inputY + INPUT_H;
        if (hoverInput || inputField.isFocused()) {
            Gui.drawRect(inputX - 1, inputY - 1, inputX + inputW + 1, inputY, ChatColors.ACCENT);
            Gui.drawRect(inputX - 1, inputY - 1, inputX, inputY + INPUT_H, ChatColors.ACCENT);
            Gui.drawRect(inputX + inputW, inputY - 1, inputX + inputW + 1, inputY + INPUT_H, ChatColors.ACCENT);
            Gui.drawRect(inputX - 1, inputY + INPUT_H, inputX + inputW + 1, inputY + INPUT_H + 1, ChatColors.ACCENT);
        }

        if (inputField.getText().isEmpty())
            mc.fontRenderer.drawStringWithShadow(getPlaceholder(), inputX + 2, inputY + 3, ChatColors.TEXT_SECONDARY);

        inputField.drawTextBox();

        // 发送按钮
        int sendX = r - PAD - 14, sendY = barTop + (BAR_H - 12) / 2;
        boolean hoverSend = mx >= sendX - 2 && mx < sendX + 14 && my >= sendY - 2 && my < sendY + 14;
        Gui.drawRect(sendX - 2, sendY - 2, sendX + 14, sendY + 14, hoverSend ? ChatColors.SEND_HOVER : 0);
        mc.fontRenderer.drawStringWithShadow("▶", sendX, sendY + 2, ChatColors.ACCENT);
    }

    private String getPlaceholder() {
        if (activeChannel == null || activeChannel.isWorld()) return "输入消息... (Tab 补全命令)";
        if (activeChannel.isPrivate()) return "私聊 " + activeChannel.getDisplayName() + "...";
        return "群聊 " + activeChannel.getDisplayName() + "...";
    }

    // ===== 右键菜单 =====

    private void drawContextMenu(int mx, int my) {
        if (contextMsgIndex < 0) return;
        int pRight = panelRight();
        int menuH = CTX_ITEM_H + 2;
        int menuX = Math.min(contextX, pRight - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < HEADER_H) menuY = contextY + 4;

        Gui.drawRect(menuX, menuY, menuX + CTX_W, menuY + menuH, ChatColors.CONTEXT_BG);
        Gui.drawRect(menuX, menuY, menuX + CTX_W, menuY + 1, ChatColors.DIVIDER);
        Gui.drawRect(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, ChatColors.DIVIDER);

        boolean hover = mx >= menuX && mx < menuX + CTX_W && my >= menuY && my < menuY + CTX_ITEM_H;
        if (hover) Gui.drawRect(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, ChatColors.CONTEXT_HOVER);
        mc.fontRenderer.drawStringWithShadow("复制消息", menuX + 6, menuY + 3, ChatColors.TEXT_PRIMARY);
    }

    // ===== 头像右键菜单 =====

    private void drawAvatarContextMenu(int mx, int my) {
        if (avatarContextIndex < 0) return;
        int itemH = CTX_ITEM_H;
        int itemCount = activeChannel != null && activeChannel.isGroup() ? 2 : 1;
        int menuH = itemCount * itemH + 2;
        int pRight = panelRight();
        int menuX = Math.min(avatarContextX, pRight - AVATAR_CTX_W - 2);
        int menuY = avatarContextY - menuH;
        if (menuY < HEADER_H) menuY = avatarContextY + 4;

        Gui.drawRect(menuX, menuY, menuX + AVATAR_CTX_W, menuY + menuH, ChatColors.CONTEXT_BG);
        Gui.drawRect(menuX, menuY, menuX + AVATAR_CTX_W, menuY + 1, ChatColors.DIVIDER);
        Gui.drawRect(menuX, menuY + menuH - 1, menuX + AVATAR_CTX_W, menuY + menuH, ChatColors.DIVIDER);

        // "发起私聊"
        boolean hover1 = mx >= menuX && mx < menuX + AVATAR_CTX_W && my >= menuY && my < menuY + itemH;
        if (hover1) Gui.drawRect(menuX + 1, menuY + 1, menuX + AVATAR_CTX_W - 1, menuY + itemH, ChatColors.CONTEXT_HOVER);
        mc.fontRenderer.drawStringWithShadow("发起私聊", menuX + 6, menuY + 3, ChatColors.TEXT_PRIMARY);

        if (itemCount > 1) {
            Gui.drawRect(menuX + 4, menuY + itemH, menuX + AVATAR_CTX_W - 4, menuY + itemH + 1, ChatColors.DIVIDER);
            boolean hover2 = mx >= menuX && mx < menuX + AVATAR_CTX_W && my >= menuY + itemH + 1 && my < menuY + menuH;
            if (hover2) Gui.drawRect(menuX + 1, menuY + itemH + 1, menuX + AVATAR_CTX_W - 1, menuY + menuH - 1, ChatColors.CONTEXT_HOVER);
            mc.fontRenderer.drawStringWithShadow("邀请进群", menuX + 6, menuY + itemH + 3, ChatColors.TEXT_PRIMARY);
        }
    }

    private void handleAvatarContextClick(int mx, int my) {
        int itemH = CTX_ITEM_H;
        int itemCount = activeChannel != null && activeChannel.isGroup() ? 2 : 1;
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

        if (mx >= menuX && mx < menuX + AVATAR_CTX_W && my >= menuY && my < menuY + itemH) {
            // 发起私聊
            UUID partnerUuid = msg.senderUUID();
            ChatChannel priv = ChatMessageStore.findOrCreatePrivateChannel(name, partnerUuid);
            switchToChannel(priv.getId());
        } else if (itemCount > 1 && mx >= menuX && mx < menuX + AVATAR_CTX_W
                && my >= menuY + itemH + 1 && my < menuY + menuH) {
            // 邀请进群
            PacketHandler.sendToServer(GroupManagePacket.invite(
                    activeChannel.getGroupId(), name));
        }
        avatarContextIndex = -1;
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

    // ===== 输入处理 =====

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (inputField.getText().isEmpty()) {
                closing = true;
                animStart = System.currentTimeMillis();
            } else {
                inputField.setText("");
            }
            return;
        }

        if (keyCode == Keyboard.KEY_TAB) return; // TODO: 补全

        if (keyCode == Keyboard.KEY_RETURN) { sendMessage(); return; }
        if (keyCode == Keyboard.KEY_UP) { navigateHistory(-1); return; }
        if (keyCode == Keyboard.KEY_DOWN) { navigateHistory(1); return; }

        inputField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (mx > panelRight()) return; // 面板外忽略

            if (mx < SIDEBAR_W)
                sidebarScrollTarget = MathHelper.clamp(sidebarScrollTarget - dw * 0.35f, 0, sidebarMaxScroll);
            else {
                scrollToBottom = false;
                scrollTarget = MathHelper.clamp(scrollTarget - dw * 0.4f, 0, maxScroll);
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

        // 面板外点击 → 关闭
        if (mx > panelRight()) {
            onGuiClosed();
            mc.displayGuiScreen(null);
            return;
        }

        // 头像右键菜单
        if (avatarContextIndex >= 0) {
            handleAvatarContextClick(mx, my);
            return;
        }

        if (contextMsgIndex >= 0) {
            int menuH = CTX_ITEM_H + 2;
            int menuX = Math.min(contextX, panelRight() - CTX_W - 2);
            int menuY = contextY - menuH;
            if (menuY < HEADER_H) menuY = contextY + 4;
            if (mx >= menuX && mx < menuX + CTX_W && my >= menuY && my < menuY + CTX_ITEM_H) {
                List<ChatMessage> msgs = ChatMessageStore.getActiveMessages();
                if (contextMsgIndex >= 0 && contextMsgIndex < msgs.size())
                    setClipboardString(msgs.get(contextMsgIndex).content().getUnformattedText());
            }
            contextMsgIndex = -1;
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

        if (btn == 1 && mx > SIDEBAR_W && my > HEADER_H && my < height - BAR_H - TOOLBAR_H) {
            contextMsgIndex = -1;
            contextX = mx;
            contextY = my;
            return;
        }

        if (btn == 0 && mx < SIDEBAR_W) { handleSidebarClick(mx, my); return; }

        // 工具栏点击
        if (btn == 0 && my >= height - BAR_H - TOOLBAR_H && my < height - BAR_H) {
            handleToolbarClick(mx, my); return;
        }

        int sendX = panelRight() - PAD - 14, sendY = height - BAR_H + (BAR_H - 12) / 2;
        if (btn == 0 && mx >= sendX - 2 && mx < sendX + 14 && my >= sendY - 2 && my < sendY + 14) {
            sendMessage(); return;
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
