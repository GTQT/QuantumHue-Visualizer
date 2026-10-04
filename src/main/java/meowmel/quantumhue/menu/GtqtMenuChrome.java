package meowmel.quantumhue.menu;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.modernsplash.Backdrop;
import meowmel.quantumhue.modernsplash.DysonSphere;
import meowmel.quantumhue.modernsplash.SplashTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * The title screen, drawn entirely from scratch.
 *
 * <p>Nothing vanilla survives on this screen: the panorama skybox is deleted outright, and the
 * two full-screen gradient washes, the {@code minecraft.png} title image and the {@code edition.png}
 * ribbon are painted over by an opaque backdrop in the same frame.  Everything visible is drawn
 * here or by {@link GtqtMenuChrome#drawButton}.
 *
 * <h3>Composition</h3>
 * A Dyson sphere is the background: its centre sits exactly on the left edge of the screen so only
 * its right half shows, and it holds no emblem.  The brand lockup — gear plus wordmark — is a small
 * mark in the top-right corner.
 *
 * <p>The sphere geometry comes from {@link DysonSphere}, the same class the loading screen uses;
 * the split into a back and a front half is irrelevant here because there is nothing at {@code z = 0}
 * to interleave it with, so both halves are simply drawn in order.
 */
public final class GtqtMenuChrome {

    public static final ResourceLocation EMBLEM =
            new ResourceLocation("quantumhue:textures/gui/title/gtqt_logo.png");

    private static final float[] ACCENT = SplashTheme.ACCENT;
    private static final float[] LINE = SplashTheme.LINE;
    private static final float[] GOLD = SplashTheme.GOLD;
    private static final float[] TEXT = SplashTheme.TEXT;
    private static final float[] TEXT_DIM = SplashTheme.TEXT_DIM;
    private static final float[] SHELL_PANEL = SplashTheme.SHELL_PANEL;

    /** Swarm output buffers; reused every frame.  The board's live in {@link Backdrop}. */
    private static final DysonSphere.Buffers SPHERE = new DysonSphere.Buffers();

    private GtqtMenuChrome() {}

    // ---------------------------------------------------------------- config

    public static boolean enabled() {
        try {
            return QuantumHueConfig.mainMenu.enabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean sphereEnabled() {
        try {
            return QuantumHueConfig.mainMenu.emblem;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean isMainMenu(Minecraft mc) {
        return enabled() && mc != null && mc.currentScreen instanceof GuiMainMenu;
    }

    // ---------------------------------------------------------------- helpers

    private static int rgba(float[] c, float a) {
        int ai = (int) (MathHelper.clamp(a, 0f, 1f) * 255f + 0.5f);
        return (ai << 24)
                | ((int) (c[0] * 255f + 0.5f) << 16)
                | ((int) (c[1] * 255f + 0.5f) << 8)
                | (int) (c[2] * 255f + 0.5f);
    }

    private static void prepare() {
        // Everything drawn here is translucent, and the splash-style hairlines sit far below the
        // alpha-test threshold of 0.1 that vanilla leaves enabled between frames.  Turning the test
        // off is what keeps the grid and the swarm's dim lines from being discarded.
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** The painter owns the blend setup; this is the shorthand the button path uses. */
    private static void blendMode(boolean additive) {
        GlStatePainter.INSTANCE.blendMode(additive);
    }

    /** Draws a texture sub-rectangle with explicit UVs. */
    private static void texture(Minecraft mc, ResourceLocation tex,
                                float x, float y, float w, float h,
                                float u0, float v0, float u1, float v1, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        GlStateManager.enableTexture2D();
        blendMode(false);
        GlStateManager.color(1f, 1f, 1f, alpha);
        mc.getTextureManager().bindTexture(tex);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + h, 0.0D).tex(u0, v1).endVertex();
        buffer.pos(x + w, y + h, 0.0D).tex(u1, v1).endVertex();
        buffer.pos(x + w, y, 0.0D).tex(u1, v0).endVertex();
        buffer.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------- backdrop

    /**
     * Paints the entire background: void wash, grid, the Dyson sphere, and the cradle.  Called from
     * the overwritten {@code renderSkybox}, and again after the vanilla title quads so that they are
     * covered — see {@code GuiMainMenuMixin}.
     */
    public static void drawBackdrop(Minecraft mc, GuiScreen screen) {
        drawBackground(mc, screen.width, screen.height);
        drawCradle(screen.width, screen.height);
    }

    /**
     * The part of the backdrop that is a *background*: the shared {@link Backdrop} plus the Dyson
     * swarm.  Shared with {@code GuiMainMenuMixin.renderSkybox}, which is what
     * {@code ClientHelper.renderPanorama} drives for every other screen, so this must be
     * self-contained and must leave GL state sane.
     *
     * <p>The wash, the PCB board and the grid are not drawn here any more — they are
     * {@link Backdrop}, the same three layers the splash and the offline previews paint, so the
     * four screens cannot drift apart.
     */
    public static void drawBackground(Minecraft mc, int w, int h) {
        // Milliseconds since boot.  Every clock derived from it is computed in double and only
        // narrowed where it is used: in float, the ulp at a multi-hour uptime is milliseconds, which
        // is enough to make the swarm's spin quantise to visible steps.
        double secs = Minecraft.getSystemTime() / 1000.0;
        GlStatePainter p = GlStatePainter.INSTANCE;

        prepare();

        // 1-3. the background every screen shares
        Backdrop.paint(p, 0f, 0f, w, h, secs);

        // 4. the Dyson sphere, in front of it
        if (sphereEnabled()) {
            // The energy arcs are phased on `(time * 0.13f) % 1f`, so a 3600 s wrap lands exactly on
            // an integer number of cycles: the loop is seamless while `time` stays small.
            float arcTime = (float) (secs % 3600.0);
            float spinDeg = (float) ((secs * 7.5) % 360.0);
            float tiltDeg = 21f + 6f * (float) Math.sin(secs * 0.13);
            DysonSphere.build(arcTime, 1f, spinDeg, tiltDeg,
                    MenuLayout.sphereCx(), MenuLayout.sphereCy(h),
                    MenuLayout.sphereRadius(h), h, SPHERE);

            p.blendMode(false);
            p.quads(SPHERE.panelBack, SPHERE.panelBackCount,
                    SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], SplashTheme.Layer.SWARM_BACK);
            p.lines(SPHERE.structBack, SPHERE.structBackCount,
                    ACCENT[0], ACCENT[1], ACCENT[2], SplashTheme.Layer.RING_BACK, 1f);
            p.blendMode(true);
            p.lines(SPHERE.energyBack, SPHERE.energyBackCount,
                    GOLD[0], GOLD[1], GOLD[2], SplashTheme.Layer.ARC_BACK, 1.4f);
            p.blendMode(false);
            p.quads(SPHERE.panelFront, SPHERE.panelFrontCount,
                    SHELL_PANEL[0], SHELL_PANEL[1], SHELL_PANEL[2], SplashTheme.Layer.SWARM_FRONT);
            p.blendMode(true);
            p.lines(SPHERE.energyFront, SPHERE.energyFrontCount,
                    GOLD[0], GOLD[1], GOLD[2], SplashTheme.Layer.ARC_FRONT, 1.6f);
            p.lines(SPHERE.structFront, SPHERE.structFrontCount,
                    ACCENT[0], ACCENT[1], ACCENT[2], SplashTheme.Layer.RING_FRONT, 1f);
            p.blendMode(false);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** The main-menu-only furniture: rails, corner brackets and the instrument column spine. */
    public static void drawCradle(int w, int h) {
        prepare();

        int inset = MenuLayout.INSET;
        Gui.drawRect(inset, inset, w - inset, inset + 1, rgba(LINE, SplashTheme.Layer.RAIL));
        Gui.drawRect(inset, h - inset - 1, w - inset, h - inset, rgba(LINE, SplashTheme.Layer.RAIL));
        bracket(inset, inset, 1, 1);
        bracket(w - inset, inset, -1, 1);
        bracket(inset, h - inset, 1, -1);
        bracket(w - inset, h - inset, -1, -1);

        int colX = MenuLayout.columnX(w);
        int colTop = MenuLayout.columnTop(h);
        int colBottom = MenuLayout.columnBottom(h);
        Gui.drawRect(colX - 14, colTop, colX - 13, colBottom, rgba(ACCENT, 0.35f));
        Gui.drawRect(colX - 14, colTop, colX - 4, colTop + 1, rgba(GOLD, 0.60f));
        Gui.drawRect(colX - 14, colBottom - 1, colX - 4, colBottom, rgba(GOLD, 0.25f));

        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void bracket(int x, int y, int dx, int dy) {
        int len = MenuLayout.BRACKET;
        Gui.drawRect(x, y, x + dx * len, y + dy, rgba(ACCENT, 0.34f));
        Gui.drawRect(x, y, x + dx, y + dy * len, rgba(ACCENT, 0.34f));
    }

    // ---------------------------------------------------------------- brand lockup

    /** The gear mark, small, in the top-right corner. */
    public static void drawTitleBlock(Minecraft mc, GuiScreen screen) {
        int w = screen.width;
        int h = screen.height;
        prepare();

        int emblem = MenuLayout.emblemSize(w, h);
        int emblemX = MenuLayout.emblemX(w, h);
        int top = MenuLayout.lockupTop();

        texture(mc, EMBLEM, emblemX, top, emblem, emblem, 0f, 0f, 1f, 1f, 1f);

        // a hairline under the mark ties it to the corner
        int railY = top + emblem + 10;
        Gui.drawRect(emblemX, railY, emblemX + emblem, railY + 1, rgba(ACCENT, 0.45f));

        mc.fontRenderer.drawString("// MAIN", MenuLayout.columnX(w) - 14,
                MenuLayout.columnTop(h) - MenuLayout.CAPTION_LIFT, rgba(ACCENT, 0.85f), false);

        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------- footer

    /**
     * The old-driver notice, when it applies.  Nothing else is drawn along the bottom: the four
     * Forge/MCP/version lines and the Mojang copyright are all build telemetry or legalese, and the
     * title screen is cleaner without them.
     *
     * <p>Rare — it needs a pre-OpenGL-2.0 context, which this pack cannot run on anyway, but
     * dropping it silently would leave such a user with no explanation at all.
     */
    public static void drawFooter(Minecraft mc, GuiScreen screen, boolean unsupportedGl) {
        if (!unsupportedGl) {
            return;
        }
        prepare();
        drawGlWarning(mc, screen, screen.width);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /**
     * Vanilla's "your graphics driver is too old" notice.  Same strings and geometry as
     * {@code GuiMainMenu}; only the draw call is ours.  Rare — it needs a pre-OpenGL-2.0 context.
     */
    private static void drawGlWarning(Minecraft mc, GuiScreen screen, int w) {
        String line1 = I18n.format("title.oldgl1");
        String line2 = I18n.format("title.oldgl2");
        int width1 = mc.fontRenderer.getStringWidth(line1);
        int width2 = mc.fontRenderer.getStringWidth(line2);
        int boxW = Math.max(width1, width2);
        int x1 = (w - boxW) / 2;
        int y1 = screen.buttonList.isEmpty() ? 40 : screen.buttonList.get(0).y - 24;
        int x2 = x1 + boxW;
        int y2 = y1 + 24;

        Gui.drawRect(x1 - 2, y1 - 2, x2 + 2, y2 - 1, 0x55200000);
        screen.drawString(mc.fontRenderer, line1, x1, y1, 0xFFFFFF);
        screen.drawString(mc.fontRenderer, line2, (w - width2) / 2, y1 + 12, 0xFFFFFF);
    }

    // ---------------------------------------------------------------- buttons

    private static String indexCode(int id) {
        switch (id) {
            case 1: return "01";
            case 2: return "02";
            case 6: return "03";
            case 14: return "04";
            case 0: return "05";
            case 4: return "06";
            default: return null;
        }
    }

    /**
     * Flat HUD plate: dark translucent fill, hairline frame, a leading accent bar and a numeric
     * readout.  Replaces {@code GuiButton.drawButton} for main-menu buttons only.
     */
    public static void drawButton(Minecraft mc, GuiButton b, int mouseX, int mouseY) {
        boolean active = b.enabled;
        // Hit-tested here rather than via GuiButton.isMouseOver(): that only ever returns the
        // "hovered" field, which vanilla assigns inside GuiButton.drawButton — a method this screen
        // deliberately never calls.  Reading it would report false for every button, forever.
        boolean hovered = active
                && mouseX >= b.x && mouseY >= b.y
                && mouseX < b.x + b.width && mouseY < b.y + b.height;

        int x = b.x;
        int y = b.y;
        int w = b.width;
        int h = b.height;

        prepare();

        Gui.drawRect(x, y, x + w, y + h, hovered ? 0x9E1E1438 : 0x73120B22);

        if (hovered) {
            // additive halo just outside the plate: three nested outlines rather than a filled
            // block, so the plate lights up without washing the label out
            blendMode(true);
            for (int i = 1; i <= 3; i++) {
                frame(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, rgba(GOLD, 0.11f / i));
            }
            blendMode(false);
            // warm interior wash so the plate reads as lit rather than merely outlined
            Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0x1CFFD24A);
        }

        int border = hovered ? rgba(ACCENT, 1f) : rgba(LINE, 0.38f);
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y + 1, x + 1, y + h - 1, border);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, border);

        int barW = hovered ? 4 : 2;
        Gui.drawRect(x, y + 1, x + barW, y + h - 1,
                hovered ? rgba(GOLD, 1f) : rgba(ACCENT, active ? 0.70f : 0.28f));

        if (hovered) {
            // selection brackets, pulsing slowly — the HUD way of saying "this one is armed"
            float pulse = 0.70f + 0.30f * (float) Math.sin(Minecraft.getSystemTime() * 0.0055);
            int colour = rgba(GOLD, pulse);
            bracketPair(x, y, w, h, colour);
        }

        int textY = y + (h - 8) / 2;
        int textColor = !active ? rgba(TEXT_DIM, 0.55f)
                : (hovered ? rgba(TEXT, 1f) : rgba(TEXT, 0.82f));
        mc.fontRenderer.drawString(b.displayString, x + 12f, textY, textColor, false);

        String code = indexCode(b.id);
        if (code != null) {
            int codeW = mc.fontRenderer.getStringWidth(code);
            mc.fontRenderer.drawString(code, x + w - 8 - codeW, textY,
                    hovered ? rgba(GOLD, 1f) : rgba(TEXT_DIM, 0.55f), false);
        }

        if (!active) {
            Gui.drawRect(x, y, x + w, y + h, 0x66000000);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** A one-pixel outline. */
    private static void frame(int x0, int y0, int x1, int y1, int colour) {
        Gui.drawRect(x0, y0, x1, y0 + 1, colour);
        Gui.drawRect(x0, y1 - 1, x1, y1, colour);
        Gui.drawRect(x0, y0 + 1, x0 + 1, y1 - 1, colour);
        Gui.drawRect(x1 - 1, y0 + 1, x1, y1 - 1, colour);
    }

    /** Four L-shaped corner ticks, {@code 2px} clear of the plate so the row gap still swallows them. */
    private static void bracketPair(int x, int y, int w, int h, int colour) {
        final int off = 2;
        final int len = 6;
        Gui.drawRect(x - off, y - off, x - off + len, y - off + 1, colour);
        Gui.drawRect(x - off, y - off, x - off + 1, y - off + len, colour);

        Gui.drawRect(x + w + off - len, y - off, x + w + off, y - off + 1, colour);
        Gui.drawRect(x + w + off - 1, y - off, x + w + off, y - off + len, colour);

        Gui.drawRect(x - off, y + h + off - 1, x - off + len, y + h + off, colour);
        Gui.drawRect(x - off, y + h + off - len, x - off + 1, y + h + off, colour);

        Gui.drawRect(x + w + off - len, y + h + off - 1, x + w + off, y + h + off, colour);
        Gui.drawRect(x + w + off - 1, y + h + off - len, x + w + off, y + h + off, colour);
    }
}
