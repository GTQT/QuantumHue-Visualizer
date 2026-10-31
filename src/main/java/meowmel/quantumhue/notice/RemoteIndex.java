package meowmel.quantumhue.notice;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 远程索引（{@code index.json}）的读取与文件地址解析。
 *
 * <p>索引格式：
 * <pre>{@code
 * { "version": 1, "files": ["1.9.0.json", "1.8.0.json"] }
 * }</pre>
 * {@code files} 与 {@code mails} 两个键都会读取，方便公告/邮件共用。
 * 文件地址相对索引所在目录解析。
 */
public final class RemoteIndex {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Index");
    private static final Gson GSON = new Gson();
    private static final int MAX_ENTRIES = 200;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_WARN =
            new java.util.concurrent.ConcurrentHashMap<>();

    private RemoteIndex() {}

    public static class Document {
        public int version = 1;
        public List<String> files = new ArrayList<>();
        public List<String> mails = new ArrayList<>();
    }

    public static class Entry {
        public final String name;
        public final String url;

        Entry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    /**
     * 读取索引并解析出文件地址。
     *
     * @return 失败返回 {@code null}；成功返回的列表可能为空
     */
    public static List<Entry> list(String indexUrl, int timeoutMs) {
        if (indexUrl == null || indexUrl.trim().isEmpty()) {
            return null;
        }
        String raw = RemoteTextFetcher.fetchFreshSync(indexUrl.trim(), timeoutMs);
        if (raw == null || raw.isEmpty()) {
            warnThrottled(indexUrl);
            return null;
        }
        Document document;
        try {
            document = GSON.fromJson(raw, Document.class);
        } catch (Throwable invalid) {
            LOGGER.warn("[Index] 索引 JSON 解析失败: {}", indexUrl);
            return null;
        }
        if (document == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        if (document.files != null) {
            names.addAll(document.files);
        }
        if (document.mails != null) {
            names.addAll(document.mails);
        }

        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String name : names) {
            String resolved = resolve(indexUrl, name);
            if (resolved == null || !seen.add(resolved)) {
                continue;
            }
            entries.add(new Entry(name == null ? "" : name.trim(), resolved));
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
        }
        return Collections.unmodifiableList(entries);
    }

    /** 30 秒重试窗口内避免同一索引每 2 秒刷一条 WARN。 */
    private static void warnThrottled(String indexUrl) {
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(indexUrl);
        if (last == null || now - last >= 15_000L) {
            LOGGER.warn("[Index] 索引拉取失败: {}", indexUrl);
            LAST_WARN.put(indexUrl, now);
        } else {
            LOGGER.debug("[Index] 索引仍未恢复: {}", indexUrl);
        }
    }

    private static String resolve(String indexUrl, String name) {        if (name == null) {
            return null;
        }
        String file = name.trim();
        if (file.isEmpty() || file.length() > 160
                || file.contains("..") || file.startsWith("/") || file.contains("\\")) {
            LOGGER.warn("[Index] 忽略可疑的文件名: {}", name);
            return null;
        }
        try {
            return new URL(new URL(indexUrl), file).toString();
        } catch (Throwable invalid) {
            LOGGER.warn("[Index] 无法解析文件地址: {}", name);
            return null;
        }
    }
}
