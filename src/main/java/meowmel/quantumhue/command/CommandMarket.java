package meowmel.quantumhue.command;

import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.OpenPagePacket;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** {@code /market}：打开自由交易商店。 */
public class CommandMarket extends CommandBase {

    @Override
    public String getName() {
        return "market";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/market - 打开自由交易市场";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("§c该命令仅限玩家使用"));
            return;
        }
        PacketHandler.sendTo(OpenPagePacket.shop(), (EntityPlayerMP) sender);
    }
}
