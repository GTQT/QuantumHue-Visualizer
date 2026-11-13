package meowmel.quantumhue.lottery.client;

import meowmel.quantumhue.lottery.LotteryCatalog;
import meowmel.quantumhue.lottery.LotteryResult;

/**
 * 客户端只读的抽奖状态。
 *
 * <p>客户端从不读配置，也不自行决定结果；奖池、余额和最终结果都由服务端推送。
 * 保存最后一次抽奖结果及其序号，界面据此只播一次开奖动画。
 */
public final class LotteryClientStore {

    private static volatile LotteryCatalog catalog = LotteryCatalog.empty();
    private static volatile long playerMoney;
    private static volatile String notice = "";
    private static volatile String error = "";
    private static volatile LotteryResult result;
    private static volatile long resultSeq;
    /** 只在“抽奖有了明确回应”（结果或错误）时自增；用于避免被普通刷新响应误判。 */
    private static volatile long spinReplySeq;
    /** 当前选中的奖池 id；收到新快照时若已不存在会回退到默认奖池。 */
    private static volatile String selectedPoolId = "";

    private LotteryClientStore() {}

    public static void apply(LotteryCatalog newCatalog, long money,
                             String newNotice, String newError, LotteryResult newResult) {
        if (newCatalog != null) {
            catalog = newCatalog;
            if (selectedPoolId == null || selectedPoolId.trim().isEmpty()
                    || newCatalog.pool(selectedPoolId) == null) {
                selectedPoolId = newCatalog.defaultPoolId();
            }
        }
        playerMoney = Math.max(0L, money);
        notice = newNotice == null ? "" : newNotice;
        error = newError == null ? "" : newError;
        if (newResult != null) {
            result = newResult;
            resultSeq++;
        }
        if (newResult != null || (newError != null && !newError.isEmpty())) {
            spinReplySeq++;
        }
    }

    public static LotteryCatalog catalog() {
        return catalog;
    }

    public static String selectedPoolId() {
        return selectedPoolId;
    }

    /** 当前选中的奖池；快照没有奖池时返回 {@code null}。 */
    public static LotteryCatalog.Pool selectedPool() {
        LotteryCatalog.Pool pool = catalog.pool(selectedPoolId);
        return pool == null ? catalog.defaultPool() : pool;
    }

    /** 切换当前奖池；id 不存在时回退到默认奖池。 */
    public static void selectPool(String poolId) {
        LotteryCatalog.Pool pool = catalog.pool(poolId);
        selectedPoolId = pool == null ? catalog.defaultPoolId() : pool.id;
        // 上一个奖池的失败提示不应跟到新奖池下面。
        error = "";
    }

    public static long playerMoney() {
        return playerMoney;
    }

    public static String notice() {
        return notice;
    }

    public static String error() {
        return error;
    }

    public static LotteryResult result() {
        return result;
    }

    /** 每收到一次新结果自增；界面用它判断是否要重新播动画。 */
    public static long resultSeq() {
        return resultSeq;
    }

    /** 每收到一次抽奖结果或抽奖错误自增；普通页面刷新不会增加。 */
    public static long spinReplySeq() {
        return spinReplySeq;
    }
}
