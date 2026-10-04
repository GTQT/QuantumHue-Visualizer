package meowmel.quantumhue.menu;

import meowmel.quantumhue.mixins.GuiMainMenuAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.util.Iterator;

/**
 * The title screen.
 *
 * <p>A {@link GuiMainMenu} subclass, so every vanilla behaviour worth keeping still works — the
 * button actions, the realms and demo handling, the {@code GuiMainMenu} contract — but
 * {@code drawScreen} is <b>not</b> delegated to vanilla.  There is consequently nothing to suppress,
 * cover, cancel or remap: no panorama, no {@code minecraft.png} title, no {@code edition.png}
 * ribbon, no gradient washes, and no mixin on {@code Gui} at all.
 *
 * <p>Three vanilla decorations are dropped outright rather than reproduced:
 * <ul>
 *   <li>the bottom-left Forge/MCP/version stack — build telemetry;</li>
 *   <li>the Mojang copyright, plus the invisible credits hitbox that went with it;</li>
 *   <li>the language picker (the flag comes out of {@code widgets.png} and does not belong beside
 *       the flat plates; language is still reachable from Options), and Forge's green mod-update
 *       badge, which is what drew that emerald onto the Mods button.</li>
 * </ul>
 *
 * <p>Swapping the screen in is {@code ModernSplashEvents}' job, via {@code GuiOpenEvent}.
 */
@SideOnly(Side.CLIENT)
public class GtqtMainMenu extends GuiMainMenu {

    /** The line vanilla measures to place its credits hitbox; kept only to disable that hot zone. */
    private static final String CREDITS_LINE = "Copyright Mojang AB. Do not distribute!";

    /** Vanilla's language picker. */
    private static final int LANGUAGE_BUTTON_ID = 5;

    /** Mirrors the condition vanilla uses to decide whether to show its OpenGL warning. */
    private boolean unsupportedGl;

    @Override
    public void initGui() {
        super.initGui();

        for (Iterator<GuiButton> it = this.buttonList.iterator(); it.hasNext(); ) {
            if (it.next().id == LANGUAGE_BUTTON_ID) {
                it.remove();
            }
        }

        for (GuiButton button : this.buttonList) {
            int[] rect = MenuLayout.buttonRect(button.id, this.width, this.height);
            button.x = rect[0];
            button.y = rect[1];
            button.width = rect[2];
            button.height = rect[3];
        }

        this.unsupportedGl = !GLContext.getCapabilities().OpenGL20 && !OpenGlHelper.areShadersSupported();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        GtqtMenuChrome.drawBackdrop(mc, this);

        for (GuiButton button : this.buttonList) {
            if (button.visible) {
                GtqtMenuChrome.drawButton(mc, button, mouseX, mouseY);
            }
        }
        for (GuiLabel label : this.labelList) {
            label.drawLabel(mc, mouseX, mouseY);
        }

        GtqtMenuChrome.drawFooter(mc, this, this.unsupportedGl);
        GtqtMenuChrome.drawTitleBlock(mc, this);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Vanilla turns the bottom-right corner strip into a "show me the credits" button.  With the
        // copyright line gone that would be an invisible trap, so the zone is swallowed here and
        // every other click still goes to the normal handling.
        int creditsWidth = this.fontRenderer.getStringWidth(CREDITS_LINE);
        if (mouseY >= this.height - 10 && mouseX >= this.width - creditsWidth - 2) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
