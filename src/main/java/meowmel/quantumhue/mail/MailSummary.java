package meowmel.quantumhue.mail;

/**
 * 邮件列表里的一行：只带列表需要的字段，避免整包发送正文和奖励。
 */
public class MailSummary {

    public String id = "";
    public String title = "";
    public String sender = "";
    public String date = "";
    public int rewardCount;
    public long money;
    public boolean claimed;
    public boolean started = true;
    public boolean expired;

    public boolean active() {
        return started && !expired;
    }

    public String statusText() {
        if (claimed) {
            return "§7已领取";
        }
        if (expired) {
            return "§c已过期";
        }
        if (!started) {
            return "§e未开始";
        }
        return "§b未领取";
    }
}
