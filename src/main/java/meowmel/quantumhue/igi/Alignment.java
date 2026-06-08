package meowmel.quantumhue.igi;

/**
 * 9个锚点屏幕位置，用于HUD信息定位。
 */
public enum Alignment {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1),
    MIDDLE_CENTER(1, 1),
    MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    final int gridX;
    final int gridY;

    Alignment(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    /**
     * 计算文本的起始X坐标。
     *
     * @param screenWidth  屏幕宽度
     * @param textWidth    文本宽度
     * @param offsetX      X轴偏移量
     * @return 起始X坐标
     */
    public int getX(int screenWidth, int textWidth, int offsetX) {
        switch (gridX) {
            case 0: // LEFT
                return offsetX;
            case 1: // CENTER
                return (screenWidth - textWidth) / 2 + offsetX;
            case 2: // RIGHT
                return screenWidth - textWidth - offsetX;
            default:
                return offsetX;
        }
    }

    /**
     * 计算文本的起始Y坐标。
     *
     * @param screenHeight 屏幕高度
     * @param totalHeight  总文本高度
     * @param offsetY      Y轴偏移量
     * @return 起始Y坐标
     */
    public int getY(int screenHeight, int totalHeight, int offsetY) {
        switch (gridY) {
            case 0: // TOP
                return offsetY;
            case 1: // MIDDLE
                return (screenHeight - totalHeight) / 2 + offsetY;
            case 2: // BOTTOM
                return screenHeight - totalHeight - offsetY;
            default:
                return offsetY;
        }
    }
}
