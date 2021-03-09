/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package automatone.cache;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * @author Brady
 * @since 8/3/2018
 */
public final class CachedChunk {

    public static final ImmutableSet<Block> BLOCKS_TO_KEEP_TRACK_OF = ImmutableSet.of(
            Blocks.ENDER_CHEST,
            Blocks.FURNACE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.END_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.SPAWNER,
            Blocks.BARRIER,
            Blocks.OBSERVER,
            Blocks.WHITE_SHULKER_BOX,
            Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX,
            Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX,
            Blocks.GRAY_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX,
            Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX,
            Blocks.NETHER_PORTAL,
            Blocks.HOPPER,
            Blocks.BEACON,
            Blocks.BREWING_STAND,

// TODO: Maybe add a predicate for blocks to keep track of?
// This should really not need to happen
            Blocks.CREEPER_HEAD,
            Blocks.CREEPER_WALL_HEAD,
            Blocks.DRAGON_HEAD,
            Blocks.DRAGON_WALL_HEAD,
            Blocks.PLAYER_HEAD,
            Blocks.PLAYER_WALL_HEAD,
            Blocks.ZOMBIE_HEAD,
            Blocks.ZOMBIE_WALL_HEAD,
            Blocks.SKELETON_SKULL,
            Blocks.SKELETON_WALL_SKULL,
            Blocks.WITHER_SKELETON_SKULL,
            Blocks.WITHER_SKELETON_WALL_SKULL,
            Blocks.ENCHANTING_TABLE,
            Blocks.ANVIL,
            Blocks.WHITE_BED,
            Blocks.ORANGE_BED,
            Blocks.MAGENTA_BED,
            Blocks.LIGHT_BLUE_BED,
            Blocks.YELLOW_BED,
            Blocks.LIME_BED,
            Blocks.PINK_BED,
            Blocks.GRAY_BED,
            Blocks.LIGHT_GRAY_BED,
            Blocks.CYAN_BED,
            Blocks.PURPLE_BED,
            Blocks.BLUE_BED,
            Blocks.BROWN_BED,
            Blocks.GREEN_BED,
            Blocks.RED_BED,
            Blocks.BLACK_BED,
            Blocks.DRAGON_EGG,
            Blocks.JUKEBOX,
            Blocks.END_GATEWAY,
            Blocks.COBWEB,
            Blocks.NETHER_WART,
            Blocks.LADDER,
            Blocks.VINE
    );

}
