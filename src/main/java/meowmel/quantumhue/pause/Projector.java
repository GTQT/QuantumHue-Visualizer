package meowmel.quantumhue.pause;

/**
 * The pause menu's one piece of 3D: a board on a table, seen from slightly above and tilted by the
 * pointer.
 *
 * <p>Everything on the screen is a rectangle or a circle on the board, and every one of them is
 * projected through here before it is drawn.  A card therefore foreshortens as it approaches the
 * top of the screen, and the disc turns into an ellipse — which is the whole reason the layout
 * reads as a physical panel rather than as a stack of quads.
 *
 * <p>The chain is: shrink by depth compensation, tilt about X, then yaw about Y, then pitch about
 * X again, then divide by {@code distance - z} for perspective.  That is exactly the EndfieldCraft
 * projection, including its numbers ({@code 20} degrees of base pitch, {@code 1600} units of
 * distance, depth compensation by {@code 1700}); only the origin constant differs, so board
 * coordinates here land back on themselves instead of being offset by the hand-tuned
 * {@code (989, 500)} the original uses.
 *
 * <p>Pure maths, no Minecraft: {@code tools/PausePreview.java} projects the same way.
 */
public final class Projector {

    private static final float DISTANCE = 1600f;
    private static final float DEPTH_COMPENSATION = 1700f;
    /** The board leans back a little even with the pointer idle. */
    private static final float BASE_PITCH = 20f;

    /** A plane parallel to the board at some depth, optionally scaled and tilted about its own X. */
    public static final class Plane {
        final float depth;
        final float scale;
        final float tilt;
        final boolean compensate;

        Plane(float depth, float scale, float tilt, boolean compensate) {
            this.depth = depth;
            this.scale = scale;
            this.tilt = tilt;
            this.compensate = compensate;
        }

        public static Plane at(float depth) {
            return new Plane(depth, 1f, 0f, true);
        }

        /** The disc's plane: its depth is not compensated, so the intro scale-up is visible. */
        public static Plane disc(float depth, float scale, float tilt) {
            return new Plane(depth, scale, tilt, false);
        }
    }

    private final PauseLayout layout;
    private float yaw;
    private float pitch;
    /** Origin of the canvas in the painter's own space: {@code (0,0)} in game, centre-based for AWT. */
    private final float originX;
    private final float originY;

    /**
     * @param yaw   degrees, positive turns the left edge away
     * @param pitch degrees, added to {@link #BASE_PITCH}; positive tips the board forward
     */
    public Projector(PauseLayout layout, float yaw, float pitch, float originX, float originY) {
        this.layout = layout;
        this.yaw = yaw;
        this.pitch = pitch;
        this.originX = originX;
        this.originY = originY;
    }

    /** Re-aims an existing projector; the screen does this every frame instead of reallocating. */
    public void orient(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitchDegrees() {
        return pitch;
    }

    /**
     * Projects one board point into canvas space.
     *
     * <p>Writes two floats into {@code out} at {@code offset} — the callers here are per-frame and
     * must not allocate, see {@code WorldLoadingScene}'s note on why.
     */
    public void project(float x, float y, Plane plane, float[] out, int offset) {
        if (layout.compact) {
            out[offset] = originX + layout.offsetX + x * layout.scale;
            out[offset + 1] = originY + layout.offsetY + y * layout.scale;
            return;
        }

        float cx = PauseLayout.CENTER_X;
        float cy = PauseLayout.CENTER_Y;
        float compensation = plane.compensate ? 1f - plane.depth / DEPTH_COMPENSATION : 1f;

        double px = (x - cx) * plane.scale * compensation;
        double py = (y - cy) * plane.scale * compensation;
        double pz = plane.depth * (plane.compensate ? 1f : plane.scale);

        double tilt = Math.toRadians(plane.tilt);
        double sinTilt = Math.sin(tilt);
        double cosTilt = Math.cos(tilt);
        double tz = pz * cosTilt + sinTilt * py;
        py = py * cosTilt - pz * sinTilt;
        pz = tz;

        double ry = Math.toRadians(yaw);
        double rx = Math.toRadians(BASE_PITCH + pitch);
        double sx1 = Math.cos(ry) * px + Math.sin(ry) * pz;
        double sz1 = -Math.sin(ry) * px + Math.cos(ry) * pz;
        double sy1 = Math.cos(rx) * py - Math.sin(rx) * sz1;
        double sz2 = Math.sin(rx) * py + Math.cos(rx) * sz1;

        double perspective = DISTANCE / (DISTANCE - sz2);
        double roll = Math.toRadians(-1.0);
        double fx = (sx1 * Math.cos(roll) - sy1 * Math.sin(roll)) * perspective;
        double fy = (sx1 * Math.sin(roll) + sy1 * Math.cos(roll)) * perspective;

        out[offset] = originX + layout.offsetX + (float) (cx + fx) * layout.scale;
        out[offset + 1] = originY + layout.offsetY + (float) (cy + fy) * layout.scale;
    }

    /**
     * Projects a rectangle's four corners. {@code out} receives {@code x0,y0, x1,y0, x1,y1, x0,y1}
     * in canvas space — the winding {@code quads()} expects.
     */
    public void corners(Box box, Plane plane, float[] out) {
        project(box.x, box.y, plane, out, 0);
        project(box.right(), box.y, plane, out, 2);
        project(box.right(), box.bottom(), plane, out, 4);
        project(box.x, box.bottom(), plane, out, 6);
    }

    /** Screen-space centre of a rectangle, written into {@code out[offset]}, {@code out[offset+1]}. */
    public void centre(Box box, Plane plane, float[] out, int offset) {
        project(box.cx(), box.cy(), plane, out, offset);
    }

    /**
     * Convex-polygon hit test on already projected points.
     *
     * @param xy  {@code count} points as {@code x,y} pairs
     */
    public static boolean insidePolygon(float[] xy, int count, double x, double y) {
        if (count < 3) {
            return false;
        }
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < count; i++) {
            int a = i * 2;
            int b = ((i + 1) % count) * 2;
            double cross = (xy[b] - xy[a]) * (y - xy[a + 1]) - (xy[b + 1] - xy[a + 1]) * (x - xy[a]);
            positive |= cross > 0.0;
            negative |= cross < 0.0;
        }
        return !(positive && negative);
    }
}
