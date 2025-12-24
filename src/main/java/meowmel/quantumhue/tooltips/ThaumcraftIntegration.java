package meowmel.quantumhue.tooltips;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

public class ThaumcraftIntegration {
    private static boolean isThaumcraftLoaded = false;

    static {
        // 检查Thaumcraft是否加载
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
            // 直接调用Thaumcraft API
            return ThaumcraftCraftingManager.getObjectTags(stack);
        } catch (NoClassDefFoundError | Exception e) {
            // 如果API不匹配或出现其他问题
            isThaumcraftLoaded = false;
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
            // 如果配置类不匹配
            isThaumcraftLoaded = false;
            System.out.println("[QuantumHue] Thaumcraft config access failed: " + e.getMessage());
            return false;
        }
    }
}