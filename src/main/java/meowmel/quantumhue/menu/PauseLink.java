package meowmel.quantumhue.menu;

import meowmel.quantumhue.pause.PauseAction;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.Desktop;
import java.net.URI;

/**
 * Links the pack wants reachable from the pause menu: its documentation and its QQ group.
 *
 * <p>These used to be three {@code GuiButton}s that GTQT Core injected into {@code GuiIngameMenu}
 * through {@code InitGuiEvent.Post}, each one cancelled into a browser launch.  This mod replaces
 * the pause screen outright, so those buttons were (a) invisible, and (b) — because this screen
 * also reflows on {@code InitGuiEvent.Post} — arriving as anonymous entries in the tile column with
 * no icon and no action.  The feature therefore lives here now, with the same URLs, as two proper
 * plates, and the injection in GTQT Core is gone.
 *
 * <p>Opening a browser never blocks the client: the launch happens on a daemon thread, and a failure
 * (no desktop, sandboxed JVM) is silent rather than fatal.
 */
@SideOnly(Side.CLIENT)
public enum PauseLink {

    DOCS("文档", PauseAction.DOCS,
            "https://docs.qq.com/aio/DRFhDRXZ5dnV0ZEtu?no_promotion=1&p=rYIzLHQ8QlYS7226dmup7b"),
    QQ("QQ群", PauseAction.QQ,
            "https://qm.qq.com/q/rsiHCYWo1O"),
    /** GTQT Core's third button was the repository link; it is kept so nothing is lost. */
    GITHUB("GitHub", PauseAction.QQ,
            "https://github.com/GTQT/GT-QuantumTransition");

    private final String label;
    private final PauseAction action;
    private final String url;

    PauseLink(String label, PauseAction action, String url) {
        this.label = label;
        this.action = action;
        this.url = url;
    }

    public String label() {
        return label;
    }

    public PauseAction action() {
        return action;
    }

    public String url() {
        return url;
    }

    /** Hands the URL to the system browser; returns false when this JVM cannot do that. */
    public boolean open() {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                PauseMenuChrome.warn("no browser integration available for " + url);
                return false;
            }
            Thread thread = new Thread(() -> {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Throwable ignored) {
                    // nothing useful to do: the player can copy the link from the config comment
                }
            }, "QuantumHue-Link");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Throwable failure) {
            PauseMenuChrome.warn("could not open " + url + ": " + failure);
            return false;
        }
    }
}
