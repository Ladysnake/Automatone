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

import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommandSystem;
import baritone.api.event.events.RenderEvent;
import baritone.api.schematic.ISchematicSystem;
import baritone.api.selection.ISelectionManager;
import baritone.behavior.PathingBehavior;
import baritone.cache.WorldProvider;
import baritone.cache.WorldScanner;
import baritone.command.CommandSystem;
import baritone.command.ExampleBaritoneControl;
import baritone.selection.SelectionManager;
import baritone.selection.SelectionRenderer;
import baritone.utils.PathRenderer;
import baritone.utils.player.EntityContext;
import baritone.utils.player.PrimaryPlayerContext;
import baritone.utils.schematic.SchematicSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider, ModInitializer {

    private static final SelectionManager selectionManager = new SelectionManager();
    public static final List<Consumer<RenderEvent>> extraRenderers = new ArrayList<>();
    public static final BaritoneProvider INSTANCE = new BaritoneProvider();

    private final Map<RegistryKey<World>, WorldProvider> worldProviders = new HashMap<>();
    private final Map<LivingEntity, IBaritone> all = new WeakHashMap<>();
    private Baritone clientBaritone;
    public ExampleBaritoneControl autocompleteHandler;

    public static ISelectionManager getSelectionManager() {
        return selectionManager;
    }

    public void onInitialize() {
        this.clientBaritone = new Baritone(PrimaryPlayerContext.INSTANCE, worldProviders.computeIfAbsent(World.OVERWORLD, r -> new WorldProvider()));
        this.autocompleteHandler = new ExampleBaritoneControl(this.clientBaritone.getCommandManager());
        ServerTickEvents.START_SERVER_TICK.register(minecraftServer -> {
            for (IBaritone baritone : all.values()) {
                baritone.getGameEventHandler().onTickServer();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            this.all.values().forEach(b -> ((PathingBehavior) b.getPathingBehavior()).shutdown());
            this.worldProviders.values().forEach(WorldProvider::closeWorld);
        });
        ServerWorldEvents.LOAD.register((minecraftServer, serverWorld) ->
                worldProviders.computeIfAbsent(serverWorld.getRegistryKey(), r -> new WorldProvider()).initWorld(serverWorld));
    }

    @Override
    public IBaritone getBaritone(LivingEntity entity) {
        if (entity.world.isClient()) throw new IllegalStateException("Lol we only support servers now");
        return all.computeIfAbsent(entity, p -> {
            Baritone baritone = new Baritone(new EntityContext(p), this.worldProviders.get(entity.world.getRegistryKey()));
            baritone.getGameEventHandler().registerEventListener(new ExampleBaritoneControl(baritone.getCommandManager()));
            return baritone;
        });
    }

    @Override
    public IBaritone getPrimaryBaritone() {
        return clientBaritone;
    }

    @Override
    public List<IBaritone> getAllBaritones() {
        return new ArrayList<>(all.values());
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return WorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }

    public void onRenderPass(RenderEvent renderEvent) {
        SelectionRenderer.renderSelections(renderEvent.getModelViewStack(), selectionManager.getSelections());

        // FIXME BOOM REACHING ACROSS SIDES
        for (IBaritone b : this.getAllBaritones()) {
            if (!b.getPlayerContext().world().isClient()) {
                PathRenderer.render(renderEvent, (PathingBehavior) b.getPathingBehavior());
            }
        }

        for (Consumer<RenderEvent> extra : extraRenderers) {
            extra.accept(renderEvent);
        }
    }
}
