package meowmel.quantumhue.createworld.api.tab;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.createworld.mixin.IGuiCreateWorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.EnumDifficulty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签页管理器——持有全部标签页、当前标签页与原版字段访问器。
 * <p>Manages all tabs, the current tab and the vanilla field accessor.</p>
 * 构造时冻结 {@link TabRegistry} 并实例化全部已注册标签页，按 tabId 升序默认选中第一个。
 */
public class TabManager {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:TabManager");

    private final Map<Integer, Tab> tabs = new HashMap<>();
    private final List<GuiButton> buttonList;
    private final GuiCreateWorld parent;
    private final IGuiCreateWorldAccess access;
    private int currentTabId = 100;
    private Tab currentTab;
    private EnumDifficulty difficulty = EnumDifficulty.NORMAL;
    private boolean difficultyLocked = false;

    public TabManager(GuiCreateWorld parent, List<GuiButton> buttonList, int width, int height) {
        this.parent = parent;
        this.buttonList = buttonList;
        this.access = (IGuiCreateWorldAccess) parent;
        EnumDifficulty d = Minecraft.getMinecraft().gameSettings.difficulty;
        this.difficulty = d != null ? d : EnumDifficulty.NORMAL;
        if (!TabRegistry.isFrozen()) {
            TabRegistry.freeze();
        }
        for (TabRegistry.TabEntry entry : TabRegistry.getEntries()) {
            Tab tab = entry.factory.get();
            this.registerTab(tab);
        }
        for (Tab tab : this.tabs.values()) {
            tab.initGui(this, width, height);
        }
        List<Integer> sortedIds = this.getSortedTabIds();
        if (!sortedIds.isEmpty()) {
            this.currentTabId = sortedIds.get(0);
        }
        this.switchToTab(this.currentTabId);
    }

    /** 世界名称为空时的创建名（配置允许空名则用默认名） */
    public String getWorldNameForCreation() {
        String current = this.access.createWorldUI$getWorldName();
        String trimmedName = current != null ? current.trim() : "";
        if (trimmedName.isEmpty() && !QuantumHueConfig.createWorld.disableCreateButtonWhenWNIsBlank) {
            return I18n.format("selectWorld.newWorld");
        }
        return trimmedName;
    }

    public void addButton(GuiButton button) {
        if (!this.buttonList.contains(button)) {
            this.buttonList.add(button);
        }
    }

    public void registerTab(Tab tab) {
        this.tabs.put(tab.getTabId(), tab);
    }

    public boolean isTabButton(int id) {
        return this.tabs.containsKey(id);
    }

    public List<Integer> getSortedTabIds() {
        ArrayList<Integer> ids = new ArrayList<>(this.tabs.keySet());
        Collections.sort(ids);
        return ids;
    }

    public void switchToTab(int tabId) {
        if (this.currentTab != null) {
            this.currentTab.setVisible(false);
        }
        this.currentTabId = tabId;
        this.currentTab = this.tabs.get(tabId);
        if (this.currentTab != null) {
            this.currentTab.setVisible(true);
        }
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.currentTab != null) {
            this.currentTab.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    public void actionPerformed(GuiButton button) {
        if (this.isTabButton(button.id)) {
            this.switchToTab(button.id);
            return;
        }
        if (this.currentTab != null) {
            this.currentTab.actionPerformed(button);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.currentTab != null) {
            this.currentTab.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (this.currentTab != null) {
            this.currentTab.keyTyped(typedChar, keyCode);
        }
    }

    /** 窗口缩放时重新初始化所有标签页并恢复当前页 */
    public void reinitializeTabs(int width, int height) {
        int savedTabId = this.currentTabId;
        for (Tab tab : this.tabs.values()) {
            tab.initGui(this, width, height);
        }
        this.switchToTab(savedTabId);
    }

    // ===== 原版字段访问（经 accessor 转发）/ Vanilla field access via accessor =====

    public String getWorldName() {
        return this.access.createWorldUI$getWorldName();
    }

    public void setWorldName(String worldName) {
        this.access.createWorldUI$setWorldName(worldName);
        LOGGER.debug("World name set to: {}", worldName);
    }

    public String getGameMode() {
        return this.access.createWorldUI$getGameMode();
    }

    public void setGameMode(String gameMode) {
        this.access.createWorldUI$setGameMode(gameMode);
        LOGGER.debug("Game mode set to: {}", gameMode);
    }

    public String getSeed() {
        return this.access.createWorldUI$getSeed();
    }

    public void setSeed(String seed) {
        this.access.createWorldUI$setSeed(seed);
        LOGGER.debug("Seed set to: {}", seed);
    }

    public int getWorldTypeIndex() {
        return this.access.createWorldUI$getWorldTypeIndex();
    }

    public void setWorldTypeIndex(int index) {
        this.access.createWorldUI$setWorldTypeIndex(index);
        LOGGER.debug("World type index set to: {}", index);
    }

    public boolean getGenerateStructures() {
        return this.access.createWorldUI$getGenerateStructures();
    }

    public void setGenerateStructures(boolean value) {
        this.access.createWorldUI$setGenerateStructures(value);
        LOGGER.debug("Generate structures set to: {}", value);
    }

    public boolean getBonusChest() {
        return this.access.createWorldUI$getBonusChest();
    }

    public void setBonusChest(boolean value) {
        this.access.createWorldUI$setBonusChest(value);
        LOGGER.debug("Bonus chest set to: {}", value);
    }

    public boolean getAllowCheats() {
        return this.access.createWorldUI$getAllowCheats();
    }

    public void setAllowCheats(boolean value) {
        this.access.createWorldUI$setAllowCheats(value);
        LOGGER.debug("Allow cheats set to: {}", value);
    }

    public boolean getHardcore() {
        return this.access.createWorldUI$getHardcore();
    }

    public void setHardcore(boolean value) {
        this.access.createWorldUI$setHardcore(value);
        LOGGER.debug("Hardcore set to: {}", value);
    }

    public EnumDifficulty getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(EnumDifficulty difficulty) {
        this.difficulty = difficulty;
        LOGGER.debug("Difficulty set to: {}", difficulty);
        Minecraft.getMinecraft().gameSettings.difficulty = difficulty;
        Minecraft.getMinecraft().gameSettings.saveOptions();
    }

    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    public void setDifficultyLocked(boolean locked) {
        this.difficultyLocked = locked;
        LOGGER.debug("Difficulty locked set to: {}", locked);
    }

    /** 某个 tabId 在排序后的序号（用于绘制选中态高亮）/ index of a tab id in sorted order */
    public int getTabIndex(int tabId) {
        List<Integer> sortedIds = this.getSortedTabIds();
        return sortedIds.indexOf(tabId);
    }

    public GuiCreateWorld getParent() {
        return this.parent;
    }

    public int getCurrentTabId() {
        return this.currentTabId;
    }

    public int getTabCount() {
        return this.tabs.size();
    }

    public Map<Integer, Tab> getAllTabs() {
        return this.tabs;
    }
}
