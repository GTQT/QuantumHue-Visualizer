package meowmel.quantumhue.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which wiki pages have been discovered by the player.
 * Data is stored in the player's persisted NBT so it survives death.
 */
public final class WikiDiscovery {

    private static final String NBT_KEY = "wiki_discovered";
    private static final Set<String> discovered = new HashSet<>();
    private static boolean loaded = false;

    private WikiDiscovery() {
    }

    /**
     * Pages with tier 0 are always visible (introductory content).
     */
    public static boolean isDiscovered(WikiPage page) {
        if (page.tier <= 0) return true;
        ensureLoaded();
        return discovered.contains(page.id);
    }

    /**
     * Discover a page by its id. Returns true if newly discovered.
     */
    public static boolean discover(String pageId) {
        ensureLoaded();
        if (discovered.add(pageId)) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Discover all pages that match a given tier or below.
     */
    public static void discoverTier(int tier) {
        boolean changed = false;
        for (WikiCategory cat : WikiContent.getCategories()) {
            for (WikiPage page : cat.pages) {
                if (page.tier <= tier && discovered.add(page.id)) {
                    changed = true;
                }
            }
        }
        if (changed) save();
    }

    /**
     * Discover a specific page and save. Used from item pickup / craft triggers.
     */
    public static void discoverByTag(String tag) {
        boolean changed = false;
        for (WikiCategory cat : WikiContent.getCategories()) {
            for (WikiPage page : cat.pages) {
                if (tag.equals(page.discoveryTag) && discovered.add(page.id)) {
                    changed = true;
                }
            }
        }
        if (changed) save();
    }

    /**
     * Reset state on logout so it reloads for the next player.
     */
    public static void reset() {
        discovered.clear();
        loaded = false;
    }

    public static int discoveredCount() {
        ensureLoaded();
        return discovered.size();
    }

    public static int totalCount() {
        int count = 0;
        for (WikiCategory cat : WikiContent.getCategories()) {
            count += cat.pages.size();
        }
        return count;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        NBTTagCompound persisted = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(NBT_KEY)) return;

        NBTTagList list = persisted.getTagList(NBT_KEY, Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            discovered.add(list.getStringTagAt(i));
        }
    }

    private static void save() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound persisted = playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)
                ? playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                : new NBTTagCompound();

        NBTTagList list = new NBTTagList();
        for (String id : discovered) {
            list.appendTag(new NBTTagString(id));
        }
        persisted.setTag(NBT_KEY, list);
        playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
}