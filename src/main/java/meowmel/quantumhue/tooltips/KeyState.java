package meowmel.quantumhue.tooltips;

public class KeyState {
    public static final long MIN_SWITCH_INTERVAL = 200;

    public boolean wasCtrlPressed = false;
    public boolean wasCPressed = false;
    public boolean wasZPressed = false;
    public long lastSwitchTime = 0;
}
