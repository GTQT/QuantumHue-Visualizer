package meowmel.quantumhue.chat;

import net.minecraft.util.math.MathHelper;

/**
 * 动画工具 — 缓动函数与插值
 */
public final class ChatAnimation {

    private ChatAnimation() {}

    /** easeOutCubic: 1 - (1-t)^3 */
    public static float easeOutCubic(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    /** 平滑趋近目标值 */
    public static float lerpTo(float current, float target, float speed, float snapThreshold) {
        float next = current + (target - current) * speed;
        if (Math.abs(next - target) < snapThreshold) return target;
        return next;
    }

    /** 淡入: ticks/duration → alpha (0-255) */
    public static int fadeIn(int ticks, int duration) {
        if (duration <= 0 || ticks >= duration) return 255;
        return ticks * 255 / duration;
    }

    /** 淡出: ticks/duration → alpha (255-0) */
    public static int fadeOut(int ticks, int duration) {
        if (duration <= 0 || ticks >= duration) return 0;
        return (duration - ticks) * 255 / duration;
    }

    /** 动画进度 (打开/关闭) */
    public static float progress(long startMs, int durationMs, boolean closing) {
        long elapsed = System.currentTimeMillis() - startMs;
        float t = MathHelper.clamp((float) elapsed / durationMs, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return easeOutCubic(t);
    }
}
