package meowmel.quantumhue.task.model;

/**
 * 任务检查项。id 在任务内唯一（i1、i2...），勾选状态与勾选人一起持久化。
 */
public class TaskItem {

    public String id = "";
    public String text = "";
    public boolean done;
    public String doneByUuid = "";
    public String doneByName = "";
    public long doneAt;

    public TaskItem() {
    }

    public TaskItem(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public TaskItem copy() {
        TaskItem item = new TaskItem(id, text);
        item.done = done;
        item.doneByUuid = doneByUuid;
        item.doneByName = doneByName;
        item.doneAt = doneAt;
        return item;
    }
}
