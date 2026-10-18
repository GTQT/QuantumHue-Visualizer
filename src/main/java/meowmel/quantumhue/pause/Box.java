package meowmel.quantumhue.pause;

/**
 * An axis-aligned rectangle in <b>design space</b> — the 1920x1080 board every pause-menu
 * measurement is expressed on.
 *
 * <p>Immutable and free of Minecraft imports, like the rest of {@code meowmel.quantumhue.pause}:
 * {@code tools/PausePreview.java} renders the same numbers outside the game.
 */
public final class Box {

    public final float x;
    public final float y;
    public final float w;
    public final float h;

    public Box(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public float right() {
        return x + w;
    }

    public float bottom() {
        return y + h;
    }

    public float cx() {
        return x + w * 0.5f;
    }

    public float cy() {
        return y + h * 0.5f;
    }

    /** Shrinks the box by {@code d} on every side. */
    public Box inset(float d) {
        return new Box(x + d, y + d, w - 2f * d, h - 2f * d);
    }

    /** Grows the box by {@code d} on every side (negative shrinks). */
    public Box grow(float d) {
        return inset(-d);
    }

    public Box translate(float dx, float dy) {
        return new Box(x + dx, y + dy, w, h);
    }

    /** Scales the box about its own centre — used for the press dip. */
    public Box scaleAboutCentre(float factor) {
        float nw = w * factor;
        float nh = h * factor;
        return new Box(cx() - nw * 0.5f, cy() - nh * 0.5f, nw, nh);
    }

    @Override
    public String toString() {
        return "Box[" + x + ", " + y + ", " + w + ", " + h + "]";
    }
}
