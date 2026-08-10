package meowmel.quantumhue.createworld.tab;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.createworld.api.GuiCyclableButton;
import meowmel.quantumhue.createworld.api.tab.AbstractScreenTab;
import meowmel.quantumhue.createworld.api.tab.TabManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLockIconButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.EnumDifficulty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 游戏设置标签页——世界名称、游戏模式、难度（含锁定）、允许作弊。
 * <p>Game settings tab: world name, game mode, difficulty (with lock), allow cheats.</p>
 */
public class GameTab extends AbstractScreenTab {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:GameTab");

    private GuiTextField worldNameField;
    private GuiCyclableButton gameModeButton;
    private GuiCyclableButton allowCheatsButton;
    private GuiCyclableButton difficultyButton;
    private GuiLockIconButton difficultyLockButton;
    private boolean userSetDifficultyLocked = false;

    public GameTab() {
        super(100, "quantumhue.createworld.tab.game");
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);

        // 世界名输入框位于 height/5，下方 8px 起依次排列三个按钮，避免中间空一大块
        int nameY = height / 5;
        int buttonY = nameY + 28;

        this.worldNameField = new GuiTextField(9, this.mc.fontRenderer, width / 2 - 104, nameY, 208, 20);
        String worldName = this.getWorldName();
        if ((worldName == null || worldName.trim().isEmpty()) && !QuantumHueConfig.createWorld.disableCreateButtonWhenWNIsBlank) {
            worldName = I18n.format("selectWorld.newWorld");
            tabManager.setWorldName(worldName);
        } else if (worldName == null || worldName.trim().isEmpty()) {
            worldName = "";
        }
        this.worldNameField.setText(worldName);
        this.worldNameField.setFocused(true);

        this.gameModeButton = new GuiCyclableButton(2, width / 2 - 104, buttonY, 208, 20,
                this::getGameModeText, direction -> this.cycleGameMode());
        this.addButton(this.gameModeButton);

        int diffBtnWidth = QuantumHueConfig.createWorld.enableLock ? 188 : 208;
        this.difficultyButton = new GuiCyclableButton(9, width / 2 - 104, buttonY + 25, diffBtnWidth, 20,
                this::getDifficultyText, direction -> {
            if (!this.getHardcore()) {
                this.cycleDifficulty();
            } else {
                this.hardcoreSetToHard();
            }
        });
        this.addButton(this.difficultyButton);

        if (QuantumHueConfig.createWorld.enableLock) {
            this.difficultyLockButton = new GuiLockIconButton(10, width / 2 - 104 + diffBtnWidth, buttonY + 25);
            this.difficultyLockButton.setLocked(false);
            this.userSetDifficultyLocked = false;
            this.addButton(this.difficultyLockButton);
        }

        this.allowCheatsButton = new GuiCyclableButton(6, width / 2 - 104, buttonY + 50, 208, 20,
                this::getAllowCheatsText, direction -> {
            if (!this.getHardcore()) {
                tabManager.setAllowCheats(!this.getAllowCheats());
            }
        });
        this.addButton(this.allowCheatsButton);

        this.setVisible(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.mc.fontRenderer.drawString(I18n.format("selectWorld.enterName"),
                this.tabManager.getParent().width / 2 - 104, this.tabManager.getParent().height / 5 - 13, 0xA0A0A0);

        this.worldNameField.drawTextBox();

        if (QuantumHueConfig.createWorld.showWorldNamePlaceHolder
                && this.worldNameField.getText().isEmpty() && !this.worldNameField.isFocused()) {
            String placeholder = I18n.format("quantumhue.createworld.placeholder.worldName");
            int x = this.worldNameField.x + 4;
            int y = this.worldNameField.y + (this.worldNameField.height - 8) / 2;
            this.mc.fontRenderer.drawStringWithShadow(placeholder, x, y, 0x808080);
        }

        if (this.gameModeButton != null) {
            this.gameModeButton.updateText();
        }
        if (this.difficultyButton != null) {
            this.difficultyButton.updateText();
        }
        if (this.allowCheatsButton != null) {
            this.allowCheatsButton.updateText();
        }

        if (this.difficultyButton != null) {
            if (this.getHardcore()) {
                this.difficultyButton.enabled = false;
                if (this.difficultyLockButton != null) {
                    if (!this.difficultyLockButton.isLocked()) {
                        this.userSetDifficultyLocked = this.difficultyLockButton.isLocked();
                    }
                    this.difficultyLockButton.setLocked(true);
                    this.difficultyLockButton.enabled = false;
                    this.tabManager.setDifficultyLocked(true);
                }
            } else if (this.difficultyLockButton != null) {
                this.difficultyLockButton.setLocked(this.userSetDifficultyLocked);
                this.difficultyLockButton.enabled = true;
                this.difficultyButton.enabled = !this.userSetDifficultyLocked;
                this.tabManager.setDifficultyLocked(this.userSetDifficultyLocked);
            } else {
                this.difficultyButton.enabled = true;
            }
        }
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button == this.difficultyLockButton) {
            if (this.getHardcore()) {
                return;
            }
            boolean newLocked = !this.difficultyLockButton.isLocked();
            this.difficultyLockButton.setLocked(newLocked);
            this.userSetDifficultyLocked = newLocked;
            this.tabManager.setDifficultyLocked(newLocked);
            if (this.difficultyButton != null) {
                this.difficultyButton.enabled = !newLocked;
            }
        }
    }

    private String getGameModeText() {
        String mode = this.getGameMode();
        if (mode == null || mode.isEmpty()) {
            mode = "survival";
        }
        return I18n.format("selectWorld.gameMode") + ": " + I18n.format("selectWorld.gameMode." + mode);
    }

    private String getDifficultyText() {
        if (this.getHardcore()) {
            return I18n.format("options.difficulty") + ": " + I18n.format("options.difficulty.hardcore");
        }
        return I18n.format("options.difficulty") + ": " + I18n.format(this.getDifficulty().getTranslationKey());
    }

    private String getAllowCheatsText() {
        boolean allowCheats = this.getAllowCheats();
        boolean hardcore = this.getHardcore();
        boolean isOn = allowCheats && !hardcore;
        return I18n.format("selectWorld.allowCommands") + " "
                + (isOn ? I18n.format("options.on") : I18n.format("options.off"));
    }

    private void cycleGameMode() {
        String[] modes = {"survival", "creative", "hardcore", "adventure"};
        String currentMode = this.getGameMode();
        if (currentMode == null) {
            currentMode = "survival";
        }
        int currentIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(currentMode)) {
                currentIndex = i;
                break;
            }
        }
        String newMode = modes[(currentIndex + 1) % modes.length];
        this.tabManager.setGameMode(newMode);
        this.tabManager.setHardcore("hardcore".equals(newMode));
        if ("hardcore".equals(newMode)) {
            this.tabManager.setAllowCheats(false);
            this.tabManager.setBonusChest(false);
        } else if ("creative".equals(newMode)) {
            this.tabManager.setAllowCheats(true);
        }

        if (this.allowCheatsButton != null) {
            this.allowCheatsButton.enabled = !this.getHardcore();
            this.allowCheatsButton.updateText();
        }
        if (this.difficultyButton != null) {
            this.difficultyButton.enabled = !this.getHardcore();
            if (this.difficultyLockButton != null) {
                if (this.getHardcore()) {
                    if (!this.difficultyLockButton.isLocked()) {
                        this.userSetDifficultyLocked = this.difficultyLockButton.isLocked();
                    }
                    this.difficultyLockButton.setLocked(true);
                    this.difficultyLockButton.enabled = false;
                    this.tabManager.setDifficultyLocked(true);
                } else {
                    this.difficultyLockButton.setLocked(this.userSetDifficultyLocked);
                    this.difficultyLockButton.enabled = true;
                    this.difficultyButton.enabled = !this.userSetDifficultyLocked;
                    this.tabManager.setDifficultyLocked(this.userSetDifficultyLocked);
                }
            }
            this.difficultyButton.updateText();
        }
    }

    private void cycleDifficulty() {
        EnumDifficulty current = this.getDifficulty();
        int next = (current.getId() + 1) % EnumDifficulty.values().length;
        this.tabManager.setDifficulty(EnumDifficulty.byId(next));
    }

    private void hardcoreSetToHard() {
        EnumDifficulty difficulty = this.getDifficulty();
        int hcs2d = difficulty.getId();
        this.tabManager.setDifficulty(EnumDifficulty.byId(hcs2d));
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.worldNameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        this.worldNameField.textboxKeyTyped(typedChar, keyCode);
        this.tabManager.setWorldName(this.worldNameField.getText());
        this.updateCreateButtonState();
    }

    private void updateCreateButtonState() {
        for (GuiButton button : this.tabButtons) {
            if (button.id != 0) {
                continue;
            }
            String text = this.worldNameField.getText().trim();
            if (QuantumHueConfig.createWorld.disableCreateButtonWhenWNIsBlank) {
                button.enabled = !text.isEmpty();
                break;
            }
            if (text.isEmpty() || text.equals(I18n.format("quantumhue.createworld.placeholder.worldName"))) {
                button.enabled = false;
                break;
            }
            button.enabled = true;
            break;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && this.worldNameField != null) {
            this.worldNameField.setFocused(true);
        }
        LOGGER.debug("GameTab visible={}", visible);
    }
}
