package meowmel.quantumhue.createworld.tab;

import meowmel.quantumhue.createworld.api.GuiCyclableButton;
import meowmel.quantumhue.createworld.api.tab.AbstractScreenTab;
import meowmel.quantumhue.createworld.api.tab.TabManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.WorldType;

/**
 * 世界选项标签页——世界类型、自定义、种子、生成建筑、奖励箱。
 * <p>World options tab: world type, customize, seed, structures, bonus chest.</p>
 */
public class WorldTab extends AbstractScreenTab {

    private GuiTextField seedField;
    private GuiCyclableButton worldTypeButton;
    private GuiButton generateStructuresButton;
    private GuiButton bonusChestButton;
    private GuiButton customizeButton;

    public WorldTab() {
        super(101, "quantumhue.createworld.tab.world");
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);

        this.seedField = new GuiTextField(10, this.mc.fontRenderer, width / 2 - 154, height / 3 - 1, 308, 20) {
            @Override
            public void drawTextBox() {
                super.drawTextBox();
                // 空且未聚焦时绘制占位提示 / placeholder when empty and unfocused
                if (this.getText().isEmpty() && !this.isFocused()) {
                    String placeholder = I18n.format("selectWorld.seedInfo");
                    int textColor = 0x808080;
                    int textX = this.x + 4;
                    int textY = this.y + (this.height - 8) / 2;
                    WorldTab.this.mc.fontRenderer.drawStringWithShadow(placeholder, textX, textY, textColor);
                }
            }
        };
        this.seedField.setText(this.getSeed());

        this.worldTypeButton = new GuiCyclableButton(5, width / 2 - 154, height / 8 + 10, 150, 20,
                this::getWorldTypeText, direction -> this.cycleWorldType());
        this.addButton(this.worldTypeButton);

        this.customizeButton = new GuiButton(8, width / 2 + 4, height / 8 + 10, 150, 20,
                I18n.format("selectWorld.customizeType"));
        this.addButton(this.customizeButton);

        this.generateStructuresButton = new GuiButton(4, width / 2 + 110, height / 2 + 15, 44, 20,
                this.getGenerateStructuresText());
        this.addButton(this.generateStructuresButton);

        this.bonusChestButton = new GuiButton(7, width / 2 + 110, height / 2 - 15, 44, 20, this.getBonusChestText());
        this.addButton(this.bonusChestButton);

        this.setVisible(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.mc.fontRenderer.drawString(I18n.format("selectWorld.enterSeed"),
                this.tabManager.getParent().width / 2 - 154, this.tabManager.getParent().height / 3 - 2 - 13, 0xA0A0A0);
        this.seedField.drawTextBox();

        this.mc.fontRenderer.drawString(I18n.format("quantumhue.createworld.selectWorld.mapFeatures"),
                this.tabManager.getParent().width / 2 - 154, this.tabManager.getParent().height / 2 + 15 + 6, 0xFFFFFF);
        this.mc.fontRenderer.drawString(I18n.format("quantumhue.createworld.selectWorld.bonusItems"),
                this.tabManager.getParent().width / 2 - 154, this.tabManager.getParent().height / 2 - 15 + 6, 0xFFFFFF);

        if (this.worldTypeButton != null) {
            this.worldTypeButton.updateText();
        }
        this.customizeButton.enabled = WorldType.WORLD_TYPES != null
                && this.getWorldTypeIndex() < WorldType.WORLD_TYPES.length
                && WorldType.WORLD_TYPES[this.getWorldTypeIndex()] != null
                && WorldType.WORLD_TYPES[this.getWorldTypeIndex()].isCustomizable();
        this.generateStructuresButton.displayString = this.getGenerateStructuresText();
        this.bonusChestButton.displayString = this.getBonusChestText();
        this.bonusChestButton.enabled = !this.getHardcore();
    }

    @Override
    public void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 4:
                this.tabManager.setGenerateStructures(!this.getGenerateStructures());
                break;
            case 7:
                if (this.getHardcore()) {
                    break;
                }
                this.tabManager.setBonusChest(!this.getBonusChest());
                break;
            case 8:
                if (WorldType.WORLD_TYPES == null
                        || this.getWorldTypeIndex() >= WorldType.WORLD_TYPES.length
                        || WorldType.WORLD_TYPES[this.getWorldTypeIndex()] == null) {
                    break;
                }
                WorldType.WORLD_TYPES[this.getWorldTypeIndex()].onCustomizeButton(this.mc, this.tabManager.getParent());
                break;
            default:
                break;
        }
    }

    private String getWorldTypeText() {
        int index = this.getWorldTypeIndex();
        if (WorldType.WORLD_TYPES == null || index >= WorldType.WORLD_TYPES.length || WorldType.WORLD_TYPES[index] == null) {
            return I18n.format("selectWorld.mapType") + " " + I18n.format("selectWorld.mapType.normal");
        }
        return I18n.format("selectWorld.mapType") + " " + I18n.format(WorldType.WORLD_TYPES[index].getTranslationKey());
    }

    private String getGenerateStructuresText() {
        return this.getGenerateStructures() ? I18n.format("options.on") : I18n.format("options.off");
    }

    private String getBonusChestText() {
        boolean bonusChest = this.getBonusChest();
        boolean hardcore = this.getHardcore();
        boolean isOn = bonusChest && !hardcore;
        return isOn ? I18n.format("options.on") : I18n.format("options.off");
    }

    private void cycleWorldType() {
        if (WorldType.WORLD_TYPES == null) {
            return;
        }
        int currentIndex = this.getWorldTypeIndex();
        int newIndex = currentIndex;
        // 跳过 null 槽位 / skip null slots
        while (WorldType.WORLD_TYPES[newIndex = (newIndex + 1) % WorldType.WORLD_TYPES.length] == null
                && newIndex != currentIndex) {
        }
        if (WorldType.WORLD_TYPES[newIndex] != null) {
            this.tabManager.setWorldTypeIndex(newIndex);
            if (this.worldTypeButton != null) {
                this.worldTypeButton.updateText();
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.seedField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        this.seedField.textboxKeyTyped(typedChar, keyCode);
        this.tabManager.setSeed(this.seedField.getText());
    }
}
