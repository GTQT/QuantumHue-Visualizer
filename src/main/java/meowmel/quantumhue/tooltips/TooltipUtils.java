package meowmel.quantumhue.tooltips;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

public class TooltipUtils {

    public static String getItemUniqueId(ItemStack stack) {
        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(stack.getItem().getRegistryName());
        idBuilder.append("@").append(stack.getItemDamage());

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            idBuilder.append("#").append(nbt.hashCode());
        }

        return idBuilder.toString();
    }

    public static List<String> wrapTooltipText(List<String> lines, FontRenderer font, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            wrappedLines.addAll(font.listFormattedStringToWidth(line, maxWidth));
        }
        return wrappedLines;
    }

    public static List<String> wrapSimpleTooltipText(List<String> lines, FontRenderer font, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        int adjustedMaxWidth = maxWidth - TooltipConstants.MOUSE_OFFSET_X;
        for (String line : lines) {
            wrappedLines.addAll(font.listFormattedStringToWidth(line, adjustedMaxWidth));
        }
        return wrappedLines;
    }
}