package meowmel.quantumhue.wiki;

import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public class DiscoveryRule {

    final String tag;
    final Predicate<ItemStack> test;

    DiscoveryRule(String tag, Predicate<ItemStack> test) {
        this.tag = tag;
        this.test = test;
    }
}