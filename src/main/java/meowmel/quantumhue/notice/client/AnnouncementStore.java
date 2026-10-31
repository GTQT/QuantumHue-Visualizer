package meowmel.quantumhue.notice.client;

import meowmel.quantumhue.notice.Announcement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 公告的内存视图。
 *
 * <p>没有本地缓存：公告由启动预取/手动刷新拉取后写入这里；页面打开不再联网。
 */
public final class AnnouncementStore {

    private static volatile List<Announcement> items = Collections.emptyList();
    private static volatile boolean synced;
    private static volatile String error = "";

    private AnnouncementStore() {}

    public static void set(List<Announcement> value) {
        List<Announcement> parsed = new ArrayList<>();
        if (value != null) {
            for (Announcement item : value) {
                if (item != null && item.normalize()) {
                    parsed.add(item);
                }
            }
        }
        parsed.sort(Comparator
                .comparing((Announcement item) -> !item.pinned)
                .thenComparing(item -> item.date, Comparator.reverseOrder()));
        items = Collections.unmodifiableList(parsed);
        synced = true;
        error = "";
    }

    /** 兼容旧调用：不再读盘，也不联网。 */
    public static void ensureLoaded(boolean force) {
        // 数据只由 PageBootstrap 写入
    }

    public static void invalidate() {
        synced = false;
        error = "正在同步公告…";
    }

    public static List<Announcement> items() {
        return items;
    }

    public static boolean isLoading() {
        return false;
    }

    public static String error() {
        if (synced) {
            return "";
        }
        return error.isEmpty() ? "尚未同步公告，请执行 /quantumhue refresh" : error;
    }
}
