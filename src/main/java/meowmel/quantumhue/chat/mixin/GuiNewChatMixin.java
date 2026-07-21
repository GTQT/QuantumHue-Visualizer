package meowmel.quantumhue.chat.mixin;

import meowmel.quantumhue.chat.ChatMessageStore;
import meowmel.quantumhue.chat.ChatMessageStore.SenderMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 劫持 GuiNewChat — 捕获消息并禁用原版聊天 HUD 渲染
 */
@Mixin(value = GuiNewChat.class, priority = 500)
public class GuiNewChatMixin {

    @Unique
    private String quantumHueChat$lastText;
    @Unique
    private long quantumHueChat$lastTime;

    /**
     * 禁用原版聊天 HUD 渲染
     */
    @Inject(method = "drawChat", at = @At("HEAD"), cancellable = true)
    private void onDrawChat(int updateCounter, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * 捕获所有 printChatMessage 调用
     * GuiNewChat 有两个重载，都会走到这
     */
    @Inject(method = "printChatMessage", at = @At("HEAD"))
    private void onPrintChatMessage(ITextComponent message, CallbackInfo ci) {
        String text = message.getUnformattedText();

        // 去重（100ms 内相同文本跳过）
        long now = System.currentTimeMillis();
        if (text.equals(quantumHueChat$lastText) && now - quantumHueChat$lastTime < 100) return;
        quantumHueChat$lastText = text;
        quantumHueChat$lastTime = now;

        // 消费 Mixin 管道传来的元数据
        SenderMeta meta = ChatMessageStore.consumePendingMeta();
        if (meta == null) {
            // 没有元数据 → 默认系统消息
            meta = new SenderMeta(
                    new UUID(0L, 0L),
                    new TextComponentTranslation("quantumhue.chat.sender.system"),
                    message,
                    true,
                    null
            );
        }

        // 回声抑制
        if (ChatMessageStore.consumeEchoIfSenderMatches(meta.senderName)) return;
        if (ChatMessageStore.consumeEchoByContent(message.getUnformattedText())) return;

        // 写入存储（默认世界频道）
        ChatMessageStore.addMessage(
                meta.rawContent != null ? meta.rawContent : message,
                meta.senderUUID,
                meta.senderName,
                meta.isSystem,
                meta.rawPlayerName,
                "world"
        );
    }
}
