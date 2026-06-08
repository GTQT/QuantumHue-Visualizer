package meowmel.quantumhue.wiki;

/**
 * Wiki 代码注册入口 — 链式 Builder API，与 JSON 注册不冲突。
 * <p>
 * 使用方式（在任意 {@code @Init} 或构造阶段调用）：
 * <pre>
 * wiki.builder()
 *     .category(new WikiCategoryBuilder("GTQT整合包攻略", icon))
 *     .page(new WikiPageBuilder("intro", "简介", icon).content("# 简介"))
 *     .register();
 * </pre>
 * 代码注册的分类默认显示在 JSON 分类上方。
 */
public final class WikiRegistry {

    /* ═══════════════ 入口 ═══════════════ */

    /** 获取 Builder 实例，建议通过 {@code wiki} 静态导入调用 */
    public static WikiRegistry builder() {
        return new WikiRegistry();
    }

    /* ─────────────────────────────────── */

    private WikiCategoryBuilder current;

    private WikiRegistry() {
    }

    /**
     * 添加一个分类。后续 {@link #page(WikiPageBuilder)} 添加的页面将归入该分类。
     */
    public WikiRegistry category(WikiCategoryBuilder cat) {
        WikiCategory built = cat.build();
        WikiContent.register(built);
        return this;
    }

    /**
     * 添加一个页面到最近添加的分类中。
     * 如果在调用 {@code category()} 之前调用将抛出 {@link IllegalStateException}。
     */
    public WikiRegistry page(WikiPageBuilder page) {
        // 将页面暂存，等下一个 category() 或 register() 时归入上一个分类
        // 但由于我们已在 category() 中立即 build，此处需要找到已注册的最后一个分类追加页面
        WikiCategory last = WikiContent.getLastCodeCategory();
        if (last == null) {
            throw new IllegalStateException("必须在 .category(...) 之后调用 .page(...)");
        }
        last.add(page.build());
        return this;
    }

    /**
     * 终止构建并完成注册。
     * 实际上所有注册在 {@link #category(WikiCategoryBuilder)} 时已完成，
     * 此方法仅作为语义化的终止标记（同 {@code #register()}）。
     */
    public void register() {
        // 所有工作已在 category()/page() 中完成
    }
}
