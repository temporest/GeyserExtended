/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.registry.populator.conversion;

import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Downgrades blocks and items for the 1.21.90-1.21.101 Bedrock palettes, which predate
 * shelves, copper blocks/utilities, and the iron chain rename.
 */
public class Pre1_21_110Converter extends GoldenDandelionConverter {
    public static final Pre1_21_110Converter INSTANCE = new Pre1_21_110Converter();

    /** Blocks whose target has no states - clear all of the source states. */
    private static final Map<String, String> BLOCK_MAPPINGS = new HashMap<>();
    /** Blocks whose target shares the source states - keep them. */
    private static final Map<String, String> STATEFUL_BLOCK_MAPPINGS = new HashMap<>();
    /** Lightning rod variants carry a powered_bit state that the older lightning rod lacks. */
    private static final Set<String> REMOVE_POWERED_BIT = new HashSet<>();

    private static final List<String> SHELF_WOODS = List.of(
        "acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle",
        "mangrove", "oak", "pale_oak", "spruce", "warped"
    );
    private static final List<String> COPPER_VARIANTS = List.of(
        "", "exposed_", "oxidized_", "weathered_", "waxed_",
        "waxed_exposed_", "waxed_oxidized_", "waxed_weathered_"
    );

    static {
        for (String wood : SHELF_WOODS) {
            BLOCK_MAPPINGS.put("minecraft:" + wood + "_shelf", "minecraft:bookshelf");
        }
        for (String variant : COPPER_VARIANTS) {
            String prefix = "minecraft:" + variant;
            STATEFUL_BLOCK_MAPPINGS.put(prefix + "copper_bars", "minecraft:iron_bars");
            STATEFUL_BLOCK_MAPPINGS.put(prefix + "copper_chain", "minecraft:chain");
            STATEFUL_BLOCK_MAPPINGS.put(prefix + "copper_chest", "minecraft:chest");
            STATEFUL_BLOCK_MAPPINGS.put(prefix + "copper_lantern", "minecraft:lantern");
            BLOCK_MAPPINGS.put(prefix + "copper_golem_statue", "minecraft:iron_block");
        }
        // Every lightning rod dropped the powered_bit state before 1.21.110
        for (String variant : COPPER_VARIANTS) {
            String name = "minecraft:" + variant + "lightning_rod";
            STATEFUL_BLOCK_MAPPINGS.put(name, "minecraft:lightning_rod");
            REMOVE_POWERED_BIT.add(name);
        }
        STATEFUL_BLOCK_MAPPINGS.put("minecraft:iron_chain", "minecraft:chain");
        STATEFUL_BLOCK_MAPPINGS.put("minecraft:copper_torch", "minecraft:torch");
    }

    @Override
    public NbtMap remapBlock(NbtMap tag) {
        tag = super.remapBlock(tag);

        String name = tag.getString("name");
        String replacement = BLOCK_MAPPINGS.get(name);
        if (replacement != null) {
            return ConversionHelper.withoutStates(replacement);
        }
        replacement = STATEFUL_BLOCK_MAPPINGS.get(name);
        if (replacement != null) {
            if (REMOVE_POWERED_BIT.contains(name)) {
                tag = ConversionHelper.removeStates(tag, "powered_bit");
            }
            return ConversionHelper.withId(tag, replacement);
        }
        return tag;
    }

    public static Map<Item, Item> itemMappings() {
        Map<Item, Item> mappings = new HashMap<>(GoldenDandelionConverter.itemMappings());

        for (Item shelf : List.of(
            Items.ACACIA_SHELF, Items.BAMBOO_SHELF, Items.BIRCH_SHELF, Items.CHERRY_SHELF,
            Items.CRIMSON_SHELF, Items.DARK_OAK_SHELF, Items.JUNGLE_SHELF, Items.MANGROVE_SHELF,
            Items.OAK_SHELF, Items.PALE_OAK_SHELF, Items.SPRUCE_SHELF, Items.WARPED_SHELF
        )) {
            mappings.put(shelf, Items.BOOKSHELF);
        }

        // Copper utilities - use their iron/stone counterparts
        mappings.put(Items.COPPER_AXE, Items.IRON_AXE);
        mappings.put(Items.COPPER_BOOTS, Items.IRON_BOOTS);
        mappings.put(Items.COPPER_CHESTPLATE, Items.IRON_CHESTPLATE);
        mappings.put(Items.COPPER_HELMET, Items.IRON_HELMET);
        mappings.put(Items.COPPER_HOE, Items.IRON_HOE);
        mappings.put(Items.COPPER_HORSE_ARMOR, Items.IRON_HORSE_ARMOR);
        mappings.put(Items.COPPER_LEGGINGS, Items.IRON_LEGGINGS);
        mappings.put(Items.COPPER_NUGGET, Items.IRON_NUGGET);
        mappings.put(Items.COPPER_PICKAXE, Items.IRON_PICKAXE);
        mappings.put(Items.COPPER_SHOVEL, Items.IRON_SHOVEL);
        mappings.put(Items.COPPER_SWORD, Items.IRON_SWORD);
        mappings.put(Items.COPPER_GOLEM_SPAWN_EGG, Items.IRON_GOLEM_SPAWN_EGG);
        mappings.put(Items.COPPER_TORCH, Items.TORCH);

        // Weathered copper block families
        for (Map.Entry<Item, Item> entry : Map.ofEntries(
            Map.entry(Items.COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.EXPOSED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.WEATHERED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.OXIDIZED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.WAXED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.WAXED_EXPOSED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.WAXED_WEATHERED_COPPER_BARS, Items.IRON_BARS),
            Map.entry(Items.WAXED_OXIDIZED_COPPER_BARS, Items.IRON_BARS),

            Map.entry(Items.IRON_CHAIN, Items.IRON_BARS),
            Map.entry(Items.COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.EXPOSED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.WEATHERED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.OXIDIZED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.WAXED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.WAXED_EXPOSED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.WAXED_WEATHERED_COPPER_CHAIN, Items.IRON_BARS),
            Map.entry(Items.WAXED_OXIDIZED_COPPER_CHAIN, Items.IRON_BARS),

            Map.entry(Items.COPPER_CHEST, Items.CHEST),
            Map.entry(Items.EXPOSED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.WEATHERED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.OXIDIZED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.WAXED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.WAXED_EXPOSED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.WAXED_WEATHERED_COPPER_CHEST, Items.CHEST),
            Map.entry(Items.WAXED_OXIDIZED_COPPER_CHEST, Items.CHEST),

            Map.entry(Items.COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.EXPOSED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.WEATHERED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.OXIDIZED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.WAXED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.WAXED_EXPOSED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.WAXED_WEATHERED_COPPER_LANTERN, Items.LANTERN),
            Map.entry(Items.WAXED_OXIDIZED_COPPER_LANTERN, Items.LANTERN),

            Map.entry(Items.COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.EXPOSED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.WEATHERED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.OXIDIZED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.WAXED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.WAXED_EXPOSED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.WAXED_WEATHERED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),
            Map.entry(Items.WAXED_OXIDIZED_COPPER_GOLEM_STATUE, Items.IRON_BLOCK),

            Map.entry(Items.EXPOSED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.WEATHERED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.OXIDIZED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.WAXED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.WAXED_EXPOSED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.WAXED_WEATHERED_LIGHTNING_ROD, Items.LIGHTNING_ROD),
            Map.entry(Items.WAXED_OXIDIZED_LIGHTNING_ROD, Items.LIGHTNING_ROD)
        ).entrySet()) {
            mappings.put(entry.getKey(), entry.getValue());
        }

        return mappings;
    }
}
