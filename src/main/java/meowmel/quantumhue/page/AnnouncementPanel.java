package meowmel.quantumhue.page;

import meowmel.quantumhue.notice.Announcement;
import meowmel.quantumhue.notice.client.AnnouncementStore;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 公告页里的“公告”页签：左侧公告列表 + 右侧正文预览。
 *
 * <p>公告数量不再限制为一条：每条公告是远程目录里的一个 JSON，索引列出文件名。
 */
@SideOnly(Side.CLIENT)
public final class AnnouncementPanel {

    private static final int ROW_H = 28;
    private static final long CACHE_RETRY_MS = 5000L;

    private final GuiAnnouncement host;

    private int listScroll;
    private int bodyScroll;
    private int listMaxScroll;
    private int bodyMaxScroll;
    private String selectedId = "";
    private long lastLoadRequestAt;

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

    public AnnouncementPanel(GuiAnnouncement host) {
        this.host = host;
    }

    public void open() {
        lastLoadRequestAt = System.currentTimeMillis();
        AnnouncementStore.ensureLoaded(false);
        ensureSelection();
    }

    private void ensureSelection() {
        List<Announcement> items = AnnouncementStore.items();
        if (!selectedId.isEmpty()) {
            for (Announcement item : items) {
                if (item.id.equals(selectedId)) {
                    return;
                }
            }
        }
        selectedId = items.isEmpty() ? "" : items.get(0).id;
        bodyScroll = 0;
    }

    public void draw(int mouseX, int mouseY) {
        computeLayout();
        if (AnnouncementStore.isLoading()
                && System.currentTimeMillis() - lastLoadRequestAt > CACHE_RETRY_MS) {
            lastLoadRequestAt = System.currentTimeMillis();
            AnnouncementStore.ensureLoaded(false);
        }
        ensureSelection();
        drawList(mouseX, mouseY);
        drawDetail(mouseX, mouseY);
    }

    private void computeLayout() {
        int x = host.contentX;
        int y = host.contentY;
        int w = host.contentWidth;
        int h = host.contentHeight;

        listX = x + 2;
        listY = y + 2;
        listH = Math.max(40, h - 4);
        int listWidth = Math.max(120, Math.min(210, (int) (w * 0.40F)));
        if (w < 260) {
            listWidth = Math.max(80, w / 2);
        }
        listW = Math.min(listWidth, Math.max(80, w - 100));

        detailX = listX + listW + 8;
        detailY = y + 2;
        detailW = Math.max(60, x + w - 2 - detailX);
        detailH = listH;

        bodyX = detailX + 4;
        bodyY = detailY + 30;
        bodyW = Math.max(20, detailW - 10);
        bodyH = Math.max(16, detailH - 34);
    }

    private void drawList(int mouseX, int mouseY) {
        host.fill(listX, listY, listW, listH, PageTheme.CONTENT_BG);
        host.fill(listX + listW, listY, 1, listH, PageTheme.SEPARATOR);

        List<Announcement> items = AnnouncementStore.items();
        listMaxScroll = Math.max(0, items.size() * ROW_H - listH + 4);
        listScroll = clamp(0, listMaxScroll, listScroll);

        host.beginClip(listX, listY, listW, listH);
        int rowY = listY + 2 - listScroll;
        for (Announcement item : items) {
            boolean selected = item.id.equals(selectedId);
            boolean hovered = inside(mouseX, mouseY, listX, rowY, listW, ROW_H);
            if (selected) {
                host.fill(listX, rowY, listW, ROW_H, PageTheme.ITEM_SELECTED);
                host.fill(listX, rowY, 2, ROW_H, PageTheme.ACCENT);
            } else if (hovered) {
                host.fill(listX, rowY, listW, ROW_H, PageTheme.ITEM_HOVER);
            }

            FontRenderer font = host.font();
            String prefix = item.pinned ? "§6★ " : "";
            String title = font.trimStringToWidth(prefix + item.title, listW - 14);
            font.drawString(title, listX + 12, rowY + 5, PageTheme.TITLE, false);
            String subtitle = font.trimStringToWidth(item.date + "  §8" + item.author, listW - 18);
            font.drawString(subtitle, listX + 12, rowY + 16, PageTheme.TEXT_FAINT, false);
            rowY += ROW_H;
        }
        host.endClip();

        if (listMaxScroll > 0) {
            host.drawScrollbar(listX + listW - 4, listY, 4, listH, listScroll, listMaxScroll,
                    false, mouseX, mouseY);
        }

        if (items.isEmpty()) {
            String message;
            if (AnnouncementStore.isLoading()) {
                message = "§7正在同步公告…";
            } else if (!AnnouncementStore.error().isEmpty()) {
                message = "§7" + AnnouncementStore.error();
            } else {
                message = "§7暂无公告";
            }
            drawCentered(message, listX + 4, listY + listH / 2 - 4, listW - 8);
        }
    }

    private void drawDetail(int mouseX, int mouseY) {
        Announcement selected = selected();
        if (selected == null) {
            String message = AnnouncementStore.isLoading() ? "§7正在加载公告…" : "§7选择左侧公告查看内容";
            drawCentered(message, detailX, detailY + detailH / 2 - 4, detailW);
            return;
        }

        FontRenderer font = host.font();
        font.drawString(font.trimStringToWidth(selected.title, detailW - 8), detailX + 4, detailY + 4,
                PageTheme.TITLE, true);
        font.drawString(font.trimStringToWidth(
                selected.date + "  §8" + selected.author, detailW - 8),
                detailX + 4, detailY + 16, PageTheme.TEXT_DIM, false);
        host.fill(detailX + 4, detailY + 26, detailW - 8, 1, PageTheme.SEPARATOR);

        List<String> lines = selected.parsedBody();
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

    private Announcement selected() {
        for (Announcement item : AnnouncementStore.items()) {
            if (item.id.equals(selectedId)) {
                return item;
            }
        }
        return null;
    }

    public boolean click(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int index = (mouseY - listY + listScroll) / ROW_H;
            List<Announcement> items = AnnouncementStore.items();
            if (index >= 0 && index < items.size()) {
                selectedId = items.get(index).id;
                bodyScroll = 0;
                return true;
            }
        }
        return false;
    }

    public boolean wheel(int mouseX, int mouseY, int wheel) {
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

    private void drawCentered(String message, int x, int y, int width) {
        FontRenderer font = host.font();
        font.drawString(message, x + (width - font.getStringWidth(message)) / 2, y,
                PageTheme.TEXT_FAINT, false);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }
}
