package meowmel.quantumhue.page;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.MessageStockAction;
import meowmel.quantumhue.stock.StockBar;
import meowmel.quantumhue.stock.StockConfig;
import meowmel.quantumhue.stock.StockProduct;
import meowmel.quantumhue.stock.StockSnapshot.PositionView;
import meowmel.quantumhue.stock.StockSnapshot.ProductView;
import meowmel.quantumhue.stock.StockSnapshot.RecordView;
import meowmel.quantumhue.stock.StockTradeRecord;
import meowmel.quantumhue.stock.client.StockClientStore;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 商店页「交易终端」的主体。
 *
 * <p>面板自己管理绘制、命中、输入焦点与滚动，宿主 {@link GuiShop} 只提供裁剪、文字和矩形基元，
 * 与 {@link LotteryPanel} 的组织方式一致。行情与账户都来自服务端快照，这里不做任何本地记账，
 * 只负责把玩家意图翻译成 {@link MessageStockAction}。
 *
 * <p>金额在协议和模型里都是“分”。界面显示时统一换算成两位小数；输入框允许玩家直接写
 * {@code 102.43} 这种价格，提交前再乘 100 取整。
 */
@SideOnly(Side.CLIENT)
public final class StockPanel {

    // ================================================================ 布局常量

    private static final int HEADER_H = 38;
    private static final int CHIPS_H = 20;
    private static final int TAB_H = 18;
    private static final int ROW_H = 22;
    private static final int RIGHT_MIN = 216;
    private static final int RIGHT_MAX = 280;

    private static final int FOCUS_NONE = 0;
    private static final int FOCUS_QTY = 1;
    private static final int FOCUS_CLOSE = 2;
    private static final int FOCUS_STOP = 3;
    private static final int FOCUS_TAKE = 4;

    private static final int TAB_POSITIONS = 0;
    private static final int TAB_RECORDS = 1;
    private static final int TAB_PROTECTION = 2;

    private static final int BG = 0x88070D14;
    private static final int PANEL_BG = 0xAA0A111A;
    private static final int PANEL_BORDER = 0x335F7C8C;
    private static final int ROW_HOVER = 0x22CFE4EF;
    private static final int ROW_SELECTED = 0x3338D6FF;

    private final GuiShop host;

    private boolean open;
    private long lastPoll;

    private String productId = StockConfig.COMPOSITE_ID;
    private int leverage = StockConfig.DEFAULT_LEVERAGE;

    private String qtyText = "1";
    private String closeText = "";
    private String stopText = "";
    private String takeText = "";
    private int focus = FOCUS_NONE;

    private int bottomTab = TAB_POSITIONS;
    private long selectedPosition = -1L;
    private boolean closeMode;

    private int zoomBars = 60;
    private int panOffset;
    private boolean chartDrag;
    private int chartDragX;
    private int chartDragStartOffset;

    private int listScroll;

    private int protectionMode = StockConfig.PROTECTION_PRICE;
    private int blink;

    private String notice = "";
    private long noticeUntil;
    private boolean noticeError;

    // 上一帧的布局，供点击/拖动复用（绘制与命中必须使用同一套坐标）。
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int chipX;
    private int chipY;
    private int chipW;
    private int chipH;
    private int bodyTop;
    private int chartX;
    private int chartY;
    private int chartW;
    private int chartH;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;
    private int bottomY;
    private int bottomH;
    private int headerH = HEADER_H;
    private boolean compact;
    private boolean showVolume;
    private boolean tooSmall;

    public StockPanel(GuiShop host) {
        this.host = host;
    }

    // ================================================================ 生命周期

    /** 打开页签：清空临时选择并请求整包。 */
    public void open() {
        open = true;
        focus = FOCUS_NONE;
        closeMode = false;
        selectedPosition = -1L;
        bottomTab = TAB_POSITIONS;
        listScroll = 0;
        panOffset = 0;
        chartDrag = false;
        lastPoll = System.currentTimeMillis();
        PacketHandler.sendToServer(MessageStockAction.request());
    }

    /** 关闭页签：停掉轮询和拖动。 */
    public void close() {
        open = false;
        focus = FOCUS_NONE;
        chartDrag = false;
        lastPoll = 0L;
    }

    /** 每 tick 调用：推进光标、消费提示、每秒轮询一次。 */
    public void update() {
        if (!open) {
            return;
        }
        blink++;
        readMessages();
        if (selectedPosition != -1L && StockClientStore.position(selectedPosition) == null) {
            selectedPosition = -1L;
            closeMode = false;
            focus = FOCUS_NONE;
        }
        long now = System.currentTimeMillis();
        if (now - lastPoll >= 1000L) {
            lastPoll = now;
            PacketHandler.sendToServer(MessageStockAction.poll());
        }
    }

    /** 服务端快照到达：立即同步提示与选中项。 */
    public void updateData() {
        if (!open) {
            return;
        }
        readMessages();
        if (selectedPosition != -1L && StockClientStore.position(selectedPosition) == null) {
            selectedPosition = -1L;
            closeMode = false;
        }
    }

    private void readMessages() {
        String news = StockClientStore.takeNotice();
        if (news != null && !news.isEmpty()) {
            pushNotice(news, false);
        }
        String error = StockClientStore.takeError();
        if (error != null && !error.isEmpty()) {
            pushNotice(error, true);
        }
    }

    private void pushNotice(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeUntil = System.currentTimeMillis() + 5200L;
    }

    // ================================================================ 绘制

    public void draw(int x, int y, int w, int h, int mouseX, int mouseY) {
        computeLayout(x, y, w, h);
        if (tooSmall) {
            drawTooSmall();
            return;
        }
        if (compact) {
            drawCompact(mouseX, mouseY);
            return;
        }
        drawHeader();
        drawChips(mouseX, mouseY);
        drawChart(mouseX, mouseY);
        drawRight(mouseX, mouseY);
        drawBottom(mouseX, mouseY);
        drawNotice();
    }

    private void computeLayout(int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;

        compact = false;
        headerH = HEADER_H;
        showVolume = false;
        tooSmall = w < 200 || h < 110;
        if (tooSmall) {
            chipW = 0;
            chipH = 0;
            chartDrag = false;
            return;
        }

        int fullChipsY = y + HEADER_H + 4;
        int fullBodyTop = fullChipsY + CHIPS_H + 4;
        int fullBottomH = clamp(96, 140, h * 32 / 100);
        int fullBottomY = y + h - fullBottomH;
        int fullBodyH = fullBottomY - 4 - fullBodyTop;
        int fullRightW = clamp(RIGHT_MIN, RIGHT_MAX, w * 34 / 100);
        int fullChartW = w - fullRightW - 8;

        if (w >= 520 && h >= 300 && fullBodyH >= 140 && fullChartW >= 200) {
            chipX = x + 8;
            chipY = fullChipsY;
            chipW = Math.max(40, (w - 16 - 4 * 4) / 5);
            chipH = CHIPS_H;
            bodyTop = fullBodyTop;
            bottomH = fullBottomH;
            bottomY = fullBottomY;
            rightW = fullRightW;
            rightX = x + w - rightW;
            rightY = bodyTop;
            rightH = Math.max(0, fullBodyH);
            chartX = x;
            chartY = bodyTop;
            chartW = Math.max(0, fullChartW);
            chartH = Math.max(0, fullBodyH);
            showVolume = chartH >= 100;
            return;
        }

        // 窗口不够完整布局时退化为“精简图表”：保留标题、品种条和 K 线，
        // 隐藏下单区与底部页签，避免整页只剩一句“窗口过小”。
        compact = true;
        headerH = 28;
        boolean showChips = h >= 185;
        chipH = showChips ? 18 : 0;
        chipY = y + headerH;
        int gap = 4;
        chipW = showChips ? Math.max(26, (w - 12 - gap * 4) / 5) : 0;
        chipX = x + 6;
        bodyTop = chipY + (showChips ? chipH + 2 : 0);
        bottomH = 14;
        bottomY = y + h - bottomH;
        chartX = x;
        chartY = bodyTop;
        chartW = w;
        chartH = Math.max(0, bottomY - chartY - 2);
        rightW = 0;
        rightX = x + w;
        rightY = bodyTop;
        rightH = 0;
        showVolume = chartH >= 130;
    }

    private void drawCompact(int mouseX, int mouseY) {
        drawCompactHeader();
        if (chipH > 0) {
            drawCompactChips(mouseX, mouseY);
        }
        drawChart(mouseX, mouseY);
        drawCompactFooter();
        drawNotice();
    }

    private void drawCompactHeader() {
        rect(px, py, px + pw, py + headerH, PANEL_BG);
        rect(px, py + headerH - 1, px + pw, py + headerH, PANEL_BORDER);

        StockProduct config = StockConfig.product(productId);
        ProductView view = StockClientStore.product(productId);
        long mark = view != null ? view.price : (config == null ? 0L : config.basePrice);
        int change = view != null ? view.change : 0;
        String name = config == null ? "交易终端" : shortName(config.name);

        text("§f" + name, px + 6, py + 4, PageTheme.TEXT);
        int priceX = px + 6 + textW(name) + 6;
        text(price(mark), priceX, py + 4, PageTheme.TEXT);
        String pct = String.format(Locale.ROOT, "%+.2f%%", change / 100.0);
        text(pct, priceX + textW(price(mark)) + 6, py + 4,
                change >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);

        String line = "§7余额 §f" + money(StockClientStore.wallet())
                + " §8· §7权益 §f" + money(StockClientStore.equity());
        text(line, px + 6, py + 15, PageTheme.TEXT_DIM);
    }

    private void drawCompactChips(int mouseX, int mouseY) {
        List<StockProduct> products = StockConfig.products();
        int gap = 4;
        int x = chipX;
        for (StockProduct product : products) {
            ProductView view = StockClientStore.product(product.id);
            boolean active = product.id.equals(productId);
            boolean hovered = in(mouseX, mouseY, x, chipY, chipW, chipH);
            rect(x, chipY, x + chipW, chipY + chipH,
                    active ? 0x552F6E7E : (hovered ? ROW_HOVER : 0x33101822));
            if (active) {
                rect(x, chipY, x + 2, chipY + chipH, PageTheme.ACCENT);
            }
            String name = shortName(product.name);
            text(name, x + (chipW - textW(name)) / 2f, chipY + 2,
                    active ? PageTheme.TEXT : PageTheme.TEXT_DIM);
            if (chipW >= 52 && view != null) {
                String value = price(view.price);
                text(value, x + (chipW - textW(value)) / 2f, chipY + 10, PageTheme.TEXT_FAINT);
            }
            x += chipW + gap;
        }
    }

    private void drawCompactFooter() {
        rect(px, bottomY, px + pw, py + ph, PANEL_BG);
        rect(px, bottomY, px + pw, bottomY + 1, PANEL_BORDER);

        String left = "§7持仓 §f" + StockClientStore.positions().size() + " §7笔";
        text(left, px + 6, bottomY + 3, PageTheme.TEXT_DIM);

        long pnl = 0L;
        for (PositionView position : StockClientStore.positions()) {
            pnl += position.unrealized;
        }
        String sign = pnl >= 0L ? "+" : "";
        String right = "浮盈 " + sign + money(pnl);
        text(right, px + pw - 6 - textW(right), bottomY + 3,
                pnl >= 0L ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);

        if (pw >= 300) {
            String hint = "放大可交易";
            text(hint, px + (pw - textW(hint)) / 2f, bottomY + 3, PageTheme.TEXT_FAINT);
        }
    }

    private void drawTooSmall() {
        rect(px, py, px + pw, py + ph, BG);

        StockProduct config = StockConfig.product(productId);
        ProductView view = StockClientStore.product(productId);
        long mark = view != null ? view.price : (config == null ? 0L : config.basePrice);
        int change = view != null ? view.change : 0;
        String title = (config == null ? "交易终端" : shortName(config.name)) + "  " + price(mark);
        text(title, px + (pw - textW(title)) / 2f, py + ph / 2f - 14, PageTheme.TEXT);

        String pct = String.format(Locale.ROOT, "%+.2f%%", change / 100.0);
        text(pct, px + (pw - textW(pct)) / 2f, py + ph / 2f - 3,
                change >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);

        String hint = "放大窗口查看行情";
        text(hint, px + (pw - textW(hint)) / 2f, py + ph / 2f + 9, PageTheme.TEXT_FAINT);
    }

    private void drawHeader() {
        rect(px, py, px + pw, py + headerH, PANEL_BG);
        rect(px, py + headerH - 1, px + pw, py + headerH, PANEL_BORDER);

        String currency = currency();
        String line1 = "§7余额 §f" + money(StockClientStore.wallet()) + " §8· §7权益 §f"
                + money(StockClientStore.equity()) + " §8· §7可用 §f"
                + money(StockClientStore.available()) + " §8· §7冻结 §f"
                + money(StockClientStore.escrow()) + " §7" + currency;
        text(line1, px + 8, py + 6, PageTheme.TEXT_DIM);

        long margin = StockClientStore.usedMargin();
        long equity = StockClientStore.equity();
        long notional = 0L;
        for (PositionView position : StockClientStore.positions()) {
            notional += position.entryPrice * position.quantity;
        }
        long leverageNow = equity > 0L ? notional * 100L / equity : 0L;
        String line2 = "§7实际杠杆 §f" + (leverageNow > 0L ? leverageNow / 100f : 0f) + "×"
                + " §8· §7已用保证金 §f" + money(margin)
                + " §8· §7维持保证金率 §f50%"
                + " §8· §7限仓 §f" + format(StockConfig.MAX_POSITION) + " 张/品种";
        text(line2, px + 8, py + 20, PageTheme.TEXT_DIM);

        if (pw >= 680) {
            String tag = "§8模拟交易 · 15 秒 K 线";
            text(tag, px + pw - 8 - textW(tag), py + 6, PageTheme.TEXT_FAINT);
        }
    }

    private void drawChips(int mouseX, int mouseY) {
        List<StockProduct> products = StockConfig.products();
        int gap = 4;
        int x = chipX;
        for (StockProduct product : products) {
            ProductView view = StockClientStore.product(product.id);
            long price = view == null ? product.basePrice : view.price;
            int change = view == null ? 0 : view.change;
            boolean active = product.id.equals(productId);
            boolean hovered = in(mouseX, mouseY, x, chipY, chipW, chipH);
            rect(x, chipY, x + chipW, chipY + chipH,
                    active ? 0x552F6E7E : (hovered ? ROW_HOVER : 0x33101822));
            if (active) {
                rect(x, chipY, x + 2, chipY + chipH, PageTheme.ACCENT);
            }
            String name = shortName(product.name);
            text(name, x + 7, chipY + 2, active ? PageTheme.TEXT : PageTheme.TEXT_DIM);
            String pct = String.format(Locale.ROOT, "%+.2f%%", change / 100.0);
            text(pct, x + chipW - 6 - textW(pct), chipY + 2,
                    change >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);
            text(price(price), x + 7, chipY + 11, PageTheme.TEXT_FAINT);
            x += chipW + gap;
        }
    }

    private void drawChart(int mouseX, int mouseY) {
        rect(chartX, chartY, chartX + chartW, chartY + chartH, 0x66070D14);
        border(chartX, chartY, chartW, chartH, PANEL_BORDER);

        ProductView product = StockClientStore.product(productId);
        if (product == null || product.bars.isEmpty()) {
            String line = "等待行情数据…";
            text(line, chartX + (chartW - textW(line)) / 2f, chartY + chartH / 2f - 4,
                    PageTheme.TEXT_DIM);
            return;
        }

        int padRight = clamp(38, 54, chartW / 7);
        int left = chartX + 6;
        int right = chartX + chartW - padRight;
        int top = chartY + 10;
        int bottom = chartY + chartH - 16;
        int plotW = right - left;
        int plotH = bottom - top;
        if (plotW < 60 || plotH < (compact ? 30 : 50)) {
            return;
        }

        int volumeH = showVolume ? Math.min(plotH / 3, Math.max(16, plotH * 26 / 100)) : 0;
        int priceBottom = bottom - (showVolume ? volumeH + 5 : 0);
        int priceH = priceBottom - top;
        if (priceH < (compact ? 18 : 30)) {
            return;
        }

        List<StockBar> bars = product.bars;
        int visible = Math.max(1, Math.min(zoomBars, bars.size()));
        int end = clamp(visible, bars.size(), bars.size() - panOffset);
        int start = end - visible;

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = start; i < end; i++) {
            StockBar bar = bars.get(i);
            min = Math.min(min, bar.low);
            max = Math.max(max, bar.high);
        }
        if (max <= min) {
            max = min + Math.max(1L, min / 100L);
        }
        long span = max - min;
        min -= span * 6 / 100;
        max += span * 6 / 100;

        // 价格网格与右侧刻度
        for (int i = 0; i < 4; i++) {
            long value = max - (max - min) * i / 3;
            int gy = (int) (priceBottom - (double) (value - min) / (max - min) * priceH);
            rect(left, gy, right, gy + 1, PageTheme.STOCK_GRID);
            String label = price(value);
            text(label, right + 4, gy - 4, PageTheme.STOCK_AXIS);
        }

        // 时间刻度
        int mid = start + visible / 2;
        drawTimeAxis(bars, start, mid, end - 1, left, right, bottom);

        double slot = (double) plotW / visible;
        int bodyW = Math.max(1, (int) (slot * 0.62));
        long maxVolume = 1L;
        if (showVolume) {
            for (int i = start; i < end; i++) {
                maxVolume = Math.max(maxVolume, bars.get(i).volume);
            }
        }

        for (int i = start; i < end; i++) {
            StockBar bar = bars.get(i);
            int cx = (int) (left + (i - start + 0.5) * slot);
            boolean up = bar.close >= bar.open;
            int color = up ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN;
            int yOpen = priceY(bar.open, min, max, priceBottom, priceH);
            int yClose = priceY(bar.close, min, max, priceBottom, priceH);
            int yHigh = priceY(bar.high, min, max, priceBottom, priceH);
            int yLow = priceY(bar.low, min, max, priceBottom, priceH);

            rect(cx, Math.min(yHigh, yLow), cx + 1, Math.max(yHigh, yLow) + 1, color);
            int bodyTopY = Math.min(yOpen, yClose);
            int bodyBottomY = Math.max(yOpen, yClose) + 1;
            rect(cx - bodyW / 2, bodyTopY, cx - bodyW / 2 + bodyW, bodyBottomY, color);

            if (showVolume) {
                int volX = (int) (left + (i - start + 0.5) * slot);
                int volBarH = (int) ((double) bar.volume / maxVolume * (volumeH - 2));
                int volTop = bottom - volBarH;
                rect(volX - Math.max(1, bodyW / 2), volTop,
                        volX - Math.max(1, bodyW / 2) + Math.max(1, bodyW), bottom,
                        up ? PageTheme.STOCK_VOLUME_UP : PageTheme.STOCK_VOLUME_DOWN);
            }
        }

        // 均线
        drawMa(bars, start, end, 5, left, slot, min, max, priceBottom, priceH, PageTheme.STOCK_MA5);
        drawMa(bars, start, end, 10, left, slot, min, max, priceBottom, priceH, PageTheme.STOCK_MA10);
        drawMa(bars, start, end, 20, left, slot, min, max, priceBottom, priceH, PageTheme.STOCK_MA20);

        int legendX = left + 2;
        text("MA5", legendX, chartY + 1, PageTheme.STOCK_MA5);
        text("MA10", legendX + 24, chartY + 1, PageTheme.STOCK_MA10);
        text("MA20", legendX + 52, chartY + 1, PageTheme.STOCK_MA20);

        // 最后一根收盘价参考线
        StockBar last = bars.get(bars.size() - 1);
        int lastY = priceY(last.close, min, max, priceBottom, priceH);
        int lastColor = last.close >= last.open ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN;
        rect(left, lastY, right, lastY + 1, (lastColor & 0x00FFFFFF) | 0x88000000);
        String lastLabel = price(last.close);
        rect(right, lastY - 5, right + padRight - 1, lastY + 5, 0xCC0A1017);
        text(lastLabel, right + 4, lastY - 4, lastColor);

        // 十字光标
        if (!chartDrag && in(mouseX, mouseY, left, top, plotW, priceBottom - top)
                && mouseX < right && mouseY < priceBottom) {
            int index = start + (int) ((mouseX - left) / slot);
            index = clamp(start, end - 1, index);
            StockBar hover = bars.get(index);
            int crossX = (int) (left + (index - start + 0.5) * slot);
            rect(crossX, top, crossX + 1, bottom, PageTheme.STOCK_CROSS);
            rect(left, mouseY, right, mouseY + 1, PageTheme.STOCK_CROSS);

            long hoverPrice = min + (long) ((double) (priceBottom - mouseY) / priceH * (max - min));
            String label = price(hoverPrice);
            rect(right, mouseY - 5, right + padRight - 1, mouseY + 5, 0xCC0A1017);
            text(label, right + 4, mouseY - 4, PageTheme.TEXT);

            String time = time(hover.time);
            int tw = textW(time);
            int tx = clamp(left, right - tw, crossX - tw / 2);
            rect(tx - 2, bottom + 1, tx + tw + 2, bottom + 11, 0xCC0A1017);
            text(time, tx, bottom + 2, PageTheme.TEXT);
        }
    }

    private void drawTimeAxis(List<StockBar> bars, int start, int mid, int end,
                              int left, int right, int bottom) {
        String first = time(bars.get(start).time);
        String middle = time(bars.get(mid).time);
        String last = time(bars.get(end).time);
        text(first, left, bottom + 2, PageTheme.STOCK_AXIS);
        text(middle, left + (right - left - textW(middle)) / 2f, bottom + 2, PageTheme.STOCK_AXIS);
        text(last, right - textW(last), bottom + 2, PageTheme.STOCK_AXIS);
    }

    private void drawMa(List<StockBar> bars, int start, int end, int period, int left, double slot,
                        long min, long max, int priceBottom, int priceH, int color) {
        int prevX = 0;
        int prevY = 0;
        boolean hasPrev = false;
        for (int i = start; i < end; i++) {
            if (i < period - 1) {
                hasPrev = false;
                continue;
            }
            long sum = 0L;
            for (int k = i - period + 1; k <= i; k++) {
                sum += bars.get(k).close;
            }
            long average = sum / period;
            int x = (int) (left + (i - start + 0.5) * slot);
            int y = priceY(average, min, max, priceBottom, priceH);
            if (hasPrev) {
                line(prevX, prevY, x, y, color);
            }
            prevX = x;
            prevY = y;
            hasPrev = true;
        }
    }

    private int priceY(long value, long min, long max, int bottom, int height) {
        if (max <= min) {
            return bottom;
        }
        return (int) (bottom - (double) (value - min) / (max - min) * height);
    }

    private void drawRight(int mouseX, int mouseY) {
        rect(rightX, rightY, rightX + rightW, rightY + rightH, PANEL_BG);
        border(rightX, rightY, rightW, rightH, PANEL_BORDER);
        PositionView selected = selectedPosition == -1L ? null : StockClientStore.position(selectedPosition);
        if (closeMode && selected != null) {
            drawDetail(selected, mouseX, mouseY);
        } else {
            drawOrder(mouseX, mouseY);
        }
    }

    private void drawOrder(int mouseX, int mouseY) {
        ProductView product = StockClientStore.product(productId);
        StockProduct config = StockConfig.product(productId);
        long mark = product != null ? product.price : (config == null ? 0L : config.basePrice);
        int change = product != null ? product.change : 0;

        int top = rightY + 6;
        text("市价委托", rightX + 7, top, PageTheme.TEXT_DIM);

        String priceText = price(mark);
        text(priceText, rightX + 7, top + 13, PageTheme.TEXT);
        String pct = String.format(Locale.ROOT, "%+.2f%%", change / 100.0);
        text(pct, rightX + 9 + textW(priceText), top + 13,
                change >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);

        int qtyY = top + 30;
        text("数量", rightX + 7, qtyY + 3, PageTheme.TEXT_DIM);
        drawField(qtyFieldX(), qtyY, 84, 14, qtyText, focus == FOCUS_QTY, "张");

        int quickY = top + 48;
        int quickGap = 3;
        int quickW = (rightW - 14 - quickGap * 3) / 4;
        String[] quick = {"+1", "+10", "+100", "最大"};
        for (int i = 0; i < quick.length; i++) {
            int qx = rightX + 7 + i * (quickW + quickGap);
            drawSmallButton(qx, quickY, quickW, 14, quick[i], mouseX, mouseY,
                    PageTheme.BTN_ALT, PageTheme.BTN_ALT_HOVER);
        }

        int levY = top + 66;
        text("杠杆", rightX + 7, levY + 3, PageTheme.TEXT_DIM);
        int levX = rightX + 42;
        int levW = Math.max(22, (rightW - 49 - 4 * 2) / 5);
        for (int i = 0; i < StockConfig.LEVERAGES.length; i++) {
            int value = StockConfig.LEVERAGES[i];
            int bx = levX + i * (levW + 2);
            boolean active = leverage == value;
            boolean hovered = in(mouseX, mouseY, bx, levY, levW, 14);
            rect(bx, levY, bx + levW, levY + 14,
                    active ? 0x662F6E7E : (hovered ? ROW_HOVER : 0x33101822));
            if (active) {
                border(bx, levY, levW, 14, PageTheme.ACCENT);
            }
            String label = value + "×";
            text(label, bx + (levW - textW(label)) / 2f, levY + 3,
                    active ? PageTheme.TEXT : PageTheme.TEXT_DIM);
        }

        long quantity = parseQty(qtyText);
        long entry = entryPrice(mark, 1);
        long notional = entry * quantity;
        long margin = leverage > 0 ? StockConfig.ceilDiv(notional, leverage) : 0L;
        long fee = fee(notional);

        int estY = top + 86;
        int buyY = orderBuyY();
        if (estY + 10 < buyY) {
            text("§7名义 §f" + money(notional), rightX + 7, estY, PageTheme.TEXT_DIM);
            if (estY + 22 < buyY) {
                text("§7保证金 §f" + money(margin), rightX + 7, estY + 12, PageTheme.TEXT_DIM);
            }
            if (estY + 34 < buyY) {
                text("§7手续费 §f" + money(fee), rightX + 7, estY + 24, PageTheme.TEXT_DIM);
            }
        }

        drawBigButton(rightX + 7, buyY, rightW - 14, 20, "买入开多", mouseX, mouseY,
                PageTheme.BTN_GOOD, PageTheme.BTN_GOOD_HOVER);
        drawBigButton(rightX + 7, buyY + 24, rightW - 14, 20, "卖出开空", mouseX, mouseY,
                PageTheme.BTN_SELL, PageTheme.BTN_SELL_HOVER);

        if (buyY + 48 < rightY + rightH - 8) {
            String hint = "§8保证金率 1/" + leverage + " · 手续费 0.055% · 滑点 0.02%";
            text(hint, rightX + 7, buyY + 49, PageTheme.TEXT_FAINT);
        }
    }

    private void drawDetail(PositionView position, int mouseX, int mouseY) {
        int top = rightY + 6;
        text("持仓详情", rightX + 7, top, PageTheme.TEXT_DIM);
        String back = "返回";
        boolean backHover = in(mouseX, mouseY, rightX + rightW - 7 - textW(back) - 4,
                top - 3, textW(back) + 8, 14);
        text(back, rightX + rightW - 7 - textW(back), top,
                backHover ? PageTheme.TEXT : PageTheme.TEXT_FAINT);

        text(position.productName, rightX + 7, top + 14,
                position.direction > 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);
        String line1 = (position.direction > 0 ? "§a多单" : "§c空单")
                + " §7" + format(position.quantity) + " 张";
        text(line1, rightX + 7, top + 26, PageTheme.TEXT_DIM);
        text("§7开仓 §f" + price(position.entryPrice) + "  §7标记 §f" + price(position.markPrice),
                rightX + 7, top + 38, PageTheme.TEXT_DIM);

        String pnl = (position.unrealized >= 0 ? "§a+" : "§c") + money(position.unrealized);
        text("§7保证金 §f" + money(position.margin), rightX + 7, top + 50, PageTheme.TEXT_DIM);
        text("§7浮盈 " + pnl, rightX + rightW - 7 - textW("浮盈 " + money(position.unrealized)),
                top + 50, position.unrealized >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);

        int fieldY = top + 68;
        text("平仓", rightX + 7, fieldY + 3, PageTheme.TEXT_DIM);
        drawField(qtyFieldX() - 18, fieldY, 70, 14, closeText, focus == FOCUS_CLOSE, "张");
        drawSmallButton(rightX + rightW - 7 - 24, fieldY, 24, 14, "全", mouseX, mouseY,
                PageTheme.BTN_ALT, PageTheme.BTN_ALT_HOVER);

        int closeY = top + 88;
        drawBigButton(rightX + 7, closeY, rightW - 14, 20, "市价平仓", mouseX, mouseY,
                PageTheme.BTN_WARN, PageTheme.BTN_WARN_HOVER);
        drawBigButton(rightX + 7, closeY + 24, rightW - 14, 18, "设置保护", mouseX, mouseY,
                PageTheme.BTN_PRIMARY, PageTheme.BTN_PRIMARY_HOVER);

        String protection = protectionSummary(position);
        if (protection != null && closeY + 46 < rightY + rightH - 4) {
            text(protection, rightX + 7, closeY + 46, PageTheme.TEXT_FAINT);
        }
    }

    private String protectionSummary(PositionView position) {
        if (position.protectionMode == StockConfig.PROTECTION_PRICE) {
            String stop = position.stopPrice > 0L ? "止损 " + price(position.stopPrice) : "";
            String take = position.takePrice > 0L ? "止盈 " + price(position.takePrice) : "";
            String text = (stop + "  " + take).trim();
            return text.isEmpty() ? null : "§8" + text;
        }
        if (position.protectionMode == StockConfig.PROTECTION_PROFIT) {
            String stop = position.stopAmount > 0L ? "止损 " + money(position.stopAmount) : "";
            String take = position.takeAmount > 0L ? "止盈 " + money(position.takeAmount) : "";
            String text = (stop + "  " + take).trim();
            return text.isEmpty() ? null : "§8" + text;
        }
        return null;
    }

    private void drawBottom(int mouseX, int mouseY) {
        rect(px, bottomY, px + pw, py + ph, PANEL_BG);
        rect(px, bottomY, px + pw, bottomY + 1, PANEL_BORDER);

        int tabW = (pw - 12) / 3;
        String[] labels = {"持仓 " + StockClientStore.positions().size(),
                "成交记录", "仓位保护"};
        for (int i = 0; i < 3; i++) {
            int tx = px + 6 + i * tabW;
            boolean active = bottomTab == i;
            boolean hovered = in(mouseX, mouseY, tx, bottomY, tabW, TAB_H);
            rect(tx, bottomY, tx + tabW, bottomY + TAB_H,
                    active ? 0x552F6E7E : (hovered ? ROW_HOVER : 0x22101822));
            if (active) {
                rect(tx, bottomY + TAB_H - 2, tx + tabW, bottomY + TAB_H, PageTheme.ACCENT);
            }
            text(labels[i], tx + 8, bottomY + 5,
                    active ? PageTheme.TEXT : PageTheme.TEXT_DIM);
        }

        if (bottomTab == TAB_POSITIONS) {
            drawPositions(mouseX, mouseY);
        } else if (bottomTab == TAB_RECORDS) {
            drawRecords(mouseX, mouseY);
        } else {
            drawProtection(mouseX, mouseY);
        }
    }

    private void drawPositions(int mouseX, int mouseY) {
        List<PositionView> positions = StockClientStore.positions();
        if (positions.isEmpty()) {
            text("§8暂无持仓，先在右侧下单开仓", px + 10, bottomY + TAB_H + 8, PageTheme.TEXT_FAINT);
            return;
        }
        int listTop = bottomY + TAB_H + 2;
        int maxRows = Math.max(1, (py + ph - listTop) / ROW_H);
        listScroll = clamp(0, Math.max(0, (positions.size() - maxRows) * ROW_H), listScroll);
        int first = listScroll / ROW_H;
        int last = Math.min(positions.size(), first + maxRows + 1);

        for (int i = first; i < last; i++) {
            PositionView position = positions.get(i);
            int ry = listTop + (i - first) * ROW_H - listScroll % ROW_H;
            boolean selected = position.id == selectedPosition;
            if (selected) {
                rect(px + 4, ry, px + pw - 4, ry + ROW_H - 1, ROW_SELECTED);
            } else if (in(mouseX, mouseY, px + 4, ry, pw - 8, ROW_H - 1)) {
                rect(px + 4, ry, px + pw - 4, ry + ROW_H - 1, ROW_HOVER);
            }
            int tx = px + 8;
            text(position.productName, tx, ry + 6,
                    position.direction > 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);
            int col = px + 150;
            text((position.direction > 0 ? "多" : "空") + " " + format(position.quantity) + " 张",
                    col, ry + 6, PageTheme.TEXT_DIM);
            col = px + 262;
            text("开 " + price(position.entryPrice) + "  标 " + price(position.markPrice),
                    col, ry + 6, PageTheme.TEXT_DIM);
            String pnl = (position.unrealized >= 0 ? "+" : "") + money(position.unrealized);
            text(pnl, px + pw - 12 - textW(pnl), ry + 6,
                    position.unrealized >= 0 ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);
        }
    }

    private void drawRecords(int mouseX, int mouseY) {
        List<RecordView> records = StockClientStore.records();
        if (records.isEmpty()) {
            text("§8暂无成交记录", px + 10, bottomY + TAB_H + 8, PageTheme.TEXT_FAINT);
            return;
        }
        int listTop = bottomY + TAB_H + 2;
        int maxRows = Math.max(1, (py + ph - listTop) / ROW_H);
        listScroll = clamp(0, Math.max(0, (records.size() - maxRows) * ROW_H), listScroll);
        int first = listScroll / ROW_H;
        int last = Math.min(records.size(), first + maxRows + 1);

        for (int i = first; i < last; i++) {
            RecordView record = records.get(i);
            int ry = listTop + (i - first) * ROW_H - listScroll % ROW_H;
            if (in(mouseX, mouseY, px + 4, ry, pw - 8, ROW_H - 1)) {
                rect(px + 4, ry, px + pw - 4, ry + ROW_H - 1, ROW_HOVER);
            }
            text(time(record.time), px + 8, ry + 6, PageTheme.TEXT_FAINT);
            text(record.productName, px + 72, ry + 6, PageTheme.TEXT_DIM);
            text(typeName(record.type), px + 176, ry + 6, typeColor(record.type));
            String detail = (record.direction > 0 ? "多 " : "空 ") + format(record.quantity)
                    + " 张 @ " + price(record.price);
            text(detail, px + 236, ry + 6, PageTheme.TEXT_DIM);
            if (record.pnl != 0L) {
                String pnl = (record.pnl > 0L ? "+" : "") + money(record.pnl);
                text(pnl, px + pw - 12 - textW(pnl), ry + 6,
                        record.pnl >= 0L ? PageTheme.STOCK_UP : PageTheme.STOCK_DOWN);
            }
        }
    }

    private void drawProtection(int mouseX, int mouseY) {
        PositionView position = selectedPosition == -1L ? null : StockClientStore.position(selectedPosition);
        if (position == null) {
            text("§8请先在「持仓」中选择一笔持仓，再设置止损 / 止盈", px + 10,
                    bottomY + TAB_H + 6, PageTheme.TEXT_FAINT);
            return;
        }
        int infoY = bottomY + TAB_H + 2;
        String info = (position.direction > 0 ? "§a多单" : "§c空单") + " §7"
                + position.productName + " §8· " + format(position.quantity)
                + " 张 · 标记 " + price(position.markPrice);
        text(info, px + 8, infoY, PageTheme.TEXT_DIM);

        int modeY = infoY + 14;
        drawModeButton(px + 8, modeY, 52, 14, "价格", protectionMode == StockConfig.PROTECTION_PRICE,
                mouseX, mouseY);
        drawModeButton(px + 64, modeY, 52, 14, "盈亏金额", protectionMode == StockConfig.PROTECTION_PROFIT,
                mouseX, mouseY);

        int fieldY = modeY + 20;
        String stopLabel = protectionMode == StockConfig.PROTECTION_PRICE ? "止损价" : "最大亏损";
        String takeLabel = protectionMode == StockConfig.PROTECTION_PRICE ? "止盈价" : "目标盈利";
        text(stopLabel, px + 8, fieldY + 3, PageTheme.TEXT_DIM);
        drawField(px + 48, fieldY, 92, 14, stopText, focus == FOCUS_STOP,
                protectionMode == StockConfig.PROTECTION_PRICE ? "" : currency());
        text(takeLabel, px + 156, fieldY + 3, PageTheme.TEXT_DIM);
        drawField(px + 196, fieldY, 92, 14, takeText, focus == FOCUS_TAKE,
                protectionMode == StockConfig.PROTECTION_PRICE ? "" : currency());

        int buttonY = fieldY + 20;
        drawSmallButton(px + 8, buttonY, 76, 18, "设置保护", mouseX, mouseY,
                PageTheme.BTN_PRIMARY, PageTheme.BTN_PRIMARY_HOVER);
        drawSmallButton(px + 90, buttonY, 76, 18, "清除保护", mouseX, mouseY,
                PageTheme.BTN_ALT, PageTheme.BTN_ALT_HOVER);

        String hint = protectionMode == StockConfig.PROTECTION_PRICE
                ? "§8价格模式：触发后按市价整笔平仓；留空表示不启用该方向"
                : "§8盈亏模式：亏损超过保证金 50% 会先触发强平，止损金额建议不超过该值";
        if (buttonY + 22 < py + ph - 2 && textW(hint) < pw - 184) {
            text(hint, px + 176, buttonY + 4, PageTheme.TEXT_FAINT);
        }
    }

    private void drawModeButton(int x, int y, int w, int h, String label, boolean active,
                                int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, y, w, h);
        rect(x, y, x + w, y + h, active ? 0x662F6E7E : (hovered ? ROW_HOVER : 0x33101822));
        if (active) {
            border(x, y, w, h, PageTheme.ACCENT);
        }
        text(label, x + (w - textW(label)) / 2f, y + 3,
                active ? PageTheme.TEXT : PageTheme.TEXT_DIM);
    }

    private void drawNotice() {
        if (System.currentTimeMillis() >= noticeUntil || notice.isEmpty()) {
            return;
        }
        String line = (noticeError ? "§c" : "§e") + notice;
        int w = Math.min(pw - 12, textW(notice) + 14);
        rect(px + 6, bodyTop + 2, px + 6 + w, bodyTop + 16,
                noticeError ? 0xCC3A1018 : 0xCC2A2410);
        border(px + 6, bodyTop + 2, w, 14, noticeError ? 0x99FF6B81 : 0x99FFD166);
        text(line, px + 13, bodyTop + 5, PageTheme.TEXT);
    }

    // ================================================================ 基础绘制

    private void drawField(int x, int y, int w, int h, String value, boolean focused, String suffix) {
        rect(x, y, x + w, y + h, PageTheme.FIELD_BG);
        border(x, y, w, h, focused ? PageTheme.FIELD_FOCUS : PageTheme.FIELD_BORDER);
        String shown = value;
        if (focused && blink % 20 < 10) {
            shown = shown + "_";
        }
        if (suffix != null && !suffix.isEmpty() && !focused) {
            int sw = textW(suffix);
            text(suffix, x + w - sw - 4, y + (h - 8) / 2f, PageTheme.TEXT_FAINT);
        }
        text(shown, x + 4, y + (h - 8) / 2f, PageTheme.TEXT);
    }

    private void drawBigButton(int x, int y, int w, int h, String label, int mouseX, int mouseY,
                               int base, int hoveredColor) {
        boolean hovered = in(mouseX, mouseY, x, y, w, h);
        rect(x, y, x + w, y + h, hovered ? hoveredColor : base);
        border(x, y, w, h, 0x33FFFFFF);
        text(label, x + (w - textW(label)) / 2f, y + (h - 8) / 2f, PageTheme.TEXT);
    }

    private void drawSmallButton(int x, int y, int w, int h, String label, int mouseX, int mouseY,
                                 int base, int hoveredColor) {
        boolean hovered = in(mouseX, mouseY, x, y, w, h);
        rect(x, y, x + w, y + h, hovered ? hoveredColor : base);
        border(x, y, w, h, 0x22FFFFFF);
        text(label, x + (w - textW(label)) / 2f, y + (h - 8) / 2f,
                hovered ? PageTheme.TEXT : PageTheme.TEXT_DIM);
    }

    private void rect(int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1) {
            host.fill(x1, y1, x2 - x1, y2 - y1, color);
        }
    }

    private void border(int x, int y, int w, int h, int color) {
        rect(x, y, x + w, y + 1, color);
        rect(x, y + h - 1, x + w, y + h, color);
        rect(x, y, x + 1, y + h, color);
        rect(x + w - 1, y, x + w, y + h, color);
    }

    private void text(String value, float x, float y, int color) {
        host.font().drawString(value, x, y, color, false);
    }

    private int textW(String value) {
        return host.font().getStringWidth(value);
    }

    private void line(int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            host.fill(x1, y1, 1, 1, color);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    // ================================================================ 交互

    public boolean click(int mouseX, int mouseY, int button) {
        if (!open || button != 0) {
            return false;
        }
        if (tooSmall) {
            return false;
        }

        if (clickChips(mouseX, mouseY)) {
            return true;
        }
        if (in(mouseX, mouseY, chartX, chartY, chartW, chartH)) {
            chartDrag = true;
            chartDragX = mouseX;
            chartDragStartOffset = panOffset;
            focus = FOCUS_NONE;
            return true;
        }
        if (compact) {
            return false;
        }
        if (clickRight(mouseX, mouseY)) {
            return true;
        }
        return clickBottom(mouseX, mouseY);
    }

    private boolean clickChips(int mouseX, int mouseY) {
        List<StockProduct> products = StockConfig.products();
        int gap = 4;
        int x = chipX;
        for (StockProduct product : products) {
            if (in(mouseX, mouseY, x, chipY, chipW, chipH)) {
                if (!product.id.equals(productId)) {
                    productId = product.id;
                    panOffset = 0;
                    closeMode = false;
                    selectedPosition = -1L;
                    focus = FOCUS_NONE;
                }
                return true;
            }
            x += chipW + gap;
        }
        return false;
    }

    private boolean clickRight(int mouseX, int mouseY) {
        PositionView selected = selectedPosition == -1L ? null : StockClientStore.position(selectedPosition);
        if (closeMode && selected != null) {
            return clickDetail(selected, mouseX, mouseY);
        }
        return clickOrder(mouseX, mouseY);
    }

    private boolean clickOrder(int mouseX, int mouseY) {
        int top = rightY + 6;
        int qtyY = top + 30;
        if (in(mouseX, mouseY, qtyFieldX(), qtyY, 84, 14)) {
            focus = FOCUS_QTY;
            return true;
        }

        int quickY = top + 48;
        int quickGap = 3;
        int quickW = (rightW - 14 - quickGap * 3) / 4;
        String[] quick = {"+1", "+10", "+100", "最大"};
        for (int i = 0; i < quick.length; i++) {
            int qx = rightX + 7 + i * (quickW + quickGap);
            if (in(mouseX, mouseY, qx, quickY, quickW, 14)) {
                applyQuick(i);
                return true;
            }
        }

        int levY = top + 66;
        int levW = Math.max(22, (rightW - 49 - 4 * 2) / 5);
        for (int i = 0; i < StockConfig.LEVERAGES.length; i++) {
            int bx = rightX + 42 + i * (levW + 2);
            if (in(mouseX, mouseY, bx, levY, levW, 14)) {
                leverage = StockConfig.LEVERAGES[i];
                return true;
            }
        }

        int buyY = orderBuyY();
        if (in(mouseX, mouseY, rightX + 7, buyY, rightW - 14, 20)) {
            sendOpen(1);
            return true;
        }
        if (in(mouseX, mouseY, rightX + 7, buyY + 24, rightW - 14, 20)) {
            sendOpen(-1);
            return true;
        }
        focus = FOCUS_NONE;
        return false;
    }

    private boolean clickDetail(PositionView position, int mouseX, int mouseY) {
        int top = rightY + 6;
        if (in(mouseX, mouseY, rightX + rightW - 7 - textW("返回") - 4, top - 3,
                textW("返回") + 8, 14)) {
            closeMode = false;
            focus = FOCUS_NONE;
            return true;
        }
        int fieldY = top + 68;
        if (in(mouseX, mouseY, qtyFieldX() - 18, fieldY, 70, 14)) {
            focus = FOCUS_CLOSE;
            return true;
        }
        if (in(mouseX, mouseY, rightX + rightW - 7 - 24, fieldY, 24, 14)) {
            closeText = Long.toString(position.quantity);
            return true;
        }
        int closeY = top + 88;
        if (in(mouseX, mouseY, rightX + 7, closeY, rightW - 14, 20)) {
            sendClose(position, false);
            return true;
        }
        if (in(mouseX, mouseY, rightX + 7, closeY + 24, rightW - 14, 18)) {
            bottomTab = TAB_PROTECTION;
            protectionMode = position.protectionMode == StockConfig.PROTECTION_NONE
                    ? StockConfig.PROTECTION_PRICE : position.protectionMode;
            fillProtectionFields(position);
            focus = FOCUS_NONE;
            return true;
        }
        focus = FOCUS_NONE;
        return false;
    }

    private boolean clickBottom(int mouseX, int mouseY) {
        int tabW = (pw - 12) / 3;
        for (int i = 0; i < 3; i++) {
            int tx = px + 6 + i * tabW;
            if (in(mouseX, mouseY, tx, bottomY, tabW, TAB_H)) {
                bottomTab = i;
                focus = FOCUS_NONE;
                return true;
            }
        }

        if (bottomTab == TAB_POSITIONS) {
            List<PositionView> positions = StockClientStore.positions();
            int listTop = bottomY + TAB_H + 2;
            if (!in(mouseX, mouseY, px + 4, listTop, pw - 8, py + ph - listTop)) {
                return false;
            }
            int index = (mouseY - listTop + listScroll) / ROW_H;
            if (index >= 0 && index < positions.size()) {
                selectPosition(positions.get(index));
                return true;
            }
            return false;
        }

        if (bottomTab == TAB_PROTECTION) {
            PositionView position = selectedPosition == -1L
                    ? null : StockClientStore.position(selectedPosition);
            if (position == null) {
                return false;
            }
            int infoY = bottomY + TAB_H + 2;
            int modeY = infoY + 14;
            if (in(mouseX, mouseY, px + 8, modeY, 52, 14)) {
                protectionMode = StockConfig.PROTECTION_PRICE;
                fillProtectionFields(position);
                return true;
            }
            if (in(mouseX, mouseY, px + 64, modeY, 52, 14)) {
                protectionMode = StockConfig.PROTECTION_PROFIT;
                fillProtectionFields(position);
                return true;
            }
            int fieldY = modeY + 20;
            if (in(mouseX, mouseY, px + 48, fieldY, 92, 14)) {
                focus = FOCUS_STOP;
                return true;
            }
            if (in(mouseX, mouseY, px + 196, fieldY, 92, 14)) {
                focus = FOCUS_TAKE;
                return true;
            }
            int buttonY = fieldY + 20;
            if (in(mouseX, mouseY, px + 8, buttonY, 76, 18)) {
                sendProtection(position);
                return true;
            }
            if (in(mouseX, mouseY, px + 90, buttonY, 76, 18)) {
                PacketHandler.sendToServer(MessageStockAction.clearProtection(position.id));
                focus = FOCUS_NONE;
                return true;
            }
        }
        return false;
    }

    public boolean wheel(int mouseX, int mouseY, int wheel) {
        if (!open || tooSmall) {
            return false;
        }
        if (in(mouseX, mouseY, chartX, chartY, chartW, chartH)) {
            ProductView product = StockClientStore.product(productId);
            int total = product == null ? 0 : product.bars.size();
            zoomBars = clamp(20, 100, zoomBars - Integer.signum(wheel) * 5);
            panOffset = clamp(0, Math.max(0, total - zoomBars), panOffset);
            return true;
        }
        if (!compact && in(mouseX, mouseY, px, bottomY + TAB_H, pw, bottomH - TAB_H)) {
            int max = bottomTab == TAB_POSITIONS
                    ? Math.max(0, StockClientStore.positions().size() - 1) * ROW_H
                    : Math.max(0, StockClientStore.records().size() - 1) * ROW_H;
            listScroll = clamp(0, max, listScroll - Integer.signum(wheel) * ROW_H);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY) {
        if (!open || !chartDrag) {
            return false;
        }
        ProductView product = StockClientStore.product(productId);
        int total = product == null ? 0 : product.bars.size();
        int visible = Math.min(zoomBars, total);
        int plotW = Math.max(40, chartW - 54);
        int delta = (mouseX - chartDragX) * visible / plotW;
        panOffset = clamp(0, Math.max(0, total - visible), chartDragStartOffset + delta);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (chartDrag) {
            chartDrag = false;
            return true;
        }
        return false;
    }

    public boolean key(char typedChar, int keyCode) {
        if (!open || focus == FOCUS_NONE) {
            return false;
        }
        String value = focusedText();
        if (keyCode == Keyboard.KEY_BACK) {
            if (!value.isEmpty()) {
                setFocusedText(value.substring(0, value.length() - 1));
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                || keyCode == Keyboard.KEY_ESCAPE) {
            focus = FOCUS_NONE;
            return true;
        }
        if (Character.isDigit(typedChar) || typedChar == '.' || (typedChar == '-' && value.isEmpty())) {
            if (value.length() < 12) {
                setFocusedText(value + typedChar);
            }
            return true;
        }
        return true;
    }

    private String focusedText() {
        switch (focus) {
            case FOCUS_QTY:
                return qtyText;
            case FOCUS_CLOSE:
                return closeText;
            case FOCUS_STOP:
                return stopText;
            case FOCUS_TAKE:
                return takeText;
            default:
                return "";
        }
    }

    private void setFocusedText(String value) {
        switch (focus) {
            case FOCUS_QTY:
                qtyText = value;
                break;
            case FOCUS_CLOSE:
                closeText = value;
                break;
            case FOCUS_STOP:
                stopText = value;
                break;
            case FOCUS_TAKE:
                takeText = value;
                break;
            default:
                break;
        }
    }

    // ================================================================ 业务

    private void applyQuick(int index) {
        long wallet = StockClientStore.wallet();
        ProductView product = StockClientStore.product(productId);
        long mark = product == null ? 0L : product.price;
        long current = parseQty(qtyText);
        long value;
        if (index == 3) {
            value = maxQuantity(wallet, mark, 1);
        } else {
            long add = index == 0 ? 1L : (index == 1 ? 10L : 100L);
            value = current + add;
        }
        qtyText = Long.toString(clamp(1L, StockConfig.MAX_POSITION, value));
        focus = FOCUS_NONE;
    }

    private long maxQuantity(long wallet, long mark, int direction) {
        if (mark <= 0L || wallet <= 0L) {
            return 1L;
        }
        double per = mark * (1.0 / leverage + 55.0 / 100000.0);
        long guess = (long) (wallet / per);
        guess = clamp(1L, StockConfig.MAX_POSITION, guess);
        int guard = 0;
        while (guess > 1L && cost(mark, guess, direction) > wallet && guard++ < 4096) {
            guess--;
        }
        guard = 0;
        while (guess < StockConfig.MAX_POSITION && cost(mark, guess + 1, direction) <= wallet
                && guard++ < 64) {
            guess++;
        }
        return guess;
    }

    private long cost(long mark, long quantity, int direction) {
        long entry = entryPrice(mark, direction);
        long notional = entry * quantity;
        return StockConfig.ceilDiv(notional, leverage) + fee(notional);
    }

    private void sendOpen(int direction) {
        long quantity = parseQty(qtyText);
        if (quantity <= 0L) {
            pushNotice("请输入有效的数量", true);
            return;
        }
        PacketHandler.sendToServer(MessageStockAction.open(productId, direction, quantity, leverage));
        focus = FOCUS_NONE;
    }

    private void sendClose(PositionView position, boolean all) {
        long quantity = all ? position.quantity : parseQty(closeText);
        if (quantity <= 0L) {
            quantity = position.quantity;
        }
        quantity = Math.min(quantity, position.quantity);
        PacketHandler.sendToServer(MessageStockAction.close(position.id, quantity));
        focus = FOCUS_NONE;
    }

    private void sendProtection(PositionView position) {
        if (protectionMode == StockConfig.PROTECTION_PRICE) {
            long stop = parseMoney(stopText);
            long take = parseMoney(takeText);
            if (stop <= 0L && take <= 0L) {
                pushNotice("请至少填写止损价或止盈价", true);
                return;
            }
            PacketHandler.sendToServer(MessageStockAction.protect(position.id,
                    StockConfig.PROTECTION_PRICE, stop, take, 0L, 0L));
        } else {
            long stop = parseMoney(stopText);
            long take = parseMoney(takeText);
            if (stop <= 0L && take <= 0L) {
                pushNotice("请至少填写止损或止盈金额", true);
                return;
            }
            PacketHandler.sendToServer(MessageStockAction.protect(position.id,
                    StockConfig.PROTECTION_PROFIT, 0L, 0L, stop, take));
        }
        focus = FOCUS_NONE;
    }

    private void selectPosition(PositionView position) {
        selectedPosition = position.id;
        closeMode = true;
        closeText = Long.toString(position.quantity);
        protectionMode = position.protectionMode == StockConfig.PROTECTION_NONE
                ? StockConfig.PROTECTION_PRICE : position.protectionMode;
        fillProtectionFields(position);
        focus = FOCUS_NONE;
        listScroll = 0;
    }

    private void fillProtectionFields(PositionView position) {
        if (protectionMode == StockConfig.PROTECTION_PROFIT) {
            stopText = position.stopAmount > 0L ? plain(position.stopAmount) : "";
            takeText = position.takeAmount > 0L ? plain(position.takeAmount) : "";
        } else {
            stopText = position.stopPrice > 0L ? plain(position.stopPrice) : "";
            takeText = position.takePrice > 0L ? plain(position.takePrice) : "";
        }
    }

    private int orderBuyY() {
        return rightY + rightH - 52;
    }

    private int qtyFieldX() {
        return rightX + rightW - 7 - 84;
    }

    // ================================================================ 格式化

    private static long entryPrice(long mark, int direction) {
        if (direction > 0) {
            return (mark * 10002L + 9999L) / 10000L;
        }
        return mark * 9998L / 10000L;
    }

    private static long fee(long notional) {
        return StockConfig.ceilDiv(notional * StockConfig.FEE_NUMERATOR,
                StockConfig.FEE_DENOMINATOR);
    }

    private static long parseQty(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed <= 0L ? 0L : Math.min(parsed, StockConfig.MAX_POSITION);
        } catch (NumberFormatException invalid) {
            return 0L;
        }
    }

    private static long parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed <= 0.0) {
                return 0L;
            }
            return Math.round(parsed * 100.0);
        } catch (NumberFormatException invalid) {
            return 0L;
        }
    }

    private static String currency() {
        try {
            String name = PageConfigs.shop().currencyName;
            return name == null || name.trim().isEmpty() ? "货币" : name.trim();
        } catch (Throwable ignored) {
            return "货币";
        }
    }

    private static String price(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    private static String plain(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    private static String money(long cents) {
        boolean negative = cents < 0L;
        long value = Math.abs(cents);
        long whole = value / 100L;
        long fraction = value % 100L;
        StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        String digits = Long.toString(whole);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(',');
            }
            out.append(digits.charAt(i));
        }
        if (fraction != 0L) {
            out.append('.').append(fraction < 10L ? "0" : "").append(fraction);
        }
        return out.toString();
    }

    private static String format(long value) {
        StringBuilder out = new StringBuilder();
        String digits = Long.toString(Math.abs(value));
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(',');
            }
            out.append(digits.charAt(i));
        }
        return (value < 0L ? "-" : "") + out;
    }

    private static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.endsWith("指数") ? name.substring(0, name.length() - 2) : name;
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(millis));
    }

    private static String typeName(int type) {
        try {
            StockTradeRecord.Type value = StockTradeRecord.Type.values()[type];
            switch (value) {
                case OPEN:
                    return "开仓";
                case CLOSE:
                    return "平仓";
                case LIQUIDATE:
                    return "强平";
                case STOP:
                    return "止损";
                case TAKE:
                    return "止盈";
                default:
                    return "记录";
            }
        } catch (RuntimeException invalid) {
            return "记录";
        }
    }

    private static int typeColor(int type) {
        try {
            switch (StockTradeRecord.Type.values()[type]) {
                case OPEN:
                    return PageTheme.TEXT_DIM;
                case CLOSE:
                    return PageTheme.TEXT;
                case LIQUIDATE:
                    return PageTheme.STOCK_DOWN;
                case STOP:
                    return PageTheme.BTN_WARN_HOVER;
                case TAKE:
                    return PageTheme.STOCK_UP;
                default:
                    return PageTheme.TEXT_FAINT;
            }
        } catch (RuntimeException invalid) {
            return PageTheme.TEXT_FAINT;
        }
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long min, long max, long value) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
