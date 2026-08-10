package meowmel.quantumhue.createworld.api.gamerule;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 游戏规则 tooltip 注册表——优先语言文件（quantumhue.gamerule.&lt;rule&gt;.tooltip.description），
 * 其次注册表，最后英文默认描述。
 * <p>Registry for game rule tooltips; lang file, registry, then English defaults.</p>
 */
@SideOnly(Side.CLIENT)
public class GameRuleTooltipRegistry {

    private static final Logger LOGGER = LogManager.getLogger("GameRuleTooltipRegistry");

    private static final Map<String, String> registeredTooltips = new HashMap<>();
    private static final Map<String, String> DEFAULT_DESCRIPTIONS = new LinkedHashMap<>();

    public static void registerTooltip(String ruleName, String tooltip) {
        if (ruleName == null || ruleName.isEmpty()) {
            LOGGER.warn("Cannot register tooltip with null or empty rule name");
            return;
        }
        if (tooltip == null || tooltip.isEmpty()) {
            LOGGER.warn("Cannot register null tooltip for rule: {}", ruleName);
            return;
        }
        registeredTooltips.put(ruleName, tooltip);
        LOGGER.debug("Registered tooltip for gamerule: {} -> {}", ruleName, tooltip);
    }

    public static void registerTooltips(Map<String, String> tooltips) {
        if (tooltips == null) {
            LOGGER.warn("Cannot register null tooltips map");
            return;
        }
        int count = 0;
        for (Map.Entry<String, String> entry : tooltips.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            registeredTooltips.put(entry.getKey(), entry.getValue());
            count++;
        }
        LOGGER.debug("Registered {} tooltips", count);
    }

    public static String getTooltip(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return null;
        }
        String translationKey = "quantumhue.gamerule." + ruleName + ".tooltip.description";
        String translated = I18n.format(translationKey);
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
            return translated;
        }
        if (registeredTooltips.containsKey(ruleName)) {
            return registeredTooltips.get(ruleName);
        }
        if (DEFAULT_DESCRIPTIONS.containsKey(ruleName)) {
            return DEFAULT_DESCRIPTIONS.get(ruleName);
        }
        return null;
    }

    public static boolean hasRegisteredTooltip(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }
        return registeredTooltips.containsKey(ruleName);
    }

    public static boolean removeTooltip(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }
        boolean removed = registeredTooltips.remove(ruleName) != null;
        if (removed) {
            LOGGER.debug("Removed tooltip for gamerule: {}", ruleName);
        }
        return removed;
    }

    public static void clearAllTooltips() {
        registeredTooltips.clear();
        LOGGER.info("Cleared all registered tooltips");
    }

    public static int getRegisteredCount() {
        return registeredTooltips.size();
    }

    public static Map<String, String> getAllRegisteredTooltips() {
        return new HashMap<>(registeredTooltips);
    }

    static {
        DEFAULT_DESCRIPTIONS.put("doFireTick", "Controls whether fire spreads and naturally extinguishes");
        DEFAULT_DESCRIPTIONS.put("mobGriefing", "Controls whether mobs can destroy blocks");
        DEFAULT_DESCRIPTIONS.put("keepInventory", "Keep inventory after death");
        DEFAULT_DESCRIPTIONS.put("doMobSpawning", "Natural mob spawning");
        DEFAULT_DESCRIPTIONS.put("doMobLoot", "Mobs drop loot");
        DEFAULT_DESCRIPTIONS.put("doTileDrops", "Blocks drop items when destroyed");
        DEFAULT_DESCRIPTIONS.put("commandBlockOutput", "Command blocks output to chat");
        DEFAULT_DESCRIPTIONS.put("naturalRegeneration", "Natural health regeneration");
        DEFAULT_DESCRIPTIONS.put("doDaylightCycle", "Day/night cycle");
    }
}
