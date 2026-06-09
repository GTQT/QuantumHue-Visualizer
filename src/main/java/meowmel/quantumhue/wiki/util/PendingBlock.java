package meowmel.quantumhue.wiki.util;

import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public final class PendingBlock {

    public final BlockPos pos;
    public final Block block;
    public final int meta;
    public final int itemMeta;
    public final JsonObject teData;

    public PendingBlock(BlockPos pos, Block block, int meta, int itemMeta, JsonObject teData) {
        this.pos = pos;
        this.block = block;
        this.meta = meta;
        this.itemMeta = itemMeta;
        this.teData = teData;
    }
}