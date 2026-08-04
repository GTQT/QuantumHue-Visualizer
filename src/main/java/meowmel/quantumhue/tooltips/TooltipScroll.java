package meowmel.quantumhue.tooltips;

import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tooltip 滚轮滚动状态管理，参考 obscure-tooltips 的 TooltipScroll 设计。
 * 当 tooltip 内容超过屏幕可见区域时，支持鼠标滚轮平滑滚动。
 *
 * <p>使用方式：
 * <ol>
 *   <li>每帧调用 {@link #update(int, int)} 传入内容高度和可见区域高度</li>
 *   <li>鼠标滚轮事件调用 {@link #onInput(int)}（+1=向下, -1=向上）</li>
 *   <li>渲染时从 {@link #getScroll()} 获取当前偏移量</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public class TooltipScroll {

    private static float scroll;
    private static float startScroll;
    private static float endScroll;
    private static int maxScroll;
    private static boolean active;
    private static long lastInputTime;

    private static final int SCROLL_SPEED = 5;
    private static final int EASE_DURATION_MS = 100;

    /** 当前滚动偏移量（像素），渲染时从内容 Y 坐标中减去此值 */
    public static float getScroll() {
        return scroll;
    }

    /** 滚动是否激活（内容高度超过可见区域） */
    public static boolean isActive() {
        return active;
    }

    /** 重置所有滚动状态 */
    public static void reset() {
        scroll = 0;
        startScroll = 0;
        endScroll = 0;
        maxScroll = 0;
        active = false;
        lastInputTime = 0;
    }

    /**
     * 处理鼠标滚轮输入。
     *
     * @param direction 滚动方向（+1 = 向下滚动内容, -1 = 向上滚动内容）
     */
    public static void onInput(int direction) {
        if (!active) return;
        float target = endScroll + direction * SCROLL_SPEED;
        endScroll = MathHelper.clamp(target, 0, maxScroll);
        startScroll = scroll;
        lastInputTime = System.currentTimeMillis();
    }

    /**
     * 每帧更新滚动状态——计算缓动动画。
     *
     * @param totalContentHeight 全部内容的像素高度
     * @param visibleHeight      tooltip 可见区域的高度
     */
    public static void update(int totalContentHeight, int visibleHeight) {
        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        active = maxScroll > 0;

        if (!active) {
            scroll = 0;
            startScroll = 0;
            endScroll = 0;
            return;
        }

        long elapsed = System.currentTimeMillis() - lastInputTime;
        float rawProgress = Math.min(1.0f, (float) elapsed / EASE_DURATION_MS);

        // EASE_OUT_CUBIC
        float eased = 1.0f - (1.0f - rawProgress) * (1.0f - rawProgress) * (1.0f - rawProgress);
        scroll = startScroll + (endScroll - startScroll) * eased;

        if (rawProgress >= 1.0f) {
            scroll = MathHelper.clamp(scroll, 0, maxScroll);
        }
    }
}
