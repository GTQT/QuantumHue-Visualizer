package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.applecore.AppleSkinRenderer;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftIntegration;
import meowmel.quantumhue.tooltips.thaumcraft.ThaumcraftRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import thaumcraft.api.aspects.AspectList;

import java.util.List;

@SideOnly(Side.CLIENT)
public class AdvancedTooltipHandler {

    private static String currentItemId = null;
    private static int currentPage = 0;
    private static KeyState currentKeyState = new KeyState();
    private static TooltipColors cachedColors = null;
    private static AspectList cachedAspects = null;
    private static AppleSkinIntegration.FoodInfo cachedFoodInfo = null;

    private final TooltipContentExtractor contentExtractor = new TooltipContentExtractor();
    private final TooltipLayoutManager layoutManager = new TooltipLayoutManager();
    private final TooltipRenderer tooltipRenderer = new TooltipRenderer();
    private final PaginationHandler paginationHandler = new PaginationHandler();
    private final ModInfoHelper modInfoHelper = new ModInfoHelper();

    public static int getCurrentPage() {
        return currentPage;
    }

    public static void setCurrentPage(int page) {
        currentPage = page;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String modName = modInfoHelper.getModName(stack);

        if (modName != null && !modInfoHelper.isModNameAlreadyPresent(event.getToolTip(), modName)) {
            event.getToolTip().add(TextFormatting.YELLOW + modName);
        }
    }

    @SubscribeEvent
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (shouldSkipCustomRender(event)) return;
        event.setCanceled(true);

        boolean isItemTooltip = !event.getStack().isEmpty();
        if (isItemTooltip) {
            renderCustomItemTooltip(event);
        } else {
            renderSimpleTooltip(event);
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
            currentItemId = itemId;
            currentPage = 0;
            currentKeyState = new KeyState();

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

        paginationHandler.calculatePagination(content, event.getScreenHeight(), currentKeyState);

        TooltipLayout layout = layoutManager.calculateLayout(content, event);

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

        tooltipRenderer.drawItemIcon(stack, layout.iconX, layout.iconY);
        tooltipRenderer.drawTooltipText(content, layout, event.getFontRenderer());

        GLStateHelper.restoreGLState();
        GlStateManager.popMatrix();
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