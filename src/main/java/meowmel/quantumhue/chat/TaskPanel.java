package meowmel.quantumhue.chat;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.task.client.TaskStore;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskComment;
import meowmel.quantumhue.task.model.TaskItem;
import meowmel.quantumhue.task.model.TaskLimits;
import meowmel.quantumhue.task.model.TaskSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 群任务板浮层。沿用 ChatSettingsPanel 的做法：不新开 GuiScreen，直接画在 ChatScreen 上面，
 * 这样聊天输入框、未读、频道切换都不会被打断。
 */
final class TaskPanel {

    private static final int VIEW_LIST = 0;
    private static final int VIEW_DETAIL = 1;
    private static final int VIEW_FORM = 2;

    private static final int FOCUS_NONE = 0;
    private static final int FOCUS_SEARCH = 1;
    private static final int FOCUS_TITLE = 2;
    private static final int FOCUS_DESC = 3;
    private static final int FOCUS_ITEM = 4;
    private static final int FOCUS_COMMENT = 5;

    private final List<EditRow> editRows = new ArrayList<>();
    private final List<int[]> itemRects = new ArrayList<>();

    private ChatScreen host;
    private boolean open;
    private String groupId = "";
    private int view = VIEW_LIST;
    private String selectedId = "";
    private Task editingTask;
    private boolean confirmDelete;
    private boolean snapshotRequested;
    private int scroll;
    private int focus = FOCUS_NONE;
    private int focusItem = -1;
    private String localNotice = "";
    private long localNoticeAt;

    private GuiTextField searchField;
    private GuiTextField titleField;
    private GuiTextField commentField;
    private TaskTextArea descArea;

    boolean isOpen() {
        return open;
    }

    String getGroupId() {
        return groupId;
    }

    void open(ChatScreen host, String groupId) {
        this.host = host;
        this.groupId = groupId == null ? "" : groupId;
        this.open = true;
        this.view = VIEW_LIST;
        this.selectedId = "";
        this.editingTask = null;
        this.confirmDelete = false;
        this.snapshotRequested = false;
        this.scroll = 0;
        this.focus = FOCUS_NONE;
        this.localNotice = "";
        ensureFields();
        searchField.setText("");
        if (!this.groupId.isEmpty()) {
            TaskStore.get().requestSummary(this.groupId);
        }
    }

    /** 从工具栏“＋任务”进入时直接打开新建表单。 */
    void openCreate(ChatScreen host, String groupId) {
        open(host, groupId);
        startCreate();
    }

    void openTask(String groupId, String taskId) {
        open(host, groupId);
        if (taskId != null && !taskId.isEmpty()) {
            selectedId = taskId;
            view = VIEW_DETAIL;
            snapshotRequested = false;
            scroll = 0;
            TaskStore.get().requestSnapshot(groupId);
        }
    }

    void close() {
        open = false;
        focus = FOCUS_NONE;
        editingTask = null;
        confirmDelete = false;
    }

    void updateScreen() {
        if (!open) {
            return;
        }
        ensureFields();
        searchField.updateCursorCounter();
        titleField.updateCursorCounter();
        commentField.updateCursorCounter();
        for (EditRow row : editRows) {
            row.field.updateCursorCounter();
        }
    }

    private void ensureFields() {
        if (searchField != null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        searchField = new GuiTextField(900, font, 0, 0, 100, 14);
        searchField.setMaxStringLength(40);
        titleField = new GuiTextField(901, font, 0, 0, 100, 14);
        titleField.setMaxStringLength(TaskLimits.MAX_TITLE);
        commentField = new GuiTextField(902, font, 0, 0, 100, 14);
        commentField.setMaxStringLength(TaskLimits.MAX_COMMENT_TEXT);
        descArea = new TaskTextArea();
        descArea.setMaxLength(TaskLimits.MAX_DESC);
    }

    private int panelW() {
        return Math.min(430, Math.max(280, host.width - 16));
    }

    private int panelH() {
        return Math.min(310, Math.max(210, host.height - 12));
    }

    private int panelX() {
        return (host.width - panelW()) / 2;
    }

    private int panelY() {
        return (host.height - panelH()) / 2;
    }

    private int contentTop() {
        return panelY() + 30;
    }

    private int contentBottom() {
        return panelY() + panelH() - 30;
    }

    private FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    void draw(int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        Gui.drawRect(0, 0, host.width, host.height, 0xC0080B0F);
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        ChatRender.roundRect(x, y, x + w, y + h, ChatColors.PANEL_BG, 5);
        ChatRender.roundRect(x, y, x + w, y + 1, ChatColors.TASK_CARD_BORDER, 1);
        ChatRender.roundRect(x, y + 24, x + w, y + 25, ChatColors.DIVIDER, 1);

        ChatRender.roundRect(x + 10, y + 8, x + 13, y + 18, ChatColors.ACCENT, 1);
        font().drawStringWithShadow("群任务板", x + 18, y + 9, ChatColors.TEXT_HEADER);
        String count = TaskStore.get().openCount(groupId) + " 项进行中";
        font().drawStringWithShadow(count, x + 82, y + 9, ChatColors.TEXT_SECONDARY);
        int closeX = x + w - 19;
        font().drawStringWithShadow("×", closeX, y + 6, ChatColors.TEXT_SECONDARY);

        if (view == VIEW_LIST) {
            drawList(mouseX, mouseY);
        } else if (view == VIEW_DETAIL) {
            drawDetail(mouseX, mouseY);
        } else {
            drawForm(mouseX, mouseY);
        }

        int noticeY = view == VIEW_DETAIL ? y + h - 58 : y + h - 41;
        if (!localNotice.isEmpty() && System.currentTimeMillis() - localNoticeAt < 4000L) {
            font().drawStringWithShadow(localNotice, x + 10, noticeY, ChatColors.TASK_DELETED);
        } else if (System.currentTimeMillis() - TaskStore.get().getLastNoticeAt() < 4000L) {
            String notice = TaskStore.get().getLastNotice();
            if (!notice.isEmpty()) {
                font().drawStringWithShadow(notice, x + 10, noticeY,
                        TaskStore.get().isLastNoticeError() ? ChatColors.TASK_DELETED : ChatColors.TASK_DONE);
            }
        }
    }

    // ---------------------------------------------------------------- list

    private void drawList(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();

        bounds(searchField, x + 8, y + 30, w - 16, 14);
        searchField.setFocused(focus == FOCUS_SEARCH);
        searchField.drawTextBox();
        if (searchField.getText().isEmpty() && focus != FOCUS_SEARCH) {
            font().drawStringWithShadow("搜索任务…", x + 11, y + 33, ChatColors.TEXT_SECONDARY);
        }

        int top = y + 50;
        int bottom = y + h - 30;
        List<TaskSummary> list = filteredSummaries();
        int rowH = 42;
        int maxScroll = Math.max(0, list.size() * (rowH + 4) - (bottom - top));
        scroll = clamp(scroll, 0, maxScroll);

        clipOn(x + 6, top, w - 12, bottom - top);
        if (list.isEmpty()) {
            font().drawStringWithShadow(searchField.getText().isEmpty() ? "还没有任务，点下方新建" : "没有匹配的任务",
                    x + 12, top + 8, ChatColors.TEXT_SECONDARY);
        }
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * (rowH + 4) - scroll;
            if (ry + rowH < top || ry > bottom) {
                continue;
            }
            drawSummaryCard(list.get(i), x + 8, ry, w - 16, mouseX, mouseY);
        }
        clipOff();

        int footerY = y + h - 24;
        button(x + 8, footerY, 86, 16, "＋ 新建任务", true, mouseX, mouseY);
        button(x + w - 66, footerY, 58, 16, "刷新", true, mouseX, mouseY);
    }

    private void drawSummaryCard(TaskSummary s, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = in(mouseX, mouseY, x, y, w, 40);
        int bg = hover ? ChatColors.SIDEBAR_HOVER : ChatColors.TASK_CARD_BG;
        ChatRender.roundRect(x, y, x + w, y + 40, bg, 3);
        int stateColor = s.deleted ? ChatColors.TASK_DELETED
                : s.completed ? ChatColors.TASK_DONE : ChatColors.ACCENT;
        ChatRender.roundRect(x, y, x + 3, y + 40, stateColor, 1);
        String title = font().trimStringToWidth(s.title == null ? "" : s.title, w - 76);
        font().drawStringWithShadow(title, x + 10, y + 6, s.deleted ? ChatColors.TASK_MUTED : ChatColors.TEXT_PRIMARY);
        String progress = s.totalCount > 0 ? s.doneCount + "/" + s.totalCount
                : (s.completed ? "已完成" : "进行中");
        font().drawStringWithShadow(progress, x + 10, y + 21, stateColor);
        String meta = "💬 " + s.commentCount + "   " + (s.creatorName == null ? "" : s.creatorName);
        font().drawStringWithShadow(font().trimStringToWidth(meta, w - 80), x + 52, y + 21, ChatColors.TEXT_SECONDARY);
        if (s.deleted) {
            font().drawStringWithShadow("已删除", x + w - 43, y + 6, ChatColors.TASK_DELETED);
        } else if (s.completed) {
            font().drawStringWithShadow("✓", x + w - 17, y + 6, ChatColors.TASK_DONE);
        }
    }

    private List<TaskSummary> filteredSummaries() {
        List<TaskSummary> all = TaskStore.get().getSummaries(groupId);
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return all;
        }
        List<TaskSummary> out = new ArrayList<>();
        for (TaskSummary s : all) {
            if (s.title != null && s.title.toLowerCase().contains(query)) {
                out.add(s);
            }
        }
        return out;
    }

    // -------------------------------------------------------------- detail

    private void drawDetail(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        int backW = 48;
        button(x + 8, y + 30, backW, 14, "← 列表", true, mouseX, mouseY);

        Task task = TaskStore.get().getTask(groupId, selectedId);
        if (task == null) {
            if (!snapshotRequested) {
                snapshotRequested = true;
                TaskStore.get().requestSnapshot(groupId);
            }
            font().drawStringWithShadow("加载中…", x + 10, y + 58, ChatColors.TEXT_SECONDARY);
            return;
        }

        int top = y + 50;
        int bottom = y + h - 48;
        int innerW = w - 24;
        clipOn(x + 6, top, w - 12, bottom - top);
        int ty = top - scroll;
        itemRects.clear();

        font().drawStringWithShadow(font().trimStringToWidth(task.title, innerW), x + 10, ty, ChatColors.TEXT_PRIMARY);
        ty += 14;
        if (task.desc != null && !task.desc.isEmpty()) {
            List<String> lines = font().listFormattedStringToWidth(task.desc, innerW);
            for (int i = 0; i < lines.size(); i++) {
                font().drawStringWithShadow(lines.get(i), x + 10, ty + i * 10, ChatColors.TEXT_SECONDARY);
            }
            ty += lines.size() * 10 + 6;
        }
        String state = task.deleted ? "任务已删除" : task.completed ? "已完成"
                : task.totalCount() > 0 ? "进行中 · " + task.doneCount() + "/" + task.totalCount() : "进行中";
        font().drawStringWithShadow(state, x + 10, ty,
                task.deleted ? ChatColors.TASK_DELETED : task.completed ? ChatColors.TASK_DONE : ChatColors.ACCENT);
        ty += 14;

        if (task.totalCount() > 0) {
            font().drawStringWithShadow("检查项", x + 10, ty, ChatColors.TEXT_HEADER);
            ty += 12;
            for (int i = 0; i < task.items.size(); i++) {
                TaskItem item = task.items.get(i);
                if (item == null) {
                    continue;
                }
                int rowY = ty + i * 14;
                boolean hover = in(mouseX, mouseY, x + 8, rowY - 2, w - 16, 13);
                if (hover) {
                    ChatRender.roundRect(x + 8, rowY - 2, x + w - 8, rowY + 11,
                            ChatColors.SIDEBAR_HOVER, 2);
                }
                int box = task.deleted ? ChatColors.TASK_MUTED
                        : item.done ? ChatColors.TASK_DONE : ChatColors.TASK_CARD_BORDER;
                Gui.drawRect(x + 12, rowY, x + 21, rowY + 9, box);
                if (item.done) {
                    font().drawStringWithShadow("✓", x + 12, rowY, ChatColors.PANEL_BG);
                }
                String text = font().trimStringToWidth(item.text, innerW - 30);
                font().drawStringWithShadow(text, x + 26, rowY, item.done ? ChatColors.TASK_MUTED : ChatColors.TEXT_PRIMARY);
                if (item.done && item.doneByName != null && !item.doneByName.isEmpty()) {
                    font().drawStringWithShadow(item.doneByName, x + w - 18 - font().getStringWidth(item.doneByName),
                            rowY, ChatColors.TEXT_SECONDARY);
                }
                if (rowY + 13 >= top && rowY <= bottom) {
                    itemRects.add(new int[]{x + 8, rowY - 2, w - 16, 13, i});
                }
            }
            ty += task.items.size() * 14 + 6;
        }

        font().drawStringWithShadow("评论 " + task.comments.size(), x + 10, ty, ChatColors.TEXT_HEADER);
        ty += 12;
        if (task.comments.isEmpty()) {
            font().drawStringWithShadow("还没有评论", x + 12, ty, ChatColors.TEXT_SECONDARY);
            ty += 12;
        } else {
            for (TaskComment comment : task.comments) {
                String head = (comment.authorName == null ? "" : comment.authorName) + "：";
                font().drawStringWithShadow(head, x + 12, ty, ChatColors.ACCENT);
                List<String> lines = font().listFormattedStringToWidth(
                        comment.text == null ? "" : comment.text, innerW - 20);
                for (int i = 0; i < lines.size(); i++) {
                    font().drawStringWithShadow(lines.get(i), x + 12, ty + 10 + i * 10, ChatColors.TEXT_PRIMARY);
                }
                ty += 12 + lines.size() * 10;
            }
        }
        clipOff();

        int detailHeight = Math.max(0, ty + scroll - top);
        scroll = clamp(scroll, 0, Math.max(0, detailHeight - (bottom - top)));

        bounds(commentField, x + 8, y + h - 44, w - 68, 14);
        commentField.setFocused(focus == FOCUS_COMMENT);
        commentField.drawTextBox();
        if (commentField.getText().isEmpty() && focus != FOCUS_COMMENT) {
            font().drawStringWithShadow("写评论…", x + 11, y + h - 41, ChatColors.TEXT_SECONDARY);
        }
        button(x + w - 54, y + h - 44, 44, 14, "发送", !commentField.getText().trim().isEmpty(), mouseX, mouseY);

        int footerY = y + h - 24;
        button(x + 8, footerY, 50, 16, "编辑", !task.deleted, mouseX, mouseY);
        button(x + 62, footerY, confirmDelete ? 74 : 50, 16, confirmDelete ? "确认删除" : "删除",
                !task.deleted, mouseX, mouseY);
        if (task.totalCount() == 0 && !task.deleted) {
            button(x + w - 82, footerY, 74, 16, task.completed ? "重新打开" : "标记完成", true, mouseX, mouseY);
        }
    }

    private int detailContentHeight(Task task) {
        int h = 40;
        if (task.desc != null && !task.desc.isEmpty()) {
            h += font().listFormattedStringToWidth(task.desc, panelW() - 24).size() * 10 + 6;
        }
        h += task.totalCount() * 14 + 12;
        h += 14;
        for (TaskComment comment : task.comments) {
            h += 12 + font().listFormattedStringToWidth(comment.text == null ? "" : comment.text,
                    panelW() - 44).size() * 10;
        }
        return h;
    }

    // ---------------------------------------------------------------- form

    private void drawForm(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        int innerW = w - 24;
        int top = y + 30;
        int bottom = y + h - 30;
        clipOn(x + 6, top, w - 12, bottom - top);

        int ty = top + 4 - scroll;
        font().drawStringWithShadow("标题", x + 10, ty, ChatColors.TEXT_SECONDARY);
        ty += 10;
        bounds(titleField, x + 10, ty, innerW, 14);
        titleField.setFocused(focus == FOCUS_TITLE);
        titleField.drawTextBox();
        ty += 20;

        font().drawStringWithShadow("说明", x + 10, ty, ChatColors.TEXT_SECONDARY);
        ty += 10;
        descArea.setBounds(x + 10, ty, innerW, 44);
        descArea.setFocused(focus == FOCUS_DESC);
        descArea.draw("补充背景、目标或注意事项…");
        ty += 50;

        font().drawStringWithShadow("检查项（全部勾选后自动完成）", x + 10, ty, ChatColors.TEXT_SECONDARY);
        ty += 12;
        for (int i = 0; i < editRows.size(); i++) {
            EditRow row = editRows.get(i);
            int rowY = ty + i * 16;
            if (rowY + 14 < top || rowY > bottom) {
                continue;
            }
            bounds(row.field, x + 10, rowY, innerW - 18, 12);
            row.field.setFocused(focus == FOCUS_ITEM && focusItem == i);
            row.field.drawTextBox();
            font().drawStringWithShadow("×", x + w - 22, rowY + 1, ChatColors.TASK_DELETED);
        }
        ty += editRows.size() * 16;
        if (editRows.size() < maxItems()) {
            button(x + 10, ty, 78, 14, "＋ 检查项", true, mouseX, mouseY);
        }
        ty += 20;

        clipOff();

        int footerY = y + h - 24;
        boolean valid = !titleField.getText().trim().isEmpty();
        button(x + 8, footerY, 62, 16, editingTask == null ? "发布" : "保存", valid, mouseX, mouseY);
        button(x + 74, footerY, 50, 16, "取消", true, mouseX, mouseY);
        String tip = "Ctrl+Enter 保存";
        font().drawStringWithShadow(tip, x + w - 8 - font().getStringWidth(tip), footerY + 4, ChatColors.TEXT_SECONDARY);
    }

    // ------------------------------------------------------------- events

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) {
            return false;
        }
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        if (!in(mouseX, mouseY, x, y, w, h)) {
            close();
            return true;
        }
        if (in(mouseX, mouseY, x + w - 22, y + 4, 18, 18)) {
            close();
            return true;
        }
        if (view == VIEW_LIST) {
            if (mouseClickedList(mouseX, mouseY)) {
                return true;
            }
        } else if (view == VIEW_DETAIL) {
            if (mouseClickedDetail(mouseX, mouseY)) {
                return true;
            }
        } else if (mouseClickedForm(mouseX, mouseY)) {
            return true;
        }
        focus = FOCUS_NONE;
        return true;
    }

    private boolean mouseClickedList(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        if (in(mouseX, mouseY, x + 8, y + 30, w - 16, 14)) {
            focus = FOCUS_SEARCH;
            searchField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        int top = y + 50;
        int bottom = y + h - 30;
        List<TaskSummary> list = filteredSummaries();
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * 46 - scroll;
            if (ry + 40 < top || ry > bottom) {
                continue;
            }
            if (in(mouseX, mouseY, x + 8, ry, w - 16, 40)) {
                selectedId = list.get(i).id;
                view = VIEW_DETAIL;
                scroll = 0;
                confirmDelete = false;
                snapshotRequested = false;
                TaskStore.get().requestSnapshot(groupId);
                return true;
            }
        }
        int footerY = y + h - 24;
        if (in(mouseX, mouseY, x + 8, footerY, 86, 16)) {
            startCreate();
            return true;
        }
        if (in(mouseX, mouseY, x + w - 66, footerY, 58, 16)) {
            TaskStore.get().requestSummary(groupId);
            return true;
        }
        return false;
    }

    private boolean mouseClickedDetail(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        if (in(mouseX, mouseY, x + 8, y + 30, 48, 14)) {
            view = VIEW_LIST;
            scroll = 0;
            confirmDelete = false;
            focus = FOCUS_NONE;
            TaskStore.get().requestSummary(groupId);
            return true;
        }
        Task task = TaskStore.get().getTask(groupId, selectedId);
        if (task == null) {
            return false;
        }
        for (int[] rect : itemRects) {
            if (rect[4] >= 0 && rect[4] < task.items.size()
                    && in(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]) && !task.deleted) {
                TaskItem item = task.items.get(rect[4]);
                TaskStore.get().checkItem(task, item.id, !item.done);
                return true;
            }
        }
        if (in(mouseX, mouseY, x + 8, y + h - 44, w - 68, 14)) {
            focus = FOCUS_COMMENT;
            commentField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (in(mouseX, mouseY, x + w - 54, y + h - 44, 44, 14)) {
            sendComment(task);
            return true;
        }
        int footerY = y + h - 24;
        if (in(mouseX, mouseY, x + 8, footerY, 50, 16)) {
            startEdit(task);
            return true;
        }
        if (in(mouseX, mouseY, x + 62, footerY, confirmDelete ? 74 : 50, 16)) {
            if (confirmDelete) {
                TaskStore.get().deleteTask(task);
                view = VIEW_LIST;
                selectedId = "";
                scroll = 0;
                confirmDelete = false;
                TaskStore.get().requestSummary(groupId);
            } else {
                confirmDelete = true;
            }
            return true;
        }
        if (task.totalCount() == 0 && in(mouseX, mouseY, x + w - 82, footerY, 74, 16)) {
            TaskStore.get().completeTask(task, !task.completed);
            return true;
        }
        return false;
    }

    private boolean mouseClickedForm(int mouseX, int mouseY) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        int innerW = w - 24;
        int top = y + 30;
        int ty = top + 14 - scroll;
        if (in(mouseX, mouseY, x + 10, ty, innerW, 14)) {
            focus = FOCUS_TITLE;
            titleField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        ty += 20;
        ty += 10;
        if (in(mouseX, mouseY, x + 10, ty, innerW, 44)) {
            focus = FOCUS_DESC;
            descArea.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        ty += 50;
        ty += 12;
        for (int i = 0; i < editRows.size(); i++) {
            EditRow row = editRows.get(i);
            int rowY = ty + i * 16;
            if (in(mouseX, mouseY, x + 10, rowY, innerW - 18, 12)) {
                focus = FOCUS_ITEM;
                focusItem = i;
                row.field.mouseClicked(mouseX, mouseY, 0);
                return true;
            }
            if (in(mouseX, mouseY, x + w - 24, rowY - 2, 14, 14)) {
                editRows.remove(i);
                focus = FOCUS_NONE;
                return true;
            }
        }
        ty += editRows.size() * 16;
        if (editRows.size() < maxItems()
                && in(mouseX, mouseY, x + 10, ty, 78, 14)) {
            addEditRow("", "");
            return true;
        }
        int footerY = y + h - 24;
        if (in(mouseX, mouseY, x + 8, footerY, 62, 16)) {
            saveForm();
            return true;
        }
        if (in(mouseX, mouseY, x + 74, footerY, 50, 16)) {
            backFromForm();
            return true;
        }
        return false;
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (view == VIEW_LIST) {
                close();
            } else if (view == VIEW_FORM) {
                backFromForm();
            } else {
                view = VIEW_LIST;
                TaskStore.get().requestSummary(groupId);
            }
            return true;
        }
        if (view == VIEW_FORM) {
            if (GuiScreenHelper.ctrlEnter(keyCode)) {
                saveForm();
                return true;
            }
            if (focus == FOCUS_TITLE) {
                if (keyCode == Keyboard.KEY_RETURN) {
                    focus = FOCUS_DESC;
                    return true;
                }
                titleField.textboxKeyTyped(typedChar, keyCode);
                return true;
            }
            if (focus == FOCUS_DESC) {
                descArea.keyTyped(typedChar, keyCode);
                return true;
            }
            if (focus == FOCUS_ITEM && focusItem >= 0 && focusItem < editRows.size()) {
                editRows.get(focusItem).field.textboxKeyTyped(typedChar, keyCode);
                return true;
            }
            return false;
        }
        if (view == VIEW_DETAIL) {
            Task task = TaskStore.get().getTask(groupId, selectedId);
            if (focus == FOCUS_COMMENT) {
                if (keyCode == Keyboard.KEY_RETURN) {
                    sendComment(task);
                    return true;
                }
                commentField.textboxKeyTyped(typedChar, keyCode);
                return true;
            }
            return false;
        }
        if (focus == FOCUS_SEARCH) {
            searchField.textboxKeyTyped(typedChar, keyCode);
            scroll = 0;
            return true;
        }
        return false;
    }

    boolean handleMouseInput() {
        if (!open) {
            return false;
        }
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return false;
        }
        scroll = clamp(scroll - Integer.signum(wheel) * 18, 0, maxScroll());
        return true;
    }

    boolean mouseDragged(int mouseX, int mouseY) {
        return open;
    }

    void mouseReleased() {
    }

    private int maxScroll() {
        if (view == VIEW_LIST) {
            return Math.max(0, filteredSummaries().size() * 46 - (contentBottom() - contentTop()));
        }
        if (view == VIEW_DETAIL) {
            Task task = TaskStore.get().getTask(groupId, selectedId);
            return task == null ? 0 : Math.max(0, detailContentHeight(task) - (panelY() + panelH() - 48 - (panelY() + 50)));
        }
        return Math.max(0, 100 + editRows.size() * 16 - (contentBottom() - contentTop()));
    }

    // ------------------------------------------------------------- form ops

    private void startCreate() {
        editingTask = null;
        view = VIEW_FORM;
        scroll = 0;
        focus = FOCUS_TITLE;
        confirmDelete = false;
        ensureFields();
        titleField.setText("");
        descArea.setText("");
        editRows.clear();
        addEditRow("", "");
        addEditRow("", "");
        addEditRow("", "");
    }

    private void startEdit(Task task) {
        if (task == null || task.deleted) {
            return;
        }
        editingTask = task;
        view = VIEW_FORM;
        scroll = 0;
        focus = FOCUS_TITLE;
        confirmDelete = false;
        ensureFields();
        titleField.setText(task.title);
        descArea.setText(task.desc);
        editRows.clear();
        if (task.items != null) {
            for (TaskItem item : task.items) {
                if (item != null) {
                    addEditRow(item.id, item.text);
                }
            }
        }
        if (editRows.isEmpty()) {
            addEditRow("", "");
        }
    }

    private void addEditRow(String id, String text) {
        if (editRows.size() >= maxItems()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GuiTextField field = new GuiTextField(1000 + editRows.size(), font, 0, 0, 100, 12);
        field.setMaxStringLength(TaskLimits.MAX_ITEM_TEXT);
        field.setText(text == null ? "" : text);
        editRows.add(new EditRow(id, field));
    }

    private void saveForm() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            notice("标题不能为空");
            return;
        }
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (EditRow row : editRows) {
            String text = row.field.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            ids.add(row.id == null ? "" : row.id);
            texts.add(text);
        }
        if (editingTask == null) {
            TaskStore.get().createTask(groupId, title, descArea.getText(), texts);
            view = VIEW_LIST;
            selectedId = "";
            scroll = 0;
            TaskStore.get().requestSummary(groupId);
            notice("已发布任务");
        } else {
            TaskStore.get().updateTask(editingTask, title, descArea.getText(), ids, texts);
            selectedId = editingTask.id;
            view = VIEW_DETAIL;
            scroll = 0;
            notice("已保存");
        }
        focus = FOCUS_NONE;
    }

    private void backFromForm() {
        if (editingTask == null) {
            view = VIEW_LIST;
        } else {
            view = VIEW_DETAIL;
            selectedId = editingTask.id;
        }
        scroll = 0;
        focus = FOCUS_NONE;
        confirmDelete = false;
    }

    private void sendComment(Task task) {
        if (task == null || task.deleted) {
            return;
        }
        String text = commentField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        TaskStore.get().addComment(task, text);
        commentField.setText("");
        focus = FOCUS_NONE;
    }

    private void notice(String text) {
        localNotice = text == null ? "" : text;
        localNoticeAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------- render util

    private boolean button(int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && in(mouseX, mouseY, x, y, w, h);
        int bg = !enabled ? ChatColors.SIDEBAR_BG : hover ? ChatColors.ACCENT_DIM : ChatColors.SIDEBAR_HOVER;
        ChatRender.roundRect(x, y, x + w, y + h, bg, 2);
        if (hover) {
            ChatRender.roundRect(x, y, x + w, y + 1, ChatColors.ACCENT, 1);
        }
        int color = enabled ? ChatColors.TEXT_PRIMARY : ChatColors.TASK_MUTED;
        font().drawStringWithShadow(label, x + (w - font().getStringWidth(label)) / 2,
                y + (h - 8) / 2, color);
        return hover;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void bounds(GuiTextField field, int x, int y, int w, int h) {
        field.x = x;
        field.y = y;
        field.width = w;
        field.height = h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int maxItems() {
        return clamp(QuantumHueConfig.taskBoard.maxItemsPerTask, 1, 50);
    }

    private void clipOn(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, Minecraft.getMinecraft().displayHeight - (y + h) * scale,
                w * scale, h * scale);
    }

    private void clipOff() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static final class EditRow {
        final String id;
        final GuiTextField field;

        EditRow(String id, GuiTextField field) {
            this.id = id;
            this.field = field;
        }
    }

    /** 只为了 Ctrl+Enter 判断，避免和 GuiScreen 的静态方法纠缠。 */
    private static final class GuiScreenHelper {
        static boolean ctrlEnter(int keyCode) {
            return keyCode == Keyboard.KEY_RETURN
                    && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        }
    }
}
