package meowmel.quantumhue.mail;

import com.google.gson.Gson;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.market.ShopDelivery;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.MessageMailView;
import meowmel.quantumhue.notice.RemoteIndex;
import meowmel.quantumhue.notice.RemoteTextFetcher;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端的邮件中心：从远程邮件索引拉取邮件、校验、发放奖励。
 *
 * <p>邮件内容与奖励只来自远程目录，本地只保存索引地址；启动预取结果只放内存。
 * 所有对外方法都在服务端线程被调用，因此内部列表替换用 volatile 快照即可。
 */
public final class MailService {

    private static final Gson GSON = new Gson();
    private static final int MAX_MAILS = 200;
    /** 单次领取最多发放的物品总数（约一背包），防止配置/客户端异常造成刷物。 */
    private static final int MAX_DELIVERY_ITEMS = 2304;
    /** 启动预取邮件的时间窗口：游戏启动后最多尝试 30 秒。 */
    private static final long PRELOAD_BUDGET_MS = 30_000L;
    private static final long PRELOAD_RETRY_MS = 2_000L;
    /** 手动拉取失败后的短冷却，避免同一秒内重复点命令。 */
    private static final long ATTEMPT_COOLDOWN_MS = 60_000L;

    private static volatile List<Mail> mails = Collections.emptyList();
    private static volatile boolean loading;
    private static volatile boolean fetchedOnce;
    private static volatile long lastFetchAt;
    private static volatile long lastAttemptAt;
    private static volatile String lastError = "";
    private static volatile World activeWorld;
    /** 启动预取的邮件（纯内存，不写本地缓存）。 */
    private static volatile List<Mail> preloaded = Collections.emptyList();
    private static volatile long preloadedAt;

    /** 最近请求过邮件数据的玩家；刷新完成后只推给他们。 */
    private static final Set<UUID> LISTENERS = ConcurrentHashMap.newKeySet();

    private MailService() {}

    // ================================================================ 生命周期

    public static void onWorldLoad(World world) {
        if (world == null || world.isRemote) {
            return;
        }
        activeWorld = world;
        loading = false;
        fetchedOnce = false;
        lastFetchAt = 0L;
        lastAttemptAt = 0L;
        lastError = "";
        LISTENERS.clear();
        // 邮件只来自远程实时拉取：存档里不保留副本，这里只清空上一个世界留下的列表
        mails = Collections.emptyList();

        // 启动预取是本次启动实时拉到的结果，可以直接用；没有就只能等手动刷新
        List<Mail> boot = preloaded;
        if (!boot.isEmpty()) {
            applyMails(world, boot, preloadedAt);
        }
        if (mails.isEmpty() && lastError.isEmpty()) {
            lastError = "尚未同步邮件，请执行 /quantumhue refresh";
        }
    }

    public static void onWorldUnload(World world) {
        if (world == null || world.isRemote) {
            return;
        }
        if (activeWorld == world || activeWorld == null) {
            activeWorld = null;
            mails = Collections.emptyList();
            loading = false;
            fetchedOnce = false;
            lastFetchAt = 0L;
            lastAttemptAt = 0L;
            lastError = "";
            LISTENERS.clear();
        }
    }

    /** 配置重载或手动刷新时调用：保留旧列表，下一次请求重新拉取。 */
    public static void invalidate() {
        fetchedOnce = false;
        lastAttemptAt = 0L;
        lastError = "";
    }
    // ================================================================ 拉取

    /**
     * 启动预取：读取邮件索引、拉取每个 JSON，结果只放内存，最多尝试 3 次。
     *
     * <p>不写本地缓存；世界加载时直接使用这里的结果，进入游戏后不再自动联网。
     */
    public static void preloadAsync() {
        MailConfig config = config();
        if (config == null || !config.enabled) {
            return;
        }
        String indexUrl = trim(config.remote.url);
        if (indexUrl.isEmpty()) {
            return;
        }
        Thread thread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + PRELOAD_BUDGET_MS;
            while (System.currentTimeMillis() < deadline) {
                List<Mail> fetched = fetchMails(indexUrl, config.remote.timeoutMs);
                if (fetched != null) {
                    preloaded = Collections.unmodifiableList(fetched);
                    preloadedAt = System.currentTimeMillis();
                    List<Mail> boot = preloaded;
                    runOnServer(() -> {
                        if (activeWorld != null) {
                            applyMails(activeWorld, boot, preloadedAt);
                        }
                    });
                    return;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    Thread.sleep(Math.min(PRELOAD_RETRY_MS, remaining));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            lastError = "远程邮件同步失败";
            QuantumHue.LOGGER.warn("[Mail] 启动预取失败（已等待 30 秒）");
        }, "QuantumHue-MailBootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    /** 手动刷新：读取索引并重新拉取一次（仅由命令触发）。 */
    public static void refreshNow(World world) {
        if (world == null || world != activeWorld) {
            return;
        }
        invalidate();
        ensureFresh(world, true);
    }

    private static void ensureFresh(World world, boolean force) {
        World storage = activeWorld;
        if (storage == null) {
            return;
        }
        MailConfig config = config();
        if (config == null || !config.enabled) {
            return;
        }
        String indexUrl = trim(config.remote.url);
        if (indexUrl.isEmpty() || loading) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastAttemptAt < ATTEMPT_COOLDOWN_MS) {
            return;
        }

        loading = true;
        lastAttemptAt = now;
        CompletableFuture.supplyAsync(() -> fetchMails(indexUrl, config.remote.timeoutMs))
                .whenComplete((fetched, failure) -> {
                    loading = false;
                    if (fetched == null) {
                        failFetch("远程邮件同步失败", failure);
                        return;
                    }
                    lastError = "";
                    runOnServer(() -> applyMails(storage, fetched,
                            System.currentTimeMillis()));
                });
    }

    /** 读取索引并解析全部邮件；索引为空返回空列表，索引/全部文件失败返回 null。 */
    private static List<Mail> fetchMails(String indexUrl, int timeoutMs) {
        List<RemoteIndex.Entry> entries = RemoteIndex.list(indexUrl, timeoutMs);
        if (entries == null) {
            return null;
        }
        List<Mail> result = new ArrayList<>();
        int failed = 0;
        for (RemoteIndex.Entry entry : entries) {
            if (result.size() >= MAX_MAILS) {
                break;
            }
            String raw = RemoteTextFetcher.fetchFreshSync(entry.url, timeoutMs);
            if (raw == null || raw.isEmpty()) {
                failed++;
                continue;
            }
            try {
                Mail mail = GSON.fromJson(raw, Mail.class);
                if (mail != null && mail.normalize()) {
                    result.add(mail);
                } else {
                    failed++;
                }
            } catch (Throwable invalid) {
                failed++;
                QuantumHue.LOGGER.warn("[Mail] 邮件 JSON 解析失败: {}", entry.name, invalid);
            }
        }
        if (result.isEmpty() && !entries.isEmpty()) {
            QuantumHue.LOGGER.warn("[Mail] {} 封邮件全部失败", failed);
            return null;
        }
        return result;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void failFetch(String message, Throwable failure) {
        loading = false;
        lastError = message;
        if (failure != null) {
            QuantumHue.LOGGER.warn("[Mail] {}", message, failure);
        } else {
            QuantumHue.LOGGER.warn("[Mail] {}", message);
        }
        // 失败也要通知正在看邮件的玩家，否则界面会一直停在“正在同步”
        runOnServer(MailService::broadcastList);
    }

    /** 归一化、去重并替换列表；允许空列表表示邮箱为空。 */
    private static void applyMails(World world, List<Mail> parsed, long fetchedAt) {
        if (world == null || world != activeWorld) {
            return;
        }
        Map<String, Mail> unique = new LinkedHashMap<>();
        for (Mail mail : parsed) {
            if (mail == null || !mail.normalize()) {
                continue;
            }
            if (unique.containsKey(mail.id)) {
                QuantumHue.LOGGER.warn("[Mail] 重复的邮件 id，后者被忽略: {}", mail.id);
                continue;
            }
            unique.put(mail.id, mail);
            if (unique.size() >= MAX_MAILS) {
                QuantumHue.LOGGER.warn("[Mail] 邮件数量超过 {}，其余已忽略", MAX_MAILS);
                break;
            }
        }
        if (unique.isEmpty() && !parsed.isEmpty()) {
            lastError = "远程邮件内容为空或格式错误";
            QuantumHue.LOGGER.warn("[Mail] 远程邮件全部无效，保留旧列表");
            return;
        }

        mails = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        fetchedOnce = true;
        lastFetchAt = fetchedAt > 0L ? fetchedAt : System.currentTimeMillis();
        lastError = "";
        QuantumHue.LOGGER.info("[Mail] 已加载 {} 封远程邮件", mails.size());
        broadcastList();
    }

    private static void runOnServer(Runnable task) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            server.addScheduledTask(task);
        } else {
            task.run();
        }
    }

    // ================================================================ 查询

    public static boolean isEnabled() {
        MailConfig config = config();
        return config != null && config.enabled;
    }

    public static boolean isLoading() {
        return loading;
    }

    public static String lastError() {
        return lastError;
    }

    public static List<Mail> mails() {
        return mails;
    }

    public static Mail find(String mailId) {
        if (mailId == null || mailId.isEmpty()) {
            return null;
        }
        for (Mail mail : mails) {
            if (mail.id.equals(mailId)) {
                return mail;
            }
        }
        return null;
    }

    public static List<MailSummary> summaries(EntityPlayerMP player) {
        if (player == null || !isEnabled()) {
            return Collections.emptyList();
        }
        MailDatabase database = MailDatabase.get(storageWorld(player));
        UUID uuid = player.getUniqueID();
        long now = System.currentTimeMillis();
        List<MailSummary> result = new ArrayList<>(mails.size());
        for (Mail mail : mails) {
            MailSummary summary = new MailSummary();
            summary.id = mail.id;
            summary.title = mail.title;
            summary.sender = mail.sender;
            summary.date = mail.date;
            summary.rewardCount = mail.rewards.size();
            summary.money = mail.money;
            summary.claimed = database.isClaimed(uuid, mail.id);
            summary.started = mail.isStarted(now);
            summary.expired = mail.isExpired(now);
            result.add(summary);
        }
        return result;
    }

    public static MailDetail detail(EntityPlayerMP player, String mailId) {
        if (player == null || !isEnabled()) {
            return null;
        }
        Mail mail = find(mailId);
        if (mail == null) {
            return null;
        }
        MailDatabase database = MailDatabase.get(storageWorld(player));
        long now = System.currentTimeMillis();
        MailDetail detail = new MailDetail();
        detail.id = mail.id;
        detail.title = mail.title;
        detail.sender = mail.sender;
        detail.date = mail.date;
        detail.body = mail.parsedBody();
        detail.money = mail.money;
        detail.claimed = database.isClaimed(player.getUniqueID(), mail.id);
        detail.started = mail.isStarted(now);
        detail.expired = mail.isExpired(now);
        for (MailReward reward : mail.rewards) {
            ItemStack stack = reward.resolve();
            if (stack.isEmpty()) {
                QuantumHue.LOGGER.warn("[Mail] 邮件 {} 的奖励物品无效: {}#{}", mail.id, reward.item, reward.meta);
                continue;
            }
            detail.rewards.add(stack);
        }
        return detail;
    }

    // ================================================================ 领取

    public static ClaimResult claim(EntityPlayerMP player, String mailId) {
        if (player == null) {
            return ClaimResult.failure("玩家不存在");
        }
        if (!isEnabled()) {
            return ClaimResult.failure("邮件系统未启用");
        }
        Mail mail = find(mailId);
        if (mail == null) {
            return ClaimResult.failure("邮件不存在或已被移除");
        }
        long now = System.currentTimeMillis();
        if (!mail.isStarted(now)) {
            return ClaimResult.failure("该邮件尚未到领取时间");
        }
        if (mail.isExpired(now)) {
            return ClaimResult.failure("该邮件已过期");
        }

        MailDatabase database = MailDatabase.get(storageWorld(player));
        UUID uuid = player.getUniqueID();
        if (database.isClaimed(uuid, mailId)) {
            return ClaimResult.failure("该邮件已经领取过");
        }

        List<ItemStack> rewards = new ArrayList<>();
        int totalItems = 0;
        for (MailReward reward : mail.rewards) {
            ItemStack stack = reward.resolve();
            if (stack.isEmpty()) {
                QuantumHue.LOGGER.warn("[Mail] 邮件 {} 的奖励物品无效，已跳过: {}#{}",
                        mail.id, reward.item, reward.meta);
                continue;
            }
            totalItems += stack.getCount();
            if (totalItems > MAX_DELIVERY_ITEMS) {
                return ClaimResult.failure("该邮件奖励超过单次发放上限");
            }
            rewards.add(stack);
        }

        if (mail.money > 0L) {
            if (!EconomyBridge.available()) {
                return ClaimResult.failure("当前没有可用的货币系统，无法领取货币奖励");
            }
            long current = EconomyBridge.get(player);
            long next;
            try {
                next = Math.addExact(current, mail.money);
            } catch (ArithmeticException overflow) {
                return ClaimResult.failure("货币数量溢出，无法领取");
            }
            if (!EconomyBridge.set(player, next)) {
                return ClaimResult.failure("货币发放失败，请稍后再试");
            }
        }
        for (ItemStack stack : rewards) {
            ShopDelivery.give(player, stack, stack.getCount());
        }
        database.markClaimed(uuid, mailId);

        StringBuilder message = new StringBuilder("§a✓ 已领取邮件：§f").append(mail.title);
        if (!rewards.isEmpty() || mail.money > 0L) {
            message.append("§a（");
            if (!rewards.isEmpty()) {
                message.append("物品 ").append(rewards.size()).append(" 种");
            }
            if (mail.money > 0L) {
                if (!rewards.isEmpty()) {
                    message.append("，");
                }
                message.append("货币 +").append(mail.money);
            }
            message.append("）");
        }
        return ClaimResult.success(message.toString());
    }

    /** 领取结果；message 直接用于聊天栏与界面内提示。 */
    public static final class ClaimResult {
        public final boolean success;
        public final String message;

        private ClaimResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ClaimResult success(String message) {
            return new ClaimResult(true, message);
        }

        public static ClaimResult failure(String message) {
            return new ClaimResult(false, "§c" + message);
        }
    }

    // ================================================================ 推送

    /** 玩家请求邮件列表后登记，刷新完成时只推给这些人。 */
    public static void markListening(EntityPlayerMP player) {
        if (player != null) {
            LISTENERS.add(player.getUniqueID());
        }
    }

    public static void forget(UUID player) {
        if (player != null) {
            LISTENERS.remove(player);
        }
    }

    /** 远程刷新完成后，把新列表推给正在看邮件的玩家。 */
    private static void broadcastList() {
        if (LISTENERS.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (UUID uuid : new ArrayList<>(LISTENERS)) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
            if (player == null) {
                LISTENERS.remove(uuid);
                continue;
            }
            PacketHandler.sendTo(MessageMailView.list(summaries(player), loading, isEnabled(),
                    lastError), player);
        }
    }

    /** 发送一条聊天提示（领取结果等）。 */
    public static void notifyPlayer(EntityPlayerMP player, String message) {
        if (player != null && message != null) {
            player.sendMessage(new TextComponentString(message));
        }
    }

    private static MailConfig config() {
        try {
            return PageConfigs.mail();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 邮件存档归主世界所有；玩家在别的维度时也能读写同一份数据。 */
    private static World storageWorld(EntityPlayerMP player) {
        World active = activeWorld;
        return active != null ? active : player.world;
    }
}
