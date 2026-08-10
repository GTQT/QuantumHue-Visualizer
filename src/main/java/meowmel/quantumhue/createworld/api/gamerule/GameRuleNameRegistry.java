package meowmel.quantumhue.createworld.api.gamerule;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏规则显示名注册表——优先语言文件（quantumhue.gamerule.&lt;rule&gt;.name），其次注册表。
 * <p>Registry for game rule display names; lang file takes priority.</p>
 */
@SideOnly(Side.CLIENT)
public class GameRuleNameRegistry {

    private static final Logger LOGGER = LogManager.getLogger("GameRuleNameRegistry");

    private static final Map<String, String> registeredNames = new HashMap<>();

    public static void registerName(String ruleName, String displayName) {
        if (ruleName == null || ruleName.isEmpty()) {
            LOGGER.warn("Cannot register display name with null or empty rule name");
            return;
        }
        if (displayName == null || displayName.isEmpty()) {
            LOGGER.warn("Cannot register null or empty display name for rule: {}", ruleName);
            return;
        }
        registeredNames.put(ruleName, displayName);
        LOGGER.debug("Registered display name for gamerule: {} -> {}", ruleName, displayName);
    }

    public static void registerNames(Map<String, String> names) {
        if (names == null) {
            LOGGER.warn("Cannot register null names map");
            return;
        }
        int count = 0;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            registeredNames.put(entry.getKey(), entry.getValue());
            count++;
        }
        LOGGER.debug("Registered {} display names", count);
    }

    public static String getName(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return ruleName;
        }
        String translationKey = "quantumhue.gamerule." + ruleName + ".name";
        String translated = I18n.format(translationKey);
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
            return translated;
        }
        if (registeredNames.containsKey(ruleName)) {
            return registeredNames.get(ruleName);
        }
        return ruleName;
    }

    public static boolean hasRegisteredName(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }
        return registeredNames.containsKey(ruleName);
    }

    public static boolean removeName(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }
        boolean removed = registeredNames.remove(ruleName) != null;
        if (removed) {
            LOGGER.debug("Removed display name for gamerule: {}", ruleName);
        }
        return removed;
    }

    public static void clearAllNames() {
        registeredNames.clear();
        LOGGER.info("Cleared all registered display names");
    }

    public static int getRegisteredCount() {
        return registeredNames.size();
    }

    public static Map<String, String> getAllRegisteredNames() {
        return new HashMap<>(registeredNames);
    }
}
