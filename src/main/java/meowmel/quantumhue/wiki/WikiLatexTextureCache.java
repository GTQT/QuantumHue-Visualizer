package meowmel.quantumhue.wiki;

import java.util.concurrent.ConcurrentHashMap;

/**
 * LaTeX 纹理与尺寸数据缓存（线程安全）
 */
public class WikiLatexTextureCache {

    public static final WikiLatexTextureCache INSTANCE = new WikiLatexTextureCache();

    private final ConcurrentHashMap<String, int[]> sizeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, int[]> textureCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> failedFormulas = new ConcurrentHashMap<>();

    private WikiLatexTextureCache() {}

    public static String buildScaleKey(float scale) {
        return String.format("%.2f", scale);
    }

    public static String buildSizeCacheKey(String formula, float scale) {
        return formula + "|SIZE|" + buildScaleKey(scale);
    }

    public static String buildTextureCacheKey(String formula, int color, float scale) {
        return formula + "|TEX|" + color + "|" + buildScaleKey(scale);
    }

    public boolean hasFailed(String formula) {
        return failedFormulas.containsKey(formula);
    }

    public void markFailed(String formula, String error) {
        failedFormulas.putIfAbsent(formula, error);
    }

    public int[] getSize(String key) {
        return sizeCache.get(key);
    }

    public void putSize(String key, int w, int h, int d) {
        sizeCache.put(key, new int[]{w, h, d});
    }

    public int[] getTexture(String key) {
        return textureCache.get(key);
    }

    public void putTexture(String key, int textureId, int w, int h) {
        textureCache.put(key, new int[]{textureId, w, h});
    }
}
