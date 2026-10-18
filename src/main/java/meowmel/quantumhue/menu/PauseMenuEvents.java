package meowmel.quantumhue.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Puts {@link GtqtInGameMenu} where vanilla would have put its own pause menu.
 *
 * <h3>Why {@code getClass() == GuiIngameMenu.class}</h3>
 * Exact-class, not {@code instanceof}.  Several mods ship their own pause screen subclass; those
 * are left completely alone, because they were somebody's deliberate choice and our layout knows
 * nothing about their buttons.  This is the same guard the reference project uses on
 * {@code PauseScreen}.
 *
 * <p>The screen is only swapped in when a world is loaded.  {@code GuiIngameMenu} can in principle
 * be opened from the title screen, and the pause menu's whole composition assumes a player and a
 * world behind it.
 */
@SideOnly(Side.CLIENT)
public final class PauseMenuEvents {

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!PauseMenuChrome.enabled()) {
            return;
        }
        GuiScreen screen = event.getGui();
        if (screen == null || screen.getClass() != GuiIngameMenu.class) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }
        event.setGui(new GtqtInGameMenu());
    }

    /**
     * Re-flows once every other mod has finished adding its own buttons.
     *
     * <p>Forge posts this <em>after</em> {@code initGui()} returns, so a menu that positioned its
     * own buttons inside {@code initGui} would silently leave anything added here at vanilla
     * coordinates — the main menu has that gap today, and the pause menu cannot afford it because
     * that is exactly where journal, quest and settings mods hang their buttons.
     */
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GtqtInGameMenu) {
            ((GtqtInGameMenu) event.getGui()).reflow();
        }
    }
}
