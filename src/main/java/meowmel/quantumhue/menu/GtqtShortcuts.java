package meowmel.quantumhue.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;

/**
 * GTQT Core's market and announcement screens, reached from the pause menu.
 *
 * <h3>Why reflection</h3>
 * These screens live in another mod.  Compiling against them would make that mod a hard dependency
 * of a purely cosmetic one, and a player without GTQT Core would crash on startup rather than simply
 * not seeing two cards.  So the classes are looked up by name once, the constructors are cached, and
 * every failure path ends in {@code null} — the caller then leaves the two cards out entirely.
 *
 * <h3>What each screen needs</h3>
 * {@code GuiShop(EntityPlayer, long money)}: the second argument is only the balance shown while
 * the first frame is laid out.  Its {@code initGui} asks the server for a page of listings, and the
 * reply carries the authoritative balance, so passing a stale (or zero) figure self-corrects.  A
 * best-effort read of FTB Money supplies a sensible starting value.
 *
 * <p>{@code GuiAnnouncement()} is no-argument and needs nothing beyond being displayed.
 */
@SideOnly(Side.CLIENT)
public final class GtqtShortcuts {

    private static final String MOD_ID = "gtqtcore";
    private static final String SHOP_CLASS = "meowmel.gtqtcore.client.gui.GuiShop";
    private static final String NEWS_CLASS = "meowmel.gtqtcore.client.gui.GuiAnnouncement";
    private static final String MONEY_CLASS = "com.feed_the_beast.mods.money.FTBMoney";

    private static boolean resolved;
    private static Constructor<?> shopConstructor;
    private static Constructor<?> newsConstructor;

    private GtqtShortcuts() {}

    /** True when the mod is present and both screens were found. */
    public static boolean available() {
        resolve();
        return shopConstructor != null || newsConstructor != null;
    }

    public static boolean hasShop() {
        resolve();
        return shopConstructor != null;
    }

    public static boolean hasNews() {
        resolve();
        return newsConstructor != null;
    }

    /** The market screen, or {@code null} when it cannot be built. */
    public static GuiScreen shop(Minecraft mc) {
        resolve();
        if (shopConstructor == null || mc == null || mc.player == null) {
            return null;
        }
        try {
            return (GuiScreen) shopConstructor.newInstance(mc.player, money(mc.player));
        } catch (Throwable failure) {
            PauseMenuChrome.warn("could not open the GTQT Core market: " + failure);
            shopConstructor = null;
            return null;
        }
    }

    /** The announcement screen, or {@code null} when it cannot be built. */
    public static GuiScreen announcement(Minecraft mc) {
        resolve();
        if (newsConstructor == null) {
            return null;
        }
        try {
            return (GuiScreen) newsConstructor.newInstance();
        } catch (Throwable failure) {
            PauseMenuChrome.warn("could not open the GTQT Core announcement: " + failure);
            newsConstructor = null;
            return null;
        }
    }

    // ---------------------------------------------------------------- internals

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        // No Loader.isModLoaded gate: the class lookup *is* the ground truth, and a mod id string
        // that does not resolve would silently disable the two screens even though the mod is
        // installed.  A missing class throws, which is exactly the signal we want.
        try {
            Class<?> shop = Class.forName(SHOP_CLASS);
            shopConstructor = shop.getConstructor(EntityPlayer.class, long.class);
        } catch (Throwable failure) {
            PauseMenuChrome.warn("GTQT Core is loaded but " + SHOP_CLASS + " was not usable: " + failure);
        }
        try {
            Class<?> news = Class.forName(NEWS_CLASS);
            newsConstructor = news.getConstructor();
        } catch (Throwable failure) {
            PauseMenuChrome.warn("GTQT Core is loaded but " + NEWS_CLASS + " was not usable: " + failure);
        }
    }

    /** FTB Money's balance, or zero when that mod is not installed. */
    private static long money(EntityPlayer player) {
        try {
            Class<?> money = Class.forName(MONEY_CLASS);
            Object value = money.getMethod("getMoney", EntityPlayer.class).invoke(null, player);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
