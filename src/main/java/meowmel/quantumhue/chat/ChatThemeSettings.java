package meowmel.quantumhue.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
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
 * 聊天界面的玩家配色偏好，存在 {@code config/quantumhue/chat_theme.json}。
 *
 * <p>和商店/公告页的 {@code theme.json} 是同一套思路：玩家自己的观感参数单独一份文件，
 * 与 Forge 配置里的服务端无关参数分开，改完立刻生效并写盘，不需要“应用”按钮。
 *
 * <p>与 {@code PageThemeSettings} 只允许换强调色不同，这里允许逐个角色改色
 * （强调 / 底色 / 我的气泡 / 对方气泡 / 主文字 / 次要文字），
 * 其余约 30 个衍生色由 {@link ChatColors#reload()} 现场算出来。
 */
public final class ChatThemeSettings {

    // ===== 可编辑的颜色角色 =====
    public static final int ROLE_ACCENT = 0;
    public static final int ROLE_BG = 1;
    public static final int ROLE_OWN = 2;
    public static final int ROLE_OTHER = 3;
    public static final int ROLE_LIGHT = 4;
    public static final int ROLE_DIM = 5;
    public static final int ROLE_COUNT = 6;

    public static final String[] ROLE_NAMES = {
            "强调色", "面板底色", "我的气泡", "对方气泡", "主文字", "次要文字"
    };

    /** 一套完整配色；预设不是只换强调色，六个角色一起换，观感才不会拼贴。 */
    public static final class Preset {
        public final String name;
        public final int accent;
        public final int bg;
        public final int own;
        public final int other;
        public final int light;
        public final int dim;

        Preset(String name, int accent, int bg, int own, int other, int light, int dim) {
            this.name = name;
            this.accent = accent;
            this.bg = bg;
            this.own = own;
            this.other = other;
            this.light = light;
            this.dim = dim;
        }

        public int color(int role) {
            switch (role) {
                case ROLE_ACCENT: return accent;
                case ROLE_BG: return bg;
                case ROLE_OWN: return own;
                case ROLE_OTHER: return other;
                case ROLE_LIGHT: return light;
                case ROLE_DIM: return dim;
                default: return 0xFFFFFF;
            }
        }
    }

    /**
     * 预置方案。第一套与 Forge 配置的默认值完全一致（深空），
     * 其余五套分别借用商店页面的强调色，再配一套亮色，方便快速得到能用的底稿。
     */
    public static final Preset[] PRESETS = {
            new Preset("深空", 0x6688CC, 0x0E0E16, 0x181830, 0x0E0E22, 0xDDDDDD, 0x999999),
            new Preset("量子青", 0x00E5FF, 0x0B1020, 0x123047, 0x0E1626, 0xE6F6FF, 0x8AA6B8),
            new Preset("鎏金", 0xFFD479, 0x191408, 0x3A2C10, 0x120F08, 0xFFF2D0, 0xB8A176),
            new Preset("青翠", 0x7BE38B, 0x0A160F, 0x123A22, 0x0C1A12, 0xE4FFE9, 0x93B39B),
            new Preset("紫晶", 0xC79BFF, 0x120C1C, 0x2A1A44, 0x160F22, 0xF0E6FF, 0xA793BF),
            new Preset("珊瑚", 0xFF8B7B, 0x180D0E, 0x3A1D1D, 0x1A0F10, 0xFFE6E1, 0xBFA098),
            new Preset("白昼", 0x3A7BD5, 0xF2F4F8, 0xDCE9FF, 0xE8E8EE, 0x2A2F3A, 0x6B7280)
    };

    // ===== 外观档位 =====
    public static final String[] DENSITY_NAMES = { "紧凑", "标准", "宽松" };
    public static final String[] CORNER_NAMES = { "直角", "微圆", "圆角" };
    /** 三档圆角对应的像素半径。 */
    public static final int[] CORNER_RADII = { 0, 3, 5 };

    private static final String FILE_NAME = "chat_theme.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static ChatThemeSettings instance;

    private int accentColor = PRESETS[0].accent;
    private int bgColor = PRESETS[0].bg;
    private int ownBubbleColor = PRESETS[0].own;
    private int otherBubbleColor = PRESETS[0].other;
    private int textLightColor = PRESETS[0].light;
    private int textDimColor = PRESETS[0].dim;

    /** 面板整体不透明度，百分比 40~100。 */
    private int panelOpacity = 80;
    private int density = 1;
    private int corner = 1;
    private boolean timeSeparators = true;
    private boolean highlightMentions = true;

    // ===== HUD（关闭聊天界面时的消息预览） =====

    /** 关闭聊天后是否在左下角显示最近消息。 */
    private boolean hudEnabled = true;
    /** HUD 显示的行数。 */
    private int hudLines = 5;
    /** 私聊 / @我 是否播放提示音。 */
    private boolean hudSound = true;

    // ===== 页面参数 =====

    /** 面板宽度占屏幕宽度的百分比。 */
    private int panelWidthPercent = 65;
    /** 左侧频道栏宽度，像素。 */
    private int sidebarWidth = 115;
    /** 气泡最大宽度占可用区域的百分比：100 表示铺满。 */
    private int bubbleWidthPercent = 100;
    private boolean showAvatars = true;
    /** 打开 / 关闭时的滑入动画时长，毫秒；0 表示不做动画。 */
    private int animationMs = 200;
    /** 页面参数是否被玩家单独调过；没调过时继续跟随 Forge 配置。 */
    private boolean layoutEdited;

    /** 是否被玩家改过；没改过时重新读 Forge 配置可以直接覆盖。 */
    private boolean customized;

    private ChatThemeSettings() {}

    /** 当前设置，首次访问时读盘。 */
    public static ChatThemeSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // ===== 颜色 =====

    public int accentColor() { return accentColor; }
    public int bgColor() { return bgColor; }
    public int ownBubbleColor() { return ownBubbleColor; }
    public int otherBubbleColor() { return otherBubbleColor; }
    public int textLightColor() { return textLightColor; }
    public int textDimColor() { return textDimColor; }

    /** 按角色取色。 */
    public int color(int role) {
        switch (role) {
            case ROLE_ACCENT: return accentColor;
            case ROLE_BG: return bgColor;
            case ROLE_OWN: return ownBubbleColor;
            case ROLE_OTHER: return otherBubbleColor;
            case ROLE_LIGHT: return textLightColor;
            case ROLE_DIM: return textDimColor;
            default: return 0xFFFFFF;
        }
    }

    /** 改一个角色的颜色：立刻刷新界面，是否写盘由调用方决定（拖动滑块时先攒着）。 */
    public void setColor(int role, int rgb) {
        int value = rgb & 0xFFFFFF;
        switch (role) {
            case ROLE_ACCENT: accentColor = value; break;
            case ROLE_BG: bgColor = value; break;
            case ROLE_OWN: ownBubbleColor = value; break;
            case ROLE_OTHER: otherBubbleColor = value; break;
            case ROLE_LIGHT: textLightColor = value; break;
            case ROLE_DIM: textDimColor = value; break;
            default: return;
        }
        customized = true;
        ChatColors.reload();
    }

    /** 当前配色命中的预设下标；手改过任何一个通道就是 -1。 */
    public int presetIndex() {
        for (int i = 0; i < PRESETS.length; i++) {
            Preset preset = PRESETS[i];
            if (preset.accent == accentColor && preset.bg == bgColor
                    && preset.own == ownBubbleColor && preset.other == otherBubbleColor
                    && preset.light == textLightColor && preset.dim == textDimColor) {
                return i;
            }
        }
        return -1;
    }

    public String presetName() {
        int index = presetIndex();
        return index >= 0 ? PRESETS[index].name : "自定义";
    }

    /** 应用整套预置方案。 */
    public void applyPreset(int index) {
        if (index < 0 || index >= PRESETS.length) {
            return;
        }
        Preset preset = PRESETS[index];
        accentColor = preset.accent;
        bgColor = preset.bg;
        ownBubbleColor = preset.own;
        otherBubbleColor = preset.other;
        textLightColor = preset.light;
        textDimColor = preset.dim;
        customized = true;
        ChatColors.reload();
        save();
    }

    // ===== 外观 =====

    /** 面板不透明度：0~255，给绘制层直接用。 */
    public int panelAlpha() {
        return Math.round(255f * panelOpacity / 100f);
    }

    public int panelOpacity() { return panelOpacity; }

    public void setPanelOpacity(int percent) {
        panelOpacity = clamp(percent, 40, 100);
    }

    public int density() { return density; }

    public String densityName() {
        return DENSITY_NAMES[clamp(density, 0, DENSITY_NAMES.length - 1)];
    }

    public void setDensity(int value) {
        density = clamp(value, 0, DENSITY_NAMES.length - 1);
    }

    /** 消息之间的垂直间距。 */
    public int messageGap() {
        switch (density) {
            case 0: return 2;
            case 2: return 7;
            default: return 4;
        }
    }

    /** 气泡内上下留白。 */
    public int bubblePadY() {
        switch (density) {
            case 0: return 3;
            case 2: return 6;
            default: return 4;
        }
    }

    public int corner() { return corner; }

    public String cornerName() {
        return CORNER_NAMES[clamp(corner, 0, CORNER_NAMES.length - 1)];
    }

    public int cornerRadius() {
        return CORNER_RADII[clamp(corner, 0, CORNER_RADII.length - 1)];
    }

    public void setCorner(int value) {
        corner = clamp(value, 0, CORNER_NAMES.length - 1);
    }

    public boolean timeSeparators() { return timeSeparators; }

    public void setTimeSeparators(boolean value) {
        timeSeparators = value;
    }

    public boolean highlightMentions() { return highlightMentions; }

    public void setHighlightMentions(boolean value) {
        highlightMentions = value;
    }

    // ===== HUD =====

    public boolean hudEnabled() { return hudEnabled; }

    public void setHudEnabled(boolean value) {
        hudEnabled = value;
    }

    public int hudLines() { return hudLines; }

    public void setHudLines(int value) {
        hudLines = clamp(value, 2, 10);
    }

    public boolean hudSound() { return hudSound; }

    public void setHudSound(boolean value) {
        hudSound = value;
    }

    // ===== 页面参数 =====

    public int panelWidthPercent() { return panelWidthPercent; }

    public void setPanelWidthPercent(int value) {
        panelWidthPercent = clamp(value, 30, 90);
        layoutEdited = true;
    }

    public int sidebarWidth() { return sidebarWidth; }

    public void setSidebarWidth(int value) {
        sidebarWidth = clamp(value, 80, 250);
        layoutEdited = true;
    }

    public int bubbleWidthPercent() { return bubbleWidthPercent; }

    public void setBubbleWidthPercent(int value) {
        bubbleWidthPercent = clamp(value, 45, 100);
        layoutEdited = true;
    }

    public boolean showAvatars() { return showAvatars; }

    public void setShowAvatars(boolean value) {
        showAvatars = value;
        layoutEdited = true;
    }

    /** 动画时长档位：0 = 快（120ms），1 = 标准（200ms），2 = 慢（320ms）。 */
    public int animationLevel() {
        if (animationMs <= 140) {
            return 0;
        }
        return animationMs >= 280 ? 2 : 1;
    }

    public void setAnimationLevel(int level) {
        animationMs = level <= 0 ? 120 : (level >= 2 ? 320 : 200);
        layoutEdited = true;
    }

    public int animationMs() { return animationMs; }

    // ===== 重置 / 色码 =====

    /** 恢复 Forge 配置里的默认值（不是写死的预置一），保留外观档位。 */
    public void resetToConfig() {
        QuantumHueConfig.ChatConfig cfg = QuantumHueConfig.chat;
        accentColor = parseColor(cfg.accentColor, PRESETS[0].accent);
        bgColor = parseColor(cfg.bgColor, PRESETS[0].bg);
        ownBubbleColor = parseColor(cfg.ownBubbleColor, PRESETS[0].own);
        otherBubbleColor = parseColor(cfg.otherBubbleColor, PRESETS[0].other);
        textLightColor = parseColor(cfg.textLight, PRESETS[0].light);
        textDimColor = parseColor(cfg.textDim, PRESETS[0].dim);
        customized = false;
        panelWidthPercent = clamp(cfg.panelWidthPercent, 30, 90);
        sidebarWidth = clamp(cfg.sidebarWidth, 80, 250);
        bubbleWidthPercent = 100;
        showAvatars = true;
        animationMs = 200;
        layoutEdited = false;
        ChatColors.reload();
        save();
    }

    /**
     * 一整个配色压成一行可复制的色码，方便把调好的方案丢给朋友。
     * 形如 {@code QHC1-6688CC-0E0E16-181830-0E0E22-DDDDDD-999999}。
     */
    public String exportCode() {
        return String.format("QHC1-%06X-%06X-%06X-%06X-%06X-%06X",
                accentColor, bgColor, ownBubbleColor, otherBubbleColor, textLightColor, textDimColor);
    }

    /** 解析 {@link #exportCode()} 的产物；格式不对返回 false，一个通道都不会改。 */
    public boolean importCode(String code) {
        if (code == null) {
            return false;
        }
        String[] parts = code.trim().split("-");
        if (parts.length != 7 || !"QHC1".equalsIgnoreCase(parts[0])) {
            return false;
        }
        int[] values = new int[6];
        try {
            for (int i = 0; i < 6; i++) {
                values[i] = Integer.parseInt(parts[i + 1], 16) & 0xFFFFFF;
            }
        } catch (NumberFormatException failure) {
            return false;
        }
        accentColor = values[0];
        bgColor = values[1];
        ownBubbleColor = values[2];
        otherBubbleColor = values[3];
        textLightColor = values[4];
        textDimColor = values[5];
        customized = true;
        ChatColors.reload();
        save();
        return true;
    }

    /** Forge 配置改了：玩家没动过的部分跟着变，动过的部分尊重玩家。 */
    public void onConfigChanged() {
        QuantumHueConfig.ChatConfig cfg = QuantumHueConfig.chat;
        boolean changed = false;
        if (!customized) {
            accentColor = parseColor(cfg.accentColor, accentColor);
            bgColor = parseColor(cfg.bgColor, bgColor);
            ownBubbleColor = parseColor(cfg.ownBubbleColor, ownBubbleColor);
            otherBubbleColor = parseColor(cfg.otherBubbleColor, otherBubbleColor);
            textLightColor = parseColor(cfg.textLight, textLightColor);
            textDimColor = parseColor(cfg.textDim, textDimColor);
            changed = true;
        }
        if (!layoutEdited) {
            panelWidthPercent = clamp(cfg.panelWidthPercent, 30, 90);
            sidebarWidth = clamp(cfg.sidebarWidth, 80, 250);
            changed = true;
        }
        if (changed) {
            ChatColors.reload();
            save();
        }
    }

    // ===== 存盘 =====

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

    private static ChatThemeSettings load() {
        ChatThemeSettings settings = new ChatThemeSettings();
        File file = file();
        if (file == null || !file.isFile()) {
            // 没有偏好文件时用 Forge 配置里的配色开局，第一次改色才会写盘
            QuantumHueConfig.ChatConfig cfg = QuantumHueConfig.chat;
            settings.accentColor = parseColor(cfg.accentColor, settings.accentColor);
            settings.bgColor = parseColor(cfg.bgColor, settings.bgColor);
            settings.ownBubbleColor = parseColor(cfg.ownBubbleColor, settings.ownBubbleColor);
            settings.otherBubbleColor = parseColor(cfg.otherBubbleColor, settings.otherBubbleColor);
            settings.textLightColor = parseColor(cfg.textLight, settings.textLightColor);
            settings.textDimColor = parseColor(cfg.textDim, settings.textDimColor);
            settings.panelWidthPercent = clamp(cfg.panelWidthPercent, 30, 90);
            settings.sidebarWidth = clamp(cfg.sidebarWidth, 80, 250);
            return settings;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader)
                    .getAsJsonObject();
            settings.accentColor = parseColor(getString(root, "accentColor"), settings.accentColor);
            settings.bgColor = parseColor(getString(root, "bgColor"), settings.bgColor);
            settings.ownBubbleColor = parseColor(getString(root, "ownBubbleColor"), settings.ownBubbleColor);
            settings.otherBubbleColor = parseColor(getString(root, "otherBubbleColor"), settings.otherBubbleColor);
            settings.textLightColor = parseColor(getString(root, "textLightColor"), settings.textLightColor);
            settings.textDimColor = parseColor(getString(root, "textDimColor"), settings.textDimColor);
            settings.panelOpacity = clamp(getInt(root, "panelOpacity", settings.panelOpacity), 40, 100);
            settings.density = clamp(getInt(root, "density", settings.density), 0, DENSITY_NAMES.length - 1);
            settings.corner = clamp(getInt(root, "corner", settings.corner), 0, CORNER_NAMES.length - 1);
            settings.timeSeparators = getBoolean(root, "timeSeparators", settings.timeSeparators);
            settings.highlightMentions = getBoolean(root, "highlightMentions", settings.highlightMentions);
            settings.panelWidthPercent = clamp(getInt(root, "panelWidthPercent", settings.panelWidthPercent), 30, 90);
            settings.sidebarWidth = clamp(getInt(root, "sidebarWidth", settings.sidebarWidth), 80, 250);
            settings.bubbleWidthPercent = clamp(getInt(root, "bubbleWidthPercent", settings.bubbleWidthPercent), 45, 100);
            settings.showAvatars = getBoolean(root, "showAvatars", settings.showAvatars);
            settings.animationMs = clamp(getInt(root, "animationMs", settings.animationMs), 0, 400);
            settings.layoutEdited = getBoolean(root, "layoutEdited", true);
            settings.hudEnabled = getBoolean(root, "hudEnabled", settings.hudEnabled);
            settings.hudLines = clamp(getInt(root, "hudLines", settings.hudLines), 2, 10);
            settings.hudSound = getBoolean(root, "hudSound", settings.hudSound);
            settings.customized = getBoolean(root, "customized", true);
        } catch (Exception failure) {
            QuantumHue.LOGGER.warn("[Chat] 无法读取聊天配色，使用默认值：{}", failure.toString());
        }
        return settings;
    }

    /** 写盘失败只记日志：配色是观感状态，不该因为它让聊天界面报错。 */
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
            root.addProperty("accentColor", String.format("#%06X", accentColor));
            root.addProperty("bgColor", String.format("#%06X", bgColor));
            root.addProperty("ownBubbleColor", String.format("#%06X", ownBubbleColor));
            root.addProperty("otherBubbleColor", String.format("#%06X", otherBubbleColor));
            root.addProperty("textLightColor", String.format("#%06X", textLightColor));
            root.addProperty("textDimColor", String.format("#%06X", textDimColor));
            root.addProperty("panelOpacity", panelOpacity);
            root.addProperty("density", density);
            root.addProperty("corner", corner);
            root.addProperty("timeSeparators", timeSeparators);
            root.addProperty("highlightMentions", highlightMentions);
            root.addProperty("panelWidthPercent", panelWidthPercent);
            root.addProperty("sidebarWidth", sidebarWidth);
            root.addProperty("bubbleWidthPercent", bubbleWidthPercent);
            root.addProperty("showAvatars", showAvatars);
            root.addProperty("animationMs", animationMs);
            root.addProperty("layoutEdited", layoutEdited);
            root.addProperty("hudEnabled", hudEnabled);
            root.addProperty("hudLines", hudLines);
            root.addProperty("hudSound", hudSound);
            root.addProperty("customized", customized);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception failure) {
            QuantumHue.LOGGER.warn("[Chat] 无法保存聊天配色：{}", failure.toString());
        }
    }

    // ===== 工具 =====

    private static String getString(JsonObject root, String key) {
        return root.has(key) ? root.get(key)
                .getAsString() : null;
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        try {
            return root.has(key) ? root.get(key)
                    .getAsInt() : fallback;
        } catch (Exception failure) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        try {
            return root.has(key) ? root.get(key)
                    .getAsBoolean() : fallback;
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** 接受 {@code #RRGGBB}、{@code RRGGBB}，颜色一律只留 RGB。 */
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

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
