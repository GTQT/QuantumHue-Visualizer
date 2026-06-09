package meowmel.quantumhue.wiki.util;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import meowmel.quantumhue.wiki.gregtech.MultiblockBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * 多方块 3D 预览渲染器解析器（客户端）。
 *
 * <p>将 Markdown 中 {@code ![multiblock:gregtech:electric_blast_furnace]}
 * 的 ResourceLocation key 解析为 {@link MultiblockPreviewRenderer} 实例，并缓存复用。</p>
 *
 * <p>查找链路：</p>
 * <ol>
 *   <li>解析 key 为 {@link ResourceLocation}</li>
 *   <li>调用 {@link MultiblockBase#getMteById(ResourceLocation)} 在 GregTech MTE 注册表中查找</li>
 *   <li>若 MTE 为 {@link MultiblockControllerBase}，构造预览渲染器</li>
 *   <li>缓存到 {@code Map<String, MultiblockPreviewRenderer>}</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class MultiblockPreviewResolver {

    private static final Map<String, MultiblockPreviewRenderer> CACHE = new HashMap<>();

    private MultiblockPreviewResolver() {}

    /**
     * 根据 ResourceLocation 字符串解析（带缓存）。
     *
     * @param key metaTileEntityId 的字符串形式（如 "gregtech:electric_blast_furnace"）
     * @return 对应的预览渲染器，若无法解析则返回 null
     */
    public static MultiblockPreviewRenderer resolve(String key) {
        if (key == null || key.isEmpty()) return null;

        MultiblockPreviewRenderer cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            ResourceLocation rl = new ResourceLocation(key.trim());
            MetaTileEntity mte = MultiblockBase.getMteById(rl);
            if (mte instanceof MultiblockControllerBase controller) {
                MultiblockPreviewRenderer renderer = new MultiblockPreviewRenderer(controller);
                CACHE.put(key, renderer);
                return renderer;
            }
        } catch (Exception ignored) {
            // key 格式无效或 MTE 未注册
        }

        return null;
    }
}
