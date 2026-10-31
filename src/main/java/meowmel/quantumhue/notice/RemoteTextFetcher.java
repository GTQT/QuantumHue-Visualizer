package meowmel.quantumhue.notice;

import meowmel.quantumhue.config.PageConfigs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 远程纯文本拉取器。
 *
 * <p><b>不做本地缓存</b>：每次调用都真正发起请求，结果只交给 {@code PageBootstrap} /
 * {@code MailService} 放到内存快照里。请求在独立线程池执行，界面线程不碰网络；
 * 直连失败后按 {@code NoticeConfig.remote.mirrors} 逐个尝试镜像。
 */
public final class RemoteTextFetcher {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Remote");
    /** 单次响应最大字符数；防止异常地址把内存打满。 */
    private static final int MAX_CHARS = 1_000_000;
    /** 同一地址的失败详情最多每 15 秒打一次，避免 30 秒重试把日志刷爆。 */
    private static final ConcurrentHashMap<String, Long> LAST_WARN = new ConcurrentHashMap<>();

    private static volatile ExecutorService executor;

    static {
        // Windows 上让 Java 尝试读取系统代理（Clash/V2Ray 的系统代理模式）
        System.setProperty("java.net.useSystemProxies", "true");
    }

    private RemoteTextFetcher() {}

    /** 异步拉取。 */
    public static CompletableFuture<String> fetchAsync(String url) {
        return fetchAsync(url, -1);
    }

    /** 异步拉取，允许调用方覆盖超时时间。 */
    public static CompletableFuture<String> fetchAsync(String url, int timeoutMs) {
        String target = key(url);
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> request(target, timeoutMs), executor());
    }

    /** 同步拉取（预加载/命令使用），失败返回 {@code null}。 */
    public static String fetchSync(String url) {
        return fetchSync(url, -1);
    }

    /** 同步拉取，允许覆盖超时时间。 */
    public static String fetchSync(String url, int timeoutMs) {
        String target = key(url);
        return target == null ? null : request(target, timeoutMs);
    }

    /** 与 {@link #fetchSync} 相同：没有缓存可绕过。 */
    public static String fetchFreshSync(String url, int timeoutMs) {
        return fetchSync(url, timeoutMs);
    }

    /** 兼容旧调用：没有缓存。 */
    public static boolean hasCached(String url) {
        return false;
    }

    /** 兼容旧调用：没有缓存。 */
    public static void invalidate(String url) {
        // no-op
    }

    /** 兼容旧调用：没有缓存。 */
    public static void invalidateAll() {
        // no-op
    }

    public static void shutdown() {
        ExecutorService current = executor;
        if (current != null && !current.isShutdown()) {
            current.shutdownNow();
        }
    }

    // ================================================================ 内部实现

    /** 先尝试系统/环境代理，再直连；都失败才按配置的镜像逐个重试。 */
    private static String request(String url, int timeoutMs) {
        String result = requestWithProxies(url, timeoutMs);
        if (result != null) {
            return result;
        }
        for (String mirror : mirrorUrls(url)) {
            LOGGER.info("[Remote] 直连失败，改用镜像: {}", mirror);
            String mirrored = requestWithProxies(mirror, timeoutMs);
            if (mirrored != null) {
                LOGGER.info("[Remote] 镜像可用: {}", mirror);
                return mirrored;
            }
        }
        return null;
    }

    /** 先尝试系统/环境代理，再直连；直连失败后才尝试本机常见代理端口。 */
    private static String requestWithProxies(String url, int timeoutMs) {
        List<Proxy> proxies = proxiesFor(url);
        for (Proxy proxy : proxies) {
            LOGGER.info("[Remote] 尝试代理 {}: {}", proxy.address(), url);
            int proxyTimeout = timeoutMs > 0 ? Math.min(timeoutMs, 6000) : 6000;
            String value = requestOnce(url, proxyTimeout, proxy);
            if (value != null) {
                LOGGER.info("[Remote] 代理可用: {}", proxy.address());
                return value;
            }
        }
        String direct = requestOnce(url, timeoutMs, Proxy.NO_PROXY);
        if (direct != null) {
            return direct;
        }
        // 直连失败才探测本机代理，避免本机恰好开着别的服务时拖慢正常请求
        for (Proxy proxy : localProxyCandidates()) {
            LOGGER.info("[Remote] 直连失败，尝试本机代理 {}: {}", proxy.address(), url);
            int proxyTimeout = timeoutMs > 0 ? Math.min(timeoutMs, 6000) : 6000;
            String value = requestOnce(url, proxyTimeout, proxy);
            if (value != null) {
                LOGGER.info("[Remote] 本机代理可用: {}", proxy.address());
                return value;
            }
        }
        return null;
    }

    /** 本机常见代理端口；只连本机，不会扫描局域网。 */
    private static List<Proxy> localProxyCandidates() {
        List<Proxy> proxies = new ArrayList<>();
        addLocalProxy(proxies, Proxy.Type.HTTP, 7890);
        addLocalProxy(proxies, Proxy.Type.HTTP, 7897);
        addLocalProxy(proxies, Proxy.Type.HTTP, 10809);
        addLocalProxy(proxies, Proxy.Type.HTTP, 8080);
        addLocalProxy(proxies, Proxy.Type.SOCKS, 7891);
        addLocalProxy(proxies, Proxy.Type.SOCKS, 1080);
        return proxies;
    }

    private static void addLocalProxy(List<Proxy> proxies, Proxy.Type type, int port) {
        if (!localPortOpen(port)) {
            return;
        }
        proxies.add(new Proxy(type, new InetSocketAddress("127.0.0.1", port)));
    }

    private static boolean localPortOpen(int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Throwable closed) {
            return false;
        }
    }

    /** 收集环境变量、Java 代理属性与系统 ProxySelector 给出的代理。 */
    private static List<Proxy> proxiesFor(String url) {
        Set<Proxy> proxies = new LinkedHashSet<>();
        boolean https = url != null && url.startsWith("https://");
        String env = firstNonEmpty(
                https ? "HTTPS_PROXY" : "HTTP_PROXY",
                https ? "https_proxy" : "http_proxy",
                "ALL_PROXY", "all_proxy");
        Proxy fromEnv = parseProxy(env);
        if (fromEnv != null) {
            proxies.add(fromEnv);
        }
        String scheme = https ? "https" : "http";
        Proxy fromProperty = parseProxy(hostPort(
                System.getProperty(scheme + ".proxyHost"),
                System.getProperty(scheme + ".proxyPort")));
        if (fromProperty != null) {
            proxies.add(fromProperty);
        }
        try {
            ProxySelector selector = ProxySelector.getDefault();
            if (selector != null && url != null) {
                for (Proxy proxy : selector.select(new URI(url))) {
                    if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
                        proxies.add(proxy);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 系统代理不可用时继续直连
        }
        return new ArrayList<>(proxies);
    }

    private static String firstNonEmpty(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String hostPort(String host, String port) {
        if (host == null || host.trim().isEmpty()) {
            return "";
        }
        return host.trim() + ":" + (port == null || port.trim().isEmpty() ? "8080" : port.trim());
    }

    private static Proxy parseProxy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        Proxy.Type type = Proxy.Type.HTTP;
        if (text.toLowerCase(java.util.Locale.ROOT).startsWith("socks")) {
            type = Proxy.Type.SOCKS;
        }
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            text = text.substring(scheme + 3);
        }
        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(0, slash);
        }
        int at = text.lastIndexOf('@');
        if (at >= 0) {
            text = text.substring(at + 1);
        }
        int colon = text.lastIndexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            return null;
        }
        String host = text.substring(0, colon);
        try {
            int port = Integer.parseInt(text.substring(colon + 1));
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Throwable invalid) {
            return null;
        }
    }

    /** 按配置展开镜像模板；未配置时返回空表。 */
    private static List<String> mirrorUrls(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return Collections.emptyList();
        }
        List<String> mirrors;
        try {
            mirrors = PageConfigs.notice().remote.mirrors;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        if (mirrors == null || mirrors.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>(mirrors.size());
        for (String mirror : mirrors) {
            if (mirror == null || mirror.trim().isEmpty()) {
                continue;
            }
            String template = mirror.trim();
            urls.add(template.contains("{url}") ? template.replace("{url}", url) : template + url);
        }
        return urls;
    }

    private static String requestOnce(String url, int timeoutMs, Proxy proxy) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection(
                    proxy == null ? Proxy.NO_PROXY : proxy);
            connection.setRequestMethod("GET");
            int timeout = timeoutMs > 0 ? timeoutMs : timeout();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", "QuantumHue/" + com.meowmel.quantumhue.Tags.VERSION);
            int code = connection.getResponseCode();
            if (code != 200) {
                warnFailure(url, "HTTP " + code);
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
                if (builder.length() > MAX_CHARS) {
                    LOGGER.warn("[Remote] 响应超过 {} 字符，已丢弃: {}", MAX_CHARS, url);
                    return null;
                }
            }
            String text = builder.toString().trim();
            LAST_WARN.remove(url);
            return text.isEmpty() ? null : text;
        } catch (Exception failure) {
            warnFailure(url, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // 关闭失败不影响结果
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 失败详情（异常类型/HTTP 状态）用于排查浏览器能开而 Java 不能开的原因。 */
    private static void warnFailure(String url, String detail) {
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(url);
        if (last == null || now - last >= 15_000L) {
            LOGGER.warn("[Remote] 拉取失败 {}: {}", url, detail);
            LAST_WARN.put(url, now);
        } else {
            LOGGER.debug("[Remote] 仍然失败 {}: {}", url, detail);
        }
    }

    private static int timeout() {
        try {
            return PageConfigs.notice().remote.timeoutMs;
        } catch (Throwable ignored) {
            return 10000;
        }
    }

    private static String key(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ExecutorService executor() {
        ExecutorService current = executor;
        if (current == null || current.isShutdown() || current.isTerminated()) {
            synchronized (RemoteTextFetcher.class) {
                current = executor;
                if (current == null || current.isShutdown() || current.isTerminated()) {
                    executor = current = Executors.newFixedThreadPool(3, runnable -> {
                        Thread thread = new Thread(runnable, "QuantumHue-Remote");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
            }
        }
        return current;
    }
}
