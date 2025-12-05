package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class AdvancedTooltipHandler {

    // 原版tooltip最大宽度
    private static final int TOOLTIP_MAX_WIDTH = 225;
    // 图标区域宽度 (包括边距)
    private static final int ICON_AREA_WIDTH = 26;
    // 鼠标偏移量
    private static final int MOUSE_OFFSET_X = 8;
    private static final int MOUSE_OFFSET_Y = 4;
    // 分页功能常量
    private static final int PAGINATION_HINT_COLOR = 0xFFAAAAAA; // 灰色提示文本
    // 当前物品状态
    private static String currentItemId = null;
    private static int currentPage = 0;
    // 按键状态跟踪
    private static KeyState currentKeyState = new KeyState();

    // 按键状态类
    private static class KeyState {
        boolean wasCtrlPressed = false;
        boolean wasCPressed = false;
        boolean wasZPressed = false;
        long lastSwitchTime = 0; // 上次切换时间(毫秒)
        static final long MIN_SWITCH_INTERVAL = 250; // 最小切换间隔(毫秒)
    }

    // ========== 模组名称获取部分 ==========
    @Nullable
    private static String getModName(ItemStack itemStack) {
        if (itemStack.isEmpty()) return null;

        String modId = itemStack.getItem().getCreatorModId(itemStack);
        if (modId == null) return null;

        ModContainer modContainer = Loader.instance().getIndexedModList().get(modId);
        return modContainer != null ? modContainer.getName() : null;
    }

    private static boolean isModNameAlreadyPresent(List<String> tooltip, String modName) {
        if (tooltip.size() <= 1) return false;

        String lastLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(tooltip.size() - 1));
        return modName.equals(lastLine);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String modName = getModName(stack);

        if (modName != null && !isModNameAlreadyPresent(event.getToolTip(), modName)) {
            event.getToolTip().add(TextFormatting.YELLOW + modName);
        }
    }

    // ========== 事件处理器 ==========
    @SubscribeEvent
    public void onRenderTooltipColor(RenderTooltipEvent.Color event) {
        if (!TooltipColorConfig.isEnabled() || event.getStack().isEmpty()) return;

        event.setBackground(TooltipColorConfig.getBackgroundColor());
        applyBorderColor(event, event.getStack());
    }

    private void applyBorderColor(RenderTooltipEvent.Color event, ItemStack stack) {
        if (TooltipColorConfig.enableRarityColors()) {
            int rarityColor = TooltipColorConfig.getRarityColor(stack.getRarity());
            event.setBorderStart(rarityColor);
            event.setBorderEnd(rarityColor);
        } else {
            int borderColor = TooltipColorConfig.getBorderColor();
            event.setBorderStart(borderColor);
            event.setBorderEnd(borderColor);
        }
    }

    @SubscribeEvent
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (event.getStack().isEmpty()) return;

        event.setCanceled(true);
        renderCustomTooltip(event);
    }

    private void renderCustomTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getStack();
        String itemId = getItemUniqueId(stack);

        // 检查物品是否切换
        if (!itemId.equals(currentItemId)) {
            // 物品切换，重置状态
            currentItemId = itemId;
            currentPage = 0;
            currentKeyState = new KeyState();
        }

        TooltipContent rawContent = extractTooltipContent(event.getLines(), stack);
        FontRenderer font = event.getFontRenderer();

        // 应用原版自动换行
        List<String> wrappedLines = wrapTooltipText(rawContent.remainingLines, font);

        // 创建处理后的内容
        TooltipContent content = new TooltipContent(
                rawContent.itemName,
                rawContent.modName,
                wrappedLines
        );

        // 计算分页信息
        calculatePagination(content, event.getScreenHeight());

        TooltipLayout layout = calculateLayout(content, event);
        TooltipColors colors = getTooltipColors(stack);

        GlStateManager.pushMatrix();
        setupGLState();

        drawTooltipBackground(layout, colors, content);
        drawItemIcon(stack, layout.iconX, layout.iconY);
        drawTooltipText(content, layout, event.getFontRenderer());

        restoreGLState();
        GlStateManager.popMatrix();
    }

    /**
     * 生成物品的唯一ID，用于状态管理
     */
    private String getItemUniqueId(ItemStack stack) {
        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(stack.getItem().getRegistryName());
        idBuilder.append("@").append(stack.getItemDamage());

        // 添加NBT哈希（如果存在）
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            idBuilder.append("#").append(nbt.hashCode());
        }

        return idBuilder.toString();
    }

    /**
     * 应用原版自动换行逻辑
     */
    private List<String> wrapTooltipText(List<String> lines, FontRenderer font) {
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            // 保留格式代码的换行处理
            wrappedLines.addAll(font.listFormattedStringToWidth(line, TOOLTIP_MAX_WIDTH));
        }
        return wrappedLines;
    }

    // ========== 分页功能 ==========
    private void calculatePagination(TooltipContent content, int screenHeight) {
        // 计算固定部分高度（物品名称+模组名称+分割线）
        int fixedLineCount = 2; // 物品名称 + 分割线
        if (content.modName != null) fixedLineCount++; // 模组名称
        int fixedHeight = fixedLineCount * 10 + 8; // 每行10像素 + 内边距

        // 计算最大可用高度（屏幕高度的75%）
        int maxHeight = (int) (screenHeight * 0.75);
        int availableHeight = maxHeight - fixedHeight - 15; // 预留空间给分页提示

        if (availableHeight < 10 || content.remainingLines.isEmpty()) {
            // 屏幕太小，无法显示任何内容
            content.currentPageLines = new ArrayList<>(content.remainingLines);
            content.totalPages = 1;
            content.needsPagination = false;
            content.currentPage = 0;
            return;
        }

        // 计算每页最多显示的行数
        int maxLinesPerPage = Math.max(1, availableHeight / 10);
        content.maxLinesPerPage = maxLinesPerPage;

        // 检查是否需要分页
        if (content.remainingLines.size() <= maxLinesPerPage) {
            content.currentPageLines = new ArrayList<>(content.remainingLines);
            content.totalPages = 1;
            content.needsPagination = false;
            content.currentPage = 0;
            return;
        }

        content.needsPagination = true;
        content.totalPages = (int) Math.ceil((double) content.remainingLines.size() / maxLinesPerPage);

        // 使用当前物品的页码
        content.currentPage = Math.min(currentPage, content.totalPages - 1);

        long currentTime = System.currentTimeMillis();

        // 获取当前按键状态
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean cDown = Keyboard.isKeyDown(Keyboard.KEY_C);
        boolean zDown = Keyboard.isKeyDown(Keyboard.KEY_Z);

        // 检测按键按下事件（从释放到按下）
        boolean ctrlPressedThisFrame = ctrlDown && !currentKeyState.wasCtrlPressed;
        boolean cPressedThisFrame = cDown && !currentKeyState.wasCPressed;
        boolean zPressedThisFrame = zDown && !currentKeyState.wasZPressed;

        // 更新按键状态
        currentKeyState.wasCtrlPressed = ctrlDown;
        currentKeyState.wasCPressed = cDown;
        currentKeyState.wasZPressed = zDown;

        // 检查是否可以切换页面（需要间隔时间）
        boolean canSwitch = (currentTime - currentKeyState.lastSwitchTime) > KeyState.MIN_SWITCH_INTERVAL;

        // 处理页面切换
        if (ctrlDown && canSwitch) {
            if (cPressedThisFrame && !zDown) {
                // Ctrl+C: 下一页（仅在按键按下时触发）
                content.currentPage = Math.min(content.currentPage + 1, content.totalPages - 1);
                currentPage = content.currentPage; // 更新全局页码
                currentKeyState.lastSwitchTime = currentTime;
            } else if (zPressedThisFrame && !cDown) {
                // Ctrl+Z: 上一页（仅在按键按下时触发）
                content.currentPage = Math.max(content.currentPage - 1, 0);
                currentPage = content.currentPage; // 更新全局页码
                currentKeyState.lastSwitchTime = currentTime;
            }
        }

        // 获取当前页内容
        int startLine = content.currentPage * maxLinesPerPage;
        int endLine = Math.min(startLine + maxLinesPerPage, content.remainingLines.size());
        content.currentPageLines = content.remainingLines.subList(startLine, endLine);
    }

    // ========== 内容提取 ==========
    private TooltipContent extractTooltipContent(List<String> originalLines, ItemStack stack) {
        String itemName = originalLines.isEmpty() ? "" : originalLines.get(0);
        String detectedModName = getModName(stack);

        List<String> remainingLines = new ArrayList<>();
        boolean modNameFound = false;

        for (int i = 1; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            String unformatted = TextFormatting.getTextWithoutFormattingCodes(line);

            if (line.startsWith(TextFormatting.YELLOW.toString()) ||
                    (detectedModName != null && detectedModName.equals(unformatted))) {
                modNameFound = true;
                continue;
            }
            remainingLines.add(line);
        }

        String modName = modNameFound ? detectedModName : null;
        if (modName == null) modName = detectedModName;

        return new TooltipContent(itemName, modName, remainingLines);
    }

    // ========== 布局计算 ==========
    private TooltipLayout calculateLayout(TooltipContent content, RenderTooltipEvent.Pre event) {
        FontRenderer font = event.getFontRenderer();
        int screenWidth = event.getScreenWidth();
        int screenHeight = event.getScreenHeight();

        // 计算文本宽度 (换行后)
        int leftWidth = 0;
        // 使用当前页的内容计算宽度
        for (String line : content.currentPageLines) {
            int width = font.getStringWidth(line);
            if (width > leftWidth) leftWidth = width;
        }

        // 限制最大宽度
        leftWidth = Math.min(leftWidth, TOOLTIP_MAX_WIDTH);

        int rightWidth = font.getStringWidth(content.itemName);
        if (content.modName != null) {
            int modWidth = font.getStringWidth(content.modName);
            if (modWidth > rightWidth) rightWidth = modWidth;
        }

        // 计算尺寸
        int textPadding = 4;
        int borderPadding = 1;
        int lineHeight = 10;

        // 总宽度 = 文本区域宽度 + 图标区域宽度 + 内边距
        int totalWidth = Math.max(leftWidth, rightWidth) + ICON_AREA_WIDTH + textPadding * 2;

        // 计算总高度 - 根据分页调整
        int lineCount = 1; // 物品名称
        if (content.modName != null) lineCount++; // 模组名称
        lineCount++; // 分割线
        lineCount += content.currentPageLines.size(); // 当前页面内容

        // 如果需要分页，添加换页提示行
        if (content.needsPagination) {
            lineCount++; // 为换页提示预留一行
        }

        int baseHeight = lineCount * lineHeight;
        int iconHeight = 18;
        int firstSectionHeight = (content.modName != null ? 2 : 1) * lineHeight;
        int heightAdjustment = Math.max(0, iconHeight - firstSectionHeight);

        int totalHeight = baseHeight + textPadding * 2 + heightAdjustment;

        // 调整位置 - 添加鼠标偏移
        int initialX = event.getX() + MOUSE_OFFSET_X;
        int initialY = event.getY() + MOUSE_OFFSET_Y;

        int x = adjustPosition(initialX, totalWidth, screenWidth, borderPadding);
        int y = adjustPosition(initialY, totalHeight, screenHeight, borderPadding);

        // 计算分割线位置
        int separatorY = y + textPadding + (content.modName != null ? 2 : 1) * lineHeight;

        // 计算图标位置
        int iconX = x + textPadding;
        int iconY = y + textPadding;

        return new TooltipLayout(x, y, totalWidth, totalHeight, separatorY, iconX, iconY);
    }

    private int adjustPosition(int pos, int size, int screenLimit, int padding) {
        if (pos + size + padding > screenLimit) {
            pos = screenLimit - size - padding;
        }
        return Math.max(padding, pos);
    }

    // ========== 渲染核心 ==========
    private void setupGLState() {
        GlStateManager.disableDepth();
        GlStateManager.translate(0.0F, 0.0F, 500.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
    }

    private void restoreGLState() {
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.translate(0.0F, 0.0F, -500.0F);
    }

    private void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        // 主背景
        Gui.drawRect(layout.x - 3, layout.y - 4,
                layout.x + layout.width + 3,
                layout.y + layout.height + 4,
                0x50000000); // 半透明背景边缘

        Gui.drawRect(layout.x - 2, layout.y - 3,
                layout.x + layout.width + 2,
                layout.y + layout.height + 3,
                0x50000000);

        Gui.drawRect(layout.x - 1, layout.y - 2,
                layout.x + layout.width + 1,
                layout.y + layout.height + 2,
                colors.background);

        // 边框
        drawBorder(layout, colors);

        // 分割线（在模组名后）
        if (content.hasModName() && layout.separatorY > layout.y && layout.separatorY < layout.y + layout.height) {
            Gui.drawRect(
                    layout.x + 1,
                    layout.separatorY,
                    layout.x + layout.width - 1,
                    layout.separatorY + 1,
                    colors.borderStart
            );
        }

        // 如果需要分页，在分割线下方添加页码指示器
        if (content.needsPagination) {
            int pageIndicatorY = layout.y + layout.height - 14; // 底部上方14像素
            String pageText = (content.currentPage + 1) + "/" + content.totalPages;
            int pageTextWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(pageText);
            int pageTextX = layout.x + layout.width - pageTextWidth - 5;

            // 背景半透明矩形
            Gui.drawRect(pageTextX - 2, pageIndicatorY - 1,
                    pageTextX + pageTextWidth + 2, pageIndicatorY + 9,
                    0x80000000);

            // 页码文本
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                    pageText, pageTextX, pageIndicatorY, 0xFFFFFFFF
            );
        }
    }

    private void drawBorder(TooltipLayout layout, TooltipColors colors) {
        // 上边框
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        // 下边框
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        // 左边框
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        // 右边框
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    private void drawItemIcon(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
                Minecraft.getMinecraft().fontRenderer, stack, x, y, null
        );
        RenderHelper.disableStandardItemLighting();
    }

    private void drawTooltipText(TooltipContent content, TooltipLayout layout, FontRenderer font) {
        int textPadding = 4;
        int lineHeight = 10;
        int iconTextX = layout.x + textPadding + 18; // 物品名称/模组名称在图标右侧
        int currentY = layout.y + textPadding;

        // 物品名称
        int itemNameColor = getItemNameColor(content.itemName);
        font.drawStringWithShadow(" " + content.itemName, iconTextX, currentY, itemNameColor);
        currentY += lineHeight;

        // 模组名称
        if (content.modName != null) {
            font.drawStringWithShadow(TextFormatting.YELLOW + " " + content.modName, iconTextX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        // 跳过分割线位置
        currentY += lineHeight;

        // 剩余内容 (左对齐，从图标区域左侧开始)
        int leftAlignedX = layout.x + textPadding;
        for (String line : content.currentPageLines) {
            font.drawStringWithShadow(line, leftAlignedX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        // 如果需要分页，在底部添加换页提示
        if (content.needsPagination) {
            String nextPageHint;
            if (content.currentPage < content.totalPages - 1) {
                nextPageHint = TextFormatting.AQUA + "[Ctrl+C 下一页]";
            } else {
                nextPageHint = TextFormatting.AQUA + "[Ctrl+Z 上一页]";
            }

            // 如果不是第一页且不是最后一页，显示双向提示
            if (content.currentPage > 0 && content.currentPage < content.totalPages - 1) {
                nextPageHint = TextFormatting.AQUA + "[Ctrl+Z 上一页] " + TextFormatting.AQUA + "[Ctrl+C 下一页]";
            }

            font.drawStringWithShadow(nextPageHint, leftAlignedX, currentY, PAGINATION_HINT_COLOR);
        }
    }

    // ========== 辅助方法 ==========
    private TooltipColors getTooltipColors(ItemStack stack) {
        if (!TooltipColorConfig.isEnabled()) {
            return new TooltipColors(
                    0xF0100010,  // 背景
                    0x505000FF,  // 边框开始
                    0x5028007F   // 边框结束
            );
        }

        int bg = TooltipColorConfig.getBackgroundColor();

        int borderStart;
        int borderEnd;
        if (TooltipColorConfig.enableRarityColors()) {
            int rarityColor = TooltipColorConfig.getRarityColor(stack.getRarity());
            borderStart = rarityColor;
            borderEnd = rarityColor;
        } else {
            int borderColor = TooltipColorConfig.getBorderColor();
            borderStart = borderColor;
            borderEnd = borderColor;
        }

        return new TooltipColors(bg, borderStart, borderEnd);
    }

    private int getItemNameColor(String itemName) {
        // 保留原始逻辑，实际应使用物品稀有度
        return 0xFFFFFF;
    }
}