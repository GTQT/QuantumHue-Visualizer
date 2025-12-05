package meowmel.quantumhue.api.utils;

import net.minecraft.item.ItemStack;

public class TranslationUtils {
    public static String getTableBarNameByItemStack(ItemStack stack){
        if(stack.getItem().getCreativeTab()==null)return  "";
        String tableBar= "itemGroup."+stack.getItem().getCreativeTab().getTabLabel();
        return  net.minecraft.client.resources.I18n.format(tableBar);
    }

}
