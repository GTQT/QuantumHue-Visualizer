package meowmel.quantumhue.tooltips;

import meowmel.quantumhue.tooltips.text.TooltipTextLine;
import meowmel.quantumhue.tooltips.text.TooltipTextMarkup;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

public class TooltipUtils {

    public static String getItemUniqueId(ItemStack stack) {
        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(stack.getItem().getRegistryName());
        idBuilder.append("@").append(stack.getItemDamage());

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            idBuilder.append("#").append(nbt.hashCode());
        }

        return idBuilder.toString();
    }

    /**
     * 按宽度换行，并把触发标签划出的特效区间一起切分到新行。
     * 标签已在 {@link TooltipTextMarkup#parse(String)} 中移除，不参与宽度计算。
     */
    public static List<TooltipTextLine> wrapTooltipText(List<TooltipTextLine> lines, FontRenderer font, int maxWidth) {
        List<TooltipTextLine> wrappedLines = new ArrayList<>();
        for (TooltipTextLine line : lines) {
            if (line.text.isEmpty()) {
                wrappedLines.add(line);
                continue;
            }
            List<String> pieces = font.listFormattedStringToWidth(line.text, maxWidth);
            if (pieces.isEmpty()) {
                wrappedLines.add(line);
                continue;
            }
            String plainText = TooltipTextMarkup.plainText(line.text);
            int searchFrom = 0;
            for (String piece : pieces) {
                String piecePlain = TooltipTextMarkup.plainText(piece);
                if (piecePlain.isEmpty()) {
                    wrappedLines.add(TooltipTextLine.plain(piece));
                    continue;
                }
                // listFormattedStringToWidth 会在换行处丢掉分隔空格，不能简单累加长度；
                // 用 plain 文本回查，保证切片区间与原始行对齐。
                int start = plainText.indexOf(piecePlain, searchFrom);
                if (start < 0) {
                    start = Math.min(searchFrom, Math.max(0, plainText.length() - piecePlain.length()));
                }
                int end = Math.min(plainText.length(), start + piecePlain.length());
                wrappedLines.add(line.slice(start, end, piece));
                searchFrom = end;
            }
        }
        return wrappedLines;
    }

    public static List<String> wrapSimpleTooltipText(List<String> lines, FontRenderer font, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        int adjustedMaxWidth = maxWidth - TooltipConstants.MOUSE_OFFSET_X;
        for (String line : lines) {
            // 简单 Tooltip 不渲染特效，但也不应该把触发标签显示出来
            String plain = TooltipTextMarkup.parse(line).text;
            wrappedLines.addAll(font.listFormattedStringToWidth(plain, adjustedMaxWidth));
        }
        return wrappedLines;
    }
}
