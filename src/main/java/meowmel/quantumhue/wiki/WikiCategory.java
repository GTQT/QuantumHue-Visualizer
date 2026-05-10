package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class WikiCategory {

    public final String name;
    public final Supplier<ItemStack> icon;
    public final List<WikiPage> pages = new ArrayList<>();
    public boolean collapsed = true;

    public WikiCategory(String name, Supplier<ItemStack> icon) {
        this.name = name;
        this.icon = icon;
    }

    public WikiCategory add(WikiPage page) {
        pages.add(page);
        return this;
    }
}