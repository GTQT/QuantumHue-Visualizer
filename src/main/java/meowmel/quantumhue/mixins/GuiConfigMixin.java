package meowmel.quantumhue.mixins;

import mcjty.theoneprobe.gui.GuiConfig;
import meowmel.quantumhue.QuantumHueConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

@Mixin(GuiConfig.class)
public abstract class GuiConfigMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onStaticInit(CallbackInfo ci) {
        try {
            Class<?> presetClass = Class.forName("mcjty.theoneprobe.gui.Preset");

            Constructor<?> constructor = presetClass.getDeclaredConstructor(
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    Pair[].class
            );
            constructor.setAccessible(true);

            Object jade = constructor.newInstance("Jade Style", 0xFF4b4b4b, 0x881f1f1f, 1, 1, new Pair[0]);
            Object custom = constructor.newInstance("Custom Style", QuantumHueConfig.top_custom.borderColor, QuantumHueConfig.top_custom.fillColor, QuantumHueConfig.top_custom.thickness, QuantumHueConfig.top_custom.offset, new Pair[0]);

            Field presetsField = GuiConfig.class.getDeclaredField("presets");
            presetsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Object> presets = (List<Object>) presetsField.get(null);
            presets.add(jade);
            presets.add(custom);

        } catch (Exception ignored) {}
    }
}