package meowmel.quantumhue.network;


import meowmel.quantumhue.command.ShowWikiPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {

    private static SimpleNetworkWrapper instance;
    private static int packetId = 0;

    public static void init() {
        instance = new SimpleNetworkWrapper("gtqtcore");
        instance.registerMessage(
                ShowWikiPacket.Handler.class,
                ShowWikiPacket.class,
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