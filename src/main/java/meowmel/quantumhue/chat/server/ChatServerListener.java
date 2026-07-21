package meowmel.quantumhue.chat.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.chat.packets.ChatGroupPacket;
import meowmel.quantumhue.chat.packets.ChatPrivatePacket;
import meowmel.quantumhue.chat.packets.GroupManagePacket;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 服务端群管理和消息转发
 *
 * 群组数据持久化到 &lt;world&gt;/quantumhue/groups.json，
 * 服务器重启/重进存档后自动恢复。
 */
public class ChatServerListener {

    private static final Map<String, Group> groups = new LinkedHashMap<>();
    private static int groupCounter;
    private static final Gson GSON = new Gson();

    public static class Group {
        public final String id;
        public String name;
        public final UUID owner;
        public final Set<UUID> members;
        /** uuid → 玩家名（持久化用，离线时也能查到名字） */
        public final Map<UUID, String> memberNames;

        public Group(String id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members = new LinkedHashSet<>();
            this.members.add(owner);
            this.memberNames = new LinkedHashMap<>();
        }
    }

    // ===== 群管理 =====

    public static void handleGroupManage(EntityPlayerMP player, GroupManagePacket pkt) {
        switch (pkt.getAction()) {
            case GroupManagePacket.CREATE: {
                String groupId = "g" + (++groupCounter);
                Group group = new Group(groupId, pkt.getGroupName(), player.getUniqueID());
                group.memberNames.put(player.getUniqueID(), player.getName());
                groups.put(groupId, group);

                // 通知创建者：群创建成功
                List<String> memberNames = getMemberNames(group);
                PacketHandler.sendTo(GroupManagePacket.createSync(groupId, group.name, memberNames), player);
                saveGroups();
                break;
            }
            case GroupManagePacket.INVITE: {
                Group group = groups.get(pkt.getGroupId());
                if (group == null) break;
                // 通知目标玩家
                EntityPlayerMP target = FMLCommonHandler.instance().getMinecraftServerInstance()
                        .getPlayerList().getPlayerByUsername(pkt.getTargetName());
                if (target != null) {
                    List<String> memberNames = getMemberNames(group);
                    PacketHandler.sendTo(GroupManagePacket.createSync(group.id, group.name, memberNames), target);
                }
                break;
            }
            case GroupManagePacket.JOIN: {
                Group group = groups.get(pkt.getGroupId());
                if (group == null) break;
                EntityPlayerMP joiner = FMLCommonHandler.instance().getMinecraftServerInstance()
                        .getPlayerList().getPlayerByUsername(pkt.getTargetName());
                if (joiner != null) {
                    group.members.add(joiner.getUniqueID());
                    group.memberNames.put(joiner.getUniqueID(), joiner.getName());
                    syncGroupToMembers(group);
                    saveGroups();
                }
                break;
            }
            case GroupManagePacket.LEAVE: {
                Group group = groups.get(pkt.getGroupId());
                if (group == null) break;
                group.members.remove(player.getUniqueID());
                group.memberNames.remove(player.getUniqueID());
                if (group.members.isEmpty()) {
                    groups.remove(pkt.getGroupId());
                } else {
                    syncGroupToMembers(group);
                }
                saveGroups();
                break;
            }
        }
    }

    // ===== 群消息转发 =====

    public static void handleGroupMessage(EntityPlayerMP sender, ChatGroupPacket pkt) {
        Group group = groups.get(pkt.getGroupId());
        if (group == null) return;

        // 广播给所有群成员（不包括发送者自己，客户端已本地回显）
        for (UUID memberUuid : group.members) {
            if (memberUuid.equals(sender.getUniqueID())) continue;
            EntityPlayerMP member = FMLCommonHandler.instance().getMinecraftServerInstance()
                    .getPlayerList().getPlayerByUUID(memberUuid);
            if (member != null) {
                PacketHandler.sendTo(new ChatGroupPacket(
                        pkt.getGroupId(), pkt.getMessage(),
                        sender.getName(), sender.getUniqueID()), member);
            }
        }
    }

    // ===== 辅助 =====

    private static List<String> getMemberNames(Group group) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : group.members) {
            // 优先用持久化的名字，其次在线查询，最后 fallback 为 UUID
            String name = group.memberNames.get(uuid);
            if (name == null || name.isEmpty()) {
                EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance()
                        .getPlayerList().getPlayerByUUID(uuid);
                name = player != null ? player.getName() : uuid.toString();
            }
            names.add(name);
        }
        return names;
    }

    private static void syncGroupToMembers(Group group) {
        List<String> memberNames = getMemberNames(group);
        for (UUID memberUuid : group.members) {
            EntityPlayerMP member = FMLCommonHandler.instance().getMinecraftServerInstance()
                    .getPlayerList().getPlayerByUUID(memberUuid);
            if (member != null) {
                PacketHandler.sendTo(GroupManagePacket.createSync(group.id, group.name, memberNames), member);
            }
        }
    }

    // ===== 持久化 =====

    private static File getGroupsFile() {
        World world = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
        if (world == null) return null;
        File worldDir = world.getSaveHandler().getWorldDirectory();
        return new File(worldDir, "quantumhue/groups.json");
    }

    private static void saveGroups() {
        File file = getGroupsFile();
        if (file == null) return;
        file.getParentFile().mkdirs();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nextId", groupCounter);
        List<Map<String, Object>> groupList = new ArrayList<>();
        for (Group g : groups.values()) {
            groupList.add(groupToJson(g));
        }
        root.put("groups", groupList);

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (Exception e) {
            QuantumHue.LOGGER.error("Failed to save groups", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadGroups(File worldDir) {
        File file = new File(worldDir, "quantumhue/groups.json");
        if (!file.exists()) return;

        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, Object> root = GSON.fromJson(r,
                    new TypeToken<Map<String, Object>>(){}.getType());
            if (root == null) return;

            groupCounter = ((Number) root.getOrDefault("nextId", 0)).intValue();
            List<Map<String, Object>> groupList = (List<Map<String, Object>>) root.get("groups");
            if (groupList == null) return;

            groups.clear();
            for (Map<String, Object> obj : groupList) {
                Group g = groupFromJson(obj);
                groups.put(g.id, g);
            }
            QuantumHue.LOGGER.info("Loaded {} chat groups from disk", groups.size());
        } catch (Exception e) {
            QuantumHue.LOGGER.error("Failed to load groups", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> groupToJson(Group g) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", g.id);
        obj.put("name", g.name);
        obj.put("owner", g.owner.toString());
        List<Map<String, String>> memberList = new ArrayList<>();
        for (UUID uuid : g.members) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("uuid", uuid.toString());
            m.put("name", g.memberNames.getOrDefault(uuid, uuid.toString()));
            memberList.add(m);
        }
        obj.put("members", memberList);
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static Group groupFromJson(Map<String, Object> obj) {
        String id = (String) obj.get("id");
        String name = (String) obj.get("name");
        UUID owner = UUID.fromString((String) obj.get("owner"));
        Group group = new Group(id, name, owner);
        // 清掉构造函数自动添加的 owner，后面从 JSON 统一添加
        group.members.clear();
        group.memberNames.clear();
        List<Map<String, String>> memberList = (List<Map<String, String>>) obj.get("members");
        if (memberList != null) {
            for (Map<String, String> m : memberList) {
                UUID uuid = UUID.fromString(m.get("uuid"));
                String memberName = m.getOrDefault("name", uuid.toString());
                group.members.add(uuid);
                group.memberNames.put(uuid, memberName);
            }
        }
        return group;
    }

    // ===== 世界事件 =====

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) return;
        if (event.getWorld().provider.getDimension() != 0) return;
        loadGroups(event.getWorld().getSaveHandler().getWorldDirectory());
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.getWorld().isRemote) return;
        if (event.getWorld().provider.getDimension() != 0) return;
        saveGroups();
    }

    // ===== 玩家事件：登录时同步已有群 =====

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        for (Group group : groups.values()) {
            if (group.members.contains(player.getUniqueID())) {
                List<String> names = getMemberNames(group);
                PacketHandler.sendTo(GroupManagePacket.createSync(group.id, group.name, names), player);
            }
        }
    }
}
