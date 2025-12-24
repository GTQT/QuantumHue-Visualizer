package meowmel.quantumhue.tooltips;


public class KeyState {
    static final long MIN_SWITCH_INTERVAL = 200; // 200毫秒
    boolean wasCtrlPressed = false;
    boolean wasCPressed = false;
    boolean wasZPressed = false;
    long lastSwitchTime = 0;
}
