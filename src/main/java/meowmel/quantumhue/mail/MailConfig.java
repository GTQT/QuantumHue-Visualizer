package meowmel.quantumhue.mail;

/**
 * 邮件系统的参数，<b>内置在 jar 里</b>，不读任何配置文件。
 *
 * <p>这里<b>没有</b>任何邮件正文或奖励：邮件内容只存在于远程邮件索引与索引列出的 JSON，
 * 由启动预取拉取到内存、世界加载时使用。
 *
 * <p>不读盘也意味着玩家改不了邮件来源；要换目录就改 {@link #remote} 里的地址。
 * 整合包版本不在这里，见 {@code meowmel.quantumhue.notice.PackVersion}。
 */
public class MailConfig {

    /** 邮件系统总开关（服务端）。关闭后不拉取、不可领取。 */
    public boolean enabled = true;

    public Remote remote = new Remote();

    public static class Remote {

        /**
         * 远程邮件索引（纯 JSON）；索引列出邮件 JSON 文件名。
         *
         * <p>内置为 GTQT 官方仓库的地址，清空表示不拉取。
         */
        public String url =
                "https://raw.githubusercontent.com/GTQT/GT-QuantumTransition/main/remote/mail/index.json";

        /** 单次 HTTP 请求超时（毫秒）。 */
        public int timeoutMs = 10000;
    }
}
