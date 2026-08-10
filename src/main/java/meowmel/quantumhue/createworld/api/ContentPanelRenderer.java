package meowmel.quantumhue.createworld.api;

import com.meowmel.quantumhue.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 内容面板渲染器——顶部/底部分隔线与面板背景的瓦片平铺绘制。
 * <p>Draws the tiled header/footer separators and panel background.</p>
 * 资源包可覆盖这几个 PNG 换皮。
 */
public final class ContentPanelRenderer {

    public static final ResourceLocation HEADER_SEPARATOR =
            new ResourceLocation(Tags.MOD_ID, "textures/gui/header_separator.png");
    public static final ResourceLocation FOOTER_SEPARATOR =
            new ResourceLocation(Tags.MOD_ID, "textures/gui/footer_separator.png");
    public static final ResourceLocation PANEL_BACKGROUND =
            new ResourceLocation(Tags.MOD_ID, "textures/gui/panel_background.png");

    public static final int SEPARATOR_HEIGHT = 2;
    private static final int SEPARATOR_TILE_W = 32;
    private static final int SEPARATOR_TILE_H = 2;
    private static final int PANEL_TILE = 16;

    private ContentPanelRenderer() {
    }

    /** 绘制完整内容面板：背景 + 顶部/底部分隔线 */
    public static void drawContentPanel(int x, int top, int width, int bottom) {
        if (width <= 0) {
            return;
        }
        int bgBottom = bottom;
        int bgTop = top + 2;
        if (bgBottom > bgTop) {
            drawPanelBackground(x, bgTop, width, bgBottom - bgTop);
        }
        drawHeaderSeparator(x, top, width);
        drawFooterSeparator(x, bottom, width);
    }

    public static void drawHeaderSeparator(int x, int y, int width) {
        drawSeparator(x, y, width, HEADER_SEPARATOR);
    }

    public static void drawFooterSeparator(int x, int y, int width) {
        drawSeparator(x, y, width, FOOTER_SEPARATOR);
    }

    public static void drawSeparator(int x, int y, int width, ResourceLocation texture) {
        if (width <= 0 || texture == null) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTiledTexture(x, y, width, SEPARATOR_HEIGHT, SEPARATOR_TILE_W, SEPARATOR_TILE_H);
    }

    public static void drawPanelBackground(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(PANEL_BACKGROUND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTiledTexture(x, y, width, height, PANEL_TILE, PANEL_TILE);
    }

    /** 纹理平铺绘制（GL_QUADS + POSITION_TEX） */
    private static void drawTiledTexture(int x, int y, int width, int height, int tileWidth, int tileHeight) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        for (int tileX = 0; tileX < width; tileX += tileWidth) {
            for (int tileY = 0; tileY < height; tileY += tileHeight) {
                int tileW = Math.min(tileWidth, width - tileX);
                int tileH = Math.min(tileHeight, height - tileY);
                double u1 = 0.0D;
                double u2 = (double) tileW / (double) tileWidth;
                double v1 = 0.0D;
                double v2 = (double) tileH / (double) tileHeight;
                buffer.pos(x + tileX, y + tileY + tileH, 0.0D).tex(u1, v2).endVertex();
                buffer.pos(x + tileX + tileW, y + tileY + tileH, 0.0D).tex(u2, v2).endVertex();
                buffer.pos(x + tileX + tileW, y + tileY, 0.0D).tex(u2, v1).endVertex();
                buffer.pos(x + tileX, y + tileY, 0.0D).tex(u1, v1).endVertex();
            }
        }
        tessellator.draw();
    }
}
