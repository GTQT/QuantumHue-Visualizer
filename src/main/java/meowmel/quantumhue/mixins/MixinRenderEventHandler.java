package meowmel.quantumhue.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import thaumcraft.client.lib.events.RenderEventHandler;

@Mixin(RenderEventHandler.class)
public abstract class MixinRenderEventHandler {

    @ModifyConstant(
            method = "tooltipEvent(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V",
            constant = @Constant(doubleValue = 18.0),
            remap = false
    )
    private static double modifyTooltipEventDoubleConstant(double original) {
        return 27.0;
    }
}