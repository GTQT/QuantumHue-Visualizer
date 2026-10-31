package meowmel.quantumhue.mail;

import meowmel.quantumhue.notice.NoticeText;

import java.util.ArrayList;
import java.util.List;

/**
 * 一封远程邮件的运行时模型。
 *
 * <p>字段直接对应远程邮件目录里的 JSON 键；{@link #normalize()} 负责把玩家/运营可能写错的
 * 值收拢到安全范围（长度、数量、时间），解析失败的邮件会被整封丢弃。
 */
public class Mail {

    public String id = "";
    public String title = "";
    public String sender = "";
    public String date = "";

    /** 生效时间（epoch 毫秒），0 表示立即生效。 */
    public long startsAt;
    /** 过期时间（epoch 毫秒），0 表示永不过期。 */
    public long expiresAt;
    /** 可选货币奖励。 */
    public long money;

    public List<String> body = new ArrayList<>();
    public List<MailReward> rewards = new ArrayList<>();

    public boolean isStarted(long now) {
        return startsAt <= 0L || now >= startsAt;
    }

    public boolean isExpired(long now) {
        return expiresAt > 0L && now > expiresAt;
    }

    public boolean isActive(long now) {
        return isStarted(now) && !isExpired(now);
    }

    /** 正文：逐行套颜色码；空正文给一条占位提示。 */
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

    /**
     * 校验并收拢字段；返回 {@code false} 表示这封邮件无效，应当丢弃。
     */
    public boolean normalize() {
        id = trim(id, 64);
        if (id.isEmpty()) {
            return false;
        }
        title = trim(title, 96);
        if (title.isEmpty()) {
            title = id;
        }
        sender = trim(sender, 48);
        if (sender.isEmpty()) {
            sender = "系统";
        }
        date = trim(date, 24);
        money = Math.max(0L, Math.min(money, 1_000_000_000_000L));

        if (startsAt < 0L) {
            startsAt = 0L;
        }
        if (expiresAt < 0L) {
            expiresAt = 0L;
        }
        if (startsAt > 0L && expiresAt > 0L && startsAt > expiresAt) {
            startsAt = 0L;
        }

        List<String> cleanBody = new ArrayList<>();
        if (body != null) {
            for (String line : body) {
                if (line == null) {
                    continue;
                }
                String value = line.length() > 512 ? line.substring(0, 512) : line;
                cleanBody.add(value);
                if (cleanBody.size() >= 100) {
                    break;
                }
            }
        }
        body = cleanBody;

        List<MailReward> cleanRewards = new ArrayList<>();
        if (rewards != null) {
            for (MailReward reward : rewards) {
                if (reward == null) {
                    continue;
                }
                if (reward.normalize()) {
                    cleanRewards.add(reward);
                }
                if (cleanRewards.size() >= 36) {
                    break;
                }
            }
        }
        rewards = cleanRewards;
        return true;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        return result.length() > max ? result.substring(0, max) : result;
    }
}
