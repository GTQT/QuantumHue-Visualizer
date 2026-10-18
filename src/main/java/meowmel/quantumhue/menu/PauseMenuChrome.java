package meowmel.quantumhue.menu;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.pause.PauseScene;
import meowmel.quantumhue.pause.PlayerCardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.stats.StatList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * The glue between {@link PauseScene} and Minecraft.
 *
 * <p>Same split as the rest of the mod: the composition is MC-free and previewable, and everything
 * that needs a client lives here — the config gate, the player snapshot and the skin face, which is
 * the one thing on the screen that has to come from the game's own texture manager.
 */
@SideOnly(Side.CLIENT)
public final class PauseMenuChrome {

    private PauseMenuChrome() {}

    // ---------------------------------------------------------------- config

    public static boolean enabled() {
        try {
            return QuantumHueConfig.pauseMenu.enabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** False turns the timeline into its instant-arrival mode (accessibility, and old GPUs). */
    public static boolean animation() {
        try {
            return QuantumHueConfig.pauseMenu.animation;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean parallax() {
        try {
            return QuantumHueConfig.pauseMenu.parallax;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Debug-only: how many inert tiles to append to the right-hand column.
     *
     * <p>Exists so the scrolling column can be looked at before the pack has enough mod buttons to
     * fill it.  They are ordinary entries with no payload, so clicking one does nothing at all.
     */
    public static int placeholderTiles() {
        try {
            int n = QuantumHueConfig.pauseMenu.placeholderTiles;
            return n < 0 ? 0 : Math.min(n, 12);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Whether the eight vanilla sub-screens are offered as tiles. */
    public static boolean vanillaShortcuts() {
        try {
            return QuantumHueConfig.pauseMenu.vanillaShortcuts;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void warn(String message) {
        QuantumHue.LOGGER.warn("[PauseMenu] {}", message);
    }

    /** Logs once per failure site and lets the caller fall back to the vanilla screen. */
    public static void fail(String stage, Throwable failure) {
        QuantumHue.LOGGER.error("[PauseMenu] falling back to the vanilla pause menu after a failure in {}", stage, failure);
    }

    // ---------------------------------------------------------------- player snapshot

    /**
     * Reads the handful of numbers the pause menu shows.
     *
     * <p>Called once when the screen opens and again four times a second: the player keeps existing
     * (and can keep taking damage) while the menu is up in multiplayer, so a snapshot taken once at
     * open would go stale.
     */
    /** When this game started; the footer and the caption read out of it. */
    private static final long SESSION_START = System.currentTimeMillis();

    /** Client-thread only, which is why one formatter is enough. */
    private static final java.text.SimpleDateFormat CLOCK =
            new java.text.SimpleDateFormat("yyyy-MM-dd   HH:mm   EEE", java.util.Locale.getDefault());

    public static PlayerCardData snapshot(Minecraft mc) {
        PlayerCardData data = new PlayerCardData();
        if (mc == null || mc.player == null) {
            return data;
        }
        EntityPlayerSP player = mc.player;
        data.name = player.getName();
        data.level = player.experienceLevel;
        data.experience = player.experience;
        data.mode = mode(player);
        data.connection = connection(mc);
        data.world = worldName(mc);
        data.dimension = dimension(mc, player.dimension);
        data.position = String.format(java.util.Locale.ROOT, "X %.0f   Y %.0f   Z %.0f",
                player.posX, player.posY, player.posZ);
        data.hint = "ESC  TO  CLOSE";
        try {
            data.clock = CLOCK.format(new java.util.Date());
        } catch (Throwable ignored) {
            data.clock = "";
        }
        data.sessionSeconds = (System.currentTimeMillis() - SESSION_START) / 1000L;
        data.fps = Minecraft.getDebugFPS();
        Runtime runtime = Runtime.getRuntime();
        data.memUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        data.memMaxMb = runtime.maxMemory() / (1024L * 1024L);
        data.yaw = player.rotationYaw;
        data.health = player.getHealth();
        data.maxHealth = player.getMaxHealth();
        data.food = player.getFoodStats().getFoodLevel();
        data.armor = player.getTotalArmorValue();
        if (mc.world != null) {
            data.players = mc.world.playerEntities.size();
            data.entities = mc.world.getLoadedEntityList().size();
            data.viewDistance = mc.gameSettings.renderDistanceChunks;
            long total = mc.world.getWorldInfo().getWorldTotalTime();
            long timeOfDay = mc.world.getWorldTime() % 24000L;
            if (timeOfDay < 0L) {
                timeOfDay += 24000L;
            }
            data.day = total / 24000L;
            int hours = (int) ((timeOfDay / 1000L + 6L) % 24L);
            int minutes = (int) (timeOfDay % 1000L * 60L / 1000L);
            data.worldClock = String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes);
            data.weather = mc.world.isThundering() ? "STORM" : mc.world.isRaining() ? "RAIN" : "CLEAR";
            data.hardcore = mc.world.getWorldInfo().isHardcoreModeEnabled();
            data.difficulty = difficultyName(mc);
            BlockPos spawn = mc.world.getSpawnPoint();
            float dx = spawn.getX() + 0.5f - (float) player.posX;
            float dz = spawn.getZ() + 0.5f - (float) player.posZ;
            data.spawnBearing = (float) ((Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0);
            data.spawnDistance = MathHelper.sqrt(dx * dx + dz * dz);
            data.hasSpawn = true;
            try {
                data.deaths = player.getStatFileWriter().readStat(StatList.DEATHS);
            } catch (Throwable ignored) {
                data.deaths = 0;
            }
            try {
                Biome biome = mc.world.getBiome(new BlockPos(player.posX, player.posY, player.posZ));
                data.biome = biome == null || biome.getBiomeName() == null ? "" : biome.getBiomeName();
            } catch (Throwable ignored) {
                data.biome = "";
            }
        }
        return data;
    }

    private static String difficultyName(Minecraft mc) {
        EnumDifficulty difficulty = mc.world == null ? EnumDifficulty.NORMAL : mc.world.getDifficulty();
        if (difficulty == null) {
            return "NORMAL";
        }
        switch (difficulty) {
            case PEACEFUL: return "PEACEFUL";
            case EASY: return "EASY";
            case HARD: return "HARD";
            case NORMAL:
            default: return "NORMAL";
        }
    }

    private static String mode(EntityPlayerSP player) {
        if (player.isSpectator()) {
            return "Spectator";
        }
        if (player.isCreative()) {
            return "Creative";
        }
        GameType type = player.world == null ? null : player.world.getWorldInfo().getGameType();
        if (type == GameType.ADVENTURE) {
            return "Adventure";
        }
        return "Survival";
    }

    private static String connection(Minecraft mc) {
        if (mc.getCurrentServerData() != null) {
            return "Multiplayer  //  " + mc.getCurrentServerData().serverIP;
        }
        return mc.isSingleplayer() ? "Single-player" : "Multiplayer";
    }

    private static String worldName(Minecraft mc) {
        if (mc.world == null || mc.world.getWorldInfo() == null) {
            return "";
        }
        return mc.world.getWorldInfo().getWorldName();
    }

    private static String dimension(Minecraft mc, int id) {
        if (mc.world != null && mc.world.provider != null && mc.world.provider.getDimensionType() != null) {
            String name = mc.world.provider.getDimensionType().getName();
            if (name != null && !name.isEmpty()) {
                // "Overworld" -> "Overworld", "The Nether" -> "The Nether"
                return name;
            }
        }
        switch (id) {
            case 0: return "Overworld";
            case -1: return "The Nether";
            case 1: return "The End";
            default: return "Dimension " + id;
        }
    }

    // ---------------------------------------------------------------- skin face

    /**
     * Paints the player's face into the quad the scene reserved for it.
     *
     * <p>UVs are the vanilla 64x64 layout: the base face is the 8x8 tile at {@code (8, 8)}.  The hat
     * layer is skipped — at 70 board units the two are nearly the same picture, and the hat alone
     * would need its own blended pass.
     */
    public static void drawFace(Minecraft mc, PauseScene.State state) {
        if (!state.faceVisible || state.faceAlpha <= 0.01f || mc == null || mc.player == null) {
            return;
        }
        ResourceLocation skin = mc.player.getLocationSkin();
        if (skin == null) {
            return;
        }
        float[] quad = state.face;
        mc.getTextureManager().bindTexture(skin);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableAlpha();
        GlStateManager.color(1f, 1f, 1f, MathHelper.clamp(state.faceAlpha, 0f, 1f));

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float u0 = 8f / 64f;
        float v0 = 8f / 64f;
        float u1 = 16f / 64f;
        float v1 = 16f / 64f;
        // quad order is top-left, top-right, bottom-right, bottom-left
        buffer.pos(quad[6], quad[7], 0.0D).tex(u0, v1).endVertex();
        buffer.pos(quad[4], quad[5], 0.0D).tex(u1, v1).endVertex();
        buffer.pos(quad[2], quad[3], 0.0D).tex(u1, v0).endVertex();
        buffer.pos(quad[0], quad[1], 0.0D).tex(u0, v0).endVertex();
        tessellator.draw();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }
}
