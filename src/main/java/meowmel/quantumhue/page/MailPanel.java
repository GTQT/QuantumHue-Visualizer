package meowmel.quantumhue.page;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.mail.MailDetail;
import meowmel.quantumhue.mail.MailSummary;
import meowmel.quantumhue.mail.client.MailClientStore;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.MessageMailAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 公告页里“邮件”页签的面板：左侧邮件列表 + 右侧正文/奖励/领取按钮。
 *
 * <p>面板只读 {@link MailClientStore}，所有数据来自服务端的 {@code MessageMailView}；点击领取只发送
 * 邮件 id，奖励由服务端根据远程邮件配置发放。
 */
@SideOnly(Side.CLIENT)
public final class MailPanel {

    private static final int ROW_H = 26;
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 4;
    private static final int REWARD_COLS = 5;
    private static final int CLAIM_W = 96;
    private static final int CLAIM_H = 20;
    private static final long LIST_REQUEST_INTERVAL_MS = 1000L;
    private static final long DETAIL_REQUEST_INTERVAL_MS = 1200L;
    /** 领取请求的兜底超时，避免服务端包丢失后按钮永远卡在“领取中”。 */
    private static final long CLAIM_TIMEOUT_MS = 5000L;

    private final GuiAnnouncement host;

    private int listScroll;
    private int bodyScroll;
    private int listMaxScroll;
    private int bodyMaxScroll;

    private String selectedId = "";
    private String requestedDetailId = "";
    private long lastListRequestAt;
    private long lastDetailRequestAt;
    private long claimSentAt;
    private boolean claimPending;

    // 每帧计算出来的矩形
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int detailX;
    private int detailY;
    private int detailW;
    private int detailH;
    private int bodyX;
    private int bodyY;
    private int bodyW;
    private int bodyH;
    private int rewardsY;
    private int rewardsHeight;
    private int claimX;
    private int claimY;

    /** 当前帧鼠标悬停的奖励物品，等所有面板画完后再弹 tooltip，避免被按钮盖住。 */
    private ItemStack hoveredReward;

    public MailPanel(GuiAnnouncement host) {
        this.host = host;
    }

    // ================================================================ 打开 / 请求

    /** 切到邮件页签时调用：刷新列表并恢复之前选中的邮件。 */
    public void open() {
        requestList(false);
        ensureSelection();
        requestDetailIfNeeded();
    }

    private void requestList(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastListRequestAt < LIST_REQUEST_INTERVAL_MS) {
            return;
        }
        lastListRequestAt = now;
        MailClientStore.markLoading();
        PacketHandler.sendToServer(MessageMailAction.list());
    }

    private void ensureSelection() {
        List<MailSummary> summaries = MailClientStore.summaries();
        if (!selectedId.isEmpty() && MailClientStore.find(selectedId) != null) {
            return;
        }
        selectedId = "";
        MailSummary fallback = null;
        for (MailSummary summary : summaries) {
            if (fallback == null) {
                fallback = summary;
            }
            if (summary.active() && !summary.claimed) {
                selectedId = summary.id;
                return;
            }
        }
        if (fallback != null) {
            selectedId = fallback.id;
        }
    }

    private void requestDetailIfNeeded() {
        if (selectedId.isEmpty()) {
            return;
        }
        MailDetail detail = MailClientStore.detail();
        if (detail != null && selectedId.equals(detail.id)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (selectedId.equals(requestedDetailId) && now - lastDetailRequestAt < DETAIL_REQUEST_INTERVAL_MS) {
            return;
        }
        requestedDetailId = selectedId;
        lastDetailRequestAt = now;
        PacketHandler.sendToServer(MessageMailAction.detail(selectedId));
    }

    private void select(String mailId) {
        if (mailId == null || mailId.isEmpty()) {
            return;
        }
        selectedId = mailId;
        bodyScroll = 0;
        claimPending = false;
        MailClientStore.clearError();
        requestDetailIfNeeded();
    }

    private void claim() {
        MailDetail detail = MailClientStore.detail();
        if (detail == null || !selectedId.equals(detail.id) || !detail.claimable() || claimPending) {
            return;
        }
        claimPending = true;
        claimSentAt = System.currentTimeMillis();
        MailClientStore.clearError();
        PacketHandler.sendToServer(MessageMailAction.claim(selectedId));
    }

    // ================================================================ 绘制

    public void draw(int mouseX, int mouseY) {
        computeLayout();
        if (!MailClientStore.isEnabled()) {
            drawCentered("邮件系统未启用", detailX, detailY + detailH / 2 - 4, detailW);
            return;
        }
        ensureSelection();
        requestDetailIfNeeded();
        if (MailClientStore.isLoading() && System.currentTimeMillis() - lastListRequestAt > 5000L) {
            requestList(true);
        }
        MailDetail detail = MailClientStore.detail();
        if (claimPending) {
            boolean claimedNow = detail != null && selectedId.equals(detail.id) && detail.claimed;
            boolean failedNow = !MailClientStore.error().isEmpty()
                    && selectedId.equals(MailClientStore.errorMailId());
            if (claimedNow || failedNow
                    || System.currentTimeMillis() - claimSentAt > CLAIM_TIMEOUT_MS) {
                claimPending = false;
                if (!claimedNow && !failedNow) {
                    MailClientStore.setError(selectedId, "§c领取超时，请重试");
                }
            }
        }

        drawList(mouseX, mouseY);
        hoveredReward = null;
        drawDetail(mouseX, mouseY);
        if (hoveredReward != null) {
            host.tooltip(hoveredReward.getTooltip(Minecraft.getMinecraft().player,
                    ITooltipFlag.TooltipFlags.NORMAL), mouseX, mouseY);
        }
    }

    private void computeLayout() {
        int x = host.contentX;
        int y = host.contentY;
        int w = host.contentWidth;
        int h = host.contentHeight;

        listX = x + 2;
        listY = y + 2;
        listH = Math.max(40, h - 4);
        int listWidth = Math.max(110, Math.min(200, (int) (w * 0.42F)));
        if (w < 260) {
            listWidth = Math.max(80, w / 2);
        }
        listW = Math.min(listWidth, Math.max(80, w - 100));

        detailX = listX + listW + 8;
        detailY = y + 2;
        detailW = Math.max(60, x + w - 2 - detailX);
        detailH = listH;

        claimY = detailY + detailH - CLAIM_H - 4;
        claimX = detailX + detailW - CLAIM_W - 4;

        // 奖励区不能把正文挤成负数：窗口很矮时只显示放得下的部分
        int rawRewardsHeight = rewardsHeight(MailClientStore.detail());
        int maxRewardsHeight = Math.max(0, claimY - 8 - (detailY + 30 + 16));
        rewardsHeight = Math.min(rawRewardsHeight, maxRewardsHeight);
        rewardsY = claimY - 8 - rewardsHeight;

        bodyX = detailX + 4;
        bodyY = detailY + 30;
        bodyW = Math.max(20, detailW - 10);
        int bodyBottom = Math.max(bodyY + 16, rewardsY - 6);
        bodyH = Math.max(16, bodyBottom - bodyY);
    }

    private int rewardsHeight(MailDetail detail) {
        if (detail == null || detail.rewards.isEmpty() && detail.money <= 0L) {
            return 0;
        }
        int rows = detail == null ? 0 : (detail.rewards.size() + REWARD_COLS - 1) / REWARD_COLS;
        int height = rows * (SLOT + SLOT_GAP);
        if (detail != null && detail.money > 0L) {
            height += 12;
        }
        return height;
    }

    private void drawList(int mouseX, int mouseY) {
        host.fill(listX, listY, listW, listH, PageTheme.CONTENT_BG);
        host.fill(listX + listW, listY, 1, listH, PageTheme.SEPARATOR);

        List<MailSummary> summaries = MailClientStore.summaries();
        listMaxScroll = Math.max(0, summaries.size() * ROW_H - listH + 4);
        listScroll = clamp(0, listMaxScroll, listScroll);

        host.beginClip(listX, listY, listW, listH);
        int rowY = listY + 2 - listScroll;
        for (int i = 0; i < summaries.size(); i++) {
            MailSummary summary = summaries.get(i);
            boolean selected = summary.id.equals(selectedId);
            boolean hovered = inside(mouseX, mouseY, listX, rowY, listW, ROW_H);
            if (selected) {
                host.fill(listX, rowY, listW, ROW_H, PageTheme.ITEM_SELECTED);
                host.fill(listX, rowY, 2, ROW_H, PageTheme.ACCENT);
            } else if (hovered) {
                host.fill(listX, rowY, listW, ROW_H, PageTheme.ITEM_HOVER);
            }

            int dotColor = summary.claimed ? PageTheme.TEXT_FAINT
                    : (summary.expired ? 0xFFFF5555 : (summary.active() ? PageTheme.ACCENT : 0xFFFFCC55));
            host.fill(listX + 5, rowY + 10, 3, 3, dotColor);

            FontRenderer font = host.font();
            String title = font.trimStringToWidth(summary.title, listW - 58);
            font.drawString(title, listX + 12, rowY + 4, PageTheme.TITLE, false);
            String status = summary.statusText();
            font.drawString(status, listX + listW - 6 - font.getStringWidth(status), rowY + 4,
                    PageTheme.TEXT_DIM, false);
            String subtitle = font.trimStringToWidth(summary.sender + "  §8" + summary.date, listW - 18);
            font.drawString(subtitle, listX + 12, rowY + 15, PageTheme.TEXT_FAINT, false);
            rowY += ROW_H;
        }
        host.endClip();

        if (listMaxScroll > 0) {
            host.drawScrollbar(listX + listW - 4, listY, 4, listH, listScroll, listMaxScroll,
                    false, mouseX, mouseY);
        }

        if (summaries.isEmpty()) {
            String message;
            if (MailClientStore.isLoading()) {
                message = "§7正在同步邮件…";
            } else if (!MailClientStore.listNote().isEmpty()) {
                message = "§7" + MailClientStore.listNote();
            } else {
                message = "§7邮箱为空";
            }
            drawCentered(message, listX + 4, listY + listH / 2 - 4, listW - 8);
        }
    }

    private void drawDetail(int mouseX, int mouseY) {
        MailDetail detail = MailClientStore.detail();
        if (detail == null || !selectedId.equals(detail.id)) {
            String message;
            String error = MailClientStore.error();
            if (!error.isEmpty() && selectedId.equals(MailClientStore.errorMailId())) {
                message = error;
            } else {
                message = MailClientStore.isLoading() ? "§7正在加载邮件…" : "§7选择左侧邮件查看内容";
            }
            drawCentered(message, detailX, detailY + detailH / 2 - 4, detailW);
            return;
        }

        FontRenderer font = host.font();
        font.drawString(font.trimStringToWidth(detail.title, detailW - 8), detailX + 4, detailY + 4,
                PageTheme.TITLE, true);
        String subtitle = detail.sender + "  §8" + detail.date + "  " + detail.statusText();
        font.drawString(font.trimStringToWidth(subtitle, detailW - 8), detailX + 4, detailY + 16,
                PageTheme.TEXT_DIM, false);
        host.fill(detailX + 4, detailY + 26, detailW - 8, 1, PageTheme.SEPARATOR);

        drawBody(detail, mouseX, mouseY);
        drawRewards(detail, mouseX, mouseY);
        drawClaimButton(detail, mouseX, mouseY);
    }

    private void drawBody(MailDetail detail, int mouseX, int mouseY) {
        FontRenderer font = host.font();
        List<String> lines = detail.body;
        int total = 0;
        for (String line : lines) {
            total += font.listFormattedStringToWidth(line, bodyW).size() * (font.FONT_HEIGHT + 2);
        }
        bodyMaxScroll = Math.max(0, total - bodyH + 4);
        bodyScroll = clamp(0, bodyMaxScroll, bodyScroll);

        host.beginClip(bodyX, bodyY, bodyW, bodyH);
        int y = bodyY + 2 - bodyScroll;
        for (String line : lines) {
            for (String piece : font.listFormattedStringToWidth(line, bodyW)) {
                if (y + font.FONT_HEIGHT >= bodyY && y <= bodyY + bodyH) {
                    font.drawString(piece, bodyX, y, PageTheme.TEXT, false);
                }
                y += font.FONT_HEIGHT + 2;
            }
        }
        host.endClip();

        if (bodyMaxScroll > 0) {
            host.drawScrollbar(detailX + detailW - 5, bodyY, 4, bodyH, bodyScroll, bodyMaxScroll,
                    false, mouseX, mouseY);
        }
    }

    private void drawRewards(MailDetail detail, int mouseX, int mouseY) {
        if (rewardsHeight <= 0) {
            return;
        }
        FontRenderer font = host.font();
        int startY = rewardsY;
        int clipBottom = Math.max(startY, claimY - 4);
        host.beginClip(detailX, startY, detailW, clipBottom - startY);
        for (int i = 0; i < detail.rewards.size(); i++) {
            ItemStack stack = detail.rewards.get(i);
            int column = i % REWARD_COLS;
            int row = i / REWARD_COLS;
            int slotX = detailX + 4 + column * (SLOT + SLOT_GAP);
            int slotY = startY + row * (SLOT + SLOT_GAP);
            boolean visible = slotY + SLOT <= clipBottom;
            boolean hovered = visible && inside(mouseX, mouseY, slotX, slotY, SLOT, SLOT);
            host.fill(slotX, slotY, SLOT, SLOT, hovered ? PageTheme.ITEM_HOVER : PageTheme.ITEM_BG);
            host.fill(slotX, slotY, SLOT, 1, PageTheme.ITEM_DIVIDER);
            host.drawItem(stack, slotX + 1, slotY + 1);
            if (stack.getCount() > 1) {
                host.drawItemCount(stack, slotX + 1, slotY + 1, null);
            }
            if (hovered) {
                hoveredReward = stack;
            }
        }

        int rows = (detail.rewards.size() + REWARD_COLS - 1) / REWARD_COLS;
        int moneyY = startY + rows * (SLOT + SLOT_GAP) + 2;
        if (detail.money > 0L && moneyY + 9 <= clipBottom) {
            font.drawString("§7货币 §e+" + detail.money + " §7" + currency(), detailX + 4, moneyY,
                    PageTheme.TEXT_DIM, false);
        }
        host.endClip();
    }

    private void drawClaimButton(MailDetail detail, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, claimX, claimY, CLAIM_W, CLAIM_H);
        boolean canClaim = detail.claimable() && !claimPending;
        int background;
        if (detail.claimed) {
            background = 0xFF2A3A33;
        } else if (detail.expired) {
            background = 0xFF4A2A2A;
        } else if (!detail.started) {
            background = 0xFF3A3520;
        } else {
            // 领取中/可领取都用绿色，避免看起来像被禁用
            background = hovered && canClaim ? PageTheme.BTN_GOOD_HOVER : PageTheme.BTN_GOOD;
        }
        host.fill(claimX, claimY, CLAIM_W, CLAIM_H, background);

        String label;
        if (detail.claimed) {
            label = "已领取";
        } else if (detail.expired) {
            label = "已过期";
        } else if (!detail.started) {
            label = "未开始";
        } else if (claimPending) {
            label = "领取中…";
        } else {
            label = "领取奖励";
        }
        FontRenderer font = host.font();
        font.drawString(label, claimX + (CLAIM_W - font.getStringWidth(label)) / 2, claimY + 6,
                canClaim || claimPending ? 0xFFFFFFFF : PageTheme.TEXT_DIM, false);

        String error = MailClientStore.error();
        if (!error.isEmpty() && detail.id.equals(MailClientStore.errorMailId())) {
            font.drawString(font.trimStringToWidth(error, detailW - CLAIM_W - 16),
                    detailX + 4, claimY + 6, 0xFFFF7777, false);
        }
    }

    private void drawCentered(String text, int x, int y, int width) {
        FontRenderer font = host.font();
        font.drawString(text, x + (width - font.getStringWidth(text)) / 2, y, PageTheme.TEXT_FAINT, false);
    }

    private static String currency() {
        try {
            String name = PageConfigs.shop().currencyName;
            return name == null || name.trim().isEmpty() ? "货币" : name.trim();
        } catch (Throwable ignored) {
            return "货币";
        }
    }

    // ================================================================ 输入

    public boolean click(int mouseX, int mouseY, int button) {
        if (button != 0 || !MailClientStore.isEnabled()) {
            return false;
        }
        MailDetail detail = MailClientStore.detail();
        if (detail != null && selectedId.equals(detail.id) && detail.claimable()
                && inside(mouseX, mouseY, claimX, claimY, CLAIM_W, CLAIM_H)) {
            claim();
            return true;
        }
        if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int index = (mouseY - listY + listScroll) / ROW_H;
            List<MailSummary> summaries = MailClientStore.summaries();
            if (index >= 0 && index < summaries.size()) {
                select(summaries.get(index).id);
                return true;
            }
        }
        return false;
    }

    public boolean wheel(int mouseX, int mouseY, int wheel) {
        if (!MailClientStore.isEnabled()) {
            return false;
        }
        if (inside(mouseX, mouseY, listX, listY, listW, listH) && listMaxScroll > 0) {
            listScroll = clamp(0, listMaxScroll, listScroll - Integer.signum(wheel) * 18);
            return true;
        }
        if (inside(mouseX, mouseY, bodyX, bodyY, bodyW, bodyH) && bodyMaxScroll > 0) {
            bodyScroll = clamp(0, bodyMaxScroll, bodyScroll - Integer.signum(wheel) * 18);
            return true;
        }
        return false;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }
}
