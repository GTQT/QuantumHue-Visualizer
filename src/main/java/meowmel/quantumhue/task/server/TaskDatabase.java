package meowmel.quantumhue.task.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meowmel.quantumhue.task.model.Task;
import meowmel.quantumhue.task.model.TaskComment;
import meowmel.quantumhue.task.model.TaskItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务板持久化：&lt;world&gt;/quantumhue/tasks.json。
 *
 * <p>结构：{@code {schema:1, nextTaskId, groups:{<gid>:{tasks:[...]}}}}。
 * 写入走临时文件 + 原子 rename，避免半截文件。
 */
public final class TaskDatabase {

    private static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Map<String, Map<String, Task>> groups = new LinkedHashMap<>();
    private int nextTaskId;
    private boolean dirty;
    private long lastSave;

    public TaskDatabase(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public synchronized void load() {
        groups.clear();
        nextTaskId = 0;
        if (file == null || !file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement rootElem = new JsonParser().parse(reader);
            if (rootElem == null || !rootElem.isJsonObject()) {
                return;
            }
            JsonObject root = rootElem.getAsJsonObject();
            if (root.has("nextTaskId")) {
                nextTaskId = Math.max(0, root.get("nextTaskId").getAsInt());
            }
            JsonObject groupsObj = root.has("groups") && root.get("groups").isJsonObject()
                    ? root.getAsJsonObject("groups") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }
                Map<String, Task> map = new LinkedHashMap<>();
                JsonArray arr = entry.getValue().getAsJsonArray();
                for (JsonElement taskElem : arr) {
                    Task task = readTask(taskElem);
                    if (task != null && task.id != null && !task.id.isEmpty()) {
                        map.put(task.id, task);
                    }
                }
                groups.put(entry.getKey(), map);
            }
        } catch (Exception ignored) {
            // 文件坏了就当作空任务板，至少不阻止进服。
        } finally {
            closeQuietly(reader);
        }
    }

    private Task readTask(JsonElement elem) {
        try {
            Task task = GSON.fromJson(elem, Task.class);
            if (task == null) {
                return null;
            }
            if (task.items == null) {
                task.items = new ArrayList<>();
            }
            if (task.comments == null) {
                task.comments = new ArrayList<>();
            }
            sanitizeTask(task);
            return task;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sanitizeTask(Task task) {
        if (task.creatorUuid == null) {
            task.creatorUuid = "";
        }
        if (task.creatorName == null) {
            task.creatorName = "";
        }
        if (task.title == null) {
            task.title = "";
        }
        if (task.desc == null) {
            task.desc = "";
        }
        for (Iterator<TaskItem> it = task.items.iterator(); it.hasNext(); ) {
            TaskItem item = it.next();
            if (item == null || item.id == null || item.id.isEmpty()) {
                it.remove();
            }
        }
        for (Iterator<TaskComment> it = task.comments.iterator(); it.hasNext(); ) {
            TaskComment comment = it.next();
            if (comment == null || comment.id == null || comment.id.isEmpty()) {
                it.remove();
            }
        }
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("nextTaskId", nextTaskId);
        JsonObject groupsObj = new JsonObject();
        for (Map.Entry<String, Map<String, Task>> entry : groups.entrySet()) {
            JsonObject groupObj = new JsonObject();
            JsonArray tasks = new JsonArray();
            for (Task task : entry.getValue().values()) {
                tasks.add(GSON.toJsonTree(task));
            }
            groupObj.add("tasks", tasks);
            groupsObj.add(entry.getKey(), groupObj);
        }
        root.add("groups", groupsObj);

        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            GSON.toJson(root, writer);
            writer.flush();
            closeQuietly(writer);
            writer = null;
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Exception ignored) {
            // 保存失败下一轮再试。
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    public synchronized Map<String, Task> group(String groupId) {
        Map<String, Task> map = groups.get(groupId);
        if (map == null) {
            map = new LinkedHashMap<>();
            groups.put(groupId, map);
        }
        return map;
    }

    public synchronized Task get(String groupId, String taskId) {
        Map<String, Task> map = groups.get(groupId);
        return map == null ? null : map.get(taskId);
    }

    public synchronized void put(Task task) {
        if (task == null || task.groupId == null || task.id == null) {
            return;
        }
        group(task.groupId).put(task.id, task);
        dirty = true;
    }

    public synchronized List<Task> all(String groupId) {
        Map<String, Task> map = groups.get(groupId);
        if (map == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(map.values());
    }

    public synchronized String allocateTaskId() {
        nextTaskId++;
        dirty = true;
        return "t" + nextTaskId;
    }

    public synchronized int nextItemNumber(Task task) {
        int max = 0;
        if (task != null && task.items != null) {
            for (TaskItem item : task.items) {
                String id = item == null ? "" : item.id;
                if (id != null && id.length() > 1 && id.charAt(0) == 'i') {
                    try {
                        max = Math.max(max, Integer.parseInt(id.substring(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return max + 1;
    }

    public synchronized int nextCommentNumber(Task task) {
        int max = 0;
        if (task != null && task.comments != null) {
            for (TaskComment comment : task.comments) {
                String id = comment == null ? "" : comment.id;
                if (id != null && id.length() > 1 && id.charAt(0) == 'c') {
                    try {
                        max = Math.max(max, Integer.parseInt(id.substring(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return max + 1;
    }

    public synchronized void pruneGroup(String groupId, int maxTasks) {
        Map<String, Task> map = group(groupId);
        if (map.size() <= maxTasks) {
            return;
        }
        List<Task> all = new ArrayList<>(map.values());
        all.sort((a, b) -> {
            if (a.deleted != b.deleted) {
                return a.deleted ? -1 : 1;
            }
            if (a.completed != b.completed) {
                return a.completed ? -1 : 1;
            }
            return Long.compare(a.updatedAt, b.updatedAt);
        });
        int remove = map.size() - maxTasks;
        for (Task task : all) {
            if (remove <= 0) {
                break;
            }
            map.remove(task.id);
            remove--;
        }
        dirty = true;
    }

    public synchronized void archiveOld(long now, long archiveMs) {
        boolean changed = false;
        for (Map<String, Task> map : groups.values()) {
            for (Iterator<Task> it = map.values().iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.completed && task.completedAt > 0
                        && now - task.completedAt > archiveMs) {
                    it.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized long lastSave() {
        return lastSave;
    }

    public synchronized void markDirty() {
        dirty = true;
    }
}
