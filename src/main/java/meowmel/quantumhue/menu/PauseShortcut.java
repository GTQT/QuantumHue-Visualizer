package meowmel.quantumhue.menu;

import meowmel.quantumhue.pause.PauseAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSnooper;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Vanilla screens the pause menu offers directly.
 *
 * <p>All eight used to be two clicks deep behind "Options…", and the pause menu had a column of
 * tiles with nothing in it.  This is the join: each constant knows the label to draw and how to
 * build its screen, and every one of them takes the menu as its parent, so "Done" comes straight
 * back here rather than to the world.
 *
 * <p>They are ordinary {@code GuiScreen}s, which means the pack's shared background and blur apply
 * to them for free — exactly as they do when reached through Options.
 */
@SideOnly(Side.CLIENT)
public enum PauseShortcut {

    VIDEO(PauseAction.DISPLAY, "options.video"),
    CONTROLS(PauseAction.CONTROLS, "options.controls"),
    SOUND(PauseAction.SOUND, "options.sounds"),
    CHAT(PauseAction.CHAT, "options.chat.title"),
    LANGUAGE(PauseAction.LANGUAGE, "options.language"),
    RESOURCE_PACKS(PauseAction.PACKS, "options.resourcepack"),
    SKIN(PauseAction.SKIN, "options.skinCustomisation"),
    SNOOPER(PauseAction.DEBUG, "options.snooper.view"),

    // GTQT Core, when it is installed.  Labels are spelled out rather than taken from I18n: these
    // are that pack's own destinations and have no vanilla translation key.
    SHOP(PauseAction.SHOP, null),
    NEWS(PauseAction.NEWS, null),
    /** Not a screen: shuts the client down.  Handled before {@link #create} is consulted. */
    QUIT(PauseAction.QUIT_GAME, null);

    private final PauseAction action;
    private final String key;

    PauseShortcut(PauseAction action, String key) {
        this.action = action;
        this.key = key;
    }

    public PauseAction action() {
        return action;
    }

    /** The label: a vanilla translation where one exists, else the pack's own name. */
    public String label() {
        if (key == null) {
            switch (this) {
                case SHOP: return "商店";
                case NEWS: return "公告";
                default: return "关闭MC";
            }
        }
        return I18n.format(key);
    }

    /** Builds the screen, with {@code parent} as the screen it returns to. */
    public GuiScreen create(Minecraft mc, GuiScreen parent) {
        switch (this) {
            case VIDEO:
                return new GuiVideoSettings(parent, mc.gameSettings);
            case CONTROLS:
                return new GuiControls(parent, mc.gameSettings);
            case SOUND:
                return new GuiScreenOptionsSounds(parent, mc.gameSettings);
            case CHAT:
                return new ScreenChatOptions(parent, mc.gameSettings);
            case LANGUAGE:
                return new GuiLanguage(parent, mc.gameSettings, mc.getLanguageManager());
            case RESOURCE_PACKS:
                return new GuiScreenResourcePacks(parent);
            case SKIN:
                return new GuiCustomizeSkin(parent);
            case QUIT:
                return null;
            case SHOP:
                return GtqtShortcuts.shop(mc);
            case NEWS:
                return GtqtShortcuts.announcement(mc);
            case SNOOPER:
            default:
                return new GuiSnooper(parent, mc.gameSettings);
        }
    }
}
