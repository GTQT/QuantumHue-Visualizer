package meowmel.quantumhue.market;

import meowmel.quantumhue.QuantumHue;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 离线收益的服务端存档。 */
public class OfflineEarningsDatabase extends WorldSavedData {

    private static final String DATA_NAME = "quantumhue_offline_earnings";
    private final ConcurrentHashMap<UUID, List<OfflineEarning>> earnings = new ConcurrentHashMap<>();

    public OfflineEarningsDatabase() {
        super(DATA_NAME);
    }

    public OfflineEarningsDatabase(String name) {
        super(name);
    }

    public static OfflineEarningsDatabase get(World world) {
        MapStorage storage = world.getMapStorage();
        OfflineEarningsDatabase instance = null;
        if (storage != null) {
            instance = (OfflineEarningsDatabase) storage.getOrLoadData(OfflineEarningsDatabase.class, DATA_NAME);
        }
        if (instance == null) {
            instance = new OfflineEarningsDatabase();
            if (storage != null) {
                storage.setData(DATA_NAME, instance);
            }
        }
        return instance;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        earnings.clear();
        NBTTagList list = nbt.getTagList("earnings", 10);
        for (NBTBase tag : list) {
            OfflineEarning earning = new OfflineEarning((NBTTagCompound) tag);
            earnings.computeIfAbsent(earning.playerUUID, key -> new ArrayList<>()).add(earning);
        }
        int total = 0;
        for (List<OfflineEarning> list2 : earnings.values()) {
            total += list2.size();
        }
        QuantumHue.LOGGER.info("[Shop] 加载离线收益数据：{} 条", total);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (List<OfflineEarning> playerEarnings : earnings.values()) {
            for (OfflineEarning earning : playerEarnings) {
                list.appendTag(earning.writeToNBT());
            }
        }
        nbt.setTag("earnings", list);
        return nbt;
    }

    @Override
    public void markDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()
                && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            super.markDirty();
        }
    }

    public void addEarning(UUID playerUUID, long amount, String source) {
        earnings.computeIfAbsent(playerUUID, key -> new ArrayList<>())
                .add(new OfflineEarning(playerUUID, amount, source));
        markDirty();
    }

    public void addEarning(UUID playerUUID, long amount, ITextComponent source) {
        earnings.computeIfAbsent(playerUUID, key -> new ArrayList<>())
                .add(new OfflineEarning(playerUUID, amount, source));
        markDirty();
    }

    public List<OfflineEarning> getAndClearEarnings(UUID playerUUID) {
        List<OfflineEarning> playerEarnings = earnings.remove(playerUUID);
        if (playerEarnings != null) {
            markDirty();
        }
        return playerEarnings != null ? playerEarnings : new ArrayList<>();
    }

    public long getTotalEarnings(UUID playerUUID) {
        List<OfflineEarning> playerEarnings = earnings.get(playerUUID);
        if (playerEarnings == null) {
            return 0L;
        }
        long total = 0L;
        for (OfflineEarning earning : playerEarnings) {
            total += earning.amount;
        }
        return total;
    }

    public int getEarningCount(UUID playerUUID) {
        List<OfflineEarning> playerEarnings = earnings.get(playerUUID);
        return playerEarnings != null ? playerEarnings.size() : 0;
    }

    public void cleanupOldEarnings(long maxAgeMillis) {
        boolean changed = false;
        for (List<OfflineEarning> playerEarnings : earnings.values()) {
            if (playerEarnings.removeIf(earning -> System.currentTimeMillis() - earning.createTime > maxAgeMillis)) {
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }
}
