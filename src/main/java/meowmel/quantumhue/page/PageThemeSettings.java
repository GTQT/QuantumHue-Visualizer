package meowmel.quantumhue.page;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.config.PageConfigs;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * 商店 / 公告页的玩家配色偏好，存在 {@code config/quantumhue/theme.json}。
 *
 * <p>和 GuideQT 的 {@code settings.json} 是同一套思路：玩家自己的观感参数单独一份文件，
 * 与运营配置（{@code shop.json}）分开，改完立刻生效并写盘，不需要“应用”按钮，也不会
 * 因为崩服丢掉一次改动。
 *
 * <p>只有服务端运营配置由 {@link PageConfigs} 管；这里的内容纯客户端，服务端不读也不关心。
 */
public final class PageThemeSettings {

    /**
     * 可选配色方案：第一个是页面原本的量子青，后面五个与 GuideQT 的强调色完全一致
     * （金、冰蓝、青翠、紫晶、珊瑚），两边切换时观感对得上。
     */
    public static final int[] ACCENTS = {
            0x00E5FF, 0xFFD479, 0x6FB7FF, 0x7BE38B, 0xC79BFF, 0xFF8B7B
    };

    /** 与 {@link #ACCENTS} 一一对应的方案名，设置页显示用。 */
    public static final String[] ACCENT_NAMES = {
            "量子青", "鎏金", "冰蓝", "青翠", "紫晶", "珊瑚"
    };

    private static final String FILE_NAME = "theme.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static PageThemeSettings instance;

    private int accentColor = ACCENTS[0];

    private PageThemeSettings() {}

    /** 当前设置，首次访问时读盘。 */
    public static PageThemeSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public int accentColor() {
        return accentColor;
    }

    /** 当前方案名；玩家手改过文件、颜色不在预置列表里时返回十六进制值。 */
    public String accentName() {
        for (int i = 0; i < ACCENTS.length; i++) {
            if (ACCENTS[i] == accentColor) {
                return ACCENT_NAMES[i];
            }
        }
        return String.format("#%06X", accentColor);
    }

    /** 换配色：先让界面用上，再写盘。 */
    public void setAccentColor(int rgb) {
        accentColor = rgb & 0xFFFFFF;
        PageTheme.apply(accentColor);
        save();
    }

    /** 还原成默认的量子青。 */
    public void reset() {
        setAccentColor(ACCENTS[0]);
    }

    // ================================================================ 存盘

    /** {@code config/quantumhue/theme.json}；配置目录还没初始化时向 FML 要，再不行就当没有。 */
    private static File file() {
        File dir = PageConfigs.directory();
        if (dir == null) {
            File configDir = null;
            try {
                configDir = Loader.instance()
                        .getConfigDir();
            } catch (Throwable ignored) {
                // 单元测试与独立工具里没有 FML loader
            }
            dir = configDir == null ? null : new File(configDir, Tags.MOD_ID);
        }
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static PageThemeSettings load() {
        PageThemeSettings settings = new PageThemeSettings();
        File file = file();
        if (file == null || !file.isFile()) {
            return settings;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader)
                    .getAsJsonObject();
            if (root.has("accentColor")) {
                settings.accentColor = parseColor(root.get("accentColor")
                        .getAsString(), ACCENTS[0]);
            }
        } catch (Exception failure) {
            QuantumHue.LOGGER.warn("[Pages] 无法读取界面配色，使用默认值：{}", failure.toString());
        }
        return settings;
    }

    /** 写盘失败只记日志：配色是观感状态，不该因为它让界面报错。 */
    public void save() {
        File file = file();
        if (file == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            JsonObject root = new JsonObject();
            // 与 GuideQT 的 settings.json 同格式："#RRGGBB"
            root.addProperty("accentColor", String.format("#%06X", accentColor));
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception failure) {
            QuantumHue.LOGGER.warn("[Pages] 无法保存界面配色：{}", failure.toString());
        }
    }

    /** 接受 {@code #RRGGBB} 与 {@code #AARRGGBB}，强调色一律只留 RGB。 */
    private static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }
}
