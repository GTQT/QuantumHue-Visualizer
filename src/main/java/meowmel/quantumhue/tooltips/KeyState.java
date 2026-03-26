package meowmel.quantumhue.tooltips;

public class KeyState {

    public static final long MIN_SWITCH_INTERVAL = 150;

    public boolean wasCtrlPressed;
    public boolean wasCPressed;
    public boolean wasZPressed;
    public long lastSwitchTime;

    public KeyState() {
        reset();
    }

    public void reset() {
        wasCtrlPressed = false;
        wasCPressed = false;
        wasZPressed = false;
        lastSwitchTime = 0;
    }
}