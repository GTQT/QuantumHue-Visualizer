package meowmel.quantumhue.stock;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个玩家的交易账户。
 *
 * <p>钱包余额不在这里：它始终以 {@code EconomyBridge} 读到的实时值为准，账户只保存
 * “已经冻结的保证金”、持仓、记录和离线通知。这样别的模组直接改钱也不会与账户对不上。
 */
public final class StockAccount {

    public final UUID player;
    /** 冻结的保证金合计（分）。 */
    public long escrow;
    /** 离线期间已经结算、尚未写回钱包的现金（分），登录时补记。 */
    public long pendingCash;
    public long nextPositionId = 1L;
    public final List<StockPosition> positions = new ArrayList<>();
    public final List<StockTradeRecord> records = new ArrayList<>();
    /** 玩家离线期间发生的强平/保护触发，登录时逐条补发。 */
    public final List<String> notices = new ArrayList<>();

    public StockAccount(UUID player) {
        this.player = player;
    }

    public StockPosition position(long id) {
        for (StockPosition position : positions) {
            if (position.id == id) {
                return position;
            }
        }
        return null;
    }

    /** 某品种某方向的持仓张数合计，用于限仓检查。 */
    public long totalQuantity(String productId, int direction) {
        long total = 0L;
        for (StockPosition position : positions) {
            if (position.productId.equals(productId) && position.direction == direction) {
                total += position.quantity;
            }
        }
        return total;
    }

    public StockPosition open(String productId, String productName, int direction, long quantity,
                              long entryPrice, long margin) {
        StockPosition position = new StockPosition();
        position.id = nextPositionId++;
        position.productId = productId;
        position.productName = productName;
        position.direction = direction;
        position.quantity = quantity;
        position.entryPrice = entryPrice;
        position.margin = margin;
        position.openedAt = System.currentTimeMillis();
        positions.add(position);
        escrow += margin;
        return position;
    }

    public void addRecord(StockTradeRecord record) {
        records.add(0, record);
        while (records.size() > StockConfig.MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
    }

    public void addNotice(String notice) {
        if (notice == null || notice.isEmpty()) {
            return;
        }
        notices.add(notice);
        while (notices.size() > StockConfig.MAX_NOTICES) {
            notices.remove(0);
        }
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("u", player.toString());
        tag.setLong("esc", escrow);
        tag.setLong("cash", pendingCash);
        tag.setLong("next", nextPositionId);

        NBTTagList positionList = new NBTTagList();
        for (StockPosition position : positions) {
            positionList.appendTag(position.writeToNbt());
        }
        tag.setTag("positions", positionList);

        NBTTagList recordList = new NBTTagList();
        for (StockTradeRecord record : records) {
            recordList.appendTag(record.writeToNbt());
        }
        tag.setTag("records", recordList);

        NBTTagList noticeList = new NBTTagList();
        for (String notice : notices) {
            noticeList.appendTag(new NBTTagString(notice));
        }
        tag.setTag("notices", noticeList);
        return tag;
    }

    public static StockAccount readFromNbt(NBTTagCompound tag) {
        UUID player;
        try {
            player = UUID.fromString(tag.getString("u"));
        } catch (Throwable invalid) {
            player = new UUID(0L, 0L);
        }
        StockAccount account = new StockAccount(player);
        account.escrow = Math.max(0L, tag.getLong("esc"));
        account.pendingCash = tag.getLong("cash");
        account.nextPositionId = Math.max(1L, tag.getLong("next"));

        NBTTagList positionList = tag.getTagList("positions", 10);
        for (NBTBase base : positionList) {
            StockPosition position = StockPosition.readFromNbt((NBTTagCompound) base);
            if (position.quantity > 0L && StockConfig.product(position.productId) != null) {
                account.positions.add(position);
            }
        }

        NBTTagList recordList = tag.getTagList("records", 10);
        for (NBTBase base : recordList) {
            account.records.add(StockTradeRecord.readFromNbt((NBTTagCompound) base));
        }
        while (account.records.size() > StockConfig.MAX_RECORDS) {
            account.records.remove(account.records.size() - 1);
        }

        // 以持仓保证金为准重算冻结额，顺便修复旧存档中已被移除品种留下的残值。
        long totalMargin = 0L;
        for (StockPosition position : account.positions) {
            totalMargin += Math.max(0L, position.margin);
        }
        account.escrow = totalMargin;

        NBTTagList noticeList = tag.getTagList("notices", 8);
        for (NBTBase base : noticeList) {
            account.notices.add(((NBTTagString) base).getString());
        }
        return account;
    }
}
