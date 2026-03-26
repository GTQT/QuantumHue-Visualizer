package meowmel.quantumhue.tooltips;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GLStateHelper {

    public static void setupGLState() {
        GlStateManager.disableDepth();
        GlStateManager.translate(0.0F, 0.0F, 500.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
    }

    public static void restoreGLState() {
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.translate(0.0F, 0.0F, -500.0F);
    }
}