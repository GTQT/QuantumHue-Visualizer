package meowmel.quantumhue.createworld.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.world.storage.ISaveFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 修改原版选择世界界面——当检测到没有存档时直接跳转到创建世界界面。
 * <p>Redirects straight to the create-world screen when no saves are detected.</p>
 */
@Mixin(GuiWorldSelection.class)
public abstract class MixinGuiSelectWorld extends GuiScreen {

    @Shadow
    protected GuiScreen prevScreen;

    @Unique
    private static final Logger modernWorldCreatingUI$logger = LogManager.getLogger("QuantumHue:MixinGuiSelectWorld");

    @Inject(method = "initGui", at = @At("HEAD"), cancellable = true)
    private void onInitGuiHead(CallbackInfo ci) {
        modernWorldCreatingUI$logger.info("Initializing GuiSelectWorld");
        if (this.modernWorldCreatingUI$hasNoSaves()) {
            modernWorldCreatingUI$logger.info("No saves detected, redirecting to Create World screen");
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new GuiCreateWorld(this.prevScreen));
            ci.cancel();
        }
    }

    @Unique
    private boolean modernWorldCreatingUI$hasNoSaves() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ISaveFormat saveFormat = mc.getSaveLoader();
            List<?> saveList = saveFormat.getSaveList();
            if (saveList == null) {
                modernWorldCreatingUI$logger.warn("Could not get save list");
                return true;
            }
            modernWorldCreatingUI$logger.info("Found {} save entries", saveList.size());
            return saveList.isEmpty();
        } catch (Exception e) {
            modernWorldCreatingUI$logger.error("Error checking for saves: ", e);
            return true;
        }
    }
}
