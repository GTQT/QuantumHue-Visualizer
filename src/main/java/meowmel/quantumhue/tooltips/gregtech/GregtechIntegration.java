package meowmel.quantumhue.tooltips.gregtech;

import gregtech.api.items.materialitem.MetaPrefixItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.BlockMaterialBase;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public class GregtechIntegration {

    public static final int[] TIER_COLORS = {
            0xFF555555, 0xFFAAAAAA, 0xFF55FFFF, 0xFFFFAA00,
            0xFFAA00AA, 0xFF5555FF, 0xFFFF55FF, 0xFFFF5555,
            0xFF00AAAA, 0xFFAA0000, 0xFF00AA00, 0xFF006600,
            0xFFFFFF55, 0xFF5555FF, 0xFFFF5555,
    };

    public static int getColor(ItemStack stack) {
        if (stack.getItem() instanceof IGTTool tool){
            return 0xFF000000 | (tool.getToolMaterial(stack).getMaterialRGB() & 0x00FFFFFF);
        }
        Material material = extractMaterial(stack);
        if (material != null) {
            return 0xFF000000 | (material.getMaterialRGB() & 0x00FFFFFF);
        }
        MetaTileEntity mte = GTUtility.getMetaTileEntity(stack);
        if(mte instanceof ITieredMetaTileEntity t){
            int tier = t.getTier();
            return tier >= 0 && tier < TIER_COLORS.length ? TIER_COLORS[tier] : -1;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        if(block instanceof BlockMaterialBase bmb){
            return 0xFF000000 | (bmb.getGtMaterial(stack).getMaterialRGB() & 0x00FFFFFF);
        }
        if(block instanceof BlockMaterialPipe bmb){
            return 0xFF000000 | (bmb.getItemMaterial(stack).getMaterialRGB() & 0x00FFFFFF);
        }
        return -1;
    }

    private static Material extractMaterial(ItemStack stack) {
        Material mat = MetaPrefixItem.tryGetMaterial(stack);
        if (mat != null) return mat;
        UnificationEntry entry = OreDictUnifier.getUnificationEntry(stack);
        if (entry != null && entry.material != null) return entry.material;
        return null;
    }
}
