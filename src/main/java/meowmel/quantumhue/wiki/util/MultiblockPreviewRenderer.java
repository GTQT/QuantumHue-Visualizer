package meowmel.quantumhue.wiki.util;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MBPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GregFakePlayer;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.scene.FBOWorldSceneRenderer;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.BlockRenderer;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Translation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多方块 3D 预览渲染器 — 完整版。
 *
 * <p>移植自 GregTech JEI {@code MultiblockInfoRecipeWrapper}，提供：</p>
 * <ul>
 *   <li>FBO 离屏渲染 + 脏标记缓存</li>
 *   <li>层级切换（逐层查看/全部显示）</li>
 *   <li>结构通道滑条（参数化多方块）</li>
 *   <li>右键点选方块 → 候选方块列表 + 3D 循环动画</li>
 *   <li>方块悬停/选中覆盖层高亮</li>
 *   <li>左侧部件清单（控制器→TE→blockID→数量排序）</li>
 *   <li>多方块名称覆盖层</li>
 * </ul>
 *
 * <p>在 Wiki 页面中嵌入使用：</p>
 * <pre>
 * MultiblockPreviewRenderer preview = new MultiblockPreviewRenderer(mb.getController());
 * // 每帧: preview.render(x, y, w, h, mouseX, mouseY);
 * // 点击: preview.handleClick(mouseX, mouseY, button);
 * // 滚轮: preview.handleScroll(delta);
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class MultiblockPreviewRenderer {

    // ──────────────────── 布局常量 ────────────────────
    private static final int ICON_SIZE = 20;
    private static final int RIGHT_PADDING = 5;
    private static final int INFO_ICON_Y = 22;
    private static final int LAYER_BUTTON_Y = INFO_ICON_Y + ICON_SIZE + 2;
    private static final int CANDIDATE_SLOT_START_Y = LAYER_BUTTON_Y + ICON_SIZE + 2;
    private static final int MAX_CANDIDATES = 6;
    private static final int PARTS_PER_ROW = 10;
    private static final int SLOT_SIZE = 18;
    private static final long CANDIDATE_CYCLE_INTERVAL_MS = 1000L;

    // ──────────────────── 静态工具提示状态 ────────────────────
    private static ItemStack staticTooltipStack;
    private static long staticLastRenderTime;

    // ──────────────────── 核心数据 ────────────────────
    private final MultiblockControllerBase controller;
    private final List<StructureChannel> supportedChannels;
    private final int[][] channelRanges;            // [channelIdx][0=min, 1=max]
    private final Map<String, Integer> channelValues = new HashMap<>();
    private MBPattern[] patterns;

    // ──────────────────── 相机 ────────────────────
    private Vector3f center;
    private float rotationYaw = 20f;
    private float rotationPitch = 50f;
    private float zoom;

    // ──────────────────── 交互状态 ────────────────────
    private int lastMouseX, lastMouseY;
    private int layerIndex = -1;                    // -1 = 全部层
    private BlockPos selected;
    private final List<TraceabilityPredicate.SimplePredicate> predicates = new ArrayList<>();
    private TraceabilityPredicate father;
    private int candidateCycleIndex;
    private long lastCandidateCycleTime;
    private boolean drawInfoIcon;

    // ──────────────────── 工具提示 ────────────────────
    private ItemStack tooltipBlockStack;
    private List<String> predicateTips;
    private long lastRenderTime;

    // ──────────────────── 滑条拖拽状态 ────────────────────
    private int draggingSliderIndex = -1;

    // ──────────────────── 帧计数器（拖拽节流） ────────────────────
    private int frameCounter;

    // ──────────────────── 预览区域（由 render() 更新） ────────────────────
    private int previewX, previewY, previewW, previewH;

    // ──────────────────── 物品槽位追踪（用于 tooltip） ────────────────────
    private final List<SlotEntry> slotEntries = new ArrayList<>();

    private static class SlotEntry {
        final int x, y, w, h;
        final ItemStack stack;
        final boolean isCandidate;      // true = 候选方块槽位（显示谓词提示）
        final int predicateIndex;       // predicates 列表中的索引

        SlotEntry(int x, int y, int w, int h, ItemStack stack, boolean isCandidate, int predicateIndex) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.stack = stack; this.isCandidate = isCandidate;
            this.predicateIndex = predicateIndex;
        }
    }

    // ════════════════════════════════════════════════
    //  构造
    // ════════════════════════════════════════════════

    @SuppressWarnings("NewExpressionSideOnly")
    public MultiblockPreviewRenderer(MultiblockControllerBase controller) {
        this.controller = controller;
        this.supportedChannels = controller.getSupportedChannels();
        this.channelRanges = new int[supportedChannels.size()][];
        for (int i = 0; i < supportedChannels.size(); i++) {
            channelRanges[i] = controller.getChannelRange(supportedChannels.get(i));
        }

        Set<ItemStack> drops = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        List<MultiblockShapeInfo> shapes = controller.getMatchingShapes(channelValues);
        if (shapes.isEmpty()) {
            this.patterns = new MBPattern[0];
            return;
        }
        this.patterns = shapes.stream()
                .map(it -> initializePattern(it, drops))
                .toArray(MBPattern[]::new);
        GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);

        // 初始相机
        TrackedDummyWorld world = (TrackedDummyWorld) getCurrentRenderer().world;
        Vector3f size = world.getSize();
        float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
        this.zoom = (float) (3.5 * Math.sqrt(max));
        resetCenter(world);
    }

    // ════════════════════════════════════════════════
    //  公开方法（Wiki 调用）
    // ════════════════════════════════════════════════

    public WorldSceneRenderer getCurrentRenderer() {
        if (patterns == null || patterns.length == 0) return null;
        return patterns[0].getSceneRenderer();
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    /**
     * @return 当前悬停方块的 ItemStack（用于外部 tooltip 渲染）
     */
    public static ItemStack getHoveredItemStack() {
        if (staticLastRenderTime > System.currentTimeMillis() - 100) {
            return staticTooltipStack;
        }
        return null;
    }

    /**
     * @return 当前悬停方块的谓词提示
     */
    public List<String> getPredicateTips() {
        return predicateTips;
    }

    /**
     * @return 预览区域坐标 {x, y, w, h}
     */
    public int[] getPreviewBounds() {
        return new int[] { previewX, previewY, previewW, previewH };
    }

    /**
     * 查询鼠标下方槽位的物品（用于 2D 物品 tooltip）。
     *
     * @return 鼠标悬停的物品，或 null
     */
    public ItemStack getSlotStackAt(int mouseX, int mouseY) {
        for (SlotEntry e : slotEntries) {
            if (mouseX >= e.x && mouseX < e.x + e.w && mouseY >= e.y && mouseY < e.y + e.h) {
                return e.stack;
            }
        }
        return null;
    }

    /**
     * 查询鼠标下方候选槽位的谓词提示。
     * 仅在鼠标悬浮在候选方块槽位上时返回非空。
     */
    public List<String> getSlotPredicateTips(int mouseX, int mouseY) {
        for (SlotEntry e : slotEntries) {
            if (e.isCandidate && mouseX >= e.x && mouseX < e.x + e.w
                    && mouseY >= e.y && mouseY < e.y + e.h) {
                if (e.predicateIndex >= 0 && e.predicateIndex < predicates.size()
                        && father != null) {
                    return predicates.get(e.predicateIndex).getToolTips(father);
                }
            }
        }
        return null;
    }

    /**
     * 主渲染入口。每帧在 GL 线程调用。
     */
    public void render(int x, int y, int w, int h, int mouseX, int mouseY) {
        if (patterns == null || patterns.length == 0) return;
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) return;

        this.previewX = x;
        this.previewY = y;
        this.previewW = w;
        this.previewH = h;
        this.slotEntries.clear();

        // ── 3D 场景 ──
        int sceneH = h - (supportedChannels.size() * 16 + 10);

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableLighting();
            RenderHelper.enableStandardItemLighting();
            renderer.render(x, y, w, sceneH, mouseX, mouseY);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }

        // ── 覆盖层 ──
        drawMultiblockName(x, w);
        drawInfoIcon(x + w);
        drawLayerButton(x + w);

        // ── 左侧部件图标 ──
        List<ItemStack> parts = patterns[0].getParts();
        int partsToShow = Math.min(parts.size(), PARTS_PER_ROW * 2);
        for (int i = 0; i < partsToShow; i++) {
            int row = i / PARTS_PER_ROW;
            int col = i % PARTS_PER_ROW;
            int ix = x + col * SLOT_SIZE + 2;
            int iy = y + row * SLOT_SIZE + 2;
            drawItemSlot(ix, iy, parts.get(i), false, -1);
        }

        // ── 右侧候选方块图标 ──
        for (int i = 0; i < predicates.size() && i < MAX_CANDIDATES; i++) {
            int ix = x + w - RIGHT_PADDING - SLOT_SIZE;
            int iy = y + i * SLOT_SIZE + CANDIDATE_SLOT_START_Y;
            TraceabilityPredicate.SimplePredicate pred = predicates.get(i);
            if (pred.getCandidates() != null && !pred.getCandidates().isEmpty()) {
                drawItemSlot(ix, iy, pred.getCandidates().get(0), true, i);
            }
        }

        GlStateManager.color(1f, 1f, 1f, 1f);

        // ── 通道滑条 ──
        if (!supportedChannels.isEmpty()) {
            drawChannelSliders(x, y, w, h);
        }

        // ── 交互：拖拽旋转/平移 ──
        boolean leftDown = org.lwjgl.input.Mouse.isButtonDown(0);
        boolean rightDown = org.lwjgl.input.Mouse.isButtonDown(1);
        boolean insideScene = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + sceneH;

        boolean cameraModified = false;
        boolean isDragging = false;
        if (insideScene && rightDown) {
            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                cameraModified = panCamera(dx, dy);
                isDragging = true;
            }
        } else if (insideScene && leftDown) {
            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                rotationYaw += dx;
                rotationYaw %= 360f;
                if (rotationYaw < 0) rotationYaw += 360f;
                rotationPitch = MathHelper.clamp(rotationPitch + dy, -89.9f, 89.9f);
                cameraModified = true;
                isDragging = true;
            }
        }
        frameCounter++;
        if (cameraModified) {
            // 拖拽时隔帧（每 2 帧）应用相机更新，避免每帧清空 FBO 造成闪烁
            // 非拖拽时（缩放等）始终立即应用
            boolean applyNow = !isDragging || frameCounter % 2 == 0;
            if (applyNow) {
                renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
            }
        }

        // ── 通道滑条拖拽更新 ──
        if (leftDown && draggingSliderIndex >= 0) {
            int sliderStartY = previewY + previewH - (supportedChannels.size() * 16 + 6);
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int sliderX = previewX + 5;
            int sliderWidth = previewW - 10;
            int max = channelRanges[draggingSliderIndex][1];
            float ratio = MathHelper.clamp((float) (mouseX - sliderX) / sliderWidth, 0, 1);
            int newValue = Math.round(ratio * max);
            setChannelValue(draggingSliderIndex, newValue, false); // 拖拽中不重建，只更新滑条显示
        }
        if (!leftDown && draggingSliderIndex >= 0) {
            regeneratePatterns(); // 松手时执行完整重建
            draggingSliderIndex = -1;
        }

        // ── 悬停检测（仅当未拖拽时） ──
        tooltipBlockStack = null;
        predicateTips = null;
        if (!leftDown && !rightDown && insideScene) {
            RayTraceResult trace = renderer.getLastTraceResult();
            if (trace != null && !renderer.world.isAirBlock(trace.getBlockPos())) {
                IBlockState state = renderer.world.getBlockState(trace.getBlockPos());
                ItemStack stack = state.getBlock().getPickBlock(state, trace, renderer.world,
                        trace.getBlockPos(), Minecraft.getMinecraft().player);

                // 谓词提示
                TraceabilityPredicate preds = patterns[0].getPredicateMap().get(trace.getBlockPos());
                if (preds != null) {
                    BlockWorldState ws = new BlockWorldState();
                    ws.update(renderer.world, trace.getBlockPos(), new PatternMatchContext(),
                            new HashMap<>(), new HashMap<>(), preds);
                    for (TraceabilityPredicate.SimplePredicate common : preds.common) {
                        if (common.test(ws)) {
                            predicateTips = common.getToolTips(preds);
                            break;
                        }
                    }
                    if (predicateTips == null) {
                        for (TraceabilityPredicate.SimplePredicate limit : preds.limited) {
                            if (limit.test(ws)) {
                                predicateTips = limit.getToolTips(preds);
                                break;
                            }
                        }
                    }
                }
                if (!stack.isEmpty()) {
                    tooltipBlockStack = stack;
                }
            }
        }

        // ── 更新静态工具提示状态 ──
        staticTooltipStack = tooltipBlockStack;
        staticLastRenderTime = System.currentTimeMillis();
        lastRenderTime = staticLastRenderTime;

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
    }

    /**
     * 处理鼠标点击。返回 true 表示事件已消费。
     */
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (patterns == null || patterns.length == 0) return false;
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) return false;

        int sceneH = previewH - (supportedChannels.size() * 16 + 10);

        // ── 通道滑条点击 ──
        if (mouseButton == 0 && !supportedChannels.isEmpty()) {
            int sliderStartY = previewY + previewH - (supportedChannels.size() * 16 + 6);
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int sliderX = previewX + 5;
            int sliderWidth = previewW - 10;
            for (int i = 0; i < supportedChannels.size(); i++) {
                int rowY = sliderStartY + i * 16;
                int trackY = rowY + fr.FONT_HEIGHT + 1;
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth
                        && mouseY >= trackY - 3 && mouseY <= trackY + 7) {
                    draggingSliderIndex = i;
                    int max = channelRanges[i][1];
                    float ratio = (float) (mouseX - sliderX) / sliderWidth;
                    int newValue = Math.round(ratio * max);
                    setChannelValue(i, newValue, true);
                    return true;
                }
            }
        }

        // ── 层级按钮 ──
        int btnX = previewX + previewW - (ICON_SIZE + RIGHT_PADDING);
        int btnY = previewY + LAYER_BUTTON_Y;
        if (mouseButton == 0 && mouseX >= btnX && mouseX <= btnX + ICON_SIZE
                && mouseY >= btnY && mouseY <= btnY + ICON_SIZE) {
            toggleNextLayer();
            return true;
        }

        // ── 右键：方块点选 / 取消 ──
        if (mouseButton == 1) {
            int sceneX = previewX, sceneY = previewY;
            if (mouseX < sceneX || mouseX > sceneX + previewW
                    || mouseY < sceneY || mouseY > sceneY + sceneH) {
                return false;
            }
            if (renderer.getLastTraceResult() == null) {
                if (selected != null) {
                    selected = null;
                    predicates.clear();
                    father = null;
                    candidateCycleIndex = 0;
                    lastCandidateCycleTime = 0L;
                    if (getCurrentRenderer() instanceof FBOWorldSceneRenderer fbo) {
                        fbo.markFBODirty();
                    }
                    return true;
                }
                return false;
            }
            BlockPos pos = renderer.getLastTraceResult().getBlockPos();
            if (!Objects.equals(this.selected, pos)) {
                predicates.clear();
                father = null;
                this.selected = pos;
                candidateCycleIndex = 0;
                lastCandidateCycleTime = 0L;
                TraceabilityPredicate predicate = patterns[0].getPredicateMap().get(selected);
                if (predicate != null) {
                    predicates.addAll(predicate.common);
                    predicates.addAll(predicate.limited);
                    predicates.removeIf(p -> p.candidates == null);
                    father = predicate;
                }
                if (getCurrentRenderer() instanceof FBOWorldSceneRenderer fbo) {
                    fbo.markFBODirty();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 处理鼠标滚轮。返回 true 表示事件已消费。
     */
    public boolean handleScroll(int delta, int mouseX, int mouseY) {
        if (patterns == null || patterns.length == 0) return false;
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) return false;

        int sceneH = previewH - (supportedChannels.size() * 16 + 10);
        if (mouseX >= previewX && mouseX < previewX + previewW
                && mouseY >= previewY && mouseY < previewY + sceneH) {
            zoom = MathHelper.clamp(zoom + (delta < 0 ? 0.5f : -0.5f), 3, 999);
            renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
            return true;
        }
        return false;
    }

    /**
     * 判断鼠标是否在 3D 场景区域内（用于滚轮缩放）。
     */
    public boolean isMouseOverPreview(int mouseX, int mouseY) {
        int sceneH = previewH - (supportedChannels.size() * 16 + 10);
        return mouseX >= previewX && mouseX < previewX + previewW
                && mouseY >= previewY && mouseY < previewY + sceneH;
    }

    /**
     * 判断鼠标是否在整个预览区域内（含通道滑条等底部 UI）。
     */
    public boolean isMouseOverFullPreview(int mouseX, int mouseY) {
        return mouseX >= previewX && mouseX < previewX + previewW
                && mouseY >= previewY && mouseY < previewY + previewH;
    }

    // ════════════════════════════════════════════════
    //  层级管理
    // ════════════════════════════════════════════════

    private void toggleNextLayer() {
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) return;
        int height = (int) ((TrackedDummyWorld) renderer.world).getSize().getY() - 1;
        if (++layerIndex > height) {
            layerIndex = -1;
        }
        setNextLayer(layerIndex);
    }

    private void setNextLayer(int newLayer) {
        this.layerIndex = newLayer;
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) return;
        TrackedDummyWorld world = (TrackedDummyWorld) renderer.world;
        resetCenter(world);
        renderer.disableClipPlanes();
        renderer.renderedBlocks.clear();
        int minY = (int) world.getMinPos().getY();
        Collection<BlockPos> renderBlocks;
        if (newLayer == -1) {
            renderBlocks = world.renderedBlocks;
        } else {
            renderBlocks = world.renderedBlocks.stream()
                    .filter(pos -> pos.getY() - minY == newLayer)
                    .collect(Collectors.toSet());
        }
        renderer.addRenderedBlocks(renderBlocks);
        if (renderer instanceof FBOWorldSceneRenderer fbo) {
            fbo.markFBODirty();
        }
    }

    private void resetCenter(TrackedDummyWorld world) {
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        if (layerIndex != -1) {
            center.y = minPos.y + layerIndex + 0.5f;
        }
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer != null) {
            renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
        }
    }

    // ════════════════════════════════════════════════
    //  通道管理
    // ════════════════════════════════════════════════

    private void setChannelValue(int channelIndex, int value, boolean regenerate) {
        if (channelIndex < 0 || channelIndex >= supportedChannels.size()) return;
        String channelName = supportedChannels.get(channelIndex).getName();
        int max = channelRanges[channelIndex][1];
        int clamped = Math.max(0, Math.min(max, value));
        if (clamped == 0) {
            channelValues.remove(channelName);
        } else {
            channelValues.put(channelName, clamped);
        }
        if (regenerate) {
            regeneratePatterns();
        }
    }

    private void regeneratePatterns() {
        Set<ItemStack> drops = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        this.patterns = controller.getMatchingShapes(channelValues).stream()
                .map(it -> initializePattern(it, drops))
                .toArray(MBPattern[]::new);
        GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);
        setNextLayer(-1);
        getCurrentRenderer().setCameraLookAt(center, zoom,
                Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
        if (selected != null) {
            selected = null;
            predicates.clear();
            father = null;
        }
        if (getCurrentRenderer() instanceof FBOWorldSceneRenderer fbo) {
            fbo.markFBODirty();
        }
    }

    private boolean panCamera(float dx, float dy) {
        final float panSensitivity = 0.08f;
        double yawRad = Math.toRadians(rotationYaw);
        double pitchRad = Math.toRadians(rotationPitch);
        Vec3d forward = new Vec3d(
                Math.cos(pitchRad) * Math.sin(yawRad),
                Math.sin(pitchRad),
                Math.cos(pitchRad) * Math.cos(yawRad)).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        Vec3d up = right.crossProduct(forward).normalize();
        center.x += (float) (-dx * right.x * panSensitivity);
        center.y += (float) (-dx * right.y * panSensitivity);
        center.z += (float) (-dx * right.z * panSensitivity);
        center.x += (float) (-dy * up.x * panSensitivity);
        center.y += (float) (-dy * up.y * panSensitivity);
        center.z += (float) (-dy * up.z * panSensitivity);
        return true;
    }

    // ════════════════════════════════════════════════
    //  绘制覆盖层
    // ════════════════════════════════════════════════

    private void drawMultiblockName(int x, int w) {
        String name = I18n.format(controller.getMetaFullName());
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = fr.listFormattedStringToWidth(name, w - 10);
        int textY = previewY + 4;
        for (String line : lines) {
            fr.drawString(line, x + (w - fr.getStringWidth(line)) / 2, textY,
                    ConfigHolder.client.multiblockPreviewFontColor);
            textY += fr.FONT_HEIGHT;
        }
    }

    private void drawInfoIcon(int right) {
        int ix = right - (ICON_SIZE + RIGHT_PADDING);
        int iy = previewY + INFO_ICON_Y;
        // 简单背景框 + "?" 文字
        Gui.drawRect(ix, iy, ix + ICON_SIZE, iy + ICON_SIZE, 0x88000000);
        Gui.drawRect(ix, iy, ix + ICON_SIZE, iy + 1, 0xFF666688);
        Gui.drawRect(ix, iy + ICON_SIZE - 1, ix + ICON_SIZE, iy + ICON_SIZE, 0xFF666688);
        Gui.drawRect(ix, iy, ix + 1, iy + ICON_SIZE, 0xFF666688);
        Gui.drawRect(ix + ICON_SIZE - 1, iy, ix + ICON_SIZE, iy + ICON_SIZE, 0xFF666688);
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow("?", ix + 7, iy + 6, 0xFFCCCCFF);
        int mouseX = lastMouseX, mouseY = lastMouseY;
        drawInfoIcon = mouseX >= ix && mouseX <= ix + ICON_SIZE
                && mouseY >= iy && mouseY <= iy + ICON_SIZE;
    }

    private void drawLayerButton(int right) {
        int bx = right - (ICON_SIZE + RIGHT_PADDING);
        int by = previewY + LAYER_BUTTON_Y;
        boolean hover = lastMouseX >= bx && lastMouseX <= bx + ICON_SIZE
                && lastMouseY >= by && lastMouseY <= by + ICON_SIZE;
        int bg = hover ? 0xFF333355 : 0xFF1A1A2E;
        Gui.drawRect(bx, by, bx + ICON_SIZE, by + ICON_SIZE, bg);
        Gui.drawRect(bx, by, bx + ICON_SIZE, by + 1, 0xFF444466);
        Gui.drawRect(bx, by + ICON_SIZE - 1, bx + ICON_SIZE, by + ICON_SIZE, 0xFF444466);
        Gui.drawRect(bx, by, bx + 1, by + ICON_SIZE, 0xFF444466);
        Gui.drawRect(bx + ICON_SIZE - 1, by, bx + ICON_SIZE, by + ICON_SIZE, 0xFF444466);
        String label = "L:" + (layerIndex == -1 ? "A" : Integer.toString(layerIndex + 1));
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(label,
                bx + (ICON_SIZE - fr.getStringWidth(label)) / 2,
                by + 6, 0xFFCCCCCC);
    }

    private void drawItemSlot(int x, int y, ItemStack stack, boolean isCandidate, int predicateIndex) {
        if (stack == null || stack.isEmpty()) return;
        Gui.drawRect(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x88111122);
        Gui.drawRect(x, y, x + SLOT_SIZE, y + 1, 0xFF333355);
        Gui.drawRect(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF333355);
        Gui.drawRect(x, y, x + 1, y + SLOT_SIZE, 0xFF333355);
        Gui.drawRect(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF333355);

        RenderItem ri = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        ri.renderItemAndEffectIntoGUI(stack, x + 1, y + 1);
        ri.renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, stack, x + 1, y + 1, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();

        // 记录槽位用于 tooltip 查询
        slotEntries.add(new SlotEntry(x, y, SLOT_SIZE, SLOT_SIZE, stack, isCandidate, predicateIndex));
    }

    private void drawChannelSliders(int x, int y, int w, int h) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int sliderStartY = y + h - (supportedChannels.size() * 16 + 6);
        int sliderX = x + 5;
        int sliderWidth = w - 10;

        for (int i = 0; i < supportedChannels.size(); i++) {
            StructureChannel channel = supportedChannels.get(i);
            String channelName = channel.getName();
            int max = channelRanges[i][1];
            int value = channelValues.getOrDefault(channelName, 0);
            int rowY = sliderStartY + i * 16;

            // 标签
            String label = I18n.format(channel.getDefaultTooltip());
            fr.drawString(label, sliderX, rowY, 0xFF404040);

            // 滑条轨道
            int trackY = rowY + fr.FONT_HEIGHT + 1;
            int trackHeight = 4;
            Gui.drawRect(sliderX, trackY, sliderX + sliderWidth, trackY + trackHeight, 0xFFAAAAAA);

            // 滑条手柄
            int range = Math.max(1, max);
            float ratio = (float) value / range;
            int handleX = sliderX + (int) (ratio * (sliderWidth - 4));
            Gui.drawRect(handleX, trackY - 1, handleX + 4, trackY + trackHeight + 1, 0xFF4488CC);

            // 当前值文字
            String valueText = value == 0 ? "Auto" : String.valueOf(value);
            ItemStack indicator = channel.getIndicatorItem(value);
            if (!indicator.isEmpty() && value > 0) {
                valueText = indicator.getDisplayName();
            }
            fr.drawString(valueText,
                    sliderX + sliderWidth - fr.getStringWidth(valueText),
                    rowY, 0xFF222222);
        }
    }

    // ════════════════════════════════════════════════
    //  Pattern 初始化（移植自 JEI）
    // ════════════════════════════════════════════════

    @SuppressWarnings("NewExpressionSideOnly")
    private MBPattern initializePattern(MultiblockShapeInfo shapeInfo, Set<ItemStack> parts) {
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        MultiblockControllerBase controllerBase = null;
        BlockPos controllerBlockPos = null;
        MultiblockControllerBase controllerClassFallback = null;
        BlockPos controllerClassFallbackPos = null;

        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    if (column[z].getTileEntity() instanceof IGregTechTileEntity &&
                            ((IGregTechTileEntity) column[z].getTileEntity())
                                    .getMetaTileEntity() instanceof MultiblockControllerBase) {
                        MultiblockControllerBase previewController =
                                (MultiblockControllerBase) ((IGregTechTileEntity) column[z].getTileEntity())
                                        .getMetaTileEntity();
                        BlockPos pos = new BlockPos(x, y, z);
                        if (controllerBlockPos == null &&
                                controller.metaTileEntityId.equals(previewController.metaTileEntityId)) {
                            controllerBase = previewController;
                            controllerBlockPos = pos;
                        } else if (controllerClassFallbackPos == null &&
                                controller.getClass().isInstance(previewController)) {
                            controllerClassFallback = previewController;
                            controllerClassFallbackPos = pos;
                        }
                    }
                    blockMap.put(new BlockPos(x, y, z), column[z]);
                }
            }
        }
        if (controllerBlockPos == null) {
            controllerBase = controllerClassFallback;
            controllerBlockPos = controllerClassFallbackPos;
        }

        // selfPredicateByClass: 替换为正确的控制器实例
        if (controllerBlockPos != null && controllerBase != null) {
            if (!controller.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
                replaceControllerInPreview(blockMap, controllerBlockPos, controllerBase);
            }
        }

        TrackedDummyWorld world = new TrackedDummyWorld();
        FBOWorldSceneRenderer worldSceneRenderer = new FBOWorldSceneRenderer(world, 512, 512);
        worldSceneRenderer.setClearColor(ConfigHolder.client.multiblockPreviewColor);
        world.addBlocks(blockMap);

        // ── 性能配置 ──
        int totalBlocks = world.renderedBlocks.size();
        if (totalBlocks > 50) {
            worldSceneRenderer.setCullInternalBlocks(true);
        }
        worldSceneRenderer.addRenderedBlocks(world.renderedBlocks);

        int blockCount = worldSceneRenderer.renderedBlocks.size();
        if (blockCount > 100) {
            worldSceneRenderer.setTileEntityFilter(te ->
                    te instanceof IGregTechTileEntity gtte &&
                            gtte.getMetaTileEntity() instanceof MultiblockControllerBase);
            worldSceneRenderer.setHitTestInterval(5);
        } else if (blockCount > 50) {
            worldSceneRenderer.setMaxTileEntityRenderers(8);
            worldSceneRenderer.setMaxTileEntityRenderDistance(16.0);
            worldSceneRenderer.setHitTestInterval(3);
        }

        worldSceneRenderer.setOnLookingAt(ray -> {});
        worldSceneRenderer.setAfterWorldRender(renderer -> {
            BlockPos look = worldSceneRenderer.getLastTraceResult() == null ? null :
                    worldSceneRenderer.getLastTraceResult().getBlockPos();
            if (look != null && look.equals(selected)) {
                renderBlockOverLay(selected, 200, 75, 75);
            } else {
                renderBlockOverLay(look, 150, 150, 150);
                renderBlockOverLay(selected, 255, 0, 0);
            }
            // 候选方块循环
            if (selected != null && !predicates.isEmpty()) {
                renderCandidateBlockAtPosition(world, selected);
            }
        });
        world.updateEntities();
        world.setRenderFilter(worldSceneRenderer.renderedBlocks::contains);

        // ── 构建 predicate 映射 ──
        Map<BlockPos, TraceabilityPredicate> predicateMap = buildPredicateMap(
                controllerBase, controllerBlockPos, blockMap);

        // ── 收集并排序部件 ──
        List<ItemStack> sortedParts = gatherStructureBlocks(worldSceneRenderer.world, blockMap, parts).stream()
                .sorted((one, two) -> {
                    if (one.isController) return -1;
                    if (two.isController) return +1;
                    if (one.isTile && !two.isTile) return -1;
                    if (two.isTile && !one.isTile) return +1;
                    if (one.blockId != two.blockId) return two.blockId - one.blockId;
                    return two.amount - one.amount;
                }).map(PartInfo::getItemStack).collect(Collectors.toList());

        return new MBPattern(worldSceneRenderer, sortedParts, predicateMap);
    }

    // ════════════════════════════════════════════════
    //  Predicate 映射构建
    // ════════════════════════════════════════════════

    private Map<BlockPos, TraceabilityPredicate> buildPredicateMap(
            MultiblockControllerBase controllerBase,
            BlockPos controllerBlockPos,
            Map<BlockPos, BlockInfo> blockMap) {

        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();

        if (controllerBase != null) {
            MultiblockState state = controllerBase.getMultiblockState();
            if (state == null) {
                controllerBase.reinitializeStructurePattern();
                state = controllerBase.getMultiblockState();
            }
            if (state != null) {
                // 尝试从缓存构建
                state.cache.forEach((pos, blockInfo) -> predicateMap
                        .put(BlockPos.fromLong(pos), (TraceabilityPredicate) blockInfo.getInfo()));
            }

            // 缓存不可用 → 从 pattern template 构建（wiki 预览总是走此路径）
            if (predicateMap.isEmpty() && state != null) {
                BlockPatternTemplate tmpl = state.getTemplate();
                if (tmpl != null) {
                    TraceabilityPredicate[][][] blockMatches = tmpl.getBlockMatches();
                    RelativeDirection[] sDir = tmpl.getStructureDir();
                    int[] centerOff = tmpl.getCenterOffset();

                    BlockPos cPos = controllerBlockPos != null ? controllerBlockPos : BlockPos.ORIGIN;
                    BlockPos controllerPreviewPos = RelativeDirection.setActualRelativeOffset(
                            centerOff[0], centerOff[1], centerOff[3],
                            EnumFacing.NORTH, EnumFacing.UP, false, sDir);
                    BlockPos offset = cPos.subtract(controllerPreviewPos);

                    for (int iz = 0; iz < tmpl.getFingerLength(); iz++) {
                        for (int iy = 0; iy < tmpl.getThumbLength(); iy++) {
                            for (int ix = 0; ix < tmpl.getPalmLength(); ix++) {
                                TraceabilityPredicate pred = blockMatches[iz][iy][ix];
                                if (pred == null || pred == TraceabilityPredicate.ANY) continue;
                                BlockPos previewPos = RelativeDirection.setActualRelativeOffset(
                                        ix, iy, iz, EnumFacing.NORTH, EnumFacing.UP, false, sDir);
                                BlockPos blockMapPos = previewPos.add(offset);
                                if (blockMap.containsKey(blockMapPos)) {
                                    predicateMap.put(blockMapPos, pred);
                                }
                            }
                        }
                    }
                }
            }
        }
        return predicateMap;
    }

    // ════════════════════════════════════════════════
    //  替换预览中的控制器
    // ════════════════════════════════════════════════

    private void replaceControllerInPreview(
            Map<BlockPos, BlockInfo> blockMap,
            BlockPos controllerPos,
            MultiblockControllerBase previewController) {
        MetaTileEntity copy = controller.createMetaTileEntity(null);
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(copy);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(previewController.getFrontFacing());
        blockMap.put(controllerPos, new BlockInfo(
                copy.getBlock().getDefaultState(), holder));
    }

    // ════════════════════════════════════════════════
    //  方块覆盖层渲染（移植自 JEI）
    // ════════════════════════════════════════════════

    @SideOnly(Side.CLIENT)
    private static void renderBlockOverLay(BlockPos pos, int r, int g, int b) {
        if (pos == null) return;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        GlStateManager.translate((pos.getX() + 0.5), (pos.getY() + 0.5), (pos.getZ() + 0.5));
        GlStateManager.scale(1.01, 1.01, 1.01);

        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.disableTexture2D();
        CCRenderState renderState = CCRenderState.instance();
        renderState.startDrawing(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR,
                tessellator.getBuffer());
        ColourMultiplier multiplier = new ColourMultiplier(0);
        renderState.setPipeline(new Translation(-0.5, -0.5, -0.5), multiplier);
        BlockRenderer.BlockFace blockFace = new BlockRenderer.BlockFace();
        renderState.setModel(blockFace);
        for (EnumFacing renderSide : EnumFacing.VALUES) {
            multiplier.colour = RenderUtil.packColor(r, g, b, 255);
            blockFace.loadCuboidFace(Cuboid6.full, renderSide.getIndex());
            renderState.render();
        }
        renderState.draw();
        GlStateManager.scale(1 / 1.01, 1 / 1.01, 1 / 1.01);
        GlStateManager.translate(-(pos.getX() + 0.5), -(pos.getY() + 0.5), -(pos.getZ() + 0.5));
        GlStateManager.enableTexture2D();

        GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);
    }

    // ════════════════════════════════════════════════
    //  候选方块循环渲染（移植自 JEI）
    // ════════════════════════════════════════════════

    @SideOnly(Side.CLIENT)
    private void renderCandidateBlockAtPosition(World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        if (now - lastCandidateCycleTime >= CANDIDATE_CYCLE_INTERVAL_MS) {
            lastCandidateCycleTime = now;
            candidateCycleIndex++;
        }

        // 收集所有候选 BlockInfo
        List<BlockInfo> allCandidateBlocks = new ArrayList<>();
        for (TraceabilityPredicate.SimplePredicate predicate : predicates) {
            if (predicate.candidates != null) {
                BlockInfo[] infos = predicate.candidates.get();
                for (BlockInfo info : infos) {
                    if (info.getBlockState().getBlock() != net.minecraft.init.Blocks.AIR) {
                        allCandidateBlocks.add(info);
                    }
                }
            }
        }
        if (allCandidateBlocks.isEmpty()) return;

        int index = candidateCycleIndex % allCandidateBlocks.size();
        BlockInfo candidateInfo = allCandidateBlocks.get(index);
        IBlockState candidateState = candidateInfo.getBlockState();

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);

        Minecraft mc = Minecraft.getMinecraft();
        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(layer);
                if (!candidateState.getBlock().canRenderInLayer(candidateState, layer)) continue;

                int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
                WorldSceneRenderer.setDefaultPassRenderState(pass);

                buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                dispatcher.renderBlock(candidateState, pos, world, buffer);
                Tessellator.getInstance().draw();
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }

        GlStateManager.disableBlend();
    }

    // ════════════════════════════════════════════════
    //  结构方块收集（移植自 JEI）
    // ════════════════════════════════════════════════

    private static Collection<PartInfo> gatherStructureBlocks(World world, Map<BlockPos, BlockInfo> blocks,
                                                              Set<ItemStack> parts) {
        Map<ItemStack, PartInfo> partsMap = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        for (Map.Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            ItemStack stack = ItemStack.EMPTY;

            // 先检查是否是 GT 机器
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof IGregTechTileEntity) {
                MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
                stack = mte.getStackForm();
            }
            if (stack.isEmpty()) {
                stack = block.getPickBlock(state,
                        new RayTraceResult(Vec3d.ZERO, EnumFacing.UP, pos), world, pos,
                        new GregFakePlayer(world));
            }
            if (stack.isEmpty()) {
                stack = GTUtility.toItem(state);
            }
            if (stack.isEmpty()) {
                NonNullList<ItemStack> list = NonNullList.create();
                state.getBlock().getDrops(list, world, pos, state, 0);
                if (!list.isEmpty()) {
                    ItemStack is = list.get(0);
                    if (!is.isEmpty()) {
                        stack = is;
                    }
                }
            }

            if (!stack.isEmpty()) {
                parts.add(stack);

                PartInfo partInfo = partsMap.get(stack);
                if (partInfo == null) {
                    partInfo = new PartInfo(stack, entry.getValue());
                    partsMap.put(stack, partInfo);
                }
                partInfo.amount++;
            }
        }
        return partsMap.values();
    }

    // ════════════════════════════════════════════════
    //  PartInfo（移植自 JEI）
    // ════════════════════════════════════════════════

    private static class PartInfo {

        final ItemStack itemStack;
        final int blockId;
        boolean isController;
        boolean isTile;
        int amount;

        PartInfo(final ItemStack itemStack, final BlockInfo blockInfo) {
            this.itemStack = itemStack;
            this.blockId = Block.getIdFromBlock(blockInfo.getBlockState().getBlock());
            TileEntity tileEntity = blockInfo.getTileEntity();
            if (tileEntity != null) {
                this.isTile = true;
                if (tileEntity instanceof IGregTechTileEntity iGregTechTileEntity) {
                    MetaTileEntity mte = iGregTechTileEntity.getMetaTileEntity();
                    this.isController = mte instanceof MultiblockControllerBase;
                }
            }
        }

        ItemStack getItemStack() {
            ItemStack result = this.itemStack.copy();
            result.setCount(this.amount);
            return result;
        }
    }
}
