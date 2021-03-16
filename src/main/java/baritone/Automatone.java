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

package baritone;

import baritone.api.cache.IWorldProvider;
import baritone.cache.WorldProvider;
import baritone.command.defaults.DefaultCommands;
import baritone.entity.fakeplayer.FakeServerPlayerEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Automatone implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("Automatone");
    public static final String MOD_ID = "automatone";
    public static final EntityType<FakeServerPlayerEntity> FAKE_PLAYER = FabricEntityTypeBuilder.<FakeServerPlayerEntity>createLiving()
            .spawnGroup(SpawnGroup.MISC)
            .entityFactory((type, world) -> new FakeServerPlayerEntity(type, (ServerWorld) world))
            .defaultAttributes(PlayerEntity::createPlayerAttributes)
            .dimensions(EntityDimensions.changing(EntityType.PLAYER.getWidth(), EntityType.PLAYER.getHeight()))
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)
            .forceTrackedVelocityUpdates(true)
            .build();

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DefaultCommands.registerAll();
        Registry.register(Registry.ENTITY_TYPE, id("fake_player"), FAKE_PLAYER);
        ServerWorldEvents.LOAD.register((minecraftServer, serverWorld) ->
                ((WorldProvider) IWorldProvider.KEY.get(serverWorld)).initWorld(serverWorld));
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverWorld) ->
                ((WorldProvider) IWorldProvider.KEY.get(serverWorld)).closeWorld());
        ServerTickEvents.START_SERVER_TICK.register(minecraftServer -> BaritoneProvider.INSTANCE.tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BaritoneProvider.INSTANCE.shutdown());
    }
}
