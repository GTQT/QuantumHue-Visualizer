package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.api.utils.Easing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TooltipAnimation {

    /** 单帧最大推进时间。卡顿/切窗口回来时不让动画一步跳到终点。 */
    private static final long MAX_FRAME_STEP_MS = 50L;

    private TooltipLayout startLayout = null;
    private long elapsedMs = 0L;
    private long lastUpdateMs = 0L;
    private boolean isAnimating = false;
    private float currentProgress = 1f;

    public boolean isAnimating() {
        return isAnimating;
    }

    public float getCurrentProgress() {
        return currentProgress;
    }

    public void startAnimation(TooltipLayout fromLayout) {
        if (fromLayout != null && QuantumHueConfig.tooltip_animation.enabled) {
            this.startLayout = fromLayout;
            this.elapsedMs = 0L;
            this.lastUpdateMs = System.currentTimeMillis();
            this.isAnimating = true;
            this.currentProgress = 0f;
        }
    }

    public void stopAnimation() {
        isAnimating = false;
        startLayout = null;
        elapsedMs = 0L;
        currentProgress = 1f;
    }

    public TooltipLayout getAnimatedLayout(TooltipLayout targetLayout) {
        if (!isAnimating || startLayout == null || !QuantumHueConfig.tooltip_animation.enabled) {
            return targetLayout;
        }

        int duration = Math.max(1, QuantumHueConfig.tooltip_animation.duration);
        long now = System.currentTimeMillis();
        elapsedMs += MathHelper.clamp(now - lastUpdateMs, 0L, MAX_FRAME_STEP_MS);
        lastUpdateMs = now;

        float rawProgress = Math.min(1.0f, elapsedMs / (float) duration);
        currentProgress = Easing.outCubic(rawProgress);

        if (rawProgress >= 1.0f) {
            stopAnimation();
            return targetLayout;
        }

        int x = lerp(startLayout.x, targetLayout.x, currentProgress);
        int y = lerp(startLayout.y, targetLayout.y, currentProgress);
        int width = lerp(startLayout.width, targetLayout.width, currentProgress);
        int height = lerp(startLayout.height, targetLayout.height, currentProgress);
        int separatorY = lerp(startLayout.separatorY, targetLayout.separatorY, currentProgress);
        int iconX = x + TooltipConstants.TEXT_PADDING;
        int iconY = y + TooltipConstants.TEXT_PADDING;

        return new TooltipLayout(x, y, width, height, separatorY, iconX, iconY);
    }

    private int lerp(int start, int end, float progress) {
        return MathHelper.floor(start + (end - start) * progress);
    }
}
