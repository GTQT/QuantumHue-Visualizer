package meowmel.quantumhue.createworld.mixin;

import com.meowmel.quantumhue.Tags;
import meowmel.quantumhue.api.utils.ClientHelper;
import meowmel.quantumhue.createworld.api.ContentPanelRenderer;
import meowmel.quantumhue.createworld.api.DifficultyApplier;
import meowmel.quantumhue.createworld.api.GuiCyclableButton;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleApplier;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleMonitorNSetter;
import meowmel.quantumhue.createworld.api.tab.Tab;
import meowmel.quantumhue.createworld.api.tab.TabManager;
import meowmel.quantumhue.createworld.api.tab.TabState;
import meowmel.quantumhue.createworld.gamerule.GuiScreenGameRuleEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 Mixin 把原版创建世界界面改造成标签页式布局。
 * <p>Transforms the vanilla world creation screen into a tabbed layout.</p>
 * 策略：initGui 保留创建(0)/取消(1)按钮后清空按钮列表；drawScreen 完全接管重绘；
 * keyTyped/actionPerformed 拦截并转发给 TabManager；mouseClicked 尾注转发。
 * 私有字段通过 {@link IGuiCreateWorldAccess} 访问；本类只管注入逻辑。
 *
 * <p>注：原版创建世界时读取的是 worldNameField/worldSeedField（GuiTextField）的文本，
 * 而标签页同步的是 worldName/worldSeed 字段——因此点击创建按钮时必须把 UI 值同步回
 * 文本框并重算目录名，否则创建出的世界名/种子永远是默认值。</p>
 */
@Mixin(GuiCreateWorld.class)
public abstract class MixinModernCreateWorld extends GuiScreen {

    // ===== 原版字段（MCP 名）/ vanilla fields (MCP names) =====
    @Shadow
    private GuiScreen parentScreen;
    @Shadow
    private boolean hardCoreMode;
    @Shadow
    private String worldName;
    @Shadow
    private String gameMode;
    @Shadow
    private String worldSeed;
    @Shadow
    private boolean generateStructuresEnabled;
    @Shadow
    private boolean bonusChestEnabled;
    @Shadow
    private boolean allowCheats;
    @Shadow
    private int selectedIndex;
    @Shadow
    private GuiTextField worldNameField;
    @Shadow
    private GuiTextField worldSeedField;
    @Shadow
    private String saveDirName;

    // ===== 新添加的字段 / new fields =====
    @Unique
    private TabManager modernWorldCreatingUI$tabManager;
    @Unique
    private static final ResourceLocation TABS_TEXTURE =
            new ResourceLocation(Tags.MOD_ID, "textures/gui/tabs.png");
    @Unique
    private static final int TAB_HEIGHT = 24;
    @Unique
    private final Map<Integer, String> modernWorldCreatingUI$hoverTexts = new HashMap<>();
    @Unique
    private boolean modernWorldCreatingUI$isInitialized = false;
    @Unique
    private int modernWorldCreatingUI$tabButtonWidth = 130;
    @Unique
    private static final Logger modernWorldCreatingUI$logger = LogManager.getLogger("QuantumHue:MixinGuiCreateWorld");

    /**
     * 重算原版世界目录名（private 方法，通过 @Invoker 调用）。
     * 原版 keyTyped 被接管后不会自动重算，创建前必须同步。
     */
    @Invoker("calcSaveDirName")
    protected abstract void invokeCalcSaveDirName();

    // ===== 初始化 / init =====

    @Inject(method = "initGui", at = @At("HEAD"))
    private void onInitGuiHead(CallbackInfo ci) {
        this.modernWorldCreatingUI$ensureFieldsNotNull();
        this.modernWorldCreatingUI$isInitialized = false;
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void onInitGuiTail(CallbackInfo ci) {
        modernWorldCreatingUI$logger.info("Initializing GUI");

        // 保留创建(0)和取消(1)按钮，其余交给标签页系统 / keep create/cancel, drop the rest
        List<GuiButton> essentialButtons = new ArrayList<>();
        for (GuiButton button : this.buttonList) {
            if (button.id == 0 || button.id == 1) {
                essentialButtons.add(button);
            }
        }
        this.buttonList.clear();
        this.buttonList.addAll(essentialButtons);

        if (this.modernWorldCreatingUI$tabManager != null) {
            // 窗口缩放导致的重新初始化 / re-init after resize
            this.modernWorldCreatingUI$tabManager.reinitializeTabs(this.width, this.height);
            modernWorldCreatingUI$logger.info("Reinitialized tabs after resize");
        } else {
            this.modernWorldCreatingUI$tabManager = new TabManager((GuiCreateWorld) (Object) this, this.buttonList, this.width, this.height);
        }

        this.modernWorldCreatingUI$createTabButtons();
        this.modernWorldCreatingUI$repositionActionButtons();
        this.modernWorldCreatingUI$initHoverTexts();
        this.modernWorldCreatingUI$isInitialized = true;
    }

    @Unique
    private void modernWorldCreatingUI$initHoverTexts() {
        this.modernWorldCreatingUI$hoverTexts.put(2, I18n.format("quantumhue.createworld.hover.gameMode"));
        this.modernWorldCreatingUI$hoverTexts.put(4, I18n.format("quantumhue.createworld.hover.generateStructures"));
        this.modernWorldCreatingUI$hoverTexts.put(5, I18n.format("quantumhue.createworld.hover.worldType"));
        this.modernWorldCreatingUI$hoverTexts.put(6, I18n.format("quantumhue.createworld.hover.allowCheats"));
        this.modernWorldCreatingUI$hoverTexts.put(7, I18n.format("quantumhue.createworld.hover.bonusChest"));
        this.modernWorldCreatingUI$hoverTexts.put(8, I18n.format("quantumhue.createworld.hover.customize"));
        this.modernWorldCreatingUI$hoverTexts.put(9, I18n.format("quantumhue.createworld.hover.difficulty"));
        this.modernWorldCreatingUI$hoverTexts.put(200, I18n.format("quantumhue.createworld.hover.gameRuleEditor"));
    }

    @Unique
    private void modernWorldCreatingUI$ensureFieldsNotNull() {
        this.worldName = I18n.format("selectWorld.newWorld");
        modernWorldCreatingUI$logger.info("Set default world name: {}", this.worldName);
        if (this.worldSeed == null) {
            this.worldSeed = "";
        }
        if (this.gameMode == null) {
            this.gameMode = "survival";
        }
        if (WorldType.WORLD_TYPES == null || this.selectedIndex < 0
                || this.selectedIndex >= WorldType.WORLD_TYPES.length
                || WorldType.WORLD_TYPES[this.selectedIndex] == null) {
            this.selectedIndex = 0;
        }
    }

    /** 创建/取消按钮移到底部脚注位置 / reposition create/cancel to the footer */
    @Unique
    private void modernWorldCreatingUI$repositionActionButtons() {
        GuiButton createButton = this.modernWorldCreatingUI$getButtonById(0);
        GuiButton cancelButton = this.modernWorldCreatingUI$getButtonById(1);
        if (createButton != null) {
            createButton.x = this.width / 2 - 155;
            createButton.y = this.height - 28;
            createButton.width = 150;
            createButton.height = 20;
            createButton.visible = true;
        }
        if (cancelButton != null) {
            cancelButton.x = this.width / 2 + 5;
            cancelButton.y = this.height - 28;
            cancelButton.width = 150;
            cancelButton.height = 20;
            cancelButton.visible = true;
        }
    }

    /**
     * 为每个标签页创建顶栏按钮（id = tabId），覆写 drawButton 用 tabs.png 画四态外观。
     * <p>Create top-bar buttons for each tab; drawButton renders 4 states from tabs.png.</p>
     */
    @Unique
    @SuppressWarnings("unchecked")
    private void modernWorldCreatingUI$createTabButtons() {
        int tabCount = this.modernWorldCreatingUI$tabManager != null ? this.modernWorldCreatingUI$tabManager.getTabCount() : 3;
        if (tabCount <= 0) {
            tabCount = 3;
        }
        this.modernWorldCreatingUI$tabButtonWidth = Math.min(130, this.width / tabCount);
        int totalWidth = this.modernWorldCreatingUI$tabButtonWidth * tabCount;
        int startX = this.width / 2 - totalWidth / 2;

        if (this.modernWorldCreatingUI$tabManager != null) {
            List<Integer> sortedIds = this.modernWorldCreatingUI$tabManager.getSortedTabIds();
            for (int i = 0; i < sortedIds.size(); i++) {
                int tabId = sortedIds.get(i);
                Tab tab = this.modernWorldCreatingUI$tabManager.getAllTabs().get(tabId);
                String tabName = tab != null ? tab.getTabName() : "";
                int xPos = startX + i * this.modernWorldCreatingUI$tabButtonWidth;
                GuiButton tabButton = new GuiButton(tabId, xPos, 0, this.modernWorldCreatingUI$tabButtonWidth, TAB_HEIGHT, tabName) {
                    @Override
                    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
                        if (!this.visible) {
                            return;
                        }
                        mc.getTextureManager().bindTexture(TABS_TEXTURE);
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                        boolean isHovered = mouseX >= this.x && mouseY >= this.y
                                && mouseX < this.x + this.width && mouseY < this.y + this.height;
                        boolean isSelected = MixinModernCreateWorld.this.modernWorldCreatingUI$tabManager != null
                                && MixinModernCreateWorld.this.modernWorldCreatingUI$tabManager.getCurrentTabId() == this.id;
                        TabState state = isSelected
                                ? (isHovered ? TabState.SELECTED_HOVER : TabState.SELECTED)
                                : (isHovered ? TabState.HOVER : TabState.NORMAL);
                        this.drawTexturedModalRect(this.x, this.y, state.u, state.v, this.width, TAB_HEIGHT);
                        this.drawCenteredString(mc.fontRenderer, this.displayString,
                                this.x + this.width / 2, this.y + (this.height - 8) / 2, state.getTextColor());
                    }
                };
                tabButton.visible = true;
                this.buttonList.add(tabButton);
            }
        }
    }

    // ===== 绘制 / drawing =====

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    public void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!this.modernWorldCreatingUI$isInitialized) {
            return;
        }
        ci.cancel();

        // drawWorldBackground 会被 GuiScreenMixin 拦截，绘制本模组的旋转全景背景；
        // 不再叠加 options_background_dark 顶部条，避免遮住全景。
        this.drawWorldBackground(0);

        int lineY = 22;
        int currentTabId = this.modernWorldCreatingUI$tabManager != null ? this.modernWorldCreatingUI$tabManager.getCurrentTabId() : -1;
        int tabCount = this.modernWorldCreatingUI$tabManager != null ? this.modernWorldCreatingUI$tabManager.getTabCount() : 3;
        if (tabCount <= 0) {
            tabCount = 3;
        }
        int actualTabWidth = Math.min(130, this.width / tabCount);
        int totalWidth = actualTabWidth * tabCount;
        int startX = this.width / 2 - totalWidth / 2;
        int tabIndex = this.modernWorldCreatingUI$tabManager != null ? this.modernWorldCreatingUI$tabManager.getTabIndex(currentTabId) : -1;

        // 内容面板背景：与选择存档页面一致的灰色滤镜（menu_list_background 瓦片平铺）
        int panelBottom = this.height - 35;
        int panelTop = 24;
        if (panelBottom > panelTop) {
            ClientHelper.renderListBackground(this.mc, 0, panelTop, this.width, panelBottom, 0.0F);
        }
        // 顶部分隔线在选中 tab 两侧断开 / header separator split around the selected tab
        if (tabIndex >= 0 && tabIndex < tabCount) {
            int selectedTabX = startX + tabIndex * actualTabWidth;
            int selectedTabEnd = selectedTabX + actualTabWidth;
            if (selectedTabX > 0) {
                ContentPanelRenderer.drawHeaderSeparator(0, lineY, selectedTabX);
            }
            if (selectedTabEnd < this.width) {
                ContentPanelRenderer.drawHeaderSeparator(selectedTabEnd, lineY, this.width - selectedTabEnd);
            }
        } else {
            ContentPanelRenderer.drawHeaderSeparator(0, lineY, this.width);
        }
        ContentPanelRenderer.drawFooterSeparator(0, this.height - 35, this.width);

        // 当前标签页内容 / current tab content
        if (this.modernWorldCreatingUI$tabManager != null) {
            this.modernWorldCreatingUI$tabManager.drawScreen(mouseX, mouseY, partialTicks);
        }

        // 按钮统一绘制（tab 按钮 + 创建/取消 + tab 内按钮都在 buttonList）/ draw all buttons
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) obj;
            if (!button.visible) {
                continue;
            }
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }

        this.modernWorldCreatingUI$drawHoverText(mouseX, mouseY);
    }

    // ===== 按钮点击 / button clicks =====

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (!this.modernWorldCreatingUI$isInitialized || button == null) {
            return;
        }
        if (button.id == 0) {
            if (this.modernWorldCreatingUI$tabManager != null) {
                DifficultyApplier.setDifficultyLocked(this.modernWorldCreatingUI$tabManager.isDifficultyLocked());
                // 修复：原版创建时读取的是 GuiTextField 文本，必须把 UI 值同步回去并重算目录名
                // fix: vanilla reads the GuiTextFields on create; sync UI values back
                this.worldNameField.setText(this.modernWorldCreatingUI$tabManager.getWorldName());
                this.worldSeedField.setText(this.modernWorldCreatingUI$tabManager.getSeed());
                this.invokeCalcSaveDirName();
            }
            return; // 放行原版创建流程 / let vanilla create the world
        }
        if (this.modernWorldCreatingUI$tabManager != null) {
            this.modernWorldCreatingUI$tabManager.actionPerformed(button);
            if (this.modernWorldCreatingUI$tabManager.isTabButton(button.id)) {
                ci.cancel();
                return;
            }
        }
        if (button.id == 200) {
            // 打开游戏规则编辑器 / open the game rule editor
            Map<String, String> pending = GameRuleApplier.getPendingGameRules();
            if (pending == null) {
                pending = new HashMap<>();
            }
            Map<String, String> cleanPending = new HashMap<>();
            for (Map.Entry<String, String> entry : pending.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                cleanPending.put(entry.getKey(), entry.getValue());
            }
            // 游戏内打开时补入当前世界规则 / fill in current-world rules when in-game
            try {
                WorldClient clientWorld = this.mc.world;
                if (clientWorld != null) {
                    Map<String, Object> opt = GameRuleMonitorNSetter.getOptimalGameruleValues(clientWorld);
                    if (opt != null && !opt.isEmpty()) {
                        for (Map.Entry<String, Object> e : opt.entrySet()) {
                            if (e.getKey() == null || e.getValue() == null) {
                                continue;
                            }
                            cleanPending.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                    }
                }
            } catch (Throwable t) {
                modernWorldCreatingUI$logger.error("On opening GameRuleEditor, an error occurred: {}", t.getMessage());
            }
            this.mc.displayGuiScreen(new GuiScreenGameRuleEditor((GuiCreateWorld) (Object) this, cleanPending));
            ci.cancel();
            return;
        }
        if (button.id >= 2 && button.id <= 9) {
            ci.cancel();
        }
    }

    // ===== 键盘输入 / keyboard =====

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void modernWorldCreatingUI$onKeyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (!this.modernWorldCreatingUI$isInitialized) {
            return;
        }
        // Ctrl+Tab 循环切换 / Ctrl+Tab cycles tabs
        if (isCtrlKeyDown() && keyCode == 15) {
            if (this.modernWorldCreatingUI$tabManager != null) {
                Map<Integer, Tab> availableTabs = this.modernWorldCreatingUI$tabManager.getAllTabs();
                List<Integer> sortedTabIds = new ArrayList<>(availableTabs.keySet());
                Collections.sort(sortedTabIds);
                if (!sortedTabIds.isEmpty()) {
                    int currentTabId = this.modernWorldCreatingUI$tabManager.getCurrentTabId();
                    int currentIndex = sortedTabIds.indexOf(currentTabId);
                    int nextIndex = isShiftKeyDown()
                            ? (currentIndex - 1 + sortedTabIds.size()) % sortedTabIds.size()
                            : (currentIndex + 1) % sortedTabIds.size();
                    this.modernWorldCreatingUI$tabManager.switchToTab(sortedTabIds.get(nextIndex));
                }
            }
            ci.cancel();
            return;
        }
        // Ctrl+1..Ctrl+0 跳转 / Ctrl+digit jumps to a tab
        if (isCtrlKeyDown() && keyCode >= 2 && keyCode <= 11) {
            int tabNumber = keyCode == 11 ? 10 : keyCode - 1;
            if (this.modernWorldCreatingUI$tabManager != null) {
                Map<Integer, Tab> availableTabs = this.modernWorldCreatingUI$tabManager.getAllTabs();
                List<Integer> sortedTabIds = new ArrayList<>(availableTabs.keySet());
                Collections.sort(sortedTabIds);
                int targetIndex = Math.min(tabNumber - 1, sortedTabIds.size() - 1);
                if (targetIndex >= 0 && targetIndex < sortedTabIds.size()) {
                    this.modernWorldCreatingUI$tabManager.switchToTab(sortedTabIds.get(targetIndex));
                }
            }
            ci.cancel();
            return;
        }
        if (this.modernWorldCreatingUI$tabManager != null) {
            this.modernWorldCreatingUI$tabManager.keyTyped(typedChar, keyCode);
        }
        // 世界名为空时禁用创建按钮 / disable create when the name is blank
        GuiButton createButton = this.modernWorldCreatingUI$getButtonById(0);
        if (createButton != null) {
            createButton.enabled = this.modernWorldCreatingUI$tabManager != null
                    && !this.modernWorldCreatingUI$tabManager.getWorldName().trim().isEmpty();
        }
        // ESC 返回父界面（原版 keyTyped 即将被取消）/ handle ESC ourselves
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
        ci.cancel();
    }

    // ===== 鼠标点击 / mouse =====

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void modernWorldCreatingUI$onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (!this.modernWorldCreatingUI$isInitialized) {
            return;
        }
        if (this.modernWorldCreatingUI$tabManager != null) {
            this.modernWorldCreatingUI$tabManager.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    /**
     * 滚轮：悬停在 GuiCyclableButton 上时切换其值。
     * <p>Note: kept as @Override rather than @Inject because vanilla GuiCreateWorld
     * does not override handleMouseInput (inherited from GuiScreen), and Mixin
     * cannot reliably inject into inherited methods.</p>
     */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (!this.modernWorldCreatingUI$isInitialized) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (this.modernWorldCreatingUI$tabManager == null) {
            return;
        }
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiCyclableButton)) {
                continue;
            }
            GuiCyclableButton button = (GuiCyclableButton) obj;
            if (!button.visible || !button.enabled
                    || mouseX < button.x || mouseX >= button.x + button.width
                    || mouseY < button.y || mouseY >= button.y + button.height) {
                continue;
            }
            int delta = Mouse.getEventDWheel();
            if (delta != 0) {
                button.mouseScrolled(delta);
            }
        }
    }

    // ===== 悬停提示 / hover tooltips =====

    @Unique
    private void modernWorldCreatingUI$drawHoverText(int mouseX, int mouseY) {
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) obj;
            if (!button.visible || mouseX < button.x || mouseY < button.y
                    || mouseX >= button.x + button.width || mouseY >= button.y + button.height) {
                continue;
            }
            if (this.modernWorldCreatingUI$tabManager != null && this.modernWorldCreatingUI$tabManager.isTabButton(button.id)) {
                continue;
            }
            if (button.id == 0 || button.id == 1) {
                continue;
            }
            String hoverText = this.modernWorldCreatingUI$hoverTexts.get(button.id);
            if (hoverText == null || hoverText.isEmpty()) {
                continue;
            }
            this.drawHoveringText(Arrays.asList(hoverText), mouseX, mouseY);
            return;
        }
        // 游戏标签页：世界名输入框 tooltip / world-name field tooltip on the Game tab
        if (this.modernWorldCreatingUI$tabManager != null
                && this.modernWorldCreatingUI$tabManager.getCurrentTabId() == 100) {
            String worldName = this.modernWorldCreatingUI$tabManager.getWorldName();
            String hoverText = worldName == null || worldName.isEmpty()
                    ? I18n.format("quantumhue.createworld.hover.worldName.empty")
                    : I18n.format("quantumhue.createworld.hover.worldName.filled", worldName);
            int inputX = this.width / 2 - 104;
            int inputY = this.height / 5;
            if (mouseX >= inputX && mouseX <= inputX + 208 && mouseY >= inputY && mouseY <= inputY + 20) {
                this.drawHoveringText(Arrays.asList(hoverText), mouseX, mouseY);
            }
        }
    }

    @Unique
    private GuiButton modernWorldCreatingUI$getButtonById(int id) {
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) obj;
            if (button.id == id) {
                return button;
            }
        }
        return null;
    }

}
