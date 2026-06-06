package meowmel.quantumhue.modernsplash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.lang.management.ManagementFactory;

public class ModernSplashEvents {

    public static long doneTime = 0;

    boolean triggered = false;
    boolean trueFullscreen;

    long startupTime;
    boolean hasBeenMainMenu = false;

    public ModernSplashEvents() {
        trueFullscreen = Minecraft.getMinecraft().gameSettings.fullScreen;
        Minecraft.getMinecraft().gameSettings.fullScreen = false;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!triggered && CustomSplash.enableTimer && event.getGui() instanceof GuiMainMenu) {
            triggered = true;

            Minecraft.getMinecraft().gameSettings.fullScreen = trueFullscreen;
            if (Minecraft.getMinecraft().gameSettings.fullScreen && !Minecraft.getMinecraft().isFullScreen()) {
                Minecraft.getMinecraft().toggleFullscreen();
                Minecraft.getMinecraft().gameSettings.fullScreen = Minecraft.getMinecraft().isFullScreen();
            }

            startupTime = ManagementFactory.getRuntimeMXBean().getUptime();
            CustomSplash.LOGGER.info("Startup took {}ms.", startupTime);

            doneTime = startupTime;

            TimeHistory.saveHistory(doneTime);
        }
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent event){
        if(!hasBeenMainMenu && CustomSplash.enableTimer && event.getGui() instanceof GuiMainMenu mainMenu){
            hasBeenMainMenu = true;

            if(CustomSplash.displayStartupTimeOnMainMenu) {
                long minutes = (startupTime / 1000) / 60;
                long seconds = (startupTime / 1000) % 60;

                float guiScale = (float)Minecraft.getMinecraft().gameSettings.guiScale;
                if(guiScale <= 0) guiScale = 1; // failsafe to prevent divide by 0

                String txt = "Startup took " + minutes + "m " + seconds + "s.";
                Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(txt, (mainMenu.width - Minecraft.getMinecraft().fontRenderer.getStringWidth(txt))/2, 10, Color.YELLOW.getRGB());
            }

        }else if(hasBeenMainMenu){
            hasBeenMainMenu = false;
        }
    }
}
