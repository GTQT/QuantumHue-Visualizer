package meowmel.quantumhue.pause;

/**
 * One row of the pause menu: what is drawn, and what to run when it is chosen.
 *
 * <p>{@link #payload} is opaque to this package — the screen puts the {@code GuiButton} it came from
 * there and hands it back to vanilla when the entry is activated, which is how the pause menu keeps
 * the original actions without importing Minecraft into the layout code.
 */
public final class PauseEntry {

    /** Index into the screen's own button list; stable for one {@code initGui}. */
    public final int index;
    /** The vanilla button id, kept only for diagnostics and the numbering on the plate. */
    public final int vanillaId;
    public final String label;
    public final PauseAction action;
    public final boolean enabled;
    public final Object payload;

    public PauseEntry(int index, int vanillaId, String label, PauseAction action,
                      boolean enabled, Object payload) {
        this.index = index;
        this.vanillaId = vanillaId;
        this.label = label == null ? "" : label;
        this.action = action;
        this.enabled = enabled;
        this.payload = payload;
    }
}
