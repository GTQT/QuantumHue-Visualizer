package meowmel.quantumhue.task.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 群任务（本质是公共记事板）。
 *
 * <p>状态只有“进行中 / 已完成”。有检查项时全部勾选自动完成，取消任一勾选自动回到进行中；
 * 没有检查项时由成员手动完成/重开。
 */
public class Task {

    public String id = "";
    public String groupId = "";
    public String title = "";
    public String desc = "";
    public String creatorUuid = "";
    public String creatorName = "";
    public List<TaskItem> items = new ArrayList<>();
    public List<TaskComment> comments = new ArrayList<>();
    public boolean completed;
    public boolean deleted;
    public long completedAt;
    public long createdAt;
    public long updatedAt;
    public int rev;

    public Task() {
    }

    public int totalCount() {
        return items == null ? 0 : items.size();
    }

    public int doneCount() {
        if (items == null) {
            return 0;
        }
        int n = 0;
        for (TaskItem item : items) {
            if (item != null && item.done) {
                n++;
            }
        }
        return n;
    }

    public boolean allItemsDone() {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (TaskItem item : items) {
            if (item == null || !item.done) {
                return false;
            }
        }
        return true;
    }

    /**
     * 有检查项时按勾选状态推导完成状态；没有检查项时保持调用方设置的手动状态。
     */
    public void recomputeCompletion(long now) {
        if (items == null || items.isEmpty()) {
            return;
        }
        boolean all = allItemsDone();
        if (all && !completed) {
            completed = true;
            completedAt = now;
        } else if (!all && completed) {
            completed = false;
            completedAt = 0L;
        }
    }

    public TaskItem findItem(String itemId) {
        if (items == null || itemId == null) {
            return null;
        }
        for (TaskItem item : items) {
            if (item != null && itemId.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    public TaskSummary toSummary() {
        return TaskSummary.from(this);
    }
}
