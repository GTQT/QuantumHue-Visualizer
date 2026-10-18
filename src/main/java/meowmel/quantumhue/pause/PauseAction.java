package meowmel.quantumhue.pause;

/**
 * What a pause-menu entry <em>is</em>, as far as the layout is concerned.
 *
 * <p>The screen builds these from the buttons vanilla and Forge put on {@code GuiIngameMenu}, so
 * the layout never has to know about button ids; only the classification step does.
 */
public enum PauseAction {

    /** "Back to Game" — drawn as an arc band inside the disc. */
    RESUME,
    /**
     * GTQT Core's market and announcement screens.
     *
     * <p>Not primaries: they are not part of the navigation column.  The market sits in the top-left
     * corner of the board and the announcement in the top-right, and both are drawn whether or not
     * GTQT Core is installed — the pair is part of the composition, and a shrunken board with two
     * holes in it would be worse than two plates that cannot be clicked.
     */
    SHOP,
    NEWS,
    /** The pack's documentation and its QQ group: browser links, not screens. */
    DOCS,
    QQ,
    /** Advancements / statistics / options / mod options — drawn as left-hand cards. */
    ADVANCEMENTS,
    STATISTICS,
    OPTIONS,
    MOD_OPTIONS,
    /** "Open to LAN" and anything a mod added — drawn as arc-ordered tiles on the right. */
    WORLD_OPTIONS,
    OTHER,
    /** "Save and quit" / "Disconnect" — the right half of the arc band. */
    DISCONNECT,
    /** Shut the game down entirely; there is no vanilla button for this, so the mod supplies it. */
    QUIT_GAME,

    // Vanilla sub-screen shortcuts the pause menu adds itself: video, keys, sound, chat, language,
    // resource packs, skin, snooper.  Each one is just a way to reach a screen the player would
    // otherwise have to walk through Options to find.
    DISPLAY,
    CONTROLS,
    SOUND,
    CHAT,
    LANGUAGE,
    PACKS,
    SKIN,
    DEBUG;

    /** The four cards on the left; everything else is a tile, the resume band or the power plate. */
    public boolean isPrimary() {
        return this == ADVANCEMENTS || this == STATISTICS || this == OPTIONS || this == MOD_OPTIONS;
    }

    /** Position of this action's card, or {@code -1} when it is not a card. */
    public int cardOrder() {
        switch (this) {
            case ADVANCEMENTS: return 0;
            case STATISTICS: return 1;
            case OPTIONS: return 2;
            case MOD_OPTIONS: return 3;
            default: return -1;
        }
    }
}
