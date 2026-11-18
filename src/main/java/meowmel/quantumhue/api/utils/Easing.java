package meowmel.quantumhue.api.utils;

/**
 * 共享缓动函数。
 *
 * <p>tooltip 动画、翻页过渡等共用同一套曲线，时间基准由调用方提供，
 * 这里只负责把进度 t 映射成缓动后的进度。</p>
 */
public final class Easing {

    private Easing() {
    }

    /**
     * easeOutCubic：{@code 1 - (1 - t)^3}。
     *
     * @param t 线性进度，超出 [0, 1] 会被截断
     */
    public static float outCubic(float t) {
        float f = 1.0F - clamp01(t);
        return 1.0F - f * f * f;
    }

    private static float clamp01(float t) {
        if (t < 0.0F) return 0.0F;
        return Math.min(t, 1.0F);
    }
}
