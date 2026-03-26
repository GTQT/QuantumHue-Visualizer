package meowmel.quantumhue.tooltips;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

public class TooltipContentExtractor {

    private final ModInfoHelper modInfoHelper = new ModInfoHelper();

    public TooltipContent extractTooltipContent(List<String> originalLines, ItemStack stack) {
        String itemName = originalLines.isEmpty() ? "" : originalLines.get(0);
        String detectedModName = modInfoHelper.getModName(stack);

        List<String> remainingLines = new ArrayList<>();
        boolean modNameFound = false;

        for (int i = 1; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            String unformatted = TextFormatting.getTextWithoutFormattingCodes(line);

            if (line.startsWith(TextFormatting.YELLOW.toString()) ||
                    (detectedModName != null && detectedModName.equals(unformatted))) {
                modNameFound = true;
                continue;
            }
            remainingLines.add(line);
        }

        String modName = modNameFound ? detectedModName : null;
        if (modName == null) modName = detectedModName;

        return new TooltipContent(itemName, modName, remainingLines);
    }
}