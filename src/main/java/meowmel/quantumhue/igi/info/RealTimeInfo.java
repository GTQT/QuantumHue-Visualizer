package meowmel.quantumhue.igi.info;

import meowmel.quantumhue.igi.IInfoElement;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 显示现实世界当前时间（HH:mm 格式）。
 */
public class RealTimeInfo implements IInfoElement {
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");

    @Override
    public String getValue() {
        return format.format(new Date());
    }

    @Override
    public String toString() {
        return getValue();
    }
}
