package meowmel.quantumhue.igi;

import meowmel.quantumhue.igi.info.ItemIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * HUD渲染处理器。
 * 负责每帧刷新文本内容并在游戏界面上渲染。
 */
@SideOnly(Side.CLIENT)
public class HudRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL_MS = 1000;
    private final RenderItem renderItem = mc.getRenderItem();

    /**
     * 客户端Tick事件：每秒刷新一次HUD组的文本内容以节约性能。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.world == null || mc.player == null) return;

        if (!IGI.isInitialized()) {
            IGI.markInitialized();
        }

        long now = Minecraft.getSystemTime();
        if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
            lastRefreshTime = now;
            for (HudGroup group : IGI.getGroups()) {
                group.refresh();
            }
        }
    }

    /**
     * 游戏界面渲染事件：仅在准星可见时（无 GUI 打开）绘制 HUD。
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (mc.world == null || mc.player == null) return;
        // 打开背包/箱子/聊天等 GUI 或按 F1 隐藏界面时不显示
        if (mc.currentScreen != null || mc.gameSettings.hideGUI) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        FontRenderer fontRenderer = mc.fontRenderer;

        for (HudGroup group : IGI.getGroups()) {
            float scale = group.fontSize > 0 ? group.fontSize / (float) fontRenderer.FONT_HEIGHT : 1.0f;
            int fontHeight = fontRenderer.FONT_HEIGHT;
            int lineSpacing = fontHeight + 1;

            GlStateManager.pushMatrix();

            if (scale != 1.0f) {
                GlStateManager.scale(scale, scale, 1.0f);
            }

            float invScale = 1.0f / scale;
            int scaledScreenWidth = (int) (screenWidth * invScale);
            int scaledScreenHeight = (int) (screenHeight * invScale);
            int scaledOffsetX = (int) (group.offsetX * invScale);
            int scaledOffsetY = (int) (group.offsetY * invScale);

            java.util.List<HudLine> lines = group.getLines();
            int totalHeight = lines.size() * lineSpacing;
            int startY = group.alignment.getY(scaledScreenHeight, totalHeight, scaledOffsetY);

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                HudLine line = lines.get(lineIdx);
                java.util.List<Object> segments = line.getSegments();
                if (segments.isEmpty()) continue;

                // 计算整行像素总宽度（ItemIcon = fontHeight 宽，TextColor 不计宽）
                int lineWidth = 0;
                for (Object seg : segments) {
                    if (seg instanceof ItemIcon) {
                        lineWidth += fontHeight;
                    } else if (seg instanceof TextColor) {
                        // 颜色码不占宽度
                    } else {
                        lineWidth += fontRenderer.getStringWidth(HudLine.resolveSegment(seg));
                    }
                }

                int x = group.alignment.getX(scaledScreenWidth, lineWidth, scaledOffsetX);
                int y = startY + lineIdx * lineSpacing;

                // 逐段渲染：TextColor 设置当前颜色，拼接到后续文本前
                String currentColor = "";
                for (Object seg : segments) {
                    if (seg instanceof TextColor) {
                        currentColor = ((TextColor) seg).getValue();
                    } else if (seg instanceof ItemIcon) {
                        ItemStack stack = ((ItemIcon) seg).getStack();
                        if (!stack.isEmpty()) {
                            renderItemIcon(stack, x, y, fontHeight);
                        }
                        x += fontHeight;
                    } else {
                        String text = HudLine.resolveSegment(seg);
                        if (!text.isEmpty()) {
                            fontRenderer.drawStringWithShadow(currentColor + text, x, y, 0xFFFFFF);
                        }
                        x += fontRenderer.getStringWidth(text);
                    }
                }
            }

            GlStateManager.popMatrix();
        }
    }

    /**
     * 在HUD上渲染一个小物品图标。
     */
    private void renderItemIcon(ItemStack stack, int x, int y, int size) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);

        // 将物品缩放到目标尺寸
        float itemScale = size / 16.0f;
        GlStateManager.scale(itemScale, itemScale, 1.0f);

        RenderHelper.enableGUIStandardItemLighting();
        renderItem.zLevel = -100;
        renderItem.renderItemAndEffectIntoGUI(stack, 0, 0);
        renderItem.zLevel = 0;
        RenderHelper.disableStandardItemLighting();

        GlStateManager.popMatrix();
    }
}
