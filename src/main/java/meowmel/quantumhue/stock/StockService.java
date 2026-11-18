package meowmel.quantumhue.stock;

import meowmel.quantumhue.market.EconomyBridge;
import meowmel.quantumhue.stock.StockSnapshot.PositionView;
import meowmel.quantumhue.stock.StockSnapshot.ProductView;
import meowmel.quantumhue.stock.StockSnapshot.RecordView;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟交易的唯一权威入口。
 *
 * <p>所有下单、平仓、保护单都由服务端在这里校验并记账，客户端只能提议操作；
 * 行情每秒推进一次，保护单和强平也在同一处检查，因此离线玩家的持仓同样会被结算。
 *
 * <p>金额一律用“分”的长整型，手续费向上取整、盈亏向下取整，避免玩家靠四舍五入套利。
 */
public final class StockService {

    /** 上次操作时间，用于 250ms 冷却。 */
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    /** 每个玩家上次收到 K 线时的当前 K 线起点，用于判断是否需要补发整段 K 线。 */
    private static final Map<UUID, Long> LAST_BAR_SENT = new HashMap<>();

    private StockService() {}

    // ================================================================ 查询

    /** 打开页签：整包同步。 */
    public static StockSnapshot request(EntityPlayerMP player) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        return build(player, database, account, true, true, "");
    }

    /** 每秒轮询：只带变化部分。 */
    public static StockSnapshot poll(EntityPlayerMP player) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        return build(player, database, account, false, false, "");
    }

    // ================================================================ 交易

    public static StockSnapshot open(EntityPlayerMP player, String productId, int direction,
                                     long quantity, int leverage) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        if (!checkCooldown(player)) {
            return build(player, database, account, false, true, "操作过于频繁，请稍后再试");
        }
        StockProduct product = StockConfig.product(productId);
        if (product == null) {
            return build(player, database, account, false, true, "未知品种");
        }
        if (direction != 1 && direction != -1) {
            return build(player, database, account, false, true, "方向无效");
        }
        if (!StockConfig.validQuantity(quantity)) {
            return build(player, database, account, false, true, "数量必须在 1 到 "
                    + StockConfig.MAX_POSITION + " 张之间");
        }
        if (!StockConfig.validLeverage(leverage)) {
            return build(player, database, account, false, true, "杠杆档位无效");
        }
        if (account.positions.size() >= StockConfig.MAX_POSITIONS) {
            return build(player, database, account, false, true, "持仓笔数已达上限 "
                    + StockConfig.MAX_POSITIONS + " 笔，请先平掉一部分");
        }
        if (!EconomyBridge.available()) {
            return build(player, database, account, false, true, "经济系统不可用，无法交易");
        }
        if (account.totalQuantity(productId, direction) + quantity > StockConfig.MAX_POSITION) {
            return build(player, database, account, false, true, "超出单品种限仓 "
                    + StockConfig.MAX_POSITION + " 张");
        }

        StockMarket market = database.market();
        long mark = market.markPrice(productId);
        long entry = applySlippage(mark, direction);
        long notional = entry * quantity;
        long margin = StockConfig.ceilDiv(notional, leverage);
        long fee = fee(notional);
        long total = margin + fee;

        long wallet = EconomyBridge.get(player);
        if (wallet < total) {
            return build(player, database, account, false, true, "可用余额不足，需要 "
                    + money(total) + "，当前 " + money(wallet));
        }
        if (!EconomyBridge.set(player, wallet - total)) {
            return build(player, database, account, false, true, "扣款失败，请稍后再试");
        }

        account.open(productId, product.name, direction, quantity, entry, margin);
        account.addRecord(new StockTradeRecord(System.currentTimeMillis(), productId, product.name,
                direction, quantity, entry, fee, 0L, StockTradeRecord.Type.OPEN));
        database.markDirty();
        return build(player, database, account, false, true, "");
    }

    public static StockSnapshot close(EntityPlayerMP player, long positionId, long quantity) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        if (!checkCooldown(player)) {
            return build(player, database, account, false, true, "操作过于频繁，请稍后再试");
        }
        StockPosition position = account.position(positionId);
        if (position == null) {
            return build(player, database, account, false, true, "持仓不存在");
        }
        if (quantity <= 0L || quantity > position.quantity) {
            quantity = position.quantity;
        }
        if (!settle(player, database, account, position, quantity, StockTradeRecord.Type.CLOSE)) {
            return build(player, database, account, false, true, "经济系统不可用，无法平仓");
        }
        return build(player, database, account, false, true, "");
    }

    public static StockSnapshot setProtection(EntityPlayerMP player, long positionId, int mode,
                                               long stopPrice, long takePrice,
                                               long stopAmount, long takeAmount) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        if (!checkCooldown(player)) {
            return build(player, database, account, false, true, "操作过于频繁，请稍后再试");
        }
        StockPosition position = account.position(positionId);
        if (position == null) {
            return build(player, database, account, false, true, "持仓不存在");
        }
        long mark = database.market().markPrice(position.productId);

        if (mode == StockConfig.PROTECTION_PRICE) {
            if (stopPrice <= 0L && takePrice <= 0L) {
                return build(player, database, account, false, true, "请至少填写止损价或止盈价");
            }
            if (position.direction > 0) {
                if (stopPrice > 0L && stopPrice >= mark) {
                    return build(player, database, account, false, true, "多单止损价必须低于当前价");
                }
                if (takePrice > 0L && takePrice <= mark) {
                    return build(player, database, account, false, true, "多单止盈价必须高于当前价");
                }
            } else {
                if (stopPrice > 0L && stopPrice <= mark) {
                    return build(player, database, account, false, true, "空单止损价必须高于当前价");
                }
                if (takePrice > 0L && takePrice >= mark) {
                    return build(player, database, account, false, true, "空单止盈价必须低于当前价");
                }
            }
            position.protectionMode = StockConfig.PROTECTION_PRICE;
            position.stopPrice = Math.max(0L, stopPrice);
            position.takePrice = Math.max(0L, takePrice);
            position.stopAmount = 0L;
            position.takeAmount = 0L;
        } else if (mode == StockConfig.PROTECTION_PROFIT) {
            if (stopAmount <= 0L && takeAmount <= 0L) {
                return build(player, database, account, false, true, "请至少填写止损或止盈金额");
            }
            position.protectionMode = StockConfig.PROTECTION_PROFIT;
            position.stopPrice = 0L;
            position.takePrice = 0L;
            position.stopAmount = Math.max(0L, Math.min(stopAmount, position.margin));
            position.takeAmount = Math.max(0L, takeAmount);
        } else {
            return build(player, database, account, false, true, "保护模式无效");
        }

        database.markDirty();
        return build(player, database, account, false, true, "");
    }

    public static StockSnapshot clearProtection(EntityPlayerMP player, long positionId) {
        StockDatabase database = database(player);
        if (database == null) {
            return errorSnapshot("存档尚未就绪");
        }
        StockAccount account = database.account(player.getUniqueID());
        if (!checkCooldown(player)) {
            return build(player, database, account, false, true, "操作过于频繁，请稍后再试");
        }
        StockPosition position = account.position(positionId);
        if (position == null) {
            return build(player, database, account, false, true, "持仓不存在");
        }
        position.clearProtection();
        database.markDirty();
        return build(player, database, account, false, true, "");
    }

    // ================================================================ 结算

    /**
     * 平掉一笔持仓（可部分）。
     *
     * @return 经济系统写入是否成功；失败时不做任何修改
     */
    private static boolean settle(EntityPlayerMP player, StockDatabase database, StockAccount account,
                                  StockPosition position, long quantity, StockTradeRecord.Type type) {
        StockMarket market = database.market();
        long mark = market.markPrice(position.productId);
        long exit = applySlippage(mark, -position.direction);
        long release = position.releaseMargin(quantity);
        long gross = (long) position.direction * (exit - position.entryPrice) * quantity;
        long closeFee = fee(exit * quantity);
        long proceeds = release + gross - closeFee;

        long wallet = EconomyBridge.get(player);
        if (wallet < 0L) {
            wallet = 0L;
        }
        long newWallet = Math.max(0L, wallet + proceeds);
        if (!EconomyBridge.set(player, newWallet)) {
            return false;
        }

        account.escrow = Math.max(0L, account.escrow - release);
        if (quantity >= position.quantity) {
            account.positions.remove(position);
        } else {
            position.quantity -= quantity;
            position.margin -= release;
        }
        account.addRecord(new StockTradeRecord(System.currentTimeMillis(), position.productId,
                position.productName, position.direction, quantity, exit, closeFee,
                gross - closeFee, type));
        database.markDirty();
        return true;
    }

    /** 强平/保护触发：整笔市价平掉，玩家不在线时只记通知。 */
    private static void forceClose(MinecraftServer server, StockDatabase database, StockAccount account,
                                   StockPosition position, StockTradeRecord.Type type) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(account.player);
        if (online == null) {
            // 离线玩家没有 EntityPlayer 可供经济桥读写：直接把结果记成通知，
            // 登录后由 onLogin 补发；余额在离线期间无法变动，这是刻意取舍。
            account.positions.remove(position);
            account.escrow = Math.max(0L, account.escrow - position.margin);
            long mark = database.market().markPrice(position.productId);
            long closeFee = fee(mark * position.quantity);
            long gross = (long) position.direction * (mark - position.entryPrice) * position.quantity;
            long proceeds = position.margin + gross - closeFee;
            account.pendingCash += proceeds;
            account.addRecord(new StockTradeRecord(System.currentTimeMillis(), position.productId,
                    position.productName, position.direction, position.quantity, mark,
                    closeFee, gross - closeFee, type));
            account.addNotice(label(type) + "：" + position.productName + " "
                    + (position.direction > 0 ? "多" : "空") + " " + position.quantity
                    + " 张已按市价平仓，盈亏 " + money(gross - closeFee));
            database.markDirty();
            return;
        }

        long mark = database.market().markPrice(position.productId);
        long exit = applySlippage(mark, -position.direction);
        long gross = (long) position.direction * (exit - position.entryPrice) * position.quantity;
        long closeFee = fee(exit * position.quantity);
        long proceeds = position.margin + gross - closeFee;
        long wallet = Math.max(0L, EconomyBridge.get(online));
        long newWallet = Math.max(0L, wallet + proceeds);
        EconomyBridge.set(online, newWallet);

        account.escrow = Math.max(0L, account.escrow - position.margin);
        account.positions.remove(position);
        account.addRecord(new StockTradeRecord(System.currentTimeMillis(), position.productId,
                position.productName, position.direction, position.quantity, exit, closeFee,
                gross - closeFee, type));
        online.sendMessage(new TextComponentString(TextFormatting.GOLD + "[交易终端] "
                + TextFormatting.RESET + label(type) + "：" + position.productName + " "
                + (position.direction > 0 ? "多" : "空") + " " + position.quantity
                + " 张已按市价平仓，盈亏 " + money(gross - closeFee)));
        database.markDirty();
    }

    // ================================================================ 服务端 tick

    /** 每 world tick 调用；内部只在该出价时推进。 */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        StockDatabase database = StockDatabase.get(server.getWorld(0));
        if (database == null) {
            return;
        }
        StockMarket market = database.market();
        if (!market.tick()) {
            return;
        }
        if (market.consumeBarClosed()) {
            database.markDirty();
        }

        for (StockAccount account : new ArrayList<>(accountsOf(database))) {
            if (account.positions.isEmpty()) {
                continue;
            }
            for (StockPosition position : new ArrayList<>(account.positions)) {
                if (account.position(position.id) == null) {
                    continue;
                }
                long mark = market.markPrice(position.productId);
                long unrealized = position.unrealized(mark);
                StockTradeRecord.Type trigger = protectionTrigger(position, mark, unrealized);
                if (trigger != null) {
                    forceClose(server, database, account, position, trigger);
                    continue;
                }
                long maintenance = position.margin * StockConfig.MAINTENANCE_NUMERATOR
                        / StockConfig.MAINTENANCE_DENOMINATOR;
                if (position.margin + unrealized <= maintenance) {
                    forceClose(server, database, account, position, StockTradeRecord.Type.LIQUIDATE);
                }
            }
        }
    }

    /** 登录补发离线通知。 */
    public static void onLogin(EntityPlayerMP player) {
        StockDatabase database = database(player);
        if (database == null) {
            return;
        }
        StockAccount account = database.account(player.getUniqueID());
        if (account.pendingCash != 0L && EconomyBridge.available()) {
            long wallet = Math.max(0L, EconomyBridge.get(player));
            long target = Math.max(0L, wallet + account.pendingCash);
            if (EconomyBridge.set(player, target)) {
                player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[交易终端] "
                        + TextFormatting.RESET + "离线结算已入账：" + money(account.pendingCash)));
                account.pendingCash = 0L;
                database.markDirty();
            }
        }
        if (account.notices.isEmpty()) {
            return;
        }
        for (String notice : account.notices) {
            player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[交易终端] "
                    + TextFormatting.RESET + notice));
        }
        account.notices.clear();
        database.markDirty();
    }

    private static StockTradeRecord.Type protectionTrigger(StockPosition position, long mark, long unrealized) {
        if (position.protectionMode == StockConfig.PROTECTION_PRICE) {
            if (position.stopPrice > 0L) {
                boolean hit = position.direction > 0 ? mark <= position.stopPrice : mark >= position.stopPrice;
                if (hit) {
                    return StockTradeRecord.Type.STOP;
                }
            }
            if (position.takePrice > 0L) {
                boolean hit = position.direction > 0 ? mark >= position.takePrice : mark <= position.takePrice;
                if (hit) {
                    return StockTradeRecord.Type.TAKE;
                }
            }
        } else if (position.protectionMode == StockConfig.PROTECTION_PROFIT) {
            if (position.stopAmount > 0L && unrealized <= -position.stopAmount) {
                return StockTradeRecord.Type.STOP;
            }
            if (position.takeAmount > 0L && unrealized >= position.takeAmount) {
                return StockTradeRecord.Type.TAKE;
            }
        }
        return null;
    }

    // ================================================================ 快照

    private static StockSnapshot build(EntityPlayerMP player, StockDatabase database, StockAccount account,
                                       boolean full, boolean includeRecords, String error) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.sequence = database.nextSequence();
        snapshot.full = full;
        snapshot.error = error == null ? "" : error;

        StockMarket market = database.market();
        Long lastBar = LAST_BAR_SENT.get(account.player);
        boolean includeBars = full || includeRecords || lastBar == null
                || lastBar.longValue() != market.currentBarStart();
        snapshot.bars = includeBars;
        if (includeBars) {
            LAST_BAR_SENT.put(account.player, market.currentBarStart());
        }

        long wallet = EconomyBridge.get(player);
        long unrealizedTotal = 0L;
        for (StockPosition position : account.positions) {
            unrealizedTotal += position.unrealized(market.markPrice(position.productId));
        }
        snapshot.wallet = wallet;
        snapshot.escrow = account.escrow;
        snapshot.equity = wallet + account.escrow + account.pendingCash + unrealizedTotal;
        snapshot.usedMargin = account.escrow;
        snapshot.available = wallet;

        for (StockProduct product : StockConfig.products()) {
            ProductView view = new ProductView();
            view.id = product.id;
            view.name = product.name;
            view.price = market.markPrice(product.id);
            view.change = market.changePct100(product.id);
            if (includeBars) {
                view.bars.addAll(market.viewBars(product.id, StockConfig.SYNC_BARS));
            }
            snapshot.products.add(view);
        }

        for (StockPosition position : account.positions) {
            PositionView view = new PositionView();
            view.id = position.id;
            view.productId = position.productId;
            view.productName = position.productName;
            view.direction = position.direction;
            view.quantity = position.quantity;
            view.entryPrice = position.entryPrice;
            view.markPrice = market.markPrice(position.productId);
            view.margin = position.margin;
            view.unrealized = position.unrealized(view.markPrice);
            view.openedAt = position.openedAt;
            view.protectionMode = position.protectionMode;
            view.stopPrice = position.stopPrice;
            view.takePrice = position.takePrice;
            view.stopAmount = position.stopAmount;
            view.takeAmount = position.takeAmount;
            snapshot.positions.add(view);
        }

        if (includeRecords) {
            for (StockTradeRecord record : account.records) {
                RecordView view = new RecordView();
                view.type = record.type.ordinal();
                view.time = record.time;
                view.productName = record.productName;
                view.direction = record.direction;
                view.quantity = record.quantity;
                view.price = record.price;
                view.fee = record.fee;
                view.pnl = record.pnl;
                snapshot.records.add(view);
            }
        }

        if (!account.notices.isEmpty()) {
            snapshot.notice = String.join("；", account.notices);
            account.notices.clear();
            database.markDirty();
        }
        return snapshot;
    }

    private static StockSnapshot errorSnapshot(String error) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.error = error == null ? "" : error;
        return snapshot;
    }

    // ================================================================ 工具

    private static StockDatabase database(EntityPlayerMP player) {
        if (player == null || player.getServer() == null) {
            return null;
        }
        return StockDatabase.get(player.getServer().getWorld(0));
    }

    private static List<StockAccount> accountsOf(StockDatabase database) {
        List<StockAccount> accounts = new ArrayList<>();
        for (StockAccount account : database.accounts()) {
            accounts.add(account);
        }
        return accounts;
    }

    private static boolean checkCooldown(EntityPlayerMP player) {
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(player.getUniqueID());
        if (last != null && now - last < StockConfig.COOLDOWN_MS) {
            return false;
        }
        LAST_ACTION.put(player.getUniqueID(), now);
        return true;
    }

    /** 成交价：买入向上滑、卖出向下滑。 */
    private static long applySlippage(long price, int side) {
        if (side > 0) {
            return StockConfig.ceilDiv(price * (StockConfig.SLIPPAGE_DENOMINATOR
                    + StockConfig.SLIPPAGE_NUMERATOR), StockConfig.SLIPPAGE_DENOMINATOR);
        }
        return price * (StockConfig.SLIPPAGE_DENOMINATOR - StockConfig.SLIPPAGE_NUMERATOR)
                / StockConfig.SLIPPAGE_DENOMINATOR;
    }

    private static long fee(long notional) {
        return StockConfig.ceilDiv(Math.max(0L, notional) * StockConfig.FEE_NUMERATOR,
                StockConfig.FEE_DENOMINATOR);
    }

    private static String label(StockTradeRecord.Type type) {
        return type == StockTradeRecord.Type.LIQUIDATE ? "强制平仓" : type.label;
    }

    public static String money(long cents) {
        boolean negative = cents < 0L;
        long value = Math.abs(cents);
        return (negative ? "-" : "") + (value / 100L) + "." + String.format("%02d", value % 100L);
    }
}
