package meowmel.quantumhue.api.utils;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.util.math.MathHelper;

/**
 * 平滑滚动的数值工具：滚动位置推进、越界回弹与边界夹取。
 */
public final class MathUtils {

    private MathUtils() {
    }

    /**
     * 把当前滚动位置向目标位置推进一帧。
     *
     * @param target   目标位置，会被就地修正到合法范围（允许回弹越界）
     * @param scroll   当前实际位置
     * @param maxScroll 内容可滚动的最大距离
     * @param delta    帧时间系数
     * @param start    滚动开始的系统时间（毫秒）
     * @param duration 滚动总时长（毫秒）
     */
    public static float handleScrollingPosition(float[] target, float scroll, float maxScroll,
                                                float delta, double start, double duration) {
        if (getBounceBackMultiplier() >= 0) {
            target[0] = clamp(target[0], maxScroll);
            if (target[0] < 0) {
                target[0] -= (float) (target[0] * (1 - getBounceBackMultiplier()) * delta / 3);
            } else if (target[0] > maxScroll) {
                target[0] = (float) ((target[0] - maxScroll) * (1 - (1 - getBounceBackMultiplier()) * delta / 3) + maxScroll);
            }
        } else {
            target[0] = clamp(target[0], maxScroll, 0);
        }

        if (Precision.almostEquals(scroll, target[0], Precision.FLOAT_EPSILON)) {
            return target[0];
        }
        double progress = Math.min((System.currentTimeMillis() - start) / duration * delta * 3, 1);
        return lerpTowards(scroll, target[0], progress);
    }

    private static double getBounceBackMultiplier() {
        return QuantumHueConfig.smoothScrolling.bounceBackMultiplier;
    }

    /** 线性插值；滚动距离很短，不需要缓动曲线。 */
    private static float lerpTowards(float start, float end, double amount) {
        return (float) (start + (end - start) * amount);
    }

    public static float clamp(float v, float maxScroll) {
        return clamp(v, maxScroll, 300);
    }

    public static float clamp(float v, float maxScroll, float clampExtension) {
        return MathHelper.clamp(v, -clampExtension, maxScroll + clampExtension);
    }

    private static final class Precision {

        private static final float FLOAT_EPSILON = 1e-3F;

        private static boolean almostEquals(float a, float b, float tolerance) {
            return Math.abs(a - b) <= tolerance;
        }
    }
}
