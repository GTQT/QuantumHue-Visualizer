package meowmel.quantumhue.page;

import meowmel.quantumhue.config.PageConfigs;
import meowmel.quantumhue.market.BuyOrder;
import meowmel.quantumhue.market.SystemShopCatalog;
import meowmel.quantumhue.market.TradeItem;
import meowmel.quantumhue.market.client.ShopClientStore;
import meowmel.quantumhue.network.PacketHandler;
import meowmel.quantumhue.network.packet.MessageBuyItem;
import meowmel.quantumhue.network.packet.MessageRemoveListing;
import meowmel.quantumhue.network.packet.MessageSellItem;
import meowmel.quantumhue.network.packet.MessageSellToOrder;
import meowmel.quantumhue.network.packet.MessageShopAction;
import meowmel.quantumhue.network.packet.MessageSystemBuy;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 自由交易商店与系统商店。
 *
 * <p>页签：系统商店 / 浏览商品 / 收购订单 / 出售物品 / 我的上架。系统商店的分类与商品全部来自
 * {@code config/quantumhue/shop.json}，货架由服务端按配置 + 已售记录重建后同步过来，所以玩家可以
 * 自由增删分类与商品，而不需要改动代码。
 *
 * <p>商店数据是服务端权威的：界面只读 {@link ShopClientStore}，打开页签时向服务端请求，买卖后由
 * 服务端回发增量或整页。客户端不直接访问 {@code ShopDatabase}。
 */
@SideOnly(Side.CLIENT)
public class GuiShop extends PageScreen {

    private static final int SEARCH_H = 16;
    private static final int ITEM_H = 48;
    private static final int ACTION_H = 76;
    private static final int ACTION_BTN_H = 22;
    private static final int FIELD_H = 12;
    /** 出售表单总高：物品卡 44 + 两行输入 + 按钮。 */
    private static final int SELL_FORM_H = 44 + 56 + 24 + 6;
    /** 系统商店左侧分类列的最大/最小宽度。 */
    private static final int CATEGORY_W_MAX = 120;
    private static final int CATEGORY_W_MIN = 80;
    private static final int CATEGORY_ITEM_H = 22;

    private final EntityPlayer player;
    private long playerMoney;

    // 内容列（搜索框 + 列表）
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int lx;
    private int ly;
    private int lw;
    private int lh;

    // 右侧操作面板
    private int ax;
    private int ay;

    private Tab currentTab = Tab.SYSTEM;
    private SystemShopCatalog.Category currentCategory;
    private List<TradeItem> listings = new ArrayList<>();
    private List<BuyOrder> buyOrders = new ArrayList<>();
    private List<SystemShopCatalog.Entry> systemEntries = new ArrayList<>();
    private TradeItem selectedListing;
    private BuyOrder selectedOrder;
    private SystemShopCatalog.Entry selectedSystem;

    private int scroll;
    private int maxScroll;
    private boolean dragScroll;
    private int dragY;
    private int dragStartScroll;

    private int categoryScroll;
    private int maxCategoryScroll;

    private GuiTextField searchField;
    private GuiTextField qtyField;
    private GuiTextField priceField;
    private GuiTextField sellQtyField;

    private final List<TabBtn> tabs = new ArrayList<>();
    private int tabHeight = PageTheme.TAB_H;
    private boolean actionHover;
    private Integer hoverIdx;
    private Integer hoverOrderIdx;

    private static final java.text.NumberFormat FMT =
            java.text.NumberFormat.getNumberInstance(Locale.US);

    static {
        FMT.setGroupingUsed(true);
    }

    public GuiShop(EntityPlayer player) {
        this.player = player;
    }

    private static String f(long value) {
        return FMT.format(value);
    }

    // ================================================================ 生命周期

    @Override
    protected void initPage() {
        PageConfigs.reloadLocal();
        layout();
        buildTabs();
        initFields();
        reload();
        rescroll();
        requestCurrentTab(0);
    }

    private void layout() {
        cx = winX + PageTheme.PAD;
        cy = contentY;
        cw = Math.max(80, tabX - PageTheme.GAP - cx);
        ch = Math.max(ITEM_H, winHeight - PageTheme.HEADER_H - PageTheme.PAD * 2);

        ax = tabX;
        ay = winY + winHeight - PageTheme.PAD - ACTION_H;

        lx = cx;
        ly = cy + SEARCH_H + PageTheme.GAP;
        lw = Math.max(40, cw - PageTheme.SCROLL_W - PageTheme.GAP);
        applyLayout();
    }

    private void applyLayout() {
        int reserve = showsAction() ? ACTION_H + PageTheme.GAP : 0;
        lh = Math.max(ITEM_H, ch - SEARCH_H - PageTheme.GAP - reserve);
        if (currentTab == Tab.SELL) {
            lh = Math.max(lh, SELL_FORM_H);
        }
        rescroll();
    }

    private boolean showsAction() {
        return (currentTab == Tab.SYSTEM && selectedSystem != null && !selectedSystem.soldOut())
                || (currentTab == Tab.BROWSE && selectedListing != null)
                || (currentTab == Tab.BUY_ORDERS && selectedOrder != null);
    }

    private void buildTabs() {
        tabs.clear();
        int count = Tab.values().length;
        // 页签列不能让操作面板吃掉：窗口矮时按可用高度压缩每格，而不是让最后两格叠在面板上
        int available = Math.max(count * 18, ay - contentY - PageTheme.GAP);
        tabHeight = Math.min(PageTheme.TAB_H,
                Math.max(18, (available - (count - 1) * PageTheme.TAB_GAP) / count));
        int y = contentY;
        for (Tab tab : Tab.values()) {
            tabs.add(new TabBtn(tab, tabX, y, PageTheme.SIDE_COL_W, tabHeight));
            y += tabHeight + PageTheme.TAB_GAP;
        }
    }

    private void initFields() {
        searchField = field(1, lx, cy, cw - PageTheme.SCROLL_W - PageTheme.GAP, FIELD_H);
        qtyField = field(2, ax + PageTheme.GAP + 34, actionRowY() + 2,
                PageTheme.SIDE_COL_W - PageTheme.GAP * 2 - 34, FIELD_H);
        int sellTop = ly;
        int sellLabelW = 34;
        int sellFieldW = Math.max(60, lw - sellLabelW - PageTheme.GAP * 2);
        priceField = field(3, lx + sellLabelW, sellTop + 54, sellFieldW, FIELD_H);
        sellQtyField = field(4, lx + sellLabelW, sellTop + 80, sellFieldW, FIELD_H);
        priceField.setText("1");
        sellQtyField.setText("1");
        qtyField.setText("1");
    }

    private GuiTextField field(int id, int x, int y, int w, int h) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, w, h);
        field.setMaxStringLength(30);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(0xFFFFFF);
        return field;
    }

    // ================================================================ 服务端请求

    private void requestCurrentTab(int offset) {
        switch (currentTab) {
            case SYSTEM:
                PacketHandler.sendToServer(MessageShopAction.system());
                break;
            case BROWSE:
                PacketHandler.sendToServer(MessageShopAction.browse(text(searchField), offset));
                break;
            case BUY_ORDERS:
                PacketHandler.sendToServer(MessageShopAction.buyOrders(text(searchField), offset));
                break;
            case MY_LISTINGS:
            case SELL:
            default:
                PacketHandler.sendToServer(MessageShopAction.own());
                break;
        }
    }

    private static String text(GuiTextField field) {
        return field == null ? "" : field.getText().trim();
    }

    // ================================================================ 数据刷新

    private void reload() {
        if (currentTab == Tab.SYSTEM) {
            ensureCategory();
            rebuildSystemEntries();
        } else if (currentTab == Tab.BROWSE) {
            listings = ShopClientStore.filterListings(text(searchField));
        } else if (currentTab == Tab.BUY_ORDERS) {
            buyOrders = ShopClientStore.filterBuyOrders(text(searchField));
        } else if (currentTab == Tab.MY_LISTINGS) {
            listings = ownListings();
        }
        playerMoney = ShopClientStore.playerMoney();
    }

    private void ensureCategory() {
        SystemShopCatalog catalog = ShopClientStore.systemShop();
        if (catalog.isEmpty()) {
            currentCategory = null;
            return;
        }
        if (currentCategory != null) {
            for (SystemShopCatalog.Category category : catalog.categories()) {
                if (category.id.equals(currentCategory.id)) {
                    currentCategory = category;
                    return;
                }
            }
        }
        currentCategory = catalog.categories().get(0);
    }

    private void rebuildSystemEntries() {
        if (currentCategory == null) {
            systemEntries = Collections.emptyList();
            return;
        }
        String search = text(searchField).toLowerCase(Locale.ROOT);
        List<SystemShopCatalog.Entry> filtered = new ArrayList<>();
        for (SystemShopCatalog.Entry entry : currentCategory.entries) {
            if (search.isEmpty() || entry.displayName.toLowerCase(Locale.ROOT).contains(search)) {
                filtered.add(entry);
            }
        }
        systemEntries = filtered;
        // 货架每次购买后都会重建，Entry 是新对象；按 id 重新绑定，选中状态才不会凭空消失
        if (selectedSystem != null) {
            SystemShopCatalog.Entry refreshed = null;
            for (SystemShopCatalog.Entry entry : systemEntries) {
                if (entry.id.equals(selectedSystem.id)
                        && entry.categoryId.equals(selectedSystem.categoryId)) {
                    refreshed = entry;
                    break;
                }
            }
            selectedSystem = refreshed;
        }
    }

    private void rescroll() {
        maxScroll = Math.max(0, listSize() * ITEM_H - lh);
        scroll = clamp(0, maxScroll, scroll);
        int categoryViewHeight = Math.max(CATEGORY_ITEM_H, lh);
        maxCategoryScroll = Math.max(0,
                categoryCount() * (CATEGORY_ITEM_H + 3) - categoryViewHeight);
        categoryScroll = clamp(0, maxCategoryScroll, categoryScroll);
    }

    private int listSize() {
        return switch (currentTab) {
            case SYSTEM -> systemEntries.size();
            case BROWSE, MY_LISTINGS -> listings.size();
            case BUY_ORDERS -> buyOrders.size();
            default -> 0;
        };
    }

    private int categoryCount() {
        SystemShopCatalog catalog = ShopClientStore.systemShop();
        return catalog.categories().size();
    }

    // ================================================================ 绘制

    @Override
    protected String pageTitle() {
        return "自由交易商店";
    }

    @Override
    protected String pageStatus() {
        return String.format("§7余额 §e%s §7%s", f(playerMoney), currency());
    }

    private static String currency() {
        try {
            String name = PageConfigs.shop().currencyName;
            return name == null || name.trim().isEmpty() ? "货币" : name.trim();
        } catch (Throwable ignored) {
            return "货币";
        }
    }

    @Override
    protected void drawPage(int mouseX, int mouseY) {
        drawTabs(mouseX, mouseY);
        if (showsAction()) {
            drawAction(mouseX, mouseY);
        }
        drawFields();

        beginClip(lx, ly, lw + PageTheme.GAP + PageTheme.SCROLL_W, lh);
        drawContent(mouseX, mouseY);
        if (maxScroll > 0) {
            drawScrollbar(lx + lw + PageTheme.GAP, ly, PageTheme.SCROLL_W, lh, scroll, maxScroll,
                    dragScroll, mouseX, mouseY);
        }
        endClip();
        drawFade(lx, ly, lw + PageTheme.GAP + PageTheme.SCROLL_W, 3, true);
        drawFade(lx, ly + lh - 3, lw + PageTheme.GAP + PageTheme.SCROLL_W, 3, false);
    }

    private void drawTabs(int mouseX, int mouseY) {
        for (TabBtn tab : tabs) {
            tab.draw(mouseX, mouseY);
        }
    }

    private void drawContent(int mouseX, int mouseY) {
        switch (currentTab) {
            case SYSTEM:
                drawSystem(mouseX, mouseY);
                break;
            case BROWSE:
                drawBrowse(mouseX, mouseY);
                break;
            case BUY_ORDERS:
                drawBuyOrders(mouseX, mouseY);
                break;
            case SELL:
                drawSell(mouseX, mouseY);
                break;
            case MY_LISTINGS:
                drawMy(mouseX, mouseY);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- 系统商店

    private void drawSystem(int mouseX, int mouseY) {
        SystemShopCatalog catalog = ShopClientStore.systemShop();
        if (catalog.isEmpty()) {
            drawEmpty("系统商店暂无商品");
            return;
        }

        int categoryW = Math.min(CATEGORY_W_MAX, Math.max(CATEGORY_W_MIN, cw / 4));
        int categoryX = lx;
        int categoryY = ly;
        int categoryH = lh;

        drawRect(categoryX, categoryY, categoryX + categoryW, categoryY + categoryH, PageTheme.CONTENT_BG);
        drawRect(categoryX + categoryW - 1, categoryY, categoryX + categoryW, categoryY + categoryH,
                PageTheme.SEPARATOR);

        beginClip(categoryX, categoryY, categoryW, categoryH);
        int y = categoryY + 4 - categoryScroll;
        for (SystemShopCatalog.Category category : catalog.categories()) {
            if (y + CATEGORY_ITEM_H > categoryY && y < categoryY + categoryH) {
                boolean active = currentCategory != null && currentCategory.id.equals(category.id);
                boolean hovered = in(mouseX, mouseY, categoryX + 4, y, categoryW - 8, CATEGORY_ITEM_H);
                drawSideTab(categoryX + 4, y, categoryW - 8, CATEGORY_ITEM_H,
                        fontRenderer.trimStringToWidth(category.name, categoryW - 26),
                        active, hovered, PageTheme.ACCENT);
            }
            y += CATEGORY_ITEM_H + 3;
        }
        endClip();
        if (maxCategoryScroll > 0) {
            drawRect(categoryX + categoryW - 3, categoryY, categoryX + categoryW - 1,
                    categoryY + categoryH, PageTheme.SCROLL_TRACK);
            int thumbHeight = Math.max(12, categoryH * categoryH / (maxCategoryScroll + categoryH));
            int thumbY = categoryY + categoryScroll * (categoryH - thumbHeight) / maxCategoryScroll;
            drawRect(categoryX + categoryW - 3, thumbY, categoryX + categoryW - 1,
                    thumbY + thumbHeight, PageTheme.SCROLL_THUMB);
        }

        int itemX = categoryX + categoryW + PageTheme.GAP;
        int itemW = Math.max(40, lx + lw - itemX);

        hoverIdx = null;
        int start = Math.max(0, scroll / ITEM_H);
        int end = Math.min(systemEntries.size(), start + lh / ITEM_H + 2);
        beginClip(itemX, ly, itemW, lh);
        for (int i = start; i < end; i++) {
            SystemShopCatalog.Entry entry = systemEntries.get(i);
            int iy = ly + i * ITEM_H - scroll;
            if (iy + ITEM_H <= ly || iy >= ly + lh) {
                continue;
            }
            boolean selected = selectedSystem != null && selectedSystem.id.equals(entry.id)
                    && selectedSystem.categoryId.equals(entry.categoryId);
            String stockText = entry.stock < 0 ? "§7库存 §a∞"
                    : (entry.stock == 0 ? "§c已售罄" : "§7库存 §f" + entry.stock + " §7份");
            if (drawItemCard(entry.stack, entry.displayName,
                    String.format("§7单价 §e%s", f(entry.price)),
                    String.format("§7每份 §b%s §7个", entry.count),
                    null, stockText, null,
                    itemX, iy, itemW, mouseX, mouseY, selected)) {
                hoverIdx = i;
            }
        }
        endClip();
        if (systemEntries.isEmpty()) {
            drawEmpty("该分类暂无商品");
        }
    }

    // ---------------------------------------------------------------- 浏览

    private void drawBrowse(int mouseX, int mouseY) {
        hoverIdx = null;
        int start = Math.max(0, scroll / ITEM_H);
        int end = Math.min(listings.size(), start + lh / ITEM_H + 2);
        beginClip(lx, ly, lw, lh);
        for (int i = start; i < end; i++) {
            TradeItem item = listings.get(i);
            int iy = ly + i * ITEM_H - scroll;
            if (iy + ITEM_H <= ly || iy >= ly + lh) {
                continue;
            }
            boolean selected = selectedListing != null && selectedListing.id.equals(item.id);
            if (drawItemCard(item.itemStack, item.itemStack.getDisplayName(),
                    String.format("§7卖家 §e%1$s", sellerName(item)),
                    String.format("§7单价 §e%1$s", f(item.pricePerUnit)),
                    "§7×" + item.quantity, item.getRemainingTimeString(), null,
                    lx, iy, lw, mouseX, mouseY, selected)) {
                hoverIdx = i;
            }
        }
        endClip();
        if (listings.isEmpty()) {
            drawEmpty("暂无商品上架");
        }
    }

    // ---------------------------------------------------------------- 收购

    private void drawBuyOrders(int mouseX, int mouseY) {
        hoverOrderIdx = null;
        int start = Math.max(0, scroll / ITEM_H);
        int end = Math.min(buyOrders.size(), start + lh / ITEM_H + 2);
        beginClip(lx, ly, lw, lh);
        for (int i = start; i < end; i++) {
            BuyOrder order = buyOrders.get(i);
            int iy = ly + i * ITEM_H - scroll;
            if (iy + ITEM_H <= ly || iy >= ly + lh) {
                continue;
            }
            boolean selected = selectedOrder != null && selectedOrder.id.equals(order.id);
            if (drawItemCard(order.itemStack, order.itemStack.getDisplayName(),
                    String.format("§7收购单价 §e%1$s", f(order.pricePerUnit)),
                    String.format("§7剩余 §b%1$s§8/%2$s", order.getRemaining(), order.quantity),
                    null, order.getRemainingTimeString(),
                    String.format("§6总价 %1$s", f(order.pricePerUnit * order.quantity)),
                    lx, iy, lw, mouseX, mouseY, selected)) {
                hoverOrderIdx = i;
            }
        }
        endClip();
        if (buyOrders.isEmpty()) {
            drawEmpty("暂无收购订单");
        }
    }

    /** 通用物品卡片。 */
    private boolean drawItemCard(ItemStack icon, String name,
                                 String line1, String line2, String sub3,
                                 String rightTop, String rightBot,
                                 int x, int y, int w, int mouseX, int mouseY, boolean selected) {
        boolean hovered = in(mouseX, mouseY, x, y, w, ITEM_H);
        int background = selected ? PageTheme.ITEM_SELECTED
                : (hovered ? PageTheme.ITEM_HOVER : PageTheme.ITEM_BG);
        drawRect(x, y, x + w, y + ITEM_H, background);
        if (selected) {
            drawRect(x, y, x + 2, y + ITEM_H, PageTheme.ACCENT);
        }

        if (!icon.isEmpty() && y + 16 > ly && y + 8 < ly + lh) {
            drawRect(x + 7, y + 7, x + 39, y + 39, 0x18000000);
            drawItem(icon, x + 11, y + 11);
        }

        int textX = x + 44;
        drawClipped(name, textX, y + 7, 0xFFFFFF);
        drawClipped(line1, textX, y + 20, PageTheme.TEXT_DIM);
        drawClipped(line2, textX, y + 32, PageTheme.TEXT_DIM);

        if (sub3 != null) {
            drawClipped(sub3, x + w - 5 - fontRenderer.getStringWidth(sub3), y + 20, PageTheme.TEXT_FAINT);
        }
        if (rightTop != null) {
            drawClipped(rightTop, x + w - 5 - fontRenderer.getStringWidth(rightTop), y + 7, 0xFFDDDDDD);
        }
        if (rightBot != null) {
            drawClipped(rightBot, x + w - 5 - fontRenderer.getStringWidth(rightBot), y + 32, 0xFF55FF55);
        }
        drawRect(x + 2, y + ITEM_H - 1, x + w - 2, y + ITEM_H, PageTheme.ITEM_DIVIDER);
        return hovered;
    }

    // ---------------------------------------------------------------- 出售 / 我的

    private int sellCardY() {
        return ly;
    }

    private void drawSell(int mouseX, int mouseY) {
        ItemStack held = player.getHeldItemMainhand();
        int iy = sellCardY();
        int cardH = 44;
        drawRect(lx, iy, lx + lw, iy + cardH, PageTheme.ITEM_BG);
        if (!held.isEmpty()) {
            drawItem(held, lx + 10, iy + 6);
            text(held.getDisplayName(), lx + 38, iy + 8, 0xFFFFFF);
            text(String.format("§7持有 %1$s 个", held.getCount()), lx + 38, iy + 22, PageTheme.TEXT_DIM);
        } else {
            text("§7请手持要出售的物品", lx + 12, iy + 14, PageTheme.TEXT_FAINT);
        }

        text("§7单价", lx, iy + cardH + 10, PageTheme.TEXT_DIM);
        text("§7数量", lx, iy + cardH + 36, PageTheme.TEXT_DIM);

        int bx = lx;
        int by = iy + cardH + 56;
        int bw = 110;
        int bh = 24;
        boolean canSell = !held.isEmpty();
        boolean hovered = in(mouseX, mouseY, bx, by, bw, bh);
        int color = canSell
                ? (hovered ? PageTheme.BTN_GOOD_HOVER : PageTheme.BTN_GOOD)
                : 0xFF333340;
        drawRect(bx, by, bx + bw, by + bh, color);
        int textWidth = fontRenderer.getStringWidth("上架出售");
        text(canSell ? "§f上架出售" : "§8上架出售", bx + (bw - textWidth) / 2, by + 6,
                canSell ? 0xFFFFFF : 0xFF666666);
    }

    private void drawMy(int mouseX, int mouseY) {
        List<TradeItem> mine = ownListings();
        int start = Math.max(0, scroll / ITEM_H);
        int end = Math.min(mine.size(), start + lh / ITEM_H + 2);
        beginClip(lx, ly, lw, lh);
        for (int i = start; i < end; i++) {
            int iy = ly + i * ITEM_H - scroll;
            if (iy + ITEM_H > ly && iy < ly + lh) {
                drawMyCard(mine.get(i), lx, iy, lw, mouseX, mouseY);
            }
        }
        endClip();
        if (mine.isEmpty()) {
            drawEmpty("你还没有上架任何物品");
        }
    }

    private void drawMyCard(TradeItem item, int x, int y, int w, int mouseX, int mouseY) {
        boolean expired = item.isExpired();
        boolean hovered = in(mouseX, mouseY, x, y, w - 52, ITEM_H);
        int background = expired
                ? (hovered ? 0xFF4A2A2A : 0xFF3A2222)
                : (hovered ? PageTheme.ITEM_HOVER : PageTheme.ITEM_BG);
        drawRect(x, y, x + w - 52, y + ITEM_H, background);

        drawRect(x + 7, y + 7, x + 39, y + 39, 0x18000000);
        drawItem(item.itemStack, x + 11, y + 11);
        int textX = x + 44;
        drawClipped(item.itemStack.getDisplayName(), textX, y + 7, 0xFFFFFF);
        drawClipped(String.format("§7单价 §e%1$s", f(item.pricePerUnit)), textX, y + 20,
                PageTheme.TEXT_DIM);
        drawClipped("§7×" + item.quantity, textX, y + 32, PageTheme.TEXT_DIM);
        drawClipped(expired ? "§c已过期" : "§7" + item.getRemainingTimeString(),
                x + w - 130, y + 20, expired ? 0xFFFF5555 : PageTheme.TEXT_FAINT);

        int buttonX = x + w - 48;
        int buttonY = y + 11;
        int buttonW = 44;
        int buttonH = 20;
        boolean buttonHovered = in(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH);
        int color = expired
                ? (buttonHovered ? PageTheme.BTN_WARN_HOVER : PageTheme.BTN_WARN)
                : (buttonHovered ? PageTheme.BTN_ALT_HOVER : PageTheme.BTN_ALT);
        drawRect(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH, color);
        String label = expired ? "取回" : "下架";
        text(label, buttonX + (buttonW - fontRenderer.getStringWidth(label)) / 2, buttonY + 5, 0xFFFFFF);

        drawRect(x + 2, y + ITEM_H - 1, x + w - 2, y + ITEM_H, PageTheme.ITEM_DIVIDER);
    }

    private void drawEmpty(String message) {
        int textWidth = fontRenderer.getStringWidth(message);
        text("§7" + message, lx + (lw - textWidth) / 2, ly + lh / 2 - 8, PageTheme.TEXT_FAINT);
    }

    // ---------------------------------------------------------------- 操作面板

    private int actionRowY() {
        return ay + PageTheme.GAP;
    }

    private int actionBtnY() {
        return ay + ACTION_H - PageTheme.GAP - ACTION_BTN_H;
    }

    private void drawAction(int mouseX, int mouseY) {
        int panelWidth = Math.min(PageTheme.SIDE_COL_W, winWidth - PageTheme.PAD * 2);
        drawRect(ax, ay, ax + panelWidth, ay + ACTION_H, PageTheme.ACTION_BG);
        drawRect(ax, ay, ax + panelWidth, ay + 1, PageTheme.SEPARATOR);

        int buttonHeight = ACTION_BTN_H;
        int buttonY = actionBtnY();
        int rowY = actionRowY();

        text("§7数量", ax + PageTheme.GAP, rowY + 2, PageTheme.TEXT_DIM);

        int buttonWidth = Math.max(40, panelWidth - PageTheme.GAP * 2);
        int buttonX = ax + PageTheme.GAP;
        actionHover = in(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

        boolean sellMode = currentTab == Tab.BUY_ORDERS;
        int color;
        if (sellMode) {
            color = actionHover ? PageTheme.BTN_SELL_HOVER : PageTheme.BTN_SELL;
        } else {
            color = actionHover ? PageTheme.BTN_PRIMARY_HOVER : PageTheme.BTN_PRIMARY;
        }
        drawRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, color);
        String label = sellMode ? "出售给系统" : "购买";
        text(label, buttonX + (buttonWidth - fontRenderer.getStringWidth(label)) / 2,
                buttonY + 5, 0xFFFFFF);

        long total = totalPrice();
        if (total > 0L) {
            text(String.format("§7合计 §e%s", f(total)), ax + PageTheme.GAP, rowY + 14,
                    PageTheme.TEXT_DIM);
        }
    }

    private long totalPrice() {
        int quantity = parseInt(text(qtyField));
        if (quantity <= 0) {
            return 0L;
        }
        if (currentTab == Tab.SYSTEM && selectedSystem != null) {
            return selectedSystem.price * quantity;
        }
        if (currentTab == Tab.BROWSE && selectedListing != null) {
            return selectedListing.pricePerUnit * quantity;
        }
        return 0L;
    }

    // ---------------------------------------------------------------- 输入框

    private void drawFields() {
        drawFieldBorder(searchField);
        searchField.drawTextBox();
        if (currentTab == Tab.SYSTEM && selectedSystem != null) {
            drawFieldBorder(qtyField);
            qtyField.drawTextBox();
        }
        if (currentTab == Tab.BROWSE && selectedListing != null) {
            drawFieldBorder(qtyField);
            qtyField.drawTextBox();
        }
        if (currentTab == Tab.BUY_ORDERS && selectedOrder != null) {
            drawFieldBorder(qtyField);
            qtyField.drawTextBox();
        }
        if (currentTab == Tab.SELL) {
            drawFieldBorder(priceField);
            priceField.drawTextBox();
            drawFieldBorder(sellQtyField);
            sellQtyField.drawTextBox();
        }
    }

    private void drawFieldBorder(GuiTextField field) {
        int pad = 1;
        int color = field.isFocused() ? PageTheme.FIELD_FOCUS : PageTheme.FIELD_BORDER;
        drawRect(field.x - pad, field.y - pad, field.x + field.width + pad,
                field.y + field.height + pad, PageTheme.FIELD_BG);
        drawRect(field.x - pad, field.y - pad, field.x + field.width + pad, field.y - pad + 1, color);
        drawRect(field.x - pad, field.y + field.height + pad, field.x + field.width + pad,
                field.y + field.height + pad + 1, color);
        drawRect(field.x - pad, field.y - pad, field.x - pad + 1,
                field.y + field.height + pad, color);
        drawRect(field.x + field.width + pad, field.y - pad, field.x + field.width + pad + 1,
                field.y + field.height + pad, color);
    }

    private void drawClipped(String value, int x, int y, int color) {
        if (y + fontRenderer.FONT_HEIGHT > ly && y < ly + lh) {
            fontRenderer.drawString(value, x, y, color);
        }
    }

    // ================================================================ 鼠标

    @Override
    protected boolean mousePageClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (tabClick(mouseX, mouseY)) {
            return true;
        }
        if (fieldClick(mouseX, mouseY)) {
            return true;
        }
        return contentClick(mouseX, mouseY);
    }

    private boolean tabClick(int mouseX, int mouseY) {
        for (TabBtn tab : tabs) {
            if (tab.over(mouseX, mouseY)) {
                if (currentTab != tab.tab) {
                    switchTo(tab.tab);
                }
                return true;
            }
        }
        return false;
    }

    private boolean fieldClick(int mouseX, int mouseY) {
        searchField.mouseClicked(mouseX, mouseY, 0);
        if (showsAction()) {
            qtyField.mouseClicked(mouseX, mouseY, 0);
        }
        if (currentTab == Tab.SELL) {
            priceField.mouseClicked(mouseX, mouseY, 0);
            sellQtyField.mouseClicked(mouseX, mouseY, 0);
        }
        return searchField.isFocused() || (showsAction() && qtyField.isFocused())
                || (currentTab == Tab.SELL && (priceField.isFocused() || sellQtyField.isFocused()));
    }

    private boolean contentClick(int mouseX, int mouseY) {
        switch (currentTab) {
            case SYSTEM:
                return clickSystem(mouseX, mouseY);
            case BROWSE:
                return clickBrowse(mouseX, mouseY);
            case BUY_ORDERS:
                return clickOrders(mouseX, mouseY);
            case MY_LISTINGS:
                return clickMy(mouseX, mouseY);
            case SELL:
                return clickSellButton(mouseX, mouseY);
            default:
                return false;
        }
    }

    private boolean clickSystem(int mouseX, int mouseY) {
        if (selectedSystem != null && actionHover) {
            doBuySystem();
            return true;
        }
        int categoryW = Math.min(CATEGORY_W_MAX, Math.max(CATEGORY_W_MIN, cw / 4));
        int y = ly + 4 - categoryScroll;
        for (SystemShopCatalog.Category category : ShopClientStore.systemShop().categories()) {
            if (in(mouseX, mouseY, lx + 4, y, categoryW - 8, CATEGORY_ITEM_H)) {
                currentCategory = category;
                selectedSystem = null;
                scroll = 0;
                rebuildSystemEntries();
                applyLayout();
                return true;
            }
            y += CATEGORY_ITEM_H + 3;
        }

        int itemX = lx + categoryW + PageTheme.GAP;
        int itemW = Math.max(40, lx + lw - itemX);
        int index = systemEntryAt(mouseX, mouseY, itemX, itemW);
        if (index >= 0 && index < systemEntries.size()) {
            selectedSystem = systemEntries.get(index);
            qtyField.setText("1");
            applyLayout();
            return true;
        }
        return dragBegin(mouseX, mouseY);
    }

    private int systemEntryAt(int mouseX, int mouseY, int itemX, int itemW) {
        if (!in(mouseX, mouseY, itemX, ly, itemW, lh)) {
            return -1;
        }
        int index = (mouseY - ly + scroll) / ITEM_H;
        return index >= 0 && index < systemEntries.size() ? index : -1;
    }

    private boolean clickBrowse(int mouseX, int mouseY) {
        if (selectedListing != null && actionHover) {
            doBuy();
            return true;
        }
        if (hoverIdx != null && hoverIdx < listings.size()) {
            selectedListing = listings.get(hoverIdx);
            qtyField.setText("1");
            applyLayout();
            return true;
        }
        return dragBegin(mouseX, mouseY);
    }

    private boolean clickOrders(int mouseX, int mouseY) {
        if (selectedOrder != null && actionHover) {
            doSellTo();
            return true;
        }
        if (hoverOrderIdx != null && hoverOrderIdx < buyOrders.size()) {
            selectedOrder = buyOrders.get(hoverOrderIdx);
            qtyField.setText("1");
            applyLayout();
            return true;
        }
        return dragBegin(mouseX, mouseY);
    }

    private boolean clickMy(int mouseX, int mouseY) {
        List<TradeItem> mine = ownListings();
        int start = Math.max(0, scroll / ITEM_H);
        int end = Math.min(mine.size(), start + lh / ITEM_H + 2);
        for (int i = start; i < end; i++) {
            TradeItem item = mine.get(i);
            int iy = ly + i * ITEM_H - scroll;
            if (in(mouseX, mouseY, lx, iy, lw, ITEM_H)) {
                int buttonX = lx + lw - 48;
                int buttonY = iy + 11;
                if (in(mouseX, mouseY, buttonX, buttonY, 44, 20)) {
                    doRemove(item.id);
                    return true;
                }
            }
        }
        return dragBegin(mouseX, mouseY);
    }

    private boolean clickSellButton(int mouseX, int mouseY) {
        int iy = sellCardY();
        if (in(mouseX, mouseY, lx, iy + 44 + 56, 110, 24)) {
            doSell();
            return true;
        }
        return false;
    }

    private boolean dragBegin(int mouseX, int mouseY) {
        if (maxScroll <= 0) {
            return false;
        }
        int barX = lx + lw + PageTheme.GAP;
        int thumbHeight = thumbHeight(lh, maxScroll);
        int thumbY = ly + scroll * (lh - thumbHeight) / maxScroll;
        if (in(mouseX, mouseY, barX - 2, thumbY, PageTheme.SCROLL_W + 4, thumbHeight)) {
            dragScroll = true;
            dragY = mouseY;
            dragStartScroll = scroll;
            return true;
        }
        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragScroll && maxScroll > 0) {
            int thumbHeight = thumbHeight(lh, maxScroll);
            scroll = clamp(0, maxScroll,
                    dragStartScroll + (mouseY - dragY) * maxScroll / Math.max(1, lh - thumbHeight));
        }
    }

    @Override
    protected boolean mousePageReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && dragScroll) {
            dragScroll = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean mousePageScrolled(int mouseX, int mouseY, int wheel) {
        if (currentTab == Tab.SYSTEM && maxCategoryScroll > 0) {
            int categoryW = Math.min(CATEGORY_W_MAX, Math.max(CATEGORY_W_MIN, cw / 4));
            if (in(mouseX, mouseY, lx, ly, categoryW, lh)) {
                categoryScroll = clamp(0, maxCategoryScroll,
                        categoryScroll - Integer.signum(wheel) * 18);
                return true;
            }
        }
        if (maxScroll <= 0 || !in(mouseX, mouseY, lx, ly, lw, lh)) {
            return false;
        }
        scroll = clamp(0, maxScroll, scroll - Integer.signum(wheel) * 18);
        return true;
    }

    // ================================================================ 键盘

    @Override
    protected boolean keyPageTyped(char typedChar, int keyCode) {
        if (searchField.isFocused()) {
            searchField.textboxKeyTyped(typedChar, keyCode);
            reload();
            rebuildSystemEntries();
            rescroll();
            return true;
        }
        if (showsAction() && qtyField.isFocused()) {
            qtyField.textboxKeyTyped(typedChar, keyCode);
            return true;
        }
        if (currentTab == Tab.SELL && priceField.isFocused()) {
            priceField.textboxKeyTyped(typedChar, keyCode);
            return true;
        }
        if (currentTab == Tab.SELL && sellQtyField.isFocused()) {
            sellQtyField.textboxKeyTyped(typedChar, keyCode);
            return true;
        }
        if (maxScroll > 0) {
            switch (keyCode) {
                case Keyboard.KEY_UP:
                    scroll = clamp(0, maxScroll, scroll - 18);
                    return true;
                case Keyboard.KEY_DOWN:
                    scroll = clamp(0, maxScroll, scroll + 18);
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    // ================================================================ 页签切换

    private void switchTo(Tab tab) {
        currentTab = tab;
        scroll = 0;
        categoryScroll = 0;
        selectedListing = null;
        selectedOrder = null;
        if (tab != Tab.SYSTEM) {
            selectedSystem = null;
        }
        reload();
        applyLayout();
        initFields();
        requestCurrentTab(0);
    }

    /** 服务端数据到达后刷新界面。 */
    public void onShopDataUpdated() {
        if (mc.currentScreen != this) {
            return;
        }
        reload();
        // 选中的条目可能已经被买走/下架：留在列表里会导致操作面板继续指向不存在的商品
        if (selectedListing != null && !listings.contains(selectedListing)) {
            selectedListing = null;
        }
        if (selectedOrder != null && !buyOrders.contains(selectedOrder)) {
            selectedOrder = null;
        }
        applyLayout();
        if (ShopClientStore.hasPendingPage() && currentTab == Tab.BROWSE) {
            requestCurrentTab(listings.size());
        }
    }

    @Override
    protected void onPageClosed() {
        dragScroll = false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        if (qtyField != null) {
            qtyField.updateCursorCounter();
        }
        if (priceField != null) {
            priceField.updateCursorCounter();
        }
        if (sellQtyField != null) {
            sellQtyField.updateCursorCounter();
        }
    }

    // ================================================================ 业务操作

    private String sellerName(TradeItem listing) {
        return new UUID(0L, 0L).equals(listing.sellerUUID) ? "§6§l系统" : listing.sellerName;
    }

    private void doBuy() {
        if (selectedListing == null) {
            return;
        }
        int quantity = parseQty();
        if (quantity <= 0 || quantity > selectedListing.quantity) {
            message("§c数量无效");
            return;
        }
        if (selectedListing.pricePerUnit * quantity > playerMoney) {
            message("§c货币不足");
            return;
        }
        PacketHandler.sendToServer(new MessageBuyItem(selectedListing.id, quantity));
    }

    private void doBuySystem() {
        if (selectedSystem == null) {
            return;
        }
        int quantity = parseQty();
        if (quantity <= 0) {
            message("§c数量无效");
            return;
        }
        if (selectedSystem.stock >= 0 && quantity > selectedSystem.stock) {
            message("§c库存不足");
            return;
        }
        if (selectedSystem.price * quantity > playerMoney) {
            message("§c货币不足");
            return;
        }
        PacketHandler.sendToServer(new MessageSystemBuy(selectedSystem.categoryId, selectedSystem.id, quantity));
    }

    private void doSellTo() {
        if (selectedOrder == null) {
            return;
        }
        int quantity = parseQty();
        if (quantity <= 0 || quantity > selectedOrder.getRemaining()) {
            message("§c数量无效");
            return;
        }
        PacketHandler.sendToServer(new MessageSellToOrder(selectedOrder.id, quantity));
    }

    private void doSell() {
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            message("§c请手持要出售的物品");
            return;
        }
        long price = parseLong(text(priceField));
        if (price <= 0L) {
            message("§c单价必须大于0");
            return;
        }
        int quantity = parseInt(text(sellQtyField));
        if (quantity <= 0) {
            message("§c数量必须大于0");
            return;
        }
        if (quantity > held.getCount()) {
            message(String.format("§c库存不足，最多%s个", held.getCount()));
            return;
        }
        PacketHandler.sendToServer(new MessageSellItem(held.copy(), price, quantity));
    }

    private void doRemove(UUID id) {
        PacketHandler.sendToServer(new MessageRemoveListing(id));
    }

    private int parseQty() {
        return parseInt(text(qtyField));
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception invalid) {
            return -1;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception invalid) {
            return -1L;
        }
    }

    private void message(String text) {
        player.sendMessage(new TextComponentString(text));
    }

    /** 自己的上架（含已过期，供取回）。 */
    private List<TradeItem> ownListings() {
        UUID self = player.getUniqueID();
        List<TradeItem> mine = new ArrayList<>();
        for (TradeItem item : ShopClientStore.ownListings()) {
            if (self.equals(item.sellerUUID)) {
                mine.add(item);
            }
        }
        return mine;
    }

    // ================================================================ 内部类型

    private enum Tab {
        SYSTEM("系统商店"),
        BROWSE("全球市场"),
        BUY_ORDERS("系统收购"),
        SELL("出售物品"),
        MY_LISTINGS("我的商品");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final class TabBtn {
        final Tab tab;
        final int x;
        final int y;
        final int width;
        final int height;

        TabBtn(Tab tab, int x, int y, int width, int height) {
            this.tab = tab;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean over(int mouseX, int mouseY) {
            return in(mouseX, mouseY, x, y, width, height);
        }

        void draw(int mouseX, int mouseY) {
            boolean hovered = over(mouseX, mouseY);
            boolean active = currentTab == tab;
            drawSideTab(x, y, width, height, tab.label, active, hovered,
                    PageTheme.accent(tab.ordinal()));
        }
    }
}
