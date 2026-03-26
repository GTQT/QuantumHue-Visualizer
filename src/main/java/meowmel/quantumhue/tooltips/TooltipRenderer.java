package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import meowmel.quantumhue.tooltips.applecore.AppleSkinRenderer;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftIntegration;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftRenderer;

import java.util.List;

public class TooltipRenderer {

    public void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        Gui.drawRect(layout.x - 2, layout.y - 2,
                layout.x + layout.width + 2,
                layout.y + layout.height + 2,
                0x50000000);

        Gui.drawRect(layout.x - 1, layout.y - 1,
                layout.x + layout.width + 1,
                layout.y + layout.height + 1,
                colors.background);

        drawBorder(layout, colors);

        if (content.hasModName() && layout.separatorY > layout.y && layout.separatorY < layout.y + layout.height) {
            Gui.drawRect(
                    layout.x + 1,
                    layout.separatorY,
                    layout.x + layout.width - 1,
                    layout.separatorY + 1,
                    colors.borderStart
            );
        }

        if (content.needsPagination) {
            drawPaginationIndicator(layout, content);
        }
    }

    private void drawBorder(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    private void drawPaginationIndicator(TooltipLayout layout, TooltipContent content) {
        int pageIndicatorY = layout.y + layout.height - 14;
        String pageText = (content.currentPage + 1) + "/" + content.totalPages;
        int pageTextWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(pageText);
        int pageTextX = layout.x + layout.width - pageTextWidth - 5;

        Gui.drawRect(pageTextX - 2, pageIndicatorY - 1,
                pageTextX + pageTextWidth + 2, pageIndicatorY + 9,
                0x80000000);

        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                pageText, pageTextX, pageIndicatorY, 0xFFFFFFFF
        );
    }

    public void drawSimpleTooltipBackground(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 2, layout.y - 2,
                layout.x + layout.width + 2,
                layout.y + layout.height + 2,
                0x50000000);

        Gui.drawRect(layout.x - 1, layout.y - 1,
                layout.x + layout.width + 1,
                layout.y + layout.height + 1,
                colors.background);

        drawSimpleBorder(layout, colors);
    }

    private void drawSimpleBorder(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    public void drawItemIcon(ItemStack stack, int x, int y) {
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        RenderHelper.enableGUIStandardItemLighting();

        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
                Minecraft.getMinecraft().fontRenderer, stack, x, y, null
        );

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableCull();
    }

    public void drawTooltipText(TooltipContent content, TooltipLayout layout, FontRenderer font) {
        int textPadding = TooltipConstants.TEXT_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;
        int iconTextX = layout.x + textPadding + TooltipConstants.ICON_SIZE;
        int currentY = layout.y + textPadding;

        int itemNameColor = TooltipColorHelper.getItemNameColor(content.itemName);
        font.drawStringWithShadow(" " + content.itemName, iconTextX, currentY, itemNameColor);
        currentY += lineHeight;

        if (content.modName != null) {
            font.drawStringWithShadow(TextFormatting.YELLOW + " " + content.modName, iconTextX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        currentY += lineHeight;

        int leftAlignedX = layout.x + textPadding;
        for (String line : content.currentPageLines) {
            font.drawStringWithShadow(line, leftAlignedX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        if (ThaumcraftIntegration.isThaumcraftAvailable() && content.shouldShowAspects()) {
            renderThaumcraftAspects(content, layout, font);
        }

        if (content.needsPagination) {
            drawPaginationHint(content, leftAlignedX, currentY, font);
            currentY += lineHeight;
        }

        if (content.shouldShowFoodInfo()) {
            AppleSkinRenderer.renderFoodInfo(
                    content.foodInfo,
                    layout.x + 4,
                    currentY,
                    layout.width - 8
            );
        }
    }

    private void renderThaumcraftAspects(TooltipContent content, TooltipLayout layout, FontRenderer font) {
        int renderHeight = -1;
        for (int i = 0; i < content.currentPageLines.size() - 1; i++) {
            String currentLine = content.currentPageLines.get(i);
            String nextLine = content.currentPageLines.get(i + 1);

            if (currentLine.contains("    ") && nextLine.contains("    ")) {
                renderHeight = layout.y + TooltipConstants.TEXT_PADDING +
                        (content.currentPageLines.indexOf(currentLine) + 3) * TooltipConstants.LINE_HEIGHT;
                break;
            }
        }

        if (renderHeight != -1) {
            ThaumcraftRenderer.renderAspectIcons(
                    content.aspects,
                    layout.x + 4,
                    renderHeight
            );
        }
    }

    private void drawPaginationHint(TooltipContent content, int x, int y, FontRenderer font) {
        String nextPageHint;
        if (content.currentPage < content.totalPages - 1) {
            nextPageHint = TextFormatting.AQUA + "[Ctrl+C 下一页]";
        } else {
            nextPageHint = TextFormatting.AQUA + "[Ctrl+Z 上一页]";
        }

        if (content.currentPage > 0 && content.currentPage < content.totalPages - 1) {
            nextPageHint = TextFormatting.AQUA + "[-]";
        }

        font.drawStringWithShadow(nextPageHint, x, y, TooltipConstants.PAGINATION_HINT_COLOR);
    }

    public void drawSimpleTooltipText(List<String> wrappedLines, TooltipLayout layout, FontRenderer font) {
        int textPadding = TooltipConstants.TEXT_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;
        int currentY = layout.y + textPadding;

        for (String line : wrappedLines) {
            font.drawStringWithShadow(line, layout.x + textPadding, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
    }
}