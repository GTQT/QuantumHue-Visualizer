package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.text.TooltipTextLine;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份 tooltip 的解析结果：标题、模组名、正文行，以及分页与附加信息的状态。
 *
 * <p>由 {@link TooltipContentExtractor} 创建；页码等可变字段写在实例上，
 * 同一物品的内容重建时由 {@link TooltipState} 负责把旧页码带过来。</p>
 */
public class TooltipContent {

    /** 物品名（含数量前缀与颜色码）。 */
    public final String itemName;

    /** 模组名，可能为 null。 */
    public final String modName;

    /** 去掉标题与模组名之后的正文行。 */
    public final List<TooltipTextLine> remainingLines;

    /** 当前页要绘制的正文行。 */
    public List<TooltipTextLine> currentPageLines = new ArrayList<>();

    /** 正文是否超过一页。 */
    public boolean needsPagination = false;

    int totalPages = 1;
    int currentPage = 0;
    int maxLinesPerPage = 0;

    /** 打字机时钟的稳定身份（物品唯一 id），由 {@link TooltipState} 写入。 */
    public String cacheKey;

    /** Thaumcraft 要素信息，未安装时保持 null。 */
    AspectList aspects = null;
    boolean showAspects = false;

    /** AppleSkin 食物信息，未安装时保持 null。 */
    AppleSkinIntegration.FoodInfo foodInfo = null;
    boolean showFoodInfo = false;

    TooltipContent(String itemName, String modName, List<TooltipTextLine> remainingLines) {
        this.itemName = itemName;
        this.modName = modName;
        this.remainingLines = remainingLines;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /** 直接把全部正文作为当前页（装备对比用）：对比 tooltip 不分页、不显示分页提示。 */
    public void showAllLines() {
        this.currentPage = 0;
        this.totalPages = 1;
        this.needsPagination = false;
        this.currentPageLines = remainingLines;
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
     * 把当前页的文本行转成组件行；Thaumcraft 要素占位行标记为 {@code ASPECT_SPACER}。
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
