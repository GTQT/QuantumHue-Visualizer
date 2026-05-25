package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TooltipAnimation {

    private TooltipLayout startLayout = null;
    private long animationStartTime = 0;
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
            this.animationStartTime = System.currentTimeMillis();
            this.isAnimating = true;
            this.currentProgress = 0f;
        }
    }

    public void stopAnimation() {
        isAnimating = false;
        startLayout = null;
        currentProgress = 1f;
    }

    public TooltipLayout getAnimatedLayout(TooltipLayout targetLayout) {
        if (!isAnimating || startLayout == null || !QuantumHueConfig.tooltip_animation.enabled) {
            return targetLayout;
        }

        int duration = QuantumHueConfig.tooltip_animation.duration;
        long elapsed = System.currentTimeMillis() - animationStartTime;
        float rawProgress = Math.min(1.0f, (float) elapsed / duration);

        currentProgress = applyEasing(rawProgress);

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

    private float applyEasing(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}