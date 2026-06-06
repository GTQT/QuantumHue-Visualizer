package meowmel.quantumhue;

import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.biomeInfo.BiomeInfoEventHandler;
import meowmel.quantumhue.blur.SimpleBlurHandler;
import meowmel.quantumhue.client.highlight.ClientHighlightHandler;
import meowmel.quantumhue.command.CommandWiki;
import meowmel.quantumhue.modernsplash.ModernSplashEvents;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.tooltips.AdvancedTooltipHandler;
import meowmel.quantumhue.wiki.WikiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.meowmel.quantumhue.Tags.MOD_ID;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class QuantumHue {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

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
        PacketHandler.init();
        MinecraftForge.EVENT_BUS.register(new ModernSplashEvents());
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandWiki());
    }


    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(MOD_ID)) {
            ConfigManager.sync(MOD_ID, Config.Type.INSTANCE);
        }
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new SimpleBlurHandler());
        MinecraftForge.EVENT_BUS.register(new AdvancedTooltipHandler());
        MinecraftForge.EVENT_BUS.register(new BiomeInfoEventHandler());
        MinecraftForge.EVENT_BUS.register(new ClientHighlightHandler());
    }
}
