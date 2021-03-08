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
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class EntityContext implements IEntityContext {

    private LivingEntity entity;

    public EntityContext(ServerPlayerEntity entity) {
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
        return PrimaryPlayerController.INSTANCE;
    }

    @Override
    public World world() {
        return this.entity.world;
    }

    @Override
    public IWorldData worldData() {
        return BaritoneAPI.getProvider().getBaritoneForPlayer(this.entity).getPlayerContext().worldData();
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(entity(), entityRotations(), playerController().getBlockReachDistance());
    }
}
