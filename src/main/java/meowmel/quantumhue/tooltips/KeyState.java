package meowmel.quantumhue.tooltips;

public class KeyState {
    static final long MIN_SWITCH_INTERVAL = 250; // 最小切换间隔(毫秒)
    boolean wasCtrlPressed = false;
    boolean wasCPressed = false;
    boolean wasZPressed = false;
    long lastSwitchTime = 0; // 上次切换时间(毫秒)
}
