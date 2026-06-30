package meowmel.quantumhue.network;


import meowmel.quantumhue.command.ShowWikiPacket;
import meowmel.quantumhue.command.WikiReloadPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {

    private static SimpleNetworkWrapper instance;
    private static int packetId = 0;

    public static void init() {
        instance = new SimpleNetworkWrapper("quantumhue");
        instance.registerMessage(
                ShowWikiPacket.Handler.class,
                ShowWikiPacket.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                WikiReloadPacket.Handler.class,
                WikiReloadPacket.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                HighlightPacket.ServerHandler.class,
                HighlightPacket.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                HighlightPacket.ClientHandler.class,
                HighlightPacket.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                ThaumcraftAuraMessage.ServerHandler.class,
                ThaumcraftAuraMessage.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                ThaumcraftAuraMessage.ClientHandler.class,
                ThaumcraftAuraMessage.class,
                packetId++,
                Side.CLIENT);
    }

    public static void sendTo(IMessage message, net.minecraft.entity.player.EntityPlayerMP player) {
        instance.sendTo(message, player);
    }

    public static void sendToAll(IMessage message) {
        instance.sendToAll(message);
    }

    public static void sendToServer(IMessage message) {
        instance.sendToServer(message);
    }
}