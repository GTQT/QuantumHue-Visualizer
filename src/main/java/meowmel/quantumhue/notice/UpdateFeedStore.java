package meowmel.quantumhue.notice;

/**
 * 更新日志的内存视图。
 *
 * <p>没有本地缓存：数据由 {@code PageBootstrap} 在加载阶段/手动刷新时拉取并写入，
 * 页面只读这里。进程重启后重新从远程拉取。
 */
public final class UpdateFeedStore {

    private static volatile UpdateFeed feed = new UpdateFeed();

    private UpdateFeedStore() {}

    public static void set(UpdateFeed value) {
        feed = value == null ? new UpdateFeed() : value;
    }

    public static UpdateFeed feed() {
        return feed;
    }

    public static boolean hasData() {
        return feed != null && feed.latest != null && !feed.latest.isEmpty();
    }
}
