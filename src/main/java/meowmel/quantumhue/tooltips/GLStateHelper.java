package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

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

    /**
     * 启用 Scissor Test，将渲染裁剪到指定 layout 的边界内。
     * 用于动画期间防止文本溢出外框。
     */
    public static void enableScissorClip(TooltipLayout layout) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();

        int scissorX = layout.x * scale;
        int scissorY = mc.displayHeight - (layout.y + layout.height) * scale;
        int scissorW = Math.max(0, layout.width * scale);
        int scissorH = Math.max(0, layout.height * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
    }

    /**
     * 禁用 Scissor Test，恢复完整渲染区域。
     */
    public static void disableScissorClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}