package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import meowmel.quantumhue.QuantumHueConfig;
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

        // 内部填充：与外框同色的淡透明层
        drawInnerBorderFill(layout, colors);

        // 页眉渐变：物品名字区域从左到右渐变色背景
        drawHeaderGradient(layout, colors, content);

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

        // 内部填充：与外框同色的淡透明层
        drawInnerBorderFill(layout, colors);

        drawSimpleBorder(layout, colors);
    }

    private void drawSimpleBorder(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    public void drawItemIcon(ItemStack stack, int x, int y) {
        drawItemIcon(stack, x, y, 1.0f);
    }

    public void drawItemIcon(ItemStack stack, int x, int y, float scale) {
        GlStateManager.pushMatrix();

        if (scale != 1.0f) {
            float centerX = x + TooltipConstants.ICON_SIZE / 2.0f;
            float centerY = y + TooltipConstants.ICON_SIZE / 2.0f;
            GlStateManager.translate(centerX, centerY, 0.0);
            GlStateManager.scale(scale, scale, 1.0);
            GlStateManager.translate(-centerX, -centerY, 0.0);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        RenderHelper.enableGUIStandardItemLighting();

        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
                Minecraft.getMinecraft().fontRenderer, stack, x, y, null
        );

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableCull();

        GlStateManager.popMatrix();
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

    /**
     * 绘制内部填充：使用边框颜色但极低透明度，覆盖在背景之上
     */
    private void drawInnerBorderFill(TooltipLayout layout, TooltipColors colors) {
        if (!QuantumHueConfig.tooltip_background.enabled) return;

        int borderColor = colors.borderStart;
        int alpha = Math.max(4, ((borderColor >> 24) & 0xFF) / 8);
        int fillColor = (borderColor & 0x00FFFFFF) | (alpha << 24);
        Gui.drawRect(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, fillColor);
    }

    /**
     * 绘制页眉渐变：物品名字区域从左到右渐隐的背景色块
     */
    private void drawHeaderGradient(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        if (!QuantumHueConfig.tooltip_background.enabled) return;

        // 计算页眉区域底部（物品名 + 模组名区域，到分隔线为止）
        int headerBottom = layout.separatorY;
        if (headerBottom <= layout.y) return;

        int borderColor = colors.borderStart;
        // 左侧：使用边框原始透明度，上限提高到更深
        int leftAlpha = Math.min(0x80, Math.max(0x20, ((borderColor >> 24) & 0xFF)));
        int leftColor = (borderColor & 0x00FFFFFF) | (leftAlpha << 24);
        // 右侧：完全透明
        int rightColor = leftColor & 0x00FFFFFF;

        drawHorizontalGradient(layout.x, layout.y, layout.x + layout.width, headerBottom, leftColor, rightColor);
    }

    /**
     * 绘制水平渐变矩形（从左到右由 startColor 渐变为 endColor）
     */
    private void drawHorizontalGradient(int left, int top, int right, int bottom, int startColor, int endColor) {
        float a1 = (float)(startColor >> 24 & 255) / 255.0F;
        float r1 = (float)(startColor >> 16 & 255) / 255.0F;
        float g1 = (float)(startColor >> 8 & 255) / 255.0F;
        float b1 = (float)(startColor & 255) / 255.0F;
        float a2 = (float)(endColor >> 24 & 255) / 255.0F;
        float r2 = (float)(endColor >> 16 & 255) / 255.0F;
        float g2 = (float)(endColor >> 8 & 255) / 255.0F;
        float b2 = (float)(endColor & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        // 右上 (endColor)
        buffer.pos(right, top, 0.0D).color(r2, g2, b2, a2).endVertex();
        // 左上 (startColor)
        buffer.pos(left, top, 0.0D).color(r1, g1, b1, a1).endVertex();
        // 左下 (startColor)
        buffer.pos(left, bottom, 0.0D).color(r1, g1, b1, a1).endVertex();
        // 右下 (endColor)
        buffer.pos(right, bottom, 0.0D).color(r2, g2, b2, a2).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}