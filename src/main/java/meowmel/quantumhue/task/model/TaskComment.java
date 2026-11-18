package meowmel.quantumhue.task.model;

/**
 * 任务评论。评论只留在任务详情里，不进入聊天流。
 */
public class TaskComment {

    public String id = "";
    public String authorUuid = "";
    public String authorName = "";
    public String text = "";
    public long at;

    public TaskComment() {
    }

    public TaskComment(String id, String authorUuid, String authorName, String text, long at) {
        this.id = id;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.text = text;
        this.at = at;
    }
}
