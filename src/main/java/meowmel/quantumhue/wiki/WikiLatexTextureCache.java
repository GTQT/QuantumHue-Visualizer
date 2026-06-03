package meowmel.quantumhue.wiki;

import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LaTeX 纹理与尺寸数据缓存（线程安全，LRU 自动驱逐）
 *
 * - 纹理缓存：access-order LinkedHashMap，上限 128 条，超限自动 glDeleteTextures
 * - 失败缓存：synchronized LinkedHashMap，上限 256 条
 * - 尺寸缓存：ConcurrentHashMap，上限 512 条
 */
public class WikiLatexTextureCache {

    public static final WikiLatexTextureCache INSTANCE = new WikiLatexTextureCache();

    private static final int MAX_TEXTURE_ENTRIES = 128;
    private static final int MAX_FAILURE_ENTRIES = 256;
    private static final int MAX_SIZE_ENTRIES = 512;

    /** LRU 纹理缓存 — 超限自动释放 GL 纹理 */
    private final Map<String, int[]> textureCache = new LinkedHashMap<>(MAX_TEXTURE_ENTRIES + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, int[]> eldest) {
            if (size() > MAX_TEXTURE_ENTRIES) {
                GL11.glDeleteTextures(eldest.getValue()[0]);
                return true;
            }
            return false;
        }
    };

    /** LRU 失败缓存 — 记录解析失败的公式，避免重复尝试 */
    private final Map<String, String> failureCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_FAILURE_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, String> eldest) {
                    return size() > MAX_FAILURE_ENTRIES;
                }
            });

    /** 尺寸缓存 — 线程安全，上限控制 */
    private final ConcurrentHashMap<String, int[]> sizeCache = new ConcurrentHashMap<>();

    private WikiLatexTextureCache() {}

    /* ═══════════════ 纹理缓存 ═══════════════ */

    public int[] getTexture(String cacheKey) {
        synchronized (textureCache) {
            return textureCache.get(cacheKey);
        }
    }

    public void putTexture(String cacheKey, int textureId, int widthPx, int heightPx) {
        synchronized (textureCache) {
            textureCache.put(cacheKey, new int[]{textureId, widthPx, heightPx});
        }
    }

    /* ═══════════════ 尺寸缓存 ═══════════════ */

    public int[] getSize(String sizeKey) {
        return sizeCache.get(sizeKey);
    }

    public void putSize(String sizeKey, int widthPx, int heightPx, int depthPx) {
        sizeCache.put(sizeKey, new int[]{widthPx, heightPx, depthPx});
        trimSizeCacheIfNeeded();
    }

    /* ═══════════════ 失败缓存 ═══════════════ */

    public boolean hasFailed(String formula) {
        return failureCache.containsKey(formula);
    }

    public void markFailed(String formula, String errorMsg) {
        failureCache.put(formula, errorMsg == null ? "" : errorMsg);
    }

    public String getFailureError(String formula) {
        return failureCache.get(formula);
    }

    /* ═══════════════ 全局清理 ═══════════════ */

    /** 删除所有 GL 纹理并清空所有缓存（必须在 GL 线程调用） */
    public void clearAll() {
        synchronized (textureCache) {
            for (int[] entry : textureCache.values()) {
                GL11.glDeleteTextures(entry[0]);
            }
            textureCache.clear();
        }
        failureCache.clear();
        sizeCache.clear();
    }

    /* ═══════════════ Key 构建 ═══════════════ */

    public static String buildTextureCacheKey(String formula, int fillColorArgb, float sourceScale) {
        return toHexColor(fillColorArgb) + ':' + buildScaleKey(sourceScale) + ':' + formula;
    }

    public static String buildSizeCacheKey(String formula, float sourceScale) {
        return buildScaleKey(sourceScale) + ':' + formula;
    }

    public static String buildScaleKey(float sourceScale) {
        int rounded = Math.round(sourceScale * 100f);
        int absolute = Math.abs(rounded);
        String sign = rounded < 0 ? "-" : "";
        return sign + absolute / 100 + "." + twoDigitFraction(absolute % 100);
    }

    /* ═══════════════ 辅助方法 ═══════════════ */

    private static String toHexColor(int color) {
        String hex = Integer.toHexString(color);
        if (hex.length() >= 8) return hex;
        StringBuilder padded = new StringBuilder(8);
        int padding = 8 - hex.length();
        for (int i = 0; i < padding; i++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
    }

    private static String twoDigitFraction(int fraction) {
        return fraction < 10 ? "0" + fraction : Integer.toString(fraction);
    }

    private void trimSizeCacheIfNeeded() {
        if (sizeCache.size() <= MAX_SIZE_ENTRIES) return;
        int removeCount = sizeCache.size() - MAX_SIZE_ENTRIES;
        for (String key : sizeCache.keySet()) {
            if (removeCount <= 0) return;
            if (sizeCache.remove(key) != null) removeCount--;
        }
    }
}
