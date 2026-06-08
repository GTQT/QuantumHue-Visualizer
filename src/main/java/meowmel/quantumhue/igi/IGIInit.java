package meowmel.quantumhue.igi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * IGI HUD 初始化器。
 * 从 JSON 配置文件加载 HUD 布局，首次运行自动复制默认配置。
 */
public final class IGIInit {
    private static final Logger LOGGER = LogManager.getLogger("IGI-Init");

    private IGIInit() {
    }

    /**
     * 从 JSON 配置加载 IGI HUD 布局。
     *
     * @param configDir Minecraft 配置目录（event.getModConfigurationDirectory()）
     */
    public static void registerDefaults(File configDir) {
        if (configDir == null) return;

        File configFile = new File(configDir, "quantumhue/igi.json");

        // 配置文件不存在 → 从资源中复制默认配置
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                try (InputStream in = IGIInit.class.getClassLoader()
                        .getResourceAsStream("assets/quantumhue/igi/default_igi.json");
                     OutputStream out = new FileOutputStream(configFile)) {
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        LOGGER.info("已创建默认 IGI 配置文件: {}", configFile.getAbsolutePath());
                    } else {
                        LOGGER.warn("内置默认 IGI 配置资源未找到，跳过");
                        return;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("无法创建默认 IGI 配置文件", e);
                return;
            }
        }

        // 加载 JSON 配置
        boolean loaded = JsonConfigParser.load(configFile);
        if (!loaded) {
            LOGGER.info("IGI 配置文件未加载，HUD 信息已关闭");
        }
    }
}
