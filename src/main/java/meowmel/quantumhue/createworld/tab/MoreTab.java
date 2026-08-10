package meowmel.quantumhue.createworld.tab;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleApplier;
import meowmel.quantumhue.createworld.api.tab.AbstractScreenTab;
import meowmel.quantumhue.createworld.api.tab.TabManager;
import meowmel.quantumhue.createworld.gamerule.GuiScreenGameRuleEditor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * 更多选项标签页——游戏规则编辑器（及可选的实验性功能占位按钮）。
 * <p>More options tab: game rule editor (plus optional placeholder buttons).</p>
 */
public class MoreTab extends AbstractScreenTab {

    private GuiButton gameRuleEditorButton;
    private GuiButton experimentsButton;
    private GuiButton dataPacksButton;

    public MoreTab() {
        super(102, "quantumhue.createworld.tab.more");
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);

        if (QuantumHueConfig.createWorld.gameruleEdit) {
            this.gameRuleEditorButton = new GuiButton(200, width / 2 - 105, height / 6 + 40, 210, 20,
                    I18n.format("quantumhue.createworld.button.gameRuleEditor"));
            this.addButton(this.gameRuleEditorButton);
        }

        if (QuantumHueConfig.createWorld.enableOtherMoreTabButton) {
            this.experimentsButton = new GuiButton(201, width / 2 - 105, height / 6 + 65, 210, 20,
                    I18n.format("selectWorld.experiments"));
            this.addButton(this.experimentsButton);
            this.dataPacksButton = new GuiButton(202, width / 2 - 105, height / 6 + 90, 210, 20,
                    I18n.format("selectWorld.dataPacks"));
            this.addButton(this.dataPacksButton);
        }

        this.setVisible(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 本页无额外绘制 / nothing extra to draw
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.id == 200) {
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
            this.mc.displayGuiScreen(new GuiScreenGameRuleEditor(this.tabManager.getParent(), cleanPending));
        } else if (button.id == 201) {
            // 实验性功能占位 / placeholder
        } else if (button.id == 202) {
            // 数据包占位 / placeholder
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}
