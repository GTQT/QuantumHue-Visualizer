package meowmel.quantumhue.igi;

import java.util.ArrayList;
import java.util.List;

/**
 * 一组HUD信息行，绑定到特定的锚点位置、偏移量和字体大小。
 */
public class HudGroup {
    Alignment alignment;
    int offsetX;
    int offsetY;
    float fontSize;
    final List<HudLine> lines = new ArrayList<>();
    final List<String> cachedTexts = new ArrayList<>();

    HudGroup(Alignment alignment, int offsetX, int offsetY, float fontSize) {
        this.alignment = alignment;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.fontSize = fontSize;
    }

    void addLine(HudLine line) {
        lines.add(line);
        cachedTexts.add("");
    }

    /**
     * 获取所有行，供渲染器逐段迭代。
     */
    public List<HudLine> getLines() {
        return lines;
    }

    /**
     * 刷新所有行的缓存的文本（每帧调用一次）。
     */
    void refresh() {
        for (int i = 0; i < lines.size(); i++) {
            cachedTexts.set(i, lines.get(i).build());
        }
    }
}
