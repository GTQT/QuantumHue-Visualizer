package meowmel.quantumhue;

import mcjty.theoneprobe.config.Config;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

public class LateMixin implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        if(isModLoaded("theoneprobe"))  configs.add("mixins.quantumhue_theoneprobe.json");
        if(isModLoaded("thaumcraft"))  configs.add("mixins.quantumhue_thaumcraft.json");
        return configs;
    }
}