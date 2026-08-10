package meowmel.quantumhue.createworld.gamerule;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.createworld.api.ContentPanelRenderer;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleApplier;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleCategoryRegistry;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleMonitorNSetter;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleNameRegistry;
import meowmel.quantumhue.createworld.api.gamerule.GameRuleTooltipRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏规则编辑器——虚拟化滚动列表：只创建可见行的组件，滚动时重建。
 * <p>Game rule editor with a virtualized scrolling list: only visible rows get
 * components, rebuilt on scroll.</p>
 * 滚动采用 lerp 插值 + 行偏移；列表区域 glScissor 裁剪（已按 GUI 缩放换算像素坐标）。
 */
public class GuiScreenGameRuleEditor extends GuiScreen {

    @SideOnly(Side.CLIENT)
    private static final Logger LOGGER = LogManager.getLogger("QuantumHue:GameRuleEditor");

    private final Map<String, String> editableRules;
    private final Map<String, GameRuleMonitorNSetter.GameruleValue> defaultRules;
    private final Map<String, String> modifiedRules;
    private final Set<String> changedRules;
    private final Map<String, GuiComponentWrapper> ruleComponents;
    private GuiButton saveButton;
    private GuiButton cancelButton;
    private GuiButton resetButton;
    private float scrollPosition;
    private float targetScrollPosition;
    private float scrollSubOffset;
    private int maxScrollPosition;
    private int scrollOffset;
    private int lastComponentScrollOffset;
    private float lastComponentCreationScrollPosition;
    private String focusedRuleName;
    private static final float SCROLL_LERP_SPEED = 0.2F;
    private static final int ROW_HEIGHT = 25;
    private static final int CATEGORY_HEADER_HEIGHT = 20;
    private int visibleRows;
    private boolean isScrolling;
    private GuiScreen parentScreen;
    private static final int PANEL_TOP = 50;
    private static final int CONTENT_TOP = 60;
    private static final boolean CLEAR_MY_BACKGROUND_LOADED = Loader.isModLoaded("clearmybackground");

    public GuiScreenGameRuleEditor(GuiScreen parentScreen, Map<String, String> editableRules) {
        this.modifiedRules = new HashMap<>();
        this.changedRules = new HashSet<>();
        this.ruleComponents = new LinkedHashMap<>();
        this.scrollPosition = 0.0F;
        this.targetScrollPosition = 0.0F;
        this.scrollSubOffset = 0.0F;
        this.scrollOffset = 0;
        this.lastComponentScrollOffset = -1;
        this.lastComponentCreationScrollPosition = -1.0F;
        this.focusedRuleName = null;
        this.visibleRows = 8;
        this.isScrolling = false;
        this.parentScreen = parentScreen;

        // 过滤 null 值 / filter null values
        this.editableRules = new HashMap<>();
        if (editableRules != null) {
            for (Map.Entry<String, String> entry : editableRules.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                this.editableRules.put(entry.getKey(), entry.getValue());
            }
        }
        // 保存原始副本用于比较修改 / original copy for change comparison
        for (Map.Entry<String, String> e : this.editableRules.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            this.modifiedRules.put(e.getKey(), e.getValue());
        }

        // 默认规则三级回退：editableRules → 当前世界 → new GameRules() / three-level fallback
        Map<String, GameRuleMonitorNSetter.GameruleValue> defaultsFromMonitor = null;

        if (!this.editableRules.isEmpty()) {
            defaultsFromMonitor = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : this.editableRules.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                boolean isBoolean = "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                int intValue = 0;
                double doubleValue = 0.0;
                try {
                    intValue = Integer.parseInt(value);
                } catch (Exception ignored) {
                }
                try {
                    doubleValue = Double.parseDouble(value);
                } catch (Exception ignored) {
                }
                defaultsFromMonitor.put(key, new GameRuleMonitorNSetter.GameruleValue(value, isBoolean, intValue, doubleValue));
            }
        }

        if (defaultsFromMonitor == null || defaultsFromMonitor.isEmpty()) {
            try {
                WorldClient w = Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null;
                if (w != null) {
                    defaultsFromMonitor = GameRuleMonitorNSetter.getAllGamerules(w);
                }
            } catch (Throwable t) {
                LOGGER.warn("Error while trying to get defaults from MonitorNSetter: {}", t.getMessage());
                defaultsFromMonitor = null;
            }
        }

        if (defaultsFromMonitor == null || defaultsFromMonitor.isEmpty()) {
            defaultsFromMonitor = new LinkedHashMap<>();
            try {
                GameRules temp = new GameRules();
                String[] keys = temp.getRules();
                if (keys != null) {
                    for (String key : keys) {
                        String s = temp.getString(key);
                        boolean b = "true".equalsIgnoreCase(s);
                        int iv = 0;
                        double dv = 0.0;
                        try {
                            iv = Integer.parseInt(s);
                        } catch (Exception ignored) {
                        }
                        try {
                            dv = Double.parseDouble(s);
                        } catch (Exception ignored) {
                        }
                        defaultsFromMonitor.put(key, new GameRuleMonitorNSetter.GameruleValue(s, b, iv, dv));
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to build defaults from temporary GameRules: {}", t.getMessage());
            }
        }

        this.defaultRules = defaultsFromMonitor != null ? new LinkedHashMap<>(defaultsFromMonitor) : new LinkedHashMap<>();

        // 确保 defaultRules 覆盖全部 editableRules / ensure coverage
        for (String k : this.editableRules.keySet()) {
            if (this.defaultRules.containsKey(k)) {
                continue;
            }
            String s = this.editableRules.get(k);
            boolean b = "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
            int iv = 0;
            double dv = 0.0;
            try {
                iv = Integer.parseInt(s);
            } catch (Exception ignored) {
            }
            try {
                dv = Double.parseDouble(s);
            } catch (Exception ignored) {
            }
            this.defaultRules.put(k, new GameRuleMonitorNSetter.GameruleValue(s, b, iv, dv));
        }
        this.maxScrollPosition = 0;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int panelBottom = this.height - 50;
        this.visibleRows = Math.max(1, (panelBottom - CONTENT_TOP) / ROW_HEIGHT);

        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int totalContentHeight = 0;
        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                totalContentHeight += CATEGORY_HEADER_HEIGHT;
            } else {
                totalContentHeight += ROW_HEIGHT;
            }
        }
        this.maxScrollPosition = Math.max(0, totalContentHeight - this.visibleRows * ROW_HEIGHT);
        this.targetScrollPosition = Math.max(0.0F, Math.min(this.targetScrollPosition, this.maxScrollPosition));
        this.scrollPosition = Math.max(0.0F, Math.min(this.scrollPosition, this.maxScrollPosition));
        this.updateScrollDerivedValues(categoryOrderedList.size());

        if (QuantumHueConfig.createWorld.enableResetButton) {
            this.saveButton = new GuiButton(0, this.width / 2 - 154, this.height - 30, 100, 20, I18n.format("options.save"));
            this.cancelButton = new GuiButton(1, this.width / 2 - 50, this.height - 30, 100, 20, I18n.format("gui.cancel"));
            this.resetButton = new GuiButton(2, this.width / 2 + 54, this.height - 30, 100, 20, I18n.format("options.reset"));
        } else {
            this.cancelButton = new GuiButton(1, this.width / 2 + 2, this.height - 30, 150, 20, I18n.format("gui.cancel"));
            this.saveButton = new GuiButton(0, this.width / 2 - 152, this.height - 30, 150, 20, I18n.format("options.save"));
        }
        if (this.saveButton != null) {
            this.buttonList.add(this.saveButton);
        }
        if (this.cancelButton != null) {
            this.buttonList.add(this.cancelButton);
        }
        if (this.resetButton != null) {
            this.buttonList.add(this.resetButton);
        }
        this.createRuleComponents();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateScrollDerivedValues(int totalItems) {
        int newScrollOffset = (int) (this.scrollPosition / ROW_HEIGHT);
        int maxRowOffset = Math.max(0, totalItems - this.visibleRows);
        this.scrollOffset = Math.max(0, Math.min(newScrollOffset, maxRowOffset));
        this.scrollSubOffset = this.scrollPosition - (float) (this.scrollOffset * ROW_HEIGHT);
        if (this.scrollSubOffset < 0.0F) {
            this.scrollSubOffset = 0.0F;
        }
    }

    /**
     * 构建按分类组织的规则列表："category:" 前缀为分类标题，其余为规则名。
     * <p>Category-ordered list; "category:" prefix marks header entries.</p>
     */
    private List<String> buildCategoryOrderedList() {
        List<String> orderedList = new ArrayList<>();
        Set<String> allRules = this.defaultRules.keySet();
        List<String> categories = GameRuleCategoryRegistry.getAllCategories();

        for (String categoryKey : categories) {
            List<String> rulesInCategory = GameRuleCategoryRegistry.getRulesInCategory(categoryKey);
            List<String> validRules = new ArrayList<>();
            for (String rule : rulesInCategory) {
                if (allRules.contains(rule)) {
                    validRules.add(rule);
                }
            }
            if (validRules.isEmpty()) {
                continue;
            }
            orderedList.add("category:" + categoryKey);
            orderedList.addAll(validRules);
        }

        Set<String> categorizedRules = new HashSet<>();
        for (String categoryKey : categories) {
            categorizedRules.addAll(GameRuleCategoryRegistry.getRulesInCategory(categoryKey));
        }
        boolean hasUncategorized = false;
        for (String rule : allRules) {
            if (categorizedRules.contains(rule)) {
                continue;
            }
            if (!hasUncategorized) {
                orderedList.add("category:quantumhue.gamerule.category.uncategorized");
                hasUncategorized = true;
            }
            orderedList.add(rule);
        }
        return orderedList;
    }

    /**
     * 重建可见行组件（虚拟化）。先删除旧组件（id>=100），再按滚动位置创建可见范围的行。
     * <p>Rebuild visible-row components; remove old ones (id>=100) first.</p>
     */
    private void createRuleComponents() {
        if (this.buttonList == null) {
            LOGGER.error("buttonList is null! Initializing...");
            this.buttonList = new ArrayList<>();
            return;
        }
        // 记住当前聚焦的文本框规则 / remember which text field had focus
        this.focusedRuleName = null;
        for (Map.Entry<String, GuiComponentWrapper> entry : this.ruleComponents.entrySet()) {
            if (entry.getValue().type == ComponentType.TEXT_FIELD) {
                GuiTextField tf = (GuiTextField) entry.getValue().component;
                if (tf.isFocused()) {
                    this.focusedRuleName = entry.getKey();
                    break;
                }
            }
        }
        // 删除所有旧规则组件（id>=100）/ remove all old rule components
        java.util.Iterator<GuiButton> it = this.buttonList.iterator();
        while (it.hasNext()) {
            GuiButton btn = it.next();
            if (btn == null || btn.id < 100) {
                continue;
            }
            it.remove();
        }
        this.ruleComponents.clear();

        int index = 0;
        int visibleUIRowIndex = 0;
        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int totalHeight = 0;
        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                totalHeight += CATEGORY_HEADER_HEIGHT;
            } else {
                totalHeight += ROW_HEIGHT;
            }
        }
        int panelBottom = this.height - 50;
        int visibleHeight = panelBottom - CONTENT_TOP;
        int currentY = 0;

        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                currentY += CATEGORY_HEADER_HEIGHT;
                index++;
                continue;
            }
            String ruleName = item;
            GameRuleMonitorNSetter.GameruleValue value = this.defaultRules.get(ruleName);
            if (value == null) {
                LOGGER.warn("GameruleValue for {} is null, skipping", ruleName);
                currentY += ROW_HEIGHT;
                index++;
                continue;
            }
            int itemBottom = currentY + ROW_HEIGHT;
            // 可见性裁剪 / visibility culling
            if ((float) itemBottom <= this.scrollPosition || (float) currentY >= this.scrollPosition + (float) visibleHeight) {
                currentY += ROW_HEIGHT;
                index++;
                continue;
            }
            int yPos = CONTENT_TOP + (currentY - this.scrollOffset * ROW_HEIGHT);
            String stringValue = null;
            if (this.modifiedRules.containsKey(ruleName)) {
                stringValue = this.modifiedRules.get(ruleName);
            } else if (this.editableRules.containsKey(ruleName)) {
                stringValue = this.editableRules.get(ruleName);
            }
            Object displayObj = stringValue != null
                    ? this.parseFromString(stringValue, value.getOptimalValue())
                    : value.getOptimalValue();
            GuiComponentWrapper wrapper = this.createComponentForRule(ruleName, displayObj, yPos, 100 + visibleUIRowIndex);
            if (wrapper != null) {
                wrapper.globalIndex = index;
                wrapper.ruleName = ruleName;
                this.ruleComponents.put(ruleName, wrapper);
            }
            visibleUIRowIndex++;
            currentY += ROW_HEIGHT;
            index++;
        }
        this.lastComponentScrollOffset = this.scrollOffset;
        this.lastComponentCreationScrollPosition = this.scrollPosition;

        // 恢复聚焦 / restore focus
        if (this.focusedRuleName != null && this.ruleComponents.containsKey(this.focusedRuleName)) {
            GuiComponentWrapper wrapper = this.ruleComponents.get(this.focusedRuleName);
            if (wrapper != null && wrapper.type == ComponentType.TEXT_FIELD) {
                ((GuiTextField) wrapper.component).setFocused(true);
            }
        }
    }

    private GuiComponentWrapper createComponentForRule(String ruleName, Object value, int yPos, int id) {
        int componentX = this.width / 2 + 90;
        int componentWidth = 44;
        if (value instanceof Boolean) {
            boolean boolValue = (Boolean) value;
            String display = boolValue ? I18n.format("options.on") : I18n.format("options.off");
            GuiButton button = new GuiButton(id, componentX, yPos, componentWidth, 20, display);
            this.buttonList.add(button);
            return new GuiComponentWrapper(button, ComponentType.BOOLEAN_BUTTON);
        }
        GuiTextField textField = new GuiTextField(id, this.fontRenderer, componentX, yPos, componentWidth, 20);
        String initial = this.modifiedRules.containsKey(ruleName)
                ? this.modifiedRules.get(ruleName)
                : this.editableRules.containsKey(ruleName)
                ? this.editableRules.get(ruleName)
                : value != null ? String.valueOf(value) : "";
        textField.setText(initial);
        textField.setMaxStringLength(200);
        return new GuiComponentWrapper(textField, ComponentType.TEXT_FIELD);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        switch (id) {
            case 0:
                this.saveChanges();
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case 1:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case 2:
                this.modifiedRules.clear();
                this.changedRules.clear();
                this.modifiedRules.putAll(this.editableRules);
                this.createRuleComponents();
                return;
            default:
                break;
        }
        if (id >= 100) {
            // 布尔规则切换 / boolean rule toggle
            String ruleName = null;
            for (Map.Entry<String, GuiComponentWrapper> entry : this.ruleComponents.entrySet()) {
                if (!(entry.getValue().component instanceof GuiButton)) {
                    continue;
                }
                GuiButton btn = (GuiButton) entry.getValue().component;
                if (btn.id != id) {
                    continue;
                }
                ruleName = entry.getValue().ruleName;
                break;
            }
            if (ruleName == null || !this.defaultRules.containsKey(ruleName)) {
                return;
            }
            GuiComponentWrapper wrapper = this.ruleComponents.get(ruleName);
            if (wrapper != null && wrapper.type == ComponentType.BOOLEAN_BUTTON) {
                this.toggleBooleanRule(ruleName, button);
            }
        }
    }

    private void toggleBooleanRule(String ruleName, GuiButton button) {
        String curStr = null;
        if (this.modifiedRules.containsKey(ruleName)) {
            curStr = this.modifiedRules.get(ruleName);
        } else if (this.editableRules.containsKey(ruleName)) {
            curStr = this.editableRules.get(ruleName);
        } else {
            GameRuleMonitorNSetter.GameruleValue def = this.defaultRules.get(ruleName);
            curStr = def != null ? String.valueOf(def.getOptimalValue()) : "false";
        }
        boolean cur = Boolean.parseBoolean(curStr);
        boolean next = !cur;
        this.modifiedRules.put(ruleName, String.valueOf(next));
        button.displayString = next ? I18n.format("options.on") : I18n.format("options.off");
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        for (Map.Entry<String, GuiComponentWrapper> entry : this.ruleComponents.entrySet()) {
            String ruleName = entry.getKey();
            GuiComponentWrapper wrapper = entry.getValue();
            if (wrapper.type != ComponentType.TEXT_FIELD) {
                continue;
            }
            GuiTextField textField = (GuiTextField) wrapper.component;
            if (!textField.textboxKeyTyped(typedChar, keyCode)) {
                continue;
            }
            String input = textField.getText();
            Object parsed = this.parseFromString(input, this.defaultRules.get(ruleName).getOptimalValue());
            this.modifiedRules.put(ruleName, String.valueOf(parsed));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int panelBottom = this.height - 50;
        // 标准按钮（保存/取消/重置）/ standard buttons
        for (Object obj : this.buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id >= 100 || !button.enabled || !button.mousePressed(this.mc, mouseX, mouseY)) {
                continue;
            }
            button.playPressSound(this.mc.getSoundHandler());
            this.actionPerformed(button);
        }
        if (mouseY >= CONTENT_TOP && mouseY <= panelBottom) {
            // 列表内：组件位置需补偿滚动子偏移 / adjust for scroll sub-offset
            int adjustedMouseY = mouseY + Math.round(this.scrollSubOffset);
            for (GuiComponentWrapper wrapper : this.ruleComponents.values()) {
                if (wrapper.type == ComponentType.BOOLEAN_BUTTON) {
                    GuiButton button = (GuiButton) wrapper.component;
                    if (!button.enabled || !button.mousePressed(this.mc, mouseX, adjustedMouseY)) {
                        continue;
                    }
                    button.playPressSound(this.mc.getSoundHandler());
                    this.actionPerformed(button);
                } else if (wrapper.type == ComponentType.TEXT_FIELD) {
                    GuiTextField textField = (GuiTextField) wrapper.component;
                    textField.mouseClicked(mouseX, adjustedMouseY, mouseButton);
                }
            }
        } else {
            for (GuiComponentWrapper wrapper : this.ruleComponents.values()) {
                if (wrapper.type != ComponentType.TEXT_FIELD) {
                    continue;
                }
                ((GuiTextField) wrapper.component).setFocused(false);
            }
        }
        // 滚动条拖拽检测 / scrollbar drag detection
        int scrollBarX = this.width / 2 + 149;
        int scrollBarY = CONTENT_TOP;
        int scrollBarHeight = this.visibleRows * ROW_HEIGHT;
        if (mouseX >= scrollBarX && mouseX <= scrollBarX + 10
                && mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
            this.isScrolling = true;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0 || state == 1) {
            this.isScrolling = false;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (this.isScrolling) {
            // 拖动滚动条 / dragging the scrollbar
            int scrollBarY = CONTENT_TOP;
            int scrollBarHeight = this.visibleRows * ROW_HEIGHT;
            List<String> categoryOrderedList = this.buildCategoryOrderedList();
            int totalItems = categoryOrderedList.size();
            int sliderHeight = Math.max(20, scrollBarHeight * this.visibleRows / totalItems);
            float relativePosition = (float) (mouseY - scrollBarY - sliderHeight / 2)
                    / (float) (scrollBarHeight - sliderHeight);
            float newPos = relativePosition * this.maxScrollPosition;
            newPos = Math.max(0.0F, Math.min(newPos, this.maxScrollPosition));
            this.scrollPosition = this.targetScrollPosition = newPos;
            this.updateScrollDerivedValues(this.buildCategoryOrderedList().size());
            if (this.scrollOffset != this.lastComponentScrollOffset
                    || Math.abs(this.scrollPosition - this.lastComponentCreationScrollPosition) > 12.5F) {
                this.createRuleComponents();
            }
        } else if (Mouse.getEventDWheel() != 0) {
            // 滚轮滚动 / wheel scrolling
            int scrollAmount = Mouse.getEventDWheel() > 0 ? -1 : 1;
            int panelBottom = this.height - 50;
            List<String> categoryOrderedList = this.buildCategoryOrderedList();
            int totalHeight = 0;
            for (String item : categoryOrderedList) {
                if (item.startsWith("category:")) {
                    totalHeight += CATEGORY_HEADER_HEIGHT;
                } else {
                    totalHeight += ROW_HEIGHT;
                }
            }
            int actualVisibleHeight = panelBottom - CONTENT_TOP;
            this.maxScrollPosition = Math.max(0, totalHeight - actualVisibleHeight);
            this.targetScrollPosition += scrollAmount * ROW_HEIGHT;
            this.targetScrollPosition = Math.max(0.0F, Math.min(this.targetScrollPosition, this.maxScrollPosition));
        }
    }

    @Override
    public void updateScreen() {
        for (GuiComponentWrapper wrapper : this.ruleComponents.values()) {
            if (wrapper.type != ComponentType.TEXT_FIELD) {
                continue;
            }
            GuiTextField textField = (GuiTextField) wrapper.component;
            textField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelBottom = this.height - 50;
        this.visibleRows = Math.max(1, (panelBottom - CONTENT_TOP) / ROW_HEIGHT);

        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int totalHeight = 0;
        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                totalHeight += CATEGORY_HEADER_HEIGHT;
            } else {
                totalHeight += ROW_HEIGHT;
            }
        }
        int actualVisibleHeight = panelBottom - CONTENT_TOP;
        this.maxScrollPosition = Math.max(0, totalHeight - actualVisibleHeight);

        // 滚动插值 / scroll lerp
        if (Math.abs(this.scrollPosition - this.targetScrollPosition) > 0.5F) {
            this.scrollPosition += (this.targetScrollPosition - this.scrollPosition) * SCROLL_LERP_SPEED;
        } else if (this.scrollPosition != this.targetScrollPosition) {
            this.scrollPosition = this.targetScrollPosition;
        }
        this.updateScrollDerivedValues(this.buildCategoryOrderedList().size());
        if (this.scrollOffset != this.lastComponentScrollOffset
                || Math.abs(this.scrollPosition - this.lastComponentCreationScrollPosition) > 12.5F) {
            this.createRuleComponents();
        }

        this.drawDefaultBackground();
        this.drawContentPanel();
        this.drawCenteredString(this.fontRenderer,
                I18n.format("quantumhue.createworld.gamerules.title"), this.width / 2, 20, 0xFFFFFF);

        // 列表裁剪（GUI 坐标转帧缓冲像素坐标，支持任意 GUI 缩放）/ scissor with framebuffer pixels
        double scaleY = (double) this.mc.displayHeight / (double) this.height;
        int scissorY = (int) Math.floor((double) (this.height - panelBottom) * scaleY);
        int scissorHeight = (int) Math.ceil((double) (panelBottom - CONTENT_TOP) * scaleY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, scissorY, this.mc.displayWidth, scissorHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, -this.scrollSubOffset, 0.0F);

        this.drawRuleList(mouseX, mouseY);
        int adjustedMouseY = mouseY + Math.round(this.scrollSubOffset);
        for (GuiComponentWrapper wrapper : this.ruleComponents.values()) {
            if (wrapper == null || wrapper.component == null) {
                continue;
            }
            if (wrapper.type == ComponentType.TEXT_FIELD) {
                GuiTextField textField = (GuiTextField) wrapper.component;
                if (textField == null) {
                    continue;
                }
                textField.drawTextBox();
            } else if (wrapper.type == ComponentType.BOOLEAN_BUTTON) {
                GuiButton button = (GuiButton) wrapper.component;
                if (button == null) {
                    continue;
                }
                button.drawButton(this.mc, mouseX, adjustedMouseY, partialTicks);
            }
        }
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        this.drawScrollBar();

        // 只让标准按钮进 super.drawScreen（避免组件被重复绘制）/ only standard buttons via super
        List<GuiButton> savedButtonList = new ArrayList<>(this.buttonList);
        List<GuiButton> standardButtons = new ArrayList<>();
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiButton)) {
                continue;
            }
            GuiButton btn = (GuiButton) obj;
            if (btn == null || btn.id >= 100) {
                continue;
            }
            standardButtons.add(btn);
        }
        this.buttonList.clear();
        this.buttonList.addAll(standardButtons);
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.buttonList.clear();
        this.buttonList.addAll(savedButtonList);

        this.drawTooltips(mouseX, mouseY);
    }

    private void drawContentPanel() {
        int panelLeft = 0;
        int panelRight = this.width;
        int panelBottom = this.height - 50;
        if (CLEAR_MY_BACKGROUND_LOADED) {
            ContentPanelRenderer.drawContentPanel(panelLeft, PANEL_TOP, panelRight - panelLeft, panelBottom);
            return;
        }
        this.drawGradientRect(panelLeft, PANEL_TOP, panelRight, panelBottom, 0x60101010, 0x80101010);
        drawRect(panelLeft, PANEL_TOP, panelRight, PANEL_TOP + 1, 0xFF000000);
        drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF000000);
    }

    private void drawRuleList(int mouseX, int mouseY) {
        int yPos = CONTENT_TOP;
        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int panelBottom = this.height - 50;
        int visibleHeight = panelBottom - CONTENT_TOP;
        int currentY = 0;

        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                String categoryKey = item.substring(9);
                if ((float) currentY >= this.scrollPosition && (float) currentY < this.scrollPosition + (float) visibleHeight) {
                    int rowY = yPos + (currentY - this.scrollOffset * ROW_HEIGHT);
                    String categoryName = GameRuleCategoryRegistry.getCategoryDisplayName(categoryKey);
                    int textWidth = this.fontRenderer.getStringWidth(categoryName);
                    int centerX = this.width / 2 - textWidth / 2;
                    this.drawString(this.fontRenderer, categoryName, centerX, rowY + 4, 0xFFFF55);
                }
                currentY += CATEGORY_HEADER_HEIGHT;
                continue;
            }
            String ruleName = item;
            GameRuleMonitorNSetter.GameruleValue originalValue = this.defaultRules.get(ruleName);
            if (originalValue == null) {
                currentY += ROW_HEIGHT;
                continue;
            }
            int itemBottom = currentY + ROW_HEIGHT;
            if ((float) itemBottom <= this.scrollPosition || (float) currentY >= this.scrollPosition + (float) visibleHeight) {
                currentY += ROW_HEIGHT;
                continue;
            }
            int rowY = yPos + (currentY - this.scrollOffset * ROW_HEIGHT);
            String localizedRuleName = GameRuleNameRegistry.getName(ruleName);
            boolean isModified = this.isRuleModified(ruleName);
            int textColor = isModified && QuantumHueConfig.createWorld.highlightModifiedRulesInGUI ? 0xFFFF55 : 0xFFFFFF;
            this.drawString(this.fontRenderer, localizedRuleName, this.width / 2 - 155, rowY + 6, textColor);
            currentY += ROW_HEIGHT;
        }
    }

    private void drawScrollBar() {
        if (this.maxScrollPosition <= 0) {
            return;
        }
        int scrollBarX = this.width / 2 + 149;
        int scrollBarY = CONTENT_TOP;
        int scrollBarHeight = this.visibleRows * ROW_HEIGHT;
        drawRect(scrollBarX, scrollBarY, scrollBarX + 10, scrollBarY + scrollBarHeight, 0xAA202020);
        drawRect(scrollBarX + 1, scrollBarY + 1, scrollBarX + 9, scrollBarY + scrollBarHeight - 1, 0xAAC0C0C0);
        float scrollPercentage = this.maxScrollPosition > 0 ? this.scrollPosition / this.maxScrollPosition : 0.0F;
        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int totalItems = categoryOrderedList.size();
        int sliderHeight = Math.max(20, scrollBarHeight * this.visibleRows / totalItems);
        int sliderY = scrollBarY + (int) (scrollPercentage * (float) (scrollBarHeight - sliderHeight));
        drawRect(scrollBarX + 2, sliderY, scrollBarX + 8, sliderY + sliderHeight, 0xFF8A8A8A);
        drawRect(scrollBarX + 2, sliderY, scrollBarX + 8, sliderY + sliderHeight - 1, 0xFFAAAAAA);
    }

    private void drawTooltips(int mouseX, int mouseY) {
        int panelBottom = this.height - 50;
        if (mouseY < CONTENT_TOP || mouseY > panelBottom) {
            return;
        }
        int yPos = CONTENT_TOP;
        int adjustedMouseY = mouseY + Math.round(this.scrollSubOffset);
        List<String> categoryOrderedList = this.buildCategoryOrderedList();
        int visibleHeight = panelBottom - CONTENT_TOP;
        int currentY = 0;

        for (String item : categoryOrderedList) {
            if (item.startsWith("category:")) {
                currentY += CATEGORY_HEADER_HEIGHT;
                continue;
            }
            String ruleName = item;
            int itemBottom = currentY + ROW_HEIGHT;
            if ((float) itemBottom <= this.scrollPosition || (float) currentY >= this.scrollPosition + (float) visibleHeight) {
                currentY += ROW_HEIGHT;
                continue;
            }
            int rowY = yPos + (currentY - this.scrollOffset * ROW_HEIGHT);
            if (this.isMouseOverRuleName(mouseX, adjustedMouseY, rowY)) {
                List<String> tooltipList = new ArrayList<>();
                tooltipList.add(TextFormatting.YELLOW + ruleName);
                GameRuleMonitorNSetter.GameruleValue defVal = this.defaultRules.get(ruleName);
                if (defVal != null) {
                    tooltipList.add(TextFormatting.GRAY
                            + I18n.format("quantumhue.createworld.customize.custom.default") + " " + defVal.getOptimalValue());
                }
                String tooltip = this.getRuleTooltip(ruleName);
                if (tooltip != null) {
                    tooltipList.add(TextFormatting.WHITE + tooltip);
                }
                this.drawHoveringText(tooltipList, mouseX, mouseY);
            }
            currentY += ROW_HEIGHT;
        }
    }

    private boolean isRuleModified(String ruleName) {
        String currentValue = this.modifiedRules.get(ruleName);
        String originalValue = this.editableRules.get(ruleName);
        if (currentValue == null && originalValue == null) {
            return false;
        }
        if (currentValue == null || originalValue == null) {
            return true;
        }
        return !currentValue.equals(originalValue);
    }

    private boolean isMouseOverRuleName(int mouseX, int mouseY, int rowY) {
        return mouseX >= this.width / 2 - 155 && mouseX <= this.width / 2 + 134
                && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
    }

    @Deprecated
    public static void registerTooltip(String ruleName, String tooltip) {
        GameRuleTooltipRegistry.registerTooltip(ruleName, tooltip);
    }

    @Deprecated
    public static void registerTooltips(Map<String, String> tooltips) {
        GameRuleTooltipRegistry.registerTooltips(tooltips);
    }

    private String getRuleTooltip(String ruleName) {
        return GameRuleTooltipRegistry.getTooltip(ruleName);
    }

    /** 将字符串解析为与参考值匹配的类型 / parse string to a type matching the reference value */
    private Object parseFromString(String text, Object originalValue) {
        if (originalValue instanceof Boolean) {
            return Boolean.parseBoolean(text);
        }
        if (originalValue instanceof Integer) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                LOGGER.error("Because of {}, this type of integer will be ignored", ignored.getMessage());
            }
        }
        if (originalValue instanceof Double) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                LOGGER.error("Because of {}, this type of double will be ignored", ignored.getMessage());
            }
        }
        return text;
    }

    /** 保存：应用到当前世界（若在游戏内）+ 存入待应用规则 + 聊天栏通知 */
    private void saveChanges() {
        LOGGER.info("saveChanges() called");
        Map<String, String> result = new HashMap<>();
        this.changedRules.clear();
        for (Map.Entry<String, String> e : this.modifiedRules.entrySet()) {
            String ruleName = e.getKey();
            String newValue = e.getValue();
            if (ruleName == null || newValue == null) {
                continue;
            }
            result.put(ruleName, newValue);
            String originalValue = this.editableRules.get(ruleName);
            if (originalValue != null && originalValue.equals(newValue)) {
                continue;
            }
            this.changedRules.add(ruleName);
        }

        // 游戏内打开时直接应用到当前世界 / apply to current world if in-game
        WorldClient currentWorld = Minecraft.getMinecraft().world;
        if (currentWorld != null && !this.changedRules.isEmpty()) {
            int appliedCount = 0;
            for (String ruleName : this.changedRules) {
                String newValue = result.get(ruleName);
                if (newValue == null) {
                    continue;
                }
                if (GameRuleMonitorNSetter.setGamerule(currentWorld, ruleName, newValue)) {
                    appliedCount++;
                }
            }
            this.editableRules.putAll(result);
            LOGGER.info("Applied {} game rules to current world.", appliedCount);
        }

        // 同时存入待应用规则（世界创建时生效）/ also stash as pending rules
        try {
            GameRuleApplier.setPendingGameRules(result);
            LOGGER.info("Saved {} modified game rules to pendingGameRules.", result.size());
        } catch (Exception ex) {
            LOGGER.error("Failed to set pending game rules: {}", ex.getMessage());
        }

        // 聊天栏通知 / chat notification
        if (!this.changedRules.isEmpty()) {
            String notificationText = I18n.format("quantumhue.createworld.gamerules.notification.changed");
            String rulesList = String.join(", ", this.changedRules);
            String message = QuantumHueConfig.createWorld.changedRulesInChatHighLighted
                    ? TextFormatting.WHITE + notificationText + TextFormatting.YELLOW + rulesList
                    : TextFormatting.WHITE + notificationText + TextFormatting.WHITE + rulesList;
            if (Minecraft.getMinecraft().ingameGUI != null) {
                Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message));
            }
            LOGGER.info("Changed rules: {}", this.changedRules);
        } else {
            String message = I18n.format("quantumhue.createworld.gamerules.notification.noChanges");
            if (Minecraft.getMinecraft().ingameGUI != null) {
                Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                        new TextComponentString(TextFormatting.WHITE + message));
            }
        }
    }

    private enum ComponentType {
        BOOLEAN_BUTTON,
        TEXT_FIELD
    }

    private static class GuiComponentWrapper {
        public final Object component;
        public final ComponentType type;
        public boolean currentBooleanValue;
        public int globalIndex = 0;
        public String ruleName = null;

        public GuiComponentWrapper(Object component, ComponentType type) {
            this.component = component;
            this.type = type;
        }
    }
}
