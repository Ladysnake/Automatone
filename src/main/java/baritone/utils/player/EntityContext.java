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
import baritone.api.pathing.calc.Avoidance;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class EntityContext implements IEntityContext {

    private final LivingEntity entity;
    private @Nullable Supplier<List<Avoidance>> avoidanceFinder;

    public EntityContext(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public @Nullable PlayerInventory inventory() {
        return entity instanceof PlayerEntity ? ((PlayerEntity) entity).getInventory() : null;
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

    private Stream<Entity> streamHostileEntities() {
        return this.worldEntitiesStream()
                .filter(entity -> entity instanceof MobEntity)
                .filter(entity -> (!(entity instanceof SpiderEntity)) || entity.getLightLevelDependentValue() < 0.5)
                .filter(entity -> !(entity instanceof ZombifiedPiglinEntity) || ((ZombifiedPiglinEntity) entity).getAttacker() != null)
                .filter(entity -> !(entity instanceof EndermanEntity) || ((EndermanEntity) entity).isAngry());
    }

    @Override
    public void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder) {
        this.avoidanceFinder = avoidanceFinder;
    }

    @Override
    public List<Avoidance> listAvoidedAreas() {
        if (!baritone().settings().avoidance.get()) {
            return Collections.emptyList();
        }

        if (this.avoidanceFinder != null) {
            return this.avoidanceFinder.get();
        }

        List<Avoidance> res = new ArrayList<>();
        double mobCoeff = baritone().settings().mobAvoidanceCoefficient.get();

        if (mobCoeff != 1.0D) {
            streamHostileEntities().forEach(entity -> res.add(new Avoidance(
                    entity.getBlockPos(),
                    mobCoeff,
                    baritone().settings().mobAvoidanceRadius.get()
            )));
        }

        return res;
    }
}
