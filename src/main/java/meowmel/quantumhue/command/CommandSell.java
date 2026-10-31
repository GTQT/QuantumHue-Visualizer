package meowmel.quantumhue.command;

import meowmel.quantumhue.market.ShopDatabase;
import meowmel.quantumhue.market.TradeItem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import java.util.List;

/** {@code /sell <单价> [数量]}：快速把手中物品上架到市场。 */
public class CommandSell extends CommandBase {

    @Override
    public String getName() {
        return "sell";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sell <单价> [数量] - 快速出售手持物品到商店";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String [] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("§c该命令仅限玩家使用"));
            return;
        }
        EntityPlayer player = (EntityPlayer) sender;
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            sender.sendMessage(new TextComponentString("§c请手持要出售的物品"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("§c用法：/sell <单价> [数量]"));
            return;
        }

        long pricePerUnit;
        try {
            pricePerUnit = Long.parseLong(args[0]);
        } catch (NumberFormatException invalid) {
            sender.sendMessage(new TextComponentString("§c无效的价格格式"));
            return;
        }
        if (pricePerUnit <= 0L) {
            sender.sendMessage(new TextComponentString("§c单价必须大于 0"));
            return;
        }

        int quantity = held.getCount();
        if (args.length >= 2) {
            try {
                quantity = Integer.parseInt(args[1]);
            } catch (NumberFormatException invalid) {
                sender.sendMessage(new TextComponentString("§c无效的数量格式"));
                return;
            }
        }
        if (quantity <= 0) {
            sender.sendMessage(new TextComponentString("§c数量必须大于 0"));
            return;
        }

        int total = count(player, held);
        if (total < quantity) {
            sender.sendMessage(new TextComponentString(String.format("§c物品不足，当前持有：%1$s", total)));
            return;
        }

        String name = held.getDisplayName();
        ItemStack selling = held.copy();
        int toRemove = quantity;
        for (int slot = 0; slot < player.inventory.getSizeInventory() && toRemove > 0; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(stack, held)) {
                int removed = Math.min(stack.getCount(), toRemove);
                stack.shrink(removed);
                toRemove -= removed;
            }
        }

        ShopDatabase database = ShopDatabase.get(player.world);
        database.addListing(new TradeItem(selling, pricePerUnit, quantity,
                player.getUniqueID(), player.getName()));

        sender.sendMessage(new TextComponentString(String.format("§a✓ 已上架：%1$s", name)));
        sender.sendMessage(new TextComponentString(String.format(
                "§7数量：§b%1$s §7| §7单价：§e%2$s §7| §7上架金额：§a%3$s",
                quantity, pricePerUnit, pricePerUnit * quantity)));
    }

    private static int count(EntityPlayer player, ItemStack target) {
        int total = 0;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(stack, target)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                                   String [] args, net.minecraft.util.math.BlockPos pos) {
        return super.getTabCompletions(server, sender, args, pos);
    }
}
