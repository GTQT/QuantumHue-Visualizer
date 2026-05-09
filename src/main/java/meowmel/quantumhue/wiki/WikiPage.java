package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class WikiPage {

    public final String id;
    public final String title;
    public final Supplier<ItemStack> icon;
    public final List<Section> sections = new ArrayList<>();

    /** Progression tier: 0 = always visible, 1 = steam, 2 = LV, 3 = MV, etc. */
    public int tier = 0;
    /** Hint shown when the page is locked, e.g. "Craft a Wrench to learn more" */
    public String discoveryHint = "";
    /** Tag used by item-pickup discovery, e.g. "wrench", "steam_boiler" */
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

    public WikiPage text(String content) {
        sections.add(new Section(SectionType.TEXT, content));
        return this;
    }

    public WikiPage heading(String text) {
        sections.add(new Section(SectionType.HEADING, text));
        return this;
    }

    public WikiPage subheading(String text) {
        sections.add(new Section(SectionType.SUBHEADING, text));
        return this;
    }

    public WikiPage table(String... rows) {
        sections.add(new Section(SectionType.TABLE, String.join("\n", rows)));
        return this;
    }

    public WikiPage diagram(String... lines) {
        sections.add(new Section(SectionType.DIAGRAM, String.join("\n", lines)));
        return this;
    }

    public WikiPage gap() {
        sections.add(new Section(SectionType.GAP, ""));
        return this;
    }

    public enum SectionType {
        TEXT,
        HEADING,
        SUBHEADING,
        TABLE,
        DIAGRAM,
        GAP
    }

    public static class Section {

        public final SectionType type;
        public final String text;

        public Section(SectionType type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}