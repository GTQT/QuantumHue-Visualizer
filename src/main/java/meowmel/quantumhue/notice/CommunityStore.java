package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 社群服务的内存视图：没有本地缓存，只保存启动预取/手动刷新得到的内容。
 */
public final class CommunityStore {

    private static volatile List<String> lines = Collections.emptyList();
    private static volatile boolean synced;

    private CommunityStore() {}

    public static void set(List<String> value) {
        lines = value == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(value));
        synced = true;
    }

    public static List<String> lines() {
        return lines;
    }

    public static boolean hasData() {
        return !lines.isEmpty();
    }

    /** 是否成功同步过（同步过但内容为空时显示“暂无”，而不是“尚未同步”）。 */
    public static boolean isSynced() {
        return synced;
    }
}
