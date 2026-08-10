package meowmel.quantumhue.createworld.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 难度锁定应用器——UI 层设置的锁定标志在 {@code IntegratedServer.loadAllWorlds}
 * 完成后被消费并应用到世界数据。
 * <p>Pending/consume state machine for the difficulty lock flag.</p>
 */
public class DifficultyApplier {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:DifficultyApplier");

    private static boolean pendingDifficultyLocked = false;

    /** UI 层设置待应用的难度锁定标志 */
    public static void setDifficultyLocked(boolean locked) {
        pendingDifficultyLocked = locked;
        LOGGER.info("Pending difficulty lock set to: {}", locked);
    }

    /** 消费（获取并清空）待应用的锁定标志 */
    public static boolean consumeDifficultyLocked() {
        boolean locked = pendingDifficultyLocked;
        pendingDifficultyLocked = false;
        return locked;
    }

    public static boolean hasPendingDifficultyLock() {
        return pendingDifficultyLocked;
    }
}
