package meowmel.quantumhue.wiki.util;

import net.minecraft.item.ItemStack;

public class SlotEntry {

    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public final ItemStack stack;

    public SlotEntry(int x, int y, int w, int h, ItemStack stack) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.stack = stack;
    }
}