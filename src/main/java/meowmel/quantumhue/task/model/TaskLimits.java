package meowmel.quantumhue.task.model;

/**
 * 任务板的硬限制。保持在模型层，服务端与客户端都能引用。
 */
public final class TaskLimits {

    private TaskLimits() {
    }

    public static final int MAX_TASKS_PER_GROUP = 100;
    public static final int MAX_ITEMS_PER_TASK = 20;
    public static final int MAX_COMMENTS_PER_TASK = 100;

    public static final int MAX_TITLE = 40;
    public static final int MAX_DESC = 500;
    public static final int MAX_ITEM_TEXT = 60;
    public static final int MAX_COMMENT_TEXT = 200;

    /** 已完成任务保留 30 天，之后在服务端清理。 */
    public static final long ARCHIVE_MS = 30L * 24L * 60L * 60L * 1000L;
}
