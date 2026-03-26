package meowmel.quantumhue.tooltips.thaumcraft;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

public class ThaumcraftIntegration {

    private static final boolean isThaumcraftLoaded;

    static {
        isThaumcraftLoaded = Loader.isModLoaded("thaumcraft");
    }

    /**
     * 检查Thaumcraft是否可用
     */
    public static boolean isThaumcraftAvailable() {
        return isThaumcraftLoaded;
    }

    /**
     * 获取物品的要素列表
     */
    public static AspectList getAspects(ItemStack stack) {
        if (!isThaumcraftLoaded || stack.isEmpty()) {
            return null;
        }

        try {
            return ThaumcraftCraftingManager.getObjectTags(stack);
        } catch (NoClassDefFoundError | Exception e) {
            System.out.println("[QuantumHue] Thaumcraft integration disabled: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取是否应该在tooltip中默认显示要素
     */
    public static boolean shouldShowAspectsByDefault() {
        if (!isThaumcraftLoaded) {
            return false;
        }

        try {
            return ModConfig.CONFIG_GRAPHICS.showTags;
        } catch (NoClassDefFoundError | Exception e) {
            System.out.println("[QuantumHue] Thaumcraft config access failed: " + e.getMessage());
            return false;
        }
    }
}