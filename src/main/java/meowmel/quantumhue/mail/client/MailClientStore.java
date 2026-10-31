package meowmel.quantumhue.mail.client;

import meowmel.quantumhue.mail.MailDetail;
import meowmel.quantumhue.mail.MailSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端持有的邮件视图。
 *
 * <p>数据全部来自 {@code MessageMailView}；这里不解析任何配置或远程文件，也不做领取判断。
 * 所有写入都发生在客户端主线程（网络包调度回主线程之后），因此读取无需加锁。
 */
public final class MailClientStore {

    private static volatile List<MailSummary> summaries = Collections.emptyList();
    private static volatile MailDetail detail;
    private static volatile boolean loading;
    private static volatile boolean enabled = true;
    private static volatile String listNote = "";
    private static volatile String error = "";
    private static volatile String errorMailId = "";

    private MailClientStore() {}

    // ================================================================ 写入

    public static synchronized void setList(boolean loading, boolean enabled, List<MailSummary> list) {
        setList(loading, enabled, list, "");
    }

    public static synchronized void setList(boolean loading, boolean enabled, List<MailSummary> list,
                                            String note) {
        MailClientStore.loading = loading;
        MailClientStore.enabled = enabled;
        MailClientStore.listNote = note == null ? "" : note;
        List<MailSummary> copy = new ArrayList<>(list == null ? Collections.emptyList() : list);
        summaries = Collections.unmodifiableList(copy);
        // 详情对应的邮件已经不在列表里时清掉，避免展示已删除的邮件
        MailDetail current = detail;
        if (current != null && findIn(summaries, current.id) == null) {
            detail = null;
        }
    }

    public static synchronized void setDetail(MailDetail value) {
        detail = value;
        if (value != null) {
            error = "";
            errorMailId = "";
            // 详情返回已领取时同步列表里的状态
            if (value.claimed) {
                markClaimed(value.id);
            }
        }
    }

    public static synchronized void setError(String mailId, String message) {
        error = message == null ? "" : message;
        errorMailId = mailId == null ? "" : mailId;
    }

    public static synchronized void clearError() {
        error = "";
        errorMailId = "";
    }

    /** 客户端发起列表请求时先切到“同步中”，避免首帧显示成空邮箱。 */
    public static synchronized void markLoading() {
        loading = true;
    }

    private static void markClaimed(String mailId) {
        List<MailSummary> next = new ArrayList<>(summaries.size());
        for (MailSummary summary : summaries) {
            if (summary.id.equals(mailId)) {
                summary.claimed = true;
            }
            next.add(summary);
        }
        summaries = Collections.unmodifiableList(next);
    }

    // ================================================================ 读取

    public static List<MailSummary> summaries() {
        return summaries;
    }

    public static MailDetail detail() {
        return detail;
    }

    public static boolean isLoading() {
        return loading;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 列表同步的附加说明（例如远程拉取失败），列表非空时一般不展示。 */
    public static String listNote() {
        return listNote;
    }

    public static String error() {
        return error;
    }

    public static String errorMailId() {
        return errorMailId;
    }

    public static MailSummary find(String mailId) {
        return findIn(summaries, mailId);
    }

    private static MailSummary findIn(List<MailSummary> list, String mailId) {
        if (mailId == null) {
            return null;
        }
        for (MailSummary summary : list) {
            if (summary.id.equals(mailId)) {
                return summary;
            }
        }
        return null;
    }
}
