package meowmel.quantumhue.wiki;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki content registry. Supports both JSON-based and code-based registration.
 * Code-registered categories (via {@link WikiRegistry}) are displayed
 * above JSON-registered categories by default.
 */
public final class WikiContent {

    private static List<WikiCategory> CATEGORIES;

    /** 代码注册的分类（优先显示） */
    private static final List<WikiCategory> CODE_CATEGORIES = new ArrayList<>();

    /**
     * 通过代码注册一个分类。
     * 注册的分类会立即生效，并显示在 JSON 分类上方。
     */
    public static void register(WikiCategory category) {
        CODE_CATEGORIES.add(category);
        // 如果已加载，同步插入到最前面（保持代码注册优先）
        if (CATEGORIES != null) {
            // 重新构建完整列表，保证顺序
            rebuild();
        }
    }

    /** 获取最新注册的代码分类（供 {@link WikiRegistry#page} 追加页面用） */
    static WikiCategory getLastCodeCategory() {
        if (CODE_CATEGORIES.isEmpty()) return null;
        return CODE_CATEGORIES.get(CODE_CATEGORIES.size() - 1);
    }

    public static List<WikiCategory> getCategories() {
        if (CATEGORIES == null) {
            rebuild();
        }
        return CATEGORIES;
    }

    /** 合并代码注册 + JSON 注册的分类列表 */
    private static void rebuild() {
        List<WikiCategory> json = WikiJsonLoader.loadCategories();
        CATEGORIES = new ArrayList<>(CODE_CATEGORIES.size() + json.size());
        CATEGORIES.addAll(CODE_CATEGORIES);   // 代码注册优先
        CATEGORIES.addAll(json);              // JSON 注册在后
    }

    /**
     * Force reload from JSON (e.g. after resource pack change).
     * Code-registered categories are preserved.
     */
    public static void reload() {
        CATEGORIES = null;
    }
}