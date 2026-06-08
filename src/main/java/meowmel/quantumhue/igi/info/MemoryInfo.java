package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;

/**
 * 显示 JVM 内存占用情况，格式："已用MB / 最大MB (百分比%)"。
 */
public class MemoryInfo implements IInfoElement {
    @Override
    public String getValue() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();

        long usedMB = used / 1024 / 1024;
        long maxMB = max / 1024 / 1024;
        int pct = (int) (used * 100 / max);

        return String.format("%dMB / %dMB (%d%%)", usedMB, maxMB, pct);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
