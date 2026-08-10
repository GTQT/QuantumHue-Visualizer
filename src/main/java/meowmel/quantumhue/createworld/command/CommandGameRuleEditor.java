package meowmel.quantumhue.createworld.command;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.createworld.gamerule.GuiScreenGameRuleEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * /gameruleEditor 命令——在游戏内打开游戏规则编辑器界面。
 * <p>Opens the game rule editor GUI in-game; requires igGameruleEdit in config.</p>
 */
public class CommandGameRuleEditor extends CommandBase {

    @Override
    public String getName() {
        return "gameruleEditor";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/gameruleEditor";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // 与 /gamerule 一致 / same as /gamerule
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (QuantumHueConfig.createWorld == null || !QuantumHueConfig.createWorld.igGameruleEdit) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        // 从服务端世界读取规则（单机命令运行在服务器线程，GUI 需要主线程）
        WorldClient world = mc.world;
        Map<String, String> gameRules = new HashMap<>();
        if (world != null) {
            GameRules rules = world.getGameRules();
            for (String ruleName : rules.getRules()) {
                gameRules.put(ruleName, rules.getString(ruleName));
            }
        }
        final Map<String, String> rulesSnapshot = gameRules;
        mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiScreenGameRuleEditor(null, rulesSnapshot)));
    }
}
