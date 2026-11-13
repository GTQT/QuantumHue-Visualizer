package meowmel.quantumhue.page;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.lottery.LotteryCatalog;
import meowmel.quantumhue.lottery.LotteryConfig;
import meowmel.quantumhue.lottery.LotteryResult;
import meowmel.quantumhue.lottery.client.LotteryClientStore;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.MessageLotteryAction;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 商店里的抽奖（老虎机）面板。
 *
 * <p>固定 5 列、最多 3 行。界面提供“抽一行 / 抽两行 / 抽三行”三个按钮：选择几行就只结算几行，
 * 费用为“每行基础价 × 行数”。抽奖结果完全由服务端决定，客户端只负责把最终图案“转”出来：
 * 服务端先返回 {@link LotteryResult}，这里按列延迟停下，动画结束后才显示中奖金额，避免玩家
 * 在动画中途看到结果。
 *
 * <p>组合面板范式与 {@code MailPanel} 相同：自己管理绘制、命中与滚动，宿主 {@link GuiShop}
 * 提供裁剪、文字、物品渲染与浮层。
 */
@SideOnly(Side.CLIENT)
final class LotteryPanel {

    private static final int COLUMNS = LotteryConfig.COLUMNS;
    private static final int MAX_ROWS = LotteryConfig.MAX_ROWS;
    private static final int CELL_GAP = 5;
    /** 第一列开始停下的时间；之后每列多转 180ms。 */
    private static final long ANIM_BASE_MS = 760L;
    private static final long ANIM_COL_STEP_MS = 180L;
    private static final long ANIM_SETTLE_MS = 260L;
    private static final long COLUMN_SETTLE_MS = 240L;
    private static final long WIN_PARTICLE_MS = 1_300L;
    private static final long WIN_COUNT_MS = 600L;
    private static final long PENDING_TIMEOUT_MS = 6_000L;
    private static final int WIN_GOLD_RGB = 0xFFD966;

    private final GuiShop host;
    private final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);

    private int x;
    private int y;
    private int w;
    private int h;

    private int gridX;
    private int gridY;
    private int cellW;
    private int cellH;

    /** 本次抽取行数；默认 1，点击两行/三行按钮后变化。 */
    private int selectedRows = 1;

    /** 奖池选择条：左右箭头与当前奖池名。 */
    private int poolPrevX;
    private int poolNextX;
    private int poolY;
    private int poolH;
    private boolean poolSwitchAvailable;

    /** 下标 1..3 对应三个抽奖按钮。 */
    private final int[] spinX = new int[MAX_ROWS + 1];
    private final int[] spinY = new int[MAX_ROWS + 1];
    private final int[] spinW = new int[MAX_ROWS + 1];
    private final int[] spinH = new int[MAX_ROWS + 1];
    private boolean spinAvailable;

    private int controlsBottom;
    private int payTop;
    private int payListTop;
    private int payListH;
    private int payScroll;
    private int payMaxScroll;

    private boolean spinPending;
    private long spinSentAt;
    private long spinReplyAtSend;

    private long lastRequestAt;
    private long shownResultSeq;
    private long animStart;
    private LotteryResult animResult;

    private int hoveredSymbol = -1;
    private List<String> hoverLines = Collections.emptyList();

    LotteryPanel(GuiShop host) {
        this.host = host;
        // 面板可能是在一次抽奖之后才创建的；把已有结果视为“看过”，避免重新打开时重播旧动画。
        this.shownResultSeq = LotteryClientStore.resultSeq();
        formatter.setGroupingUsed(true);
    }

    // ================================================================ 生命周期

    void open() {
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < 300L) {
            return;
        }
        lastRequestAt = now;
        PacketHandler.sendToServer(MessageLotteryAction.request());
    }

    void close() {
        spinPending = false;
    }

    void update() {
        if (!spinPending) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean answered = LotteryClientStore.spinReplySeq() != spinReplyAtSend;
        if (answered || now - spinSentAt > PENDING_TIMEOUT_MS) {
            spinPending = false;
        }
    }

    // ================================================================ 绘制

    void draw(int x, int y, int w, int h, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        hoveredSymbol = -1;
        hoverLines = Collections.emptyList();
        clearHitBoxes();

        LotteryCatalog catalog = LotteryClientStore.catalog();
        if (!catalog.enabled()) {
            drawCentered("§c抽奖暂未开放", y + h / 2 - 8);
            return;
        }
        if (catalog.isEmpty()) {
            drawCentered("§7奖池尚未配置，请通过 API 注册抽奖符号", y + h / 2 - 8);
            return;
        }

        long now = System.currentTimeMillis();
        refreshAnimation(now);
        int rows = clamp(selectedRows, 1, MAX_ROWS);
        LotteryCatalog.Pool pool = LotteryClientStore.selectedPool();
        if (pool == null) {
            drawCentered("§7奖池尚未配置，请通过 API 注册抽奖符号", y + h / 2 - 8);
            return;
        }

        drawHeader(catalog, pool, rows, now, mouseX, mouseY);
        drawGrid(pool, rows, now, mouseX, mouseY);
        drawControls(pool, now, mouseX, mouseY);
        drawPaytable(pool, mouseX, mouseY);
    }

    /** 浮层必须画在裁剪之外，否则会被列表的 scissor 切掉。 */
    void drawOverlay(int mouseX, int mouseY) {
        if (!hoverLines.isEmpty()) {
            host.tooltip(hoverLines, mouseX, mouseY);
        }
    }

    private void drawHeader(LotteryCatalog catalog, LotteryCatalog.Pool pool, int rows, long now,
                            int mouseX, int mouseY) {
        FontRenderer font = host.font();
        font.drawString("§l幸运老虎机", x + 2, y + 1, PageTheme.TITLE, false);
        String right = String.format("§7本次 §e%d §7行 × §e%d §7列", rows, COLUMNS);
        font.drawString(right, x + w - 2 - font.getStringWidth(right), y + 2,
                PageTheme.TEXT_DIM, false);

        poolY = y + 14;
        poolH = 16;
        List<LotteryCatalog.Pool> pools = catalog.pools();
        poolSwitchAvailable = pools.size() > 1 && !spinPending && !isAnimating(now);

        poolPrevX = x + 2;
        poolNextX = x + w - 14;
        int arrowColor = poolSwitchAvailable ? PageTheme.ACCENT : PageTheme.TEXT_FAINT;
        if (pools.size() > 1) {
            if (poolSwitchAvailable && in(mouseX, mouseY, poolPrevX, poolY, 12, poolH)) {
                host.fill(poolPrevX, poolY, 12, poolH, PageTheme.ITEM_HOVER);
            }
            if (poolSwitchAvailable && in(mouseX, mouseY, poolNextX, poolY, 12, poolH)) {
                host.fill(poolNextX, poolY, 12, poolH, PageTheme.ITEM_HOVER);
            }
            font.drawString("◀", poolPrevX + 2, poolY + 4,
                    poolSwitchAvailable ? arrowColor : 0x40556070, false);
            font.drawString("▶", poolNextX + 2, poolY + 4,
                    poolSwitchAvailable ? arrowColor : 0x40556070, false);
        }

        int index = 0;
        for (int i = 0; i < pools.size(); i++) {
            if (pools.get(i).id.equals(pool.id)) {
                index = i;
                break;
            }
        }
        String label = "§8奖池 §f" + pool.name
                + (pools.size() > 1 ? " §8(" + (index + 1) + "/" + pools.size() + ")" : "");
        int left = pools.size() > 1 ? poolPrevX + 12 : x + 2;
        int rightEdge = pools.size() > 1 ? poolNextX - 2 : x + w - 2;
        label = font.trimStringToWidth(label, Math.max(1, rightEdge - left));
        font.drawString(label, left, poolY + 4, PageTheme.TEXT_DIM, false);

        host.fill(x, y + 31, w, 1, PageTheme.SEPARATOR);
        gridY = y + 35;
    }

    private void drawGrid(LotteryCatalog.Pool pool, int rows, long now, int mouseX, int mouseY) {
        // 给奖池条、三个按钮、提示、状态与奖池表头留出固定高度；矮窗口优先压缩格子。
        int belowReserve = 145;
        int available = Math.max(3 * 22 + 2 * CELL_GAP,
                h - (gridY - y) - belowReserve);
        cellH = clamp((available - 2 * CELL_GAP) / 3, 22, 54);
        cellW = clamp((w - 4 * CELL_GAP - 4) / COLUMNS, 22, 88);

        int gridW = cellW * COLUMNS + (COLUMNS - 1) * CELL_GAP;
        int gridH = MAX_ROWS * cellH + (MAX_ROWS - 1) * CELL_GAP;
        int rowsH = rows * cellH + (rows - 1) * CELL_GAP;
        gridX = x + Math.max(2, (w - gridW) / 2);

        boolean animating = isAnimating(now);
        int frameColor = animating
                ? withAlpha(PageTheme.ACCENT, 90 + (int) (100 * pulse(now, 480L)))
                : PageTheme.ACCENT;
        host.fill(gridX - 3, gridY - 3, gridW + 6, gridH + 6, 0x50000000);
        host.fill(gridX - 3, gridY - 3, gridW + 6, 1, frameColor);
        host.fill(gridX - 3, gridY + gridH + 2, gridW + 6, 1,
                withAlpha(PageTheme.ACCENT, 80));

        List<LotteryCatalog.Symbol> symbols = pool.symbols();
        for (int row = 0; row < MAX_ROWS; row++) {
            int rowY = gridY + row * (cellH + CELL_GAP);
            boolean active = row < rows;
            boolean win = active && animResult != null && animationFinished(now)
                    && animResult.rowWin(row);
            float winPulse = pulse(now + row * 90L, 420L);
            int winAlpha = 170 + (int) (75 * winPulse);
            int borderColor = win ? withAlpha(WIN_GOLD_RGB, winAlpha) : PageTheme.ITEM_DIVIDER;

            int shineW = Math.max(10, cellW / 2);
            int shineSpan = gridW + shineW * 2;
            int shineStart = gridX - shineW
                    + (int) ((now / 8L + row * 83L) % shineSpan);

            for (int col = 0; col < COLUMNS; col++) {
                int cellX = gridX + col * (cellW + CELL_GAP);
                boolean hovered = active && in(mouseX, mouseY, cellX, rowY, cellW, cellH);
                long stopAt = animStart + ANIM_BASE_MS + col * ANIM_COL_STEP_MS;
                boolean colSpinning = animResult != null && now < stopAt;

                int background;
                if (!active) {
                    background = 0x90101720;
                } else if (win) {
                    background = withAlpha(0x5A4718, 120 + (int) (45 * winPulse));
                } else if (hovered) {
                    background = 0x90203A4A;
                } else {
                    background = 0x70101822;
                }
                host.fill(cellX, rowY, cellW, cellH, background);
                host.fill(cellX, rowY, cellW, 1, borderColor);
                host.fill(cellX, rowY + cellH - 1, cellW, 1, borderColor);
                if (win) {
                    host.fill(cellX, rowY, 1, cellH, borderColor);
                    host.fill(cellX + cellW - 1, rowY, 1, cellH, borderColor);
                    host.fill(cellX - 1, rowY - 1, cellW + 2, 1,
                            withAlpha(WIN_GOLD_RGB, 35 + (int) (35 * winPulse)));
                }
                if (!active) {
                    String skipped = cellW >= 36 ? "未选" : "—";
                    font().drawString(skipped,
                            cellX + (cellW - font().getStringWidth(skipped)) / 2,
                            rowY + cellH / 2 - 4, 0x40556070, false);
                    continue;
                }

                if (win) {
                    int local = shineStart - cellX;
                    int sx = Math.max(cellX + 1, cellX + local);
                    int ex = Math.min(cellX + cellW - 1, cellX + local + shineW);
                    if (ex > sx) {
                        host.fill(sx, rowY + 1, ex - sx, cellH - 2, 0x18FFF2B0);
                    }
                }
                if (colSpinning) {
                    int scan = (int) (((now - animStart) / 2L + col * 17L)
                            % Math.max(1, cellH - 2));
                    host.fill(cellX + 1, rowY + 1 + scan, cellW - 2, 1, 0x3000E5FF);
                }

                int symbolIndex = displaySymbol(pool, row, col, now);
                if (symbolIndex >= 0 && symbolIndex < symbols.size()) {
                    LotteryCatalog.Symbol symbol = symbols.get(symbolIndex);
                    ItemStack icon = symbol.icon;
                    int iconX = cellX + (cellW - 16) / 2;
                    int bob = colSpinning
                            ? spinJitter(now, animStart, col)
                            : Math.round(columnSettle(now, col));
                    int iconY = rowY + (cellH >= 40 ? 5 : Math.max(2, (cellH - 16) / 2));
                    iconY = Math.max(rowY + 2, Math.min(rowY + cellH - 18, iconY + bob));
                    if (icon.isEmpty()) {
                        String question = "?";
                        font().drawString(question, cellX + (cellW - font().getStringWidth(question)) / 2,
                                iconY + 4, PageTheme.TEXT_FAINT, false);
                    } else {
                        host.drawItem(icon, iconX, iconY);
                    }
                    if (cellH >= 40 && cellW >= 40) {
                        String name = font().trimStringToWidth(symbol.name, cellW - 4);
                        font().drawString(name,
                                cellX + (cellW - font().getStringWidth(name)) / 2,
                                rowY + cellH - 12,
                                win ? 0xFFFFE699 : PageTheme.TEXT_DIM, false);
                    }
                    if (hovered) {
                        hoveredSymbol = symbolIndex;
                        hoverLines = symbolTooltip(symbol, symbols);
                    }
                }
            }
        }

        for (int col = 0; col < COLUMNS; col++) {
            drawColumnStopFlash(col, rowsH, now);
        }
        drawWinParticles(now);
    }

    private void drawControls(LotteryCatalog.Pool pool, long now, int mouseX, int mouseY) {
        int gridBottom = gridY + MAX_ROWS * cellH + (MAX_ROWS - 1) * CELL_GAP;
        int controlsY = gridBottom + 6;
        int gap = 6;
        int buttonW = Math.max(1, (w - 4 - gap * 2) / 3);
        int buttonH = 36;
        controlsBottom = controlsY + buttonH;

        boolean animating = isAnimating(now);
        boolean busy = spinPending || animating || !pool.enabled();

        for (int i = 1; i <= MAX_ROWS; i++) {
            int buttonX = x + 2 + (i - 1) * (buttonW + gap);
            spinX[i] = buttonX;
            spinY[i] = controlsY;
            spinW[i] = buttonW;
            spinH[i] = buttonH;

            boolean hover = !busy && in(mouseX, mouseY, buttonX, controlsY, buttonW, buttonH);
            boolean chosen = i == clamp(selectedRows, 1, MAX_ROWS);
            int color;
            if (busy) {
                color = 0xFF1A2C33;
            } else if (hover) {
                color = PageTheme.BTN_PRIMARY_HOVER;
            } else if (chosen) {
                color = PageTheme.BTN_PRIMARY;
            } else {
                color = PageTheme.BTN_ALT;
            }
            host.fill(buttonX, controlsY, buttonW, buttonH, color);
            host.fill(buttonX, controlsY, buttonW, 1, busy ? 0x334A5A66 : 0x80FFFFFF);
            if (chosen && !busy) {
                int barW = Math.max(6, (int) (buttonW * (0.35F + 0.45F * pulse(now, 900L))));
                host.fill(buttonX + (buttonW - barW) / 2, controlsY + buttonH - 2,
                        barW, 1, PageTheme.ACCENT);
            }

            String title;
            if (i == 1) {
                title = "抽一行";
            } else if (i == 2) {
                title = "抽两行";
            } else {
                title = "抽三行";
            }
            long cost = pool.spinCost(i);
            String costText = format(cost) + " " + currency();
            boolean affordable = LotteryClientStore.playerMoney() >= cost;
            int titleColor = busy ? PageTheme.TEXT_FAINT : 0xFFFFFF;
            int costColor = busy ? PageTheme.TEXT_FAINT : (affordable ? 0xFFFFD966 : 0xFFFF8080);
            font().drawString(title,
                    buttonX + (buttonW - font().getStringWidth(title)) / 2,
                    controlsY + 7, titleColor, false);
            costText = font().trimStringToWidth(costText, Math.max(1, buttonW - 2));
            font().drawString(costText,
                    buttonX + (buttonW - font().getStringWidth(costText)) / 2,
                    controlsY + 21, costColor, false);
        }
        spinAvailable = !busy;

        String hint = "§7每行独立结算，左起连续 3 个相同即中奖";
        int hintY = controlsBottom + 4;
        font().drawString(font().trimStringToWidth(hint, w - 4), x + 2, hintY,
                PageTheme.TEXT_FAINT, false);

        String status = pool.enabled() ? statusText(now) : "§c该奖池暂未开放";
        if (!status.isEmpty()) {
            font().drawString(font().trimStringToWidth(status, w - 4), x + 2, hintY + 12,
                    PageTheme.TEXT, false);
        }
    }

    private void drawPaytable(LotteryCatalog.Pool pool, int mouseX, int mouseY) {
        payTop = controlsBottom + 32;
        payListTop = payTop + 14;
        payListH = y + h - payListTop;
        payMaxScroll = 0;
        if (payListH < 22) {
            payListH = 0;
            return;
        }

        host.fill(x, payTop - 4, w, 1, PageTheme.SEPARATOR);
        font().drawString("§7奖池一览 §8· 滚轮查看", x + 2, payTop - 1,
                PageTheme.TEXT_DIM, false);

        List<LotteryCatalog.Symbol> symbols = pool.symbols();
        int rowHeight = 18;
        int contentHeight = symbols.size() * rowHeight;
        payMaxScroll = Math.max(0, contentHeight - payListH);
        payScroll = clamp(payScroll, 0, payMaxScroll);

        host.beginClip(x, payListTop, w, payListH);
        int start = payScroll / rowHeight;
        int end = Math.min(symbols.size(), start + payListH / rowHeight + 2);
        for (int i = start; i < end; i++) {
            LotteryCatalog.Symbol symbol = symbols.get(i);
            int rowY = payListTop + i * rowHeight - payScroll;
            boolean hover = in(mouseX, mouseY, x, rowY, w, rowHeight);
            if (hover) {
                host.fill(x, rowY, w, rowHeight, PageTheme.ITEM_HOVER);
                hoveredSymbol = i;
                hoverLines = symbolTooltip(symbol, symbols);
            }
            if (!symbol.icon.isEmpty()) {
                host.drawItem(symbol.icon, x + 2, rowY + 1);
            }
            String line = compactPayline(symbol);
            font().drawString(font().trimStringToWidth(line, w - 24), x + 22, rowY + 5,
                    PageTheme.TEXT, false);
        }
        host.endClip();
        if (payMaxScroll > 0) {
            host.drawScrollbar(x + w - PageTheme.SCROLL_W, payListTop, PageTheme.SCROLL_W,
                    payListH, payScroll, payMaxScroll, false, mouseX, mouseY);
        }
    }

    // ================================================================ 文案

    private String statusText(long now) {
        String error = LotteryClientStore.error();
        if (!error.isEmpty()) {
            return "§c" + error;
        }
        if (spinPending) {
            int dots = 1 + (int) ((now / 280L) % 3L);
            StringBuilder line = new StringBuilder("§7正在下发抽奖请求");
            for (int i = 0; i < dots; i++) {
                line.append('.');
            }
            return line.toString();
        }
        if (isAnimating(now)) {
            int dots = 1 + (int) ((now / 280L) % 3L);
            StringBuilder line = new StringBuilder("§7转轮转动中");
            for (int i = 0; i < dots; i++) {
                line.append('.');
            }
            return line.toString();
        }
        String notice = LotteryClientStore.notice();
        if (!notice.isEmpty()) {
            return "§b" + notice;
        }
        LotteryResult result = animResult;
        if (result != null) {
            if (!result.anyWin()) {
                return "§8本次没有中奖";
            }
            StringBuilder line = new StringBuilder("§6中奖！ ");
            if (result.wonMoney > 0L) {
                line.append("§e").append(format(revealedMoney(result.wonMoney, now)))
                        .append(" §7").append(currency());
            }
            if (!result.rewardLines.isEmpty()) {
                if (result.wonMoney > 0L) {
                    line.append(" §8+ ");
                }
                for (int i = 0; i < result.rewardLines.size(); i++) {
                    if (i > 0) {
                        line.append("§8、");
                    }
                    line.append(result.rewardLines.get(i));
                }
            }
            return line.toString();
        }
        return "§8选择上方按钮开始抽奖";
    }

    private static String compactPayline(LotteryCatalog.Symbol symbol) {
        StringBuilder line = new StringBuilder("§f").append(symbol.name);
        for (int i = 0; i < symbol.paytable.size(); i++) {
            String payout = symbol.paytable.get(i);
            if (payout == null || payout.isEmpty()) {
                continue;
            }
            line.append("  §8").append(i + 3).append("连 ").append(payout);
        }
        return line.toString();
    }

    private List<String> symbolTooltip(LotteryCatalog.Symbol symbol, List<LotteryCatalog.Symbol> all) {
        List<String> lines = new ArrayList<>();
        lines.add("§b" + symbol.name);
        long totalWeight = 0L;
        for (LotteryCatalog.Symbol other : all) {
            totalWeight += Math.max(1, other.weight);
        }
        double chance = totalWeight <= 0L ? 0.0
                : 100.0 * Math.max(1, symbol.weight) / totalWeight;
        lines.add(String.format("§8单格概率 §7%.2f%% §8(权重 %d)", chance, symbol.weight));
        for (int i = 0; i < symbol.paytable.size(); i++) {
            String payout = symbol.paytable.get(i);
            if (payout == null || payout.isEmpty()) {
                lines.add(String.format("§8%d 连 §7无奖励", i + 3));
            } else {
                lines.add(String.format("§7%d 连  %s", i + 3, payout));
            }
        }
        return lines;
    }

    // ================================================================ 动画

    private void refreshAnimation(long now) {
        long seq = LotteryClientStore.resultSeq();
        if (seq != shownResultSeq) {
            shownResultSeq = seq;
            LotteryResult result = LotteryClientStore.result();
            if (result != null) {
                animResult = result;
                selectedRows = clamp(result.rows, 1, MAX_ROWS);
                animStart = now;
            }
        }
    }

    private boolean isAnimating(long now) {
        return animResult != null && !animationFinished(now);
    }

    private boolean animationFinished(long now) {
        return now >= animationEnd();
    }

    private long animationEnd() {
        return animStart + ANIM_BASE_MS
                + (COLUMNS - 1) * ANIM_COL_STEP_MS + ANIM_SETTLE_MS;
    }

    /** 中奖金额在 0.6 秒内从 0 滚到最终值。 */
    private long revealedMoney(long amount, long now) {
        long elapsed = now - animationEnd();
        if (elapsed >= WIN_COUNT_MS) {
            return amount;
        }
        if (elapsed <= 0L) {
            return 0L;
        }
        return amount * elapsed / WIN_COUNT_MS;
    }

    /** 每列停下的瞬间扫过一道竖向亮线。 */
    private void drawColumnStopFlash(int col, int rowsH, long now) {
        if (animResult == null || rowsH <= 0) {
            return;
        }
        long stopAt = animStart + ANIM_BASE_MS + col * ANIM_COL_STEP_MS;
        long elapsed = now - stopAt;
        if (elapsed < 0L || elapsed > 180L) {
            return;
        }
        int alpha = (int) (160 * (1.0F - elapsed / 180.0F));
        int color = withAlpha(0xBFEFFF, alpha);
        int colX = gridX + col * (cellW + CELL_GAP);
        host.fill(colX, gridY, 1, rowsH, color);
        host.fill(colX + cellW - 1, gridY, 1, rowsH, color);
        host.fill(colX, gridY, cellW, 1, color);
    }

    /** 中奖后从网格里升起的小金色碎屑。 */
    private void drawWinParticles(long now) {
        if (animResult == null || !animResult.anyWin()) {
            return;
        }
        long elapsed = now - animationEnd();
        if (elapsed < 0L || elapsed > WIN_PARTICLE_MS) {
            return;
        }
        int areaW = cellW * COLUMNS + (COLUMNS - 1) * CELL_GAP;
        if (areaW <= 0) {
            return;
        }
        int winningRows = 0;
        for (int row = 0; row < selectedRows && row < MAX_ROWS; row++) {
            if (animResult.rowWin(row)) {
                winningRows++;
            }
        }
        if (winningRows <= 0) {
            return;
        }
        float progress = elapsed / (float) WIN_PARTICLE_MS;
        for (int i = 0; i < 14; i++) {
            int seed = i * 7349 + 977;
            int px = gridX + Math.floorMod(seed * 31, areaW);
            int winner = winningRowAt(i % winningRows);
            if (winner < 0) {
                continue;
            }
            int baseY = gridY + winner * (cellH + CELL_GAP)
                    + Math.floorMod(seed * 17, Math.max(1, cellH));
            int rise = (int) (progress * (26 + (i % 4) * 7));
            int py = baseY - rise;
            int alpha = (int) ((1.0F - progress) * (140 + (i % 3) * 25));
            if (alpha <= 0) {
                continue;
            }
            int rgb = (i & 1) == 0 ? 0xFFF6D8 : WIN_GOLD_RGB;
            host.fill(px, py, 2, 2, withAlpha(rgb, alpha));
            if (i % 3 == 0) {
                host.fill(px - 1, py + 1, 4, 1, withAlpha(rgb, alpha / 2));
            }
        }
    }

    private int winningRowAt(int ordinal) {
        int seen = 0;
        for (int row = 0; row < selectedRows && row < MAX_ROWS; row++) {
            if (animResult.rowWin(row)) {
                if (seen == ordinal) {
                    return row;
                }
                seen++;
            }
        }
        return -1;
    }

    /** 转动时图标的轻微上下抖动，停下后换成一次短促的回弹。 */
    private static int spinJitter(long now, long start, int col) {
        return (int) Math.round(Math.sin((now - start) / 38.0 + col * 1.7) * 1.6);
    }

    private float columnSettle(long now, int col) {
        if (animResult == null) {
            return 0.0F;
        }
        long stopAt = animStart + ANIM_BASE_MS + col * ANIM_COL_STEP_MS;
        long elapsed = now - stopAt;
        if (elapsed < 0L || elapsed > COLUMN_SETTLE_MS) {
            return 0.0F;
        }
        float t = elapsed / (float) COLUMN_SETTLE_MS;
        return (float) (Math.sin(t * Math.PI * 3.0) * (1.0F - t) * 2.2F);
    }

    private static float pulse(long now, long periodMs) {
        if (periodMs <= 0L) {
            return 0.5F;
        }
        double phase = (now % periodMs) / (double) periodMs;
        return (float) ((Math.sin(phase * Math.PI * 2.0) + 1.0) * 0.5);
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private int displaySymbol(LotteryCatalog.Pool pool, int row, int col, long now) {
        int count = pool.symbols().size();
        if (count <= 0) {
            return -1;
        }
        if (animResult == null) {
            return (row * COLUMNS + col * 3) % count;
        }
        long stopAt = animStart + ANIM_BASE_MS + col * ANIM_COL_STEP_MS;
        if (now < stopAt) {
            long tick = Math.max(0L, now - animStart) / 65L;
            return (int) Math.floorMod(tick + col * 3L + row * 2L, count);
        }
        int index = animResult.symbolAt(row, col);
        return index >= 0 && index < count ? index : 0;
    }

    // ================================================================ 输入

    boolean click(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (poolSwitchAvailable) {
            if (in(mouseX, mouseY, poolPrevX, poolY, 12, poolH)) {
                switchPool(-1);
                return true;
            }
            if (in(mouseX, mouseY, poolNextX, poolY, 12, poolH)) {
                switchPool(1);
                return true;
            }
        }
        if (!spinAvailable) {
            return false;
        }
        for (int i = 1; i <= MAX_ROWS; i++) {
            if (in(mouseX, mouseY, spinX[i], spinY[i], spinW[i], spinH[i])) {
                spin(i);
                return true;
            }
        }
        return false;
    }

    boolean wheel(int mouseX, int mouseY, int wheel) {
        if (payListH <= 0 || payMaxScroll <= 0
                || !in(mouseX, mouseY, x, payListTop, w, payListH)) {
            return false;
        }
        payScroll = clamp(payScroll - Integer.signum(wheel) * 18, 0, payMaxScroll);
        return true;
    }

    private void spin(int rows) {
        long now = System.currentTimeMillis();
        if (spinPending || isAnimating(now)) {
            return;
        }
        LotteryCatalog.Pool pool = LotteryClientStore.selectedPool();
        if (pool == null || !pool.enabled() || pool.symbols().isEmpty()) {
            return;
        }
        int safeRows = clamp(rows, 1, MAX_ROWS);
        selectedRows = safeRows;
        spinPending = true;
        spinSentAt = now;
        spinReplyAtSend = LotteryClientStore.spinReplySeq();
        // 先收起上一次的结果：请求失败时不应继续显示旧的赢奖画面。
        animResult = null;
        shownResultSeq = LotteryClientStore.resultSeq();
        PacketHandler.sendToServer(MessageLotteryAction.spin(pool.id, safeRows));
    }

    private void switchPool(int delta) {
        LotteryCatalog catalog = LotteryClientStore.catalog();
        List<LotteryCatalog.Pool> pools = catalog.pools();
        if (pools.size() < 2) {
            return;
        }
        int current = 0;
        for (int i = 0; i < pools.size(); i++) {
            if (pools.get(i).id.equals(LotteryClientStore.selectedPoolId())) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + delta, pools.size());
        LotteryClientStore.selectPool(pools.get(next).id);
        // 旧结果属于旧奖池，切换后不再显示；下一次抽奖重新开始动画。
        animResult = null;
        shownResultSeq = LotteryClientStore.resultSeq();
        payScroll = 0;
    }

    // ================================================================ 小工具

    private void clearHitBoxes() {
        spinAvailable = false;
        poolSwitchAvailable = false;
        payListH = 0;
        payMaxScroll = 0;
        for (int i = 0; i <= MAX_ROWS; i++) {
            spinW[i] = 0;
            spinH[i] = 0;
        }
    }

    private void drawCentered(String text, int textY) {
        FontRenderer font = host.font();
        font.drawString(text, x + (w - font.getStringWidth(text)) / 2, textY,
                PageTheme.TEXT_FAINT, false);
    }

    private FontRenderer font() {
        return host.font();
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
        return w > 0 && h > 0 && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String currency() {
        try {
            String name = PageConfigs.shop().currencyName;
            return name == null || name.trim().isEmpty() ? "货币" : name.trim();
        } catch (Throwable ignored) {
            return "货币";
        }
    }

    private String format(long value) {
        return formatter.format(value);
    }
}
