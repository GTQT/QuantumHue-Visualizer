package meowmel.quantumhue.tooltips;

public class TooltipLayout {
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final int separatorY;
    public final int iconX;
    public final int iconY;

    public TooltipLayout(int x, int y, int width, int height, int separatorY, int iconX, int iconY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.separatorY = separatorY;
        this.iconX = iconX;
        this.iconY = iconY;
    }
}