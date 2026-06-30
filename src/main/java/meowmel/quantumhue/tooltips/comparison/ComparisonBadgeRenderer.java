package meowmel.quantumhue.tooltips.comparison;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.TooltipLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ComparisonBadgeRenderer {

    public static final int BADGE_HEIGHT = 12;
    private static final String DEFAULT_BADGE_TEXT = "已装备"; // 已装备

    /**
     * 在对比Tooltip上方绘制"已装备"徽章
     */
    public void drawBadge(TooltipLayout layout, FontRenderer font) {
        QuantumHueConfig.EquipmentComparison config = QuantumHueConfig.equipmentComparison;

        int badgeX = layout.x;
        int badgeY = layout.y - BADGE_HEIGHT - 2;
        int badgeWidth = layout.width;

        int bgColor = config.badgeBackgroundColor;
        int borderStart = config.badgeBorderStartColor;
        int borderEnd = config.badgeBorderEndColor;

        // 背景
        Gui.drawRect(badgeX, badgeY, badgeX + badgeWidth, badgeY + BADGE_HEIGHT, bgColor);

        // 边框：上 = borderStart, 下 = borderEnd, 左 = borderStart, 右 = borderEnd
        Gui.drawRect(badgeX, badgeY, badgeX + badgeWidth, badgeY + 1, borderStart);
        Gui.drawRect(badgeX, badgeY + BADGE_HEIGHT - 1, badgeX + badgeWidth, badgeY + BADGE_HEIGHT, borderEnd);
        Gui.drawRect(badgeX, badgeY, badgeX + 1, badgeY + BADGE_HEIGHT, borderStart);
        Gui.drawRect(badgeX + badgeWidth - 1, badgeY, badgeX + badgeWidth, badgeY + BADGE_HEIGHT, borderEnd);

        // 文字
        String badgeText = getBadgeText();
        int textColor = config.badgeTextColor;
        int textWidth = font.getStringWidth(badgeText);
        int textX = badgeX + (badgeWidth - textWidth) / 2;
        int textY = badgeY + (BADGE_HEIGHT - font.FONT_HEIGHT) / 2;

        font.drawStringWithShadow(badgeText, textX, textY, textColor);
    }

    /**
     * 获取徽章下方Tooltip的顶部Y坐标偏移量（让Tooltip为徽章留出空间）
     */
    public int getBadgeOffset() {
        return BADGE_HEIGHT + 2;
    }

    private String getBadgeText() {
        QuantumHueConfig.EquipmentComparison config = QuantumHueConfig.equipmentComparison;
        if (config.overrideBadgeText && !config.badgeText.isEmpty()) {
            return config.badgeText;
        }
        return DEFAULT_BADGE_TEXT;
    }
}
