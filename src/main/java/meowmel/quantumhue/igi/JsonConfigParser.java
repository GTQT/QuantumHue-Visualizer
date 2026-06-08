package meowmel.quantumhue.igi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meowmel.quantumhue.igi.info.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;

/**
 * IGI JSON 配置文件解析器。
 * 从 config/quantumhue/igi.json 读取 HUD 布局并注册到 IGI 系统。
 * <p>
 * 如果文件不存在、为空、或没有 groups，则不显示任何 IGI 信息。
 */
public class JsonConfigParser {
    private static final Logger LOGGER = LogManager.getLogger("IGI-JsonParser");

    private JsonConfigParser() {
    }

    /**
     * 加载 JSON 配置文件并注册所有 HUD 组。
     *
     * @param configFile JSON 文件路径
     * @return true 解析成功（含空配置），false 文件不存在或解析失败
     */
    public static boolean load(File configFile) {
        if (configFile == null || !configFile.exists()) {
            LOGGER.info("IGI 配置文件不存在，不显示 HUD 信息");
            return false;
        }

        try (Reader reader = new FileReader(configFile)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

            if (!root.has("groups")) {
                LOGGER.info("IGI 配置文件中没有 groups 定义，不显示 HUD 信息");
                return true;
            }

            JsonArray groups = root.getAsJsonArray("groups");
            if (groups == null || groups.size() == 0) {
                LOGGER.info("IGI 配置文件 groups 为空，不显示 HUD 信息");
                return true;
            }

            for (JsonElement groupEl : groups) {
                parseGroup(groupEl.getAsJsonObject());
            }

            LOGGER.info("成功加载 IGI 配置，共 {} 个 HUD 组", groups.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("解析 IGI 配置文件失败", e);
            return false;
        }
    }

    private static void parseGroup(JsonObject groupObj) {
        String alignStr = getString(groupObj, "alignment", "top_left");
        int offsetX = getInt(groupObj, "offsetX", 2);
        int offsetY = getInt(groupObj, "offsetY", 2);
        float fontSize = (float) getDouble(groupObj, "fontSize", -1);

        Alignment alignment = Alignment.fromString(alignStr);
        if (alignment == null) {
            alignment = Alignment.TOP_LEFT;
        }

        InfoBuilder builder = IGI.register()
                .pos(alignment)
                .offset(offsetX, offsetY)
                .size(fontSize);

        JsonArray lines = groupObj.getAsJsonArray("lines");
        if (lines != null) {
            for (JsonElement lineEl : lines) {
                if (lineEl.isJsonArray()) {
                    JsonArray segments = lineEl.getAsJsonArray();
                    if (segments.size() == 0) {
                        builder.info("");
                    } else {
                        Object[] parts = new Object[segments.size()];
                        boolean allEmpty = true;
                        for (int i = 0; i < segments.size(); i++) {
                            parts[i] = parseSegment(segments.get(i));
                            if (parts[i] != null) allEmpty = false;
                        }
                        if (!allEmpty) {
                            builder.info(parts);
                        } else {
                            builder.info("");
                        }
                    }
                } else if (lineEl.isJsonPrimitive() && !lineEl.getAsString().isEmpty()) {
                    builder.info(lineEl.getAsString());
                } else {
                    builder.info("");
                }
            }
        }

        builder.builder();
    }

    private static Object parseSegment(JsonElement el) {
        if (el.isJsonPrimitive()) {
            String str = el.getAsString();
            if (str.equalsIgnoreCase("empty") || str.isEmpty()) {
                return "";
            }
            return str;
        }

        if (!el.isJsonObject()) return "";

        JsonObject obj = el.getAsJsonObject();
        String type = getString(obj, "type", "text");

        switch (type) {
            case "text":
                return getString(obj, "value", "");

            case "color":
                String colorName = getString(obj, "name", "white");
                TextColor color = TextColor.fromName(colorName);
                return color != null ? color : TextColor.WHITE;

            case "icon":
            case "item": {
                String itemName = getString(obj, "item", "");
                int meta = getInt(obj, "meta", 0);
                if (!itemName.isEmpty()) {
                    ItemStack stack = parseItemStack(itemName, meta);
                    if (!stack.isEmpty()) {
                        return new ItemIcon(stack);
                    }
                }
                return "";
            }

            // === 动态信息提供者 ===
            case "tps":          return new TpsInfo();
            case "mspt":         return new MsptInfo();
            case "memory":       return new MemoryInfo();
            case "fps":          return new FpsInfo();
            case "player_name":  return new PlayerNameInfo();
            case "real_time":    return new RealTimeInfo();
            case "mc_date":      return new McDateInfo();
            case "mc_time":      return new McTimeFormattedInfo();
            case "dim_full":     return new DimFullInfo();
            case "weather":      return new WeatherInfo();
            case "biome":        return new BiomeInfo();
            case "biome_temp":   return new BiomeTempInfo();
            case "biome_humidity": return new BiomeHumidityInfo();
            case "chunk_x":      return new ChunkXInfo();
            case "chunk_z":      return new ChunkZInfo();
            case "chunk_offset": return new ChunkOffsetInfo();
            case "facing":       return new FacingInfo();
            case "foot_light":   return new FootLightInfo();
            case "eye_light":    return new EyeLightInfo();
            case "time":         return new TimeInfo();
            case "day":          return new DayInfo();
            case "player_pos":   return new PlayerPosInfo();
            case "health":       return new HealthInfo();
            case "food":         return new FoodInfo();
            case "light":        return new LightInfo();

            default:
                LOGGER.warn("未知的 IGI 段类型: {}", type);
                return "";
        }
    }

    private static ItemStack parseItemStack(String itemName, int meta) {
        Item item = Item.getByNameOrId(itemName);
        if (item != null) {
            return new ItemStack(item, 1, meta);
        }
        // 尝试用 ResourceLocation 查找
        try {
            ResourceLocation rl = new ResourceLocation(itemName);
            if (Item.REGISTRY.containsKey(rl)) {
                item = Item.REGISTRY.getObject(rl);
                return new ItemStack(item, 1, meta);
            }
        } catch (Exception e) {
            // ignore
        }
        LOGGER.warn("无法找到物品: {}", itemName);
        return ItemStack.EMPTY;
    }

    // === 安全读取 JSON 字段 ===

    private static String getString(JsonObject obj, String key, String def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsInt(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsDouble(); } catch (Exception ignored) {}
        }
        return def;
    }
}
