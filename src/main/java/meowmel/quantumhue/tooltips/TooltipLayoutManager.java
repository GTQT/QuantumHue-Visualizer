package meowmel.quantumhue.tooltips;

import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderTooltipEvent;

import java.util.List;

public class TooltipLayoutManager {

    public TooltipLayout calculateLayout(TooltipContent content, RenderTooltipEvent.Pre event) {
        FontRenderer font = event.getFontRenderer();
        int screenWidth = event.getScreenWidth();
        int screenHeight = event.getScreenHeight();
        int mouseX = event.getX();
        int mouseY = event.getY();

        int leftWidth = calculateMaxWidth(content.wrappedLines, font);
        leftWidth = Math.min(leftWidth, TooltipConstants.TOOLTIP_MAX_WIDTH);

        int rightWidth = calculateRightWidth(content, font);
        rightWidth += TooltipConstants.ICON_AREA_WIDTH;

        int textPadding = TooltipConstants.TEXT_PADDING;
        int borderPadding = TooltipConstants.BORDER_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;

        int totalWidth = Math.max(leftWidth, rightWidth) + textPadding * 2;

        // 计算全部内容高度（不受分页限制）
        int fullContentHeight = calculateFullContentHeight(content, lineHeight, textPadding);
        content.totalContentHeight = fullContentHeight;

        // 限制 tooltip 可见区域高度
        int maxVisibleHeight = (int) (screenHeight * TooltipConstants.MAX_SCREEN_HEIGHT_RATIO);
        int visibleHeight = Math.min(fullContentHeight, maxVisibleHeight);

        // 判断是否需要滚动
        content.needsScroll = fullContentHeight > maxVisibleHeight;

        int x = calculateXPosition(mouseX, totalWidth, screenWidth, borderPadding);
        int y = calculateYPosition(mouseY, visibleHeight, screenHeight, borderPadding);

        int separatorY = y + textPadding + (content.modName != null ? 2 : 1) * lineHeight;
        int iconX = x + textPadding;
        int iconY = y + textPadding;

        return new TooltipLayout(x, y, totalWidth, visibleHeight, separatorY, iconX, iconY);
    }

    public TooltipLayout calculateSimpleLayout(List<String> wrappedLines, RenderTooltipEvent.Pre event) {
        FontRenderer font = event.getFontRenderer();
        int screenWidth = event.getScreenWidth();
        int screenHeight = event.getScreenHeight();
        int mouseX = event.getX();
        int mouseY = event.getY();

        int maxWidth = calculateMaxWidth(wrappedLines, font);
        int textPadding = TooltipConstants.TEXT_PADDING;
        int borderPadding = TooltipConstants.BORDER_PADDING;
        int lineHeight = TooltipConstants.LINE_HEIGHT;

        int totalWidth = maxWidth + textPadding * 2;
        int totalHeight = wrappedLines.size() * lineHeight + textPadding * 2;

        int x = adjustPosition(mouseX + TooltipConstants.MOUSE_OFFSET_X, totalWidth, screenWidth, borderPadding);
        int y = adjustPosition(mouseY + TooltipConstants.MOUSE_OFFSET_Y, totalHeight, screenHeight, borderPadding);

        return new TooltipLayout(x, y, totalWidth, totalHeight, 0, x + textPadding, y + textPadding);
    }

    private int calculateMaxWidth(List<String> lines, FontRenderer font) {
        int maxWidth = 0;
        for (String line : lines) {
            int width = font.getStringWidth(line);
            if (width > maxWidth) maxWidth = width;
        }
        return maxWidth;
    }

    private int calculateRightWidth(TooltipContent content, FontRenderer font) {
        int rightWidth = font.getStringWidth(content.itemName);
        if (content.modName != null) {
            int modWidth = font.getStringWidth(content.modName);
            if (modWidth > rightWidth) rightWidth = modWidth;
        }
        return rightWidth;
    }

    private int calculateFullContentHeight(TooltipContent content, int lineHeight, int textPadding) {
        int lineCount = 1;                                              // 物品名
        if (content.modName != null) lineCount++;                       // 模组名
        lineCount++;                                                     // 空行
        lineCount += content.wrappedLines.size();                       // 正文行
        if (content.showFoodInfo) lineCount += 2;                       // 食物信息

        int baseHeight = lineCount * lineHeight;
        int iconHeight = TooltipConstants.ICON_SIZE;
        int firstSectionHeight = (content.modName != null ? 2 : 1) * lineHeight;
        int heightAdjustment = Math.max(0, iconHeight - firstSectionHeight);

        return baseHeight + textPadding * 2 + heightAdjustment;
    }

    private int calculateXPosition(int mouseX, int totalWidth, int screenWidth, int borderPadding) {
        int spaceRight = screenWidth - mouseX;
        int spaceLeft = mouseX;
        int requiredSpace = totalWidth + TooltipConstants.MOUSE_OFFSET_X + borderPadding * 2;

        boolean preferRight = spaceRight >= requiredSpace || spaceLeft < requiredSpace;

        int x;
        if (preferRight) {
            x = mouseX + TooltipConstants.MOUSE_OFFSET_X;
            if (x + totalWidth + borderPadding > screenWidth) {
                x = screenWidth - totalWidth - borderPadding;
            }
        } else {
            x = mouseX - TooltipConstants.MOUSE_OFFSET_X - totalWidth;
            if (x < borderPadding) {
                x = borderPadding;
            }
        }
        return x;
    }

    private int calculateYPosition(int mouseY, int totalHeight, int screenHeight, int borderPadding) {
        int y = mouseY + TooltipConstants.MOUSE_OFFSET_Y;
        if (y + totalHeight + borderPadding > screenHeight) {
            y = mouseY - totalHeight - TooltipConstants.MOUSE_OFFSET_Y;
            if (y < borderPadding) {
                y = borderPadding;
            }
        }
        return y;
    }

    private int adjustPosition(int pos, int size, int screenLimit, int padding) {
        if (pos + size + padding > screenLimit) {
            pos = screenLimit - size - padding;
        }
        return Math.max(padding, pos);
    }
}