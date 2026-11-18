package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.api.utils.Easing;
import meowmel.quantumhue.tooltips.applecore.AppleSkinIntegration;
import meowmel.quantumhue.tooltips.text.TooltipTextLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import thaumcraft.api.aspects.AspectList;

import java.util.List;

/**
 * 单个 tooltip 的渲染状态与内容缓存。
 *
 * <p>每个 tooltip 各持一份状态：装备对比在同一帧渲染多个 tooltip 时，页码、颜色、
 * 要素/食物信息互不干扰；状态随物品身份失效，无需手动重置。</p>
 *
 * <p>内容缓存只依赖原始文本行与物品身份：文本行不变时跳过标记解析与逐行测宽换行，
 * 这两步是每帧最贵的部分。</p>
 */
public class TooltipState {

    /** 物品身份（注册名 + damage + NBT hash），切换物品时整份状态重建。 */
    public final String itemId;

    /** 翻页按键边沿检测，跨帧保存。 */
    public final KeyState keyState = new KeyState();

    /** 颜色、要素、食物信息按物品身份缓存一份；由 AdvancedTooltipHandler 填充。 */
    TooltipColors colors;
    AspectList aspects;
    AppleSkinIntegration.FoodInfo foodInfo;

    private int sourceHash;
    private TooltipContent content;

    /** 打开时刻，驱动面板微光等与时间相关的装饰。 */
    private final long openTimeMs = System.currentTimeMillis();

    /** 上一次看到的页码，用来给翻页做滑动/弹一下的过渡。 */
    private int lastSeenPage = -1;
    private long pageFlipStartMs = -1L;
    private int pageFlipDir = 1;

    public TooltipState(String itemId) {
        this.itemId = itemId;
    }

    public float elapsedSeconds() {
        return (System.currentTimeMillis() - openTimeMs) / 1000.0F;
    }

    /** 每帧在算完分页后调用：页码变了就记下方向与时刻。 */
    public void notePage(int page) {
        if (lastSeenPage >= 0 && page != lastSeenPage) {
            pageFlipDir = page > lastSeenPage ? 1 : -1;
            pageFlipStartMs = System.currentTimeMillis();
        }
        lastSeenPage = page;
    }

    /** 翻页过渡进度：1 表示已经落定。 */
    private float pageFlipPhase() {
        if (pageFlipStartMs < 0L) return 1.0F;
        float t = (System.currentTimeMillis() - pageFlipStartMs) / 150.0F;
        if (t >= 1.0F) return 1.0F;
        return Easing.outCubic(Math.max(0.0F, t));
    }

    /** 正文垂直偏移：新一页从下方/上方滑入。 */
    public float pageFlipOffsetY() {
        float phase = pageFlipPhase();
        if (phase >= 1.0F) return 0.0F;
        return pageFlipDir * 6.0F * (1.0F - phase);
    }

    /** 页码胶囊的缩放系数：翻页瞬间轻微放大再回弹。 */
    public float pageFlipPulse() {
        float phase = pageFlipPhase();
        if (phase >= 1.0F) return 1.0F;
        return 1.0F + 0.18F * (1.0F - phase);
    }

    /**
     * 取回缓存的内容；只有原始文本行发生变化（按住 Shift、其它模组动态改行）时
     * 才重新解析标记并重新换行。字体与最大宽度是常量，不参与缓存 key。
     */
    public TooltipContent resolveContent(List<String> lines, ItemStack stack,
                                         TooltipContentExtractor extractor, FontRenderer font) {
        int hash = lines.hashCode();
        if (content == null || hash != sourceHash) {
            // 页码挂在 TooltipContent 上，重建时必须把旧页码带过去：否则任何一行文本
            // 变化（GT 的动态行、别的模组延时补行）都会让翻页后的 tooltip 自己跳回第一页。
            int keepPage = content == null ? 0 : content.currentPage;
            TooltipContent raw = extractor.extractTooltipContent(lines, stack);
            List<TooltipTextLine> wrapped = TooltipUtils.wrapTooltipText(
                    raw.remainingLines, font, TooltipConstants.TOOLTIP_MAX_WIDTH);
            content = new TooltipContent(raw.itemName, raw.modName, wrapped);
            content.currentPage = keepPage;
            // 打字机时钟用稳定身份，避免同名不同 NBT 的物品共享时钟
            content.cacheKey = itemId;
            sourceHash = hash;
        }
        return content;
    }
}
