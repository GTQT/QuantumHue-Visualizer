package meowmel.quantumhue.createworld.api.tab;

import net.minecraft.client.gui.GuiButton;

/**
 * 标签页接口。
 * <p>Interface implemented by each tab of the create-world screen.</p>
 * 生命周期：initGui（每次打开/窗口缩放）→ drawScreen/actionPerformed/mouseClicked/keyTyped。
 */
public interface Tab {

    void initGui(TabManager tabManager, int width, int height);

    void drawScreen(int mouseX, int mouseY, float partialTicks);

    void actionPerformed(GuiButton button);

    void mouseClicked(int mouseX, int mouseY, int mouseButton);

    void keyTyped(char typedChar, int keyCode);

    int getTabId();

    String getTabName();

    void setVisible(boolean visible);
}
