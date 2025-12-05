package meowmel.quantumhue;

import com.meowmel.quantumhue.Tags;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

@IFMLLoadingPlugin.Name(Tags.MOD_ID)
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class EarlyMixin implements IEarlyMixinLoader, IFMLLoadingPlugin {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();

        configs.add("mixins.quantumhue.json");

        return configs;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return IEarlyMixinLoader.super.shouldMixinConfigQueue(mixinConfig);
    }
}