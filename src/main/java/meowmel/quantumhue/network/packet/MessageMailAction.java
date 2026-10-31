package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.mail.MailService;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求邮件数据 / 领取邮件。
 *
 * <p>请求里只有 {@code mailId}，没有任何奖励字段；服务端用它自己拉到的远程邮件做校验与发放。
 */
public class MessageMailAction implements IMessage {

    public enum Mode {
        /** 邮件列表摘要。 */
        LIST,
        /** 单封邮件详情。 */
        DETAIL,
        /** 领取奖励。 */
        CLAIM
    }

    private Mode mode = Mode.LIST;
    private String mailId = "";

    public MessageMailAction() {}

    private MessageMailAction(Mode mode, String mailId) {
        this.mode = mode;
        this.mailId = mailId == null ? "" : mailId;
    }

    public static MessageMailAction list() {
        return new MessageMailAction(Mode.LIST, "");
    }

    public static MessageMailAction detail(String mailId) {
        return new MessageMailAction(Mode.DETAIL, mailId);
    }

    public static MessageMailAction claim(String mailId) {
        return new MessageMailAction(Mode.CLAIM, mailId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Mode[] modes = Mode.values();
        mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.LIST;
        mailId = ByteBufUtils.readUTF8String(buf);
        if (mailId != null && mailId.length() > 64) {
            mailId = mailId.substring(0, 64);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        ByteBufUtils.writeUTF8String(buf, mailId == null ? "" : mailId);
    }

    public static class Handler implements IMessageHandler<MessageMailAction, IMessage> {

        @Override
        public IMessage onMessage(MessageMailAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, MessageMailAction message) {
            MailService.markListening(player);
            switch (message.mode) {
                case LIST:
                    PacketHandler.sendTo(MessageMailView.list(MailService.summaries(player),
                            MailService.isLoading(), MailService.isEnabled(),
                            MailService.lastError()), player);
                    break;
                case DETAIL: {
                    meowmel.quantumhue.mail.MailDetail detail =
                            MailService.detail(player, message.mailId);
                    if (detail == null) {
                        PacketHandler.sendTo(MessageMailView.error(message.mailId,
                                "§c邮件不存在或已被移除"), player);
                    } else {
                        PacketHandler.sendTo(MessageMailView.detail(detail), player);
                    }
                    break;
                }
                case CLAIM: {
                    MailService.ClaimResult result = MailService.claim(player, message.mailId);
                    if (result.success) {
                        meowmel.quantumhue.mail.MailDetail detail =
                                MailService.detail(player, message.mailId);
                        if (detail != null) {
                            PacketHandler.sendTo(MessageMailView.detail(detail), player);
                        }
                        MailService.notifyPlayer(player, result.message);
                    } else {
                        PacketHandler.sendTo(MessageMailView.error(message.mailId, result.message), player);
                        MailService.notifyPlayer(player, result.message);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }
}
