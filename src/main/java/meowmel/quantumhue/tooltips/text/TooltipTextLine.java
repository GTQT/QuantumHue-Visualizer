package meowmel.quantumhue.tooltips.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一行 Tooltip 文本，以及行内由触发标签划出的特效片段。
 *
 * <p>片段下标以去掉 {@code §} 格式码后的可见文本为准，[start, end)。
 * 标签本身不会出现在 {@link #text} 中，因此不会影响换行与 Tooltip 宽度。</p>
 */
public final class TooltipTextLine {

    /** 发光。 */
    public static final int FX_GLOW = 1;
    /** 高亮条。 */
    public static final int FX_HIGHLIGHT = 2;
    /** 故障乱码。 */
    public static final int FX_GLITCH = 4;
    /** {@code [效果]} 的完整组合。 */
    public static final int FX_ALL = FX_GLOW | FX_HIGHLIGHT | FX_GLITCH;

    /** 行内一段特效区间。 */
    public static final class Span {
        public final int start;
        public final int end;
        public final int mask;

        public Span(int start, int end, int mask) {
            this.start = start;
            this.end = end;
            this.mask = mask;
        }
    }

    /** 显示文本（保留 § 格式码，不含触发标签）。 */
    public final String text;
    /** 按 start 升序、互不重叠的特效区间。 */
    public final List<Span> spans;

    private TooltipTextLine(String text, List<Span> spans) {
        this.text = text == null ? "" : text;
        this.spans = spans == null || spans.isEmpty()
                ? Collections.<Span>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(spans));
    }

    public static TooltipTextLine plain(String text) {
        return new TooltipTextLine(text, Collections.<Span>emptyList());
    }

    public static TooltipTextLine of(String text, List<Span> spans) {
        return new TooltipTextLine(text, spans);
    }

    public boolean hasEffects() {
        return !spans.isEmpty();
    }

    /** 该可见下标命中的特效位；没有则为 0。 */
    public int maskAt(int plainIndex) {
        for (Span span : spans) {
            if (span.start > plainIndex) break;
            if (plainIndex < span.end) return span.mask;
        }
        return 0;
    }

    /** 该行是否包含某类特效。 */
    public boolean hasMask(int mask) {
        for (Span span : spans) {
            if ((span.mask & mask) != 0) return true;
        }
        return false;
    }

    /**
     * 截取换行后的一段，并把特效区间平移到新行内。
     */
    public TooltipTextLine slice(int from, int to, String sliceText) {
        if (from < 0) from = 0;
        if (to < from) to = from;
        List<Span> clipped = new ArrayList<>(spans.size());
        for (Span span : spans) {
            int start = Math.max(span.start, from);
            int end = Math.min(span.end, to);
            if (end > start) {
                clipped.add(new Span(start - from, end - from, span.mask));
            }
        }
        return new TooltipTextLine(sliceText, clipped);
    }
}
