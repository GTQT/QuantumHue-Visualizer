package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

public class WikiPage {
    public final String id;
    public final String title;
    public final Supplier<ItemStack> icon;

    /** 用于携带自定义附加数据（如多方块预览渲染器） */
    public Object attachment;

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

    @SuppressWarnings("unchecked")
    public <T> T getAttachment(Class<T> type) {
        return attachment != null && type.isInstance(attachment) ? (T) attachment : null;
    }
}