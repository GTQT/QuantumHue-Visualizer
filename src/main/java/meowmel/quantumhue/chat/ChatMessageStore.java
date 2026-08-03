package meowmel.quantumhue.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 消息存储中心
 *
 * 职责:
 *   1. 存储所有消息（按频道分离）
 *   2. 回声抑制（避免自己发的消息重复显示）
 *   3. 防刷屏合并（连续相同消息显示 xN 计数）
 *   4. 未读追踪
 *   5. 聊天历史持久化
 *   6. ThreadLocal 管道：Mixin → setPendingMeta → captureMessage → addMessage
 */
@SideOnly(Side.CLIENT)
public class ChatMessageStore {

    private static final int MAX_MESSAGES = 10000;
    private static final int MAX_HISTORY = 500;

    /** 所有频道的消息 */
    private static final Map<String, List<ChatMessage>> channelMessages = new LinkedHashMap<>();

    /** 频道列表（保持插入顺序） */
    private static final List<ChatChannel> channels = new ArrayList<>();

    /** 当前活跃频道 */
    private static ChatChannel activeChannel;

    /** 聊天界面是否打开 */
    private static boolean screenOpen;

    // ===== 回声抑制 =====
    private static final List<PendingEcho> pendingEchoes = new ArrayList<>();
    private static final long ECHO_TIMEOUT_MS = 10_000;

    private static class PendingEcho {
        final String text;
        final long time;
        PendingEcho(String text, long time) { this.text = text; this.time = time; }
    }

    // ===== ThreadLocal 管道（Mixin 间传递消息元数据） =====
    private static final ThreadLocal<SenderMeta> PENDING_META = new ThreadLocal<>();

    // ===== 消息模型 =====

    public static class ChatMessage {
        private final UUID senderUUID;
        private final ITextComponent senderName;
        private final ITextComponent content;
        private final LocalTime time;
        private final boolean isOwn;
        private final boolean isSystem;
        private final String rawPlayerName;
        private final String channelId;
        private final int duplicateCount;
        private final String messageHash;

        public ChatMessage(UUID senderUUID, ITextComponent senderName, ITextComponent content,
                           LocalTime time, boolean isOwn, boolean isSystem,
                           String rawPlayerName, String channelId, int duplicateCount, String messageHash) {
            this.senderUUID = senderUUID;
            this.senderName = senderName;
            this.content = content;
            this.time = time;
            this.isOwn = isOwn;
            this.isSystem = isSystem;
            this.rawPlayerName = rawPlayerName;
            this.channelId = channelId;
            this.duplicateCount = duplicateCount;
            this.messageHash = messageHash;
        }

        public UUID senderUUID() { return senderUUID; }
        public ITextComponent senderName() { return senderName; }
        public ITextComponent content() { return content; }
        public LocalTime time() { return time; }
        public boolean isOwn() { return isOwn; }
        public boolean isSystem() { return isSystem; }
        public String rawPlayerName() { return rawPlayerName; }
        public String channelId() { return channelId; }
        public int duplicateCount() { return duplicateCount; }
        public String messageHash() { return messageHash; }
    }

    public static class SenderMeta {
        public final UUID senderUUID;
        public final ITextComponent senderName;
        public final ITextComponent rawContent;
        public final boolean isSystem;
        public final String rawPlayerName;

        public SenderMeta(UUID senderUUID, ITextComponent senderName, ITextComponent rawContent,
                          boolean isSystem, String rawPlayerName) {
            this.senderUUID = senderUUID;
            this.senderName = senderName;
            this.rawContent = rawContent;
            this.isSystem = isSystem;
            this.rawPlayerName = rawPlayerName;
        }
    }

    // ===== ThreadLocal 管道 =====

    public static void setPendingMeta(SenderMeta meta) { PENDING_META.set(meta); }

    public static SenderMeta consumePendingMeta() {
        SenderMeta m = PENDING_META.get();
        PENDING_META.remove();
        return m;
    }

    // ===== 频道管理 =====

    static {
        // 初始频道：世界频道
        ChatChannel world = ChatChannel.world();
        channels.add(world);
        channelMessages.put(world.getId(), new ArrayList<>());
        activeChannel = world;
    }

    public static List<ChatChannel> getChannels() { return channels; }
    public static ChatChannel getActiveChannel() { return activeChannel; }
    public static List<ChatMessage> getActiveMessages() {
        return channelMessages.getOrDefault(activeChannel.getId(), Collections.emptyList());
    }

    /** 清空当前频道全部消息 */
    public static void clearActiveChannel() {
        List<ChatMessage> msgs = channelMessages.get(activeChannel.getId());
        if (msgs != null) msgs.clear();
    }

    public static void setActiveChannel(String channelId) {
        for (ChatChannel ch : channels) {
            if (ch.getId().equals(channelId)) {
                activeChannel = ch;
                ch.markRead();
                return;
            }
        }
    }

    public static ChatChannel findOrCreatePrivateChannel(String partnerName, UUID partnerUuid) {
        String id = "priv:" + partnerName;
        for (ChatChannel ch : channels) {
            if (ch.getId().equals(id)) return ch;
        }
        ChatChannel priv = ChatChannel.privateChat(partnerName, partnerUuid);
        channels.add(priv);
        channelMessages.put(priv.getId(), new ArrayList<>());
        return priv;
    }

    public static ChatChannel findOrCreateGroupChannel(String groupId, String groupName,
                                                        List<String> memberNames, List<UUID> memberUuids) {
        String id = "group:" + groupId;
        for (ChatChannel ch : channels) {
            if (ch.getId().equals(id)) return ch;
        }
        ChatChannel grp = ChatChannel.group(groupId, groupName, memberNames, memberUuids);
        channels.add(grp);
        channelMessages.put(grp.getId(), new ArrayList<>());
        return grp;
    }

    /** 获取频道内的消息（如果不存在则初始化） */
    public static List<ChatMessage> getChannelMessages(String channelId) {
        return channelMessages.computeIfAbsent(channelId, k -> new ArrayList<>());
    }

    // ===== 消息添加 =====

    public static void addMessage(ITextComponent content, UUID senderUUID,
                                   ITextComponent senderName, boolean isSystem,
                                   String rawPlayerName, String channelId) {
        String messageHash = String.valueOf(content.getUnformattedText().hashCode());

        // 判断是否是自己发的（通过 rawPlayerName 匹配本地玩家名）
        Minecraft mc = Minecraft.getMinecraft();
        String localName = mc.player != null ? mc.player.getName() : "";
        boolean own = (rawPlayerName != null && !rawPlayerName.isEmpty())
                ? rawPlayerName.equals(localName)
                : (senderName != null && senderName.getUnformattedText().equals(localName));

        // 如果没指定频道，默认世界频道
        if (channelId == null || channelId.isEmpty()) {
            channelId = "world";
        }

        List<ChatMessage> targetList = getChannelMessages(channelId);

        // 防刷屏：连续相同消息合并计数
        if (isSystem && !targetList.isEmpty()) {
            ChatMessage last = targetList.get(targetList.size() - 1);
            if (last.isSystem() && last.content().getUnformattedText().equals(content.getUnformattedText())) {
                targetList.set(targetList.size() - 1, new ChatMessage(
                        last.senderUUID(), last.senderName(), last.content(),
                        LocalTime.now(), last.isOwn(), last.isSystem(),
                        last.rawPlayerName(), last.channelId(),
                        last.duplicateCount() + 1, last.messageHash()));
                return;
            }
        }

        if (!isSystem && !targetList.isEmpty()) {
            ChatMessage last = targetList.get(targetList.size() - 1);
            if (!last.isSystem() && isSameSender(last, senderName, rawPlayerName)
                    && last.content().getUnformattedText().equals(content.getUnformattedText())) {
                targetList.set(targetList.size() - 1, new ChatMessage(
                        last.senderUUID(), last.senderName(), last.content(),
                        LocalTime.now(), last.isOwn(), last.isSystem(),
                        last.rawPlayerName(), last.channelId(),
                        last.duplicateCount() + 1, last.messageHash()));
                return;
            }
        }

        targetList.add(new ChatMessage(
                senderUUID,
                senderName != null ? senderName : new TextComponentString(""),
                content,
                LocalTime.now(),
                own,
                isSystem,
                rawPlayerName,
                channelId,
                1,
                messageHash
        ));

        // 容量限制
        while (targetList.size() > MAX_MESSAGES) {
            targetList.remove(0);
        }

        // 未读追踪
        if (!screenOpen || !channelId.equals(activeChannel.getId())) {
            ChatChannel ch = findChannelById(channelId);
            if (ch != null && !ch.getId().equals(activeChannel.getId())) {
                ch.incrementUnread();
            }
        }
    }

    private static boolean isSameSender(ChatMessage last, ITextComponent senderName, String rawPlayerName) {
        if (rawPlayerName != null && !rawPlayerName.isEmpty()
                && last.rawPlayerName() != null && !last.rawPlayerName().isEmpty()) {
            return rawPlayerName.equals(last.rawPlayerName());
        }
        return last.senderName().getUnformattedText().equals(senderName.getUnformattedText());
    }

    private static ChatChannel findChannelById(String id) {
        for (ChatChannel ch : channels) {
            if (ch.getId().equals(id)) return ch;
        }
        return null;
    }

    // ===== 回声抑制 =====

    public static void incrementPendingEcho(String sentText) {
        purgeStaleEchoes();
        pendingEchoes.add(new PendingEcho(sentText, System.currentTimeMillis()));
    }

    public static boolean consumeEchoIfSenderMatches(ITextComponent senderName) {
        purgeStaleEchoes();
        if (pendingEchoes.isEmpty()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;
        String s = senderName.getUnformattedText();
        if (s.contains(mc.player.getName())) {
            pendingEchoes.remove(0);
            return true;
        }
        return false;
    }

    public static boolean consumeEchoByContent(String incomingText) {
        purgeStaleEchoes();
        for (int i = 0; i < pendingEchoes.size(); i++) {
            if (incomingText.equals(pendingEchoes.get(i).text)) {
                pendingEchoes.remove(i);
                return true;
            }
        }
        return false;
    }

    private static void purgeStaleEchoes() {
        long cutoff = System.currentTimeMillis() - ECHO_TIMEOUT_MS;
        pendingEchoes.removeIf(e -> e.time < cutoff);
    }

    // ===== 屏幕状态 =====

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
        if (open && activeChannel != null) {
            activeChannel.markRead();
        }
    }

    public static boolean isScreenOpen() { return screenOpen; }

    // ===== 未读（HUD红点用） =====

    public static int getTotalUnread() {
        int sum = 0;
        for (ChatChannel ch : channels) {
            if (!ch.getId().equals(activeChannel != null ? activeChannel.getId() : "world")) {
                sum += ch.getUnreadCount();
            }
        }
        return sum;
    }

    // ===== 聊天历史持久化 =====

    private static String currentWorldKey;

    public static void setCurrentWorld(String key) {
        if (Objects.equals(key, currentWorldKey)) return;
        if (currentWorldKey != null && isWorldSpecific(currentWorldKey)) {
            saveAllChannels(currentWorldKey);
        }
        currentWorldKey = key;
        // 清空旧数据
        channels.clear();
        channelMessages.clear();
        // 重建世界频道（兜底）
        ChatChannel world = ChatChannel.world();
        channels.add(world);
        channelMessages.put(world.getId(), new ArrayList<>());
        activeChannel = world;
        // 加载所有频道
        if (isWorldSpecific(currentWorldKey)) {
            loadAllChannels(currentWorldKey);
        }
    }

    private static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:"));
    }

    private static File getHistoryFile(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String hash = Integer.toHexString(worldKey.hashCode());
        return new File(Minecraft.getMinecraft().gameDir,
                "quantumhue/chat/" + safe + "_" + hash + ".json");
    }

    // ===== 持久化：所有频道 =====

    @SuppressWarnings("unchecked")
    private static void saveAllChannels(String worldKey) {
        File f = getHistoryFile(worldKey);
        f.getParentFile().mkdirs();
        Gson gson = new Gson();

        List<Map<String, Object>> channelList = new ArrayList<>();
        for (ChatChannel ch : channels) {
            List<ChatMessage> msgs = channelMessages.get(ch.getId());
            if (msgs == null || msgs.isEmpty()) continue;

            Map<String, Object> chObj = new LinkedHashMap<>();
            chObj.put("channelId", ch.getId());
            chObj.put("channelType", ch.getType().name());
            chObj.put("displayName", ch.getDisplayName());
            if (ch.getPartnerName() != null) chObj.put("partnerName", ch.getPartnerName());
            if (ch.getGroupId() != null) chObj.put("groupId", ch.getGroupId());
            if (!ch.getMemberNames().isEmpty()) chObj.put("memberNames", ch.getMemberNames());

            List<Map<String, Object>> msgList = new ArrayList<>();
            int start = Math.max(0, msgs.size() - MAX_HISTORY);
            for (int i = start; i < msgs.size(); i++) {
                ChatMessage msg = msgs.get(i);
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("senderUUID", msg.senderUUID().toString());
                obj.put("senderName", msg.senderName().getUnformattedText());
                obj.put("content", ITextComponent.Serializer.componentToJson(msg.content()));
                obj.put("time", msg.time().format(DateTimeFormatter.ISO_LOCAL_TIME));
                obj.put("isOwn", msg.isOwn());
                obj.put("isSystem", msg.isSystem());
                if (msg.rawPlayerName() != null) obj.put("rawPlayerName", msg.rawPlayerName());
                msgList.add(obj);
            }
            chObj.put("messages", msgList);
            channelList.add(chObj);
        }

        // 没有聊天数据就不写文件，避免覆盖手动备份
        if (channelList.isEmpty()) return;

        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            gson.toJson(channelList, w);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void loadAllChannels(String worldKey) {
        File f = getHistoryFile(worldKey);
        if (!f.exists()) return;
        Gson gson = new Gson();

        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> channelList = gson.fromJson(r,
                    new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (channelList == null || channelList.isEmpty()) return;

            // 检查格式：如果第一个元素没有 "channelId" 字段 → 旧格式（纯消息数组）
            Map<String, Object> first = channelList.get(0);
            if (!first.containsKey("channelId")) {
                // 旧格式：全是世界频道的消息
                List<ChatMessage> msgs = channelMessages.get("world");
                if (msgs == null) {
                    msgs = new ArrayList<>();
                    channelMessages.put("world", msgs);
                }
                for (Map<String, Object> obj : channelList) {
                    ChatMessage msg = deserializeMessage(obj);
                    if (msg != null) msgs.add(msg);
                }
                while (msgs.size() > MAX_MESSAGES) msgs.remove(0);
                return;
            }

            // 新格式：频道列表
            for (Map<String, Object> chObj : channelList) {
                String chId = (String) chObj.get("channelId");
                String chTypeStr = (String) chObj.get("channelType");
                String displayName = (String) chObj.get("displayName");
                String partnerName = (String) chObj.get("partnerName");
                String groupId = (String) chObj.get("groupId");
                List<String> memberNames = (List<String>) chObj.get("memberNames");

                // 重建频道
                ChatChannel ch = restoreChannel(chId, chTypeStr, displayName,
                        partnerName, groupId, memberNames);
                if (ch == null) continue;

                // 移除默认世界频道（如果从文件加载到了）
                if (ch.isWorld()) {
                    channels.removeIf(c -> c.isWorld());
                }
                channels.add(ch);

                // 加载消息
                List<ChatMessage> msgs = new ArrayList<>();
                List<Map<String, Object>> msgList = (List<Map<String, Object>>) chObj.get("messages");
                if (msgList != null) {
                    for (Map<String, Object> obj : msgList) {
                        ChatMessage msg = deserializeMessage(obj);
                        if (msg != null) {
                            // 矫正 channelId
                            msg = new ChatMessage(msg.senderUUID(), msg.senderName(), msg.content(),
                                    msg.time(), msg.isOwn(), msg.isSystem(),
                                    msg.rawPlayerName(), chId, msg.duplicateCount(), msg.messageHash());
                            msgs.add(msg);
                        }
                    }
                    while (msgs.size() > MAX_MESSAGES) msgs.remove(0);
                }
                channelMessages.put(chId, msgs);
            }
        } catch (Exception ignored) {}
    }

    private static ChatChannel restoreChannel(String chId, String typeStr, String displayName,
                                               String partnerName, String groupId,
                                               List<String> memberNames) {
        if (chId == null || typeStr == null) return null;
        try {
            ChatChannel.ChannelType type = ChatChannel.ChannelType.valueOf(typeStr);
            switch (type) {
                case WORLD:
                    return ChatChannel.world();
                case PRIVATE:
                    if (partnerName == null) return null;
                    // UUID 不可恢复（离线了），用 nil UUID
                    return ChatChannel.privateChat(partnerName, new UUID(0L, 0L));
                case GROUP:
                    if (groupId == null) return null;
                    List<String> names = memberNames != null ? memberNames : Collections.emptyList();
                    return ChatChannel.group(groupId, displayName != null ? displayName : groupId,
                            names, Collections.emptyList());
                default:
                    return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static ChatMessage deserializeMessage(Map<String, Object> obj) {
        try {
            UUID uuid = UUID.fromString((String) obj.get("senderUUID"));
            ITextComponent senderName = new TextComponentString((String) obj.get("senderName"));
            String contentJson = (String) obj.get("content");
            ITextComponent content = ITextComponent.Serializer.jsonToComponent(contentJson);
            if (content == null) content = new TextComponentString("");
            LocalTime time = LocalTime.parse((String) obj.get("time"), DateTimeFormatter.ISO_LOCAL_TIME);
            boolean own = Boolean.TRUE.equals(obj.get("isOwn"));
            boolean sys = Boolean.TRUE.equals(obj.get("isSystem"));
            String rpn = (String) obj.get("rawPlayerName");
            return new ChatMessage(uuid, senderName, content, time, own, sys, rpn,
                    "world", 1, "");
        } catch (Exception e) {
            return null;
        }
    }
}
