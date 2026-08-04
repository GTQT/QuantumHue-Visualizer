package meowmel.quantumhue.tooltips;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class PaginationHandler {

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
        content.currentPage = Math.min(AdvancedTooltipHandler.getCurrentPage(), content.totalPages - 1);

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
                AdvancedTooltipHandler.setCurrentPage(content.currentPage);
                keyState.lastSwitchTime = currentTime;
            } else if (zPressedThisFrame && !cDown) {
                content.currentPage = Math.max(content.currentPage - 1, 0);
                AdvancedTooltipHandler.setCurrentPage(content.currentPage);
                keyState.lastSwitchTime = currentTime;
            }
        }

        int startLine = content.currentPage * maxLinesPerPage;
        int endLine = Math.min(startLine + maxLinesPerPage, content.remainingLines.size());
        content.currentPageLines = content.remainingLines.subList(startLine, endLine);
    }
}
