package meowmel.quantumhue.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import meowmel.quantumhue.wiki.gregtech.MultiblockPreviewRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * 从已渲染的行中查找第一个已解析的多方块预览渲染器。
     * 预览由 WikiRenderer 在渲染时懒加载，因此该值在首帧 drawScreen() 之后才可用。
     */
    private MultiblockPreviewRenderer getActivePreview() {
        for (RenderLine line : lines) {
            if (line.type == LineType.MULTIBLOCK_PREVIEW
                    && line.extraData instanceof MultiblockPreviewRenderer) {
                return (MultiblockPreviewRenderer) line.extraData;
            }
        }
        return null;
    }

    public WikiScreen() {
        this(null);
    }

    public WikiScreen(String initialPageId) {
        this.categories = WikiContent.getCategories();
        if (!categories.isEmpty()) {
            categories.get(0).collapsed = false;
            if (!categories.get(0).pages.isEmpty()) {
                activePage = categories.get(0).pages.get(0);
            }
        }
        if (initialPageId != null) {
            navigateToPage(initialPageId);
        }
    }

    public static void open() {
        Minecraft.getMinecraft().displayGuiScreen(new WikiScreen());
    }

    public static void open(String pageId) {
        Minecraft.getMinecraft().displayGuiScreen(new WikiScreen(pageId));
    }

    /** 导航到指定 pageId 的页面，自动展开对应分类并滚动到该页面 */
    private void navigateToPage(String pageId) {
        for (WikiCategory cat : categories) {
            for (WikiPage page : cat.pages) {
                if (page.id.equals(pageId)) {
                    cat.collapsed = false;
                    activePage = page;
                    scroll = 0;
                    scrollTarget = 0;
                    dirty = true;
                    return;
                }
            }
        }
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
        maxScroll = WikiRenderer.drawPageContent(mc, lines, scroll, cx, cy, cr, width, height, mx, my);
        WikiRenderer.disableScissor();
        WikiRenderer.drawScrollbar(cy, cb, scroll, maxScroll, cr);

        toastTimer = WikiRenderer.drawToast(mc, width, toastMessage, toastTimer);

        // 3D 预览 tooltip（优先级最高：槽位物品 > 3D 方块 > 信息图标）
        MultiblockPreviewRenderer activePreview = getActivePreview();
        boolean previewTooltipShown = false;
        if (activePreview != null && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            // 1) 2D 物品槽位（左侧部件 + 右侧候选方块）
            ItemStack slotStack = activePreview.getSlotStackAt(mx, my);
            if (slotStack != null && !slotStack.isEmpty()) {
                List<String> tooltip = slotStack.getTooltip(mc.player,
                        mc.gameSettings.advancedItemTooltips ?
                                ITooltipFlag.TooltipFlags.ADVANCED :
                                ITooltipFlag.TooltipFlags.NORMAL);
                List<String> slotTips = activePreview.getSlotPredicateTips(mx, my);
                if (slotTips != null && !slotTips.isEmpty()) {
                    tooltip.addAll(slotTips);
                }
                drawHoveringText(tooltip, mx, my);
                previewTooltipShown = true;
            } else {
                // 2) 3D 场景方块悬停
                ItemStack hovered = MultiblockPreviewRenderer.getHoveredItemStack();
                if (hovered != null && !hovered.isEmpty()) {
                    List<String> tooltip = hovered.getTooltip(mc.player,
                            mc.gameSettings.advancedItemTooltips ?
                                    ITooltipFlag.TooltipFlags.ADVANCED :
                                    ITooltipFlag.TooltipFlags.NORMAL);
                    List<String> predTips = activePreview.getPredicateTips();
                    if (predTips != null && !predTips.isEmpty()) {
                        tooltip.addAll(predTips);
                    }
                    drawHoveringText(tooltip, mx, my);
                    previewTooltipShown = true;
                }
            }
            // 3) 信息图标 tooltip
            int[] bounds = activePreview.getPreviewBounds();
            int iconX = bounds[0] + bounds[2] - 25;
            int iconY = bounds[1] + 22;
            if (mx >= iconX && mx <= iconX + 20 && my >= iconY && my <= iconY + 20) {
                drawHoveringText(Arrays.asList(
                        I18n.format("gregtech.multiblock.preview.zoom"),
                        I18n.format("gregtech.multiblock.preview.rotate"),
                        I18n.format("gregtech.multiblock.preview.select")
                ), mx, my);
                previewTooltipShown = true;
            }
        }

        // 页面内嵌物品图标 tooltip（[item:...] / ![item:...]）— 仅在 3D 预览 tooltip 未显示时
        if (!previewTooltipShown && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            for (IconSlot slot : WikiRenderer.getIconSlots()) {
                if (mx >= slot.x && mx < slot.x + slot.w && my >= slot.y && my < slot.y + slot.h) {
                    List<String> tip = slot.stack.getTooltip(mc.player,
                            mc.gameSettings.advancedItemTooltips ?
                                    ITooltipFlag.TooltipFlags.ADVANCED :
                                    ITooltipFlag.TooltipFlags.NORMAL);
                    drawHoveringText(tip, mx, my);
                    break;
                }
            }
        }

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

        // 多方块预览现在通过 Markdown 语法 ![multiblock:...] 嵌入，
        // 由 WikiRenderer 在渲染时懒加载解析，不再使用 page.attachment
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = Mouse.getEventY() * height / mc.displayHeight;
            // 如果鼠标在 3D 预览区域内，优先缩放场景
            MultiblockPreviewRenderer preview = getActivePreview();
            if (preview != null && preview.isMouseOverPreview(mx, my)) {
                preview.handleScroll(dw, mx, my);
            } else if (mx < WikiRenderer.SIDEBAR_W) {
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
        // 如果鼠标在预览区域内（含通道滑条等底部 UI），优先转发给预览渲染器
        MultiblockPreviewRenderer preview = getActivePreview();
        if (preview != null && preview.isMouseOverFullPreview(mx, my)) {
            if (preview.handleClick(mx, my, btn)) return;
        }
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
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_E) mc.displayGuiScreen(null);
        else if (key == Keyboard.KEY_UP) scrollTarget = Math.max(0, scrollTarget - 50);
        else if (key == Keyboard.KEY_DOWN) scrollTarget = Math.min(maxScroll, scrollTarget + 50);
        else if (key == Keyboard.KEY_HOME) scrollTarget = 0;
        else if (key == Keyboard.KEY_END) scrollTarget = maxScroll;
        else if (key == Keyboard.KEY_PRIOR) scrollTarget = Math.max(0, scrollTarget - 200);
        else if (key == Keyboard.KEY_NEXT) scrollTarget = Math.min(maxScroll, scrollTarget + 200);
        else if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_F) searchField.setFocused(true);
    }
}
