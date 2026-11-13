package meowmel.quantumhue.lottery;

/**
 * 服务端返回给界面的完整抽奖状态：奖池快照、余额、提示/错误，以及本次抽奖结果。
 *
 * <p>请求视图时 {@code result} 为 {@code null}；抽奖时携带最终结果（结果里带有本次抽取的行数）。
 */
public final class LotterySnapshot {

    public final LotteryCatalog catalog;
    public final long money;
    public final LotteryResult result;
    public final String notice;
    public final String error;

    public LotterySnapshot(LotteryCatalog catalog, long money,
                           LotteryResult result, String notice, String error) {
        this.catalog = catalog == null ? LotteryCatalog.empty() : catalog;
        this.money = Math.max(0L, money);
        this.result = result;
        this.notice = notice == null ? "" : notice;
        this.error = error == null ? "" : error;
    }
}
