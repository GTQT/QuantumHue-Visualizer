package meowmel.quantumhue.pause;

/**
 * The readouts the pause menu shows about the player, captured once when the screen opens (and
 * refreshed a few times a second, because the player can be standing in lava while the menu is up).
 *
 * <p>Kept free of Minecraft types so the layout and the offline preview can be given fake values.
 */
public final class PlayerCardData {

    public String name = "";
    /** Experience level. */
    public int level;
    /** Experience bar progress, 0..1. */
    public float experience;

    /** "Survival" / "Creative" / "Adventure" — whichever the player is in. */
    public String mode = "";
    /** Single-player save name, or the server address. */
    public String world = "";
    /** "Single-player" / "Multiplayer". */
    public String connection = "";
    /** Formatted coordinates. */
    public String position = "";
    /** Dimension name, e.g. "Overworld". */
    public String dimension = "";
    /** Biome the player is standing in, as the game names it. */
    public String biome = "";
    /** A one-line hint shown bottom-right, in the HUD's own voice. */
    public String hint = "";
    /** Real-world date and time, already formatted; the scene cannot read a calendar itself. */
    public String clock = "";

    // ---- telemetry the instruments read; every one of these is a real client value
    public int fps;
    public long memUsedMb;
    public long memMaxMb;
    public int players;
    public int entities;
    /** Render distance in chunks. */
    public int viewDistance;
    public float health;
    public float maxHealth;
    public int food;
    public int armor;
    /** In-world day number. */
    public long day;
    /** In-world time as {@code HH:mm}. */
    public String worldClock = "";
    /** {@code CLEAR}, {@code RAIN} or {@code STORM}. */
    public String weather = "";
    /** Player yaw in degrees; drives the heading readout. */
    public float yaw;
    public long sessionSeconds;
    /** Localised-free difficulty name: PEACEFUL / EASY / NORMAL / HARD. */
    public String difficulty = "";
    public boolean hardcore;
    /** Deaths in this world. */
    public int deaths;
    /** Bearing to the world spawn, 0 = north, clockwise; only valid when {@link #hasSpawn}. */
    public boolean hasSpawn;
    public float spawnBearing;
    public float spawnDistance;

    public PlayerCardData copy() {
        PlayerCardData c = new PlayerCardData();
        c.name = name;
        c.level = level;
        c.experience = experience;
        c.mode = mode;
        c.world = world;
        c.connection = connection;
        c.position = position;
        c.dimension = dimension;
        c.hint = hint;
        c.clock = clock;
        c.fps = fps;
        c.memUsedMb = memUsedMb;
        c.memMaxMb = memMaxMb;
        c.players = players;
        c.viewDistance = viewDistance;
        c.entities = entities;
        c.health = health;
        c.maxHealth = maxHealth;
        c.food = food;
        c.armor = armor;
        c.day = day;
        c.worldClock = worldClock;
        c.weather = weather;
        c.yaw = yaw;
        c.sessionSeconds = sessionSeconds;
        c.difficulty = difficulty;
        c.hardcore = hardcore;
        c.deaths = deaths;
        c.biome = biome;
        c.hasSpawn = hasSpawn;
        c.spawnBearing = spawnBearing;
        c.spawnDistance = spawnDistance;
        return c;
    }
}
