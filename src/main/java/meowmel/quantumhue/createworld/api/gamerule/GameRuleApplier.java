package meowmel.quantumhue.createworld.api.gamerule;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏规则应用器——在世界加载时应用创建界面保存的待应用规则。
 * <p>Applies pending game rules when the world loads; registers/unregisters
 * its event listener lazily (one-shot application).</p>
 */
public class GameRuleApplier {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:GameRuleApplier");

    private static Map<String, String> pendingGameRules = null;
    private static boolean registered = false;

    /**
     * 设置要在下一个创建的世界中应用的规则。
     * @param gameRules 规则映射（键：规则名，值：字符串值）
     */
    public static void setPendingGameRules(Map<String, String> gameRules) {
        if (gameRules == null) {
            pendingGameRules = null;
            return;
        }
        pendingGameRules = new HashMap<>(gameRules);
        // 只注册一次事件监听器 / register the listener only once
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new GameRuleApplier());
            registered = true;
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer)) {
            return; // 仅处理服务端世界 / server-side worlds only
        }
        if (pendingGameRules != null && !pendingGameRules.isEmpty()) {
            this.applyGameRules(event.getWorld());
            int appliedCount = pendingGameRules.size();
            pendingGameRules.clear();
            // 一次性应用后注销监听 / unregister after one-shot application
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
            LOGGER.info("Applied {} game rules while creating the world.", appliedCount);
        }
    }

    public static Map<String, String> getPendingGameRules() {
        return pendingGameRules;
    }

    private void applyGameRules(World world) {
        if (pendingGameRules == null || pendingGameRules.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : pendingGameRules.entrySet()) {
            GameRuleMonitorNSetter.setGamerule(world, entry.getKey(), entry.getValue());
        }
    }
}
