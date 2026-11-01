package meowmel.quantumhue.config;

import com.google.gson.Gson;
import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.notice.*;
import meowmel.quantumhue.notice.client.AnnouncementStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 加载阶段的远程信息预取。
 *
 * <p>远程目录通过 {@code index.json} 列举；版本更新公告再按版本号自行比较，
 * 只拉取不低于本地版本的条目（当前版本也会显示）。
 *
 * <p>结果只保存在内存中（{@link UpdateFeedStore} / {@link CommunityStore} / {@link AnnouncementStore}），
 * 不写本地缓存。进入游戏后页面只读内存，除非执行 {@code /quantumhue refresh}，不会再次联网。
 */
public final class PageBootstrap {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Bootstrap");
    private static final Gson GSON = new Gson();
    /**
     * 一次预取的总时间上限。
     *
     * <p>游戏启动时先读当前版本，再给远程访问最多 30 秒：单次请求最多 10 秒，
     * 失败后每 2 秒重试一次，直到预算用完或三个来源都成功。
     */
    private static final long TOTAL_BUDGET_MS = 30_000L;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final int MAX_VERSION_FILES = 20;
    private static final int MAX_ANNOUNCEMENTS = 100;

    /**
     * 三个数据源与各文件之间并行拉取，避免先起跑的版本目录把总预算吃干。
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "QuantumHue-PageBootstrap");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static volatile int revision;

    private PageBootstrap() {
    }

    /**
     * 游戏加载时调用一次；重复调用不会重复启动。
     */
    public static void startAtLoad() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        launch();
    }

    /**
     * 手动刷新：后台重新预取，完成后 {@link #revision()} 自增。
     */
    public static void refreshAsync() {
        launch();
    }

    /**
     * 数据版本号；界面用它判断是否需要重绘。
     */
    public static int revision() {
        return revision;
    }

    private static void launch() {
        if (!RUNNING.compareAndSet(false, true)) {
            PENDING.set(true);
            return;
        }
        Thread thread = new Thread(PageBootstrap::run, "QuantumHue-PageBootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
        boolean updateDone = false;
        boolean communityDone = false;
        boolean announcementsDone = false;
        try {
            while (System.currentTimeMillis() < deadline) {
                CompletableFuture<Boolean> update = updateDone
                        ? CompletableFuture.completedFuture(true)
                        : submit(() -> fetchUpdate(deadline));
                CompletableFuture<Boolean> community = communityDone
                        ? CompletableFuture.completedFuture(true)
                        : submit(() -> fetchCommunity(deadline));
                CompletableFuture<Boolean> announcements = announcementsDone
                        ? CompletableFuture.completedFuture(true)
                        : submit(() -> fetchAnnouncements(deadline));
                long remaining = Math.max(1L, deadline - System.currentTimeMillis());
                try {
                    CompletableFuture.allOf(update, community, announcements)
                            .get(remaining, TimeUnit.MILLISECONDS);
                } catch (TimeoutException timeout) {
                    LOGGER.debug("[Pages] 本轮预取超时，等待下一轮");
                } catch (Throwable failure) {
                    LOGGER.debug("[Pages] 本轮预取失败，等待下一轮", failure);
                }
                updateDone = Boolean.TRUE.equals(update.getNow(false));
                communityDone = Boolean.TRUE.equals(community.getNow(false));
                announcementsDone = Boolean.TRUE.equals(announcements.getNow(false));
                if (updateDone && communityDone && announcementsDone) {
                    break;
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0L) {
                    break;
                }
                LOGGER.debug("[Pages] 预取不完整（版本={}，社群={}，公告={}），稍后重试",
                        updateDone, communityDone, announcementsDone);
                sleep(Math.min(RETRY_DELAY_MS, left));
            }
            if (!updateDone || !communityDone || !announcementsDone) {
                LOGGER.warn("[Pages] 30 秒内未完成预取（版本={}，社群={}，公告={}）",
                        updateDone, communityDone, announcementsDone);
            }
        } catch (Throwable failure) {
            LOGGER.warn("[Pages] 启动预取失败", failure);
        } finally {
            RUNNING.set(false);
            revision++;
            if (PENDING.compareAndSet(true, false)) {
                launch();
            }
        }
    }

    /**
     * 并行跑一轮三个来源；只等到本轮剩余预算。
     */
    private static CompletableFuture<Boolean> submit(Callable<Boolean> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Throwable failure) {
                LOGGER.warn("[Pages] 预取任务失败", failure);
                return false;
            }
        }, EXECUTOR);
    }

    // ================================================================ 版本更新公告

    private static boolean fetchUpdate(long deadline) {
        String indexUrl = trim(QuantumHueConfig.notice.versionIndexUrl);
        if (indexUrl.isEmpty()) {
            return true;
        }
        List<RemoteIndex.Entry> entries = RemoteIndex.list(indexUrl, timeoutMs());
        boolean indexOk = entries != null;
        if (entries == null) {
            // 索引临时不可用时仍然直接探测“当前版本.json”，避免整页空白
            LOGGER.debug("[Pages] 版本索引读取失败，尝试直接拉取当前版本公告: {}", indexUrl);
            entries = new ArrayList<>();
        }

        // 当前版本 = 整合包版本：装了 GTQTCore 取它的 VERSION，否则取经 API 注册的版本配置；都没有就不过滤
        String local = PackVersion.current();
        List<RemoteIndex.Entry> candidates = new ArrayList<>();
        String highest = "";
        for (RemoteIndex.Entry entry : entries) {
            String fromName = VersionUtil.versionFromName(entry.name);
            if (!fromName.isEmpty()) {
                if (highest.isEmpty() || VersionUtil.compare(fromName, highest) > 0) {
                    highest = fromName;
                }
                // 文件名已经是版本号：旧版本不需要下载，当前版本要显示更新公告
                if (VersionUtil.compare(fromName, local) < 0) {
                    continue;
                }
            }
            candidates.add(entry);
            if (candidates.size() >= MAX_VERSION_FILES) {
                break;
            }
        }

        boolean complete = true;
        List<UpdateFeed.VersionLog> logs = new ArrayList<>();
        // 版本文件并行下载：串行时每个文件都要重新建连，很容易超出总预算
        List<CompletableFuture<String>> requests = new ArrayList<>(candidates.size());
        for (RemoteIndex.Entry entry : candidates) {
            requests.add(CompletableFuture.supplyAsync(() -> fetch(entry.url, deadline), EXECUTOR));
        }
        for (int i = 0; i < candidates.size(); i++) {
            RemoteIndex.Entry entry = candidates.get(i);
            String raw = requests.get(i).join();
            if (raw == null) {
                complete = false;
                LOGGER.warn("[Pages] 版本文件拉取失败: {} ({})", entry.name, entry.url);
                continue;
            }
            VersionAnnouncement announcement;
            try {
                announcement = GSON.fromJson(raw, VersionAnnouncement.class);
            } catch (Throwable invalid) {
                complete = false;
                LOGGER.warn("[Pages] 版本公告解析失败: {}", entry.name, invalid);
                continue;
            }
            if (announcement == null || !announcement.normalize()) {
                complete = false;
                LOGGER.warn("[Pages] 版本公告缺少有效版本号: {}", entry.name);
                continue;
            }
            if (VersionUtil.compare(announcement.version, local) < 0) {
                // 文件名无法判断时以 JSON 里的版本为准，旧版本仍然丢弃
                continue;
            }
            if (highest.isEmpty() || VersionUtil.compare(announcement.version, highest) > 0) {
                highest = announcement.version;
            }
            UpdateFeed.VersionLog log = new UpdateFeed.VersionLog();
            log.version = announcement.version;
            log.date = announcement.date;
            log.lines = announcement.parsedBody();
            logs.add(log);
        }

        // 索引可能漏了当前版本，或刚更新还没进索引：直接按当前版本号补拉一次
        boolean hasCurrent = false;
        for (UpdateFeed.VersionLog log : logs) {
            if (log.version.equals(local)) {
                hasCurrent = true;
                break;
            }
        }
        if (!local.isEmpty() && !hasCurrent) {
            // 刚推送的索引/文件可能还有 CDN 延迟，稍等再直连一次当前版本
            sleep(600L);
            String fallbackUrl = sibling(indexUrl, local + ".json");
            String raw = fetch(fallbackUrl, deadline);
            if (raw != null) {
                try {
                    VersionAnnouncement announcement = GSON.fromJson(raw, VersionAnnouncement.class);
                    if (announcement != null && announcement.normalize()
                            && VersionUtil.compare(announcement.version, local) >= 0) {
                        UpdateFeed.VersionLog log = new UpdateFeed.VersionLog();
                        log.version = announcement.version;
                        log.date = announcement.date;
                        log.lines = announcement.parsedBody();
                        logs.add(log);
                        if (highest.isEmpty()
                                || VersionUtil.compare(announcement.version, highest) > 0) {
                            highest = announcement.version;
                        }
                    }
                } catch (Throwable invalid) {
                    LOGGER.warn("[Pages] 当前版本公告直连解析失败: {}", fallbackUrl, invalid);
                }
            }
        }

        logs.sort((a, b) -> VersionUtil.compare(b.version, a.version));

        if (!indexOk && logs.isEmpty()) {
            // 索引失败且直连当前版本也失败：不要把“本地版本”伪装成远程最新
            LOGGER.debug("[Pages] 版本索引与当前版本公告均不可用，保留旧数据");
            return false;
        }

        UpdateFeed feed = new UpdateFeed();
        feed.localVersion = local;
        feed.latest = highest.isEmpty() ? local : highest;
        feed.fetchedAt = System.currentTimeMillis();
        feed.logs = logs;
        UpdateFeedStore.set(feed);
        LOGGER.info("[Pages] 版本索引 {}，本地 {}，候选 {}，日志 {}，最新 {}",
                indexOk ? "正常" : "失败", local, candidates.size(), logs.size(), feed.latest);
        // 索引失败但直连当前版本成功时也算有可用数据，不必再等满 30 秒
        return complete || !logs.isEmpty();
    }

    /**
     * 索引地址同目录下的文件地址，例如 .../version/index.json + 1.9.0.json。
     */
    private static String sibling(String indexUrl, String fileName) {
        try {
            return new java.net.URL(new java.net.URL(indexUrl), fileName).toString();
        } catch (Throwable invalid) {
            return "";
        }
    }

    // ================================================================ 社群服务

    private static boolean fetchCommunity(long deadline) {
        String url = trim(QuantumHueConfig.notice.communityUrl);
        if (url.isEmpty()) {
            return true;
        }
        String raw = fetch(url, deadline);
        if (raw == null) {
            return false;
        }
        CommunityStore.set(NoticeText.parse(raw));
        return true;
    }

    // ================================================================ 公告

    private static boolean fetchAnnouncements(long deadline) {
        String indexUrl = trim(QuantumHueConfig.notice.announcementIndexUrl);
        if (indexUrl.isEmpty()) {
            return true;
        }
        List<RemoteIndex.Entry> entries = RemoteIndex.list(indexUrl, timeoutMs());
        if (entries == null) {
            return false;
        }
        List<RemoteIndex.Entry> limited = entries.size() > MAX_ANNOUNCEMENTS
                ? new ArrayList<>(entries.subList(0, MAX_ANNOUNCEMENTS))
                : entries;
        List<CompletableFuture<String>> requests = new ArrayList<>(limited.size());
        for (RemoteIndex.Entry entry : limited) {
            requests.add(CompletableFuture.supplyAsync(() -> fetch(entry.url, deadline), EXECUTOR));
        }
        boolean complete = true;
        List<Announcement> items = new ArrayList<>();
        for (int i = 0; i < limited.size(); i++) {
            RemoteIndex.Entry entry = limited.get(i);
            String raw = requests.get(i).join();
            if (raw == null) {
                complete = false;
                continue;
            }
            try {
                Announcement announcement = GSON.fromJson(raw, Announcement.class);
                if (announcement != null && announcement.normalize()) {
                    items.add(announcement);
                } else {
                    complete = false;
                }
            } catch (Throwable invalid) {
                complete = false;
                LOGGER.warn("[Pages] 公告 JSON 解析失败: {}", entry.name, invalid);
            }
        }
        AnnouncementStore.set(items);
        return complete;
    }

    // ================================================================ 工具

    private static String fetch(String url, long deadline) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        if (System.currentTimeMillis() > deadline) {
            return null;
        }
        String raw = RemoteTextFetcher.fetchFreshSync(url.trim(), timeoutMs());
        if (raw == null) {
            LOGGER.debug("[Pages] 预取失败: {}", url);
        }
        return raw;
    }

    /**
     * 单次请求超时：只认 {@code quantumhue.cfg} 的 {@code Notice.timeoutMs}。
     */
    private static int timeoutMs() {
        return QuantumHueConfig.notice.timeoutMs;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
