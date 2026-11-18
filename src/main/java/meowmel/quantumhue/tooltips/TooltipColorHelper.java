package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.gregtech.GregtechIntegration;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

public class TooltipColorHelper {
    private static final int DEFAULT_BACKGROUND_COLOR = 0xF0100010;
    private static final int DEFAULT_BORDER_START_COLOR = 0x505000FF;
    private static final int DEFAULT_BORDER_END_COLOR = 0x5028007F;

    public static TooltipColors getTooltipColors() {
        if (!TooltipColorConfig.isEnabled()) {
            return getDefaultTooltipColors();
        }

        int bg = TooltipColorConfig.getBackgroundColor();
        int borderColor = TooltipColorConfig.getBorderColor();

        return palette(bg, borderColor, borderColor, false);
    }

    public static TooltipColors getTooltipColors(ItemStack stack) {
        if (!TooltipColorConfig.isEnabled()) {
            return getDefaultTooltipColors();
        }

        int bg = TooltipColorConfig.getBackgroundColor();
        boolean rarityPalette = QuantumHueConfig.tooltip_aesthetic != null
                && QuantumHueConfig.tooltip_aesthetic.enabled
                && QuantumHueConfig.tooltip_aesthetic.rarity_palette;

        if (isModLoaded("gregtech")) {
            int color = GregtechIntegration.getColor(stack);
            if (color != -1) {
                return palette(bg, color, color, false);
            }
        }

        if (TooltipColorConfig.enableRarityColors() && stack != null) {
            if (rarityPalette) {
                // 与 Obscure Tooltips 的调色板同源：低 alpha 描边 + 高亮强调色，
                // 面板底色在用户配置的基础上轻微偏向稀有度色。
                EnumRarity rarity = stack.getRarity();
                if (rarity == EnumRarity.UNCOMMON) {
                    return palette(bg, 0x80FF9019, 0x6075350B, false);
                }
                if (rarity == EnumRarity.RARE) {
                    return palette(bg, 0x80BBBBCC, 0x60606070, false);
                }
                if (rarity == EnumRarity.EPIC) {
                    return palette(bg, 0x80FFBB00, 0x60AA5000, true);
                }
            }

            int rarityColor = TooltipColorConfig.getRarityColor(stack.getRarity());
            return palette(bg, rarityColor, rarityColor, false);
        }

        int borderColor = TooltipColorConfig.getBorderColor();
        return palette(bg, borderColor, borderColor, false);
    }

    private static TooltipColors palette(int background, int borderStart, int borderEnd, boolean shimmer) {
        // GT / 稀有度给的描边色常常是不透明的纯色，直接当"涂料"铺开会发死、像贴纸，
        // 这里把过高的不透明度压到 72%：色相保留，但不再糊满整块。
        borderStart = soften(borderStart);
        borderEnd = soften(borderEnd);
        int top = TooltipColors.mix(background, borderStart, 0.08F);
        int bottom = TooltipColors.mix(background, 0xFF000000, 0.20F);
        int accent = TooltipColors.mix(borderStart, 0xFFFFFFFF, 0.45F);
        return new TooltipColors(top, bottom, borderStart, borderEnd, accent, shimmer);
    }

    /** 把不透明度过高的颜色压到 0xB8，避免整块面板被纯色覆盖。 */
    private static int soften(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha <= 0xB8) return color;
        return (color & 0x00FFFFFF) | (0xB8 << 24);
    }

    public static TooltipColors getDefaultTooltipColors() {
        return new TooltipColors(
                DEFAULT_BACKGROUND_COLOR,
                DEFAULT_BORDER_START_COLOR,
                DEFAULT_BORDER_END_COLOR
        );
    }

    public static int getItemNameColor(String itemName) {
        return 0xFFFFFF;
    }
}
