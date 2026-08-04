package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.comparison.EquipmentCompareHandler;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftIntegration;
import meowmel.quantumhue.wiki.WikiScreen;
import meowmel.quantumhue.wiki.gregtech.MultiblockBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import thaumcraft.api.aspects.AspectList;

import java.util.List;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

@SideOnly(Side.CLIENT)
public class AdvancedTooltipHandler {

    private static String currentItemId = null;
    private static TooltipColors cachedColors = null;
    private static AspectList cachedAspects = null;
    private static AppleSkinIntegration.FoodInfo cachedFoodInfo = null;

    // ── Wiki 集成 ──
    private static String wikiPageId = null;
    private static String lastWikiCheckedItemId = null;
    private static long wKeyPressStartTime = 0;
    private static boolean wWasDownLastFrame = false;
    private static final long WIKI_HOLD_THRESHOLD_MS = 400;

    private final TooltipContentExtractor contentExtractor = new TooltipContentExtractor();
    private final TooltipLayoutManager layoutManager = new TooltipLayoutManager();
    private final TooltipRenderer tooltipRenderer = new TooltipRenderer();
    private final ModInfoHelper modInfoHelper = new ModInfoHelper();
    private final TooltipAnimation tooltipAnimation = new TooltipAnimation();
    private final ItemIconAnimation itemIconAnimation = new ItemIconAnimation();
    private final EquipmentCompareHandler compareHandler = new EquipmentCompareHandler();
    private TooltipLayout lastRenderedLayout = null;
    private long lastRenderTime = 0;
    private static final long ANIMATION_THRESHOLD_MS = 500;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String modName = modInfoHelper.getModName(stack);

        if (modName != null && !modInfoHelper.isModNameAlreadyPresent(event.getToolTip(), modName)) {
            event.getToolTip().add(TextFormatting.YELLOW + modName);
        }

        if(isModLoaded("gregtech")) {
            String itemId = TooltipUtils.getItemUniqueId(stack);
            if (!itemId.equals(lastWikiCheckedItemId)) {
                lastWikiCheckedItemId = itemId;
                MultiblockBase wikiEntry = MultiblockBase.getDocumentedMultiblockFor(stack);
                wikiPageId = wikiEntry != null ? wikiEntry.getPageId() : null;
            }
            if (wikiPageId != null) {
                event.getToolTip().add(2, TextFormatting.GRAY + " 长按"+TextFormatting.AQUA + " [W]" + TextFormatting.GRAY + "打开Wiki攻略");
            }
        }
    }

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && TooltipScroll.isActive()) {
            TooltipScroll.onInput(-Integer.signum(dWheel));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (shouldSkipCustomRender(event)) return;
        event.setCanceled(true);

        boolean isItemTooltip = !event.getStack().isEmpty();

        if (!isItemTooltip) {
            // 切换到简单tooltip时，重置物品状态并停止动画
            currentItemId = null;
            tooltipAnimation.stopAnimation();
            itemIconAnimation.stop();
            renderSimpleTooltip(event);
        } else {
            renderCustomItemTooltip(event);
        }
    }

    private boolean shouldSkipCustomRender(RenderTooltipEvent.Pre event) {
        String screenName = Minecraft.getMinecraft().currentScreen.getClass().getName();
        for (String pattern : QuantumHueConfig.TooltipColor.skippedGuiPatterns) {
            if (screenName.contains(pattern.replace("*", ""))) {
                return true;
            }
        }
        return false;
    }

    private void renderCustomItemTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getStack();
        String itemId = TooltipUtils.getItemUniqueId(stack);

        if (!itemId.equals(currentItemId)) {
            if (QuantumHueConfig.tooltip_animation.enabled) {
                long timeSinceLastRender = System.currentTimeMillis() - lastRenderTime;
                if (timeSinceLastRender < ANIMATION_THRESHOLD_MS && lastRenderedLayout != null) {
                    tooltipAnimation.startAnimation(lastRenderedLayout);
                }
            }

            currentItemId = itemId;
            TooltipScroll.reset();

            itemIconAnimation.trigger();

            cachedColors = TooltipColorHelper.getTooltipColors(stack);
            if (ThaumcraftIntegration.isThaumcraftAvailable()) {
                cachedAspects = ThaumcraftIntegration.getAspects(stack);
            } else {
                cachedAspects = null;
            }
            if (AppleSkinIntegration.isAppleSkinAvailable()) {
                EntityPlayer player = Minecraft.getMinecraft().player;
                cachedFoodInfo = AppleSkinIntegration.getFoodInfo(stack, player);
            } else {
                cachedFoodInfo = null;
            }
        }

        TooltipColors colors = cachedColors;
        if (colors == null) {
            colors = TooltipColorHelper.getTooltipColors(stack);
        }

        TooltipContent rawContent = contentExtractor.extractTooltipContent(event.getLines(), stack);
        FontRenderer font = event.getFontRenderer();
        List<String> wrappedLines = TooltipUtils.wrapTooltipText(rawContent.remainingLines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);

        TooltipContent content = new TooltipContent(rawContent.itemName, rawContent.modName, wrappedLines);
        content.wrappedLines = wrappedLines;

        if (ThaumcraftIntegration.isThaumcraftAvailable() && cachedAspects != null) {
            content.aspects = cachedAspects;
            boolean isShiftKeyDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            boolean defaultShowAspects = ThaumcraftIntegration.shouldShowAspectsByDefault();
            content.showAspects = (isShiftKeyDown != defaultShowAspects);
        }

        if (AppleSkinIntegration.isAppleSkinAvailable() && cachedFoodInfo != null) {
            content.foodInfo = cachedFoodInfo;
            content.showFoodInfo = AppleSkinIntegration.shouldShowFoodInfo();
        }

        // 先计算布局（会设置 content.totalContentHeight / needsScroll）
        TooltipLayout layout = layoutManager.calculateLayout(content, event);

        // 更新滚动状态
        int headerHeight = layout.separatorY - layout.y + TooltipConstants.TEXT_PADDING;
        int scrollableContentHeight = content.totalContentHeight - headerHeight;
        int visibleScrollArea = layout.height - headerHeight;
        TooltipScroll.update(scrollableContentHeight, visibleScrollArea);

        if (QuantumHueConfig.tooltip_animation.enabled) {
            layout = tooltipAnimation.getAnimatedLayout(layout);
        }

        GlStateManager.pushMatrix();
        GLStateHelper.setupGLState();

        tooltipRenderer.drawTooltipBackground(layout, colors, content);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        // 动画中或滚动激活时启用裁剪
        boolean needsClip = tooltipAnimation.isAnimating() || TooltipScroll.isActive();
        if (needsClip) {
            GLStateHelper.enableScissorClip(layout);
        }

        float scrollOffset = TooltipScroll.getScroll();
        tooltipRenderer.drawItemIcon(stack, layout.iconX, layout.iconY, itemIconAnimation.getScale());
        tooltipRenderer.drawTooltipText(content, layout, event.getFontRenderer(), scrollOffset);

        if (needsClip) {
            GLStateHelper.disableScissorClip();
        }

        GLStateHelper.restoreGLState();
        GlStateManager.popMatrix();

        // ── 装备对比 ──
        if (QuantumHueConfig.equipmentComparison.enabled && !stack.isEmpty()) {
            GlStateManager.pushMatrix();
            compareHandler.renderComparisons(stack, content, layout, colors, event, font);
            GlStateManager.popMatrix();
        }

        // ── Wiki 长按 W 检测 ──
        if (wikiPageId != null) {
            boolean wDown = Keyboard.isKeyDown(Keyboard.KEY_W);
            if (wDown && !wWasDownLastFrame) {
                wKeyPressStartTime = System.currentTimeMillis();
            }
            if (wDown && System.currentTimeMillis() - wKeyPressStartTime > WIKI_HOLD_THRESHOLD_MS) {
                Minecraft.getMinecraft().displayGuiScreen(null);
                WikiScreen.open(wikiPageId);
                wikiPageId = null;
            }
            wWasDownLastFrame = wDown;
        } else {
            wWasDownLastFrame = false;
        }

        lastRenderedLayout = layout;
        lastRenderTime = System.currentTimeMillis();
    }

    private void renderSimpleTooltip(RenderTooltipEvent.Pre event) {
        List<String> lines = event.getLines();
        if (lines.isEmpty()) return;

        FontRenderer font = event.getFontRenderer();
        List<String> wrappedLines = TooltipUtils.wrapSimpleTooltipText(lines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);

        TooltipLayout layout = layoutManager.calculateSimpleLayout(wrappedLines, event);
        TooltipColors colors = TooltipColorHelper.getTooltipColors();

        GlStateManager.pushMatrix();
        GLStateHelper.setupGLState();

        tooltipRenderer.drawSimpleTooltipBackground(layout, colors);
        tooltipRenderer.drawSimpleTooltipText(wrappedLines, layout, font);

        GLStateHelper.restoreGLState();
        GlStateManager.popMatrix();
    }
}