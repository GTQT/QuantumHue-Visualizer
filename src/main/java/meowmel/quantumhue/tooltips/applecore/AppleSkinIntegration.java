package meowmel.quantumhue.tooltips.applecore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import squeek.appleskin.ModConfig;
import squeek.appleskin.helpers.FoodHelper;
import squeek.appleskin.helpers.KeyHelper;

@SideOnly(Side.CLIENT)
public class AppleSkinIntegration {
    private static final boolean isAppleSkinLoaded;

    static {
        isAppleSkinLoaded = Loader.isModLoaded("appleskin");
        if (isAppleSkinLoaded) {
            System.out.println("[QuantumHue] AppleSkin integration enabled");
        }
    }

    /**
     * 检查AppleSkin是否可用
     */
    public static boolean isAppleSkinAvailable() {
        return isAppleSkinLoaded;
    }

    /**
     * 检查物品是否为食物
     */
    public static boolean isFood(ItemStack stack) {
        return isAppleSkinLoaded && !stack.isEmpty() && FoodHelper.isFood(stack);
    }

    /**
     * 获取食物信息
     */
    public static FoodInfo getFoodInfo(ItemStack stack, EntityPlayer player) {
        if (!isAppleSkinLoaded || !isFood(stack)) {
            return null;
        }

        FoodHelper.BasicFoodValues defaultValues = FoodHelper.getDefaultFoodValues(stack);
        FoodHelper.BasicFoodValues modifiedValues = FoodHelper.getModifiedFoodValues(stack, player);

        return new FoodInfo(defaultValues, modifiedValues);
    }

    /**
     * 是否应该显示食物信息
     */
    public static boolean shouldShowFoodInfo() {
        if (!isAppleSkinLoaded) return false;

        // AppleSkin的显示逻辑：按住Shift或配置为始终显示
        boolean shiftKeyDown = KeyHelper.isShiftKeyDown();
        boolean alwaysShow = ModConfig.ALWAYS_SHOW_FOOD_VALUES_TOOLTIP;
        boolean showInTooltip = ModConfig.SHOW_FOOD_VALUES_IN_TOOLTIP;

        return (showInTooltip && shiftKeyDown) || alwaysShow;
    }

    /**
     * 食物信息数据类
     */
    public static class FoodInfo {
        public final FoodHelper.BasicFoodValues defaultValues;
        public final FoodHelper.BasicFoodValues modifiedValues;
        public final boolean hasChanged;

        public FoodInfo(FoodHelper.BasicFoodValues defaultValues, FoodHelper.BasicFoodValues modifiedValues) {
            this.defaultValues = defaultValues;
            this.modifiedValues = modifiedValues;
            this.hasChanged = !defaultValues.equals(modifiedValues);
        }

        public boolean hasHunger() {
            return defaultValues.hunger != 0 || modifiedValues.hunger != 0;
        }
    }
}