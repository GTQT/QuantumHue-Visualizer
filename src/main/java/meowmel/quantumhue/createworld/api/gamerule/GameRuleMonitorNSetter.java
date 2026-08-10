package meowmel.quantumhue.createworld.api.gamerule;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 游戏规则监控与设置器——提供游戏规则的获取、设置、添加等操作，支持多种数据类型。
 * <p>Monitor and setter for game rules; supports multiple value types.</p>
 */
@SideOnly(Side.CLIENT)
public class GameRuleMonitorNSetter {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:GameruleMonitorAndSetter");

    /**
     * 游戏规则值容器——同时保存字符串/布尔/整数/浮点四种形式。
     * <p>Value container holding all four representations.</p>
     */
    public static class GameruleValue {

        public final String stringValue;
        public final boolean booleanValue;
        public final int intValue;
        public final double doubleValue;

        public GameruleValue(String stringValue, boolean booleanValue, int intValue, double doubleValue) {
            this.stringValue = stringValue;
            this.booleanValue = booleanValue;
            this.intValue = intValue;
            this.doubleValue = doubleValue;
        }

        @Override
        public String toString() {
            return String.format("String: %s, Boolean: %b, Int: %d, Double: %.2f",
                    stringValue, booleanValue, intValue, doubleValue);
        }

        /** 按字符串内容推断最合适的类型：整数 > 浮点 > 布尔 > 字符串 */
        public Object getOptimalValue() {
            if (stringValue.matches("-?\\d+")) {
                return intValue;
            }
            if (stringValue.matches("-?\\d+\\.\\d+")) {
                return doubleValue;
            }
            if ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
                return booleanValue;
            }
            return stringValue;
        }
    }

    /** 获取世界全部游戏规则的完整值 */
    public static Map<String, GameruleValue> getAllGamerules(World world) {
        Map<String, GameruleValue> gamerules = new HashMap<>();
        if (world == null) {
            LOGGER.warn("World object is null, returning empty gamerule map");
            return gamerules;
        }
        GameRules gameRules = world.getGameRules();
        for (String ruleName : gameRules.getRules()) {
            GameruleValue value = getGamerule(world, ruleName);
            if (value != null) {
                gamerules.put(ruleName, value);
            }
        }
        LOGGER.debug("Retrieved {} gamerules from world", gamerules.size());
        return gamerules;
    }

    /** 获取特定游戏规则的完整值（数值经反射读取 GameRules.Value 内部字段） */
    public static GameruleValue getGamerule(World world, String ruleName) {
        if (world == null) {
            LOGGER.warn("World object is null, cannot get gamerule: {}", ruleName);
            return null;
        }
        if (!world.getGameRules().hasRule(ruleName)) {
            LOGGER.debug("Gamerule does not exist: {}", ruleName);
            return null;
        }
        GameRules gameRules = world.getGameRules();
        String stringValue = gameRules.getString(ruleName);
        boolean booleanValue = gameRules.getBoolean(ruleName);
        int intValue = 0;
        double doubleValue = 0.0;
        try {
            Field field = GameRules.class.getDeclaredField("theGameRules");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            TreeMap<String, Object> rulesMap = (TreeMap<String, Object>) field.get(gameRules);
            Object valueObj = rulesMap.get(ruleName);
            if (valueObj != null) {
                Field intField = valueObj.getClass().getDeclaredField("valueInteger");
                Field doubleField = valueObj.getClass().getDeclaredField("valueDouble");
                intField.setAccessible(true);
                doubleField.setAccessible(true);
                intValue = intField.getInt(valueObj);
                doubleValue = doubleField.getDouble(valueObj);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve numeric values for gamerule {} via reflection: {}", ruleName, e.getMessage());
            try {
                intValue = Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
            }
            try {
                doubleValue = Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return new GameruleValue(stringValue, booleanValue, intValue, doubleValue);
    }

    /** 设置游戏规则值（转换为字符串存储） */
    public static boolean setGamerule(World world, String ruleName, Object value) {
        if (world == null) {
            LOGGER.warn("World object is null, cannot set gamerule: {}", ruleName);
            return false;
        }
        try {
            String stringValue;
            if (value instanceof Boolean || value instanceof Integer || value instanceof Double) {
                stringValue = value.toString();
            } else if (value instanceof String) {
                stringValue = (String) value;
            } else {
                stringValue = String.valueOf(value);
            }
            world.getGameRules().setOrCreateGameRule(ruleName, stringValue);
            LOGGER.debug("Successfully set gamerule {} to value: {}", ruleName, stringValue);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to set gamerule {} to value {}: {}", ruleName, value, e.getMessage());
            return false;
        }
    }

    /** 添加新游戏规则（已存在则跳过） */
    public static boolean addGamerule(World world, String ruleName, Object defaultValue) {
        if (world == null) {
            LOGGER.warn("World object is null, cannot add gamerule: {}", ruleName);
            return false;
        }
        if (world.getGameRules().hasRule(ruleName)) {
            LOGGER.debug("Gamerule already exists: {}", ruleName);
            return false;
        }
        try {
            world.getGameRules().setOrCreateGameRule(ruleName, String.valueOf(defaultValue));
            LOGGER.debug("Successfully added new gamerule {} with default value: {}", ruleName, defaultValue);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to add gamerule {} with default value {}: {}", ruleName, defaultValue, e.getMessage());
            return false;
        }
    }

    public static boolean hasGamerule(World world, String ruleName) {
        boolean exists = world != null && world.getGameRules().hasRule(ruleName);
        LOGGER.debug("Gamerule {} exists: {}", ruleName, exists);
        return exists;
    }

    /** 获取所有游戏规则的最优类型值 */
    public static Map<String, Object> getOptimalGameruleValues(World world) {
        Map<String, Object> result = new HashMap<>();
        Map<String, GameruleValue> allGamerules = getAllGamerules(world);
        for (Map.Entry<String, GameruleValue> entry : allGamerules.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getOptimalValue());
        }
        LOGGER.debug("Retrieved optimal values for {} gamerules", result.size());
        return result;
    }
}
