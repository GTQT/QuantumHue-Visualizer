package meowmel.quantumhue.notice;

import meowmel.quantumhue.config.PageConfigs;

/**
 * 「当前版本」（整合包版本）的统一读取入口。
 *
 * <p>版本完全来自外部经开放 API（{@code QuantumHuePageApi.registerPackVersion}）注册进来的配置，
 * 存在 {@link PageConfigs#version()}；本类只负责读取，不再反射探测 {@code GTQTCore.VERSION}。
 * GTQTCore 这类核心模组会主动把自己的 {@code VERSION} 经 API 注册，因此来源不变，只是换成了显式注册。
 */
public final class PackVersion {

    private PackVersion() {}

    /** 当前整合包版本：取 API 注册的配置值（未注册时为内置默认）；读取异常时返回空串，视为版本未知。 */
    public static String current() {
        try {
            return PageConfigs.version().resolved();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
