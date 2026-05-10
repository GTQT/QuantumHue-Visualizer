package meowmel.quantumhue.wiki;

import gregtech.common.items.MetaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Scans the player inventory to unlock wiki pages based on items held.
 * Called when the wiki screen is opened.
 */
public final class WikiDiscoveryScanner {

    private static final List<DiscoveryRule> RULES = new ArrayList<>();

    static {
        // Steam tier - having any bronze item or steam machine
        rule("steam", stack -> {
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("bronze") || name.contains("steam");
        });

        // LV tier - LV machine hulls or motors
        rule("lv", stack -> {
            if (MetaItems.ELECTRIC_MOTOR_LV != null &&
                    MetaItems.ELECTRIC_MOTOR_LV.isItemEqual(stack)) return true;
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("lv ") || name.contains("low voltage");
        });

        // MV tier
        rule("mv", stack -> {
            if (MetaItems.ELECTRIC_MOTOR_MV != null &&
                    MetaItems.ELECTRIC_MOTOR_MV.isItemEqual(stack)) return true;
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("mv ") || name.contains("medium voltage");
        });

        // HV tier
        rule("hv", stack -> {
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("hv ") || name.contains("high voltage");
        });

        // EV tier
        rule("ev", stack -> {
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("ev ") || name.contains("extreme voltage");
        });

        // Tools
        rule("wrench", stack -> isToolType(stack, "wrench"));
        rule("screwdriver", stack -> isToolType(stack, "screwdriver"));
        rule("soft_mallet", stack -> isToolType(stack, "soft_mallet") || isToolType(stack, "mallet"));
        rule("crowbar", stack -> isToolType(stack, "crowbar"));
        rule("wire_cutter", stack -> isToolType(stack, "wire_cutter"));
        rule("hammer", stack -> isToolType(stack, "hammer"));

        // Specific items
        rule("circuit", stack -> {
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("circuit") || name.contains("vacuum tube");
        });
        rule("cover", stack -> {
            if (MetaItems.ELECTRIC_PUMP_LV != null &&
                    MetaItems.ELECTRIC_PUMP_LV.isItemEqual(stack)) return true;
            return MetaItems.CONVEYOR_MODULE_LV != null &&
                    MetaItems.CONVEYOR_MODULE_LV.isItemEqual(stack);
        });
        rule("ore_processing", stack -> {
            String name = stack.getDisplayName().toLowerCase();
            return name.contains("crushed") || name.contains("purified ore");
        });
    }

    public static void scanPlayer(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            for (DiscoveryRule r : RULES) {
                if (r.test.test(stack)) {
                    WikiDiscovery.discoverByTag(r.tag);
                }
            }
        }
    }

    private static boolean isToolType(ItemStack stack, String toolName) {
        Item item = stack.getItem();
        String regName = item.getRegistryName() != null ? item.getRegistryName().toString() : "";
        return regName.contains(toolName);
    }

    private static void rule(String tag, Predicate<ItemStack> test) {
        RULES.add(new DiscoveryRule(tag, test));
    }
}