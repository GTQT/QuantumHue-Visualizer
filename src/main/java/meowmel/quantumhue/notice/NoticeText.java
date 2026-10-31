package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 公告文本的解析：{@code &} 颜色码、{@code #} 注释、空行。
 *
 * <p>配置里的 {@code lines} 和远程拉下来的纯文本走同一套解析，所以运营在 JSON 里写的颜色码与
 * 在 GitHub 上改的公告表现一致。
 */
public final class NoticeText {

    private NoticeText() {}

    /** 单行：去空白、跳过注释、把 {@code &} 换成 Minecraft 的 {@code §}。 */
    public static String parseLine(String raw) {
        if (raw == null) {
            return null;
        }
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        return line.replace('&', '\u00a7');
    }

    /** 解析多行文本；没有任何有效行时返回一条占位提示。 */
    public static List<String> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.singletonList("§7[暂无内容]");
        }
        List<String> result = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String parsed = parseLine(line);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result.isEmpty() ? Collections.singletonList("§7[暂无内容]") : result;
    }

    /** 页签内容来源地址：只取页签自己的 url。 */
    public static String remoteUrl(NoticeConfig.Tab tab) {
        if (tab != null && tab.url != null) {
            return tab.url.trim();
        }
        return "";
    }
}
