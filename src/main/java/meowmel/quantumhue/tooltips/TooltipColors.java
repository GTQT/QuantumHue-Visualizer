package meowmel.quantumhue.tooltips;

/**
 * Tooltip 调色板：面板底色、描边、强调色。
 *
 * <p>从单色平涂升级为三层：{@code backgroundTop/Bottom} 供四角渐变使用，
 * {@code borderStart/End} 保持原有描边的双色语义，{@code accent} 给斜面高光
 * 与稀有度微光取色。旧的 3 参数构造器仍然可用，会自动推导另外几个颜色。</p>
 */
public class TooltipColors {

    public final int background;
    public final int borderStart;
    public final int borderEnd;

    /** 四角渐变的上/下底色。 */
    public final int backgroundTop;
    public final int backgroundBottom;

    /** 斜面高光与微光的强调色。 */
    public final int accent;

    /** 是否绘制环绕微光（EPIC 稀有度）。 */
    public final boolean shimmer;

    public TooltipColors(int backgroundTop, int backgroundBottom, int borderStart, int borderEnd,
                         int accent, boolean shimmer) {
        this.background = mix(backgroundTop, backgroundBottom, 0.5F);
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.borderStart = borderStart;
        this.borderEnd = borderEnd;
        this.accent = accent;
        this.shimmer = shimmer;
    }

    public TooltipColors(int background, int borderStart, int borderEnd) {
        this(
                mix(background, borderStart, 0.10F),
                mix(background, 0xFF000000, 0.22F),
                borderStart,
                borderEnd,
                mix(borderStart, 0xFFFFFFFF, 0.45F),
                false
        );
    }

    /** 线性混色（含 alpha）；t=0 取 from，t=1 取 to。 */
    public static int mix(int from, int to, float t) {
        float f = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        int aa = (from >>> 24) & 0xFF;
        int ar = (from >> 16) & 0xFF;
        int ag = (from >> 8) & 0xFF;
        int ab = from & 0xFF;
        int ba = (to >>> 24) & 0xFF;
        int br = (to >> 16) & 0xFF;
        int bg = (to >> 8) & 0xFF;
        int bb = to & 0xFF;

        int a = Math.round(aa + (ba - aa) * f);
        int r = Math.round(ar + (br - ar) * f);
        int g = Math.round(ag + (bg - ag) * f);
        int b = Math.round(ab + (bb - ab) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
