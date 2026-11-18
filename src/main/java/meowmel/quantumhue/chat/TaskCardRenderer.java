package meowmel.quantumhue.chat;

import meowmel.quantumhue.task.client.TaskStore;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * 聊天流里的任务卡。卡片本身只存 taskId，标题/进度/评论数每次绘制都从 TaskStore 现取，
 * 所以任务被编辑、勾选后卡片会跟着变，不需要重发消息。
 */
final class TaskCardRenderer {

    static final int HEIGHT = 54;

    private TaskCardRenderer() {
    }

    static int cardWidth(int wrapWidth) {
        return Math.min(300, Math.max(180, wrapWidth - 18));
    }

    static int draw(ChatMessageStore.ChatMessage msg, int x, int y, int wrapWidth) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int cardW = cardWidth(wrapWidth);
        String groupId = groupIdOf(msg);
        Task task = groupId.isEmpty() ? null : TaskStore.get().getTask(groupId, msg.taskId());
        TaskSummary summary = task == null || task.title == null
                ? (groupId.isEmpty() ? null : TaskStore.get().getSummary(groupId, msg.taskId())) : null;
        String title = task != null ? task.title
                : summary != null ? summary.title : msg.content().getUnformattedText();
        boolean deleted = task != null ? task.deleted : summary != null && summary.deleted;
        boolean completed = task != null ? task.completed : summary != null && summary.completed;
        int stateColor = deleted ? ChatColors.TASK_DELETED
                : completed ? ChatColors.TASK_DONE : ChatColors.ACCENT;

        ChatRender.roundRect(x, y, x + cardW, y + HEIGHT, ChatColors.TASK_CARD_BG, 4);
        ChatRender.roundRect(x, y, x + 3, y + HEIGHT, stateColor, 1);
        ChatRender.roundRect(x, y, x + cardW, y + 1, ChatColors.TASK_CARD_BORDER, 1);

        String label = deleted ? "任务已删除" : completed ? "已完成" : "群任务";
        font.drawStringWithShadow(label, x + 10, y + 5, stateColor);
        String trimmed = font.trimStringToWidth(title == null ? "" : title, cardW - 20);
        font.drawStringWithShadow(trimmed, x + 10, y + 17, ChatColors.TEXT_PRIMARY);

        int total = task != null ? task.totalCount() : summary != null ? summary.totalCount : 0;
        int done = task != null ? task.doneCount() : summary != null ? summary.doneCount : 0;
        int comments = task != null
                ? (task.comments == null ? 0 : task.comments.size())
                : summary != null ? summary.commentCount : 0;
        String progress = total > 0 ? done + "/" + total : (completed ? "完成" : "进行中");
        font.drawStringWithShadow(progress, x + 10, y + 33, stateColor);

        int barX = x + 10 + font.getStringWidth(progress) + 8;
        int barW = Math.max(20, cardW - (barX - x) - 16);
        ChatRender.roundRect(barX, y + 36, barX + barW, y + 40, ChatColors.TASK_PROGRESS_BG, 2);
        if (total > 0) {
            int fill = Math.max(0, Math.min(barW, barW * done / total));
            ChatRender.roundRect(barX, y + 36, barX + fill, y + 40, ChatColors.TASK_PROGRESS_FG, 2);
        } else if (completed) {
            ChatRender.roundRect(barX, y + 36, barX + barW, y + 40, ChatColors.TASK_PROGRESS_FG, 2);
        }

        String meta = "💬 " + comments + "   " + (msg.senderName() == null ? "" : msg.senderName().getUnformattedText());
        font.drawStringWithShadow(font.trimStringToWidth(meta, cardW - 20), x + 10, y + 44,
                ChatColors.TEXT_SECONDARY);
        return HEIGHT;
    }

    static String groupIdOf(ChatMessageStore.ChatMessage msg) {
        String channel = msg.channelId();
        if (channel != null && channel.startsWith("group:")) {
            return channel.substring("group:".length());
        }
        return "";
    }
}
