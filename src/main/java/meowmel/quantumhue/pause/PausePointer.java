package meowmel.quantumhue.pause;

/**
 * Press-and-release bookkeeping for one menu.
 *
 * <p>A click only counts if the pointer went down on the same entry it came up on and did not travel
 * further than the drag threshold in between.  That single rule is what makes the menu forgiving
 * with a real mouse and impossible to misfire when the player is dragging the world behind it.
 */
public final class PausePointer {

    private int captured = -1;
    private float startX;
    private float startY;
    private boolean dragging;
    private boolean down;

    public void begin(int index, float x, float y) {
        captured = index;
        startX = x;
        startY = y;
        dragging = false;
        down = true;
    }

    /** Marks the gesture as a drag once it has travelled past {@code threshold}; returns that flag. */
    public boolean move(float x, float y, float threshold) {
        if (down) {
            float dx = x - startX;
            float dy = y - startY;
            if (dx * dx + dy * dy > threshold * threshold) {
                dragging = true;
            }
        }
        return dragging;
    }

    /**
     * Ends the gesture.
     *
     * @param hovered the entry under the pointer, or {@code -1}
     * @return the entry that was actually chosen, or {@code -1}
     */
    public int release(int hovered) {
        int chosen = down && !dragging && captured >= 0 && captured == hovered ? captured : -1;
        cancel();
        return chosen;
    }

    public void cancel() {
        down = false;
        dragging = false;
        captured = -1;
    }

    public boolean pressed(int index) {
        return down && !dragging && index >= 0 && index == captured;
    }

    public boolean down() {
        return down;
    }
}
