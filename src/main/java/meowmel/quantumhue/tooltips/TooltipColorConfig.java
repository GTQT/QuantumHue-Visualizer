package meowmel.quantumhue.tooltips;


// ========== 颜色配置 ==========

import meowmel.quantumhue.QuantumHueConfig;

public class TooltipColorConfig {
    public static boolean isEnabled() {
        return QuantumHueConfig.tooltips_custom.enabled;
    }

    public static int getBackgroundColor() {
        return QuantumHueConfig.tooltips_custom.backgroundColor;
    }

    public static int getBorderColor() {
        return QuantumHueConfig.tooltips_custom.borderColor;
    }

    public static boolean enableRarityColors() {
        return QuantumHueConfig.tooltips_custom.enableRarityColors;
    }

    public static int getRarityColor(net.minecraft.item.EnumRarity rarity) {
        switch (rarity) {
            case UNCOMMON: return 0xFFFFFF55; // 黄绿色
            case RARE: return 0xFF5555FF;     // 蓝色
            case EPIC: return 0xFFAA00AA;     // 紫色
            default: return getBorderColor();
        }
    }
}