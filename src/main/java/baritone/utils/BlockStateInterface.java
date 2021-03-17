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

package baritone.utils;

import baritone.api.utils.IEntityContext;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Wraps get for chuck caching capability
 *
 * @author leijurv
 */
public class BlockStateInterface {

    private final ServerChunkManagerAccessor provider;
    protected final BlockView world;
    public final BlockPos.Mutable isPassableBlockPos;
    public final BlockView access;

    private WorldChunk prev = null;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    public BlockStateInterface(IEntityContext ctx) {
        this(ctx.world());
    }

    public BlockStateInterface(World world) {
        this.world = world;
        this.provider = (ServerChunkManagerAccessor) world.getChunkManager();
        this.isPassableBlockPos = new BlockPos.Mutable();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return provider.automatone$getChunkNow(blockX >> 4, blockZ >> 4) != null;
    }

    public static Block getBlock(IEntityContext ctx, BlockPos pos) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return get(ctx, pos).getBlock();
    }

    public static BlockState get(IEntityContext ctx, BlockPos pos) {
        return ctx.world().getBlockState(pos);
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) { // Mickey resigned
        if (World.isOutOfBuildLimitVertically(y)) return AIR;

        WorldChunk cached = prev;
        // there's great cache locality in block state lookups
        // generally it's within each movement
        // if it's the same chunk as last time
        // we can just skip the mc.world.getChunk lookup
        // which is a Long2ObjectOpenHashMap.get
        // see issue #113
        if (cached != null && cached.getPos().x == x >> 4 && cached.getPos().z == z >> 4) {
            return getFromChunk(cached, x, y, z);
        }
        WorldChunk chunk = provider.automatone$getChunkNow(x >> 4, z >> 4);
        if (chunk != null && !chunk.isEmpty()) {
            prev = chunk;
            return getFromChunk(chunk, x, y, z);
        }
        return AIR;
    }

    public boolean isLoaded(int x, int z) {
        WorldChunk prevChunk = prev;
        if (prevChunk != null && prevChunk.getPos().x == x >> 4 && prevChunk.getPos().z == z >> 4) {
            return true;
        }
        prevChunk = provider.automatone$getChunkNow(x >> 4, z >> 4);
        if (prevChunk != null && !prevChunk.isEmpty()) {
            prev = prevChunk;
            return true;
        }
        return false;
    }

    // get the block at x,y,z from this chunk WITHOUT creating a single blockpos object
    public static BlockState getFromChunk(Chunk chunk, int x, int y, int z) {
        ChunkSection section = chunk.getSectionArray()[y >> 4];
        if (ChunkSection.isEmpty(section)) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}
