package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.text.TooltipTextLine;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.List;

public class TooltipContent {
    public final String itemName;
    public final String modName;
    public final List<TooltipTextLine> remainingLines;
    public List<TooltipTextLine> currentPageLines = new ArrayList<>();
    public boolean needsPagination = false;
    int totalPages = 1;
    int currentPage = 0;
    int maxLinesPerPage = 0;

    public int getCurrentPage() {
        return currentPage;
    }

    // Thaumcraft要素支持
    AspectList aspects = null;
    boolean showAspects = false;

    // AppleCore要素支持
    AppleSkinIntegration.FoodInfo foodInfo = null;
    boolean showFoodInfo = false;

    TooltipContent(String itemName, String modName, List<TooltipTextLine> remainingLines) {
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

    /**
     * 将当前页的字符串行转换为组件行列表。
     * Thaumcraft 要素占位行会被标记为 ASPECT_SPACER 类型。
     */
    public List<TooltipLine> buildComponentLines() {
        List<TooltipLine> result = new ArrayList<>();
        for (TooltipTextLine line : currentPageLines) {
            if (TooltipLine.isAspectSpacer(line.text)) {
                result.add(TooltipLine.aspectSpacer());
            } else {
                result.add(TooltipLine.text(line));
            }
        }
        return result;
    }
}
