package meowmel.quantumhue.page;

import meowmel.quantumhue.config.PageBootstrap;
import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.notice.CommunityStore;
import meowmel.quantumhue.notice.UpdateFeed;
import meowmel.quantumhue.notice.UpdateFeedStore;
import meowmel.quantumhue.notice.VersionState;
import meowmel.quantumhue.notice.VersionUtil;
import meowmel.quantumhue.notice.client.AnnouncementStore;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 更新与公告页。
 *
 * <p>页签固定为四个：更新日志、公告、社群服务、邮件（邮件受内置的邮件总开关控制）；
 * 没有任何可配置页签，远程地址只决定拉不拉数据。
 *
 * <p>任何网络请求都不在页面打开时发生：内容来自启动预取缓存，手动刷新由命令触发。
 */
@SideOnly(Side.CLIENT)
public class GuiAnnouncement extends PageScreen {

    /** 内容列左右内边距。 */
    private static final int CONTENT_PAD = 8;

    private final List<TabView> tabs = new ArrayList<>();
    private final AnnouncementPanel announcementPanel = new AnnouncementPanel(this);
    private final MailPanel mailPanel = new MailPanel(this);
    private int currentIndex;
    private String currentTabId = "update";
    private List<String> contentLines = new ArrayList<>();

    private int scroll;
    private int maxScroll;
    private boolean draggingScroll;
    private int dragStartY;
    private int dragStartScroll;

    /** 上次看到的启动预取版本；预取完成后自动刷新当前页。 */
    private int lastBootstrapRevision = -1;

    // ================================================================ 生命周期

    @Override
    protected void initPage() {
        // 页面只读内存数据；网络预取在加载阶段由 PageBootstrap 完成
        lastBootstrapRevision = PageBootstrap.revision();
        rebuildTabs();
        loadCurrent();
    }

    private void rebuildTabs() {
        tabs.clear();
        // 页签固定：更新日志 / 公告 / 社群服务 / 邮件
        tabs.add(new TabView("update", "更新日志", "update"));
        tabs.add(new TabView("announcements", "公告", "announcements"));
        tabs.add(new TabView("community", "社群服务", "community"));
        // 邮件正文与奖励只来自服务端远程数据，只有内置总开关打开时才显示
        if (PageConfigs.mail().enabled) {
            tabs.add(new TabView("mail", "邮件", "mail"));
        }
        currentIndex = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id.equals(currentTabId)) {
                currentIndex = i;
                break;
            }
        }
        currentTabId = tabs.get(currentIndex).id;
    }

    private boolean isAnnouncementsTab() {
        return currentIndex < tabs.size() && "announcements".equals(tabs.get(currentIndex).type);
    }

    private boolean isMailTab() {
        return currentIndex < tabs.size() && "mail".equals(tabs.get(currentIndex).type);
    }

    private boolean isUpdateTab() {
        return currentIndex < tabs.size() && "update".equals(tabs.get(currentIndex).type);
    }

    private boolean isCommunityTab() {
        return currentIndex < tabs.size() && "community".equals(tabs.get(currentIndex).type);
    }

    @Override
    protected void onPageClosed() {
        draggingScroll = false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        int revision = PageBootstrap.revision();
        if (revision == lastBootstrapRevision) {
            return;
        }
        lastBootstrapRevision = revision;
        // 启动预取或手动刷新完成：直接刷新当前页（数据已在内存里）
        if (isUpdateTab() || isCommunityTab()) {
            loadCurrent();
        } else if (isAnnouncementsTab()) {
            announcementPanel.open();
        }
    }

    // ================================================================ 内容加载

    private void loadCurrent() {
        if (currentIndex >= tabs.size()) {
            return;
        }
        TabView tab = tabs.get(currentIndex);
        if ("announcements".equals(tab.type)) {
            contentLines = Collections.emptyList();
            announcementPanel.open();
            return;
        }
        if ("mail".equals(tab.type)) {
            contentLines = Collections.emptyList();
            mailPanel.open();
            return;
        }
        if ("community".equals(tab.type)) {
            contentLines = new ArrayList<>(CommunityStore.lines());
            if (contentLines.isEmpty()) {
                contentLines.add(CommunityStore.isSynced()
                        ? "§7暂无社群服务内容。"
                        : "§7尚未同步社群服务。请执行 §b/quantumhue refresh§7，或等待下次启动。");
            }
            recalculateScroll();
            return;
        }
        if ("update".equals(tab.type)) {
            loadUpdateTab();
        }
    }

    /** 更新日志来自启动预取的内存数据，不在页面里联网。 */
    private void loadUpdateTab() {
        UpdateFeed feed = UpdateFeedStore.feed();
        String current = VersionState.currentVersion();
        contentLines = new ArrayList<>();

        String latest = feed.latest == null ? "" : feed.latest.trim();
        if (latest.isEmpty() && feed.logs != null && !feed.logs.isEmpty()) {
            latest = feed.logs.get(0).version;
        }
        if (latest.isEmpty()) {
            contentLines.add("§7尚未同步更新日志。请执行 §b/quantumhue refresh§7，或等待下次启动。");
            recalculateScroll();
            return;
        }

        if (current.isEmpty()) {
            contentLines.add(String.format("§e[更新检查]§r 远程最新 §a%1$s", latest));
            contentLines.add("§7当前版本未知：装上 §bGTQTCore§7，或让整合包通过 §bQuantumHuePageApi.registerPackVersion§7 填写整合包版本号。");
        } else {
            contentLines.add(String.format("§e[更新检查]§r 远程最新 §a%1$s§r | 当前 §e%2$s",
                    latest, current));
            if (VersionUtil.compare(latest, current) <= 0) {
                contentLines.add(String.format("§a当前已是最新版 §a%1$s", current));
            }
        }
        contentLines.add("§8----------------------");

        boolean any = false;
        if (feed.logs != null) {
            for (UpdateFeed.VersionLog log : feed.logs) {
                if (log == null || log.version == null || log.version.isEmpty()) {
                    continue;
                }
                if (!VersionUtil.atLeast(log.version, current)) {
                    continue;
                }
                any = true;
                boolean isCurrent = VersionUtil.compare(log.version, current) == 0;
                StringBuilder header = new StringBuilder("§6§l=== ").append(log.version).append(" ===");
                if (log.date != null && !log.date.trim().isEmpty()) {
                    header.append(" §8[").append(log.date.trim()).append(']');
                }
                if (isCurrent) {
                    header.append(" §7(当前版本)");
                }
                contentLines.add(header.toString());
                if (log.lines == null || log.lines.isEmpty()) {
                    contentLines.add("§7[该版本没有更新日志]");
                } else {
                    contentLines.addAll(log.lines);
                }
                contentLines.add("§8----------------------");
            }
        }
        if (!any) {
            if (current.isEmpty()) {
                contentLines.add("§7远程版本目录里没有可用的更新日志。");
            } else {
                contentLines.add(VersionUtil.compare(latest, current) > 0
                        ? "§c远程有更新版本，但更新日志拉取失败。请稍后执行 §b/quantumhue refresh§c。"
                        : "§7远程没有当前或更新版本的更新日志。");
            }
        }
        recalculateScroll();
    }

    /** 只接受仍然属于当前页签的结果，并切回客户端线程。 */
    private void recalculateScroll() {
        maxScroll = Math.max(0, textBlockHeight(contentLines, contentWrapWidth()) - contentHeight + CONTENT_PAD * 2);
        scroll = clamp(0, maxScroll, scroll);
    }

    private int contentWrapWidth() {
        return Math.max(20, contentWidth - CONTENT_PAD * 2);
    }

    // ================================================================ 绘制

    @Override
    protected String pageTitle() {
        return "更新与公告";
    }

    @Override
    protected String pageStatus() {
        String current = VersionState.currentVersion();
        return current.isEmpty() ? "§7版本 §8未配置" : "§7版本 §a" + current;
    }

    @Override
    protected void drawPage(int mouseX, int mouseY) {
        drawTabs(mouseX, mouseY);
        if (isAnnouncementsTab()) {
            announcementPanel.draw(mouseX, mouseY);
            return;
        }
        if (isMailTab()) {
            mailPanel.draw(mouseX, mouseY);
            return;
        }
        drawContent();
        if (maxScroll > 0) {
            drawScrollbar(contentX + contentWidth + 2, contentY, PageTheme.SCROLL_W, contentHeight,
                    scroll, maxScroll, draggingScroll, mouseX, mouseY);
        }
    }

    private void drawTabs(int mouseX, int mouseY) {
        int x = tabX;
        int y = contentY;
        int w = tabWidth;
        for (int i = 0; i < tabs.size(); i++) {
            TabView tab = tabs.get(i);
            boolean active = i == currentIndex;
            boolean hovered = in(mouseX, mouseY, x, y, w, PageTheme.TAB_H);
            String label = fontRenderer.trimStringToWidth(tab.label, w - 16);
            drawSideTab(x, y, w, PageTheme.TAB_H, label, active, hovered, PageTheme.accent(i));
            y += PageTheme.TAB_H + PageTheme.TAB_GAP;
        }
    }

    private void drawContent() {
        beginClip(contentX, contentY, contentWidth, contentHeight);
        drawWrapped(contentLines, contentX + CONTENT_PAD, contentY + CONTENT_PAD,
                contentWrapWidth(), scroll, contentY, contentY + contentHeight);
        endClip();
        drawFade(contentX, contentY, contentWidth, 4, true);
        drawFade(contentX, contentY + contentHeight - 4, contentWidth, 4, false);
    }

    // ================================================================ 输入

    @Override
    protected boolean mousePageClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = tabX;
        int y = contentY;
        for (int i = 0; i < tabs.size(); i++) {
            if (in(mouseX, mouseY, x, y, tabWidth, PageTheme.TAB_H)) {
                select(i);
                return true;
            }
            y += PageTheme.TAB_H + PageTheme.TAB_GAP;
        }
        if (isAnnouncementsTab()) {
            return announcementPanel.click(mouseX, mouseY, button);
        }
        if (isMailTab()) {
            return mailPanel.click(mouseX, mouseY, button);
        }
        if (maxScroll > 0) {
            int barX = contentX + contentWidth + 2;
            int thumbHeight = thumbHeight(contentHeight, maxScroll);
            int thumbY = contentY + scroll * (contentHeight - thumbHeight) / maxScroll;
            if (in(mouseX, mouseY, barX - 2, thumbY, PageTheme.SCROLL_W + 4, thumbHeight)) {
                draggingScroll = true;
                dragStartY = mouseY;
                dragStartScroll = scroll;
                return true;
            }
            if (in(mouseX, mouseY, barX - 2, contentY, PageTheme.SCROLL_W + 4, contentHeight)) {
                scroll = clamp(0, maxScroll, scroll + (mouseY < thumbY ? -contentHeight : contentHeight));
                return true;
            }
        }
        return false;
    }

    private void select(int index) {
        if (index < 0 || index >= tabs.size() || index == currentIndex) {
            return;
        }
        currentIndex = index;
        currentTabId = tabs.get(index).id;
        scroll = 0;
        loadCurrent();
    }

    @Override
    protected boolean mousePageReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (!draggingScroll || maxScroll <= 0) {
            return;
        }
        int thumbHeight = thumbHeight(contentHeight, maxScroll);
        int delta = mouseY - dragStartY;
        scroll = clamp(0, maxScroll, dragStartScroll + delta * maxScroll / Math.max(1, contentHeight - thumbHeight));
    }

    @Override
    protected boolean mousePageScrolled(int mouseX, int mouseY, int wheel) {
        if (isAnnouncementsTab()) {
            return announcementPanel.wheel(mouseX, mouseY, wheel);
        }
        if (isMailTab()) {
            return mailPanel.wheel(mouseX, mouseY, wheel);
        }
        if (maxScroll <= 0 || !in(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            return false;
        }
        scroll = clamp(0, maxScroll, scroll - Integer.signum(wheel) * 18);
        return true;
    }

    @Override
    protected boolean keyPageTyped(char typedChar, int keyCode) {
        if (isAnnouncementsTab() || isMailTab()) {
            return false;
        }
        if (maxScroll <= 0) {
            return false;
        }
        switch (keyCode) {
            case Keyboard.KEY_UP:
                scroll = clamp(0, maxScroll, scroll - 18);
                return true;
            case Keyboard.KEY_DOWN:
                scroll = clamp(0, maxScroll, scroll + 18);
                return true;
            case Keyboard.KEY_PRIOR:
                scroll = clamp(0, maxScroll, scroll - contentHeight);
                return true;
            case Keyboard.KEY_NEXT:
                scroll = clamp(0, maxScroll, scroll + contentHeight);
                return true;
            case Keyboard.KEY_HOME:
                scroll = 0;
                return true;
            case Keyboard.KEY_END:
                scroll = maxScroll;
                return true;
            default:
                return false;
        }
    }

    /** 页签的运行时视图：四个页签都是固定内容，只需 id / 标签 / 类型。 */
    private static final class TabView {
        final String id;
        final String label;
        final String type;

        TabView(String id, String label, String type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }
    }
}
