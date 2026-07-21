package meowmel.quantumhue.chat.mixin;

import meowmel.quantumhue.chat.ChatMessageStore;
import meowmel.quantumhue.chat.ChatMessageStore.SenderMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 劫持 NetHandlerPlayClient.handleChat — 消息分类与元数据提取
 *
 * 1.12.2 的 SPacketChat:
 *   - chatComponent: ITextComponent 消息内容
 *   - type: ChatType (CHAT=0, SYSTEM=1, GAME_INFO=2)
 *
 * 分类逻辑:
 *   CHAT(0) → 尝试提取玩家名和原始内容 → 气泡消息
 *   SYSTEM(1) → 检测是否是私聊 → 否则系统消息
 *   GAME_INFO(2) → 忽略（action bar）
 */
@Mixin(value = NetHandlerPlayClient.class, priority = 500)
public class NetHandlerPlayClientMixin {

    @Unique
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void onHandleChat(SPacketChat packet, CallbackInfo ci) {
        ITextComponent message = packet.getChatComponent();
        ChatType type = packet.getType();

        // GAME_INFO (action bar) → 忽略
        if (type == ChatType.GAME_INFO) return;

        // === /msg 私聊关键词检测 ===
        String text = message.getUnformattedText();
        if (type == ChatType.SYSTEM) {
            SenderMeta wm = detectWhisperInSystemMessage(text);
            if (wm != null) {
                ChatMessageStore.setPendingMeta(wm);
                return;
            }
        }

        // === CHAT(0) 普通聊天 ===
        if (type == ChatType.CHAT) {
            // 尝试从 "<PlayerName> message" 格式中提取
            SenderMeta meta = extractChatMeta(message, text);
            if (meta != null) {
                ChatMessageStore.setPendingMeta(meta);
                return;
            }
        }

        // === 默认：系统消息 ===
        ChatMessageStore.setPendingMeta(new SenderMeta(
                NIL_UUID,
                new TextComponentTranslation("quantumhue.chat.sender.system"),
                message,
                type == ChatType.SYSTEM,
                null
        ));
    }

    /**
     * 从 "<PlayerName> message" 格式提取玩家信息
     */
    @Unique
    private SenderMeta extractChatMeta(ITextComponent message, String text) {
        // 尝试匹配 "<name> content" 格式
        if (text.startsWith("<")) {
            int close = text.indexOf("> ");
            if (close > 1 && close < 50) {
                String rawName = text.substring(1, close);
                String content = text.substring(close + 2);
                NetworkPlayerInfo info = findPlayerByName(rawName);
                UUID uuid = info != null ? info.getGameProfile().getId() : NIL_UUID;
                String profileName = info != null ? info.getGameProfile().getName() : rawName;

                return new SenderMeta(
                        uuid,
                        new TextComponentString(rawName),
                        new TextComponentString(content),
                        false,
                        profileName
                );
            }
        }
        return null;
    }

    /**
     * 检测系统消息中的私聊（/msg 在 1.12.2 以系统消息形式到达）
     */
    @Unique
    private SenderMeta detectWhisperInSystemMessage(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.getConnection() == null) return null;

        // 多语言私聊关键词
        String[] whisperPatterns = {
                "whispers to you", "whispers",
                "悄悄", "私聊", "密语", "密聊", "对你说",
        };

        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            for (String pattern : whisperPatterns) {
                if (text.contains(pattern) && text.contains(name)) {
                    // 提取消息内容：名字之后的部分
                    int idx = text.indexOf(name);
                    // 跳过名字和分隔符 ": ", "：", " whispers: "
                    int contentStart = findContentStart(text, idx + name.length());
                    String content = contentStart > 0 ? text.substring(contentStart).trim() : text;

                    return new SenderMeta(
                            info.getGameProfile().getId(),
                            new TextComponentString(name),
                            new TextComponentString(content),
                            false,
                            name
                    );
                }
            }
        }
        return null;
    }

    @Unique
    private int findContentStart(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '：' || ch == '»') continue;
            return i;
        }
        return -1;
    }

    @Unique
    private NetworkPlayerInfo findPlayerByName(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) return null;
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            if (info.getGameProfile().getName().equalsIgnoreCase(name))
                return info;
        }
        // 模糊匹配（颜色码等）
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            if (info.getGameProfile().getName().toLowerCase().contains(name.toLowerCase()))
                return info;
        }
        return null;
    }
}
