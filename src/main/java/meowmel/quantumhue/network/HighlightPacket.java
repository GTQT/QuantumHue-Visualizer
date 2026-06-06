package meowmel.quantumhue.network;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.client.highlight.ClientHighlightHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class HighlightPacket implements IMessage {

    private BlockPos pos;
    private int entityId;
    private int targetType; // 0 = block, 1 = entity
    private String targetName;
    private String playerName;

    public HighlightPacket() {
    }

    public HighlightPacket(BlockPos pos, int entityId, int targetType, String targetName, String playerName) {
        this.pos = pos;
        this.entityId = entityId;
        this.targetType = targetType;
        this.targetName = targetName;
        this.playerName = playerName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.entityId = buf.readInt();
        this.targetType = buf.readInt();
        this.targetName = ByteBufUtils.readUTF8String(buf);
        this.playerName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(entityId);
        buf.writeInt(targetType);
        ByteBufUtils.writeUTF8String(buf, targetName);
        ByteBufUtils.writeUTF8String(buf, playerName);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getEntityId() {
        return entityId;
    }

    public int getTargetType() {
        return targetType;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getPlayerName() {
        return playerName;
    }

    // ========== 服务端处理器 ==========
    public static class ServerHandler implements IMessageHandler<HighlightPacket, IMessage> {
        @Override
        public IMessage onMessage(HighlightPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                String playerName = player.getDisplayName().getFormattedText();

                String targetDesc = message.targetType == 0 ? "方块" : "生物";

                String coordStr = String.format("(%d, %d, %d)",
                        message.pos.getX(), message.pos.getY(), message.pos.getZ());

                String chatMessage = String.format("§e[广播]§r %s 提醒坐标 %s 的%s: %s",
                        playerName, coordStr, targetDesc, message.targetName);

                // 向所有在线玩家广播聊天消息
                for (EntityPlayerMP p : player.getServer().getPlayerList().getPlayers()) {
                    p.sendMessage(new TextComponentString(chatMessage));
                }

                // 向所有客户端广播高亮数据包
                HighlightPacket broadcast = new HighlightPacket(
                        message.pos, message.entityId, message.targetType,
                        message.targetName, playerName
                );
                PacketHandler.sendToAll(broadcast);
            });
            return null;
        }
    }

    // ========== 客户端处理器 ==========
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<HighlightPacket, IMessage> {
        @Override
        public IMessage onMessage(HighlightPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                ClientHighlightHandler.addHighlight(message);
            });
            return null;
        }
    }
}
