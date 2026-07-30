package meowmel.quantumhue.chat;

import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * 表情注册表 — 短码 ↔ 占位符 ↔ 纹理 的映射中心
 *
 * 50 个常用表情，按 5 行 × 10 列排列（对应 Emoji Picker 的网格布局）。
 *
 * 原理：
 *   1. 渲染前：文字中的 :joy: 等短码被替换为 Unicode 私用区占位符 (U+E000..U+E031)
 *   2. 换行计算：通过 Mixin FontRenderer.getCharWidth 让占位符返回 12px 宽度
 *   3. 文字渲染：占位符 glyphWidth=0，FontRenderer 跳过（留空 12px 间隙）
 *   4. 贴图覆盖：在间隙位置用 GL 绘制对应表情纹理
 */
public final class EmojiRegistry {

    private EmojiRegistry() {}

    // ===== 常量 =====
    /** 表情渲染尺寸 (px)，与 Minecraft 默认行高 9 协调 */
    public static final int EMOJI_SIZE = 12;
    /** 表情选择器每格尺寸 */
    public static final int PICKER_CELL = 26;
    public static final int PICKER_COLS = 10;
    public static final int PICKER_ROWS = 5;
    /** 私用区起始码点 */
    private static final char PUA_BASE = 0xE000;
    /** 表情总数 */
    public static final int COUNT = 50;

    // ===== 表情定义：{codepoint(Hex), 短码, 符号(文档用)}，按 5×10 网格排列 =====
    private static final String[][] EMOJI_DEFS = {
            // Row 1 — 基础笑脸
            {"1f600", ":grin:",      "😀"},
            {"1f602", ":joy:",       "😂"},
            {"1f923", ":rofl:",      "🤣"},
            {"1f60a", ":blush:",     "😊"},
            {"1f60d", ":hearteyes:", "😍"},
            {"1f970", ":love:",      "🥰"},
            {"1f618", ":kiss:",      "😘"},
            {"1f60e", ":cool:",      "😎"},
            {"1f929", ":wow:",       "🤩"},
            {"1f62d", ":cry:",       "😭"},
            // Row 2 — 表情
            {"1f621", ":angry:",     "😡"},
            {"1f631", ":scream:",    "😱"},
            {"1f914", ":think:",     "🤔"},
            {"1f644", ":eyeroll:",   "🙄"},
            {"1f634", ":sleep:",     "😴"},
            {"1f917", ":hug:",       "🤗"},
            {"1f97a", ":plead:",     "🥺"},
            {"1f60f", ":smirk:",     "😏"},
            {"1f605", ":sweat:",     "😅"},
            {"1f60b", ":yum:",       "😋"},
            // Row 3 — 手势 + 心
            {"1f44d", ":like:",      "👍"},
            {"1f44e", ":dislike:",   "👎"},
            {"1f44f", ":clap:",      "👏"},
            {"1f64f", ":pray:",      "🙏"},
            {"1f4aa", ":muscle:",    "💪"},
            {"2764",  ":heart:",     "❤️"},
            {"1f495", ":hearts:",    "💕"},
            {"1f494", ":broken:",    "💔"},
            {"1f4af", ":100:",       "💯"},
            {"1f525", ":fire:",      "🔥"},
            // Row 4 — 符号与趣味
            {"2728",  ":sparkles:",  "✨"},
            {"1f389", ":party:",     "🎉"},
            {"1f38a", ":confetti:",  "🎊"},
            {"2b50",  ":star:",      "⭐"},
            {"1f31f", ":glowstar:",  "🌟"},
            {"1f440", ":eyes:",      "👀"},
            {"1f382", ":cake:",      "🎂"},
            {"1f648", ":monkey:",    "🙈"},
            {"1f480", ":skull:",     "💀"},
            {"1f608", ":devil:",     "😈"},
            // Row 5 — 物品与活动
            {"1f3ae", ":game:",      "🎮"},
            {"1f3c6", ":trophy:",    "🏆"},
            {"1f355", ":pizza:",     "🍕"},
            {"1f3b5", ":music:",     "🎵"},
            {"1f4a4", ":zzz:",       "💤"},
            {"1f680", ":rocket:",    "🚀"},
            {"1f451", ":crown:",     "👑"},
            {"1f431", ":cat:",       "🐱"},
            {"1f308", ":rainbow:",   "🌈"},
            {"1f370", ":slice:",     "🍰"},
    };

    // ===== 映射表 =====
    /** 短码 → PUA 占位符 */
    private static final Map<String, Character> shortcodeToPua = new LinkedHashMap<>();
    /** PUA 占位符 → ResourceLocation（纹理路径） */
    private static final Map<Character, ResourceLocation> puaToTexture = new HashMap<>();
    /** PUA 占位符 → 短码（反向查，输入框插入用） */
    private static final Map<Character, String> puaToShortcode = new HashMap<>();
    /** 短码列表（按网格顺序，选择器渲染用） */
    private static final String[] gridShortcodes = new String[COUNT];

    static {
        for (int i = 0; i < EMOJI_DEFS.length; i++) {
            String codepoint = EMOJI_DEFS[i][0];
            String shortcode = EMOJI_DEFS[i][1];
            char pua = (char) (PUA_BASE + i);

            shortcodeToPua.put(shortcode, pua);
            puaToTexture.put(pua, new ResourceLocation("quantumhue", "textures/emoji/" + codepoint + ".png"));
            puaToShortcode.put(pua, shortcode);
            gridShortcodes[i] = shortcode;
        }
    }

    // ===== 公共 API =====

    /** 将文本中的短码替换为 PUA 占位符（渲染前调用） */
    public static String replaceShortcodes(String text) {
        if (text == null || text.isEmpty()) return text;
        for (Map.Entry<String, Character> entry : shortcodeToPua.entrySet()) {
            text = text.replace(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return text;
    }

    /** 是否为表情占位符 */
    public static boolean isPlaceholder(char c) {
        return c >= PUA_BASE && c < PUA_BASE + COUNT;
    }

    /** 占位符 → 表情索引 (0..49) */
    public static int placeholderIndex(char c) {
        return c - PUA_BASE;
    }

    /** 获取表情纹理 */
    public static ResourceLocation getTexture(char placeholder) {
        return puaToTexture.get(placeholder);
    }

    /** 获取表情纹理（按格子索引） */
    public static ResourceLocation getTextureByIndex(int index) {
        if (index < 0 || index >= COUNT) return null;
        char pua = (char) (PUA_BASE + index);
        return puaToTexture.get(pua);
    }

    /** 获取短码（按格子索引） */
    public static String getShortcodeByIndex(int index) {
        if (index < 0 || index >= COUNT) return "";
        return gridShortcodes[index];
    }

    /** 占位符 → 短码 */
    public static String placeholderToShortcode(char placeholder) {
        return puaToShortcode.getOrDefault(placeholder, "");
    }

    /** 表情像素尺寸 */
    public static int getEmojiSize() { return EMOJI_SIZE; }

    /** 获取所有短码（用于遍历） */
    public static Collection<String> getAllShortcodes() {
        return shortcodeToPua.keySet();
    }
}
