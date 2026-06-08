package meowmel.quantumhue.modernsplash;

import meowmel.quantumhue.QuantumHue;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MSMixinConfigPlugin implements IMixinConfigPlugin {

    private static final boolean SMOOTH_FONT_PRESENT;

    static {
        boolean found = false;
        try {
            Class.forName("bre.smoothfont.mod_SmoothFont");
            found = true;
        } catch (Throwable ignored) {}
        SMOOTH_FONT_PRESENT = found;
        QuantumHue.LOGGER.info("[ModernSplash] SmoothFont detected: {}", SMOOTH_FONT_PRESENT);
    }
    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String s, String s1) {
        if (s1.equals("meowmel.quantumhue.modernsplash.mixin.FontRendererHookMixin")) {
            return SMOOTH_FONT_PRESENT;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return Collections.emptyList();
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
