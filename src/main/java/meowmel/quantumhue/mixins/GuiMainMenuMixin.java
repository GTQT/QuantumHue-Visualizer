package meowmel.quantumhue.mixins;

import meowmel.quantumhue.api.utils.ClientHelper;
import meowmel.quantumhue.mixininterface.IGuiMainMenuMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin implements IGuiMainMenuMixin {

    @Shadow
    private float panoramaTimer;

    @Shadow
    private static final ResourceLocation[] TITLE_PANORAMA_PATHS = new ResourceLocation[] {
        new ResourceLocation("textures/gui/title/background/panorama_0.png"),
        new ResourceLocation("textures/gui/title/background/panorama_1.png"),
        new ResourceLocation("textures/gui/title/background/panorama_2.png"),
        new ResourceLocation("textures/gui/title/background/panorama_3.png"),
        new ResourceLocation("textures/gui/title/background/panorama_4.png"),
        new ResourceLocation("textures/gui/title/background/panorama_5.png")
    };

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.panoramaTimer = ((GuiMainMenuAccessor) ClientHelper.MENU_INSTANCE).getPanoramaTimer();
        ClientHelper.MENU_INSTANCE = (GuiMainMenu) (Object) this;
    }

    @Unique
    @Override
    public void clearMyBackground$tickPanoramaTimer(float partialTicks) {
        this.panoramaTimer += partialTicks;
    }

    /**
     * @author MeowmelMuku
     * @reason Custom skybox rendering: direct full-resolution render, no blur, no intermediate texture
     */
    @Overwrite
    private void renderSkybox(int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth;
        int h = mc.displayHeight;

        GlStateManager.matrixMode(5889); // GL_PROJECTION
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, (float)w / (float)h, 0.05F, 10.0F);

        GlStateManager.matrixMode(5888); // GL_MODELVIEW
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(0.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        GlStateManager.rotate(MathHelper.sin(this.panoramaTimer / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-this.panoramaTimer * 0.1F, 0.0F, 1.0F, 0.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (int face = 0; face < 6; ++face) {
            GlStateManager.pushMatrix();
            switch (face) {
                case 1: GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F); break;
                case 2: GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F); break;
                case 3: GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F); break;
                case 4: GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F); break;
                case 5: GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F); break;
            }
            mc.getTextureManager().bindTexture(TITLE_PANORAMA_PATHS[face]);
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(-1.0D, -1.0D, 1.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
            buffer.pos(1.0D, -1.0D, 1.0D).tex(1.0D, 0.0D).color(255, 255, 255, 255).endVertex();
            buffer.pos(1.0D, 1.0D, 1.0D).tex(1.0D, 1.0D).color(255, 255, 255, 255).endVertex();
            buffer.pos(-1.0D, 1.0D, 1.0D).tex(0.0D, 1.0D).color(255, 255, 255, 255).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }

        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }
}