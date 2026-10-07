package meowmel.quantumhue.api.utils;

import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.mixins.GuiMainMenuAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.GuiNotification;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ClientHelper {
    /**
     * Creates a scissor test using minecraft screen coordinates instead of pixel coordinates.
     */
    public static void scissor(int screenX, int screenY, int boxWidth, int boxHeight) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledRes = new ScaledResolution(mc);
        int scale = scaledRes.getScaleFactor();

        int x = screenX * scale;
        int y = mc.displayHeight - (screenY * scale + boxHeight * scale);
        int width = Math.max(0, boxWidth * scale);
        int height = Math.max(0, boxHeight * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    /**
     * The {@link GuiMainMenu} instance used for panorama rendering.
     */
    public static @Nonnull GuiMainMenu MENU_INSTANCE = new GuiMainMenu();
    /**
     * Whether {@link #MENU_INSTANCE} has already been initialized once.
     * <p>
     * {@link GuiScreen#setWorldAndResolution} ends with a call to {@code initGui()}, and
     * {@code GuiMainMenu#initGui()} allocates a brand-new {@code DynamicTexture(256, 256)} plus a
     * unique {@code dynamic/background_N} key every single time. Neither is ever released, so
     * calling it per-frame (or per resolution change) leaks ~262KB each time and eventually OOMs.
     * We therefore run the full init exactly once and only update the size fields afterwards.
     */
    private static boolean menuInited = false;

    private static final ResourceLocation MENU_BACKGROUND = new ResourceLocation(Tags.MOD_ID, "textures/gui/menu_background.png");
    private static final ResourceLocation MENU_LIST_BACKGROUND = new ResourceLocation(Tags.MOD_ID, "textures/gui/menu_list_background.png");
    private static final ResourceLocation HEADER_SEPARATOR = new ResourceLocation(Tags.MOD_ID, "textures/gui/header_separator.png");
    private static final ResourceLocation FOOTER_SEPARATOR = new ResourceLocation(Tags.MOD_ID, "textures/gui/footer_separator.png");
    private static final ResourceLocation INWORLD_MENU_BACKGROUND = new ResourceLocation(Tags.MOD_ID, "textures/gui/inworld_menu_background.png");
    private static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = new ResourceLocation(Tags.MOD_ID, "textures/gui/inworld_menu_list_background.png");
    private static final ResourceLocation INWORLD_HEADER_SEPARATOR = new ResourceLocation(Tags.MOD_ID, "textures/gui/inworld_header_separator.png");
    private static final ResourceLocation INWORLD_FOOTER_SEPARATOR = new ResourceLocation(Tags.MOD_ID, "textures/gui/inworld_footer_separator.png");

    public static boolean renderWorldBackground(@Nullable GuiScreen screen, @Nonnull Minecraft mc, int width, int height) {
        if (!shouldModifyBG(screen)) return false;
        if (mc.world == null) {
            renderPanorama(mc);
        }
        renderMenuBackground(mc, width, height);
        return true;
    }

    private static void renderMenuBackground(@Nonnull Minecraft mc, int width, int height) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        mc.getTextureManager().bindTexture(mc.world == null ? MENU_BACKGROUND : INWORLD_MENU_BACKGROUND);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0.0F, 0.0F, width, height, 32, 32);

        if (blend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }

    public static void renderPanorama(@Nonnull Minecraft mc) {
        final ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        if (!menuInited) {
            // Full init exactly once: this is the only call that triggers initGui() and thus
            // registers a DynamicTexture. Doing it every frame/resolution change leaks memory.
            MENU_INSTANCE.setWorldAndResolution(mc, scaledWidth, scaledHeight);
            menuInited = true;
        } else {
            // Afterwards just keep the size in sync; initGui() is never re-run so nothing leaks.
            MENU_INSTANCE.width = scaledWidth;
            MENU_INSTANCE.height = scaledHeight;
        }

        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();

        ((GuiMainMenuAccessor) MENU_INSTANCE).invokeRenderSkybox(0, 0, mc.getTickLength());

        if (alpha) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
        if (depth) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }

    public static void renderListSeparators(@Nonnull Minecraft mc, int left, int top, int right, int bottom) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        mc.getTextureManager().bindTexture(mc.world == null ? HEADER_SEPARATOR : INWORLD_HEADER_SEPARATOR);
        Gui.drawModalRectWithCustomSizedTexture(left, top - 2, 0.0F, 0.0F, right - left, 2, 32, 2);
        mc.getTextureManager().bindTexture(mc.world == null ? FOOTER_SEPARATOR : INWORLD_FOOTER_SEPARATOR);
        Gui.drawModalRectWithCustomSizedTexture(left, bottom, 0.0F, 0.0F, right - left, 2, 32, 2);

        if (blend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }

    public static void renderListBackground(@Nonnull Minecraft mc, int left, int top, int right, int bottom, float amountScrolled) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        mc.getTextureManager().bindTexture(mc.world == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND);
        Gui.drawModalRectWithCustomSizedTexture(left, top, right, bottom + amountScrolled, right - left, bottom - top, 32, 32);

        if (blend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }
    private static boolean shouldModifyBG(@Nullable GuiScreen screen) {
        if (!QuantumHue.GAME_LOADING_DONE || screen == null) return false;
        // The fluxloading exclusion that used to sit here is gone along with the mod itself: the
        // world-loading screens are ours now, via LoadingScreenRendererMixin and GtqtMenuChrome.
        if (Loader.isModLoaded("catalogue") &&
                (screen instanceof GuiModList)
        ) return false;
        //noinspection RedundantIfStatement
        if (screen instanceof GuiNotification) return false;
        return true;
    }
}
