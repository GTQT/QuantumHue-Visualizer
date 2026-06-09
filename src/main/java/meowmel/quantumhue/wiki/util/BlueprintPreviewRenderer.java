package meowmel.quantumhue.wiki.util;

import codechicken.lib.render.BlockRenderer;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Translation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GregFakePlayer;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.client.renderer.scene.FBOWorldSceneRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.ConfigHolder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 蓝图 3D 预览渲染器。
 *
 * <p>从 JSON 蓝图文件（如 {@code bp_4x_ebf.json}）解析方块数据，
 * 使用 {@link TrackedDummyWorld} + {@link FBOWorldSceneRenderer} 构建
 * 离屏 3D 预览场景，嵌入到 Wiki 页面中渲染。</p>
 *
 * <h3>渲染管线</h3>
 * <ol>
 *   <li><b>JSON 解析</b> — 读取蓝图 JSON，提取方块位置、类型、物品 meta、TE 数据</li>
 *   <li><b>方块放置</b> —
 *     <ul>
 *       <li>MTE 方块（{@code *:mte}）：通过 {@link MTERegistry#getObjectById(int)}
 *           查找 MetaTileEntity 模板，创建 {@link MetaTileEntityHolder} 后经
 *           {@link TrackedDummyWorld#addBlocks(Map)} 加入场景</li>
 *       <li>管道 / 线缆：通过 {@link ItemBlock#placeBlockAt} 完整放置（确保 TE 材质
 *           由 ItemStack damage 正确初始化），随后手动补入 {@code renderedBlocks}</li>
 *       <li>外壳 / 线圈等普通方块：直接构造 {@link BlockInfo} 加入场景</li>
 *     </ul>
 *   </li>
 *   <li><b>TE 数据恢复</b> — 管道连接及阻塞方向通过反射写入位掩码字段
 *       （绕过 {@code setConnection → getWorldPipeNet} 的 NPE）</li>
 *   <li><b>FBO 离屏渲染</b> — {@link FBOWorldSceneRenderer} 渲染到 FBO 纹理，
 *       脏标记缓存避免重复绘制</li>
 *   <li><b>GUI 覆盖层</b> — 名称标题、层级切换按钮、左侧部件清单</li>
 * </ol>
 *
 * <h3>在 Markdown 中使用</h3>
 * <pre>![blueprint:quantumhue:wiki/bp_4x_ebf]</pre>
 *
 * <h3>交互</h3>
 * <ul>
 *   <li>左键拖拽 — 旋转</li>
 *   <li>右键拖拽 — 平移</li>
 *   <li>滚轮 — 缩放</li>
 *   <li>右上角按钮 — 切换层级（全部 / 逐层）</li>
 *   <li>悬停方块 — tooltip 显示方块名 + 坐标</li>
 *   <li>悬停左侧部件 — 显示物品 tooltip</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class BlueprintPreviewRenderer {

    private static final Logger LOGGER = LogManager.getLogger("Wiki-Blueprint");

    // ════════════════════════════════════════════════════════════════
    //  布局常量
    // ════════════════════════════════════════════════════════════════

    private static final int ICON_SIZE = 20;
    private static final int RIGHT_PADDING = 5;
    private static final int LAYER_BUTTON_Y = 22 + ICON_SIZE + 2;
    private static final int PARTS_PER_ROW = 10;
    private static final int SLOT_SIZE = 18;

    // ════════════════════════════════════════════════════════════════
    //  静态 tooltip 状态（供 WikiScreen 跨实例访问）
    // ════════════════════════════════════════════════════════════════
    private static final float PAN_SENSITIVITY = 0.08f;
    private static ItemStack staticTooltipStack;

    // ════════════════════════════════════════════════════════════════
    //  核心渲染数据
    // ════════════════════════════════════════════════════════════════
    private static long staticLastRenderTime;
    private final FBOWorldSceneRenderer renderer;
    private final TrackedDummyWorld world;
    /**
     * 左侧部件清单（按数量降序排列）
     */
    private final List<ItemStack> parts;
    private final String structureName;

    // ════════════════════════════════════════════════════════════════
    //  相机状态
    // ════════════════════════════════════════════════════════════════
    private final String author;
    private final List<SlotEntry> slotEntries = new ArrayList<>();
    private Vector3f center;
    private float rotationYaw = 20f;

    // ════════════════════════════════════════════════════════════════
    //  交互状态
    // ════════════════════════════════════════════════════════════════
    private float rotationPitch = 50f;
    private float zoom;
    private int lastMouseX;
    private int lastMouseY;

    // ════════════════════════════════════════════════════════════════
    //  tooltip 上下文
    // ════════════════════════════════════════════════════════════════
    /**
     * -1 ＝ 全部层，0..N ＝ 仅显示第 N 层
     */
    private int layerIndex = -1;
    private int frameCounter;
    private ItemStack tooltipBlockStack;

    // ════════════════════════════════════════════════════════════════
    //  预览区域（由 render() 每帧更新）
    // ════════════════════════════════════════════════════════════════
    private List<String> tooltipLines;

    // ════════════════════════════════════════════════════════════════
    //  物品槽位追踪（用于 2D 物品 tooltip）
    // ════════════════════════════════════════════════════════════════
    private long lastRenderTime;

    // ════════════════════════════════════════════════════════════════
    //  内部类
    // ════════════════════════════════════════════════════════════════
    private int previewX, previewY, previewW, previewH;

    /**
     * @param blueprintPath 蓝图 JSON 的资源路径
     *                      （如 {@code new ResourceLocation("quantumhue", "wiki/bp_4x_ebf.json")}）
     */
    @SuppressWarnings("NewExpressionSideOnly")
    public BlueprintPreviewRenderer(ResourceLocation blueprintPath) {
        // ── 1. 加载 JSON ──────────────────────────────────────────
        JsonObject root = loadBlueprintJson(blueprintPath);
        if (root == null) {
            this.renderer = null;
            this.world = null;
            this.parts = new ArrayList<>();
            this.structureName = "Missing Blueprint";
            this.author = "";
            return;
        }

        this.structureName = root.has("name") ? root.get("name").getAsString() : "Blueprint";
        this.author = root.has("author") ? root.get("author").getAsString() : "";

        // ── 2. 解析方块 → blockMap / pipeBlocks / 部件清单 ─────────
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        List<PendingBlock> pipeBlocks = new ArrayList<>();
        PartsCollector partsCtx = new PartsCollector();

        JsonArray blocksArr = root.getAsJsonArray("blocks");
        if (blocksArr != null) {
            for (JsonElement elem : blocksArr) {
                JsonObject blockObj = elem.getAsJsonObject();
                JsonArray posArr = blockObj.getAsJsonArray("pos");
                if (posArr == null || posArr.size() < 3) continue;

                BlockPos pos = new BlockPos(
                        posArr.get(0).getAsInt(),
                        posArr.get(1).getAsInt(),
                        posArr.get(2).getAsInt());

                String blockId = blockObj.get("blockId").getAsString();
                int meta = blockObj.has("meta") ? blockObj.get("meta").getAsInt() : 0;
                JsonObject teData = blockObj.has("teData") ? blockObj.getAsJsonObject("teData") : null;

                ResourceLocation rl = new ResourceLocation(blockId);
                String namespace = rl.getNamespace();
                Block block = Block.REGISTRY.getObject(rl);
                if (block == Blocks.AIR) continue;

                // requiredItems[0].meta — MTE 作网络 ID，管道作材质 ID
                int itemMeta = meta;
                if (blockObj.has("requiredItems")) {
                    JsonArray itemsArr = blockObj.getAsJsonArray("requiredItems");
                    if (itemsArr.size() > 0) {
                        itemMeta = itemsArr.get(0).getAsJsonObject().has("meta")
                                ? itemsArr.get(0).getAsJsonObject().get("meta").getAsInt() : meta;
                    }
                }

                boolean isPipe = teData != null && hasPipeBaseKey(teData);

                // ── 分类构建 BlockInfo ──
                if (blockId.endsWith(":mte")) {
                    BlockInfo info = createMTEBlockInfo(itemMeta, teData, namespace);
                    if (info != null) blockMap.put(pos, info);
                } else if (isPipe) {
                    pipeBlocks.add(new PendingBlock(pos, block, meta, itemMeta, teData));
                } else {
                    blockMap.put(pos, new BlockInfo(block.getStateFromMeta(meta), null));
                }

                // 收集部件（用于左侧清单）
                collectParts(blockObj, block, meta, partsCtx);
            }
        }

        // ── 3. 部件排序 ────────────────────────────────────────────
        this.parts = partsCtx.sortedList();

        // ── 4. 创建场景 — addBlocks（非管道方块） ──────────────────
        this.world = new TrackedDummyWorld();
        FBOWorldSceneRenderer sceneRenderer = new FBOWorldSceneRenderer(world, 512, 512);
        sceneRenderer.setClearColor(ConfigHolder.client.multiblockPreviewColor);
        world.addBlocks(blockMap);

        // ── 5. 管道 / 线缆 — ItemBlock.placeBlockAt 放置 ────────────
        GregFakePlayer fakePlayer = new GregFakePlayer(world);
        for (PendingBlock pb : pipeBlocks) {
            IBlockState state = pb.block.getStateFromMeta(pb.meta);
            ItemStack placeStack = new ItemStack(pb.block, 1, pb.itemMeta);

            if (placeStack.getItem() instanceof ItemBlock) {
                ((ItemBlock) placeStack.getItem()).placeBlockAt(
                        placeStack, fakePlayer, world, pb.pos,
                        EnumFacing.UP, 0.5f, 0.5f, 0.5f, state);
            } else {
                world.setBlockState(pb.pos, state, 3);
            }

            // placeBlockAt 不走 addBlocks，需手动补入 renderedBlocks
            if (world.getBlockState(pb.pos).getBlock() != Blocks.AIR) {
                world.renderedBlocks.add(pb.pos);
            }

            // 恢复管道连接方向（绕过 WorldPipeNet NPE）
            if (pb.teData != null && !pb.teData.entrySet().isEmpty()) {
                try {
                    restoreTEData(world, pb.pos, pb.teData);
                } catch (Exception e) {
                    LOGGER.warn("TE restore failed at {}: {}", pb.pos, e.getMessage());
                }
            }
        }

        // ── 6. 配置 FBOWorldSceneRenderer ─────────────────────────
        int totalBlocks = world.renderedBlocks.size();
        if (totalBlocks > 50) {
            sceneRenderer.setCullInternalBlocks(true);
        }
        sceneRenderer.addRenderedBlocks(world.renderedBlocks);

        int blockCount = sceneRenderer.renderedBlocks.size();
        if (blockCount > 100) {
            sceneRenderer.setTileEntityFilter(te ->
                    te instanceof IGregTechTileEntity gtte &&
                            gtte.getMetaTileEntity() instanceof MultiblockControllerBase);
            sceneRenderer.setHitTestInterval(5);
        } else if (blockCount > 50) {
            sceneRenderer.setMaxTileEntityRenderers(8);
            sceneRenderer.setMaxTileEntityRenderDistance(16.0);
            sceneRenderer.setHitTestInterval(3);
        }

        sceneRenderer.setOnLookingAt(ray -> {
        });
        sceneRenderer.setAfterWorldRender(r -> {
            BlockPos look = sceneRenderer.getLastTraceResult() == null ? null
                    : sceneRenderer.getLastTraceResult().getBlockPos();
            if (look != null) {
                renderBlockOverlay(look, 150, 150, 150);
            }
        });

        world.updateEntities();
        world.setRenderFilter(sceneRenderer.renderedBlocks::contains);
        this.renderer = sceneRenderer;

        // ── 7. 初始相机 ────────────────────────────────────────────
        Vector3f size = world.getSize();
        float maxSide = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
        this.zoom = (float) (3.5 * Math.sqrt(maxSide));
        resetCenter();
    }

    // ════════════════════════════════════════════════════════════════
    //  构造
    // ════════════════════════════════════════════════════════════════

    /**
     * @return 当前帧悬停方块的 ItemStack（供 WikiScreen tooltip 用，100ms 窗口）
     */
    public static ItemStack getHoveredItemStack() {
        return (staticLastRenderTime > System.currentTimeMillis() - 100)
                ? staticTooltipStack : null;
    }

    // ════════════════════════════════════════════════════════════════
    //  公开 API
    // ════════════════════════════════════════════════════════════════

    /**
     * 在指定方块位置渲染半透明覆盖层立方体。
     * 移植自原版 {@code MultiblockPreviewRenderer.renderBlockOverLay}。
     */
    @SideOnly(Side.CLIENT)
    private static void renderBlockOverlay(BlockPos pos, int r, int g, int b) {
        if (pos == null) return;

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        GlStateManager.scale(1.01, 1.01, 1.01);

        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.disableTexture2D();
        CCRenderState renderState = CCRenderState.instance();
        renderState.startDrawing(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR,
                tessellator.getBuffer());

        ColourMultiplier multiplier = new ColourMultiplier(0);
        renderState.setPipeline(new Translation(-0.5, -0.5, -0.5), multiplier);
        BlockRenderer.BlockFace blockFace = new BlockRenderer.BlockFace();
        renderState.setModel(blockFace);

        for (EnumFacing side : EnumFacing.VALUES) {
            multiplier.colour = RenderUtil.packColor(r, g, b, 255);
            blockFace.loadCuboidFace(Cuboid6.full, side.getIndex());
            renderState.render();
        }

        renderState.draw();
        GlStateManager.scale(1 / 1.01, 1 / 1.01, 1 / 1.01);
        GlStateManager.translate(-pos.getX() - 0.5, -pos.getY() - 0.5, -pos.getZ() - 0.5);
        GlStateManager.enableTexture2D();

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);
    }

    /**
     * 为 GT MTE 方块创建包含 {@link MetaTileEntityHolder} 的 BlockInfo。
     *
     * <p>查找链路：
     * {@code GregTechAPI.mteManager.getRegistry(namespace)
     * → getObjectById(networkId)}（利用 {@code GTControlledRegistry}
     * 在 {@code register(int, K, V)} 时写入 {@code underlyingIntegerMap} 的 ID）</p>
     *
     * @param mteMetaId 网络 ID（{@code requiredItems[0].meta}）
     * @param teData    方块实体 JSON（读取 facing 朝向）
     * @param namespace 从 blockId 解析的模组 ID（如 {@code "gregtech"}）
     */
    private static BlockInfo createMTEBlockInfo(int mteMetaId, JsonObject teData, String namespace) {
        MetaTileEntity template = getMteByNetworkId(mteMetaId, namespace);
        if (template == null) {
            LOGGER.warn("MTE not found for network ID {} in namespace '{}'", mteMetaId, namespace);
            Block block = Block.REGISTRY.getObject(new ResourceLocation(namespace, "mte"));
            return (block != null && block != Blocks.AIR)
                    ? new BlockInfo(block.getDefaultState(), null) : null;
        }

        try {
            MetaTileEntity copy = template.createMetaTileEntity(null);
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            holder.setMetaTileEntity(copy);
            holder.getMetaTileEntity().onPlacement();

            // 应用 teData 中的 facing
            if (teData != null) {
                for (Map.Entry<String, JsonElement> entry : teData.entrySet()) {
                    if (entry.getKey().endsWith(":mte_base") && entry.getValue().isJsonObject()) {
                        JsonObject baseData = entry.getValue().getAsJsonObject();
                        if (baseData.has("facing")) {
                            int f = baseData.get("facing").getAsInt();
                            if (f >= 0 && f < EnumFacing.VALUES.length) {
                                holder.getMetaTileEntity().setFrontFacing(EnumFacing.VALUES[f]);
                            }
                        }
                        break;
                    }
                }
            }

            return new BlockInfo(copy.getBlock().getDefaultState(), holder);
        } catch (Exception e) {
            LOGGER.warn("Failed to create MTE holder for ID {}: {}", mteMetaId, e.getMessage());
            Block block = Block.REGISTRY.getObject(new ResourceLocation(namespace, "mte"));
            return (block != null && block != Blocks.AIR)
                    ? new BlockInfo(block.getDefaultState(), null) : null;
        }
    }

    /**
     * 通过 GT MTE 网络 ID 从指定 namespace 的注册表中查找 MetaTileEntity 模板。
     */
    private static MetaTileEntity getMteByNetworkId(int id, String namespace) {
        try {
            MTERegistry registry = GregTechAPI.mteManager.getRegistry(namespace);
            if (registry != null) {
                return registry.getObjectById(id);
            }
        } catch (Exception e) {
            LOGGER.debug("getObjectById({}) in namespace '{}' failed: {}", id, namespace, e.getMessage());
        }
        return null;
    }

    /**
     * 对已放置的方块，从 {@code teData} 恢复 TE 内部状态。
     * 模拟 {@code CompositeTileEntityProcessor.restoreFromCompositeJson} 的行为。
     */
    private static void restoreTEData(TrackedDummyWorld world, BlockPos pos, JsonObject teData) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return;

        for (Map.Entry<String, JsonElement> entry : teData.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject data = entry.getValue().getAsJsonObject();

            if (key.endsWith(":pipe_base")) {
                restorePipeBase(te, data);
            } else if (key.endsWith(":mte_base") && te instanceof MetaTileEntityHolder holder) {
                MetaTileEntity mte = holder.getMetaTileEntity();
                if (mte != null && data.has("facing")) {
                    int f = data.get("facing").getAsInt();
                    if (f >= 0 && f < EnumFacing.VALUES.length) {
                        mte.setFrontFacing(EnumFacing.VALUES[f]);
                    }
                }
            }
        }
    }

    /**
     * 恢复 {@link TileEntityPipeBase} 的连接 / 阻塞方向。
     *
     * <p><b>重要：</b>不能调用 {@code pipe.setConnection()}，因为该方法内部会触发
     * {@code getWorldPipeNet(this.getWorld())}，而 {@link TrackedDummyWorld}
     * 没有注册管道网络，会直接 NPE。此处改用 {@link #setPipeConnectionBits}
     * 通过反射直接写入位掩码字段，绕过网络更新。</p>
     *
     * <p>逻辑严格对应 {@code GTPipeBaseHandler.restoreData}。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restorePipeBase(TileEntity te, JsonObject data) {
        if (!data.has("connections") || !data.has("blockedConnections")) return;
        if (!(te instanceof TileEntityPipeBase<?, ?> pipe)) return;

        World world = pipe.getWorld();
        BlockPos pos = pipe.getPos();
        if (world == null || pos == null) return;

        int connections = data.get("connections").getAsInt();
        int blockedConnections = data.get("blockedConnections").getAsInt();

        // 直接写入位掩码字段，绕过 setConnection → getWorldPipeNet NPE
        setPipeConnectionBits(te, connections, "connections");
        setPipeConnectionBits(te, blockedConnections, "blockedConnections");

        pipe.markDirty();
        pipe.notifyBlockUpdate();

        // 双向同步相邻管道
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighborPos = pos.offset(facing);
            TileEntity neighborTE = world.getTileEntity(neighborPos);
            if (!(neighborTE instanceof TileEntityPipeBase<?, ?> neighborPipe)) continue;

            EnumFacing opposite = facing.getOpposite();
            boolean shouldConnect = pipe.isConnected(facing);

            if (neighborPipe.isConnected(opposite) != shouldConnect) {
                int neighborConn = neighborPipe.getConnections();
                neighborConn = shouldConnect
                        ? neighborConn | (1 << opposite.getIndex())
                        : neighborConn & ~(1 << opposite.getIndex());
                setPipeConnectionBits(neighborTE, neighborConn, "connections");
                neighborPipe.markDirty();
                neighborPipe.notifyBlockUpdate();
            }
        }
    }

    /**
     * 通过反射直接将连接 / 阻塞位掩码值写入 {@link TileEntityPipeBase}
     * 的对应字段。支持 {@code byte}、{@code int}、{@code short} 三种字段类型。
     */
    private static void setPipeConnectionBits(TileEntity te, int value, String fieldName) {
        try {
            Class<?> clazz = te.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    if (field.getType() == byte.class) {
                        field.setByte(te, (byte) value);
                    } else if (field.getType() == int.class) {
                        field.setInt(te, value);
                    } else if (field.getType() == short.class) {
                        field.setShort(te, (short) value);
                    }
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("setPipeConnectionBits '{}' failed: {}", fieldName, e.getMessage());
        }
    }

    /**
     * 判断 teData 是否包含管道 / 线缆专用的 {@code *:pipe_base} 条目
     */
    private static boolean hasPipeBaseKey(JsonObject teData) {
        for (Map.Entry<String, JsonElement> entry : teData.entrySet()) {
            if (entry.getKey().endsWith(":pipe_base")) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  每帧渲染
    // ════════════════════════════════════════════════════════════════

    /**
     * 从 assets 加载蓝图 JSON 文件
     */
    private static JsonObject loadBlueprintJson(ResourceLocation path) {
        try {
            IResource res = Minecraft.getMinecraft().getResourceManager().getResource(path);
            JsonElement el = new JsonParser().parse(
                    new InputStreamReader(res.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to load blueprint: {}", path, e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  鼠标交互
    // ════════════════════════════════════════════════════════════════

    /**
     * 收集单个方块的部件信息到 {@link PartsCollector}
     */
    private static void collectParts(JsonObject blockObj, Block block, int meta,
                                     PartsCollector ctx) {
        if (blockObj.has("requiredItems")) {
            JsonArray itemsArr = blockObj.getAsJsonArray("requiredItems");
            for (JsonElement itemElem : itemsArr) {
                JsonObject itemObj = itemElem.getAsJsonObject();
                String itemId = itemObj.get("itemId").getAsString();
                int itMeta = itemObj.has("meta") ? itemObj.get("meta").getAsInt() : 0;
                int count = itemObj.has("count") ? itemObj.get("count").getAsInt() : 1;
                ctx.add(createItemStack(itemId, itMeta), count);
            }
        } else {
            ctx.add(new ItemStack(block, 1, meta), 1);
        }
    }

    /**
     * 从物品 / 方块注册表创建 ItemStack。
     * 优先查物品注册表，若无则查方块注册表。
     */
    private static ItemStack createItemStack(String itemId, int meta) {
        ResourceLocation rl = new ResourceLocation(itemId);
        net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(rl);
        if (item != null && item != Items.AIR) {
            return new ItemStack(item, 1, meta);
        }
        Block block = Block.REGISTRY.getObject(rl);
        if (block != Blocks.AIR) {
            return new ItemStack(block, 1, meta);
        }
        return ItemStack.EMPTY;
    }

    /**
     * @return 当前悬停方块的坐标 tooltip 行
     */
    public List<String> getTooltipLines() {
        return tooltipLines;
    }

    /**
     * @return 预览在屏幕上的区域 {@code {x, y, w, h}}
     */
    public int[] getPreviewBounds() {
        return new int[]{previewX, previewY, previewW, previewH};
    }

    // ════════════════════════════════════════════════════════════════
    //  层级管理
    // ════════════════════════════════════════════════════════════════

    /**
     * @return 鼠标下方的 2D 槽位物品，或 null
     */
    public ItemStack getSlotStackAt(int mouseX, int mouseY) {
        for (SlotEntry e : slotEntries) {
            if (mouseX >= e.x && mouseX < e.x + e.w
                    && mouseY >= e.y && mouseY < e.y + e.h) {
                return e.stack;
            }
        }
        return null;
    }

    /**
     * 蓝图无 Predicate 概念，始终返回 null
     */
    public List<String> getSlotPredicateTips(int mouseX, int mouseY) {
        return null;
    }

    /**
     * 蓝图无 Predicate 概念，始终返回 null
     */
    public List<String> getPredicateTips() {
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  相机平移
    // ════════════════════════════════════════════════════════════════

    /**
     * @return true 表示 JSON 加载成功且渲染器可用
     */
    public boolean isValid() {
        return renderer != null && world != null;
    }

    /**
     * 主渲染入口，每帧在 GL 线程调用。
     */
    public void render(int x, int y, int w, int h, int mouseX, int mouseY) {
        if (renderer == null || world == null) return;

        this.previewX = x;
        this.previewY = y;
        this.previewW = w;
        this.previewH = h;
        this.slotEntries.clear();

        // ── 3D 场景 FBO ────────────────────────────────────────────
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableLighting();
            RenderHelper.enableStandardItemLighting();
            renderer.render(x, y, w, h, mouseX, mouseY);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }

        // ── 覆盖层 ─────────────────────────────────────────────────
        drawStructureName(x, w);
        drawLayerButton(x + w);

        // ── 左侧部件图标（最多 2 行 × 10 列） ──────────────────────
        int partsToShow = Math.min(parts.size(), PARTS_PER_ROW * 2);
        for (int i = 0; i < partsToShow; i++) {
            int row = i / PARTS_PER_ROW, col = i % PARTS_PER_ROW;
            drawItemSlot(x + col * SLOT_SIZE + 2, y + row * SLOT_SIZE + 2, parts.get(i));
        }

        GlStateManager.color(1f, 1f, 1f, 1f);

        // ── 相机拖拽 ───────────────────────────────────────────────
        boolean leftDown = org.lwjgl.input.Mouse.isButtonDown(0);
        boolean rightDown = org.lwjgl.input.Mouse.isButtonDown(1);
        boolean insideScene = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;

        boolean cameraModified = false;
        boolean isDragging = false;

        if (insideScene && rightDown) {
            float dx = mouseX - lastMouseX, dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                cameraModified = panCamera(dx, dy);
                isDragging = true;
            }
        } else if (insideScene && leftDown) {
            float dx = mouseX - lastMouseX, dy = mouseY - lastMouseY;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                rotationYaw = (rotationYaw + dx) % 360f;
                if (rotationYaw < 0) rotationYaw += 360f;
                rotationPitch = MathHelper.clamp(rotationPitch + dy, -89.9f, 89.9f);
                cameraModified = true;
                isDragging = true;
            }
        }

        frameCounter++;
        if (cameraModified) {
            if (!isDragging || frameCounter % 2 == 0) {
                renderer.setCameraLookAt(center, zoom,
                        Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
            }
        }

        // ── 悬停检测 ───────────────────────────────────────────────
        tooltipBlockStack = null;
        tooltipLines = null;
        if (!leftDown && !rightDown && insideScene) {
            RayTraceResult trace = renderer.getLastTraceResult();
            if (trace != null && !renderer.world.isAirBlock(trace.getBlockPos())) {
                IBlockState state = renderer.world.getBlockState(trace.getBlockPos());
                ItemStack stack = state.getBlock().getPickBlock(state, trace, renderer.world,
                        trace.getBlockPos(), Minecraft.getMinecraft().player);
                if (!stack.isEmpty()) {
                    tooltipBlockStack = stack;
                }
                tooltipLines = new ArrayList<>();
                tooltipLines.add("§7(" + trace.getBlockPos().getX() + ", "
                        + trace.getBlockPos().getY() + ", " + trace.getBlockPos().getZ() + ")");
            }
        }

        // ── 更新静态 tooltip 状态 ──────────────────────────────────
        staticTooltipStack = tooltipBlockStack;
        staticLastRenderTime = System.currentTimeMillis();
        lastRenderTime = staticLastRenderTime;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
    }

    // ════════════════════════════════════════════════════════════════
    //  GUI 覆盖层绘制
    // ════════════════════════════════════════════════════════════════

    /**
     * 处理鼠标点击。返回 true 表示事件已消费。
     */
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (renderer == null) return false;

        // 层级切换按钮
        int btnX = previewX + previewW - (ICON_SIZE + RIGHT_PADDING);
        int btnY = previewY + LAYER_BUTTON_Y;
        if (mouseButton == 0 && mouseX >= btnX && mouseX <= btnX + ICON_SIZE
                && mouseY >= btnY && mouseY <= btnY + ICON_SIZE) {
            toggleNextLayer();
            return true;
        }
        return false;
    }

    /**
     * 处理滚轮缩放。返回 true 表示事件已消费。
     */
    public boolean handleScroll(int delta, int mouseX, int mouseY) {
        if (renderer == null) return false;
        if (!isMouseOverPreview(mouseX, mouseY)) return false;

        zoom = MathHelper.clamp(zoom + (delta < 0 ? 0.5f : -0.5f), 3, 999);
        renderer.setCameraLookAt(center, zoom,
                Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
        return true;
    }

    /**
     * 鼠标是否在 3D 场景区域内
     */
    public boolean isMouseOverPreview(int mouseX, int mouseY) {
        return mouseX >= previewX && mouseX < previewX + previewW
                && mouseY >= previewY && mouseY < previewY + previewH;
    }

    // ════════════════════════════════════════════════════════════════
    //  3D 方块覆盖层渲染（悬停 / 选中高亮）
    // ════════════════════════════════════════════════════════════════

    /**
     * 鼠标是否在整个预览区域内（蓝图预览无通道滑条，等同于 {@link #isMouseOverPreview}）
     */
    public boolean isMouseOverFullPreview(int mouseX, int mouseY) {
        return isMouseOverPreview(mouseX, mouseY);
    }

    // ════════════════════════════════════════════════════════════════
    //  BlockInfo 构建 — MTE 方块
    // ════════════════════════════════════════════════════════════════

    /**
     * 切换到下一层（全部 → L1 → L2 → … → 全部）
     */
    private void toggleNextLayer() {
        if (renderer == null || world == null) return;
        int height = (int) world.getSize().getY() - 1;
        layerIndex = (layerIndex + 1 > height) ? -1 : layerIndex + 1;
        setNextLayer(layerIndex);
    }

    /**
     * 设置当前显示的层级（-1 ＝ 全部层）
     */
    private void setNextLayer(int newLayer) {
        this.layerIndex = newLayer;
        if (renderer == null || world == null) return;
        resetCenter();

        renderer.disableClipPlanes();
        renderer.renderedBlocks.clear();

        int minY = (int) world.getMinPos().getY();
        Collection<BlockPos> blocks = (newLayer == -1)
                ? world.renderedBlocks
                : world.renderedBlocks.stream()
                .filter(p -> p.getY() - minY == newLayer)
                .collect(Collectors.toSet());

        renderer.addRenderedBlocks(blocks);
        renderer.markFBODirty();
    }

    // ════════════════════════════════════════════════════════════════
    //  TE 数据恢复
    // ════════════════════════════════════════════════════════════════

    /**
     * 将相机居中到场景几何中心（或指定层中心）
     */
    private void resetCenter() {
        if (world == null) return;
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        if (layerIndex != -1) {
            center.y = minPos.y + layerIndex + 0.5f;
        }
        if (renderer != null) {
            renderer.setCameraLookAt(center, zoom,
                    Math.toRadians(rotationYaw), Math.toRadians(rotationPitch));
        }
    }

    /**
     * 右键拖拽平移相机
     */
    private boolean panCamera(float dx, float dy) {
        double yawRad = Math.toRadians(rotationYaw);
        double pitchRad = Math.toRadians(rotationPitch);

        Vec3d forward = new Vec3d(
                Math.cos(pitchRad) * Math.sin(yawRad),
                Math.sin(pitchRad),
                Math.cos(pitchRad) * Math.cos(yawRad)).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        Vec3d up = right.crossProduct(forward).normalize();

        center.x += (float) (-dx * right.x * PAN_SENSITIVITY);
        center.y += (float) (-dx * right.y * PAN_SENSITIVITY);
        center.z += (float) (-dx * right.z * PAN_SENSITIVITY);
        center.x += (float) (-dy * up.x * PAN_SENSITIVITY);
        center.y += (float) (-dy * up.y * PAN_SENSITIVITY);
        center.z += (float) (-dy * up.z * PAN_SENSITIVITY);
        return true;
    }

    /**
     * 顶部居中渲染蓝图名称（+ 作者）
     */
    private void drawStructureName(int x, int w) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String name = (author != null && !author.isEmpty())
                ? structureName + "  §7(" + author + ")"
                : structureName;
        List<String> lines = fr.listFormattedStringToWidth(name, w - 10);
        int ty = previewY + 4;
        for (String line : lines) {
            fr.drawString(line, x + (w - fr.getStringWidth(line)) / 2, ty,
                    ConfigHolder.client.multiblockPreviewFontColor);
            ty += fr.FONT_HEIGHT;
        }
    }

    /**
     * 右上角层级切换按钮（L:A / L:1 / L:2 / …）
     */
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

    // ════════════════════════════════════════════════════════════════
    //  辅助方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 绘制一个物品槽位（带边框 + 物品渲染），并记录位置供 tooltip 查询
     */
    private void drawItemSlot(int x, int y, ItemStack stack) {
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

        slotEntries.add(new SlotEntry(x, y, SLOT_SIZE, SLOT_SIZE, stack));
    }

    /**
     * 部件收集器 — 解析时收集所有物品并按数量降序排列。
     */
    private static class PartsCollector {
        final Set<ItemStack> items = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        final Map<ItemStack, Integer> counts = new Object2ObjectOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());

        void add(ItemStack stack, int count) {
            if (stack.isEmpty()) return;
            items.add(stack.copy());
            ItemStack key = stack.copy();
            key.setCount(1);
            counts.merge(key, count, Integer::sum);
        }

        List<ItemStack> sortedList() {
            List<ItemStack> list = new ArrayList<>(items);
            list.sort(Comparator.comparingInt((ItemStack s) -> {
                ItemStack k = s.copy();
                k.setCount(1);
                return counts.getOrDefault(k, 0);
            }).reversed());
            return list;
        }
    }
}