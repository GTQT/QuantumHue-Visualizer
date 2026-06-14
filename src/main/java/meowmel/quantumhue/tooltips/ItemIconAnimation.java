package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 物品图标的果冻弹出动画。
 * 当鼠标切换到不同物品时，图标从略小的尺寸弹性弹出、
 * 微量过冲后回弹稳定到正常大小，提供类似果冻/弹簧的视觉反馈。
 *
 * <p>动画模型：阻尼正弦振荡
 * <pre>
 *   scale(t) = baseline(t) + spring(t)
 *   baseline(t) = 0.7 + 0.3 × (1 − e^(−12t))
 *   spring(t)   = A × e^(−6t) × sin(16t)
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class ItemIconAnimation {

    private long startTime = 0;
    private boolean animating = false;

    /** 动画是否正在播放 */
    public boolean isAnimating() {
        return animating;
    }

    /** 触发一次果冻弹出动画。如果配置中禁用了该功能则无操作。 */
    public void trigger() {
        if (!QuantumHueConfig.tooltip_animation.icon_pop_enabled) return;
        this.startTime = System.currentTimeMillis();
        this.animating = true;
    }

    /** 立即停止动画，重置到正常大小。 */
    public void stop() {
        animating = false;
    }

    /**
     * 获取当前动画对应的缩放因子。
     *
     * @return 缩放因子（1.0 = 正常大小）。动画未激活时返回 1.0。
     */
    public float getScale() {
        if (!animating) return 1.0f;

        int duration = QuantumHueConfig.tooltip_animation.icon_pop_duration;
        float t = (System.currentTimeMillis() - startTime) / (float) duration;

        if (t >= 1.0f) {
            animating = false;
            return 1.0f;
        }

        float strength = (float) QuantumHueConfig.tooltip_animation.icon_pop_strength;
        return calculateJellyScale(t, strength);
    }

    /**
     * 果冻弹性缩放的核心公式。
     *
     * @param t        归一化时间 [0, 1]
     * @param strength 用户配置的力度 [0, 1]
     * @return 当前缩放因子
     */
    private float calculateJellyScale(float t, float strength) {
        // 阻尼弹簧参数
        double damping = 6.0;
        double frequency = 16.0;
        double amplitude = 0.30 * strength;

        // 基线：从 0.7 快速上升到 1.0
        double baseline = 0.7 + 0.3 * (1.0 - Math.exp(-t * 12.0));

        // 弹簧分量：阻尼正弦振荡
        double spring = amplitude * Math.exp(-damping * t) * Math.sin(frequency * t);

        return (float) (baseline + spring);
    }
}
