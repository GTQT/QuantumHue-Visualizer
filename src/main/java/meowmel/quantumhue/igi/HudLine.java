package meowmel.quantumhue.igi;

import meowmel.quantumhue.igi.info.ItemIcon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 表示HUD中的一行文本，由静态字符串和动态信息片段组成。
 */
public class HudLine {
    private final List<Object> segments = new ArrayList<>();

    HudLine() {
    }

    void addSegment(String text) {
        segments.add(text);
    }

    void addSegment(IInfoElement element) {
        segments.add(element);
    }

    void addSegment(ItemIcon itemIcon) {
        segments.add(itemIcon);
    }

    void addSegment(Supplier<String> supplier) {
        segments.add(supplier);
    }

    /**
     * 获取所有片段，供渲染器逐段处理（文本 + 图标混合渲染）。
     */
    public List<Object> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * 解析单个片段为字符串值。
     */
    public static String resolveSegment(Object seg) {
        if (seg instanceof ItemIcon) {
            return ((ItemIcon) seg).getValue();
        } else if (seg instanceof IInfoElement) {
            return ((IInfoElement) seg).getValue();
        } else if (seg instanceof Supplier) {
            return ((Supplier<String>) seg).get();
        } else {
            return String.valueOf(seg);
        }
    }

    /**
     * 构建当前行的完整字符串（每帧调用一次）。
     *
     * @return 渲染用文本
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        for (Object seg : segments) {
            sb.append(resolveSegment(seg));
        }
        return sb.toString();
    }
}
