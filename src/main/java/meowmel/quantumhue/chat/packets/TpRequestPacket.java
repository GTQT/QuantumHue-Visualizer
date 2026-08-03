package meowmel.quantumhue.chat.packets;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

/**
 * 传送请求包 — 客户端右键头像 → C→S → 服务端通知目标
 *
 * 接受方在聊天栏输入 "tpaccept" 即同意传送。
 */
public class TpRequestPacket implements IMessage {

    private String targetName;
    private String requesterName;
    private UUID requesterUUID;

    public TpRequestPacket() {}

    public TpRequestPacket(String targetName, String requesterName, UUID requesterUUID) {
        this.targetName = targetName;
        this.requesterName = requesterName;
        this.requesterUUID = requesterUUID;
    }

    public String getTargetName() { return targetName; }
    public String getRequesterName() { return requesterName; }
    public UUID getRequesterUUID() { return requesterUUID; }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetName = ByteBufUtils.readUTF8String(buf);
        requesterName = ByteBufUtils.readUTF8String(buf);
        requesterUUID = UUID.fromString(ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, targetName);
        ByteBufUtils.writeUTF8String(buf, requesterName);
        ByteBufUtils.writeUTF8String(buf, requesterUUID.toString());
    }

    /** 服务端：记录请求并通知目标 */
    public static class ServerHandler implements IMessageHandler<TpRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(TpRequestPacket pkt, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP requester = ctx.getServerHandler().player;
                EntityPlayerMP target = FMLCommonHandler.instance().getMinecraftServerInstance()
                        .getPlayerList().getPlayerByUsername(pkt.targetName);
                if (target == null) {
                    requester.sendMessage(new TextComponentString(
                            "§c[Tp] 玩家 " + pkt.targetName + " 不在线"));
                    return;
                }
                if (target.getUniqueID().equals(requester.getUniqueID())) {
                    requester.sendMessage(new TextComponentString("§c[Tp] 不能向自己发送传送请求"));
                    return;
                }

                // 记录请求
                TpManager.addRequest(target.getUniqueID(), requester.getUniqueID(), requester.getName());

                // 通知目标
                target.sendMessage(new TextComponentString(
                        "§b[Tp] " + requester.getName() + " §f请求传送到你身边 §7— 输入 §a/tpaccept§7 同意"));
                // 通知请求者
                requester.sendMessage(new TextComponentString(
                        "§7[Tp] 已向 " + pkt.targetName + " 发送传送请求，等待对方同意..."));
            });
            return null;
        }
    }

    /** 客户端：不使用 */
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<TpRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(TpRequestPacket pkt, MessageContext ctx) {
            return null;
        }
    }

    // ===== 服务端请求管理器 =====

    public static class TpManager {
        /** targetUUID → { requesterUUID, requesterName, timestamp } */
        private static final Map<UUID, PendingRequest> pending = new LinkedHashMap<>();
        private static final long TIMEOUT_MS = 60_000; // 1 分钟超时

        private static class PendingRequest {
            final UUID requesterUUID;
            final String requesterName;
            final long timestamp;
            PendingRequest(UUID requesterUUID, String requesterName, long timestamp) {
                this.requesterUUID = requesterUUID;
                this.requesterName = requesterName;
                this.timestamp = timestamp;
            }
        }

        public static void addRequest(UUID targetUUID, UUID requesterUUID, String requesterName) {
            pending.put(targetUUID, new PendingRequest(requesterUUID, requesterName, System.currentTimeMillis()));
        }

        /** 处理 /tpaccept — 返回 true 表示消费了这次接受 */
        public static boolean acceptTp(UUID accepterUUID) {
            purgeExpired();
            PendingRequest req = pending.remove(accepterUUID);
            if (req == null) return false;

            EntityPlayerMP requester = FMLCommonHandler.instance().getMinecraftServerInstance()
                    .getPlayerList().getPlayerByUUID(req.requesterUUID);
            EntityPlayerMP target = FMLCommonHandler.instance().getMinecraftServerInstance()
                    .getPlayerList().getPlayerByUUID(accepterUUID);

            if (requester == null) {
                if (target != null) target.sendMessage(new TextComponentString(
                        "§c[Tp] 请求者 " + req.requesterName + " 已离线"));
                return true;
            }
            if (target == null) {
                requester.sendMessage(new TextComponentString("§c[Tp] 对方已离线"));
                return true;
            }

            // 执行传送
            requester.connection.setPlayerLocation(
                    target.posX, target.posY, target.posZ,
                    target.rotationYaw, target.rotationPitch);
            requester.sendMessage(new TextComponentString(
                    "§a[Tp] 已传送到 " + target.getName() + " 身边"));
            target.sendMessage(new TextComponentString(
                    "§a[Tp] " + requester.getName() + " 已传送到你身边"));
            return true;
        }

        private static void purgeExpired() {
            long cutoff = System.currentTimeMillis() - TIMEOUT_MS;
            pending.values().removeIf(r -> r.timestamp < cutoff);
        }
    }
}
