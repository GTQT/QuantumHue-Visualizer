package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.gregtech.GregtechIntegration;
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

        return new TooltipColors(bg, borderColor, borderColor);
    }

    public static TooltipColors getTooltipColors(ItemStack stack) {
        if (!TooltipColorConfig.isEnabled()) {
            return getDefaultTooltipColors();
        }

        int bg = TooltipColorConfig.getBackgroundColor();
        int borderStart;
        int borderEnd;

        if(isModLoaded("gregtech")){
            int color = GregtechIntegration.getColor(stack);
            if(color != -1){
                return new TooltipColors(bg, color, color);
            }
        }
        if (TooltipColorConfig.enableRarityColors() && stack != null) {
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