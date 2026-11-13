package meowmel.quantumhue.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 关闭聊天界面时的消息预览层。
 *
 * <p>这个模组会接管原版聊天 HUD，所以玩家在正常游戏时看不到任何消息；
 * 这里在左下角补一条精简的消息流，并在私聊 / 被 @ 时响一声。
 * 只做服务器环境里真正会用到的东西：不弹窗、不抢焦点、不打断操作。</p>
 */
@SideOnly(Side.CLIENT)
public final class ChatHud {

    private static final long SHOW_MS = 10_000L;
    private static final long FADE_MS = 1_500L;
    private static final long SOUND_COOLDOWN_MS = 900L;

    private static long lastSoundAt;

    private ChatHud() {}

    /** 每 tick 调用：处理提示音。 */
    public static void tick(Minecraft mc) {
        ChatThemeSettings settings = ChatThemeSettings.get();
        if (!settings.hudEnabled() || !settings.hudSound()) {
            ChatMessageStore.consumeNotify();
            return;
        }
        int kind = ChatMessageStore.consumeNotify();
        if (kind == 0 || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSoundAt < SOUND_COOLDOWN_MS) {
            return;
        }
        lastSoundAt = now;
        // 被 @ 比普通私聊更"急"一点，音高更高。
        float pitch = kind == 2 ? 1.55F : 1.15F;
        mc.player.playSound(SoundEvents.BLOCK_NOTE_PLING, 0.45F, pitch);
    }

    /** RenderGameOverlayEvent.Post(ALL) 时调用。 */
    public static void render(Minecraft mc, ScaledResolution resolution) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen != null || mc.gameSettings.hideGUI) {
            return;
        }
        ChatThemeSettings settings = ChatThemeSettings.get();
        if (!settings.hudEnabled()) {
            return;
        }

        int unread = ChatMessageStore.getTotalUnread();
        long idle = System.currentTimeMillis() - ChatMessageStore.getLastMessageAt();
        if (idle > SHOW_MS && unread <= 0) {
            return;
        }
        List<ChatMessageStore.ChatMessage> recent = ChatMessageStore.getRecentMessages();
        if (recent.isEmpty()) {
            return;
        }

        int screenW = resolution.getScaledWidth();
        int screenH = resolution.getScaledHeight();
        FontRenderer font = mc.fontRenderer;

        int shown = Math.min(settings.hudLines(), recent.size());
        int lineH = 10;
        int stripW = Math.min(300, screenW - 16);
        int blockH = shown * lineH + 6;
        int x = 4;
        int bottom = screenH - 38;
        int y = bottom - blockH;

        float alpha = 1.0F;
        long remain = SHOW_MS - idle;
        if (remain > 0 && remain < FADE_MS) {
            alpha = remain / (float) FADE_MS;
        }
        // idle 超过展示时长但仍有未读时保持整条可见，避免未读标记一起被淡没。
        int a = (int) (alpha * 255.0F);
        int bg = ChatColors.withAlpha(ChatColors.PANEL_BG, (int) (a * 0.62F));
        int edge = ChatColors.withAlpha(ChatColors.ACCENT, (int) (a * 0.75F));

        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                net.minecraft.client.renderer.GlStateManager.SourceFactor.SRC_ALPHA,
                net.minecraft.client.renderer.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                net.minecraft.client.renderer.GlStateManager.SourceFactor.ONE,
                net.minecraft.client.renderer.GlStateManager.DestFactor.ZERO);

        ChatRender.roundRect(x, y, x + stripW, y + blockH, bg, 3);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + 2, y + blockH, edge);

        int start = recent.size() - shown;
        for (int i = 0; i < shown; i++) {
            ChatMessageStore.ChatMessage message = recent.get(start + i);
            int lineY = y + 3 + i * lineH;
            drawLine(mc, font, message, x + 6, lineY, stripW - 10, a);
        }

        if (unread > 0) {
            String badge = unread + " 条未读";
            int badgeW = font.getStringWidth(badge) + 8;
            int bx = x + stripW - badgeW;
            int by = y - 12;
            ChatRender.roundRect(bx, by, bx + badgeW, by + 11, ChatColors.withAlpha(ChatColors.NOTIF_BG, a), 3);
            font.drawStringWithShadow(badge, bx + 4, by + 2, ChatColors.withAlpha(ChatColors.NOTIF_TEXT, a));
        }
    }

    private static void drawLine(Minecraft mc, FontRenderer font, ChatMessageStore.ChatMessage message,
                                 int x, int y, int maxWidth, int alpha) {
        String tag = "";
        String id = message.channelId();
        if (id != null && id.startsWith("priv:")) {
            tag = "[私聊] ";
        } else if (id != null && id.startsWith("group:")) {
            tag = "[群] ";
        }

        boolean mention = !message.isOwn() && !message.isSystem() && mentions(message.content().getUnformattedText());
        int textColor = ChatColors.withAlpha(ChatColors.TEXT_PRIMARY, alpha);
        int nameColor = ChatColors.withAlpha(mention ? ChatColors.MENTION_MARK : ChatColors.ACCENT, alpha);
        int tagColor = ChatColors.withAlpha(ChatColors.TEXT_SECONDARY, alpha);

        int cursor = x;
        if (!tag.isEmpty()) {
            font.drawStringWithShadow(tag, cursor, y, tagColor);
            cursor += font.getStringWidth(tag);
        }
        String sender = message.isSystem() ? "" : message.senderName().getUnformattedText();
        if (!sender.isEmpty()) {
            String name = sender + ": ";
            int room = maxWidth - (cursor - x);
            if (room > 12) {
                String shownName = font.trimStringToWidth(name, Math.max(20, room / 2));
                font.drawStringWithShadow(shownName, cursor, y, nameColor);
                cursor += font.getStringWidth(shownName);
            }
        }
        String body = message.content().getUnformattedText().replace('\n', ' ');
        int room = maxWidth - (cursor - x);
        if (room <= 4) {
            return;
        }
        String shown = font.trimStringToWidth(body, room);
        if (shown.length() < body.length()) {
            shown = shown + "…";
        }
        font.drawStringWithShadow(shown, cursor, y, textColor);

        if (mention) {
            net.minecraft.client.gui.Gui.drawRect(x - 4, y - 1, x - 2, y + 9, ChatColors.withAlpha(ChatColors.ACCENT, alpha));
        }
    }

    private static boolean mentions(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || text == null) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("@all") || lower.contains("@全体")) {
            return true;
        }
        String name = mc.player.getName().toLowerCase(java.util.Locale.ROOT);
        int idx = lower.indexOf("@" + name);
        while (idx >= 0) {
            int end = idx + 1 + name.length();
            boolean left = idx == 0 || !isWordChar(lower.charAt(idx - 1));
            boolean right = end >= lower.length() || !isWordChar(lower.charAt(end));
            if (left && right) {
                return true;
            }
            idx = lower.indexOf("@" + name, idx + 1);
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_');
    }
}
