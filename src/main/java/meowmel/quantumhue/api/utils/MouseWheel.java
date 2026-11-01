package meowmel.quantumhue.api.utils;

/**
 * 滚轮增量归一化。
 *
 * <p>同样一格滚轮，两套 LWJGL 给出的 {@code Mouse.getEventDWheel()} 完全不同：
 *
 * <ul>
 *   <li>LWJGL 2（Forge 正式环境）：一格 {@code ±120}，是原始设备增量；</li>
 *   <li>LWJGL 3 / Cleanroom 的 lwjglx 兼容层：一格 {@code ±1}，在 shim 的
 *       {@code addWheelEvent} 里就已经换算成“格数”了。</li>
 * </ul>
 *
 * <p>于是按 {@code wheel / 120} 归一化的代码在 Cleanroom 里得到 0.008，滚轮等于没反应；
 * 按 {@code wheel * 像素} 处理的代码则慢一百多倍。统一走这里，两边行为才一致。
 */
public final class MouseWheel {

    /** LWJGL 2 里一格滚轮的原始增量。 */
    private static final int LWJGL2_NOTCH = 120;

    private MouseWheel() {}

    /** 把 {@code Mouse.getEventDWheel()} 的返回值统一成“格数”（向上为正）。 */
    public static float notches(int eventDWheel) {
        if (eventDWheel == 0) {
            return 0f;
        }
        // 一次事件里可能挤进好几格；只有达到 LWJGL2 的量级才做除法，
        // 免得把 lwjglx 的 ±2、±3 误当成 0
        if (Math.abs(eventDWheel) >= LWJGL2_NOTCH) {
            return eventDWheel / (float) LWJGL2_NOTCH;
        }
        return eventDWheel;
    }

    /** 按“每格多少像素”换算成位移。{@code perNotch} 用 LWJGL2 时代的数值即可。 */
    public static float pixels(int eventDWheel, float perNotch) {
        return notches(eventDWheel) * perNotch;
    }
}
