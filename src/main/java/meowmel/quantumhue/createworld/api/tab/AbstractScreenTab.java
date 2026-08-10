package meowmel.quantumhue.createworld.api.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.EnumDifficulty;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签页抽象基类。
 * <p>Base class for tabs; forwards all state access to the {@link TabManager}
 * (which in turn reads/writes the vanilla GuiCreateWorld private fields).</p>
 * 所有状态读写都经由 TabManager 转发到原版字段访问器，tab 自身不直接碰原版私有字段。
 */
public abstract class AbstractScreenTab implements Tab {

    protected TabManager tabManager;
    protected Minecraft mc;
    protected List<GuiButton> tabButtons = new ArrayList<>();
    protected boolean visible = true;
    protected int tabId;
    protected String tabNameKey;

    public AbstractScreenTab(int tabId, String tabNameKey) {
        this.tabId = tabId;
        this.tabNameKey = tabNameKey;
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        this.tabManager = tabManager;
        this.tabButtons.clear();
    }

    @Override
    public int getTabId() {
        return this.tabId;
    }

    @Override
    public String getTabName() {
        return I18n.format(this.tabNameKey);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        for (GuiButton button : this.tabButtons) {
            button.visible = visible;
        }
    }

    /** 把按钮加入 tab 自身列表和 GuiCreateWorld 的 buttonList（由 TabManager 转发） */
    protected void addButton(GuiButton button) {
        this.tabButtons.add(button);
        this.tabManager.addButton(button);
    }

    protected String getWorldName() {
        return this.tabManager.getWorldName();
    }

    protected String getGameMode() {
        return this.tabManager.getGameMode();
    }

    protected String getSeed() {
        return this.tabManager.getSeed();
    }

    protected int getWorldTypeIndex() {
        return this.tabManager.getWorldTypeIndex();
    }

    protected boolean getGenerateStructures() {
        return this.tabManager.getGenerateStructures();
    }

    protected boolean getBonusChest() {
        return this.tabManager.getBonusChest();
    }

    protected boolean getAllowCheats() {
        return this.tabManager.getAllowCheats();
    }

    protected boolean getHardcore() {
        return this.tabManager.getHardcore();
    }

    protected EnumDifficulty getDifficulty() {
        return this.tabManager.getDifficulty();
    }
}
