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
import meowmel.quantumhue.tooltips.text.TooltipTextRenderer;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftIntegration;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftRenderer;

import java.util.List;

public class TooltipRenderer {

    private final TooltipTextRenderer textEffects = new TooltipTextRenderer();

    // ==================== 背景绘制 ====================

    public void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        drawTooltipBackground(layout, colors, content, 0.0F);
    }

    /**
     * @param timeSeconds tooltip 打开后经过的秒数，驱动稀有度微光；0 表示静止。
     */
    public void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content,
                                      float timeSeconds) {
        drawTooltipBackground(layout, colors, content, timeSeconds, 1.0F);
    }

    /**
     * @param timeSeconds tooltip 打开后经过的秒数，驱动稀有度微光；0 表示静止。
     * @param pagePulse   页码胶囊的缩放系数，1 表示静止（翻页瞬间会大于 1）。
     */
    public void drawTooltipBackground(TooltipLayout layout, TooltipColors colors, TooltipContent content,
                                      float timeSeconds, float pagePulse) {
        if (!drawRoundedPanel(layout, colors, content)) {
            if (!drawTechPanel(layout, colors, content)) {
                drawPanel(layout, colors);
                drawHeaderGradient(layout, colors, content);
                drawBorder(layout, colors);
            }
        }
        drawBorderFlow(layout, colors, timeSeconds);
        drawShimmer(layout, colors, timeSeconds);

        if (content.hasModName() && layout.separatorY > layout.y && layout.separatorY < layout.y + layout.height) {
            drawSeparator(layout, colors);
        }

        if (content.needsPagination) {
            drawPaginationIndicator(layout, colors, content, pagePulse);
        }
    }

    public void drawSimpleTooltipBackground(TooltipLayout layout, TooltipColors colors) {
        if (!drawTechPanel(layout, colors, null)) {
            drawPanel(layout, colors);
            drawSimpleBorder(layout, colors);
        }
    }

    // ==================== 面板外观 ====================

    /** 平涂面板（圆角关闭时的回退路径）。 */
    private void drawPanel(TooltipLayout layout, TooltipColors colors) {
        Gui.drawRect(layout.x - 1, layout.y - 1,
                layout.x + layout.width + 1, layout.y + layout.height + 1, colors.background);
        drawInnerBorderFill(layout, colors);
    }

    /**
     * 圆角面板：外圈按稀有度描边色、内圈按背景色各画一个圆角矩形，
     * 两层之间露出的 1px 就是描边。
     *
     * @return true 表示已经画过面板（调用方不要再画直角版本）
     */
    private boolean drawRoundedPanel(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        QuantumHueConfig.TooltipAesthetic cfg = QuantumHueConfig.tooltip_aesthetic;
        if (cfg == null || !cfg.enabled || !cfg.rounded) return false;

        int radius = Math.max(1, Math.min(cfg.corner_radius, Math.min(layout.width, layout.height) / 2));
        int top = cfg.corner_gradient ? colors.backgroundTop : colors.background;
        int bottom = cfg.corner_gradient ? colors.backgroundBottom : colors.background;
        // 外圈 1px 描边
        drawRoundedFill(layout.x - 1, layout.y - 1, layout.width + 2, layout.height + 2,
                radius + 1, colors.borderStart, colors.borderEnd);
        // 主体：垂直渐变
        drawRoundedFill(layout.x, layout.y, layout.width, layout.height,
                radius, top, bottom);
        // 与旧版一致：整体再叠一层极淡的描边色，让下半呈"淡色半透"
        if (QuantumHueConfig.tooltip_background.enabled) {
            int borderColor = colors.borderStart;
            int tintAlpha = Math.min(0x10, Math.max(3, ((borderColor >> 24) & 0xFF) / 16));
            int tint = (borderColor & 0x00FFFFFF) | (tintAlpha << 24);
            drawRoundedFill(layout.x, layout.y, layout.width, layout.height, radius, tint, tint);
        }
        // 上半页眉：左亮右透明的水平渐变，随顶部圆角裁切
        drawRoundedHeaderBand(layout, colors, radius);
        return true;
    }

    /**
     * 顶部圆角、底部平直的水平渐变条（页眉），逐行铺 1px 条带。
     */
    private void drawRoundedHeaderBand(TooltipLayout layout, TooltipColors colors, int radius) {
        if (!QuantumHueConfig.tooltip_background.enabled) return;

        int headerBottom = layout.separatorY;
        if (headerBottom <= layout.y) return;
        int height = Math.min(headerBottom, layout.y + layout.height) - layout.y;
        if (height <= 0) return;

        int borderColor = colors.borderStart;
        int borderAlpha = (borderColor >> 24) & 0xFF;
        int leftAlpha = Math.min(0x40, Math.max(0x10, borderAlpha / 4));
        int leftColor = (borderColor & 0x00FFFFFF) | (leftAlpha << 24);
        int rightColor = leftColor & 0x00FFFFFF;

        drawRoundedStrips(layout.x, layout.y, layout.width, height, radius,
                leftColor, rightColor, true);
    }

    /**
     * 逐行 1px 条带填充圆角区域，每行可指定左右两种颜色（用于水平渐变）。
     *
     * @param topOnly true 时只有上方两角是圆的（页眉条），false 时四角都圆
     */
    private void drawRoundedStrips(int x, int y, int width, int height, int radius,
                                   int leftColor, int rightColor, boolean topOnly) {
        if (width <= 0 || height <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(width, height)));

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < height; i++) {
            int inset = topOnly ? roundedTopInset(i, radius) : roundedInset(i, height, radius);
            float x0 = x + inset;
            float x1 = x + width - inset;
            float y0 = y + i;
            float y1 = y + i + 1;
            buffer.pos(x1, y0, 0.0D).color(red(rightColor), green(rightColor), blue(rightColor), alpha(rightColor)).endVertex();
            buffer.pos(x0, y0, 0.0D).color(red(leftColor), green(leftColor), blue(leftColor), alpha(leftColor)).endVertex();
            buffer.pos(x0, y1, 0.0D).color(red(leftColor), green(leftColor), blue(leftColor), alpha(leftColor)).endVertex();
            buffer.pos(x1, y1, 0.0D).color(red(rightColor), green(rightColor), blue(rightColor), alpha(rightColor)).endVertex();
        }
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** 只看上方圆角的缩进量（页眉条的上一半）。 */
    private static int roundedTopInset(int i, int radius) {
        if (radius <= 0 || i >= radius) return 0;
        // 半径 1：圆角公式会退化成 0，直接切掉一个角像素
        if (radius == 1) return 1;
        double dy = radius - i - 0.5D;
        double dx = radius - Math.sqrt(Math.max(0.0D, (double) radius * radius - dy * dy));
        return (int) Math.round(dx);
    }

    /** 逐行 1px 条带拼出圆角矩形，颜色从顶部渐变到底部。 */
    private void drawRoundedFill(int x, int y, int width, int height, int radius,
                                 int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < height; i++) {
            int inset = roundedInset(i, height, radius);
            float f = height <= 1 ? 0.0F : i / (float) (height - 1);
            int color = TooltipColors.mix(topColor, bottomColor, f);
            float a = alpha(color);
            float cr = red(color);
            float cg = green(color);
            float cb = blue(color);
            float x0 = x + inset;
            float x1 = x + width - inset;
            float y0 = y + i;
            float y1 = y + i + 1;
            buffer.pos(x1, y0, 0.0D).color(cr, cg, cb, a).endVertex();
            buffer.pos(x0, y0, 0.0D).color(cr, cg, cb, a).endVertex();
            buffer.pos(x0, y1, 0.0D).color(cr, cg, cb, a).endVertex();
            buffer.pos(x1, y1, 0.0D).color(cr, cg, cb, a).endVertex();
        }
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** 第 i 行（0 起）为形成圆角需要向内缩进的像素数。 */
    private static int roundedInset(int i, int height, int radius) {
        if (radius <= 0) return 0;
        int d = Math.min(i, height - 1 - i);
        if (d >= radius) return 0;
        // 半径 1：圆角公式会退化成 0，直接切掉一个角像素
        if (radius == 1) return 1;
        double dy = radius - d - 0.5D;
        double dx = radius - Math.sqrt(Math.max(0.0D, (double) radius * radius - dy * dy));
        return (int) Math.round(dx);
    }

    /** 描边上绕圈跑的流光（跑马灯）。圆角处断开，四边按 2px 分段。 */
    private void drawBorderFlow(TooltipLayout layout, TooltipColors colors, float timeSeconds) {
        QuantumHueConfig.TooltipAesthetic cfg = QuantumHueConfig.tooltip_aesthetic;
        if (cfg == null || !cfg.enabled || !cfg.border_flow) return;
        if (timeSeconds <= 0.0F) return;

        int left = layout.x - 1;
        int top = layout.y - 1;
        int width = layout.width + 2;
        int height = layout.height + 2;
        int radius = 0;
        if (cfg.rounded) {
            radius = Math.max(1, Math.min(cfg.corner_radius, Math.min(layout.width, layout.height) / 2));
        } else {
            // 直角科技面板只有一圈暗线（外扩 1px），流光沿着它跑
            left -= 1;
            top -= 1;
            width += 2;
            height += 2;
        }

        float horizontal = width - 2 * radius;
        float vertical = height - 2 * radius;
        if (horizontal <= 0 || vertical <= 0) return;
        float perimeter = 2.0F * (horizontal + vertical);
        float head = (timeSeconds * 90.0F * (float) cfg.shimmer_speed) % perimeter;
        float tail = Math.max(18.0F, perimeter * 0.16F);

        int accent = TooltipColors.mix(colors.accent, 0xFFFFFFFF, 0.30F);
        float cr = red(accent);
        float cg = green(accent);
        float cb = blue(accent);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);

        flowRun(buffer, 0.0F, horizontal, head, perimeter, tail,
                left + radius, top, true, 1, cr, cg, cb);
        flowRun(buffer, horizontal, vertical, head, perimeter, tail,
                left + width - 1, top + radius, false, 1, cr, cg, cb);
        flowRun(buffer, horizontal + vertical, horizontal, head, perimeter, tail,
                left + width - radius, top + height - 1, true, -1, cr, cg, cb);
        flowRun(buffer, 2 * horizontal + vertical, vertical, head, perimeter, tail,
                left, top + height - radius, false, -1, cr, cg, cb);

        tessellator.draw();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /**
     * 沿一条边铺 2px 小段，按与光头的距离算亮度（尾巴平方衰减）。
     *
     * @param start    该边在总周长上的起点
     * @param dir      边的延伸方向：+1 向右/下，-1 向左/上
     * @param x,y      dir=+1 时该边起点坐标
     */
    private void flowRun(BufferBuilder buffer, float start, float length,
                         float head, float perimeter, float tail,
                         int x, int y, boolean horizontal, int dir,
                         float cr, float cg, float cb) {
        int step = 2;
        int segments = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 0; i < segments; i++) {
            float s = start + (i + 0.5F) * step;
            float d = head - s;
            d -= perimeter * (float) Math.round(d / perimeter);
            float a = 1.0F - Math.abs(d) / tail;
            if (a <= 0.0F) continue;
            a = a * a * 0.9F;

            float a0 = i * step;
            float a1 = Math.min(length, a0 + step);
            float x0, x1, y0, y1;
            if (horizontal) {
                x0 = x + dir * a0;
                x1 = x + dir * a1;
                y0 = y;
                y1 = y + 1;
            } else {
                x0 = x;
                x1 = x + 1;
                y0 = y + dir * a0;
                y1 = y + dir * a1;
            }
            quad(buffer, Math.min(x0, x1), Math.min(y0, y1),
                    Math.max(x0, x1), Math.max(y0, y1), cr, cg, cb, a);
        }
    }

    /** 四角颜色各不相同的矩形：左上/右上/右下/左下分别取色。 */
    private void drawGradientQuad(int left, int top, int right, int bottom,
                                  int topLeft, int topRight, int bottomRight, int bottomLeft) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(right, top, 0.0D).color(red(topRight), green(topRight), blue(topRight), alpha(topRight)).endVertex();
        buffer.pos(left, top, 0.0D).color(red(topLeft), green(topLeft), blue(topLeft), alpha(topLeft)).endVertex();
        buffer.pos(left, bottom, 0.0D).color(red(bottomLeft), green(bottomLeft), blue(bottomLeft), alpha(bottomLeft)).endVertex();
        buffer.pos(right, bottom, 0.0D).color(red(bottomRight), green(bottomRight), blue(bottomRight), alpha(bottomRight)).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** EPIC 描边上流动的微光：四边分段的低透明度色块，按正弦波错峰。 */
    private void drawShimmer(TooltipLayout layout, TooltipColors colors, float timeSeconds) {
        QuantumHueConfig.TooltipAesthetic cfg = QuantumHueConfig.tooltip_aesthetic;
        if (cfg == null || !cfg.enabled || !cfg.shimmer || !colors.shimmer) return;
        if (timeSeconds <= 0.0F) return;

        float speed = (float) cfg.shimmer_speed;
        int accent = colors.accent;
        float r = red(accent);
        float g = green(accent);
        float b = blue(accent);
        float phase = timeSeconds * speed * 3.0F;

        int left = layout.x - 1;
        int top = layout.y - 1;
        int width = layout.width + 2;
        int height = layout.height + 2;
        if (!cfg.rounded) {
            // 直角面板只有一圈暗线（外扩 1px），微光贴着它走
            left -= 1;
            top -= 1;
            width += 2;
            height += 2;
        }
        int segX = Math.max(8, Math.min(48, width / 6));
        int segY = Math.max(8, Math.min(48, height / 6));

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < segX; i++) {
            float a = shimmerAlpha(i, segX, phase);
            if (a <= 0.01F) continue;
            float x0 = left + (float) width * i / segX;
            float x1 = left + (float) width * (i + 1) / segX;
            quad(buffer, x0, top - 1, x1, top, r, g, b, a);
            quad(buffer, x0, top + height, x1, top + height + 1, r, g, b, a * 0.7F);
        }
        for (int i = 0; i < segY; i++) {
            float a = shimmerAlpha(i, segY, phase + 1.3F);
            if (a <= 0.01F) continue;
            float y0 = top + (float) height * i / segY;
            float y1 = top + (float) height * (i + 1) / segY;
            quad(buffer, left - 1, y0, left, y1, r, g, b, a * 0.85F);
            quad(buffer, left + width, y0, left + width + 1, y1, r, g, b, a * 0.85F);
        }
        tessellator.draw();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private float shimmerAlpha(int index, int segments, float phase) {
        float wave = 0.5F + 0.5F * (float) Math.sin(index * 0.45F - phase);
        float fade = (float) Math.sin(Math.PI * (index + 0.5F) / segments);
        return 0.08F + 0.50F * wave * fade;
    }

    private void quad(BufferBuilder buffer, float x0, float y0, float x1, float y1,
                      float r, float g, float b, float a) {
        buffer.pos(x1, y0, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(x0, y0, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(x0, y1, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, 0.0D).color(r, g, b, a).endVertex();
    }

    private static float alpha(int color) {
        return ((color >> 24) & 0xFF) / 255.0F;
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
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

    /** 页码胶囊：右下角一个圆角小胶囊，当前页高亮，翻页时轻微弹一下。 */
    private void drawPaginationIndicator(TooltipLayout layout, TooltipColors colors,
                                         TooltipContent content, float pagePulse) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int total = Math.max(1, content.totalPages);

        // 页数不多时用一排小点表示，比"1 / 2"更安静，也更像仪表刻度
        if (total > 1 && total <= 8) {
            drawPaginationDots(layout, colors, total, content.currentPage, pagePulse);
            return;
        }

        int current = Math.min(content.currentPage + 1, total);

        String cur = String.valueOf(current);
        String sep = " / ";
        String tot = String.valueOf(total);
        int textWidth = font.getStringWidth(cur) + font.getStringWidth(sep) + font.getStringWidth(tot);

        int padX = 5;
        int chipHeight = 12;
        int chipWidth = textWidth + padX * 2;
        int chipRight = layout.x + layout.width - 5;
        int chipBottom = layout.y + layout.height - 4;
        int chipLeft = chipRight - chipWidth;
        int chipTop = chipBottom - chipHeight;
        int radius = chipHeight / 2;

        int bg = TooltipColors.withAlpha(TooltipColors.mix(colors.background, colors.accent, 0.30F), 0xD8);
        int border = TooltipColors.withAlpha(colors.accent, 0x70);

        boolean pulsing = Math.abs(pagePulse - 1.0F) > 0.001F;
        if (pulsing) {
            GlStateManager.pushMatrix();
            float cx = chipLeft + chipWidth / 2.0F;
            float cy = chipTop + chipHeight / 2.0F;
            GlStateManager.translate(cx, cy, 0.0D);
            GlStateManager.scale(pagePulse, pagePulse, 1.0D);
            GlStateManager.translate(-cx, -cy, 0.0D);
        }

        drawRoundedFill(chipLeft - 1, chipTop - 1, chipWidth + 2, chipHeight + 2,
                radius + 1, border, border);
        drawRoundedFill(chipLeft, chipTop, chipWidth, chipHeight, radius, bg, bg);

        int textY = chipTop + (chipHeight - font.FONT_HEIGHT) / 2 + 1;
        int x = chipLeft + padX;
        int curColor = TooltipColors.mix(colors.accent, 0xFFFFFFFF, 0.55F);
        font.drawStringWithShadow(cur, x, textY, curColor);
        x += font.getStringWidth(cur);
        font.drawStringWithShadow(sep, x, textY, 0x55FFFFFF);
        x += font.getStringWidth(sep);
        font.drawStringWithShadow(tot, x, textY, 0x99FFFFFF);

        if (pulsing) {
            GlStateManager.popMatrix();
        }
    }

    /** 页码小点：一页一个 3×3 的方块，当前页用强调色，其余为暗灰。 */
    private void drawPaginationDots(TooltipLayout layout, TooltipColors colors,
                                    int total, int page, float pagePulse) {
        int size = 3;
        int gap = 2;
        int width = total * size + (total - 1) * gap;
        int x0 = layout.x + layout.width - 6 - width;
        int y0 = layout.y + layout.height - 7;
        int current = Math.max(0, Math.min(page, total - 1));

        boolean pulsing = Math.abs(pagePulse - 1.0F) > 0.001F;
        if (pulsing) {
            GlStateManager.pushMatrix();
            float cx = x0 + width / 2.0F;
            float cy = y0 + size / 2.0F;
            GlStateManager.translate(cx, cy, 0.0D);
            GlStateManager.scale(pagePulse, pagePulse, 1.0D);
            GlStateManager.translate(-cx, -cy, 0.0D);
        }

        int active = TooltipColors.withAlpha(colors.accent, 0xE6);
        for (int i = 0; i < total; i++) {
            int x = x0 + i * (size + gap);
            int color = i == current ? active : 0x40FFFFFF;
            Gui.drawRect(x, y0, x + size, y0 + size, color);
        }

        if (pulsing) {
            GlStateManager.popMatrix();
        }
    }

    // ==================== 物品图标 ====================

    public void drawItemIcon(ItemStack stack, int x, int y) {
        drawItemIcon(stack, x, y, null, 1.0f);
    }

    public void drawItemIcon(ItemStack stack, int x, int y, float scale) {
        drawItemIcon(stack, x, y, null, scale);
    }

    public void drawItemIcon(ItemStack stack, int x, int y, TooltipColors colors, float scale) {
        if (colors != null) drawIconFrame(colors, x, y);

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

    /**
     * 直角科技面板：垂直渐变底、等级色极淡染色、扫描线、顶部窄高光、双层外框。
     *
     * @return false 表示外观增强未启用，调用方回退到旧版平涂
     */
    private boolean drawTechPanel(TooltipLayout layout, TooltipColors colors, TooltipContent content) {
        QuantumHueConfig.TooltipAesthetic cfg = QuantumHueConfig.tooltip_aesthetic;
        if (cfg == null || !cfg.enabled) return false;

        int left = layout.x - 1;
        int top = layout.y - 1;
        int right = layout.x + layout.width + 1;
        int bottom = layout.y + layout.height + 1;
        int borderAlpha = (colors.borderStart >>> 24) & 0xFF;

        drawTechBody(cfg, colors, left, top, right, bottom, borderAlpha);
        drawTechHeader(cfg, colors, layout, content, left, top, right, bottom, borderAlpha);
        drawTechHighlight(colors, left, top, right, borderAlpha);
        drawTechFrame(colors, left, top, right, bottom);
        return true;
    }

    /** 面板底：上下两段垂直渐变，再叠一层等级色极淡染色。 */
    private void drawTechBody(QuantumHueConfig.TooltipAesthetic cfg, TooltipColors colors,
                              int left, int top, int right, int bottom, int borderAlpha) {
        int bodyTop = cfg.corner_gradient ? colors.backgroundTop : colors.background;
        int bodyBottom = cfg.corner_gradient ? colors.backgroundBottom : colors.background;

        int topFill = TooltipColors.withAlpha(TooltipColors.mix(bodyTop, colors.borderStart, 0.10F), 0xDC);
        int bottomFill = TooltipColors.withAlpha(bodyBottom, 0xFC);
        drawGradientQuad(left, top, right, bottom, topFill, topFill, bottomFill, bottomFill);

        if (QuantumHueConfig.tooltip_background.enabled) {
            int tintAlpha = Math.min(0x1C, Math.max(0x10, borderAlpha / 8));
            Gui.drawRect(left, top, right, bottom,
                    (colors.borderStart & 0x00FFFFFF) | (tintAlpha << 24));
        }
    }

    /** 页眉：等级色水平渐变（左浓右透明），叠加扫描线。 */
    private void drawTechHeader(QuantumHueConfig.TooltipAesthetic cfg, TooltipColors colors,
                                TooltipLayout layout, TooltipContent content,
                                int left, int top, int right, int bottom, int borderAlpha) {
        int headerBottom = (content != null && layout.separatorY > layout.y)
                ? layout.separatorY
                : layout.y + Math.min(20, Math.max(10, layout.height / 3));
        headerBottom = Math.min(headerBottom, bottom - 2);

        if (QuantumHueConfig.tooltip_background.enabled && headerBottom > top + 1) {
            int headAlpha = Math.min(0x70, Math.max(0x2E, borderAlpha / 2));
            int rgb = colors.borderStart & 0x00FFFFFF;
            int headLeft = rgb | (headAlpha << 24);
            int headLeftLow = rgb | ((headAlpha * 2 / 3) << 24);
            int clear = rgb;
            drawGradientQuad(left, top, right, headerBottom, headLeft, clear, clear, headLeftLow);
        }

        if (cfg.scanline_strength > 0) {
            drawScanlines(left, top, right, bottom, Math.min(0xFF, cfg.scanline_strength));
        }
    }

    /** 顶部 2px 窄高光，模拟金属棱边受光。 */
    private void drawTechHighlight(TooltipColors colors, int left, int top, int right, int borderAlpha) {
        int highlight = Math.min(0x30, Math.max(0x10, borderAlpha / 5));
        int accentRgb = colors.accent & 0x00FFFFFF;
        Gui.drawRect(left, top, right, top + 1, accentRgb | (highlight << 24));
        Gui.drawRect(left, top + 1, right, top + 2, accentRgb | ((highlight / 2) << 24));
    }

    /** 双层外框：外圈暗金 1px，内圈亮金 1px，均为实色。 */
    private void drawTechFrame(TooltipColors colors, int left, int top, int right, int bottom) {
        int dl = left - 2;
        int dt = top - 2;
        int dr = right + 2;
        int db = bottom + 2;

        int frame = (TooltipColors.mix(colors.accent, 0xFF000000, 0.40F) & 0x00FFFFFF) | 0xFF000000;
        Gui.drawRect(dl, dt, dr, dt + 1, frame);
        Gui.drawRect(dl, db - 1, dr, db, frame);
        Gui.drawRect(dl, dt + 1, dl + 1, db - 1, frame);
        Gui.drawRect(dr - 1, dt + 1, dr, db - 1, frame);

        int line = (colors.accent & 0x00FFFFFF) | 0xFF000000;
        Gui.drawRect(dl + 1, dt + 1, dr - 1, dt + 2, line);
        Gui.drawRect(dl + 1, db - 2, dr - 1, db - 1, line);
        Gui.drawRect(dl + 1, dt + 2, dl + 2, db - 2, line);
        Gui.drawRect(dr - 2, dt + 2, dr - 1, db - 2, line);
    }

    /** 面板内的横向扫描线，一次 Tessellator 批量画完，避免每行一次 draw call。 */
    private void drawScanlines(int left, int top, int right, int bottom, int alpha) {
        if (right <= left || bottom <= top) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int y = top; y < bottom; y += 2) {
            buffer.pos(right, y, 0.0D).color(1.0F, 1.0F, 1.0F, alpha / 255.0F).endVertex();
            buffer.pos(left, y, 0.0D).color(1.0F, 1.0F, 1.0F, alpha / 255.0F).endVertex();
            buffer.pos(left, y + 1, 0.0D).color(1.0F, 1.0F, 1.0F, alpha / 255.0F).endVertex();
            buffer.pos(right, y + 1, 0.0D).color(1.0F, 1.0F, 1.0F, alpha / 255.0F).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** 四角 L 形角标：科技风的"瞄准角"，长度与亮度由配置控制。 */

    /**
     * 图标插槽，照 Obscure Tooltips 的默认 slot 做的：20×20 的纯白极淡方块，
     * 1px 内缩填充 0x20FFFFFF + 1px 描边 0x30FFFFFF，不加角标、不加暗槽。
     * 存在的意义只是让图标"落在格子上"，个性交给图标动画和面板效果。
     */
    private void drawIconFrame(TooltipColors colors, int x, int y) {
        QuantumHueConfig.TooltipAesthetic cfg = QuantumHueConfig.tooltip_aesthetic;
        if (cfg == null || !cfg.enabled || !cfg.icon_frame) return;

        int size = TooltipConstants.ICON_SIZE + 2;
        int left = x - 2;
        int top = y - 2;

        // 填充（1px 内缩，让描边露在四周）
        Gui.drawRect(left + 1, top + 1, left + size - 1, top + size - 1, 0x20FFFFFF);

        // 1px 描边，四角留空（与参考实现一致）
        int border = 0x30FFFFFF;
        Gui.drawRect(left + 1, top, left + size - 1, top + 1, border);
        Gui.drawRect(left + 1, top + size - 1, left + size - 1, top + size, border);
        Gui.drawRect(left, top + 1, left + 1, top + size - 1, border);
        Gui.drawRect(left + size - 1, top + 1, left + size, top + size - 1, border);
    }

    // ==================== 文本渲染（组件化） ====================

    public void drawTooltipText(TooltipContent content, TooltipLayout layout, FontRenderer font, TooltipColors colors) {
        int textPadding = TooltipConstants.TEXT_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;
        int iconTextX = layout.x + textPadding + TooltipConstants.ICON_SIZE;
        int currentY = layout.y + textPadding;

        List<TooltipLine> lines = content.buildComponentLines();
        QuantumHueConfig.TooltipTextEffects textCfg = QuantumHueConfig.tooltip_text_effects;
        boolean effects = textCfg != null && textCfg.enabled && !textEffects.isBroken();
        if (effects) {
            textEffects.beginFrame(content, lines, font);
        }

        // 物品名
        int itemNameColor = TooltipColorHelper.getItemNameColor(content.itemName);
        String itemText = " " + content.itemName;
        if (effects) {
            textEffects.drawItemName(itemText, iconTextX, currentY, itemNameColor, colors.borderStart);
        } else {
            font.drawStringWithShadow(itemText, iconTextX, currentY, itemNameColor);
        }
        currentY += lineHeight;

        // 模组名
        if (content.modName != null) {
            font.drawStringWithShadow(TextFormatting.YELLOW + " " + content.modName, iconTextX, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }

        currentY += lineHeight; // 空行

        // 根据组件类型渲染每一行
        int leftAlignedX = layout.x + textPadding;

        // 找到连续 ASPECT_SPACER 区域的起始行索引
        int aspectRenderY = -1;
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).type == TooltipLine.Type.ASPECT_SPACER &&
                lines.get(i + 1).type == TooltipLine.Type.ASPECT_SPACER) {
                aspectRenderY = layout.y + textPadding + (i + 3) * lineHeight;
                break;
            }
        }

        int bodyIndex = 0;
        for (TooltipLine line : lines) {
            switch (line.type) {
                case TEXT:
                    if (effects) {
                        textEffects.drawBodyLine(line.styled, leftAlignedX, currentY, bodyIndex,
                                colors.borderStart, layout.x + layout.width - 2);
                    } else {
                        font.drawStringWithShadow(line.text, leftAlignedX, currentY, 0xFFFFFF);
                    }
                    bodyIndex++;
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
        boolean hasNext = content.currentPage < content.totalPages - 1;
        boolean hasPrev = content.currentPage > 0;

        String hint;
        if (hasNext && hasPrev) {
            hint = TextFormatting.AQUA + "[Ctrl+C/Z 翻页]";
        } else if (hasNext) {
            hint = TextFormatting.AQUA + "[Ctrl+C 下一页]";
        } else {
            hint = TextFormatting.AQUA + "[Ctrl+Z 上一页]";
        }

        font.drawStringWithShadow(hint, x, y, TooltipConstants.PAGINATION_HINT_COLOR);
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
        int borderAlpha = (borderColor >> 24) & 0xFF;
        int leftAlpha = Math.min(0x40, Math.max(0x10, borderAlpha / 4));
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
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
