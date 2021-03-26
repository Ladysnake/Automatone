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
import baritone.api.fakeplayer.FakeServerPlayerEntity;
import baritone.cache.WorldProvider;
import baritone.command.defaults.DefaultCommands;
import baritone.command.manager.BaritoneArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.tag.TagRegistry;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@KeepName
public final class Automatone implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("Automatone");
    public static final String MOD_ID = "automatone";

    public static final Tag<Item> EMPTY_BUCKETS = TagRegistry.item(id("empty_buckets"));
    public static final Tag<Item> WATER_BUCKETS = TagRegistry.item(id("water_buckets"));

    public static final EntityType<PlayerEntity> FAKE_PLAYER = FabricEntityTypeBuilder.<PlayerEntity>createLiving()
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
        ArgumentTypes.register("automatone:command", BaritoneArgumentType.class, new ConstantArgumentSerializer<>(BaritoneArgumentType::baritone));
        Registry.register(Registry.ENTITY_TYPE, id("fake_player"), FAKE_PLAYER);
        ServerWorldEvents.LOAD.register((minecraftServer, serverWorld) ->
                ((WorldProvider) IWorldProvider.KEY.get(serverWorld)).initWorld(serverWorld));
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverWorld) ->
                ((WorldProvider) IWorldProvider.KEY.get(serverWorld)).closeWorld());
    }
}
