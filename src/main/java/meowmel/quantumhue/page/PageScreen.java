package meowmel.quantumhue.page;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 公告页与商店页共用的整屏外壳：外发光、渐变底、四角括号、标题栏、关闭键、裁剪、滚动条。
 *
 * <p>两个页面以前各画一套相近的“窗口”，位置和配色略有出入。统一成基类后，子类只需要提供标题、
 * 右侧状态文字、页签列和内容区，装饰语言自然一致。
 *
 * <p>裁剪倍率取 {@link ScaledResolution#getScaleFactor()} 而不是 {@code displayHeight / height}：
 * 后者在显示高度不是缩放倍数整数倍时会少 1，全屏下内容顶部会被裁掉一条。
 */
@SideOnly(Side.CLIENT)
public abstract class PageScreen extends GuiScreen {

    protected int winX;
    protected int winY;
    protected int winWidth;
    protected int winHeight;

    protected int contentX;
    protected int contentY;
    protected int contentWidth;
    protected int contentHeight;

    /** 右侧页签列的左边界。 */
    protected int tabX;
    /** 页签列宽度。 */
    protected int tabWidth;

    protected int closeX;
    protected int closeY;
    protected boolean closeHover;
    protected boolean closePressed;

    /** 关闭键左侧的配色设置键。 */
    protected int settingsX;
    protected int settingsY;
    protected boolean settingsHover;
    protected boolean settingsPressed;

    /** 设置键左侧的音乐开关；只在需要背景音乐的页面（商店）显示。 */
    protected int musicX;
    protected int musicY;
    protected boolean musicHover;
    protected boolean musicPressed;

    /** 设置键打开的配色浮层；两个页面共用的同一块面板。 */
    private final PageThemePanel themePanel = new PageThemePanel();

    // ================================================================ 生命周期

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        computeLayout();
        closeX = winX + winWidth - PageTheme.PAD - PageTheme.CLOSE_SIZE;
        closeY = winY + (PageTheme.HEADER_H - PageTheme.CLOSE_SIZE) / 2;
        settingsX = closeX - PageTheme.CLOSE_SIZE - 4;
        settingsY = closeY;
        musicX = settingsX - PageTheme.CLOSE_SIZE - 4;
        musicY = settingsY;
        closePressed = false;
        settingsPressed = false;
        musicPressed = false;
        // 窗口尺寸变了，浮层位置跟着失效；重新进 GUI 时直接收起
        themePanel.close();
        initPage();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    /** 由可用空间推导窗口与内容区；子类可以调整内容区宽度。 */
    protected void computeLayout() {
        winX = PageTheme.MARGIN;
        winY = PageTheme.MARGIN;
        winWidth = Math.max(240, width - PageTheme.MARGIN * 2);
        winHeight = Math.max(140, height - PageTheme.MARGIN * 2);

        tabX = winX + winWidth - PageTheme.PAD - PageTheme.SIDE_COL_W;
        tabWidth = PageTheme.SIDE_COL_W;

        contentX = winX + PageTheme.PAD;
        contentY = winY + PageTheme.HEADER_H + PageTheme.PAD;
        contentWidth = Math.max(80, tabX - PageTheme.PAD - contentX - PageTheme.SCROLL_W);
        contentHeight = Math.max(40, winHeight - PageTheme.HEADER_H - PageTheme.PAD * 2);
    }

    protected abstract void initPage();

    protected abstract String pageTitle();

    /** 标题栏右侧的状态文字，返回 {@code null} 表示不画。 */
    protected String pageStatus() {
        return null;
    }

    /** 标题栏是否显示音乐开关；默认不显示，只有带背景音乐的页面（商店）打开。 */
    protected boolean musicToggleVisible() {
        return false;
    }

    /** 音乐开关的当前状态：{@code true} 表示正在播放（画暂停图标），否则画播放图标。 */
    protected boolean musicPlaying() {
        return false;
    }

    /** 切换背景音乐，返回切换后的状态。 */
    protected boolean toggleMusic() {
        return false;
    }

    /** 绘制标题栏以下的一切（页签列、内容区、输入框）。 */
    protected abstract void drawPage(int mouseX, int mouseY);

    /** 处理页面内的点击；返回是否已消费。 */
    protected abstract boolean mousePageClicked(int mouseX, int mouseY, int button);

    /** 处理页面内的释放；返回是否已消费。 */
    protected boolean mousePageReleased(int mouseX, int mouseY, int state) {
        return false;
    }

    /** 处理页面内的滚轮；返回是否已消费。 */
    protected boolean mousePageScrolled(int mouseX, int mouseY, int wheel) {
        return false;
    }

    /** 处理页面内的按键；返回是否已消费。 */
    protected boolean keyPageTyped(char typedChar, int keyCode) {
        return false;
    }

    /** 页面关闭前的一次机会，用于取消拖拽等状态。 */
    protected void onPageClosed() {}

    // ================================================================ 绘制

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        resetDrawState();
        drawRect(0, 0, width, height, PageTheme.DIM);
        drawChrome();
        drawHeader();
        drawPage(mouseX, mouseY);
        drawMusicButton(mouseX, mouseY);
        drawSettingsButton(mouseX, mouseY);
        drawCloseButton(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (musicToggleVisible() && musicHover) {
            tooltip(java.util.Collections.singletonList(musicPlaying() ? "关闭音乐" : "开启音乐"),
                    mouseX, mouseY);
        }
        if (themePanel.isOpen()) {
            // 浮层最后画：它自带一层压暗，盖住标题栏和两个键。画之前必须把深度测试关掉，
            // 否则物品写入的深度会把面板矩形顶掉，看起来就是物品浮在面板上面
            resetDrawState();
            themePanel.draw(this, mouseX, mouseY);
        }
    }

    /**
     * 把 GL 状态压回页面假定的 2D 状态。
     *
     * <p>物品渲染（含数量覆层）会打开光照和深度测试，原版是假定调用方随后自己收拾的；
     * 一旦漏掉，之后画的矩形都会在物品像素上被深度测试剔除（物品看起来浮在最上层），
     * 或者被环境光压成灰色。
     */
    protected void resetDrawState() {
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void drawChrome() {
        drawRect(winX - 3, winY - 3, winX + winWidth + 3, winY + winHeight + 3, PageTheme.GLOW);
        drawRect(winX - 1, winY - 1, winX + winWidth + 1, winY + winHeight + 1, PageTheme.BORDER);
        drawGradientRect(winX, winY, winX + winWidth, winY + winHeight, PageTheme.BG_TOP, PageTheme.BG_BOT);
        drawRect(winX + 1, winY, winX + winWidth - 1, winY + 1, PageTheme.TOP_HIGHLIGHT);
        drawRect(winX + 1, winY + PageTheme.HEADER_H - 1, winX + winWidth - 1,
                winY + PageTheme.HEADER_H, PageTheme.HEADER_LINE);
        drawRect(winX + 1, winY + winHeight - 1, winX + winWidth - 1,
                winY + winHeight, PageTheme.SEPARATOR);
        drawCornerBrackets(winX, winY, winWidth, winHeight, 10, PageTheme.CORNER);
        drawRect(contentX, contentY, contentX + contentWidth, contentY + contentHeight,
                PageTheme.CONTENT_BG);
    }

    protected void drawHeader() {
        int textY = winY + (PageTheme.HEADER_H - fontRenderer.FONT_HEIGHT) / 2 - 1;
        drawRect(winX + PageTheme.PAD, textY, winX + PageTheme.PAD + 3,
                textY + fontRenderer.FONT_HEIGHT, PageTheme.ACCENT);
        String brand = "§6§l[GTQT] §r§7格雷量子跃迁 §8| §f" + pageTitle();
        fontRenderer.drawString(brand, winX + PageTheme.PAD + 7, textY, PageTheme.TITLE, true);

        String status = pageStatus();
        if (status != null && !status.isEmpty()) {
            // 设置键（以及商店页它左边的音乐键）也在右边：状态文字再往左让一格，否则会压在键上
            int statusAnchor = musicToggleVisible() ? musicX : settingsX;
            int statusX = statusAnchor - 10 - fontRenderer.getStringWidth(status);
            // 窄窗口下标题和状态会撞在一起：状态宁可让位，也不覆盖标题
            if (statusX > winX + PageTheme.PAD + 7 + fontRenderer.getStringWidth(brand) + 6) {
                fontRenderer.drawString(status, statusX, textY, PageTheme.TEXT, false);
            }
        }
        drawRect(winX + PageTheme.PAD, winY + PageTheme.HEADER_H,
                winX + winWidth - PageTheme.PAD, winY + PageTheme.HEADER_H + 1, PageTheme.SEPARATOR);
    }

    /**
     * 设置键左侧的音乐开关。
     *
     * <p>播放中画暂停双竖条（点一下即停），已停止画向右的播放三角（点一下重新播放）。
     * 图标用矩形拼出来，和设置键一样不依赖字体里有没有音符字形。
     */
    protected void drawMusicButton(int mouseX, int mouseY) {
        if (!musicToggleVisible()) {
            musicHover = false;
            return;
        }
        musicHover = in(mouseX, mouseY, musicX, musicY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        int background = musicPressed ? PageTheme.SETTINGS_PRESS
                : (musicHover ? PageTheme.SETTINGS_HOVER : PageTheme.SETTINGS_BG);
        drawRect(musicX + 1, musicY, musicX + PageTheme.CLOSE_SIZE - 1,
                musicY + PageTheme.CLOSE_SIZE, background);
        drawRect(musicX, musicY + 1, musicX + PageTheme.CLOSE_SIZE,
                musicY + PageTheme.CLOSE_SIZE - 1, background);

        boolean playing = musicPlaying();
        int icon = musicHover || musicPressed ? 0xFFFFFFFF
                : (playing ? PageTheme.TITLE : PageTheme.TEXT_DIM);
        if (playing) {
            drawRect(musicX + 4, musicY + 4, musicX + 6, musicY + 10, icon);
            drawRect(musicX + 8, musicY + 4, musicX + 10, musicY + 10, icon);
        } else {
            for (int i = 0; i < 5; i++) {
                int half = i + 1;
                drawRect(musicX + 5 + i, musicY + 7 - half, musicX + 6 + i, musicY + 7 + half, icon);
            }
        }
    }

    /** 关闭键左侧的配色设置键：三条带旋钮的推子，不依赖字体里有没有齿轮字形。 */
    protected void drawSettingsButton(int mouseX, int mouseY) {
        settingsHover = in(mouseX, mouseY, settingsX, settingsY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        int background = settingsPressed ? PageTheme.SETTINGS_PRESS
                : (settingsHover ? PageTheme.SETTINGS_HOVER : PageTheme.SETTINGS_BG);
        drawRect(settingsX + 1, settingsY, settingsX + PageTheme.CLOSE_SIZE - 1,
                settingsY + PageTheme.CLOSE_SIZE, background);
        drawRect(settingsX, settingsY + 1, settingsX + PageTheme.CLOSE_SIZE,
                settingsY + PageTheme.CLOSE_SIZE - 1, background);

        int lineX = settingsX + 3;
        int lineW = PageTheme.CLOSE_SIZE - 6;
        int icon = settingsHover || settingsPressed ? 0xFFFFFFFF : PageTheme.TITLE;
        int[] knobs = { 4, 1, 5 };
        for (int i = 0; i < 3; i++) {
            int lineY = settingsY + 4 + i * 3;
            drawRect(lineX, lineY, lineX + lineW, lineY + 1, icon);
            drawRect(lineX + knobs[i], lineY - 1, lineX + knobs[i] + 2, lineY + 2, icon);
        }
    }

    protected void drawCloseButton(int mouseX, int mouseY) {
        closeHover = in(mouseX, mouseY, closeX, closeY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        int background = closePressed ? PageTheme.CLOSE_PRESS
                : (closeHover ? PageTheme.CLOSE_HOVER : PageTheme.CLOSE_BG);
        // 用两个矩形拼出圆角感，和页签的直角语言区分开
        drawRect(closeX + 1, closeY, closeX + PageTheme.CLOSE_SIZE - 1,
                closeY + PageTheme.CLOSE_SIZE, background);
        drawRect(closeX, closeY + 1, closeX + PageTheme.CLOSE_SIZE,
                closeY + PageTheme.CLOSE_SIZE - 1, background);

        String cross = "✕";
        int textW = fontRenderer.getStringWidth(cross);
        int textH = fontRenderer.FONT_HEIGHT;
        fontRenderer.drawString(cross,
                closeX + (PageTheme.CLOSE_SIZE - textW) / 2f,
                closeY + (PageTheme.CLOSE_SIZE - textH) / 2f,
                closeHover ? 0xFFFFFFFF : PageTheme.TITLE, false);
    }

    /** 四角 L 形角标：两个页面统一的装饰语言。 */
    protected void drawCornerBrackets(int x, int y, int w, int h, int length, int color) {
        int l = Math.min(length, Math.min(w, h) / 2);
        if (l <= 0) {
            return;
        }
        drawRect(x, y, x + l, y + 1, color);
        drawRect(x, y, x + 1, y + l, color);
        drawRect(x + w - l, y, x + w, y + 1, color);
        drawRect(x + w - 1, y, x + w, y + l, color);
        drawRect(x, y + h - 1, x + l, y + h, color);
        drawRect(x, y + h - l, x + 1, y + h, color);
        drawRect(x + w - l, y + h - 1, x + w, y + h, color);
        drawRect(x + w - 1, y + h - l, x + w, y + h, color);
    }

    /** 右侧列的一枚页签；激活时左侧有 2px 强调条。 */
    protected void drawSideTab(int x, int y, int w, int h, String label, boolean active,
                               boolean hovered, int accent) {
        int background = active ? PageTheme.TAB_ACTIVE
                : (hovered ? PageTheme.TAB_HOVER : PageTheme.TAB_BG);
        drawRect(x, y, x + w, y + h, background);
        if (active) {
            drawRect(x, y, x + 2, y + h, accent);
            drawRect(x, y, x + w, y + 1, PageTheme.HEADER_LINE);
        } else if (hovered) {
            drawRect(x, y, x + w, y + 1, PageTheme.BORDER);
        }
        int textColor = active ? 0xFFFFFFFF : (hovered ? PageTheme.TEXT : PageTheme.TEXT_DIM);
        fontRenderer.drawString((active ? "§l" : "") + label, x + 9,
                y + (h - fontRenderer.FONT_HEIGHT) / 2f, textColor, false);
    }

    // ================================================================ 滚动与裁剪

    private final Deque<int[]> clipStack = new ArrayDeque<>();

    protected void beginClip(int x, int y, int w, int h) {
        // scissor 不是栈式的：内层 endClip 如果直接关闭，会把外层列表的裁剪一起取消。
        // 这里保存每一层并求交，系统商店“分类列 + 商品列”这种嵌套裁剪才不会漏出来。
        int cx = x;
        int cy = y;
        int cw = Math.max(0, w);
        int ch = Math.max(0, h);
        int[] parent = clipStack.peek();
        if (parent != null) {
            int x1 = Math.max(cx, parent[0]);
            int y1 = Math.max(cy, parent[1]);
            int x2 = Math.min(cx + cw, parent[0] + parent[2]);
            int y2 = Math.min(cy + ch, parent[1] + parent[3]);
            cx = x1;
            cy = y1;
            cw = Math.max(0, x2 - x1);
            ch = Math.max(0, y2 - y1);
        }
        clipStack.push(new int[]{cx, cy, cw, ch});
        applyClip(cx, cy, cw, ch);
        GlStateManager.pushMatrix();
    }

    protected void endClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        clipStack.pop();
        GlStateManager.popMatrix();
        if (clipStack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int[] parent = clipStack.peek();
        applyClip(parent[0], parent[1], parent[2], parent[3]);
    }

    private void applyClip(int x, int y, int w, int h) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, (resolution.getScaledHeight() - y - h) * scale,
                Math.max(0, w) * scale, Math.max(0, h) * scale);
    }

    protected void drawFade(int x, int y, int w, int h, boolean top) {
        for (int i = 0; i < h; i++) {
            float ratio = top ? 1f - (float) i / h : (float) i / h;
            int alpha = clamp(0, 200, (int) (ratio * 200f));
            drawRect(x, y + i, x + w, y + i + 1, (alpha << 24) | (PageTheme.BG_BOT & 0xFFFFFF));
        }
    }

    /** 画滚动条并返回滑块矩形（供命中判定复用）。 */
    protected int[] drawScrollbar(int x, int y, int w, int h, int scroll, int maxScroll,
                                  boolean dragging, int mouseX, int mouseY) {
        drawRect(x, y, x + PageTheme.SCROLL_W, y + h, PageTheme.SCROLL_TRACK);
        if (maxScroll <= 0) {
            return new int[]{x, y, PageTheme.SCROLL_W, 0};
        }
        int thumbHeight = thumbHeight(h, maxScroll);
        int thumbY = y + scroll * (h - thumbHeight) / maxScroll;
        boolean hovered = in(mouseX, mouseY, x - 2, thumbY, PageTheme.SCROLL_W + 4, thumbHeight);
        int color = dragging ? PageTheme.SCROLL_DRAG
                : (hovered ? PageTheme.SCROLL_HOVER : PageTheme.SCROLL_THUMB);
        drawRect(x, thumbY, x + PageTheme.SCROLL_W, thumbY + thumbHeight, color);
        drawRect(x, thumbY, x + 1, thumbY + thumbHeight, 0x80FFFFFF);
        return new int[]{x - 2, thumbY, PageTheme.SCROLL_W + 4, thumbHeight};
    }

    protected int thumbHeight(int trackHeight, int maxScroll) {
        return Math.max(15, trackHeight * trackHeight / (maxScroll + trackHeight));
    }

    // ================================================================ 文本工具

    protected void text(String value, float x, float y, int color) {
        fontRenderer.drawString(value, x, y, color, false);
    }

    /** 整段文本按可用宽度换行后的总高（每行间距 2px）。 */
    protected int textBlockHeight(List<String> lines, int wrapWidth) {
        int total = 0;
        for (String line : lines) {
            total += fontRenderer.listFormattedStringToWidth(line, wrapWidth).size()
                    * (fontRenderer.FONT_HEIGHT + 2);
        }
        return total;
    }

    /** 从 {@code topY} 起逐行画换行文本，返回新的 y。 */
    protected int drawWrapped(List<String> lines, int x, int topY, int wrapWidth, int scroll,
                              int clipTop, int clipBottom) {
        int y = topY - scroll;
        int lineHeight = fontRenderer.FONT_HEIGHT + 2;
        for (String line : lines) {
            List<String> wrapped = fontRenderer.listFormattedStringToWidth(line, wrapWidth);
            for (String piece : wrapped) {
                if (y + fontRenderer.FONT_HEIGHT >= clipTop && y <= clipBottom) {
                    fontRenderer.drawString(piece, x, y, PageTheme.TEXT, false);
                }
                y += lineHeight;
            }
        }
        return y;
    }

    // ================================================================ 输入

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        // 浮层是模态的：开着的时候外面的键、列表、搜索框都不该响应
        if (themePanel.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        closeHover = in(mouseX, mouseY, closeX, closeY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        if (button == 0 && closeHover) {
            closePressed = true;
            return;
        }
        settingsHover = in(mouseX, mouseY, settingsX, settingsY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        if (button == 0 && settingsHover) {
            settingsPressed = true;
            return;
        }
        musicHover = musicToggleVisible()
                && in(mouseX, mouseY, musicX, musicY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
        if (button == 0 && musicHover) {
            musicPressed = true;
            return;
        }
        if (mousePageClicked(mouseX, mouseY, button)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (themePanel.isOpen()) {
            closePressed = false;
            settingsPressed = false;
            musicPressed = false;
            return;
        }
        if (state == 0 && closePressed) {
            boolean activate = in(mouseX, mouseY, closeX, closeY, PageTheme.CLOSE_SIZE, PageTheme.CLOSE_SIZE);
            closePressed = false;
            if (activate) {
                closePage();
                return;
            }
        }
        if (state == 0 && settingsPressed) {
            boolean activate = in(mouseX, mouseY, settingsX, settingsY, PageTheme.CLOSE_SIZE,
                    PageTheme.CLOSE_SIZE);
            settingsPressed = false;
            if (activate) {
                openSettings();
                return;
            }
        }
        if (state == 0 && musicPressed) {
            boolean activate = in(mouseX, mouseY, musicX, musicY, PageTheme.CLOSE_SIZE,
                    PageTheme.CLOSE_SIZE);
            musicPressed = false;
            if (activate) {
                toggleMusic();
                return;
            }
        }
        mousePageReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int scaledW = Math.max(1, mc.displayWidth);
        int scaledH = Math.max(1, mc.displayHeight);
        int mouseX = org.lwjgl.input.Mouse.getEventX() * width / scaledW;
        int mouseY = height - org.lwjgl.input.Mouse.getEventY() * height / scaledH - 1;
        if (themePanel.isOpen()) {
            // 滚轮不穿透到列表，否则改配色的时候列表在背后乱滚
            return;
        }
        mousePageScrolled(mouseX, mouseY, wheel);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (themePanel.isOpen()) {
            // 先用 ESC 收浮层；其余按键也一并吞掉，别漏进商店的搜索框
            themePanel.keyPressed(keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closePage();
            return;
        }
        if (keyPageTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** 打开配色设置浮层；收起后页面状态（搜索词、页签、滚动）原样还在。 */
    protected void openSettings() {
        themePanel.open(this);
    }

    protected void closePage() {
        onPageClosed();
        mc.displayGuiScreen(null);
        if (mc.currentScreen == null) {
            mc.setIngameFocus();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ================================================================ 小工具

    protected static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- 给同包的组合面板使用

    /** 供 {@code MailPanel} 等组合面板复用页面绘制能力。 */
    public void fill(int x, int y, int width, int height, int color) {
        drawRect(x, y, x + width, y + height, color);
    }

    public void fillGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
        drawGradientRect(x, y, x + width, y + height, topColor, bottomColor);
    }

    public FontRenderer font() {
        return fontRenderer;
    }

    public void drawItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // 方块物品走的是 3D 模型，必须显式打开 GUI 光照，否则会渲染成灰黑色；
        // 渲染完还要把颜色/光照恢复，避免后面画的矩形、按钮、tooltip 被染色。
        RenderHelper.enableGUIStandardItemLighting();
        try {
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        } finally {
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableColorMaterial();
            // renderItemOverlayIntoGUI 以 enableDepth() 收尾，这里必须关回去：
            // 深度测试开着的话，之后画的浮层/淡化遮罩/矩形在物品像素上会被剔除
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void drawItemCount(ItemStack stack, int x, int y, String text) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // 关键：renderItemOverlayIntoGUI 结束时无条件 enableLighting()（原版假定调用方本来就开着灯）。
        // 若不在这里关掉，后续所有 drawRect 都会被环境光压成 0.4 灰（#666666），
        // 按钮、格子底色、关闭键就全变成灰块，文字也会被压暗。
        RenderHelper.enableGUIStandardItemLighting();
        try {
            itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, text);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableColorMaterial();
            // 同上：数量覆层会把深度测试留在开启状态
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void tooltip(java.util.List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 不使用 GuiScreen#drawHoveringText：物品渲染会改变光照/颜色，原版 tooltip 在这里
        // 偶尔只剩底板、文字发黑。这里直接按主题画一个稳定的深色浮层。
        int pad = 4;
        int lineHeight = fontRenderer.FONT_HEIGHT + 2;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(line == null ? "" : line));
        }
        int maxTextWidth = Math.max(40, width - 24);
        textWidth = Math.min(textWidth, maxTextWidth);
        int boxWidth = textWidth + pad * 2;
        int boxHeight = lines.size() * lineHeight + pad * 2 - 2;

        int x = mouseX + 10;
        int y = mouseY - 10;
        if (x + boxWidth > width - 4) {
            x = mouseX - boxWidth - 10;
        }
        if (y + boxHeight > height - 4) {
            y = height - boxHeight - 4;
        }
        x = Math.max(4, x);
        y = Math.max(4, y);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        fill(x - 1, y - 1, boxWidth + 2, boxHeight + 2, PageTheme.TOOLTIP_BORDER);
        fill(x, y, boxWidth, boxHeight, 0xF0142028);

        int textY = y + pad;
        for (String line : lines) {
            fontRenderer.drawString(fontRenderer.trimStringToWidth(line == null ? "" : line, textWidth),
                    x + pad, textY, 0xFFEAF6FF, false);
            textY += lineHeight;
        }
        // 页面本身是在关灯状态下画 2D 的：这里必须关回去，不能像原版物品 tooltip 那样 enableLighting，
        // 否则下一帧/后续的 drawRect 会被环境光染成灰色。
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
