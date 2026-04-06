package meowmel.quantumhue.blur;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.gui.advancements.GuiScreenAdvancements;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class SimpleBlurHandler {

    private static final Logger LOGGER = LogManager.getLogger(SimpleBlurHandler.class);
    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean isBlurActive = false;

    private boolean configEnabled = true;
    private String customShader = "shaders/post/blur.json";

    // 允许触发模糊的 GUI 白名单
    private final Set<Class<? extends GuiScreen>> allowedGuis = new HashSet<>();

    public SimpleBlurHandler() {
        refreshConfigCache();
        // 注册允许的 GUI
        allowedGuis.add(GuiIngameMenu.class);
        allowedGuis.add(GuiOptions.class);
        allowedGuis.add(GuiVideoSettings.class);
        allowedGuis.add(GuiLanguage.class);
        allowedGuis.add(GuiControls.class);
        allowedGuis.add(GuiCustomizeSkin.class);
        allowedGuis.add(ScreenChatOptions.class);
        allowedGuis.add(GuiSnooper.class);
        allowedGuis.add(GuiScreenResourcePacks.class);
        allowedGuis.add(GuiScreenOptionsSounds.class);
        allowedGuis.add(GuiScreenRealmsProxy.class);
        allowedGuis.add(GuiChat.class);
        allowedGuis.add(GuiInventory.class);
        allowedGuis.add(GuiContainerCreative.class);
        allowedGuis.add(GuiContainer.class); // 已覆盖所有基础容器（含GuiFurnace等）
        allowedGuis.add(GuiScreenAdvancements.class);
        allowedGuis.add(GuiShareToLan.class);
        allowedGuis.add(GuiStats.class);
    }

    /**
     * 刷新配置缓存
     */
    private void refreshConfigCache() {
        try {
            configEnabled = QuantumHueConfig.blur.enabled;
            customShader = QuantumHueConfig.blur.customShader;
        } catch (Exception ignored) {
            // 防止模组初始化阶段配置未就绪
        }
    }

    /**
     * 根据配置获取着色器路径
     */
    private ResourceLocation getShaderPath() {
        return new ResourceLocation(customShader);
    }

    /**
     * 监听GUI打开/关闭事件，精准控制模糊开关
     */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        refreshConfigCache();

        // 配置关闭时强制清理模糊
        if (!configEnabled) {
            if (isBlurActive) disableBlur();
            return;
        }

        GuiScreen openedGui = event.getGui();
        boolean shouldBlur = false;

        if (openedGui != null) {
            LOGGER.debug("[QuantumHue] GUI opened: {}", openedGui.getClass().getName());
            for (Class<? extends GuiScreen> allowedClass : allowedGuis) {
                if (allowedClass.isInstance(openedGui)) {
                    shouldBlur = true;
                    break;
                }
            }
        }

        // 状态切换：符合条件开启，否则关闭
        if (shouldBlur) {
            enableBlur();
        } else {
            disableBlur();
        }
    }

    /**
     * 监听世界卸载事件（切换维度/退出存档），防止服务端线程调用 GL 崩溃
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        // 仅处理客户端世界
        if (!event.getWorld().isRemote) return;
        disableBlur();
    }

    /**
     * 启用模糊
     */
    public void enableBlur() {
        runOnClientThread(() -> {
            if (configEnabled && !isBlurActive) {
                try {
                    mc.entityRenderer.loadShader(getShaderPath());
                    isBlurActive = true;
                    LOGGER.debug("[QuantumHue] 模糊效果已启用");
                } catch (Exception e) {
                    LOGGER.error("[QuantumHue] 模糊着色器加载失败: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 禁用模糊
     */
    public void disableBlur() {
        runOnClientThread(() -> {
            if (isBlurActive) {
                try {
                    mc.entityRenderer.stopUseShader();
                    isBlurActive = false;
                    LOGGER.debug("[QuantumHue] 模糊效果已禁用");
                } catch (Exception e) {
                    LOGGER.error("[QuantumHue] 模糊着色器停止失败: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 确保所有 OpenGL 操作仅在 Minecraft 客户端主线程执行
     */
    private void runOnClientThread(Runnable task) {
        if (mc.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            mc.addScheduledTask(task);
        }
    }
}