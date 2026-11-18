package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.comparison.EquipmentCompareHandler;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * tooltip 渲染入口：接管原版绘制，负责状态缓存、分页、动画、事件补发与装备对比。
 */
@SideOnly(Side.CLIENT)
public class AdvancedTooltipHandler {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue");
    private static final long ANIMATION_THRESHOLD_MS = 500;

    private final TooltipContentExtractor contentExtractor = new TooltipContentExtractor();
    private final TooltipLayoutManager layoutManager = new TooltipLayoutManager();
    private final TooltipRenderer tooltipRenderer = new TooltipRenderer();
    private final PaginationHandler paginationHandler = new PaginationHandler();
    private final ModInfoHelper modInfoHelper = new ModInfoHelper();
    private final TooltipAnimation tooltipAnimation = new TooltipAnimation();
    private final ItemIconAnimation itemIconAnimation = new ItemIconAnimation();
    private final EquipmentCompareHandler compareHandler = new EquipmentCompareHandler();

    /**
     * 当前悬停物品的 tooltip 状态。装备对比会在同一帧渲染多个 tooltip，
     * 因此状态按物品身份各自持有，互不覆盖。
     */
    private TooltipState state = null;
    private TooltipLayout lastRenderedLayout = null;
    private long lastRenderTime = 0;

    /** 在 tooltip 末尾补一行模组名，已存在时跳过。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String modName = modInfoHelper.getModName(stack);

        if (modName != null && !modInfoHelper.isModNameAlreadyPresent(event.getToolTip(), modName)) {
            event.getToolTip().add(TextFormatting.YELLOW + modName);
        }
    }

    /**
     * 接管原版 tooltip 绘制。先取消原版绘制再自绘；若自绘抛异常，catch 中补一份
     * 朴素 tooltip，避免画面空白。
     */
    @SubscribeEvent
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (shouldSkipCustomRender(event)) return;
        if (event.getLines().isEmpty()) return;

        event.setCanceled(true);
        try {
            if (event.getStack().isEmpty()) {
                state = null;
                tooltipAnimation.stopAnimation();
                itemIconAnimation.stop();
                renderSimpleTooltip(event);
            } else {
                renderCustomItemTooltip(event);
            }
        } catch (Throwable t) {
            LOGGER.error("QuantumHue tooltip rendering failed; drawing fallback tooltip", t);
            renderFallbackTooltip(event);
        }
    }

    /** 无 GUI 或命中跳过列表时不接管绘制，交回原版。 */
    private boolean shouldSkipCustomRender(RenderTooltipEvent.Pre event) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null) {
            return true;
        }
        String screenName = screen.getClass().getName();
        for (String pattern : QuantumHueConfig.TooltipColor.skippedGuiPatterns) {
            if (screenName.contains(pattern.replace("*", ""))) {
                return true;
            }
        }
        return false;
    }

    /** 物品与上一帧不同时重建状态；500ms 内从上一布局过渡，避免相邻 tooltip 跳变。 */
    private TooltipState getOrCreateState(ItemStack stack) {
        String itemId = TooltipUtils.getItemUniqueId(stack);
        if (state == null || !state.itemId.equals(itemId)) {
            if (QuantumHueConfig.tooltip_animation.enabled) {
                long timeSinceLastRender = System.currentTimeMillis() - lastRenderTime;
                if (timeSinceLastRender < ANIMATION_THRESHOLD_MS && lastRenderedLayout != null) {
                    tooltipAnimation.startAnimation(lastRenderedLayout);
                }
            }

            TooltipState next = new TooltipState(itemId);
            next.colors = TooltipColorHelper.getTooltipColors(stack);
            if (ThaumcraftIntegration.isThaumcraftAvailable()) {
                next.aspects = ThaumcraftIntegration.getAspects(stack);
            }
            if (AppleSkinIntegration.isAppleSkinAvailable()) {
                next.foodInfo = AppleSkinIntegration.getFoodInfo(stack, Minecraft.getMinecraft().player);
            }

            state = next;
            itemIconAnimation.trigger();
        }
        return state;
    }

    /** 物品 tooltip 主流程：解析内容 → 分页 → 布局 → 动画 → 绘制 → 装备对比。 */
    private void renderCustomItemTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getStack();
        TooltipState current = getOrCreateState(stack);
        TooltipColors colors = current.colors;
        if (colors == null) {
            colors = TooltipColorHelper.getTooltipColors(stack);
        }

        TooltipContent content = current.resolveContent(
                event.getLines(), stack, contentExtractor, event.getFontRenderer());
        applyEnvironment(content, current);
        paginationHandler.calculatePagination(content, event.getScreenHeight(), current.keyState);
        current.notePage(content.currentPage);

        TooltipLayout layout = layoutManager.calculateLayout(content, event);
        if (QuantumHueConfig.tooltip_animation.enabled) {
            layout = tooltipAnimation.getAnimatedLayout(layout);
        }

        FontRenderer font = event.getFontRenderer();
        drawTooltipFrame(stack, content, colors, layout, event, font, current);

        // ── 装备对比 ──
        if (QuantumHueConfig.equipmentComparison.enabled && !stack.isEmpty()) {
            GlStateManager.pushMatrix();
            try {
                compareHandler.renderComparisons(stack, content, layout, colors, event, font);
            } catch (Throwable t) {
                // 对比只是附加信息，失败了不应连累已经画好的主 tooltip
                LOGGER.error("QuantumHue equipment comparison rendering failed", t);
            } finally {
                GlStateManager.popMatrix();
            }
        }

        lastRenderedLayout = layout;
        lastRenderTime = System.currentTimeMillis();
    }

    private void applyEnvironment(TooltipContent content, TooltipState current) {
        if (ThaumcraftIntegration.isThaumcraftAvailable() && current.aspects != null) {
            content.aspects = current.aspects;
            boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            content.showAspects = shiftDown != ThaumcraftIntegration.shouldShowAspectsByDefault();
        }

        if (AppleSkinIntegration.isAppleSkinAvailable() && current.foodInfo != null) {
            content.foodInfo = current.foodInfo;
            content.showFoodInfo = AppleSkinIntegration.shouldShowFoodInfo();
        }
    }

    /**
     * 绘制主 tooltip。matrix / depth / blend / scissor 全部在 finally 里还原，
     * 绘制中途抛异常也不会把 GL 状态留给后续 GUI。
     *
     * <p>Forge 的 RenderTooltipEvent.PostBackground / PostText 是其它模组
     * （WAILA 系、伤害显示等）在 tooltip 上叠加内容的唯一入口。自绘接管了原版
     * 管线，就必须按原版时机补发，否则这些模组的叠加会静默消失。</p>
     */
    private void drawTooltipFrame(ItemStack stack, TooltipContent content, TooltipColors colors,
                                  TooltipLayout layout, RenderTooltipEvent.Pre event, FontRenderer font,
                                  TooltipState state) {
        GlStateManager.pushMatrix();
        boolean scissorEnabled = false;
        try {
            GLStateHelper.setupGLState();

            tooltipRenderer.drawTooltipBackground(layout, colors, content,
                    state != null ? state.elapsedSeconds() : 0.0F,
                    state != null ? state.pageFlipPulse() : 1.0F);
            postTooltipBackground(event, layout);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            float flipOffset = state != null ? state.pageFlipOffsetY() : 0.0F;
            if (tooltipAnimation.isAnimating() || flipOffset != 0.0F) {
                GLStateHelper.enableScissorClip(layout);
                scissorEnabled = true;
            }

            tooltipRenderer.drawItemIcon(stack, layout.iconX, layout.iconY, colors, itemIconAnimation.getScale());
            if (flipOffset != 0.0F) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.0F, flipOffset, 0.0D);
            }
            tooltipRenderer.drawTooltipText(content, layout, font, colors);
            if (flipOffset != 0.0F) {
                GlStateManager.popMatrix();
            }

            if (scissorEnabled) {
                GLStateHelper.disableScissorClip();
                scissorEnabled = false;
            }

            postTooltipText(event, layout);
        } finally {
            if (scissorEnabled) {
                GLStateHelper.disableScissorClip();
            }
            GLStateHelper.restoreGLState();
            GlStateManager.popMatrix();
        }
    }

    private void renderSimpleTooltip(RenderTooltipEvent.Pre event) {
        List<String> lines = event.getLines();
        if (lines.isEmpty()) return;

        FontRenderer font = event.getFontRenderer();
        List<String> wrappedLines = TooltipUtils.wrapSimpleTooltipText(lines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);

        TooltipLayout layout = layoutManager.calculateSimpleLayout(wrappedLines, event);
        TooltipColors colors = TooltipColorHelper.getTooltipColors();

        GlStateManager.pushMatrix();
        try {
            GLStateHelper.setupGLState();

            tooltipRenderer.drawSimpleTooltipBackground(layout, colors);
            postTooltipBackground(event, layout);

            tooltipRenderer.drawSimpleTooltipText(wrappedLines, layout, font);

            postTooltipText(event, layout);
        } finally {
            GLStateHelper.restoreGLState();
            GlStateManager.popMatrix();
        }
    }

    /**
     * 自绘失败时的兜底：画一份不带特效的朴素 tooltip，保证信息可见。
     * 不再补发事件，避免与出错前已经发出的 PostBackground 重复叠加。
     */
    private void renderFallbackTooltip(RenderTooltipEvent.Pre event) {
        try {
            List<String> lines = event.getLines();
            if (lines.isEmpty()) return;

            FontRenderer font = event.getFontRenderer();
            List<String> wrappedLines = TooltipUtils.wrapSimpleTooltipText(lines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);
            TooltipLayout layout = layoutManager.calculateSimpleLayout(wrappedLines, event);

            GlStateManager.pushMatrix();
            try {
                GLStateHelper.setupGLState();
                tooltipRenderer.drawSimpleTooltipBackground(layout, TooltipColorHelper.getTooltipColors());
                tooltipRenderer.drawSimpleTooltipText(wrappedLines, layout, font);
            } finally {
                GLStateHelper.restoreGLState();
                GlStateManager.popMatrix();
            }
        } catch (Throwable t) {
            LOGGER.error("QuantumHue fallback tooltip rendering also failed", t);
        }
    }

    private void postTooltipBackground(RenderTooltipEvent.Pre event, TooltipLayout layout) {
        MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostBackground(
                event.getStack(), event.getLines(),
                layout.x + TooltipConstants.TEXT_PADDING,
                layout.y + TooltipConstants.TEXT_PADDING,
                event.getFontRenderer(),
                Math.max(0, layout.width - TooltipConstants.TEXT_PADDING * 2),
                Math.max(0, layout.height - TooltipConstants.TEXT_PADDING * 2)));
    }

    private void postTooltipText(RenderTooltipEvent.Pre event, TooltipLayout layout) {
        MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(
                event.getStack(), event.getLines(),
                layout.x + TooltipConstants.TEXT_PADDING,
                layout.y + TooltipConstants.TEXT_PADDING,
                event.getFontRenderer(),
                Math.max(0, layout.width - TooltipConstants.TEXT_PADDING * 2),
                Math.max(0, layout.height - TooltipConstants.TEXT_PADDING * 2)));
    }
}
