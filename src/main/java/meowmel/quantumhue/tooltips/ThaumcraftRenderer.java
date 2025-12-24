package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.lib.UtilsFX;

public class ThaumcraftRenderer {
    /**
     * 渲染Thaumcraft要素图标（单行显示，不换行，超过宽度直接溢出）
     */
    public static void renderAspectIcons(AspectList aspects, int x, int y, int maxWidth) {
        if (aspects == null || aspects.size() == 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // 保存当前GL状态
        GlStateManager.pushMatrix();

        try {
            // 设置渲染状态
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            // 渲染所有要素（单行，不换行）
            int index = 0;
            for (Aspect aspect : aspects.getAspectsSortedByAmount()) {
                if (aspect == null) continue;

                // 计算每个要素的位置（依次向右排列）
                int aspectX = x + index * 18; // 每个要素宽度18像素
                int aspectY = y;

                // 调用Thaumcraft的drawTag方法渲染要素
                UtilsFX.drawTag(aspectX, aspectY, aspect, (float)aspects.getAmount(aspect), 0, 300.0);

                index++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 恢复渲染状态
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

}