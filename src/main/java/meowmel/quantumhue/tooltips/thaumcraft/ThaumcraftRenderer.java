package meowmel.quantumhue.tooltips.thaumcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.lib.UtilsFX;

public class ThaumcraftRenderer {
    /**
     * 渲染Thaumcraft要素图标（单行显示，不换行，超过宽度直接溢出）
     * 修改版本：手动控制数字位置
     */
    public static void renderAspectIcons(AspectList aspects, int x, int y) {
        if (aspects == null || aspects.size() == 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen gui = mc.currentScreen;
        if (gui instanceof GuiContainer) {
            // 保存当前GL状态（使用GL11而不是GlStateManager以匹配原版）
            GL11.glPushMatrix();

            try {
                // 渲染所有要素（单行，不换行）
                int index = 0;
                for (Aspect aspect : aspects.getAspectsSortedByAmount()) {
                    if (aspect == null) continue;

                    // 计算每个要素的位置（依次向右排列）
                    int aspectX = x + index * 18; // 每个要素宽度18像素
                    int aspectY = y;

                    // 调用Thaumcraft的drawTag方法渲染要素
                    UtilsFX.drawTag(aspectX, aspectY, aspect, (float) aspects.getAmount(aspect), 0, gui.zLevel);

                    index++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 恢复GL状态
                GL11.glPopMatrix();
            }
        }
    }
}