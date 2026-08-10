package meowmel.quantumhue.createworld.api.tab;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 标签页按钮的四态外观。
 * <p>Four-state appearance of a tab button; u/v are texture coordinates into tabs.png.</p>
 * NORMAL(0,0) / HOVER(0,24) / SELECTED(0,48) / SELECTED_HOVER(0,72)，每态 130x24。
 */
public enum TabState {
    NORMAL(0, 0, 0xFFFFFF, false),
    HOVER(0, 24, 0xFFFF55, true),
    SELECTED(0, 48, 0xFFFFFF, false),
    SELECTED_HOVER(0, 72, 0xFFFF55, true);

    private static final int YELLOW_COLOR = 0xFFFF55;
    private static final int WHITE_COLOR = 0xFFFFFF;

    /** 纹理 u 坐标 / Texture u coordinate */
    public final int u;
    /** 纹理 v 坐标 / Texture v coordinate */
    public final int v;
    private final int baseTextColor;
    private final boolean isHighlight;

    TabState(int u, int v, int textColor, boolean isHighlight) {
        this.u = u;
        this.v = v;
        this.baseTextColor = textColor;
        this.isHighlight = isHighlight;
    }

    public int getTextColor() {
        if (this.isHighlight) {
            // 检测是否启用现代纹理资源包 + vintagefix，决定悬停文字用白色还是黄色
            List<IResourcePack> theList = getResourcePackList();
            boolean modernityEnabled = theList.stream().anyMatch(pack -> pack.getPackName().toLowerCase(Locale.ROOT).contains("modernity"));
            boolean mcntEnabled = theList.stream().anyMatch(pack -> pack.getPackName().toLowerCase(Locale.ROOT).contains("mc-new-textures"));
            boolean archaicFixLoaded = Loader.isModLoaded("vintagefix");
            boolean configEnabled = QuantumHueConfig.createWorld != null && QuantumHueConfig.createWorld.topTabCharatorModernWhite;
            if (archaicFixLoaded && configEnabled && (modernityEnabled || mcntEnabled)) {
                return WHITE_COLOR;
            }
            return YELLOW_COLOR;
        }
        return this.baseTextColor;
    }

    private static List<IResourcePack> getResourcePackList() {
        LinkedHashSet<IResourcePack> resourcePacks = new LinkedHashSet<>();
        SimpleReloadableResourceManager manager = (SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        Map<String, FallbackResourceManager> domainManagers = ObfuscationReflectionHelper.getPrivateValue(SimpleReloadableResourceManager.class, manager, "domainResourceManagers");
        if (domainManagers != null) {
            for (FallbackResourceManager fallback : domainManagers.values()) {
                List<IResourcePack> fallbackPacks = ObfuscationReflectionHelper.getPrivateValue(FallbackResourceManager.class, fallback, "resourcePacks");
                if (fallbackPacks != null) {
                    resourcePacks.addAll(fallbackPacks);
                }
            }
        }
        return new ArrayList<>(resourcePacks);
    }
}
