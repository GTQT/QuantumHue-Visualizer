package meowmel.quantumhue.blur;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置驱动的模糊效果处理器 - 仅在游戏内场景启用
 *
 * 启用场景：背包、聊天栏、容器界面、成就/统计等游戏内GUI
 * 禁用场景：主菜单、世界选择、模组列表、选项等菜单级GUI
 */
@SideOnly(Side.CLIENT)
public class SimpleBlurHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean isBlurActive = false;

    private final Set<String> blacklistCache = new HashSet<>();
    private final Set<String> whitelistCache = new HashSet<>();

    private boolean configEnabled = true;
    private boolean blurChat = true;
    private String customShader = "shaders/post/blur.json";
    private boolean debugMode = false;
    private boolean resetOnGuiSwitch = true;
    private boolean gameOnlyMode = true;

    public SimpleBlurHandler() {
        refreshConfigCache();
    }

    /**
     * 刷新配置缓存
     */
    private void refreshConfigCache() {
        configEnabled = QuantumHueConfig.blur.enabled;
        blurChat = QuantumHueConfig.blur.blurChat;
        customShader = QuantumHueConfig.blur.customShader;
        debugMode = QuantumHueConfig.blur.debugMode;
        resetOnGuiSwitch = QuantumHueConfig.blur.resetOnGuiSwitch;

        blacklistCache.clear();
        blacklistCache.addAll(Arrays.asList(QuantumHueConfig.blur.blacklist));

        whitelistCache.clear();
        whitelistCache.addAll(Arrays.asList(QuantumHueConfig.blur.whitelist));
    }

    /**
     * 判断是否是游戏内相关的GUI
     */
    private boolean isInGameScreen(Object gui) {
        if (gui == null || mc.world == null) {
            return false;
        }

        String className = gui.getClass().getName();

        if (gui instanceof GuiContainer) {
            return true;
        }
        if (gui instanceof GuiChat) {
            return blurChat;
        }

        if (className.contains("GuiAchievements") ||
                className.contains("GuiStats") ||
                className.contains("GuiScreenRecipeBook") ||
                className.contains("GuiScreenBook") ||
                className.contains("GuiBeacon") ||
                className.contains("GuiAnvil") ||
                className.contains("GuiEnchantment") ||
                className.contains("GuiMerchant") ||
                className.contains("GuiHorse") ||
                className.contains("GuiScreenDemo")) {
            return true;
        }

        if (className.contains("ConfigGui") && !className.contains("net.minecraftforge")) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否是应该排除的菜单级GUI
     */
    private boolean isMenuScreen(Object gui) {
        if (gui == null) {
            return true;
        }

        String className = gui.getClass().getName();

        if (className.equals("net.minecraft.client.gui.GuiMainMenu") ||
                className.equals("net.minecraft.client.gui.GuiWorldSelection") ||
                className.equals("net.minecraft.client.gui.GuiMultiplayer") ||
                className.equals("net.minecraft.client.gui.GuiSelectWorld") ||
                className.equals("net.minecraftforge.fml.client.GuiModList") ||
                className.equals("net.minecraft.client.gui.GuiOptions") ||
                className.equals("net.minecraft.client.gui.GuiControls") ||
                className.equals("net.minecraft.client.gui.GuiVideoSettings") ||
                className.equals("net.minecraft.client.gui.GuiScreenOptionsSounds") ||
                className.equals("net.minecraft.client.gui.GuiLanguage") ||
                className.equals("net.minecraft.client.gui.GuiSnooper") ||
                className.equals("net.minecraft.client.gui.GuiShareToLan") ||
                className.equals("net.minecraft.client.gui.GuiDisconnected") ||
                className.equals("net.minecraft.client.gui.GuiScreenRealmsProxy")) {
            return true;
        }

        if (className.contains("MainMenu") ||
                className.contains("WorldSelection") ||
                className.contains("ModList")) {
            return true;
        }

        return false;
    }

    /**
     * 检查GUI是否应该启用模糊（最终决策）
     */
    private boolean shouldBlur(Object gui) {
        if (!configEnabled || gui == null) {
            return false;
        }

        String guiClassName = gui.getClass().getName();

        if (blacklistCache.contains(guiClassName)) {
            return false;
        }

        if (!whitelistCache.isEmpty()) {
            return whitelistCache.contains(guiClassName);
        }

        if (gameOnlyMode) {
            if (isMenuScreen(gui)) {
                return false;
            }
            return isInGameScreen(gui);
        }

        if (!blurChat && guiClassName.contains("Chat")) {
            return false;
        }
        if (guiClassName.contains("GuiDownloadTerrain")) {
            return false;
        }
        if (guiClassName.contains("GuiDebug")) {
            return false;
        }

        return true;
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
        if (!configEnabled) return;

        try {
            if (!isBlurActive) {
                mc.entityRenderer.loadShader(getShaderPath());
                isBlurActive = true;

                if (debugMode) {
                    QuantumHue.LOGGER.info("[Blur] Effect started for: " +
                            (mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "null"));
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                QuantumHue.LOGGER.error("[Blur] Failed to load shader: " + e.getMessage(), e);
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
                QuantumHue.LOGGER.info("[Blur] Effect stopped");
            }
        }
    }

    /**
     * 监听世界加载事件，确保状态同步
     */
    @SubscribeEvent
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (debugMode) {
            QuantumHue.LOGGER.info("[Blur] World loaded, refreshing state");
        }
        refreshConfigCache();
        if (gameOnlyMode && mc.currentScreen != null && isInGameScreen(mc.currentScreen)) {
            startBlur();
        }
    }

    /**
     * 监听世界卸载事件，强制关闭模糊
     */
    @SubscribeEvent
    public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (debugMode) {
            QuantumHue.LOGGER.info("[Blur] World unloaded, disabling blur");
        }
        if (gameOnlyMode) {
            stopBlur();
        }
    }

    /**
     * 监听GUI打开事件，控制模糊启用/禁用
     */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!configEnabled) {
            if (isBlurActive) stopBlur();
            return;
        }

        refreshConfigCache();

        Object gui = event.getGui();
        boolean shouldEnable = shouldBlur(gui);

        if (debugMode && gui != null) {
            QuantumHue.LOGGER.info("[Blur] GUI: " + gui.getClass().getSimpleName() +
                    " | InGame: " + isInGameScreen(gui) +
                    " | Menu: " + isMenuScreen(gui) +
                    " | ShouldBlur: " + shouldEnable);
        }

        if (shouldEnable && !isBlurActive) {
            if (resetOnGuiSwitch) {
                stopBlur();
            }
            startBlur();
        } else if (!shouldEnable && isBlurActive) {
            stopBlur();
        }
    }

    /**
     * 监听游戏内渲染事件，处理动态切换
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Pre event) {
        if (!gameOnlyMode || !configEnabled) return;

        if (mc.currentScreen == null && isBlurActive) {
            stopBlur();
        }
    }

    /**
     * 手动启用模糊效果
     */
    public void enableBlur() {
        if (!isBlurActive && configEnabled) {
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
     * 设置游戏内模式
     */
    public void setGameOnlyMode(boolean enabled) {
        this.gameOnlyMode = enabled;
        if (debugMode) {
            QuantumHue.LOGGER.info("[Blur] GameOnlyMode set to: " + enabled);
        }
        if (mc.currentScreen != null) {
            if (shouldBlur(mc.currentScreen) && !isBlurActive) {
                startBlur();
            } else if (!shouldBlur(mc.currentScreen) && isBlurActive) {
                stopBlur();
            }
        }
    }

    /**
     * 添加临时黑名单项
     */
    public void addToBlacklist(String guiClassName) {
        blacklistCache.add(guiClassName);
        if (debugMode) {
            QuantumHue.LOGGER.info("[Blur] Added to blacklist: " + guiClassName);
        }
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
        blacklistCache.addAll(Arrays.asList(QuantumHueConfig.blur.blacklist));
    }

    /**
     * 获取当前模糊状态
     */
    public boolean isBlurActive() {
        return isBlurActive;
    }

    /**
     * 获取当前模式
     */
    public boolean isGameOnlyMode() {
        return gameOnlyMode;
    }
}