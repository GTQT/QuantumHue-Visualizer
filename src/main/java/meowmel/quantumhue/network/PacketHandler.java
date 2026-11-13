package meowmel.quantumhue.network;


import meowmel.quantumhue.chat.packets.ChatGroupPacket;
import meowmel.quantumhue.chat.packets.ChatNoticePacket;
import meowmel.quantumhue.chat.packets.ChatPrivatePacket;
import meowmel.quantumhue.chat.packets.GroupManagePacket;
import meowmel.quantumhue.chat.packets.TpRequestPacket;
import meowmel.quantumhue.network.packet.MessageBuyItem;
import meowmel.quantumhue.network.packet.MessageMailAction;
import meowmel.quantumhue.network.packet.MessageMailView;
import meowmel.quantumhue.network.packet.MessageLotteryAction;
import meowmel.quantumhue.network.packet.MessageLotteryView;
import meowmel.quantumhue.network.packet.MessageRemoveListing;
import meowmel.quantumhue.network.packet.MessageSellItem;
import meowmel.quantumhue.network.packet.MessageSellToOrder;
import meowmel.quantumhue.network.packet.MessageShopAction;
import meowmel.quantumhue.network.packet.MessageShopView;
import meowmel.quantumhue.network.packet.MessageSystemBuy;
import meowmel.quantumhue.network.packet.OpenPagePacket;
import meowmel.quantumhue.network.packet.RefreshPagesPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {

    private static SimpleNetworkWrapper instance;
    private static int packetId = 0;

    public static void init() {
        instance = new SimpleNetworkWrapper("quantumhue");
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
        // Chat - Private
        instance.registerMessage(
                ChatPrivatePacket.ServerHandler.class,
                ChatPrivatePacket.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                ChatPrivatePacket.ClientHandler.class,
                ChatPrivatePacket.class,
                packetId++,
                Side.CLIENT);
        // Chat - Group
        instance.registerMessage(
                ChatGroupPacket.ServerHandler.class,
                ChatGroupPacket.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                ChatGroupPacket.ClientHandler.class,
                ChatGroupPacket.class,
                packetId++,
                Side.CLIENT);
        // Chat - Group Manage
        instance.registerMessage(
                GroupManagePacket.ServerHandler.class,
                GroupManagePacket.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                GroupManagePacket.ClientHandler.class,
                GroupManagePacket.class,
                packetId++,
                Side.CLIENT);
        // Chat - TP Request
        instance.registerMessage(
                TpRequestPacket.ServerHandler.class,
                TpRequestPacket.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                TpRequestPacket.ClientHandler.class,
                TpRequestPacket.class,
                packetId++,
                Side.CLIENT);

        // 商店 / 公告页面
        instance.registerMessage(
                OpenPagePacket.Handler.class,
                OpenPagePacket.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                RefreshPagesPacket.Handler.class,
                RefreshPagesPacket.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                MessageShopView.Handler.class,
                MessageShopView.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                MessageShopAction.Handler.class,
                MessageShopAction.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageBuyItem.Handler.class,
                MessageBuyItem.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageRemoveListing.Handler.class,
                MessageRemoveListing.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageSellItem.Handler.class,
                MessageSellItem.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageSellToOrder.Handler.class,
                MessageSellToOrder.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageSystemBuy.Handler.class,
                MessageSystemBuy.class,
                packetId++,
                Side.SERVER);
        instance.registerMessage(
                MessageLotteryView.Handler.class,
                MessageLotteryView.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                MessageLotteryAction.Handler.class,
                MessageLotteryAction.class,
                packetId++,
                Side.SERVER);

        // 邮件
        instance.registerMessage(
                MessageMailView.Handler.class,
                MessageMailView.class,
                packetId++,
                Side.CLIENT);
        instance.registerMessage(
                MessageMailAction.Handler.class,
                MessageMailAction.class,
                packetId++,
                Side.SERVER);

        // 聊天提示（服务端 → 客户端）
        instance.registerMessage(
                ChatNoticePacket.ClientHandler.class,
                ChatNoticePacket.class,
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