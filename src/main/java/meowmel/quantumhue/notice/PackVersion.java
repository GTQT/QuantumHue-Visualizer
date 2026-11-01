package meowmel.quantumhue.notice;

import meowmel.quantumhue.config.PageConfigs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 「当前版本」（整合包版本）的解析。
 *
 * <p>优先读 {@code meowmel.gtqtcore.GTQTCore.VERSION}：整合包版本由核心模组发布，装了 GTQTCore 就以它为准。
 * 没装 GTQTCore（或取不到字段）时才回退到经 API 注册的版本配置（{@link PageConfigs#version()}）。
 *
 * <p>用反射而不是编译期依赖：GTQTCore 是可选模组，把它的类写进依赖会让本模组在没装它时直接
 * {@code NoClassDefFoundError}。
 */
public final class PackVersion {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Version");

    /** GTQTCore 的主类，其 {@code VERSION} 常量就是整合包版本。 */
    private static final String CORE_CLASS = "meowmel.gtqtcore.GTQTCore";
    private static final String CORE_FIELD = "VERSION";

    /** 探测结果缓存：一次运行里整合包版本不会变，失败也没必要反复试。 */
    private static volatile boolean probed;
    private static volatile String coreVersion = "";

    private PackVersion() {}

    /** 当前整合包版本：核心模组优先，未安装时取经 API 注册的版本配置（内置兜底 {@code 1.0.0}）；两处都没有则返回空串。 */
    public static String current() {
        String core = coreVersion();
        if (!core.isEmpty()) {
            return core;
        }
        try {
            return PageConfigs.version().resolved();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 当前版本是否来自 GTQTCore（供界面/日志说明来源）。 */
    public static boolean fromCore() {
        return !coreVersion().isEmpty();
    }

    private static String coreVersion() {
        if (probed) {
            return coreVersion;
        }
        synchronized (PackVersion.class) {
            if (!probed) {
                coreVersion = readCoreVersion();
                probed = true;
                if (coreVersion.isEmpty()) {
                    LOGGER.debug("[Notice] 未找到 GTQTCore.VERSION，当前版本回退到 API 注册的版本配置");
                } else {
                    LOGGER.info("[Notice] 当前整合包版本取自 GTQTCore：{}", coreVersion);
                }
            }
            return coreVersion;
        }
    }

    private static String readCoreVersion() {
        // 1.12.2 里所有模组共用一个 ModClassLoader，但这里仍依次试一遍常见加载器，
        // 免得将来启用类加载隔离时整个功能静默失效
        ClassLoader[] candidates = {
                PackVersion.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : candidates) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> core = Class.forName(CORE_CLASS, true, loader);
                Object value = core.getField(CORE_FIELD).get(null);
                if (value instanceof String) {
                    String text = ((String) value).trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            } catch (ClassNotFoundException absent) {
                // 没装 GTQTCore，换下一个加载器试
            } catch (Throwable failure) {
                LOGGER.warn("[Notice] 读取 GTQTCore.VERSION 失败", failure);
            }
        }
        return "";
    }
}
