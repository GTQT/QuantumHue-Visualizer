package meowmel.quantumhue.notice;

/**
 * 当前整合包版本的本地配置（{@code config/quantumhue/version.json}）。
 *
 * <p>它只是<b>兜底</b>：装了 GTQTCore 时，当前版本直接取它的 {@code VERSION} 字段（见 {@link PackVersion}），
 * 本文件会被忽略；只有没装核心模组时才读这里的值。
 *
 * <p>这里存的是<b>整合包</b>的版本号，不是模组 jar 的版本。代码里不给默认值，留空就是“版本未知”：
 * 此时页面不显示版本号，也不做任何更新比较。
 */
public class VersionConfig {

    public String _comment = "当前整合包版本号（例如 1.9.0）。装了 GTQTCore 时以它的 VERSION 为准、本文件被忽略；"
            + "没装时才读这里。留空表示版本未知，此时不显示版本号也不提示更新。与模组自身的版本无关。";

    /** 当前整合包版本号；留空表示未配置。 */
    public String version = "";

    /** 去掉首尾空白的版本号；未配置时返回空串。 */
    public String resolved() {
        return version == null ? "" : version.trim();
    }

    /** 是否填了版本号。 */
    public boolean isConfigured() {
        return !resolved().isEmpty();
    }
}
