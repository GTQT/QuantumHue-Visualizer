package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条公告的远程模型。每个公告文件对应一个这样的对象。
 *
 * <p>与邮件不同，公告没有奖励、也不写存档，所以完全由客户端拉取与展示；服务端不参与。
 */
public class Announcement {

    public String id = "";
    public String title = "";
    public String date = "";
    public String author = "";
    public boolean pinned;

    public List<String> body = new ArrayList<>();

    public boolean normalize() {
        id = trim(id, 64);
        if (id.isEmpty()) {
            return false;
        }
        title = trim(title, 96);
        if (title.isEmpty()) {
            title = id;
        }
        date = trim(date, 24);
        author = trim(author, 48);
        if (author.isEmpty()) {
            author = "GTQT";
        }
        List<String> clean = new ArrayList<>();
        if (body != null) {
            for (String line : body) {
                if (line == null) {
                    continue;
                }
                clean.add(line.length() > 512 ? line.substring(0, 512) : line);
                if (clean.size() >= 200) {
                    break;
                }
            }
        }
        body = clean;
        return true;
    }

    public List<String> parsedBody() {
        List<String> lines = new ArrayList<>();
        for (String raw : body) {
            String parsed = NoticeText.parseLine(raw);
            if (parsed != null) {
                lines.add(parsed);
            }
        }
        if (lines.isEmpty()) {
            lines.add("§7[无正文]");
        }
        return lines;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        return result.length() > max ? result.substring(0, max) : result;
    }

    /** 公告索引：列出本目录下的公告文件名。 */
    public static class Index {
        public int version = 1;
        public List<String> files = new ArrayList<>();
        /** 兼容旧写法；与 files 合并。 */
        public List<String> announcements = new ArrayList<>();
    }

    /** 本地缓存：启动时拉取到的公告列表。 */
    public static class Cache {
        public long fetchedAt;
        public List<Announcement> items = new ArrayList<>();
    }
}
