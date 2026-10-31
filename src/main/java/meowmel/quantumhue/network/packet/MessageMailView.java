package meowmel.quantumhue.network.packet;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.mail.MailDetail;
import meowmel.quantumhue.mail.MailSummary;
import meowmel.quantumhue.mail.client.MailClientStore;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端的邮件数据。
 *
 * <p>列表只传摘要，详情按邮件单独请求；因此邮件正文再长也不会把列表包撑爆。客户端没有任何邮件
 * 内容来源，显示什么完全由这个包决定。
 */
public class MessageMailView implements IMessage {

    public enum Kind {
        LIST,
        DETAIL,
        ERROR
    }

    private Kind kind = Kind.LIST;
    private boolean loading;
    private boolean enabled = true;
    private final List<MailSummary> summaries = new ArrayList<>();
    private MailDetail detail;
    private String mailId = "";
    private String error = "";

    public MessageMailView() {}

    public static MessageMailView list(List<MailSummary> summaries, boolean loading, boolean enabled) {
        return list(summaries, loading, enabled, "");
    }

    public static MessageMailView list(List<MailSummary> summaries, boolean loading, boolean enabled,
                                       String note) {
        MessageMailView message = new MessageMailView();
        message.kind = Kind.LIST;
        message.summaries.addAll(summaries);
        message.loading = loading;
        message.enabled = enabled;
        message.error = note == null ? "" : note;
        return message;
    }

    public static MessageMailView detail(MailDetail detail) {
        MessageMailView message = new MessageMailView();
        message.kind = Kind.DETAIL;
        message.detail = detail;
        message.mailId = detail == null ? "" : detail.id;
        return message;
    }

    public static MessageMailView error(String mailId, String error) {
        MessageMailView message = new MessageMailView();
        message.kind = Kind.ERROR;
        message.mailId = mailId == null ? "" : mailId;
        message.error = error == null ? "" : error;
        return message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeBoolean(loading);
        buf.writeBoolean(enabled);
        ByteBufUtils.writeUTF8String(buf, mailId == null ? "" : mailId);
        ByteBufUtils.writeUTF8String(buf, error == null ? "" : error);

        buf.writeInt(summaries.size());
        for (MailSummary summary : summaries) {
            ByteBufUtils.writeUTF8String(buf, summary.id);
            ByteBufUtils.writeUTF8String(buf, summary.title);
            ByteBufUtils.writeUTF8String(buf, summary.sender);
            ByteBufUtils.writeUTF8String(buf, summary.date);
            buf.writeInt(summary.rewardCount);
            buf.writeLong(summary.money);
            buf.writeBoolean(summary.claimed);
            buf.writeBoolean(summary.started);
            buf.writeBoolean(summary.expired);
        }

        buf.writeBoolean(detail != null);
        if (detail != null) {
            ByteBufUtils.writeUTF8String(buf, detail.id);
            ByteBufUtils.writeUTF8String(buf, detail.title);
            ByteBufUtils.writeUTF8String(buf, detail.sender);
            ByteBufUtils.writeUTF8String(buf, detail.date);
            buf.writeInt(detail.body.size());
            for (String line : detail.body) {
                ByteBufUtils.writeUTF8String(buf, line == null ? "" : line);
            }
            buf.writeInt(detail.rewards.size());
            for (ItemStack stack : detail.rewards) {
                ByteBufUtils.writeItemStack(buf, stack);
            }
            buf.writeLong(detail.money);
            buf.writeBoolean(detail.claimed);
            buf.writeBoolean(detail.started);
            buf.writeBoolean(detail.expired);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Kind[] kinds = Kind.values();
        kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.LIST;
        loading = buf.readBoolean();
        enabled = buf.readBoolean();
        mailId = ByteBufUtils.readUTF8String(buf);
        error = ByteBufUtils.readUTF8String(buf);

        int summaryCount = buf.readInt();
        if (summaryCount < 0 || summaryCount > 512) {
            return;
        }
        for (int i = 0; i < summaryCount; i++) {
            MailSummary summary = new MailSummary();
            summary.id = ByteBufUtils.readUTF8String(buf);
            summary.title = ByteBufUtils.readUTF8String(buf);
            summary.sender = ByteBufUtils.readUTF8String(buf);
            summary.date = ByteBufUtils.readUTF8String(buf);
            summary.rewardCount = buf.readInt();
            summary.money = buf.readLong();
            summary.claimed = buf.readBoolean();
            summary.started = buf.readBoolean();
            summary.expired = buf.readBoolean();
            summaries.add(summary);
        }

        if (buf.readBoolean()) {
            MailDetail parsed = new MailDetail();
            parsed.id = ByteBufUtils.readUTF8String(buf);
            parsed.title = ByteBufUtils.readUTF8String(buf);
            parsed.sender = ByteBufUtils.readUTF8String(buf);
            parsed.date = ByteBufUtils.readUTF8String(buf);
            int bodyCount = buf.readInt();
            if (bodyCount < 0 || bodyCount > 200) {
                return;
            }
            for (int i = 0; i < bodyCount; i++) {
                parsed.body.add(ByteBufUtils.readUTF8String(buf));
            }
            int rewardCount = buf.readInt();
            if (rewardCount < 0 || rewardCount > 64) {
                return;
            }
            for (int i = 0; i < rewardCount; i++) {
                parsed.rewards.add(ByteBufUtils.readItemStack(buf));
            }
            parsed.money = buf.readLong();
            parsed.claimed = buf.readBoolean();
            parsed.started = buf.readBoolean();
            parsed.expired = buf.readBoolean();
            detail = parsed;
        }
    }

    public static class Handler implements IMessageHandler<MessageMailView, IMessage> {

        @Override
        public IMessage onMessage(MessageMailView message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(MessageMailView message) {
            switch (message.kind) {
                case LIST:
                    MailClientStore.setList(message.loading, message.enabled, message.summaries,
                            message.error);
                    break;
                case DETAIL:
                    MailClientStore.setDetail(message.detail);
                    break;
                case ERROR:
                default:
                    MailClientStore.setError(message.mailId, message.error);
                    break;
            }
        }
    }
}
