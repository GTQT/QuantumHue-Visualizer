package meowmel.quantumhue.market;

import meowmel.quantumhue.QuantumHue;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Method;

/**
 * 货币系统的反射桥。
 *
 * <p>商店本身是 QuantumHue 的一部分，而钱来自整合包里的 FTB Money。直接把 FTB Money 变成硬依赖会让
 * 这个纯界面模组在没装它的环境里启动失败，所以这里只按名字查找两个静态方法，查不到就退化成
 * “没有经济系统”：界面照常打开，只是余额为 0、购买被拒绝。
 *
 * <p>方法句柄只在第一次使用时解析并缓存；解析失败也会缓存，避免每次点击都吃一次异常。
 */
public final class EconomyBridge {

    private static final String MONEY_CLASS = "com.feed_the_beast.mods.money.FTBMoney";

    private static volatile boolean resolved;
    private static volatile Method getter;
    private static volatile Method setter;

    private EconomyBridge() {}

    /** 货币系统是否可用。 */
    public static boolean available() {
        resolve();
        return getter != null;
    }

    /** 读取余额；经济系统缺失时返回 0。 */
    public static long get(EntityPlayer player) {
        if (player == null) {
            return 0L;
        }
        resolve();
        Method method = getter;
        if (method == null) {
            return 0L;
        }
        try {
            Object value = method.invoke(null, player);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Throwable failure) {
            QuantumHue.LOGGER.warn("[Shop] 读取余额失败: {}", failure.toString());
            return 0L;
        }
    }

    /** 写入余额；经济系统缺失或调用失败时返回 {@code false}。 */
    public static boolean set(EntityPlayer player, long amount) {
        if (player == null) {
            return false;
        }
        resolve();
        Method method = setter;
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, player, amount);
            return true;
        } catch (Throwable failure) {
            QuantumHue.LOGGER.warn("[Shop] 写入余额失败: {}", failure.toString());
            return false;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (EconomyBridge.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                Class<?> money = Class.forName(MONEY_CLASS);
                getter = find(money, "getMoney", EntityPlayer.class);
                setter = find(money, "setMoney", EntityPlayer.class, long.class);
                if (getter != null && setter != null) {
                    QuantumHue.LOGGER.info("[Shop] 已连接货币系统 {}", MONEY_CLASS);
                } else {
                    QuantumHue.LOGGER.warn("[Shop] {} 存在但缺少 getMoney/setMoney，商店将只读", MONEY_CLASS);
                }
            } catch (Throwable missing) {
                QuantumHue.LOGGER.info("[Shop] 未找到 FTB Money，商店购买功能将不可用");
            }
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
