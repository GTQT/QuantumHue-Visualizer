package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

public class WikiPage {
    public final String id;
    public final String title;
    public final Supplier<ItemStack> icon;

    /**
     * 完整 Markdown 内容
     */
    public String markdownContent = "";

    public WikiPage(String id, String title, Supplier<ItemStack> icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
    }

    public WikiPage content(String md) {
        this.markdownContent = md;
        return this;
    }
}