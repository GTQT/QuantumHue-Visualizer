package meowmel.quantumhue.wiki;

import java.util.List;

/**
 * Wiki content registry. Loads all categories and pages from JSON files
 * located in assets/wiki/. Players can edit, add, or remove
 * wiki entries by modifying these JSON files or using a resource pack.
 */
public final class WikiContent {

    private static List<WikiCategory> CATEGORIES;

    public static List<WikiCategory> getCategories() {
        if (CATEGORIES == null) {
            CATEGORIES = WikiJsonLoader.loadCategories();
        }
        return CATEGORIES;
    }

    /**
     * Force reload from JSON (e.g. after resource pack change).
     */
    public static void reload() {
        CATEGORIES = null;
    }
}