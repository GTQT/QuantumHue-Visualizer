package meowmel.quantumhue.command;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.mail.MailService;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.OpenPagePacket;
import meowmel.quantumhue.network.packet.RefreshPagesPacket;
import meowmel.quantumhue.notice.VersionState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /quantumhue <reload|refresh|shop|news>}：页面系统的管理入口。
 *
 * <ul>
 *   <li>{@code reload} —— 重读 {@code config/quantumhue/*.json}；</li>
 *   <li>{@code refresh} —— 重读配置，并重新预取更新日志/公告/社群服务/邮件；</li>
 *   <li>{@code shop} / {@code news} —— 打开对应页面。</li>
 * </ul>
 */
public class CommandQuantumHue extends CommandBase {

    @Override
    public String getName() {
        return "quantumhue";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/quantumhue <reload|refresh|shop|news>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("qh");
        return aliases;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String [] args) {
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("§c用法：" + getUsage(sender)));
            return;
        }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "reload":
                PageConfigs.reload();
                VersionState.loadCached();
                sender.sendMessage(new TextComponentString("§a[QuantumHue] 配置已重新加载"));
                return;
            case "refresh":
                PageConfigs.reload();
                VersionState.loadCached();
                MailService.refreshNow(server.getWorld(0));
                if (sender instanceof EntityPlayerMP) {
                    PacketHandler.sendTo(new RefreshPagesPacket(), (EntityPlayerMP) sender);
                }
                sender.sendMessage(new TextComponentString(
                        "§a[QuantumHue] 正在后台刷新更新日志、公告、社群服务与邮件"));
                return;
            case "shop":
                open(sender, true);
                return;
            case "news":
                open(sender, false);
                return;
            default:
                sender.sendMessage(new TextComponentString("§c用法：" + getUsage(sender)));
        }
    }

    private void open(ICommandSender sender, boolean shop) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("§c该命令仅限玩家使用"));
            return;
        }
        PacketHandler.sendTo(shop ? OpenPagePacket.shop() : OpenPagePacket.news(), (EntityPlayerMP) sender);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                                   String [] args, net.minecraft.util.math.BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "refresh", "shop", "news");
        }
        return new ArrayList<>();
    }
}
