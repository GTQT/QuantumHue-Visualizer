package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.List;

public class TooltipContent {
    public final String itemName;
    public final String modName;
    public final List<String> remainingLines;

    /** 全部换行后的文本行（不再分页截取，由滚轮滚动处理溢出） */
    public List<String> wrappedLines = new ArrayList<>();

    /** 是否需要滚轮滚动（内容高度超过可见区域） */
    public boolean needsScroll = false;

    /** 全部内容的像素高度（含文本 + 要素 + 食物信息等额外区域） */
    public int totalContentHeight = 0;

    // Thaumcraft要素支持
    AspectList aspects = null;
    boolean showAspects = false;

    // AppleCore要素支持
    AppleSkinIntegration.FoodInfo foodInfo = null;
    boolean showFoodInfo = false;

    TooltipContent(String itemName, String modName, List<String> remainingLines) {
        this.itemName = itemName;
        this.modName = modName;
        this.remainingLines = remainingLines;
    }

    boolean hasModName() {
        return modName != null && !modName.isEmpty();
    }

    boolean shouldShowAspects() {
        return aspects != null && showAspects;
    }

    boolean shouldShowFoodInfo() {
        return foodInfo != null && showFoodInfo;
    }
}