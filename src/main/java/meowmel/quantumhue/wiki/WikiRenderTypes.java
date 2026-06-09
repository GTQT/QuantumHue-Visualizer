package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Wiki 渲染引擎的类型定义 */
public final class WikiRenderTypes {

    private WikiRenderTypes() {}

    /** 记录页面上已渲染的图标位置，用于 tooltip 查询 */
    public static class IconSlot {
        public final int x, y, w, h;
        public final ItemStack stack;

        public IconSlot(int x, int y, int w, int h, ItemStack stack) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.stack = stack;
        }
    }

    /* ═══════════════ 渲染行类型 ═══════════════ */
    public enum LineType {
        HEADING, SUBHEADING, SUBSUBHEADING,
        TEXT,
        TABLE_HEADER, TABLE_ROW,
        INLINE_ICON, INLINE_IMAGE, INLINE_LATEX,
        BLOCKQUOTE, CODE_BLOCK, LATEX_BLOCK,
        MULTIBLOCK_PREVIEW,
        BLUEPRINT_PREVIEW,
        GAP
    }

    /* ═══════════════ 行内片段类型 ═══════════════ */
    public enum PartType { TEXT, ICON, LATEX }

    /* ═══════════════ 行内样式片段 ═══════════════ */
    public static class TextPart {
        public final PartType type;
        public final String text;
        public final int color;
        public final ItemStack icon;

        public TextPart(PartType t, String txt, int col) {
            type = t; text = txt; color = col; icon = null;
        }

        public TextPart(PartType t, ItemStack i) {
            type = t; text = null; color = 0; icon = i;
        }
    }

    /* ═══════════════ 行级渲染单元 ═══════════════ */
    public static class RenderLine {
        public final LineType type;
        public final String text;
        public List<TextPart> parts = new ArrayList<>();
        public int height, extra;
        public ItemStack icon;
        public ResourceLocation image;
        public int imageW, imageH;
        /** 任意附加数据（如多方块3D渲染器） */
        public Object extraData;

        public RenderLine(LineType t, String txt, int h) {
            type = t; text = txt; height = h;
        }

        public RenderLine(LineType t, TextPart p, int h) {
            type = t; parts.add(p); text = null; height = h;
        }

        public RenderLine(LineType t, List<TextPart> p, int h) {
            type = t; parts = p; text = null; height = h;
        }

        public RenderLine(LineType t, ItemStack i) {
            type = t; icon = i; text = null; height = 16;
        }

        public RenderLine(LineType t, ResourceLocation img, int w, int h) {
            type = t; image = img; imageW = w; imageH = h; text = null; height = h + 4;
        }
    }
}
