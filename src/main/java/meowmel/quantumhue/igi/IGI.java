package meowmel.quantumhue.igi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IGI（InGame Info）主API入口。
 * <p>
 * 使用示例：
 * <pre>
 * IGI.register()
 *     .pos(Alignment.TOP_LEFT)
 *     .offset(2, 2)
 *     .size(18)
 *     .info("FPS: ", new FpsInfo())
 *     .info("位置: ", new PlayerPosInfo())
 *     .builder();
 * </pre>
 */
public final class IGI {
    private static final List<HudGroup> groups = new ArrayList<>();
    private static boolean initialized = false;

    private IGI() {
    }

    /**
     * 创建一个新的HUD组构建器。
     *
     * @return InfoBuilder 实例
     */
    public static InfoBuilder register() {
        return new InfoBuilder();
    }

    /**
     * 内部方法：注册一个构建完成的HUD组。
     */
    static void registerGroup(HudGroup group) {
        groups.add(group);
    }

    /**
     * 获取所有已注册的HUD组（不可变视图）。
     */
    public static List<HudGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * 标记IGI系统已初始化（由HudRenderer调用）。
     */
    public static void markInitialized() {
        initialized = true;
    }

    /**
     * 检查IGI系统是否已初始化。
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 清除所有已注册的HUD组（用于重载等场景）。
     */
    public static void clearAll() {
        groups.clear();
        initialized = false;
    }
}
