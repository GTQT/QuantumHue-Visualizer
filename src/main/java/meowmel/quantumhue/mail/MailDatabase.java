package meowmel.quantumhue.mail;

import meowmel.quantumhue.QuantumHue;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件系统的服务端存档：
 * <ul>
 *   <li>{@code claimed}：每名玩家已领取过的邮件 id，防止重复领取。</li>
 * </ul>
 *
 * <p>这里不存邮件正文：邮件只来自远程实时拉取，存档里留一份副本相当于给玩家一个可以脱机造邮件的
 * 入口，也不再符合“内容只存在于远程”的模型。
 */
public class MailDatabase extends WorldSavedData {

    private static final String DATA_NAME = "quantumhue_mail_data";

    private final Map<UUID, Set<String>> claimed = new ConcurrentHashMap<>();

    public MailDatabase() {
        super(DATA_NAME);
    }

    public MailDatabase(String name) {
        super(name);
    }

    public static MailDatabase get(World world) {
        MapStorage storage = world.getMapStorage();
        MailDatabase instance = null;
        if (storage != null) {
            instance = (MailDatabase) storage.getOrLoadData(MailDatabase.class, DATA_NAME);
        }
        if (instance == null) {
            instance = new MailDatabase();
            if (storage != null) {
                storage.setData(DATA_NAME, instance);
            }
        }
        return instance;
    }

    // ================================================================ 领取记录

    public boolean isClaimed(UUID player, String mailId) {
        Set<String> mails = claimed.get(player);
        return mails != null && mails.contains(mailId);
    }

    public void markClaimed(UUID player, String mailId) {
        claimed.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet()).add(mailId);
        markDirty();
    }

    public Set<String> claimedOf(UUID player) {
        Set<String> mails = claimed.get(player);
        return mails == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(mails));
    }

    // ================================================================ 持久化

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        claimed.clear();
        NBTTagList list = nbt.getTagList("claimed", 10);
        for (NBTBase base : list) {
            NBTTagCompound entry = (NBTTagCompound) base;
            try {
                UUID player = UUID.fromString(entry.getString("player"));
                String mail = entry.getString("mail");
                if (!mail.isEmpty()) {
                    claimed.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet()).add(mail);
                }
            } catch (Throwable invalid) {
                QuantumHue.LOGGER.warn("[Mail] 忽略损坏的领取记录");
            }
        }
        QuantumHue.LOGGER.info("[Mail] 加载邮件数据：{} 名玩家", claimed.size());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Set<String>> entry : claimed.entrySet()) {
            for (String mailId : entry.getValue()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("player", entry.getKey().toString());
                tag.setString("mail", mailId);
                list.appendTag(tag);
            }
        }
        nbt.setTag("claimed", list);
        return nbt;
    }

    @Override
    public void markDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()
                && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            super.markDirty();
        }
    }
}
