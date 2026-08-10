package meowmel.quantumhue.createworld.api.tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * 标签页注册表。
 * <p>Registry for tabs of the create-world screen.</p>
 * 冻结（freeze）后不可再注册——在首次打开创建世界界面时由 {@link TabManager} 冻结。
 */
public final class TabRegistry {

    private static final List<TabEntry> entries = new ArrayList<>();
    private static boolean frozen = false;

    private TabRegistry() {
    }

    /**
     * 注册一个标签页。
     * @param factory 标签页工厂 / tab factory
     * @param tabId 标签页 ID（也作为 tab 按钮的按钮 ID，建议 100 起）/ tab id (also the button id, suggest >= 100)
     * @param nameKey 名称翻译键 / name translation key
     * @param priority 排序优先级，小在前 / sort priority, ascending
     */
    public static void registerTab(Supplier<Tab> factory, int tabId, String nameKey, int priority) {
        if (frozen) {
            throw new IllegalStateException("TabRegistry is already frozen. Tabs must be registered before GUI initialization.");
        }
        for (TabEntry entry : entries) {
            if (entry.tabId == tabId) {
                throw new IllegalArgumentException("Tab ID " + tabId + " is already registered by " + entry.nameKey);
            }
        }
        entries.add(new TabEntry(factory, tabId, nameKey, priority));
    }

    public static void registerTab(Supplier<Tab> factory, int tabId, String nameKey) {
        registerTab(factory, tabId, nameKey, tabId);
    }

    /** 按 priority、再按 tabId 排序的注册表快照 / sorted snapshot of the registry */
    public static List<TabEntry> getEntries() {
        ArrayList<TabEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt((TabEntry e) -> e.priority).thenComparingInt((TabEntry e) -> e.tabId));
        return Collections.unmodifiableList(sorted);
    }

    public static void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static void clear() {
        entries.clear();
        frozen = false;
    }

    public static final class TabEntry {
        public final Supplier<Tab> factory;
        public final int tabId;
        public final String nameKey;
        public final int priority;

        TabEntry(Supplier<Tab> factory, int tabId, String nameKey, int priority) {
            this.factory = factory;
            this.tabId = tabId;
            this.nameKey = nameKey;
            this.priority = priority;
        }
    }
}
