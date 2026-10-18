package meowmel.quantumhue.menu;

import meowmel.quantumhue.pause.PauseAction;
import meowmel.quantumhue.pause.PauseEntry;
import meowmel.quantumhue.pause.PauseLayout;
import meowmel.quantumhue.pause.PausePointer;
import meowmel.quantumhue.pause.PauseScene;
import meowmel.quantumhue.pause.PauseScroll;
import meowmel.quantumhue.pause.PauseTimeline;
import meowmel.quantumhue.pause.PlayerCardData;
import meowmel.quantumhue.pause.Projector;
import meowmel.quantumhue.pause.Spring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The pause menu: vanilla's {@code GuiIngameMenu} with the whole frame drawn by {@link PauseScene}.
 *
 * <h3>Why a subclass and not a replacement</h3>
 * Everything about the pause menu that matters is a <em>chain</em>: the options, statistics,
 * advancements and LAN screens all take {@code this} as their parent and come back to it, "save and
 * quit" runs vanilla's {@code sendQuittingDisconnectingPacket} / {@code loadWorld(null)} sequence,
 * and other mods test {@code instanceof GuiIngameMenu} to decide whether the game is paused.  A
 * subclass keeps every one of those links intact; a fresh {@code GuiScreen} would break all of them
 * and, because {@code SimpleBlurHandler} whitelists the class, would also lose the blur.
 *
 * <p>{@code initGui()} is still vanilla's — the buttons are built exactly as before (including
 * Forge's "Mod Options") and simply read back out of {@code buttonList}; only their drawing and
 * their hit areas are ours.  That is also why the entries are rebuilt after
 * {@code InitGuiEvent.Post}: other mods add their own buttons to the pause menu there.
 *
 * <h3>What stays vanilla</h3>
 * <ul>
 *   <li>the actions themselves — {@link #actionPerformed} is never bypassed;</li>
 *   <li>{@code doesGuiPauseGame()}, so single-player still pauses;</li>
 *   <li>ESC, which runs the close animation and only then leaves the screen;</li>
 *   <li>the Forge {@code ActionPerformedEvent} pair, posted the way vanilla posts them.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class GtqtInGameMenu extends GuiIngameMenu {

    /** The button array's own order can differ from the order entries are drawn in. */
    private static final float DRAG_HYSTERESIS = 7f;
    /**
     * Degrees of yaw per pixel of drag.  High on purpose: the sphere is a toy, not a slider, so it
     * should outrun the pointer rather than trail it.  A quick flick across half the screen is more
     * than a full turn.
     */
    private static final float DRAG_DEGREES_PER_PIXEL = 1.2f;
    /** Speed ceiling and the fraction of the pointer's speed that survives the release. */
    private static final float SPIN_SPEED_CAP = 900f;
    private static final float SPIN_CARRY = 0.65f;
    /** Coast decay, in reciprocal seconds: 0.9 keeps the spin alive for four or five seconds. */
    private static final float SPIN_DECAY = 0.9f;

    private final PausePointer pointer = new PausePointer();
    private final Spring yaw = new Spring();
    private final Spring pitch = new Spring();

    private PauseLayout layout;
    private PauseScene.State state;
    private PauseTimeline timeline;
    private Spring[] hoverSprings = new Spring[0];
    private Spring[] pressSprings = new Spring[0];
    private PlayerCardData player;
    private final PauseScroll scroll = new PauseScroll();
    /** Frame-rate history for the console's sparkline; oldest first. */
    private final float[] spark = new float[48];
    private int sparkCount;

    /** Slot index waiting to fire once the close animation ends, or {@link PauseScene#CLOSE}. */
    private int pending = PauseScene.NONE;
    private GuiButton pendingButton;
    private int focusedSlot = PauseScene.NONE;

    /** Set while a press started on the sphere; such a drag spins it instead of moving anything. */
    private boolean sphereDragging;
    private float sphereLastX;
    private float sphereLastY;
    private float sphereSpin;
    private float sphereTilt;
    /** Degrees per second carried over after the drag ends, decaying back to the idle spin. */
    private float sphereSpinVelocity;
    /**
     * Smoothed pointer speed, in degrees per second.
     *
     * <p>The carry used to be the last frame's delta divided by that frame's dt, which is zero the
     * moment the pointer pauses — and a flick almost always ends with one quiet frame before the
     * button comes up, so there was never any inertia to feel.  An exponential average over the last
     * few frames still remembers the flick when the release arrives.
     */
    private float sphereDragSpeed;
    /** One-shot diagnostic so a future "the sphere ignores me" report can be answered from the log. */
    private boolean sphereGrabReported;

    /** Set while a press started inside the scrolling tile band; such a press scrolls, never clicks. */
    private boolean scrollDragging;
    private float lastDragY;
    private long lastDragAt;
    private int lastExtras;
    private PauseShortcut pendingShortcut;

    private long lastFrame;
    private long lastSnapshot;

    private boolean fallback;

    // ================================================================ lifecycle

    @Override
    public void initGui() {
        // super first: it builds the vanilla buttons, including the ones Forge adds here
        super.initGui();

        fallback = !PauseMenuChrome.enabled() || this.mc.world == null;
        if (fallback) {
            return;
        }
        try {
            rebuild(true);
        } catch (Throwable failure) {
            PauseMenuChrome.fail("init", failure);
            fallback = true;
        }
    }

    /** Called from {@code InitGuiEvent.Post}, once every other mod has had its say about the list. */
    public void reflow() {
        if (fallback || this.mc == null || this.mc.world == null) {
            return;
        }
        if (sameButtons()) {
            return;
        }
        try {
            rebuild(false);
        } catch (Throwable failure) {
            PauseMenuChrome.fail("reflow", failure);
            fallback = true;
        }
    }

    /**
     * True when the button list is exactly what the current layout was built from.
     *
     * <p>{@code InitGuiEvent.Post} fires on every open, including the one that just ran
     * {@code initGui}, so without this the whole board would be rebuilt twice per open and the
     * entrance would lose its first frame.
     */
    private boolean sameButtons() {
        if (this.layout == null || this.state == null) {
            return false;
        }
        if (this.layout.slots.size() != this.buttonList.size() + this.lastExtras) {
            return false;
        }
        // rebuild() walks buttonList in order and gives every button exactly one slot, so the two
        // lists are parallel by construction (the debug placeholders trail behind them).
        for (int i = 0; i < this.buttonList.size(); i++) {
            if (this.layout.slots.get(i).entry.payload != this.buttonList.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param restartAnimation {@code true} on a real open or a return from a sub-screen, {@code false}
     *                         when only the button list changed and the menu should not replay itself
     */
    private void rebuild(boolean restartAnimation) {
        long now = System.nanoTime();

        List<PauseEntry> entries = new ArrayList<PauseEntry>();
        for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton button = this.buttonList.get(i);
            if (button == null) {
                continue;
            }
            PauseAction action = classify(button.id);
            // "Save and Quit to Title" is too long for half an arc, and the half it lands in is now
            // explicitly the "leave this world" one, so it gets a short label of its own.
            String text = action == PauseAction.DISCONNECT ? "退出游戏" : button.displayString;
            entries.add(new PauseEntry(entries.size(), button.id, text, action, button.enabled,
                    button));
        }
        int placeholders = PauseMenuChrome.placeholderTiles();
        for (int i = 0; i < placeholders; i++) {
            entries.add(new PauseEntry(entries.size(), -1000 - i,
                    String.format(java.util.Locale.ROOT, "PLACEHOLDER %02d", i + 1),
                    PauseAction.OTHER, true, null));
        }
        int extras = placeholders;
        // The market and the announcement are always drawn: they anchor the two upper corners, and
        // a board with two holes in it would be worse than two plates that cannot be clicked.  When
        // GTQT Core is missing they are simply disabled, which the scene already renders as a dimmed
        // plate that never takes a hover.
        // The three links come before the vanilla shortcuts so they land inside the four rows the
        // band can show without scrolling: they are the pack's own destinations and are far more
        // likely to be used than "Skin Customisation".
        // Unconditional.  These used to be gated on Loader.isModLoaded("gtqtcore"), which silently
        // dropped all three the moment that string did not resolve — and a missing tile is
        // indistinguishable from a tile that was never written.  The destinations are this pack's
        // own; there is nothing to probe for.
        for (PauseLink link : PauseLink.values()) {
            entries.add(new PauseEntry(entries.size(), -4000 - link.ordinal(),
                    link.label(), link.action(), true, link));
            extras++;
        }
        for (PauseShortcut shortcut : PauseShortcut.values()) {
            boolean available = shortcut != PauseShortcut.SHOP || GtqtShortcuts.hasShop();
            if (shortcut == PauseShortcut.NEWS) {
                available = GtqtShortcuts.hasNews();
            }
            boolean wanted = shortcut == PauseShortcut.SHOP || shortcut == PauseShortcut.NEWS
                    || (shortcut != PauseShortcut.QUIT && PauseMenuChrome.vanillaShortcuts());
            if (!wanted) {
                continue;
            }
            entries.add(new PauseEntry(entries.size(), -2000 - shortcut.ordinal(),
                    shortcut.label(), shortcut.action(), available, shortcut));
            extras++;
        }
        // The power plate is the mod's own: closing the game has no vanilla button, and the slot
        // that used to hold "save and quit" is now the right half of the arc band.
        entries.add(new PauseEntry(entries.size(), -3000, "关闭MC", PauseAction.QUIT_GAME, true,
                PauseShortcut.QUIT));
        extras++;

        this.lastExtras = extras;

        this.layout = PauseLayout.create(this.width, this.height,
                this.mc.displayWidth, this.mc.displayHeight, entries);
        reportProblems(entries);

        if (this.state == null) {
            this.state = new PauseScene.State();
        }
        this.state.layout = this.layout;
        this.state.width = this.width;
        this.state.height = this.height;
        this.state.left = 0f;
        this.state.top = 0f;
        this.state.projector = new Projector(this.layout, 0f, 0f, 0f, 0f);
        this.state.flat = null;
        this.state.spark = this.spark;
        this.state.sparkCount = this.sparkCount;
        this.scroll.setLimit(this.layout.maxScroll);
        this.state.scroll = this.scroll.position();
        this.scrollDragging = false;

        int slots = this.layout.slots.size();
        if (this.hoverSprings.length != slots) {
            this.hoverSprings = new Spring[slots];
            this.pressSprings = new Spring[slots];
            for (int i = 0; i < slots; i++) {
                this.hoverSprings[i] = new Spring();
                this.pressSprings[i] = new Spring();
            }
            this.state.hover = new float[slots];
            this.state.press = new float[slots];
        }

        this.player = PauseMenuChrome.snapshot(this.mc);
        this.state.player = this.player;
        this.lastSnapshot = now;

        if (restartAnimation || this.timeline == null) {
            this.timeline = new PauseTimeline(now, !PauseMenuChrome.animation());
            this.yaw.reset(0f);
            this.pitch.reset(0f);
            this.pointer.cancel();
            this.focusedSlot = PauseScene.NONE;
            this.pending = PauseScene.NONE;
            this.pendingButton = null;
            for (Spring spring : this.hoverSprings) {
                spring.reset(0f);
            }
            for (Spring spring : this.pressSprings) {
                spring.reset(0f);
            }
        }
        this.lastFrame = now;
    }

    /**
     * Maps a vanilla pause-menu button id onto the role it plays on the board.
     *
     * <p>These are the ids {@code GuiIngameMenu.initGui} creates plus Forge's; everything else — a
     * journal mod's button, a to-do list, whatever — becomes a tile with its own label.
     */
    private static PauseAction classify(int id) {
        switch (id) {
            case 4: return PauseAction.RESUME;
            case 0: return PauseAction.OPTIONS;
            case 12: return PauseAction.MOD_OPTIONS;
            case 5: return PauseAction.ADVANCEMENTS;
            case 6: return PauseAction.STATISTICS;
            case 7: return PauseAction.WORLD_OPTIONS;
            case 1: return PauseAction.DISCONNECT;
            default: return PauseAction.OTHER;
        }
    }

    /** Problems already logged, so a rebuild-per-open does not repeat the same line every time. */
    private static final java.util.Set<String> REPORTED = new java.util.HashSet<String>();

    /**
     * Runs the layout's self-check and logs anything it found.
     *
     * <p>The pause menu's button list is not ours: vanilla builds seven, Forge adds one, and every
     * other mod may add more.  A role that ends up with no card, or two entries on the same plate,
     * is not a crash — it is a button that silently disappears, which is far worse.  Each distinct
     * problem is logged once per session.
     */
    private void reportProblems(List<PauseEntry> entries) {
        List<String> problems;
        try {
            problems = this.layout.validate(entries);
        } catch (Throwable failure) {
            PauseMenuChrome.warn("layout self-check threw: " + failure);
            return;
        }
        for (String problem : problems) {
            if (REPORTED.add(problem)) {
                PauseMenuChrome.warn("layout: " + problem);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        this.pointer.cancel();
        this.pending = PauseScene.NONE;
        this.pendingButton = null;
        super.onGuiClosed();
    }

    // ================================================================ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (fallback || this.state == null || this.timeline == null) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        try {
            long now = System.nanoTime();
            float dt = this.lastFrame == 0L ? 0f : (now - this.lastFrame) / 1_000_000_000f;
            this.lastFrame = now;

            animate(mouseX, mouseY, dt, now);
            PauseScene.draw(GlStatePainter.get(this.mc), this.state);
            PauseMenuChrome.drawFace(this.mc, this.state);
            GlStateManager.color(1f, 1f, 1f, 1f);
        } catch (Throwable failure) {
            PauseMenuChrome.fail("draw", failure);
            fallback = true;
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    /**
     * One frame of motion: the pointer springs, the parallax, and the hover/press values the scene
     * reads.  Everything is driven by {@code System.nanoTime()} rather than {@code partialTicks},
     * because the game is paused here and vanilla freezes the partial tick while it is — an animation
     * on that clock would stop dead.
     */
    private void animate(int mouseX, int mouseY, float dt, long now) {
        this.state.millis = now / 1_000_000L;
        this.state.frame = this.timeline.sample(now);

        boolean idle = !this.pointer.down() && !this.layout.compact && PauseMenuChrome.parallax()
                && !this.timeline.closing();
        float targetYaw = idle ? -(mouseX / (float) Math.max(this.width, 1) * 2f - 1f) * 4f : 0f;
        float targetPitch = idle ? (mouseY / (float) Math.max(this.height, 1) * 2f - 1f) * 2.5f : 0f;
        this.yaw.update(targetYaw, dt, 12f);
        this.pitch.update(targetPitch, dt, 12f);
        this.state.projector.orient(this.yaw.value(), this.pitch.value());

        this.scroll.update(dt, this.scrollDragging && this.pointer.down());
        this.state.scroll = this.scroll.position();

        if (!this.sphereDragging && Math.abs(this.sphereSpinVelocity) > 1f) {
            // carry: spin down exponentially, so the sphere coasts to the idle speed
            this.sphereSpin += this.sphereSpinVelocity * dt;
            this.sphereSpinVelocity *= (float) Math.exp(-dt * SPIN_DECAY);
            this.state.sphereSpin = this.sphereSpin;
        }

        int hovered = this.timeline.closing() ? PauseScene.NONE
                : PauseScene.hitTest(this.state, mouseX, mouseY);

        for (int i = 0; i < this.hoverSprings.length; i++) {
            boolean enabled = i < this.layout.slots.size()
                    && this.layout.slots.get(i).entry.enabled;
            this.hoverSprings[i].update(enabled && hovered == i ? 1f : 0f, dt, 22f);
            this.pressSprings[i].update(this.pointer.pressed(i) ? 1f : 0f, dt, 35f);
            this.state.hover[i] = this.hoverSprings[i].value();
            this.state.press[i] = this.pressSprings[i].value();
        }
        this.state.focused = this.focusedSlot;
    }

    // ================================================================ input

    /**
     * The wheel and the drag, both read straight off the input queue.
     *
     * <p>1.12.2 has no scroll or drag callbacks on {@code GuiScreen} — {@code handleMouseInput} is
     * the only hook — so the wheel is normalised to one unit per notch and the drag is polled from
     * the pointer position, converted from display pixels into GUI units the way vanilla does it.
     */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (fallback || this.state == null || this.timeline == null || this.timeline.closing()) {
            return;
        }
        if (this.layout.maxScroll > 0.5f) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                this.scroll.wheel(wheel / 120.0);
                this.pointer.cancel();
            }
        }
        if (this.sphereDragging) {
            // No Mouse.isButtonDown() guard: the release callback is authoritative, and polling the
            // button state here risked cancelling a legitimate drag on its very first frame — which
            // would look exactly like "the sphere ignores the pointer".
            ScaledResolution resolution = new ScaledResolution(this.mc);
            float guiX = Mouse.getX() * (float) resolution.getScaledWidth()
                    / Math.max(1, this.mc.displayWidth);
            float guiY = resolution.getScaledHeight()
                    - Mouse.getY() * (float) resolution.getScaledHeight()
                    / Math.max(1, this.mc.displayHeight) - 1f;
            long now = System.nanoTime();
            float dt = Math.max(0.008f, (now - this.lastDragAt) / 1_000_000_000f);
            float dx = guiX - this.sphereLastX;
            float dy = guiY - this.sphereLastY;
            this.sphereLastX = guiX;
            this.sphereLastY = guiY;
            this.lastDragAt = now;
            spinSphere(dx, dy, dt);
            return;
        }
        if (!this.scrollDragging || !this.pointer.down() || !Mouse.isButtonDown(0)) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(this.mc);
        float guiX = Mouse.getX() * (float) resolution.getScaledWidth()
                / Math.max(1, this.mc.displayWidth);
        float guiY = resolution.getScaledHeight()
                - Mouse.getY() * (float) resolution.getScaledHeight()
                / Math.max(1, this.mc.displayHeight) - 1f;
        long now = System.nanoTime();
        float dt = Math.max(0.008f, (now - this.lastDragAt) / 1_000_000_000f);
        this.scroll.drag((guiY - this.lastDragY) / Math.max(0.1f, this.layout.scale), dt);
        this.lastDragY = guiY;
        this.lastDragAt = now;
        this.pointer.move(guiX, guiY, dragThreshold());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (fallback || this.state == null || this.timeline == null) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (mouseButton != 0 || this.timeline.closing()) {
            return;
        }
        int hit = PauseScene.hitTest(this.state, mouseX, mouseY);
        // Sphere first, and before the "nothing was hit" return: the sphere is not a slot, so a press
        // on it *is* a press on nothing as far as the layout is concerned, and testing it after that
        // return meant the branch was unreachable — which is exactly why it never grabbed.
        if (hit == PauseScene.NONE && PauseScene.insideSphere(this.state, mouseX, mouseY)) {
            // The sphere is not an entry: it is a thing you can grab.  No pointer, no focus, no
            // collapse — just a drag that feeds the scene two angles.
            this.sphereDragging = true;
            this.sphereLastX = mouseX;
            this.sphereLastY = mouseY;
            this.sphereSpinVelocity = 0f;
            this.sphereDragSpeed = 0f;
            this.lastDragAt = System.nanoTime();
            if (!this.sphereGrabReported) {
                this.sphereGrabReported = true;
                PauseMenuChrome.warn("sphere: grabbed at " + mouseX + "," + mouseY);
            }
            return;
        }
        if (hit == PauseScene.NONE) {
            return;
        }
        this.pointer.begin(hit, mouseX, mouseY);
        this.focusedSlot = hit;
        this.scrollDragging = this.layout.maxScroll > 0.5f
                && PauseScene.insideBand(this.state, mouseX, mouseY);
        this.lastDragY = mouseY;
        this.lastDragAt = System.nanoTime();
        if (this.scrollDragging) {
            this.scroll.stop();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int released) {
        if (fallback || this.state == null || this.timeline == null) {
            super.mouseReleased(mouseX, mouseY, released);
            return;
        }
        if (this.sphereDragging) {
            this.sphereDragging = false;
            // The release itself does not touch the velocity: whatever the smoothed pointer speed had
            // reached is what the coast starts from, so a flick keeps turning and a slow drag does not.
            return;
        }
        if (!this.pointer.down()) {
            return;
        }
        this.pointer.move(mouseX, mouseY, dragThreshold());
        if (this.scrollDragging) {
            this.scrollDragging = false;
            this.scroll.stop();
            // A press inside the band is only a *drag* if it travelled: falling through lets
            // pointer.release() decide, and it returns "nothing chosen" once the gesture moved.
        }
        int hovered = this.timeline.closing() ? PauseScene.NONE
                : PauseScene.hitTest(this.state, mouseX, mouseY);
        int chosen = this.pointer.release(hovered);
        if (chosen == PauseScene.NONE) {
            return;
        }
        if (chosen == PauseScene.CLOSE) {
            close();
            return;
        }
        activate(chosen);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (fallback || this.state == null || this.timeline == null) {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (this.timeline.closing()) {
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            cycleFocus(isShiftKeyDown() ? -1 : 1);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.focusedSlot != PauseScene.NONE) {
                activate(this.focusedSlot);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** The same click a vanilla button makes, for the shortcuts that are not buttons. */
    private void playClick() {
        if (this.mc.getSoundHandler() != null) {
            this.mc.getSoundHandler().playSound(
                    PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * Turns a mouse delta into sphere rotation.
     *
     * <p>Horizontal drags yaw it, vertical drags tilt it, and the horizontal speed is kept as
     * angular velocity so the sphere keeps turning for a moment after the button is released.  The
     * idle spin is added in the scene, so this never has to fight it.
     */
    private void spinSphere(float dx, float dy, float dt) {
        float deg = DRAG_DEGREES_PER_PIXEL;
        this.sphereSpin += dx * deg;
        this.sphereTilt = Math.max(-55f, Math.min(55f, this.sphereTilt + dy * deg * 0.6f));
        // average the pointer speed over roughly the last three frames
        float instant = dx * deg / dt;
        this.sphereDragSpeed += (instant - this.sphereDragSpeed) * Math.min(1f, dt * 14f);
        this.sphereSpinVelocity = Math.max(-SPIN_SPEED_CAP,
                Math.min(SPIN_SPEED_CAP, this.sphereDragSpeed * SPIN_CARRY));
        this.state.sphereSpin = this.sphereSpin;
        this.state.sphereTilt = this.sphereTilt;
    }

    /** A drag is a gesture, not a click: this is the same margin the reference uses. */
    private float dragThreshold() {
        int scale = new ScaledResolution(this.mc).getScaleFactor();
        return Math.max(2f, DRAG_HYSTERESIS / Math.max(1, scale));
    }

    private void cycleFocus(int direction) {
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < this.layout.slots.size(); i++) {
            if (this.layout.slots.get(i).entry.enabled) {
                order.add(i);
            }
        }
        if (order.isEmpty()) {
            return;
        }
        int at = order.indexOf(this.focusedSlot);
        int next = at < 0 ? (direction > 0 ? 0 : order.size() - 1)
                : (at + direction + order.size()) % order.size();
        this.focusedSlot = order.get(next);
    }

    // ================================================================ actions

    /**
     * Starts the close animation, then hands the entry back to vanilla.
     *
     * <p>The activation is deliberately deferred to {@link #updateScreen()}: the menu collapsing and
     * the next screen appearing at the same instant looks like a glitch, whereas one after the other
     * looks like navigation.  Nothing is bypassed in between — {@link #actionPerformed} still runs,
     * and the Forge event pair is posted exactly where vanilla posts it.
     */
    private void activate(int index) {
        if (this.timeline.closing() || index < 0 || index >= this.layout.slots.size()) {
            return;
        }
        Object payload = this.layout.slots.get(index).entry.payload;
        // A link is a side effect, not navigation: the menu stays exactly where it is, and the
        // entrance is not replayed, because the player has not gone anywhere.
        if (payload instanceof PauseLink) {
            playClick();
            ((PauseLink) payload).open();
            return;
        }
        if (payload instanceof PauseShortcut && this.layout.slots.get(index).entry.enabled) {
            playClick();
            this.pending = index;
            this.pendingButton = null;
            this.pendingShortcut = (PauseShortcut) payload;
            this.timeline.close(System.nanoTime());
            this.pointer.cancel();
            return;
        }
        if (!(payload instanceof GuiButton)) {
            return;
        }
        GuiButton button = (GuiButton) payload;
        if (!button.enabled) {
            return;
        }
        GuiScreenEvent.ActionPerformedEvent.Pre event =
                new GuiScreenEvent.ActionPerformedEvent.Pre(this, button, this.buttonList);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            this.pointer.cancel();
            return;
        }
        button = event.getButton();
        this.selectedButton = button;
        button.playPressSound(this.mc.getSoundHandler());
        this.pending = index;
        this.pendingButton = button;
        this.timeline.close(System.nanoTime());
        this.pointer.cancel();
    }

    private void close() {
        if (this.timeline == null || this.timeline.closing()) {
            return;
        }
        this.pending = PauseScene.CLOSE;
        this.pendingButton = null;
        this.timeline.close(System.nanoTime());
        this.pointer.cancel();
    }

    @Override
    public void updateScreen() {
        if (fallback || this.timeline == null) {
            super.updateScreen();
            return;
        }
        long now = System.nanoTime();
        if (this.player != null && now - this.lastSnapshot >= 250_000_000L) {
            this.player = PauseMenuChrome.snapshot(this.mc);
            this.state.player = this.player;
            // shift the history and append the newest sample; 120 fps is treated as full scale
            for (int i = 0; i < this.spark.length - 1; i++) {
                this.spark[i] = this.spark[i + 1];
            }
            this.spark[this.spark.length - 1] =
                    Math.min(1f, Math.max(0f, this.player.fps / 120f));
            if (this.sparkCount < this.spark.length) {
                this.sparkCount++;
            }
            this.state.sparkCount = this.sparkCount;
            this.lastSnapshot = now;
        }
        if (this.timeline.finished(now) && this.pending != PauseScene.NONE) {
            applyPending(now);
        }
    }

    private void applyPending(long now) {
        int action = this.pending;
        GuiButton button = this.pendingButton;
        PauseShortcut shortcut = this.pendingShortcut;
        this.pending = PauseScene.NONE;
        this.pendingButton = null;
        this.pendingShortcut = null;

        if (shortcut != null) {
            if (shortcut == PauseShortcut.QUIT) {
                this.mc.shutdown();
                return;
            }
            this.mc.displayGuiScreen(shortcut.create(this.mc, this));
            return;
        }
        if (action == PauseScene.CLOSE) {
            this.mc.displayGuiScreen(null);
            if (this.mc.currentScreen == null) {
                this.mc.setIngameFocus();
            }
            return;
        }
        if (button == null) {
            return;
        }
        try {
            this.actionPerformed(button);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (this.equals(this.mc.currentScreen)) {
            MinecraftForge.EVENT_BUS.post(
                    new GuiScreenEvent.ActionPerformedEvent.Post(this, button, this.buttonList));
            // the action did not leave the screen (a toggle, or a cancelled open): play the intro again
            this.timeline = new PauseTimeline(now, !PauseMenuChrome.animation());
            this.pointer.cancel();
        }
    }
}
