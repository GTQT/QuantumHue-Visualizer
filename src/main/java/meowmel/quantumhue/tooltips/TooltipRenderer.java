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

    // ==================== 背景绘制 ====================

    public void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        Gui.drawRect(layout.x - 2, layout.y - 2,
                layout.x + layout.width + 2,
                layout.y + layout.height + 2,
                0x50000000);

        Gui.drawRect(layout.x - 1, layout.y - 1,
                layout.x + layout.width + 1,
                layout.y + layout.height + 1,
                colors.background);

        drawInnerBorderFill(layout, colors);
        drawHeaderGradient(layout, colors, content);
        drawBorder(layout, colors);

        if (content.hasModName() && layout.separatorY > layout.y && layout.separatorY < layout.y + layout.height) {
            drawSeparator(layout, colors);
        }

        if (content.needsPagination) {
            drawPaginationIndicator(layout, content);
        }
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

        drawInnerBorderFill(layout, colors);
        drawSimpleBorder(layout, colors);
    }

    // ==================== 边框 ====================

    private void drawBorder(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    private void drawSimpleBorder(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y, colors.borderStart);
        Gui.drawRect(layout.x - 1, layout.y + layout.height, layout.x + layout.width + 1, layout.y + layout.height + 1, colors.borderEnd);
        Gui.drawRect(layout.x - 1, layout.y, layout.x, layout.y + layout.height, colors.borderStart);
        Gui.drawRect(layout.x + layout.width, layout.y, layout.x + layout.width + 1, layout.y + layout.height, colors.borderEnd);
    }

    // ==================== 分隔线（两端渐变变尖） ====================

    private void drawSeparator(TooltipLayout layout, TooltipColors colors) {
        int left = layout.x + 8;
        int right = layout.x + layout.width - 8;
        int mid = (left + right) / 2;
        int y = layout.separatorY;
        int transparent = colors.borderStart & 0x00FFFFFF;

        drawHorizontalGradient(left, y, mid, y + 1, transparent, colors.borderStart);
        drawHorizontalGradient(mid, y, right, y + 1, colors.borderStart, transparent);
    }

    // ==================== 分页指示器 ====================

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

    // ==================== 物品图标 ====================

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

    // ==================== 文本渲染（组件化） ====================

    public void drawTooltipText(TooltipContent content, TooltipLayout layout, FontRenderer font) {
        int textPadding = TooltipConstants.TEXT_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;
        int iconTextX = layout.x + textPadding + TooltipConstants.ICON_SIZE;
        int currentY = layout.y + textPadding;

        // 物品名
        int itemNameColor = TooltipColorHelper.getItemNameColor(content.itemName);
        font.drawStringWithShadow(" " + content.itemName, iconTextX, currentY, itemNameColor);
        currentY += lineHeight;

        // 模组名
        if (content.modName != null) {
            font.drawStringWithShadow(TextFormatting.YELLOW + " " + content.modName, iconTextX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        currentY += lineHeight; // 空行

        // 根据组件类型渲染每一行
        int leftAlignedX = layout.x + textPadding;
        List<TooltipLine> lines = content.buildComponentLines();

        // 找到连续 ASPECT_SPACER 区域的起始行索引
        int aspectRenderY = -1;
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).type == TooltipLine.Type.ASPECT_SPACER &&
                lines.get(i + 1).type == TooltipLine.Type.ASPECT_SPACER) {
                aspectRenderY = layout.y + textPadding + (i + 3) * lineHeight;
                break;
            }
        }

        for (TooltipLine line : lines) {
            switch (line.type) {
                case TEXT:
                    font.drawStringWithShadow(line.text, leftAlignedX, currentY, 0xFFFFFF);
                    break;
                case ASPECT_SPACER:
                    // 占位行：仍渲染原文字以保证和原版一致（要素图标覆盖在上面）
                    font.drawStringWithShadow(line.text, leftAlignedX, currentY, 0xFFFFFF);
                    break;
                default:
                    break;
            }
            currentY += lineHeight;
        }

        // Thaumcraft 要素图标
        if (ThaumcraftIntegration.isThaumcraftAvailable() && content.shouldShowAspects() && aspectRenderY != -1) {
            ThaumcraftRenderer.renderAspectIcons(content.aspects, layout.x + 4, aspectRenderY);
        }

        // 分页提示
        if (content.needsPagination) {
            drawPaginationHint(content, leftAlignedX, currentY, font);
            currentY += lineHeight;
        }

        // AppleCore 食物信息
        if (content.shouldShowFoodInfo()) {
            AppleSkinRenderer.renderFoodInfo(content.foodInfo, layout.x + 4, currentY, layout.width - 8);
        }
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

    // ==================== 背景效果 ====================

    private void drawInnerBorderFill(TooltipLayout layout, TooltipColors colors) {
        if (!QuantumHueConfig.tooltip_background.enabled) return;

        int borderColor = colors.borderStart;
        int alpha = Math.max(4, ((borderColor >> 24) & 0xFF) / 8);
        int fillColor = (borderColor & 0x00FFFFFF) | (alpha << 24);
        Gui.drawRect(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, fillColor);
    }

    private void drawHeaderGradient(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        if (!QuantumHueConfig.tooltip_background.enabled) return;

        int headerBottom = layout.separatorY;
        if (headerBottom <= layout.y) return;

        int borderColor = colors.borderStart;
        int leftAlpha = Math.min(0x80, Math.max(0x20, ((borderColor >> 24) & 0xFF)));
        int leftColor = (borderColor & 0x00FFFFFF) | (leftAlpha << 24);
        int rightColor = leftColor & 0x00FFFFFF;

        drawHorizontalGradient(layout.x, layout.y, layout.x + layout.width, headerBottom, leftColor, rightColor);
    }

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
        buffer.pos(right, top, 0.0D).color(r2, g2, b2, a2).endVertex();
        buffer.pos(left, top, 0.0D).color(r1, g1, b1, a1).endVertex();
        buffer.pos(left, bottom, 0.0D).color(r1, g1, b1, a1).endVertex();
        buffer.pos(right, bottom, 0.0D).color(r2, g2, b2, a2).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
