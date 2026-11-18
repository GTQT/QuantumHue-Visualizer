package meowmel.quantumhue.stock;

/** 一个可交易指数的静态定义。 */
public final class StockProduct {

    /** 存档与网络里使用的稳定 id。 */
    public final String id;
    /** 界面显示名。 */
    public final String name;
    /** 基准价（分）。 */
    public final long basePrice;
    /** 单根 15 秒 K 线的对数波动率。 */
    public final double barVolatility;
    /** 在综合指数里的权重；综合指数本身为 0。 */
    public final double weight;
    /** 是否由其余品种合成。 */
    public final boolean composite;

    StockProduct(String id, String name, long basePrice, double barVolatility, double weight,
                 boolean composite) {
        this.id = id;
        this.name = name;
        this.basePrice = basePrice;
        this.barVolatility = barVolatility;
        this.weight = weight;
        this.composite = composite;
    }
}
