package meowmel.quantumhue.biomeInfo;

import meowmel.quantumhue.QuantumHueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Objects;

public class BiomeInfoEventHandler {
    private String previousBiome;
    private int displayTime = 0;
    private int alpha = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!QuantumHueConfig.biome_info.FADE_OUT && alpha != 255) {
            alpha = 255;
        } else if (QuantumHueConfig.biome_info.FADE_OUT) {
            if (displayTime > 0) {
                displayTime--;
            } else if (alpha > 0) {
                alpha -= QuantumHueConfig.biome_info.FADE_SPEED;
                if (alpha < 0) alpha = 0;
            }
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!QuantumHueConfig.biome_info.ENABLED || Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        BlockPos pos = new BlockPos(mc.player.posX, mc.player.getEntityBoundingBox().minY, mc.player.posZ);
        if (!mc.world.isBlockLoaded(pos) || pos.getY() < 0 || pos.getY() >= 256) return;

        Biome currentBiome = mc.world.getBiome(pos);
        if (!Objects.equals(previousBiome, currentBiome.getBiomeName())) {
            previousBiome = currentBiome.getBiomeName();
            displayTime = QuantumHueConfig.biome_info.DISPLAY_TIME;
            alpha = 255;
        }

        if (alpha <= 0) return;
        renderBiomeInfo(event, currentBiome.getBiomeName(), alpha);
    }

    private void renderBiomeInfo(RenderGameOverlayEvent event, String biomeName, int alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        double scale = QuantumHueConfig.biome_info.SCALE;

        // 保存OpenGL状态
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.scale(scale, scale, scale);

        // 计算文本尺寸
        int textWidth = mc.fontRenderer.getStringWidth(biomeName);
        int textHeight = mc.fontRenderer.FONT_HEIGHT;

        // 计算缩放后的屏幕尺寸
        int scaledWidth = (int)(event.getResolution().getScaledWidth() / scale);
        int scaledHeight = (int)(event.getResolution().getScaledHeight() / scale);

        // 计算在宽度0.5、高度0.75处居中显示的位置
        // 文本渲染的原点是文本左上角，所以要减去一半的文本尺寸来居中
        int centerX = scaledWidth / 2;  // 宽度0.5处
        int centerY = (int)(scaledHeight * 0.25);  // 高度0.75处

        // 计算文本渲染位置（使文本中心对齐到指定位置）
        int textX = centerX - (textWidth / 2);
        int textY = centerY - (textHeight / 2);

        // 渲染文本
        int textColor = Configuration.getTextColor() | (alpha << 24);
        mc.fontRenderer.drawString(biomeName, textX, textY, textColor, QuantumHueConfig.biome_info.TEXT_SHADOW);

        // 恢复OpenGL状态
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}