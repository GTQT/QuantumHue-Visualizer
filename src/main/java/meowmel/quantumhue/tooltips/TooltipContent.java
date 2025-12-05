package meowmel.quantumhue.tooltips;

import java.util.ArrayList;
import java.util.List;

// ========== 内部数据类 ==========
public class TooltipContent {
    final String itemName;
    final String modName;
    final List<String> remainingLines;
    List<String> currentPageLines = new ArrayList<>(); // 当前页显示的内容
    boolean needsPagination = false; // 是否需要分页
    int totalPages = 1; // 总页数
    int currentPage = 0; // 当前页码 (0-indexed)
    int maxLinesPerPage = 0; // 每页最大行数

    TooltipContent(String itemName, String modName, List<String> remainingLines) {
        this.itemName = itemName;
        this.modName = modName;
        this.remainingLines = remainingLines;
    }

    boolean hasModName() {
        return modName != null && !modName.isEmpty();
    }
}