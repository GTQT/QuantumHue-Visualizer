package meowmel.quantumhue.tooltips.comparison;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public class EquipmentCompareHandler {

    private static final KeyBinding TOGGLE_KEY = new KeyBinding(
            "quantumhue.key.equipCompare",
            KeyConflictContext.GUI,
            Keyboard.KEY_LSHIFT,
            "key.categories.inventory"
    );

    private static boolean comparisonsActive = false;
    private static boolean isRenderingComparisons = false;

    private final TooltipContentExtractor contentExtractor = new TooltipContentExtractor();
    private final TooltipLayoutManager layoutManager = new TooltipLayoutManager();
    private final TooltipRenderer tooltipRenderer = new TooltipRenderer();
    private final ComparisonLayoutManager comparisonLayoutManager = new ComparisonLayoutManager();
    private final ComparisonBadgeRenderer badgeRenderer = new ComparisonBadgeRenderer();

    // ── 按键绑定注册 ──

    public static void registerKeyBinding() {
        ClientRegistry.registerKeyBinding(TOGGLE_KEY);
    }

    // ── 按键输入处理 ──

    @SubscribeEvent
    public static void onKeyInput(GuiScreenEvent.KeyboardInputEvent event) {
        comparisonsActive = Keyboard.isKeyDown(TOGGLE_KEY.getKeyCode());
    }

    // ── 切换状态 ──

    public static boolean isActive() {
        return comparisonsActive ^ QuantumHueConfig.equipmentComparison.defaultOn;
    }

    // ── 查找已装备对比物品 ──

    /**
     * 查找与悬停物品同槽位的已装备物品列表
     */
    public List<ItemStack> findEquippedItems(ItemStack hoveredStack, EntityPlayer player) {
        List<ItemStack> equippedItems = new ArrayList<>();

        if (hoveredStack.isEmpty() || player == null) {
            return equippedItems;
        }

        // 获取物品的装备槽位
        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(hoveredStack);

        // 获取该槽位的已装备物品
        ItemStack equippedItem = player.getItemStackFromSlot(slot);

        // 检查是否需要对比
        boolean shouldCompare = true;

        // 主手槽位：只对比有耐久度的物品
        if (slot == EntityEquipmentSlot.MAINHAND) {
            if (hoveredStack.getItem().getMaxDamage() <= 0 ||
                    equippedItem.getItem().getMaxDamage() <= 0) {
                shouldCompare = false;
            }
            // 严格模式：只对比相同类型的物品
            else if (QuantumHueConfig.equipmentComparison.strict) {
                if (!hoveredStack.getItem().getClass().equals(
                        equippedItem.getItem().getClass())) {
                    shouldCompare = false;
                }
            }
        }

        if (shouldCompare && !equippedItem.isEmpty()
                && equippedItem.getItem() != Items.AIR
                && !ItemStack.areItemStacksEqual(equippedItem, hoveredStack)) {
            equippedItems.add(equippedItem);
        }

        // 移除黑名单物品
        List<String> blacklist = Arrays.asList(QuantumHueConfig.equipmentComparison.blacklist);
        equippedItems.removeIf(stack ->
                blacklist.contains(stack.getItem().getRegistryName().toString()));

        // 移除与悬停物品相同的物品（再次确保）
        equippedItems.removeIf(stack ->
                ItemStack.areItemStacksEqual(stack, hoveredStack));

        return equippedItems;
    }

    // ── 对比渲染 ──

    /**
     * 渲染装备对比Tooltip。
     *
     * @return true 如果成功渲染了对比
     */
    public boolean renderComparisons(ItemStack hoveredStack,
                                     TooltipContent hoveredContent,
                                     TooltipLayout primaryLayout,
                                     TooltipColors primaryColors,
                                     RenderTooltipEvent.Pre event,
                                     FontRenderer font) {

        // 防护：防止递归
        if (isRenderingComparisons) return false;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        GuiScreen screen = mc.currentScreen;

        if (player == null || screen == null) return false;

        // 检查是否启用且激活
        if (!QuantumHueConfig.equipmentComparison.enabled) return false;
        if (!isActive()) return false;

        // 查找已装备物品
        List<ItemStack> equippedItems = findEquippedItems(hoveredStack, player);
        if (equippedItems.isEmpty()) return false;

        isRenderingComparisons = true;

        try {
            // 为每个已装备物品构建Tooltip内容
            List<TooltipContent> comparisonContents = new ArrayList<>();
            List<TooltipColors> comparisonColorsList = new ArrayList<>();
            List<TooltipLayout> comparisonLayouts = new ArrayList<>();

            for (ItemStack equippedStack : equippedItems) {
                // 获取Tooltip文本行
                List<String> rawLines = screen.getItemToolTip(equippedStack);

                // 提取内容
                TooltipContent content = contentExtractor.extractTooltipContent(rawLines, equippedStack);
                List<String> wrappedLines = TooltipUtils.wrapTooltipText(
                        content.remainingLines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);
                content.currentPageLines = wrappedLines;
                content.needsPagination = false;

                // 获取颜色
                TooltipColors colors = TooltipColorHelper.getTooltipColors(equippedStack);

                // 计算独立布局（位置会被后续调整）
                TooltipLayout layout = layoutManager.calculateLayout(content, event);

                comparisonContents.add(content);
                comparisonColorsList.add(colors);
                comparisonLayouts.add(layout);
            }

            // 计算对齐后的布局
            List<TooltipLayout> positionedLayouts = comparisonLayoutManager.positionAll(
                    primaryLayout, comparisonLayouts,
                    event.getX(), event.getY(),
                    event.getScreenWidth(), event.getScreenHeight());

            // 渲染每个对比Tooltip
            for (int i = 0; i < positionedLayouts.size(); i++) {
                TooltipLayout layout = positionedLayouts.get(i);
                TooltipContent content = comparisonContents.get(i);
                TooltipColors colors = comparisonColorsList.get(i);
                ItemStack equippedStack = equippedItems.get(i);

                GlStateManager.pushMatrix();
                GLStateHelper.setupGLState();

                // 绘制徽章
                badgeRenderer.drawBadge(layout, font);

                // 绘制Tooltip
                tooltipRenderer.drawTooltipBackground(layout, colors, content);
                tooltipRenderer.drawItemIcon(equippedStack, layout.iconX, layout.iconY);
                tooltipRenderer.drawTooltipText(content, layout, font);

                GLStateHelper.restoreGLState();
                GlStateManager.popMatrix();
            }

            return true;
        } finally {
            isRenderingComparisons = false;
        }
    }
}
