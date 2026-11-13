package meowmel.quantumhue.lottery;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次抽奖的服务端结果，按行保存最终符号下标与中奖标记。
 *
 * <p>界面拿着最终结果做滚轮动画：每一列在延迟后停到 {@link #grid} 对应的符号上，
 * 动画期间不重新抽奖，所以屏幕上最后停下的图案与服务端结算完全一致。
 */
public final class LotteryResult {

    public final int rows;
    /** 行优先的符号下标：{@code row * 5 + column}。 */
    public final int[] grid;
    public final boolean[] wins;
    public final long wonMoney;
    /** 物品奖励的展示文本，例如“钻石 ×3”。 */
    public final List<String> rewardLines;

    public LotteryResult(int rows, int[] grid, boolean[] wins, long wonMoney, List<String> rewardLines) {
        this.rows = Math.max(1, Math.min(LotteryConfig.MAX_ROWS, rows));
        this.grid = grid == null ? new int[this.rows * LotteryConfig.COLUMNS] : grid;
        this.wins = wins == null ? new boolean[this.rows] : wins;
        this.wonMoney = Math.max(0L, wonMoney);
        this.rewardLines = rewardLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(rewardLines));
    }

    public boolean anyWin() {
        for (boolean win : wins) {
            if (win) {
                return true;
            }
        }
        return false;
    }

    public boolean rowWin(int row) {
        return row >= 0 && row < wins.length && wins[row];
    }

    public int symbolAt(int row, int column) {
        int index = row * LotteryConfig.COLUMNS + column;
        return index >= 0 && index < grid.length ? grid[index] : 0;
    }

    // ================================================================ 序列化

    public void write(ByteBuf buf) {
        buf.writeByte(rows);
        for (int i = 0; i < rows * LotteryConfig.COLUMNS; i++) {
            buf.writeByte(i < grid.length ? grid[i] & 0xFF : 0);
        }
        int flags = 0;
        for (int row = 0; row < wins.length && row < 8; row++) {
            if (wins[row]) {
                flags |= 1 << row;
            }
        }
        buf.writeByte(flags);
        buf.writeLong(wonMoney);
        buf.writeInt(rewardLines.size());
        for (String line : rewardLines) {
            ByteBufUtils.writeUTF8String(buf, line == null ? "" : line);
        }
    }

    public static LotteryResult read(ByteBuf buf) {
        int rows = Math.max(1, Math.min(LotteryConfig.MAX_ROWS, buf.readByte()));
        int[] grid = new int[rows * LotteryConfig.COLUMNS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = buf.readByte() & 0xFF;
        }
        int flags = buf.readByte() & 0xFF;
        boolean[] wins = new boolean[rows];
        for (int row = 0; row < rows; row++) {
            wins[row] = (flags & (1 << row)) != 0;
        }
        long wonMoney = buf.readLong();
        int lineCount = Math.min(32, Math.max(0, buf.readInt()));
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(ByteBufUtils.readUTF8String(buf));
        }
        return new LotteryResult(rows, grid, wins, wonMoney, lines);
    }
}
