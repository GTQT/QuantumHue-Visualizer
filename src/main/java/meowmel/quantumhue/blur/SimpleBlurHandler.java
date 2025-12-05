package meowmel.quantumhue.blur;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置驱动的模糊效果处理器
 */
@SideOnly(Side.CLIENT)
public class SimpleBlurHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean isBlurActive = false;
    private long lastGuiOpenTime = 0;

    // 缓存配置值以提高性能
    private final Set<String> blacklistCache = new HashSet<>();
    private final Set<String> whitelistCache = new HashSet<>();
    private boolean configEnabled = true;
    private boolean blurChat = true;
    private boolean dynamicBlur = true;
    private String customShader = "shaders/post/blur.json";
    private boolean debugMode = false;
    private boolean disableOnWorldLoad = true;
    private boolean disableOnDebugScreen = true;
    private boolean resetOnGuiSwitch = true;

    public SimpleBlurHandler() {
        refreshConfigCache();
    }

    /**
     * 刷新配置缓存
     */
    private void refreshConfigCache() {
        configEnabled = QuantumHueConfig.blur.enabled;
        blurChat = QuantumHueConfig.blur.blurChat;
        dynamicBlur = QuantumHueConfig.blur.dynamicBlur;
        customShader = QuantumHueConfig.blur.customShader;
        debugMode = QuantumHueConfig.blur.debugMode;
        disableOnWorldLoad = QuantumHueConfig.blur.disableOnWorldLoad;
        disableOnDebugScreen = QuantumHueConfig.blur.disableOnDebugScreen;
        resetOnGuiSwitch = QuantumHueConfig.blur.resetOnGuiSwitch;

        // 更新列表缓存
        blacklistCache.clear();
        blacklistCache.addAll(Arrays.asList(QuantumHueConfig.blur.blacklist));

        whitelistCache.clear();
        whitelistCache.addAll(Arrays.asList(QuantumHueConfig.blur.whitelist));
    }

    /**
     * 检查GUI是否应该启用模糊
     */
    private boolean shouldBlur(Object gui) {
        if (!configEnabled) return false;
        if (gui == null) return false;

        String guiClassName = gui.getClass().getName();

        // 检查是否是聊天界面
        if (!blurChat && guiClassName.contains("Chat")) {
            return false;
        }

        // 检查白名单（如果白名单不为空，则只启用白名单中的GUI）
        if (!whitelistCache.isEmpty() && !whitelistCache.contains(guiClassName)) {
            return false;
        }

        // 检查黑名单
        if (blacklistCache.contains(guiClassName)) {
            return false;
        }

        // 特殊情况的处理
        if (disableOnWorldLoad && guiClassName.contains("GuiDownloadTerrain")) {
            return false;
        }

        return !disableOnDebugScreen || !guiClassName.contains("GuiDebug");
    }

    /**
     * 根据配置获取着色器路径
     */
    private ResourceLocation getShaderPath() {
        return new ResourceLocation(customShader);
    }

    /**
     * 开始模糊效果
     */
    private void startBlur() {
        if (mc.world == null || !configEnabled) return;

        try {
            if (!isBlurActive) {
                mc.entityRenderer.loadShader(getShaderPath());
                isBlurActive = true;

                if (debugMode) {
                    QuantumHue.LOGGER.info("Blur effect started");
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                QuantumHue.LOGGER.error("Failed to load blur shader: " + e.getMessage());
            }
        }
    }

    /**
     * 停止模糊效果
     */
    private void stopBlur() {
        if (isBlurActive) {
            mc.entityRenderer.stopUseShader();
            isBlurActive = false;

            if (debugMode) {
                QuantumHue.LOGGER.info("Blur effect stopped");
            }
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (mc.world == null) return;

        // 刷新配置缓存（确保使用最新的配置）
        refreshConfigCache();

        boolean shouldBlur = shouldBlur(event.getGui());

        if (shouldBlur && !isBlurActive) {
            // 如果需要重置模糊效果
            if (resetOnGuiSwitch && isBlurActive) {
                stopBlur();
            }
            startBlur();
            lastGuiOpenTime = System.currentTimeMillis();
        } else if (!shouldBlur && isBlurActive) {
            stopBlur();
        }

        // 调试信息
        if (debugMode && event.getGui() != null) {
            QuantumHue.LOGGER.info("GUI opened: " + event.getGui().getClass().getName() +
                    ", Blur enabled: " + shouldBlur);
        }
    }

    /**
     * 手动启用模糊效果
     */
    public void enableBlur() {
        if (!isBlurActive) {
            startBlur();
        }
    }

    /**
     * 手动禁用模糊效果
     */
    public void disableBlur() {
        if (isBlurActive) {
            stopBlur();
        }
    }

    /**
     * 切换模糊效果状态
     */
    public void toggleBlur() {
        if (isBlurActive) {
            disableBlur();
        } else {
            enableBlur();
        }
    }

    /**
     * 添加临时黑名单项（运行时）
     */
    public void addToBlacklist(String guiClassName) {
        blacklistCache.add(guiClassName);
    }

    /**
     * 从黑名单中移除
     */
    public void removeFromBlacklist(String guiClassName) {
        blacklistCache.remove(guiClassName);
    }

    /**
     * 清空运行时黑名单
     */
    public void clearRuntimeBlacklist() {
        blacklistCache.clear();
        // 重新加载配置的黑名单
        blacklistCache.addAll(Arrays.asList(QuantumHueConfig.blur.blacklist));
    }
}