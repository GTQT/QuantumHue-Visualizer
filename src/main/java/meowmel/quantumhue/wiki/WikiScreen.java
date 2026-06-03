package meowmel.quantumhue.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static meowmel.quantumhue.wiki.WikiRenderTypes.*;

public class WikiScreen extends GuiScreen {

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
        searchField = new GuiTextField(0, mc.fontRenderer, 6, WikiRenderer.HEADER_H + 4,
                WikiRenderer.SIDEBAR_W - 12, WikiRenderer.SEARCH_H - 4);
        searchField.setMaxStringLength(50);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(WikiRenderer.TXT);
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
        // 平滑滚动
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * 0.3f;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.5f) sidebarScroll = sidebarScrollTarget;

        if (dirty) {
            rebuildLines();
            dirty = false;
        }

        Gui.drawRect(0, 0, width, height, WikiRenderer.BG);
        WikiRenderer.drawSidebar(mc, mx, my, width, height, categories, activePage, searchField, sidebarScroll);
        sidebarMaxScroll = WikiRenderer.computeSidebarMaxScroll(categories, searchField, height, sidebarScroll);
        WikiRenderer.drawPageHeader(mc, width, activePage);

        int cx = WikiRenderer.SIDEBAR_W + 1, cy = WikiRenderer.HEADER_H,
                cr = width - WikiRenderer.SCROLLBAR_W, cb = height;
        WikiRenderer.enableScissor(mc, cx, cy, cr, cb, width, height);
        maxScroll = WikiRenderer.drawPageContent(mc, lines, scroll, cx, cy, cr, width, height);
        WikiRenderer.disableScissor();
        WikiRenderer.drawScrollbar(cy, cb, scroll, maxScroll, cr);

        toastTimer = WikiRenderer.drawToast(mc, width, toastMessage, toastTimer);
        super.drawScreen(mx, my, pt);
    }

    public void showToast(String msg) {
        toastMessage = msg;
        toastTimer = 60;
    }

    private void rebuildLines() {
        lines.clear();
        if (activePage == null) return;
        String content = activePage.markdownContent;
        if (content == null || content.isEmpty()) return;
        FontRenderer fr = mc.fontRenderer;
        int maxW = width - WikiRenderer.SIDEBAR_W - 1 - WikiRenderer.PAD * 2 - WikiRenderer.SCROLLBAR_W;
        lines.addAll(WikiMarkdownParser.parse(content, maxW, fr));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            if (mx < WikiRenderer.SIDEBAR_W) {
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
        if (btn != 0 || mx >= WikiRenderer.SIDEBAR_W) return;
        int listTop = WikiRenderer.HEADER_H + 2 + WikiRenderer.SEARCH_H + 4;
        if (my < listTop) return;
        String query = searchField.getText().toLowerCase(java.util.Locale.ROOT).trim();
        boolean hasSearch = !query.isEmpty();
        int y = listTop - (int) sidebarScroll;
        for (WikiCategory cat : categories) {
            List<WikiPage> visible = hasSearch ? WikiRenderer.filterPages(cat, query) : cat.pages;
            if (hasSearch && visible.isEmpty()) continue;
            if (my >= y && my < y + WikiRenderer.CAT_H) {
                if (!hasSearch) cat.collapsed = !cat.collapsed;
                return;
            }
            y += WikiRenderer.CAT_H;
            boolean open = hasSearch || !cat.collapsed;
            if (!open) continue;
            for (WikiPage page : visible) {
                if (my >= y && my < y + WikiRenderer.ENTRY_H) {
                    activePage = page;
                    scroll = 0; scrollTarget = 0; maxScroll = 0;
                    dirty = true;
                    return;
                }
                y += WikiRenderer.ENTRY_H;
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
                sidebarScrollTarget = 0; sidebarScroll = 0;
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
}
