package meowmel.quantumhue.pause;

/**
 * A critically-damped spring, the thing that makes the hover and press feedback feel like hardware
 * rather than like a linear fade.
 *
 * <p>{@link #update} is fed the target and the frame time; the frequency is the only tuning knob —
 * higher is stiffer.  The values used by the scene are 22 for hover and 35 for press, which settle
 * in roughly 150 ms and 90 ms respectively.
 *
 * <p>Two details matter and both are the source implementation's: the time step is clamped to 50 ms
 * so a hitched frame cannot make the spring explode, and values are snapped to the target once they
 * are within 0.1% so nothing is left jittering at the end.
 */
public final class Spring {

    private float value;
    private float velocity;

    public float update(float target, float seconds, float frequency) {
        float dt = seconds < 0f ? 0f : (seconds > 0.05f ? 0.05f : seconds);
        float offset = value - target;
        float decay = (float) Math.exp(-frequency * dt);
        float intermediate = velocity + frequency * offset;
        value = target + (offset + intermediate * dt) * decay;
        velocity = (velocity - frequency * intermediate * dt) * decay;
        if (Math.abs(value - target) < 0.0001f && Math.abs(velocity) < 0.0001f) {
            value = target;
            velocity = 0f;
        }
        return value;
    }

    public float value() {
        return value;
    }

    public void reset(float target) {
        value = target;
        velocity = 0f;
    }
}
