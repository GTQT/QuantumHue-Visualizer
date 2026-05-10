package meowmel.quantumhue.wiki;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.common.items.MetaItems;
import gregtech.common.items.ToolItems;
import gregtech.common.metatileentities.MetaTileEntities;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Resolves icon strings from wiki JSON into ItemStack Suppliers.
 * <p>
 * Supported formats:
 * item:minecraft:book         - vanilla item by registry name
 * block:minecraft:crafting_table - vanilla block by registry name
 * ore:prefix:material         - OreDictUnifier.get(OrePrefix, Material)
 * tool:toolname:material      - ToolItems field, e.g. tool:wrench:iron
 * machine:fieldname           - MetaTileEntities single field, e.g. machine:electric_blast_furnace
 * machine_array:fieldname:tier - MetaTileEntities array field, e.g. machine_array:macerator:1
 * machine_hull:tier           - MetaTileEntities.HULL[tier]
 * machine_transformer:tier    - MetaTileEntities.TRANSFORMER[tier]
 * metaitem:fieldname          - MetaItems field, e.g. metaitem:electric_pump_lv
 */
public final class WikiIconResolver {

    private WikiIconResolver() {
    }

    public static Supplier<ItemStack> resolve(String iconStr) {
        if (iconStr == null || iconStr.isEmpty()) {
            return () -> ItemStack.EMPTY;
        }

        String[] parts = iconStr.split(":");
        if (parts.length < 2) return () -> ItemStack.EMPTY;

        String type = parts[0];

        switch (type) {
            case "item":
                return resolveItem(joinFrom(parts, 1));
            case "block":
                return resolveBlock(joinFrom(parts, 1));
            case "ore":
                if (parts.length >= 3) return resolveOre(parts[1], parts[2]);
                break;
            case "tool":
                if (parts.length >= 3) return resolveTool(parts[1], parts[2]);
                break;
            case "machine":
                return resolveMachine(parts[1]);
            case "machine_array":
                if (parts.length >= 3) return resolveMachineArray(parts[1], parseInt(parts[2]));
                break;
            case "machine_hull":
                return resolveMachineArray("hull", parseInt(parts[1]));
            case "machine_transformer":
                return resolveMachineArray("transformer", parseInt(parts[1]));
            case "metaitem":
                return resolveMetaItem(parts[1]);
            default:
                break;
        }
        return () -> ItemStack.EMPTY;
    }

    private static Supplier<ItemStack> resolveItem(String name) {
        return () -> {
            Item item = Item.getByNameOrId(name);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveBlock(String name) {
        return () -> {
            Block block = Block.getBlockFromName(name);
            return block != null && block != Blocks.AIR ? new ItemStack(block) : ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveOre(String prefixName, String materialName) {
        return () -> {
            OrePrefix prefix = OrePrefix.getPrefix(prefixName);
            Material mat = findMaterial(materialName);
            if (prefix != null && mat != null) {
                return OreDictUnifier.get(prefix, mat);
            }
            return ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveTool(String toolName, String materialName) {
        return () -> {
            Material mat = findMaterial(materialName);
            if (mat == null) return ItemStack.EMPTY;
            try {
                String fieldName = toolName.toUpperCase();
                Field f = ToolItems.class.getField(fieldName);
                Object tool = f.get(null);
                if (tool != null) {
                    // ToolItems fields have a get(Material) method
                    java.lang.reflect.Method getMethod = tool.getClass().getMethod("get", Material.class);
                    Object result = getMethod.invoke(tool, mat);
                    if (result instanceof ItemStack) return (ItemStack) result;
                }
            } catch (Exception ignored) {
            }
            return ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveMachine(String fieldName) {
        return () -> {
            try {
                String name = fieldName.toUpperCase();
                Field f = MetaTileEntities.class.getField(name);
                Object mte = f.get(null);
                if (mte != null) {
                    java.lang.reflect.Method m = mte.getClass().getMethod("getStackForm");
                    Object result = m.invoke(mte);
                    if (result instanceof ItemStack) return (ItemStack) result;
                }
            } catch (Exception ignored) {
            }
            return ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveMachineArray(String fieldName, int index) {
        return () -> {
            try {
                String name = fieldName.toUpperCase();
                Field f = MetaTileEntities.class.getField(name);
                Object arr = f.get(null);
                if (arr != null && arr.getClass().isArray()) {
                    Object[] array = (Object[]) arr;
                    if (index >= 0 && index < array.length && array[index] != null) {
                        java.lang.reflect.Method m = array[index].getClass().getMethod("getStackForm");
                        Object result = m.invoke(array[index]);
                        if (result instanceof ItemStack) return (ItemStack) result;
                    }
                }
            } catch (Exception ignored) {
            }
            return ItemStack.EMPTY;
        };
    }

    private static Supplier<ItemStack> resolveMetaItem(String fieldName) {
        return () -> {
            try {
                String name = fieldName.toUpperCase();
                Field f = MetaItems.class.getField(name);
                Object item = f.get(null);
                if (item != null) {
                    java.lang.reflect.Method m = item.getClass().getMethod("getStackForm");
                    Object result = m.invoke(item);
                    if (result instanceof ItemStack) return (ItemStack) result;
                }
            } catch (Exception ignored) {
            }
            return ItemStack.EMPTY;
        };
    }

    private static Material findMaterial(String name) {
        // Try direct field lookup on Materials class
        try {
            String fieldName = name.substring(0, 1).toUpperCase() + name.substring(1);
            Field f = Materials.class.getField(fieldName);
            Object mat = f.get(null);
            if (mat instanceof Material) return (Material) mat;
        } catch (Exception ignored) {
        }

        // Try all-caps
        try {
            Field f = Materials.class.getField(name.toUpperCase());
            Object mat = f.get(null);
            if (mat instanceof Material) return (Material) mat;
        } catch (Exception ignored) {
        }

        // Try case-insensitive scan
        try {
            for (Field f : Materials.class.getFields()) {
                if (f.getName().equalsIgnoreCase(name) && Material.class.isAssignableFrom(f.getType())) {
                    Object mat = f.get(null);
                    if (mat instanceof Material) return (Material) mat;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String joinFrom(String[] parts, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append(':');
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}