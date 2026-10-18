package meowmel.quantumhue.pause;

/**
 * Wheel and drag scrolling for the tile column, with inertia and a rubber-band stop at either end.
 *
 * <p>A port of the reference's {@code ScrollMotion}: the wheel pushes a velocity, dragging sets one
 * directly, and once the list is past an end it springs back.  Positions are in <b>board units</b>
 * so the same numbers work at every window size.
 */
public final class PauseScroll {

    private float position;
    private float velocity;
    private float limit;
    private float springTarget = Float.NaN;

    public void setLimit(float limit) {
        this.limit = Math.max(0f, limit);
        this.position = clamp(position, 0f, this.limit);
        this.velocity = 0f;
        this.springTarget = Float.NaN;
    }

    public float limit() {
        return limit;
    }

    public void wheel(double amount) {
        if (limit <= 0f) {
            return;
        }
        springTarget = Float.NaN;
        velocity -= (float) amount * 720f;
    }

    /** Over-scrolls freely; {@link #update} pulls it back with resistance. */
    public void drag(float deltaBoard, float seconds) {
        if (limit <= 0f) {
            return;
        }
        springTarget = Float.NaN;
        float resistance = position < 0f || position > limit ? 0.3f : 1f;
        position -= deltaBoard * resistance;
        velocity = clamp(-deltaBoard / Math.max(seconds, 0.008f) * resistance, -2400f, 2400f);
    }

    public void update(float seconds, boolean dragging) {
        if (dragging || limit <= 0f) {
            return;
        }
        float dt = clamp(seconds, 0f, 0.033f);
        if (Float.isNaN(springTarget) && (position < 0f || position > limit)) {
            springTarget = clamp(position, 0f, limit);
        }
        if (!Float.isNaN(springTarget)) {
            float offset = position - springTarget;
            float decay = (float) Math.exp(-16f * dt);
            float intermediate = velocity + 16f * offset;
            position = springTarget + (offset + intermediate * dt) * decay;
            velocity = (velocity - 16f * intermediate * dt) * decay;
            if (Math.abs(velocity) < 0.2f && Math.abs(position - springTarget) < 0.2f) {
                position = springTarget;
                velocity = 0f;
                springTarget = Float.NaN;
            }
        } else {
            velocity *= (float) Math.exp(-7f * dt);
            position += velocity * dt;
            // relax the overscroll even when the wheel stopped short of an end
            if (position < 0f || position > limit) {
                position = clamp(position, -160f, limit + 160f);
            }
        }
    }

    public void stop() {
        velocity = 0f;
    }

    public float position() {
        return position;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
