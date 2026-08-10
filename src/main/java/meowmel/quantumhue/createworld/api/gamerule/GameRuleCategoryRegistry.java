package meowmel.quantumhue.createworld.api.gamerule;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏规则分类注册表——按分类组织规则列表；分类键即翻译键（quantumhue.gamerule.category.*）。
 * <p>Registry mapping game rules to categories; the category key is a translation key.</p>
 */
@SideOnly(Side.CLIENT)
public class GameRuleCategoryRegistry {

    private static final Logger LOGGER = LogManager.getLogger("GameRuleCategoryRegistry");

    private static final Map<String, List<String>> categoryMap = new LinkedHashMap<>();
    private static final Map<String, String> ruleToCategory = new HashMap<>();
    private static final Map<String, List<String>> VANILLA_DEFAULT_CATEGORIES = new LinkedHashMap<>();
    private static boolean defaultsInitialized = false;

    private static void initializeDefaults() {
        if (defaultsInitialized) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : VANILLA_DEFAULT_CATEGORIES.entrySet()) {
            String categoryKey = entry.getKey();
            List<String> ruleNames = entry.getValue();
            if (!categoryMap.containsKey(categoryKey)) {
                categoryMap.put(categoryKey, new ArrayList<>());
            }
            for (String ruleName : ruleNames) {
                categoryMap.get(categoryKey).add(ruleName);
                ruleToCategory.put(ruleName, categoryKey);
            }
        }
        defaultsInitialized = true;
        LOGGER.debug("Initialized default game rule categories");
    }

    public static void createCategory(String categoryKey, List<String> ruleNames) {
        if (categoryKey == null || categoryKey.isEmpty()) {
            LOGGER.warn("Cannot create category with null or empty key");
            return;
        }
        if (ruleNames == null) {
            LOGGER.warn("Cannot create category with null rule list");
            return;
        }
        initializeDefaults();
        categoryMap.put(categoryKey, new ArrayList<>(ruleNames));
        for (String ruleName : ruleNames) {
            if (ruleName == null || ruleName.isEmpty()) {
                continue;
            }
            ruleToCategory.put(ruleName, categoryKey);
        }
        LOGGER.debug("Created category: {} with {} rules", categoryKey, ruleNames.size());
    }

    public static void addRuleToCategory(String categoryKey, String ruleName) {
        if (categoryKey == null || categoryKey.isEmpty()) {
            LOGGER.warn("Cannot add rule to null or empty category key");
            return;
        }
        if (ruleName == null || ruleName.isEmpty()) {
            LOGGER.warn("Cannot add null or empty rule name");
            return;
        }
        initializeDefaults();
        if (!categoryMap.containsKey(categoryKey)) {
            categoryMap.put(categoryKey, new ArrayList<>());
        }
        List<String> rules = categoryMap.get(categoryKey);
        if (!rules.contains(ruleName)) {
            rules.add(ruleName);
            ruleToCategory.put(ruleName, categoryKey);
            LOGGER.debug("Added rule {} to category {}", ruleName, categoryKey);
        }
    }

    public static void addRulesToCategory(String categoryKey, List<String> ruleNames) {
        if (categoryKey == null || categoryKey.isEmpty()) {
            LOGGER.warn("Cannot add rules to null or empty category key");
            return;
        }
        if (ruleNames == null) {
            LOGGER.warn("Cannot add null rule list");
            return;
        }
        initializeDefaults();
        if (!categoryMap.containsKey(categoryKey)) {
            categoryMap.put(categoryKey, new ArrayList<>());
        }
        List<String> rules = categoryMap.get(categoryKey);
        for (String ruleName : ruleNames) {
            if (ruleName == null || ruleName.isEmpty() || rules.contains(ruleName)) {
                continue;
            }
            rules.add(ruleName);
            ruleToCategory.put(ruleName, categoryKey);
        }
        LOGGER.debug("Added {} rules to category {}", ruleNames.size(), categoryKey);
    }

    public static List<String> getRulesInCategory(String categoryKey) {
        initializeDefaults();
        List<String> rules = categoryMap.get(categoryKey);
        if (rules == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(rules);
    }

    public static String getCategoryForRule(String ruleName) {
        initializeDefaults();
        return ruleToCategory.get(ruleName);
    }

    public static List<String> getAllCategories() {
        initializeDefaults();
        return Collections.unmodifiableList(new ArrayList<>(categoryMap.keySet()));
    }

    /** 分类显示名（分类键即翻译键，翻译失败回退键本身） */
    public static String getCategoryDisplayName(String categoryKey) {
        if (categoryKey == null || categoryKey.isEmpty()) {
            return categoryKey;
        }
        String translated = I18n.format(categoryKey);
        if (translated != null && !translated.isEmpty() && !translated.equals(categoryKey)) {
            return translated;
        }
        return categoryKey;
    }

    public static boolean removeRuleFromCategory(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }
        initializeDefaults();
        String categoryKey = ruleToCategory.get(ruleName);
        if (categoryKey == null) {
            return false;
        }
        List<String> rules = categoryMap.get(categoryKey);
        if (rules != null) {
            boolean removed = rules.remove(ruleName);
            if (removed) {
                ruleToCategory.remove(ruleName);
                LOGGER.debug("Removed rule {} from category {}", ruleName, categoryKey);
            }
            return removed;
        }
        return false;
    }

    public static boolean removeCategory(String categoryKey) {
        if (categoryKey == null || categoryKey.isEmpty()) {
            return false;
        }
        initializeDefaults();
        List<String> rules = categoryMap.remove(categoryKey);
        if (rules != null) {
            for (String ruleName : rules) {
                ruleToCategory.remove(ruleName);
            }
            LOGGER.debug("Removed category: {} with {} rules", categoryKey, rules.size());
            return true;
        }
        return false;
    }

    public static void clearCustomCategories() {
        categoryMap.clear();
        ruleToCategory.clear();
        defaultsInitialized = false;
        initializeDefaults();
        LOGGER.info("Cleared all custom categories, restored defaults");
    }

    public static void clearAllCategories() {
        categoryMap.clear();
        ruleToCategory.clear();
        defaultsInitialized = false;
        LOGGER.info("Cleared all categories");
    }

    public static int getCategoryCount() {
        initializeDefaults();
        return categoryMap.size();
    }

    public static Map<String, List<String>> getAllCategoriesMap() {
        initializeDefaults();
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return copy;
    }

    static {
        List<String> worldRules = new ArrayList<>();
        worldRules.add("doFireTick");
        worldRules.add("doTileDrops");
        worldRules.add("doDaylightCycle");
        VANILLA_DEFAULT_CATEGORIES.put("quantumhue.gamerule.category.world", worldRules);

        List<String> mobsRules = new ArrayList<>();
        mobsRules.add("doMobSpawning");
        mobsRules.add("doMobLoot");
        mobsRules.add("mobGriefing");
        VANILLA_DEFAULT_CATEGORIES.put("quantumhue.gamerule.category.mobs", mobsRules);

        List<String> playerRules = new ArrayList<>();
        playerRules.add("naturalRegeneration");
        playerRules.add("keepInventory");
        VANILLA_DEFAULT_CATEGORIES.put("quantumhue.gamerule.category.player", playerRules);

        List<String> chatRules = new ArrayList<>();
        chatRules.add("commandBlockOutput");
        VANILLA_DEFAULT_CATEGORIES.put("quantumhue.gamerule.category.chat", chatRules);

        defaultsInitialized = false;
    }
}
