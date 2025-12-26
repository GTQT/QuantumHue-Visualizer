package meowmel.quantumhue.tooltips.applecore;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;


public class AppleSkinRenderer {
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_SPACING = 4;

    // 颜色定义
    private static final int HUNGER_BAR_BG_COLOR = 0xFF3F3F3F; // 饥饿值背景色
    private static final int HUNGER_BAR_FG_COLOR = 0xFF00AA00; // 饥饿值前景色（绿色）
    private static final int HUNGER_BAR_NEGATIVE_COLOR = 0xFFAA0000; // 负饥饿值颜色（红色）
    private static final int SATURATION_BAR_BG_COLOR = 0xFF3F3F3F; // 饱和度背景色
    private static final int SATURATION_BAR_FG_COLOR = 0xFFFFDD00; // 饱和度前景色（金色）
    private static final int SATURATION_BAR_NEGATIVE_COLOR = 0xFFFF6600; // 负饱和度颜色（橙色）

    // 边框颜色
    private static final int BORDER_COLOR = QuantumHueConfig.tooltips_custom.borderColor;

    /**
     * 渲染AppleSkin食物信息（进度条形式）
     *
     * @param foodInfo 食物信息
     * @param x        起始X坐标
     * @param y        起始Y坐标
     * @param maxWidth 最大宽度
     */
    public static void renderFoodInfo(AppleSkinIntegration.FoodInfo foodInfo, int x, int y, int maxWidth) {
        if (foodInfo == null || !foodInfo.hasHunger()) return;

        // 计算饥饿值和饱和度
        float hunger = (float) foodInfo.modifiedValues.hunger / 2.0f; // 转换为半饥饿值
        float saturation = foodInfo.modifiedValues.getSaturationIncrement() / 2.0f; // 转换为半饱和度

        // 绘制饥饿值进度条
        drawProgressBar(x, y, maxWidth, hunger, HUNGER_BAR_BG_COLOR,
                hunger >= 0 ? HUNGER_BAR_FG_COLOR : HUNGER_BAR_NEGATIVE_COLOR);

        // 绘制饱和度进度条
        drawProgressBar(x, y + BAR_HEIGHT + BAR_SPACING, maxWidth, saturation, SATURATION_BAR_BG_COLOR,
                saturation >= 0 ? SATURATION_BAR_FG_COLOR : SATURATION_BAR_NEGATIVE_COLOR);
    }

    private static void drawProgressBar(int x, int y, int maxWidth, float value,
                                        int bgColor, int fgColor) {
        Minecraft mc = Minecraft.getMinecraft();

        // 绘制边框
        Gui.drawRect(x - 1, y - 1, x + maxWidth + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);

        // 绘制背景
        Gui.drawRect(x, y, x + maxWidth, y + BAR_HEIGHT, bgColor);

        // 计算进度条宽度
        float progress = Math.min(Math.abs(value) / 10.0f, 1.0f); // 最大显示10个饥饿图标对应的值
        int barWidth = (int) (maxWidth * progress);

        // 绘制前景（进度）
        if (value >= 0) {
            // 正值：从左到右
            Gui.drawRect(x, y, x + barWidth, y + BAR_HEIGHT, fgColor);
        } else {
            // 负值：从右到左
            Gui.drawRect(x + maxWidth - barWidth, y, x + maxWidth, y + BAR_HEIGHT, fgColor);
        }

        // 绘制数值文本
        String valueText = String.format("%.1f", Math.abs(value));
        if (Math.abs(value) >= 10.0f) {
            valueText = "10+";
        }

        int textX = x + (maxWidth - mc.fontRenderer.getStringWidth(valueText)) / 2;
        int textY = y + (BAR_HEIGHT - 8) / 2;

        // 绘制阴影文本
        mc.fontRenderer.drawStringWithShadow(valueText, textX, textY, 0xFFFFFF);
    }
}