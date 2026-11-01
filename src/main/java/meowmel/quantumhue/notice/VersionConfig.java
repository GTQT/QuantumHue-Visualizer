package meowmel.quantumhue.notice;

import meowmel.quantumhue.config.PageConfigs;

/**
 * 当前整合包版本的配置模型，由外部通过对外开放 API 注册（见 {@code QuantumHuePageApi}），不再落盘。
 *
 * <p>版本只有一个来源：外部经 API 注册、存在 {@link PageConfigs#version()} 里的值，
 * {@link PackVersion} 不再反射探测其它模组（如 GTQTCore）的字段。
 *
 * <p>这里存的是<b>整合包</b>的版本号，不是模组 jar 的版本。唯一的代码默认值是 {@code 1.0.0}；
 * 注册成空串表示“版本未知”：此时页面不显示版本号，也不做任何更新比较。
 */
public class VersionConfig {

    /** 内置兜底的整合包版本号；外部经 API 注册后会被覆盖。 */
    public static final String DEFAULT_VERSION = "1.0.0";

    /** 当前整合包版本号；空串表示未配置。 */
    public String version = DEFAULT_VERSION;

    /** 便捷构造：直接以给定版本号创建配置。 */
    public static VersionConfig of(String version) {
        VersionConfig config = new VersionConfig();
        config.version = version;
        return config;
    }

    /** 去掉首尾空白的版本号；未配置时返回空串。 */
    public String resolved() {
        return version == null ? "" : version.trim();
    }

    /** 是否填了版本号。 */
    public boolean isConfigured() {
        return !resolved().isEmpty();
    }
}
