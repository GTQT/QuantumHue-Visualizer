package meowmel.quantumhue.tooltips;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.List;

public class ModInfoHelper {

    public String getModName(ItemStack itemStack) {
        if (itemStack.isEmpty()) return null;

        String modId = itemStack.getItem().getCreatorModId(itemStack);
        if (modId == null) return null;

        ModContainer modContainer = Loader.instance().getIndexedModList().get(modId);
        return modContainer != null ? modContainer.getName() : null;
    }

    public boolean isModNameAlreadyPresent(List<String> tooltip, String modName) {
        if (tooltip.size() <= 1) return false;

        String lastLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(tooltip.size() - 1));
        return modName.equals(lastLine);
    }
}