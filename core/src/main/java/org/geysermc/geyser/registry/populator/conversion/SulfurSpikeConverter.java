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

import java.util.Map;

/**
 * Downgrades the sulfur spike for Bedrock versions that predate it.
 */
public class SulfurSpikeConverter extends ICanHasStates {
    public static final SulfurSpikeConverter INSTANCE = new SulfurSpikeConverter();

    private static final Map<Item, Item> ITEM_MAPPINGS = Map.of(
        Items.SULFUR_SPIKE, Items.POINTED_DRIPSTONE,
        Items.MUSIC_DISC_BOUNCE, Items.MUSIC_DISC_BLOCKS
    );

    @Override
    public NbtMap remapBlock(NbtMap tag) {
        tag = super.remapBlock(tag);

        String name = tag.getString("name");
        if (name.equals("minecraft:sulfur_spike")) {
            return ConversionHelper.withId(tag, "minecraft:pointed_dripstone");
        } else if (name.equals("minecraft:potent_sulfur")) {
            // 26.20 predates the potent sulfur states
            return ConversionHelper.withoutStates("minecraft:potent_sulfur");
        }
        return tag;
    }

    public static Map<Item, Item> itemMappings() {
        return ITEM_MAPPINGS;
    }
}
