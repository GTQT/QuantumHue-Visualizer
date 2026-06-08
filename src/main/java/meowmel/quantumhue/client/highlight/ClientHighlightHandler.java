package meowmel.quantumhue.client.highlight;

import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.network.HighlightPacket;
import meowmel.quantumhue.network.PacketHandler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 中键提醒功能客户端处理器
 * - 检测鼠标中键按下，发射射线获取目标
 * - 将目标信息发送到服务端
 * - 接收服务端广播，渲染高亮线框
 */
@SideOnly(Side.CLIENT)
public class ClientHighlightHandler {

    // 当前活跃的高亮标记列表
    private static final CopyOnWriteArrayList<ActiveHighlight> activeHighlights = new CopyOnWriteArrayList<>();

    // ========== 输入检测：鼠标中键 ==========

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (event.getButton() == 2 && event.isButtonstate()) {
            handleMiddleClick();
        }
    }

    /**
     * 备选方案：通过 InputEvent.MouseInputEvent 检测鼠标中键
     */
    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (Mouse.getEventButton() == 2 && Mouse.getEventButtonState()) {
            // 防止重复处理（已经通过 MouseEvent 处理了）
        }
    }

    private static final double MAX_TRACE_DISTANCE = 200.0;

    private void handleMiddleClick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (!QuantumHueConfig.highlight.enabled) return;

        // 使用自定义远距离射线检测
        RayTraceResult result = rayTrace(mc.player, MAX_TRACE_DISTANCE, 1.0f);
        if (result == null || result.typeOfHit == RayTraceResult.Type.MISS) return;

        BlockPos pos;
        int entityId = -1;
        int targetType;
        String targetName;

        if (result.typeOfHit == RayTraceResult.Type.ENTITY && result.entityHit != null) {
            Entity entity = result.entityHit;
            pos = entity.getPosition();
            entityId = entity.getEntityId();
            targetType = 1;
            targetName = entity.getName();
        } else if (result.typeOfHit == RayTraceResult.Type.BLOCK && result.getBlockPos() != null) {
            pos = result.getBlockPos();
            targetType = 0;
            IBlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            ItemStack pickBlock = block.getPickBlock(state, result, mc.world, pos, mc.player);
            if (!pickBlock.isEmpty()) {
                pickBlock.getItem();
            }
            if (!pickBlock.isEmpty()) {
                targetName = pickBlock.getDisplayName();
            } else {
                targetName = block.getLocalizedName();
            }
        } else {
            return;
        }

        HighlightPacket packet = new HighlightPacket(pos, entityId, targetType, targetName, "");
        PacketHandler.sendToServer(packet);
    }

    /**
     * 远距离射线检测，同时检测方块和实体
     */
    private RayTraceResult rayTrace(EntityPlayer player, double distance, float partialTicks) {
        Vec3d start = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks);
        Vec3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        // 先检测方块
        RayTraceResult blockHit = player.world.rayTraceBlocks(start, end, false, false, true);

        // 再检测实体
        double blockDist = blockHit != null ? blockHit.hitVec.distanceTo(start) : Double.MAX_VALUE;

        // 扩展搜索区域：以射线为中心的长方体
        double expand = 2.0;
        Vec3d min = new Vec3d(
                Math.min(start.x, end.x) - expand,
                Math.min(start.y, end.y) - expand,
                Math.min(start.z, end.z) - expand
        );
        Vec3d max = new Vec3d(
                Math.max(start.x, end.x) + expand,
                Math.max(start.y, end.y) + expand,
                Math.max(start.z, end.z) + expand
        );

        AxisAlignedBB searchBox = new AxisAlignedBB(min.x, min.y, min.z, max.x, max.y, max.z);
        Entity closestEntity = null;
        double closestEntityDist = blockDist;

        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, searchBox)) {
            if (!entity.canBeCollidedWith() && !entity.isEntityAlive()) continue;

            AxisAlignedBB entityBB = entity.getEntityBoundingBox();
            if (entityBB == null) continue;

            // 扩大一点点边界框使瞄准更友好
            RayTraceResult entityHit = entityBB.grow(0.3).calculateIntercept(start, end);
            if (entityHit != null) {
                double dist = start.distanceTo(entityHit.hitVec);
                if (dist < closestEntityDist) {
                    closestEntity = entity;
                    closestEntityDist = dist;
                }
            }
        }

        // 如果实体比方块更近，返回实体命中
        if (closestEntity != null && closestEntityDist < blockDist) {
            Vec3d hitVec = start.add(look.scale(closestEntityDist / Math.sqrt(look.x * look.x + look.y * look.y + look.z * look.z)));
            return new RayTraceResult(closestEntity, hitVec);
        }

        // 否则返回方块命中
        return blockHit;
    }

    // ========== 高亮接收 ==========

    /**
     * 由网络处理器调用，添加一个新的高亮标记
     */
    public static void addHighlight(HighlightPacket packet) {
        long duration = QuantumHueConfig.highlight.duration * 1000L;
        activeHighlights.clear();
        activeHighlights.add(new ActiveHighlight(
                packet.getPos(),
                packet.getEntityId(),
                packet.getTargetType(),
                packet.getTargetName(),
                packet.getPlayerName(),
                System.currentTimeMillis() + duration
        ));
    }

    // ========== 线框渲染 ==========

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (activeHighlights.isEmpty()) return;
        if (!QuantumHueConfig.highlight.enabled) {
            activeHighlights.clear();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        float partialTicks = event.getPartialTicks();

        // 清理过期的高亮
        activeHighlights.removeIf(h -> now > h.expiryTime);

        for (ActiveHighlight highlight : activeHighlights) {
            if (highlight.targetType == 0) {
                // 方块高亮
                renderBlockWireframe(mc, highlight.pos, partialTicks, now);
            } else {
                // 实体高亮
                Entity entity = mc.world.getEntityByID(highlight.entityId);
                if (entity != null) {
                    renderEntityWireframe(mc, entity, partialTicks, now);
                } else {
                    // 实体不存在则回退到方块位置
                    renderBlockWireframe(mc, highlight.pos, partialTicks, now);
                }
            }
        }
    }

    private void renderBlockWireframe(Minecraft mc, BlockPos pos, float partialTicks, long now) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(QuantumHueConfig.highlight.lineWidth);

        // 获取相机位置
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) viewEntity = mc.player;
        double dx = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double dy = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double dz = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

        double x = pos.getX() - dx;
        double y = pos.getY() - dy;
        double z = pos.getZ() - dz;

        int color = QuantumHueConfig.highlight.color;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = QuantumHueConfig.highlight.opacity / 255f;

        drawCubeEdges(x, y, z, x + 1, y + 1, z + 1, r, g, b, a);

        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void renderEntityWireframe(Minecraft mc, Entity entity, float partialTicks, long now) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(QuantumHueConfig.highlight.lineWidth);

        // 获取相机位置
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) viewEntity = mc.player;
        double dx = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double dy = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double dz = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

        // 获取实体的边界框（世界坐标）
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        if (bb == null) {
            bb = new AxisAlignedBB(entity.posX - 0.3, entity.posY, entity.posZ - 0.3,
                    entity.posX + 0.3, entity.posY + 1.8, entity.posZ + 0.3);
        }

        // 转为相机相对坐标
        double minX = bb.minX - dx;
        double minY = bb.minY - dy;
        double minZ = bb.minZ - dz;
        double maxX = bb.maxX - dx;
        double maxY = bb.maxY - dy;
        double maxZ = bb.maxZ - dz;

        int color = QuantumHueConfig.highlight.entityColor;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = QuantumHueConfig.highlight.opacity / 255f;

        drawCubeEdges(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * 绘制立方体的 12 条边
     */
    private void drawCubeEdges(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               float r, float g, float b, float a) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);

        // 底面
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();

        // 顶面
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();

        tessellator.draw();

        // 竖线
        buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        tessellator.draw();
    }

    // ========== 高亮数据 ==========

    private static class ActiveHighlight {
        final BlockPos pos;
        final int entityId;
        final int targetType;
        final String targetName;
        final String playerName;
        final long expiryTime;

        ActiveHighlight(BlockPos pos, int entityId, int targetType,
                        String targetName, String playerName, long expiryTime) {
            this.pos = pos;
            this.entityId = entityId;
            this.targetType = targetType;
            this.targetName = targetName;
            this.playerName = playerName;
            this.expiryTime = expiryTime;
        }
    }
}
