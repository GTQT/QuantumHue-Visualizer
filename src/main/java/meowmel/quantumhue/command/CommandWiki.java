package meowmel.quantumhue.command;

import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.wiki.WikiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CommandWiki extends CommandBase {

    @Override
    public String getName() {
        return "wiki";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/wiki - 打开Wiki窗口";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // 所有玩家可用
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true; // 无需权限
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayer player = (EntityPlayer) sender;

        // 客户端直接显示（单人游戏/局域网主机）
        if (player.world.isRemote) {
            showAnnouncementGui();
            return;
        }

        // 服务器端：发送网络包到客户端显示
        if (player instanceof EntityPlayerMP) {
            PacketHandler.sendTo(new ShowWikiPacket(), (EntityPlayerMP) player);
        }
    }

    // 客户端专用方法
    @SideOnly(Side.CLIENT)
    private void showAnnouncementGui() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new WikiScreen());
            }
        });
    }
}