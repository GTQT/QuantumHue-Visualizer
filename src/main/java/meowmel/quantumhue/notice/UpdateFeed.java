package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次预取得到的更新日志集合（只保存在内存）。
 *
 * <p>{@code localVersion} 是本次比较使用的本地版本；{@code logs} 只包含比本地新的版本，
 * 由 {@code PageBootstrap} 在加载阶段/手动刷新时填充。
 */
public class UpdateFeed {

    public String localVersion = "";
    public String latest = "";
    public long fetchedAt;
    public List<VersionLog> logs = new ArrayList<>();

    public static class VersionLog {
        public String version = "";
        public String date = "";
        public List<String> lines = new ArrayList<>();
    }
}
