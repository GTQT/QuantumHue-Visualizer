package meowmel.quantumhue.igi;

import java.util.function.Supplier;

/**
 * HUD信息构建器。
 * 用于链式调用构建一个HUD组。
 */
public class InfoBuilder {
    private Alignment alignment = Alignment.TOP_LEFT;
    private int offsetX = 2;
    private int offsetY = 2;
    private float fontSize = -1; // -1 表示使用默认字体大小
    private final HudGroup group;

    InfoBuilder() {
        this.group = new HudGroup(alignment, offsetX, offsetY, fontSize);
    }

    /**
     * 设置锚点位置。
     */
    public InfoBuilder pos(Alignment alignment) {
        this.alignment = alignment;
        group.alignment = alignment;
        return this;
    }

    /**
     * 设置屏幕偏移量（像素）。
     */
    public InfoBuilder offset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
        group.offsetX = x;
        group.offsetY = y;
        return this;
    }

    /**
     * 设置字体大小。
     * 默认为-1，表示使用Minecraft默认字体大小。
     * 例如 18 表示放大到18像素。
     */
    public InfoBuilder size(float fontSize) {
        this.fontSize = fontSize;
        group.fontSize = fontSize;
        return this;
    }

    /**
     * 添加一行纯文本信息。
     *
     * @param text 静态文本
     */
    public InfoBuilder info(String text) {
        HudLine line = new HudLine();
        line.addSegment(text);
        group.addLine(line);
        return this;
    }

    /**
     * 添加一行由静态文本和动态元素组成的信息。
     * 例如：.info("时间: ", new TimeInfo(), " 天气: ", new WeatherInfo())
     *
     * @param parts 交替的字符串和 IInfoElement
     */
    public InfoBuilder info(Object... parts) {
        HudLine line = new HudLine();
        for (Object part : parts) {
            if (part instanceof IInfoElement) {
                line.addSegment((IInfoElement) part);
            } else if (part instanceof Supplier) {
                @SuppressWarnings("unchecked")
                Supplier<String> supplier = (Supplier<String>) part;
                line.addSegment(supplier);
            } else {
                line.addSegment(String.valueOf(part));
            }
        }
        group.addLine(line);
        return this;
    }

    /**
     * 完成构建并注册此HUD组。
     *
     * @return 构建好的 HudGroup
     */
    public HudGroup builder() {
        IGI.registerGroup(group);
        return group;
    }
}
