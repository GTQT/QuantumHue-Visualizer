package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wiki 分类 Builder — 用于代码注册分类时链式构建。
 * <pre>
 * new WikiCategoryBuilder("GTQT攻略", () -> new ItemStack(Items.COMPASS))
 *     .page(new WikiPageBuilder("intro", "简介", icon).content("# 简介"))
 *     .page(new WikiPageBuilder("story", "故事", icon).content("# 背景故事"))
 * </pre>
 */
public class WikiCategoryBuilder {

    private final String name;
    private final Supplier<ItemStack> icon;
    private final List<WikiPageBuilder> pageBuilders = new ArrayList<>();

    public WikiCategoryBuilder(String name, Supplier<ItemStack> icon) {
        this.name = name;
        this.icon = icon;
    }

    /** 添加一个页面到该分类 */
    public WikiCategoryBuilder page(WikiPageBuilder page) {
        pageBuilders.add(page);
        return this;
    }

    /* 包级：构建为 WikiCategory 实例 */
    WikiCategory build() {
        WikiCategory cat = new WikiCategory(name, icon);
        for (WikiPageBuilder pb : pageBuilders) {
            cat.add(pb.build());
        }
        return cat;
    }
}
