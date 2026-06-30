package meowmel.quantumhue.tooltips.comparison;

import meowmel.quantumhue.tooltips.TooltipLayout;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class ComparisonLayoutManager {

    private static final int GAP = 4;
    private static final int BADGE_OFFSET = ComparisonBadgeRenderer.BADGE_HEIGHT + 2;

    /**
     * 自动布局计算：根据主Tooltip相对鼠标的位置，智能选择对比Tooltip放置侧。
     * <ul>
     *   <li>鼠标在主Tooltip左侧 → 对比Tooltip放在主Tooltip左边（远离鼠标）</li>
     *   <li>鼠标在主Tooltip右侧 → 对比Tooltip放在主Tooltip右边（远离鼠标）</li>
     *   <li>首选侧空间不足时，尝试另一侧</li>
     *   <li>两侧都不够时，压缩并靠屏幕边缘放置</li>
     * </ul>
     *
     * @param primaryLayout    主Tooltip（悬停物品）的布局
     * @param comparisonLayouts 对比Tooltip列表
     * @param mouseX           鼠标X坐标
     * @param mouseY           鼠标Y坐标
     * @param screenWidth      屏幕宽度
     * @param screenHeight     屏幕高度
     * @return 调整后的对比Tooltip布局列表
     */
    public List<TooltipLayout> positionAll(TooltipLayout primaryLayout,
                                           List<TooltipLayout> comparisonLayouts,
                                           int mouseX, int mouseY,
                                           int screenWidth, int screenHeight) {
        if (comparisonLayouts.isEmpty()) {
            return comparisonLayouts;
        }

        // 计算所需总宽度
        int totalWidthNeeded = 0;
        for (TooltipLayout layout : comparisonLayouts) {
            totalWidthNeeded += layout.width + GAP;
        }
        totalWidthNeeded -= GAP;

        // 判断鼠标相对于主Tooltip的位置
        int primaryCenterX = primaryLayout.x + primaryLayout.width / 2;
        boolean mouseOnLeft = mouseX < primaryCenterX;

        // 可用空间
        int spaceLeft = primaryLayout.x - 2;  // 主Tooltip左侧可用空间
        int spaceRight = screenWidth - (primaryLayout.x + primaryLayout.width) - 2;  // 右侧

        // 首选侧：远离鼠标的一侧
        boolean preferLeft = mouseOnLeft;
        boolean preferRight = !mouseOnLeft;

        // 检查首选侧是否有足够空间
        if (preferLeft && spaceLeft >= totalWidthNeeded) {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.LEFT, screenWidth, screenHeight);
        }
        if (preferRight && spaceRight >= totalWidthNeeded) {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.RIGHT, screenWidth, screenHeight);
        }

        // 首选侧不够，尝试另一侧
        if (preferLeft && spaceRight >= totalWidthNeeded) {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.RIGHT, screenWidth, screenHeight);
        }
        if (preferRight && spaceLeft >= totalWidthNeeded) {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.LEFT, screenWidth, screenHeight);
        }

        // 两侧都不够 — 选空间更大的一侧放置
        if (spaceLeft >= spaceRight) {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.LEFT, screenWidth, screenHeight);
        } else {
            return layoutOnSide(primaryLayout, comparisonLayouts,
                    SideChoice.RIGHT, screenWidth, screenHeight);
        }
    }

    private enum SideChoice { LEFT, RIGHT }

    /**
     * 在指定侧排列对比Tooltip
     */
    private List<TooltipLayout> layoutOnSide(TooltipLayout primaryLayout,
                                             List<TooltipLayout> comparisonLayouts,
                                             SideChoice side,
                                             int screenWidth, int screenHeight) {
        List<TooltipLayout> result = new ArrayList<>();

        if (side == SideChoice.LEFT) {
            // 从主Tooltip左侧向左排列
            int currentX = primaryLayout.x - GAP;

            for (int i = comparisonLayouts.size() - 1; i >= 0; i--) {
                TooltipLayout original = comparisonLayouts.get(i);
                currentX -= original.width;

                TooltipLayout adjusted = createAdjustedLayout(original, currentX,
                        primaryLayout.y, screenHeight);
                result.add(0, adjusted);
                currentX -= GAP;
            }

            // 如果超出左边界，整体右移
            if (!result.isEmpty() && result.get(0).x < 2) {
                int shiftX = 2 - result.get(0).x;
                shiftAll(result, shiftX);
            }
        } else {
            // 从主Tooltip右侧向右排列
            int currentX = primaryLayout.x + primaryLayout.width + GAP;

            for (int i = 0; i < comparisonLayouts.size(); i++) {
                TooltipLayout original = comparisonLayouts.get(i);

                TooltipLayout adjusted = createAdjustedLayout(original, currentX,
                        primaryLayout.y, screenHeight);
                result.add(adjusted);
                currentX += original.width + GAP;
            }

            // 如果超出右边界，整体左移
            if (!result.isEmpty()) {
                TooltipLayout last = result.get(result.size() - 1);
                int rightEdge = last.x + last.width;
                if (rightEdge > screenWidth - 2) {
                    int shiftX = (screenWidth - 2) - rightEdge;
                    shiftAll(result, shiftX);
                }
            }
        }

        return result;
    }

    /**
     * 创建一个调整Y坐标后的布局副本
     */
    private TooltipLayout createAdjustedLayout(TooltipLayout original, int newX,
                                               int alignY, int screenHeight) {
        int alignedY = alignY;

        // 不超出屏幕底部
        if (alignedY + original.height + BADGE_OFFSET > screenHeight) {
            alignedY = screenHeight - original.height - BADGE_OFFSET - 2;
        }
        // 不超出屏幕顶部
        if (alignedY < BADGE_OFFSET + 2) {
            alignedY = BADGE_OFFSET + 2;
        }

        int iconX = newX + 4;
        int iconY = alignedY + 4;

        return new TooltipLayout(
                newX, alignedY,
                original.width, original.height,
                original.separatorY - original.y + alignedY,
                iconX, iconY
        );
    }

    /**
     * 将所有布局在X轴上整体平移
     */
    private void shiftAll(List<TooltipLayout> list, int shiftX) {
        for (int i = 0; i < list.size(); i++) {
            TooltipLayout original = list.get(i);
            list.set(i, new TooltipLayout(
                    original.x + shiftX,
                    original.y,
                    original.width,
                    original.height,
                    original.separatorY,
                    original.iconX + shiftX,
                    original.iconY
            ));
        }
    }
}
