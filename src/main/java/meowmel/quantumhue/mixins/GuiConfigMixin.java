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
public class GuiConfigMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onStaticInit(CallbackInfo ci) {
        try {
            // 使用反射完全避免在代码中直接引用 Preset
            Class<?> presetClass = Class.forName("mcjty.theoneprobe.gui.Preset");

            // 获取正确的构造函数参数类型
            Constructor<?> constructor = presetClass.getDeclaredConstructor(
                    String.class,      // name
                    int.class,         // borderColor
                    int.class,         // fillColor
                    int.class,         // thickness
                    int.class,         // offset
                    Pair[].class       // stylePairs (注意这里是 Pair[].class，不是 Object[].class)
            );
            constructor.setAccessible(true);

            // 创建 Preset 实例
            Object jade = constructor.newInstance(
                    "Jade Style",     // 主题名称
                    0xFF4b4b4b,
                    0x881f1f1f,
                    1,
                    1,
                    new Pair[0]
            );

            Object custom = constructor.newInstance(
                    "Custom Style",     // 主题名称
                    QuantumHueConfig.top_custom.borderColor,
                    QuantumHueConfig.top_custom.fillColor,
                    QuantumHueConfig.top_custom.thickness,
                    QuantumHueConfig.top_custom.offset,
                    new Pair[0]
            );

            // 获取 presets 字段并添加自定义预设
            Field presetsField = GuiConfig.class.getDeclaredField("presets");
            presetsField.setAccessible(true);

            // 注意：这里需要处理泛型类型，但可以通过原始类型操作
            @SuppressWarnings("unchecked")
            List<Object> presets = (List<Object>) presetsField.get(null);
            presets.add(jade);
            presets.add(custom);

        } catch (Exception ignored) {

        }
    }
}