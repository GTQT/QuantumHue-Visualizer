package meowmel.quantumhue.stock;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟交易的主世界存档：行情引擎状态 + 每个玩家的账户。
 *
 * <p>整份数据只有服务端会写，客户端拿到的是 {@link StockSnapshot} 的副本。
 * 每收一根 K 线、每次成交都会 {@link #markDirty()}，由原版按周期落盘。
 */
public final class StockDatabase extends WorldSavedData {

    public static final String DATA_NAME = "quantumhue_stock";

    private final StockMarket market = new StockMarket();
    private final Map<UUID, StockAccount> accounts = new LinkedHashMap<>();
    private long sequence;

    public StockDatabase() {
        this(DATA_NAME);
    }

    public StockDatabase(String name) {
        super(name);
    }

    /** 取主世界存档；不存在时创建并挂到 MapStorage 上。 */
    public static StockDatabase get(World world) {
        if (world == null) {
            return null;
        }
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            return null;
        }
        StockDatabase data = (StockDatabase) storage.getOrLoadData(StockDatabase.class, DATA_NAME);
        if (data == null) {
            data = new StockDatabase();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public StockMarket market() {
        return market;
    }

    public StockAccount account(UUID player) {
        return accounts.computeIfAbsent(player, StockAccount::new);
    }

    public StockAccount accountIfPresent(UUID player) {
        return accounts.get(player);
    }

    public Iterable<StockAccount> accounts() {
        return accounts.values();
    }

    public long nextSequence() {
        return ++sequence;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        sequence = nbt.getLong("seq");
        accounts.clear();
        if (nbt.hasKey("market")) {
            market.readFromNbt(nbt.getCompoundTag("market"));
        }
        NBTTagList list = nbt.getTagList("accounts", 10);
        for (NBTBase base : list) {
            StockAccount account = StockAccount.readFromNbt((NBTTagCompound) base);
            if (account != null) {
                accounts.put(account.player, account);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("seq", sequence);
        nbt.setTag("market", market.writeToNbt());
        NBTTagList list = new NBTTagList();
        for (StockAccount account : accounts.values()) {
            list.appendTag(account.writeToNbt());
        }
        nbt.setTag("accounts", list);
        return nbt;
    }

    @Override
    public void markDirty() {
        // 存档只挂在主世界、只有服务端会改；客户端没有实例，直接落盘即可。
        super.markDirty();
    }
}
