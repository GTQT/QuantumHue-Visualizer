package meowmel.quantumhue.notice;

/**
 * 远程版本状态。
 *
 * <p>运行时不再联网：版本号与更新日志来自启动阶段缓存的 {@link UpdateFeed}。
 * 需要刷新时执行 {@code /quantumhue refresh}。
 */
public final class VersionState {

    public enum Status {
        /** 还没有可用的缓存。 */
        IDLE,
        /** 缓存里有远程版本信息。 */
        READY,
        /** 缓存为空（首次启动离线、拉取失败等）。 */
        FAILED
    }

    private VersionState() {}

    /** 兼容旧调用：数据由启动预取写入内存，不再读盘/联网。 */
    public static void precheck() {
        // no-op
    }

    public static void loadCached() {
        // no-op：UpdateFeedStore 已由 PageBootstrap 填充
    }

    public static Status status() {
        return UpdateFeedStore.hasData() ? Status.READY : Status.FAILED;
    }

    public static String remoteVersion() {
        return UpdateFeedStore.feed().latest;
    }

    public static boolean isChecked() {
        return UpdateFeedStore.hasData() && remoteVersion() != null && !remoteVersion().isEmpty();
    }

    /** 远程版本是否比本地新；当前整合包版本未配置时无法比较，一律返回 {@code false}。 */
    public static boolean hasNewer() {
        String current = currentVersion();
        return isChecked() && !current.isEmpty()
                && VersionUtil.compare(remoteVersion(), current) > 0;
    }

    /**
     * 公告页与聊天栏使用的“当前版本”，即整合包版本。
     *
     * <p>装了 GTQTCore 时取它的 {@code VERSION} 字段，没装才取经 API 注册的版本配置（内置兜底 {@code 1.0.0}）；
     * 两处都没有就是空串，不回退到模组版本。
     */
    public static String currentVersion() {
        return PackVersion.current();
    }

    /** 是否配了当前整合包版本；没配时不做版本比较，也不显示版本号。 */
    public static boolean hasCurrentVersion() {
        return !currentVersion().isEmpty();
    }

    /**
     * 兼容旧调用：没有异步过程了，直接执行回调。
     */
    public static void whenSettled(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    public static void reset() {
        // 运行期不缓存，reset 不再清空内存中的更新信息
    }
}
