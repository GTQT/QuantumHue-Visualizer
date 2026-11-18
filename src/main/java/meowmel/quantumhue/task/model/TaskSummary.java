package meowmel.quantumhue.task.model;

/**
 * 列表/聊天卡使用的轻量任务快照。完整检查项与评论只在 Task 里。
 */
public class TaskSummary {

    public String id = "";
    public String groupId = "";
    public String title = "";
    public String descSnippet = "";
    public String creatorUuid = "";
    public String creatorName = "";
    public int doneCount;
    public int totalCount;
    public int commentCount;
    public boolean completed;
    public boolean deleted;
    public long updatedAt;
    public int rev;

    public TaskSummary() {
    }

    public static TaskSummary from(Task task) {
        TaskSummary s = new TaskSummary();
        if (task == null) {
            return s;
        }
        s.id = task.id;
        s.groupId = task.groupId;
        s.title = task.title;
        s.descSnippet = task.desc == null ? "" : task.desc;
        if (s.descSnippet.length() > 160) {
            s.descSnippet = s.descSnippet.substring(0, 160);
        }
        s.creatorUuid = task.creatorUuid;
        s.creatorName = task.creatorName;
        s.doneCount = task.doneCount();
        s.totalCount = task.totalCount();
        s.commentCount = task.comments == null ? 0 : task.comments.size();
        s.completed = task.completed;
        s.deleted = task.deleted;
        s.updatedAt = task.updatedAt;
        s.rev = task.rev;
        return s;
    }
}
