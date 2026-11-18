package meowmel.quantumhue.tooltips;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 按屏幕可用高度把正文切成若干页，并把 Ctrl+C / Ctrl+Z 的按键边沿转成页码变化。
 */
@SideOnly(Side.CLIENT)
public class PaginationHandler {

    /**
     * 计算分页信息；内容放得下时视为单页。
     *
     * @param content      解析后的 tooltip 内容，结果写回其分页字段
     * @param screenHeight 当前 GUI 逻辑高度
     * @param keyState     翻页按键的跨帧状态
     */
    public void calculatePagination(TooltipContent content, int screenHeight, KeyState keyState) {
        int fixedLineCount = 2;
        if (content.modName != null) fixedLineCount++;
        int fixedHeight = fixedLineCount * TooltipConstants.LINE_HEIGHT + TooltipConstants.TEXT_PADDING * 2;
        fixedHeight += 15;

        int maxHeight = (int) (screenHeight * TooltipConstants.MAX_SCREEN_HEIGHT_RATIO);
        int availableHeight = maxHeight - fixedHeight;

        if (availableHeight < TooltipConstants.LINE_HEIGHT || content.remainingLines.isEmpty()) {
            resetPagination(content);
            return;
        }

        int maxLinesPerPage = Math.max(1, availableHeight / TooltipConstants.LINE_HEIGHT);
        content.maxLinesPerPage = maxLinesPerPage;

        if (content.remainingLines.size() <= maxLinesPerPage) {
            resetPagination(content);
            return;
        }

        setupPagination(content, maxLinesPerPage, keyState);
    }

    private void resetPagination(TooltipContent content) {
        content.currentPageLines = new ArrayList<>(content.remainingLines);
        content.totalPages = 1;
        content.needsPagination = false;
        content.currentPage = 0;
    }

    private void setupPagination(TooltipContent content, int maxLinesPerPage, KeyState keyState) {
        content.needsPagination = true;
        content.totalPages = (int) Math.ceil((double) content.remainingLines.size() / maxLinesPerPage);
        // 页码挂在 content 上，随物品身份重建；这里只负责把越界的页码拉回范围内
        content.currentPage = Math.min(content.currentPage, content.totalPages - 1);

        long currentTime = System.currentTimeMillis();
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean cDown = Keyboard.isKeyDown(Keyboard.KEY_C);
        boolean zDown = Keyboard.isKeyDown(Keyboard.KEY_Z);

        boolean cPressedThisFrame = cDown && !keyState.wasCPressed;
        boolean zPressedThisFrame = zDown && !keyState.wasZPressed;

        keyState.wasCtrlPressed = ctrlDown;
        keyState.wasCPressed = cDown;
        keyState.wasZPressed = zDown;

        boolean canSwitch = (currentTime - keyState.lastSwitchTime) > KeyState.MIN_SWITCH_INTERVAL;

        if (ctrlDown && canSwitch) {
            if (cPressedThisFrame && !zDown) {
                content.currentPage = Math.min(content.currentPage + 1, content.totalPages - 1);
                keyState.lastSwitchTime = currentTime;
            } else if (zPressedThisFrame && !cDown) {
                content.currentPage = Math.max(content.currentPage - 1, 0);
                keyState.lastSwitchTime = currentTime;
            }
        }

        int startLine = content.currentPage * maxLinesPerPage;
        int endLine = Math.min(startLine + maxLinesPerPage, content.remainingLines.size());
        content.currentPageLines = content.remainingLines.subList(startLine, endLine);
    }
}
