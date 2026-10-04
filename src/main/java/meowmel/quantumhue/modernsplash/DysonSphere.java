package meowmel.quantumhue.modernsplash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Dyson swarm: a geodesic shell, a few thousand orbiting collector panels and a handful of
 * orbital rings, built around the product emblem.
 *
 * <h3>Why there is no depth buffer</h3>
 * The emblem is a flat sprite living on the {@code z = 0} plane, so "in front of the emblem" is
 * exactly {@code z > 0} after the view rotation.  Every primitive is therefore sorted into a back
 * half and a front half by the sign of its average rotated z, and the scene draws
 * <em>back half → emblem → front half</em>.  That produces the correct occlusion for a flat mark
 * inside a sphere without touching {@code GL_DEPTH_TEST} — which matters here because the splash
 * render thread shares the context with the game and {@code CustomSplash.setGL} deliberately
 * leaves depth testing off.
 *
 * <p>Points are unit-length; the caller supplies the screen centre, the desired silhouette radius
 * in splash coordinates and the viewport height, and the camera distance is solved from those so
 * the structure keeps its apparent size relative to the emblem at any resolution.
 *
 * <p>Free of Minecraft / LWJGL imports so {@code tools/SplashPreview.java} replays it exactly.
 */
public final class DysonSphere {

    /** Collector panels in the swarm. */
    public static final int PANELS = 460;
    /** Icosphere subdivision of the structural shell (1 -> 42 vertices, 120 edges). */
    private static final int SUBDIV = 1;
    /** Samples per orbital ring. */
    private static final int RING_SAMPLES = 72;
    /** Half-width of the bright energy arc travelling along each ring, in degrees. */
    private static final float ARC_HALF_DEG = 20f;
    /** Vertical field of view of the virtual camera. */
    private static final float FOV_DEG = 40f;
    /** Radius the un-assembled panels start from, in shell radii. */
    private static final float SPAWN_RADIUS = 2.55f;
    /**
     * Panel edge length as a fraction of the mean panel spacing on the shell.  Below ~0.4 the
     * shell reads as a swarm of separate collectors rather than as a tiled ball.  It was raised to
     * 0.25 at one point and the collectors came out as grey flags stuck on a wireframe; at 0.16 the
     * shell reads as a fine swarm and the great-circle rings carry the silhouette.
     */
    private static final float PANEL_COVERAGE = 0.16f;

    private DysonSphere() {}

    // ---------------------------------------------------------------- static geometry

    private static final float[] SHELL_VERTS;
    private static final int[] SHELL_EDGES;
    private static final float[] PANEL_POS;
    private static final float[] PANEL_TAN;
    private static final float[] PANEL_BIT;
    private static final float[] PANEL_DELAY;
    private static final float[] PANEL_ROLL;
    private static final float[][] RINGS;
    private static final float[] RING_PHASE;

    static {
        List<float[]> verts = new ArrayList<>();
        Set<Long> edges = new HashSet<>();
        buildIcosphere(verts, edges);

        SHELL_VERTS = new float[verts.size() * 3];
        for (int i = 0; i < verts.size(); i++) {
            float[] v = verts.get(i);
            SHELL_VERTS[i * 3] = v[0];
            SHELL_VERTS[i * 3 + 1] = v[1];
            SHELL_VERTS[i * 3 + 2] = v[2];
        }
        SHELL_EDGES = new int[edges.size() * 2];
        int e = 0;
        for (long key : edges) {
            SHELL_EDGES[e++] = (int) (key >>> 32);
            SHELL_EDGES[e++] = (int) (key & 0xFFFFFFFFL);
        }

        PANEL_POS = new float[PANELS * 3];
        PANEL_TAN = new float[PANELS * 3];
        PANEL_BIT = new float[PANELS * 3];
        PANEL_DELAY = new float[PANELS];
        PANEL_ROLL = new float[PANELS];
        float golden = (float) Math.PI * (3f - (float) Math.sqrt(5.0));
        for (int i = 0; i < PANELS; i++) {
            float y = 1f - 2f * (i + 0.5f) / PANELS;
            float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            float theta = i * golden;
            float x = (float) Math.cos(theta) * r;
            float z = (float) Math.sin(theta) * r;

            PANEL_POS[i * 3] = x;
            PANEL_POS[i * 3 + 1] = y;
            PANEL_POS[i * 3 + 2] = z;

            float ux = 0f;
            float uy = 1f;
            if (Math.abs(y) > 0.985f) {
                ux = 1f;
                uy = 0f;
            }
            float tx = uy * z - 0f * y;
            float ty = 0f * x - ux * z;
            float tz = ux * y - uy * x;
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-5f) {
                tx = 1f;
                ty = 0f;
                tz = 0f;
                tl = 1f;
            }
            tx /= tl;
            ty /= tl;
            tz /= tl;
            PANEL_TAN[i * 3] = tx;
            PANEL_TAN[i * 3 + 1] = ty;
            PANEL_TAN[i * 3 + 2] = tz;
            PANEL_BIT[i * 3] = y * tz - z * ty;
            PANEL_BIT[i * 3 + 1] = z * tx - x * tz;
            PANEL_BIT[i * 3 + 2] = x * ty - y * tx;

            PANEL_DELAY[i] = hash01(i * 2654435761L);
            PANEL_ROLL[i] = (hash01(i * 40503L + 7L) - 0.5f) * 2f;
        }

        RINGS = new float[4][];
        RING_PHASE = new float[] {0.00f, 0.27f, 0.54f, 0.78f};
        float[][] axes = {
                {0f, 1f, 0f},
                {1f, 0f, 0.42f},
                {0.32f, 0.24f, 1f},
                {0.86f, -0.48f, 0.18f},
        };
        for (int a = 0; a < axes.length; a++) {
            float[] axis = normalize(axes[a]);
            float[] t1 = normalize(cross(axis, Math.abs(axis[1]) > 0.9f ? new float[] {1f, 0f, 0f} : new float[] {0f, 1f, 0f}));
            float[] t2 = cross(axis, t1);
            float[] pts = new float[(RING_SAMPLES + 1) * 3];
            for (int i = 0; i <= RING_SAMPLES; i++) {
                double ang = i * 2.0 * Math.PI / RING_SAMPLES;
                float c = (float) Math.cos(ang);
                float s = (float) Math.sin(ang);
                pts[i * 3] = t1[0] * c + t2[0] * s;
                pts[i * 3 + 1] = t1[1] * c + t2[1] * s;
                pts[i * 3 + 2] = t1[2] * c + t2[2] * s;
            }
            RINGS[a] = pts;
        }
    }

    private static void buildIcosphere(List<float[]> verts, Set<Long> edges) {
        final float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] ico = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1},
        };
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1},
        };
        for (float[] v : ico) {
            verts.add(normalize(v));
        }
        Map<Long, Integer> cache = new HashMap<>();
        for (int s = 0; s < SUBDIV; s++) {
            List<int[]> next = new ArrayList<>(faces.length * 4);
            for (int[] f : faces) {
                int a = midpoint(verts, cache, f[0], f[1]);
                int b = midpoint(verts, cache, f[1], f[2]);
                int c = midpoint(verts, cache, f[2], f[0]);
                next.add(new int[] {f[0], a, c});
                next.add(new int[] {f[1], b, a});
                next.add(new int[] {f[2], c, b});
                next.add(new int[] {a, b, c});
            }
            faces = next.toArray(new int[0][]);
        }
        for (int[] f : faces) {
            for (int i = 0; i < 3; i++) {
                int u = f[i];
                int v = f[(i + 1) % 3];
                edges.add(pack(Math.min(u, v), Math.max(u, v)));
            }
        }
    }

    private static int midpoint(List<float[]> verts, Map<Long, Integer> cache, int i, int j) {
        long key = pack(Math.min(i, j), Math.max(i, j));
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        float[] a = verts.get(i);
        float[] b = verts.get(j);
        float[] m = normalize(new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
        verts.add(m);
        int index = verts.size() - 1;
        cache.put(key, index);
        return index;
    }

    private static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private static float[] normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-6f) {
            return new float[] {0f, 1f, 0f};
        }
        return new float[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0],
        };
    }

    private static float hash01(long x) {
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return (x >>> 11) / (float) (1L << 53);
    }

    // ---------------------------------------------------------------- per-frame output

    /** Growable flat float arrays, one per primitive group. Reused across frames. */
    public static final class Buffers {
        public float[] structBack = new float[2048];
        public int structBackCount;
        public float[] structFront = new float[2048];
        public int structFrontCount;
        public float[] energyBack = new float[256];
        public int energyBackCount;
        public float[] energyFront = new float[256];
        public int energyFrontCount;
        public float[] panelBack = new float[4096];
        public int panelBackCount;
        public float[] panelFront = new float[4096];
        public int panelFrontCount;

        public void reset() {
            structBackCount = 0;
            structFrontCount = 0;
            energyBackCount = 0;
            energyFrontCount = 0;
            panelBackCount = 0;
            panelFrontCount = 0;
        }

        private static float[] grow(float[] array, int needed) {
            if (needed <= array.length) {
                return array;
            }
            int size = array.length;
            while (size < needed) {
                size *= 2;
            }
            return new float[size];
        }
    }

    /**
     * Projects the swarm and splits it into back/front halves.
     *
     * @param time        seconds, drives the travelling energy arcs
     * @param reveal      0..1 assembly progress
     * @param spinDeg     rotation about the world Y axis
     * @param tiltDeg     rotation about the world X axis
     * @param cx          screen centre x, in splash coordinates
     * @param cy          screen centre y, in splash coordinates
     * @param radiusPx    desired silhouette radius, in splash coordinates
     * @param viewportH   viewport height in pixels, used to derive the camera distance
     */
    public static void build(float time, float reveal, float spinDeg, float tiltDeg,
                             float cx, float cy, float radiusPx, float viewportH,
                             Buffers out) {
        out.reset();
        if (reveal <= 0.002f || radiusPx <= 1f || viewportH <= 1f) {
            return;
        }

        float halfH = viewportH * 0.5f;
        float cot = 1f / (float) Math.tan(Math.toRadians(FOV_DEG * 0.5f));
        float dist = cot * halfH / radiusPx;

        float spin = (float) Math.toRadians(spinDeg);
        float tilt = (float) Math.toRadians(tiltDeg);
        float cs = (float) Math.cos(spin);
        float sn = (float) Math.sin(spin);
        float ct = (float) Math.cos(tilt);
        float st = (float) Math.sin(tilt);

        float[] p = new float[3];
        // hoisted out of the panel loop; build() runs once per frame on the splash thread
        float[] projected = new float[8];

        // ---- structural shell
        for (int i = 0; i < SHELL_EDGES.length; i += 2) {
            int a = SHELL_EDGES[i] * 3;
            int b = SHELL_EDGES[i + 1] * 3;
            rotate(SHELL_VERTS, a, cs, sn, ct, st, p);
            float ax = p[0];
            float ay = p[1];
            float az = p[2];
            rotate(SHELL_VERTS, b, cs, sn, ct, st, p);
            float bx = p[0];
            float by = p[1];
            float bz = p[2];

            float mz = (az + bz) * 0.5f;
            float[] target = mz >= 0f ? out.structFront : out.structBack;
            int count = mz >= 0f ? out.structFrontCount : out.structBackCount;
            target = Buffers.grow(target, (count + 1) * 4);
            int o = count * 4;
            target[o] = cx + ax * cot / (dist - az) * halfH;
            target[o + 1] = cy - ay * cot / (dist - az) * halfH;
            target[o + 2] = cx + bx * cot / (dist - bz) * halfH;
            target[o + 3] = cy - by * cot / (dist - bz) * halfH;
            if (mz >= 0f) {
                out.structFront = target;
                out.structFrontCount = count + 1;
            } else {
                out.structBack = target;
                out.structBackCount = count + 1;
            }
        }

        // ---- orbital rings, and a bright arc running along each
        for (int r = 0; r < RINGS.length; r++) {
            float[] ring = RINGS[r];
            float phase = (time * 0.13f + RING_PHASE[r]) % 1f;
            for (int i = 0; i < RING_SAMPLES; i++) {
                int i0 = i * 3;
                int i1 = (i + 1) * 3;
                rotate(ring, i0, cs, sn, ct, st, p);
                float ax = p[0];
                float ay = p[1];
                float az = p[2];
                rotate(ring, i1, cs, sn, ct, st, p);
                float bx = p[0];
                float by = p[1];
                float bz = p[2];

                boolean front = (az + bz) * 0.5f >= 0f;
                emitLine(out, front, false, cx, cy, halfH, cot, dist,
                        ax, ay, az, bx, by, bz);

                float u = i / (float) RING_SAMPLES;
                float delta = Math.abs(u - phase);
                delta = Math.min(delta, 1f - delta);
                if (delta <= ARC_HALF_DEG / 360f) {
                    emitLine(out, front, true, cx, cy, halfH, cot, dist,
                            ax, ay, az, bx, by, bz);
                }
            }
        }

        // ---- collector panels
        float spacing = (float) Math.sqrt(4.0 * Math.PI / PANELS);
        float size = spacing * PANEL_COVERAGE * 0.5f;
        for (int i = 0; i < PANELS; i++) {
            float local = SplashTimeline.clamp01((reveal - PANEL_DELAY[i] * 0.75f) / 0.25f);
            if (local <= 0.002f) {
                continue;
            }
            float eased = SplashTimeline.easeOutCubic(local);
            float radius = SplashTimeline.lerp(SPAWN_RADIUS, 1f, eased);
            float scale = size * SplashTimeline.lerp(0.35f, 1f, eased);
            float roll = PANEL_ROLL[i] * 0.6f;
            float extraSpin = (1f - eased) * 180f * PANEL_ROLL[i];

            int pi = i * 3;
            float nx = PANEL_POS[pi];
            float ny = PANEL_POS[pi + 1];
            float nz = PANEL_POS[pi + 2];
            float tx = PANEL_TAN[pi];
            float ty = PANEL_TAN[pi + 1];
            float tz = PANEL_TAN[pi + 2];
            float bx = PANEL_BIT[pi];
            float by = PANEL_BIT[pi + 1];
            float bz = PANEL_BIT[pi + 2];

            // roll the tile around its own normal so the swarm does not look stamped
            float cr = (float) Math.cos(roll);
            float sr = (float) Math.sin(roll);
            float rx = tx * cr + bx * sr;
            float ry = ty * cr + by * sr;
            float rz = tz * cr + bz * sr;
            bx = -tx * sr + bx * cr;
            by = -ty * sr + by * cr;
            bz = -tz * sr + bz * cr;
            tx = rx;
            ty = ry;
            tz = rz;

            float localSpin = spin + (float) Math.toRadians(extraSpin);
            float lcs = (float) Math.cos(localSpin);
            float lsn = (float) Math.sin(localSpin);

            float sumZ = 0f;
            for (int corner = 0; corner < 4; corner++) {
                float sx = (corner == 1 || corner == 2) ? 1f : -1f;
                float sy = (corner >= 2) ? 1f : -1f;
                float wx = nx * radius + (tx * sx + bx * sy) * scale;
                float wy = ny * radius + (ty * sx + by * sy) * scale;
                float wz = nz * radius + (tz * sx + bz * sy) * scale;

                rotate(lcs, lsn, ct, st, wx, wy, wz, p);
                float vx = p[0];
                float vy = p[1];
                float vz = p[2];
                sumZ += vz;
                float denom = dist - vz;
                projected[corner * 2] = cx + vx * cot / denom * halfH;
                projected[corner * 2 + 1] = cy - vy * cot / denom * halfH;
            }

            boolean front = sumZ >= 0f;
            float[] target = front ? out.panelFront : out.panelBack;
            int count = front ? out.panelFrontCount : out.panelBackCount;
            target = Buffers.grow(target, (count + 1) * 8);
            System.arraycopy(projected, 0, target, count * 8, 8);
            if (front) {
                out.panelFront = target;
                out.panelFrontCount = count + 1;
            } else {
                out.panelBack = target;
                out.panelBackCount = count + 1;
            }
        }
    }

    private static void emitLine(Buffers out, boolean front, boolean energy,
                                 float cx, float cy, float halfH, float cot, float dist,
                                 float ax, float ay, float az, float bx, float by, float bz) {
        float[] target;
        int count;
        if (energy) {
            target = front ? out.energyFront : out.energyBack;
            count = front ? out.energyFrontCount : out.energyBackCount;
        } else {
            target = front ? out.structFront : out.structBack;
            count = front ? out.structFrontCount : out.structBackCount;
        }
        target = Buffers.grow(target, (count + 1) * 4);
        int o = count * 4;
        target[o] = cx + ax * cot / (dist - az) * halfH;
        target[o + 1] = cy - ay * cot / (dist - az) * halfH;
        target[o + 2] = cx + bx * cot / (dist - bz) * halfH;
        target[o + 3] = cy - by * cot / (dist - bz) * halfH;
        if (energy) {
            if (front) {
                out.energyFront = target;
                out.energyFrontCount = count + 1;
            } else {
                out.energyBack = target;
                out.energyBackCount = count + 1;
            }
        } else {
            if (front) {
                out.structFront = target;
                out.structFrontCount = count + 1;
            } else {
                out.structBack = target;
                out.structBackCount = count + 1;
            }
        }
    }

    /** World rotation: first about Y, then about X. Also applies the perspective divide input. */
    private static void rotate(float[] src, int off, float cs, float sn, float ct, float st, float[] out) {
        float x = src[off];
        float y = src[off + 1];
        float z = src[off + 2];
        rotate(cs, sn, ct, st, x, y, z, out);
    }

    private static void rotate(float cs, float sn, float ct, float st,
                               float x, float y, float z, float[] out) {
        float rx = x * cs + z * sn;
        float rz = -x * sn + z * cs;
        float ry = y * ct - rz * st;
        rz = y * st + rz * ct;
        out[0] = rx;
        out[1] = ry;
        out[2] = rz;
    }
}
