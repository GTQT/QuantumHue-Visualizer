package meowmel.quantumhue.chat;

import java.util.*;

/**
 * 频道数据模型
 *
 * 三种频道类型:
 *   WORLD   — 世界频道（所有人可见，走原版聊天管道）
 *   PRIVATE — 私聊（一对一）
 *   GROUP   — 群聊（多人）
 */
public class ChatChannel {

    public enum ChannelType {
        WORLD,
        PRIVATE,
        GROUP
    }

    private final ChannelType type;
    private final String id;          // WORLD: "world", PRIVATE: "priv:<partnerName>", GROUP: "group:<groupId>"
    private final String displayName; // 侧边栏显示名
    private final UUID partner;       // PRIVATE 专用：对方 UUID
    private final String partnerName; // PRIVATE 专用：对方名
    private final String groupId;     // GROUP 专用：群 ID
    private final List<String> memberNames; // GROUP 专用：成员名列表
    private final List<UUID> memberUuids;   // GROUP 专用：成员 UUID 列表

    // unread tracking
    private int unreadCount;
    private long lastReadTime;

    private ChatChannel(ChannelType type, String id, String displayName,
                        UUID partner, String partnerName,
                        String groupId, List<String> memberNames, List<UUID> memberUuids) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.partner = partner;
        this.partnerName = partnerName;
        this.groupId = groupId;
        this.memberNames = memberNames != null ? memberNames : Collections.emptyList();
        this.memberUuids = memberUuids != null ? memberUuids : Collections.emptyList();
        this.lastReadTime = System.currentTimeMillis();
    }

    // --- 工厂方法 ---

    public static ChatChannel world() {
        return new ChatChannel(ChannelType.WORLD, "world", "§b世界频道", null, null, null, null, null);
    }

    public static ChatChannel privateChat(String partnerName, UUID partnerUuid) {
        return new ChatChannel(ChannelType.PRIVATE,
                "priv:" + partnerName,
                partnerName,
                partnerUuid, partnerName, null, null, null);
    }

    public static ChatChannel group(String groupId, String groupName,
                                     List<String> memberNames, List<UUID> memberUuids) {
        return new ChatChannel(ChannelType.GROUP,
                "group:" + groupId,
                groupName,
                null, null, groupId, memberNames, memberUuids);
    }

    // --- Getters ---

    public ChannelType getType() { return type; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public UUID getPartner() { return partner; }
    public String getPartnerName() { return partnerName; }
    public String getGroupId() { return groupId; }
    public List<String> getMemberNames() { return memberNames; }
    public List<UUID> getMemberUuids() { return memberUuids; }

    public boolean isWorld() { return type == ChannelType.WORLD; }
    public boolean isPrivate() { return type == ChannelType.PRIVATE; }
    public boolean isGroup() { return type == ChannelType.GROUP; }

    public int getUnreadCount() { return unreadCount; }
    public void incrementUnread() { unreadCount++; }
    public void clearUnread() { unreadCount = 0; }
    public long getLastReadTime() { return lastReadTime; }
    public void markRead() { lastReadTime = System.currentTimeMillis(); unreadCount = 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatChannel)) return false;
        return id.equals(((ChatChannel) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
