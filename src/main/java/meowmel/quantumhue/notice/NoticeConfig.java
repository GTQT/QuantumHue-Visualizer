package meowmel.quantumhue.notice;

import java.util.ArrayList;
import java.util.List;

/**
 * 公告 / 更新检查的参数，<b>内置在 jar 里</b>，不读任何配置文件。
 *
 * <p>远程地址直接写死为 GTQT 官方仓库，要换仓库就改这里；某个地址留空表示不拉取该项。
 * 整合包版本不在这里，见 {@link VersionConfig}（{@code version.json}）与 {@link PackVersion}。
 * 页签内容有五种来源：
 * <ul>
 *   <li>{@code update} —— 对比本地版本与远程版本，并显示远程更新日志；</li>
 *   <li>{@code community} —— 拉取社群服务远程文本；</li>
 *   <li>{@code announcements} —— 读取公告索引后逐条拉取公告 JSON；</li>
 *   <li>{@code remote} —— 拉取一个纯文本 URL，失败时回退到 {@code lines}；</li>
 *   <li>{@code mail} / {@code text} —— 邮件列表；只显示 {@code lines}。</li>
 * </ul>
 */
public class NoticeConfig {

    /** 登录时检查远程版本（只影响聊天栏提示）。 */
    public boolean checkVersionOnLogin = true;

    /** 登录时自动打开公告页。 */
    public boolean showOnLogin = true;

    public Remote remote = new Remote();

    public List<Tab> tabs = defaultTabs();

    public static class Remote {
        /**
         * 版本更新公告索引（纯 JSON）。
         *
         * <p>索引列出目录内的版本 JSON 文件名；模组先比较文件名里的版本号，只下载比本地新的文件。
         * 内置为 GTQT 官方仓库的地址，清空表示不拉取。
         */
        public String versionIndexUrl =
                "https://raw.githubusercontent.com/GTQT/GT-QuantumTransition/main/remote/version/index.json";

        /** 公告索引（纯 JSON）；索引列出公告 JSON 文件名。留空表示不拉取。 */
        public String announcementIndexUrl =
                "https://raw.githubusercontent.com/GTQT/GT-QuantumTransition/main/remote/announcement/index.json";

        /** 社群服务远程文本地址。留空表示不拉取。 */
        public String communityUrl =
                "https://raw.githubusercontent.com/GTQT/GT-QuantumTransition/main/remote/community.txt";

        /**
         * 直连失败后的镜像模板，按顺序逐个尝试；空列表表示不启用。
         *
         * <p>模板里的 {@code {url}} 会替换成原始地址，没有占位符时按前缀拼接。例如
         * {@code https://ghfast.top/{url}}。镜像只是 GitHub 直连失败时的备选，直连成功时不会走它。
         */
        public List<String> mirrors = new ArrayList<>();

        /** 单次 HTTP 请求超时（毫秒）。 */
        public int timeoutMs = 10000;

        /** 请求失败后的冷却时间（毫秒），避免反复卡网络。 */
        public int failureCooldownMs = 60000;
    }

    public static class Tab {
        /** 稳定 id，仅用于日志与页面记忆。 */
        public String id = "";
        public String label = "公告";
        /** update / community / announcements / remote / mail / text。 */
        public String type = "text";
        /** type=remote 时的地址；留空则只显示空状态。 */
        public String url = "";

        public Tab() {}

        public Tab(String id, String label, String type, String url) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.url = url;
        }
    }

    // ================================================================ 默认值

    private static List<Tab> defaultTabs() {
        List<Tab> tabs = new ArrayList<>();
        // 更新日志正文只来自远程；本地不再放兜底文案
        tabs.add(new Tab("update", "更新日志", "update", ""));
        tabs.add(new Tab("community", "社群服务", "community", ""));
        return tabs;
    }
}
