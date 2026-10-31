package meowmel.quantumhue;

import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.biomeInfo.BiomeInfoEventHandler;
import meowmel.quantumhue.chat.ChatClientListener;
import meowmel.quantumhue.chat.server.ChatServerListener;
import meowmel.quantumhue.blur.SimpleBlurHandler;
import meowmel.quantumhue.client.highlight.ClientHighlightHandler;
import meowmel.quantumhue.command.CommandAnnouncement;
import meowmel.quantumhue.command.CommandMarket;
import meowmel.quantumhue.command.CommandQuantumHue;
import meowmel.quantumhue.command.CommandSell;
import meowmel.quantumhue.config.PageBootstrap;
import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.createworld.CreateWorldUIClient;
import meowmel.quantumhue.createworld.command.CommandGameRuleEditor;
import meowmel.quantumhue.igi.IGI;
import meowmel.quantumhue.igi.IGIInit;
import meowmel.quantumhue.igi.HudRenderer;
import meowmel.quantumhue.igi.ThaumcraftAuraUpdater;
import meowmel.quantumhue.mail.MailEvents;
import meowmel.quantumhue.mail.MailService;
import meowmel.quantumhue.market.ShopEvents;
import meowmel.quantumhue.menu.PauseMenuEvents;
import meowmel.quantumhue.modernsplash.ModernSplashEvents;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.notice.RemoteTextFetcher;
import meowmel.quantumhue.notice.VersionState;
import meowmel.quantumhue.tooltips.AdvancedTooltipHandler;
import meowmel.quantumhue.tooltips.comparison.EquipmentCompareHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLModDisabledEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;


@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class QuantumHue {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    static {
        // 让 Java 也走 Windows 系统代理；否则浏览器能开而游戏内 HttpURLConnection 直连失败
        System.setProperty("java.net.useSystemProxies", "true");
    }

    private static File configDir;

    public static boolean GAME_LOADING_DONE = false;

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        GAME_LOADING_DONE = true;
    }

    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Tags.MOD_NAME);
        configDir = event.getModConfigurationDirectory();
        // 商店 / 公告的全部运营内容从这里读；首次运行会生成 config/quantumhue/*.json
        PageConfigs.init(configDir);
        // 加载阶段后台预取更新日志/公告/社群服务，最多尝试 3 次；进入游戏后只读内存
        PageBootstrap.startAtLoad();
        // 邮件同样在加载阶段预取到内存，世界加载时直接使用
        MailService.preloadAsync();
        VersionState.loadCached();
        PacketHandler.init();
        EquipmentCompareHandler.registerKeyBinding();
        MinecraftForge.EVENT_BUS.register(new ModernSplashEvents());
        CreateWorldUIClient.registerTabs();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandGameRuleEditor());
        event.registerServerCommand(new CommandMarket());
        event.registerServerCommand(new CommandAnnouncement());
        event.registerServerCommand(new CommandSell());
        event.registerServerCommand(new CommandQuantumHue());
    }

    @Mod.EventHandler
    public void modDisabled(FMLModDisabledEvent event) {
        RemoteTextFetcher.shutdown();
    }


    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(Tags.MOD_ID)) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        }
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new PauseMenuEvents());
        }
        MinecraftForge.EVENT_BUS.register(new SimpleBlurHandler());
        MinecraftForge.EVENT_BUS.register(new AdvancedTooltipHandler());
        MinecraftForge.EVENT_BUS.register(EquipmentCompareHandler.class);
        MinecraftForge.EVENT_BUS.register(new BiomeInfoEventHandler());
        MinecraftForge.EVENT_BUS.register(new ClientHighlightHandler());
        MinecraftForge.EVENT_BUS.register(new HudRenderer());
        MinecraftForge.EVENT_BUS.register(new ThaumcraftAuraUpdater());
        MinecraftForge.EVENT_BUS.register(new ChatClientListener());
        MinecraftForge.EVENT_BUS.register(new ChatServerListener());
        // 商店 / 公告的服务端部分：订单投放、过期清理、登录结算
        MinecraftForge.EVENT_BUS.register(new ShopEvents());
        // 邮件系统的服务端部分：远程拉取、领取校验
        MinecraftForge.EVENT_BUS.register(new MailEvents());

        IGIInit.registerDefaults(configDir);
    }
}
