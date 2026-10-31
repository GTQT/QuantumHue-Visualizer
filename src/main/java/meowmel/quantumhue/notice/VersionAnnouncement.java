package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个版本更新公告（{@code remote/version/<version>.json}）。
 */
public class VersionAnnouncement {

    public String version = "";
    public String date = "";
    public String title = "";
    public List<String> body = new ArrayList<>();

    public boolean normalize() {
        version = trim(version, 32);
        if (version.isEmpty()) {
            return false;
        }
        title = trim(title, 96);
        if (title.isEmpty()) {
            title = version + " 更新公告";
        }
        date = trim(date, 24);
        List<String> clean = new ArrayList<>();
        for (String line : body == null ? new ArrayList<String>() : body) {
            if (line == null) {
                continue;
            }
            clean.add(line.length() > 512 ? line.substring(0, 512) : line);
            if (clean.size() >= 200) {
                break;
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
            lines.add("§7[无更新内容]");
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
}
