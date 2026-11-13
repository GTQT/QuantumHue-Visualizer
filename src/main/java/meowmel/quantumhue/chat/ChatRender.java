package meowmel.quantumhue.chat;

import net.minecraft.client.gui.Gui;

/**
 * 聊天界面用到的几种小绘制工具。
 *
 * <p>1.12.2 的 {@link Gui} 只有直角矩形，这里补一个“逐行内缩”的圆角矩形：
 * 每行按圆弧公式算内缩量，圆角半径小的时候就是像素级的倒角，够聊天气泡用。
 */
final class ChatRender {

    private ChatRender() {}

    /** 圆角矩形；radius <= 0 时退化成直角矩形。 */
    static void roundRect(int x1, int y1, int x2, int y2, int color, int radius) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            Gui.drawRect(x1, y1, x2, y2, color);
            return;
        }
        // 中间整段一次画完，再补上下两个圆角
        Gui.drawRect(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - (i + 0.5);
            int inset = r - (int) Math.ceil(Math.sqrt(r * r - dy * dy));
            if (inset < 0) {
                inset = 0;
            }
            Gui.drawRect(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            Gui.drawRect(x1 + inset, y2 - 1 - i, x2 - inset, y2 - i, color);
        }
    }

    /** 横向渐变条：按列拆成小竖条画，色标少的时候比手写 Tessellator 省事。 */
    static void gradientH(int x1, int y1, int x2, int y2, int left, int right) {
        int width = x2 - x1;
        if (width <= 0 || y2 <= y1) {
            return;
        }
        int steps = Math.min(width, 64);
        for (int i = 0; i < steps; i++) {
            int sx = x1 + width * i / steps;
            int ex = x1 + width * (i + 1) / steps;
            if (ex <= sx) {
                ex = sx + 1;
            }
            Gui.drawRect(sx, y1, ex, y2, lerp(left, right, steps == 1 ? 0f : i / (float) (steps - 1)));
        }
    }

    /** 按比例混合两个颜色，alpha 取两者的平均值。 */
    static int lerp(int a, int b, float ratio) {
        float inv = 1f - ratio;
        int alpha = Math.round(((a >>> 24) & 0xFF) * inv + ((b >>> 24) & 0xFF) * ratio);
        int red = Math.round(((a >> 16) & 0xFF) * inv + ((b >> 16) & 0xFF) * ratio);
        int green = Math.round(((a >> 8) & 0xFF) * inv + ((b >> 8) & 0xFF) * ratio);
        int blue = Math.round((a & 0xFF) * inv + (b & 0xFF) * ratio);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** 换掉颜色里的 alpha 通道。 */
    static int withAlpha(int color, int alpha) {
        int a = alpha < 0 ? 0 : Math.min(alpha, 255);
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** 取颜色亮度 0~1，用来判断亮色/暗色主题。 */
    static float luminance(int color) {
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue;
    }
}
