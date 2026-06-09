package meowmel.quantumhue.wiki.util;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * 蓝图 3D 预览渲染器解析器（客户端）。
 *
 * <p>将 Markdown 中 {@code ![blueprint:quantumhue:wiki/bp_4x_ebf]}
 * 的资源路径解析为 {@link BlueprintPreviewRenderer} 实例，并缓存复用。</p>
 *
 * <p>解析链路：</p>
 * <ol>
 *   <li>解析 key 的 domain 和 path（path 自动附加 .json 后缀）</li>
 *   <li>构造 {@link ResourceLocation}</li>
 *   <li>创建 {@link BlueprintPreviewRenderer} 并加载 JSON 蓝图</li>
 *   <li>缓存到 {@code Map<String, BlueprintPreviewRenderer>}</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class BlueprintPreviewResolver {

    private static final Map<String, BlueprintPreviewRenderer> CACHE = new HashMap<>();

    private BlueprintPreviewResolver() {}

    /**
     * 根据 key 解析蓝图（带缓存）。
     *
     * @param key 蓝图路径的字符串形式（如 "quantumhue:wiki/bp_4x_ebf"）
     * @return 对应的预览渲染器，若无法解析则返回 null
     */
    public static BlueprintPreviewRenderer resolve(String key) {
        if (key == null || key.isEmpty()) return null;

        BlueprintPreviewRenderer cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            // key 格式: "domain:path"（如 "quantumhue:wiki/bp_4x_ebf"）
            String[] parts = key.trim().split(":", 2);
            if (parts.length < 2) return null;

            String domain = parts[0];
            String path = parts[1] + ".json";  // 自动附加 .json 后缀

            ResourceLocation rl = new ResourceLocation(domain, path);
            BlueprintPreviewRenderer renderer = new BlueprintPreviewRenderer(rl);

            if (renderer.isValid()) {
                CACHE.put(key, renderer);
                return renderer;
            }
        } catch (Exception ignored) {
            // key 格式无效或文件不存在
        }

        return null;
    }

    /** 清除所有缓存（用于 /wiki reload）。 */
    public static void clearCache() {
        CACHE.clear();
    }
}
