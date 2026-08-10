package meowmel.quantumhue.createworld.mixin;

import meowmel.quantumhue.createworld.api.DifficultyApplier;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在世界加载完成后应用创建界面设置的难度锁定。
 * <p>Applies the pending difficulty lock after world loading.</p>
 * loadAllWorlds 完成后 worldServers 已完全初始化，可直接访问。
 */
@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServer {

    private static final Logger createWorldUI$logger = LogManager.getLogger("QuantumHue:MixinIntegratedServer");

    @Inject(method = "loadAllWorlds", at = @At("TAIL"))
    private void createWorldUI$onLoadAllWorlds(String saveName, String worldNameIn, long seed,
                                               WorldType type, String generatorOptions, CallbackInfo ci) {
        if (!DifficultyApplier.hasPendingDifficultyLock()) {
            return;
        }
        boolean difficultyLocked = DifficultyApplier.consumeDifficultyLocked();
        if (!difficultyLocked) {
            return;
        }
        createWorldUI$logger.info("Applying pending difficulty lock to world");
        IntegratedServer server = (IntegratedServer) (Object) this;
        if (server.worlds != null) {
            for (WorldServer worldServer : server.worlds) {
                if (worldServer == null) {
                    continue;
                }
                worldServer.getWorldInfo().setDifficultyLocked(true);
            }
        }
    }
}
