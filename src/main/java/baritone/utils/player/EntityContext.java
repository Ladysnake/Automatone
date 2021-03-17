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

package baritone.utils.player;

import baritone.api.BaritoneAPI;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

public class EntityContext implements IEntityContext {

    private final LivingEntity entity;

    public EntityContext(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public @Nullable PlayerInventory inventory() {
        return entity instanceof PlayerEntity ? ((PlayerEntity) entity).inventory : null;
    }

    @Override
    public IPlayerController playerController() {
        return IPlayerController.KEY.get(this.entity);
    }

    @Override
    public ServerWorld world() {
        World world = this.entity.world;
        if (world.isClient) throw new IllegalStateException();
        return (ServerWorld) world;
    }

    @Override
    public IWorldData worldData() {
        return BaritoneAPI.getProvider().getBaritone(this.entity).getPlayerContext().worldData();
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(entity(), entityRotations(), playerController().getBlockReachDistance());
    }

    @Override
    public BetterBlockPos feetPos() {
        // TODO find a better way to deal with soul sand!!!!!
        double x = entity().getX();
        double z = entity().getZ();
        BetterBlockPos feet = new BetterBlockPos(x, entity().getY() + 0.1251, z);

        ServerWorld world = world();
        if (world != null) {
            WorldChunk chunk = ((ServerChunkManagerAccessor) world.getChunkManager()).automatone$getChunkNow((int) x << 4, (int) z << 4);
            if (chunk != null && chunk.getBlockState(feet).getBlock() instanceof SlabBlock) {
                return feet.up();
            }
        }

        return feet;
    }
}
