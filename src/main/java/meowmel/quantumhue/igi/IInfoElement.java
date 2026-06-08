package meowmel.quantumhue.igi;

/**
 * 动态信息提供者接口。
 * 实现此接口的类可以在每帧被重新求值，以显示实时变化的数据。
 */
@FunctionalInterface
public interface IInfoElement {
    /**
     * 获取当前时刻的值。
     *
     * @return 当前值的字符串表示
     */
    String getValue();
}
