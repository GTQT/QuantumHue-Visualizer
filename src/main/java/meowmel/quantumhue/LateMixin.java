package meowmel.quantumhue;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

public class LateMixin implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        if(isModLoaded("theoneprobe"))  configs.add("mixins.quantumhue_late.json");
        return configs;
    }
}