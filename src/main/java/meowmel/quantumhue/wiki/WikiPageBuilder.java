package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

/**
 * Wiki 页面 Builder — 用于代码注册页面时链式构建。
 * <pre>
 * new WikiPageBuilder("welcome", "欢迎页面", () -> new ItemStack(Items.BOOK))
 *     .content("# 欢迎\n\n这是正文内容")
 * </pre>
 */
public class WikiPageBuilder {

    private final String id;
    private final String title;
    private final Supplier<ItemStack> icon;
    private String markdown = "";
    private Object attachment;

    public WikiPageBuilder(String id, String title, Supplier<ItemStack> icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
    }

    /** 设置页面正文 Markdown 内容 */
    public WikiPageBuilder content(String md) {
        this.markdown = md;
        return this;
    }

    /** 设置附加数据（如多方块渲染器引用） */
    public WikiPageBuilder attach(Object obj) {
        this.attachment = obj;
        return this;
    }

    /* 包级：构建为 WikiPage 实例 */
    WikiPage build() {
        WikiPage page = new WikiPage(id, title, icon).content(markdown);
        page.attachment = attachment;
        return page;
    }
}
