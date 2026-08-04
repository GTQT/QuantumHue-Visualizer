package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.List;

public class TooltipContent {
    public final String itemName;
    public final String modName;
    public final List<String> remainingLines;
    public List<String> currentPageLines = new ArrayList<>();
    public boolean needsPagination = false;
    int totalPages = 1;
    int currentPage = 0;
    int maxLinesPerPage = 0;

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

    /**
     * 将当前页的字符串行转换为组件行列表。
     * Thaumcraft 要素占位行会被标记为 ASPECT_SPACER 类型。
     */
    public List<TooltipLine> buildComponentLines() {
        List<TooltipLine> result = new ArrayList<>();
        for (String line : currentPageLines) {
            if (TooltipLine.isAspectSpacer(line)) {
                result.add(TooltipLine.aspectSpacer());
            } else {
                result.add(TooltipLine.text(line));
            }
        }
        return result;
    }
}