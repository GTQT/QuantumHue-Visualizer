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

    public int tier = 0;
    public String discoveryHint = "";
    public String discoveryTag = "";

    public WikiPage(String id, String title, Supplier<ItemStack> icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
    }

    public WikiPage tier(int tier) {
        this.tier = tier;
        return this;
    }

    public WikiPage hint(String hint) {
        this.discoveryHint = hint;
        return this;
    }

    public WikiPage tag(String tag) {
        this.discoveryTag = tag;
        return this;
    }

    public WikiPage content(String md) {
        this.markdownContent = md;
        return this;
    }
}