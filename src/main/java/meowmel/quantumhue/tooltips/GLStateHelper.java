package meowmel.quantumhue.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * tooltip 绘制期间的 GL 状态封装：深度与混合的开关、以及支持嵌套的裁剪栈。
 */
@SideOnly(Side.CLIENT)
public class GLStateHelper {

    /**
     * 嵌套裁剪栈：按栈恢复并对父级矩形取交集，内层关闭时不会把外层裁剪一并关掉。
     */
    private static final Deque<int[]> SCISSOR_STACK = new ArrayDeque<>();

    /** 进入 tooltip 绘制态：抬到 GUI 前景平面，关闭深度测试，开启标准 alpha 混合。 */
    public static void setupGLState() {
        // 先平移再改其它状态：即使后续某一步抛异常，restore 的 -500 也一定对称
        GlStateManager.translate(0.0F, 0.0F, 500.0F);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
    }

    /** 退出 tooltip 绘制态，与 {@link #setupGLState()} 严格对称。 */
    public static void restoreGLState() {
        GlStateManager.translate(0.0F, 0.0F, -500.0F);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }

    /**
     * 启用 Scissor Test，将渲染裁剪到指定 layout 的边界内。
     * 用于动画期间防止文本溢出外框。
     *
     * <p>坐标换算使用当前 ScaledResolution 的实际缩放比（displayWidth / scaledWidth），
     * 而不是 GUI scale 整数：窗口拉伸或高 DPI 下两者可能不一致，直接乘 GUI scale
     * 会让裁剪框随分辨率漂移。</p>
     */
    public static void enableScissorClip(TooltipLayout layout) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);

        int scaledWidth = Math.max(1, sr.getScaledWidth());
        int scaledHeight = Math.max(1, sr.getScaledHeight());
        float scaleX = mc.displayWidth / (float) scaledWidth;
        float scaleY = mc.displayHeight / (float) scaledHeight;

        int left = Math.round(layout.x * scaleX);
        int bottom = Math.round(mc.displayHeight - (layout.y + layout.height) * scaleY);
        int width = Math.max(0, Math.round(layout.width * scaleX));
        int height = Math.max(0, Math.round(layout.height * scaleY));

        int[] box = intersect(new int[]{left, bottom, width, height},
                new int[]{0, 0, mc.displayWidth, mc.displayHeight});
        int[] parent = SCISSOR_STACK.peek();
        if (parent != null) {
            box = intersect(box, parent);
        }

        SCISSOR_STACK.push(box);
        apply(box);
    }

    /**
     * 禁用 Scissor Test；如果还有父级裁剪，则恢复父级矩形而不是直接关闭。
     */
    public static void disableScissorClip() {
        if (!SCISSOR_STACK.isEmpty()) {
            SCISSOR_STACK.pop();
        }
        int[] parent = SCISSOR_STACK.peek();
        if (parent == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(parent);
        }
    }

    private static void apply(int[] box) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(box[0], box[1], box[2], box[3]);
    }

    private static int[] intersect(int[] a, int[] b) {
        int left = Math.max(a[0], b[0]);
        int bottom = Math.max(a[1], b[1]);
        int right = Math.min(a[0] + a[2], b[0] + b[2]);
        int top = Math.min(a[1] + a[3], b[1] + b[3]);
        return new int[]{left, bottom, Math.max(0, right - left), Math.max(0, top - bottom)};
    }
}
