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
import baritone.selection.SelectionManager;
import baritone.selection.SelectionRenderer;
import baritone.utils.GuiClick;
import baritone.utils.PathRenderer;
import baritone.utils.schematic.SchematicSystem;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider, ModInitializer {

    private static final SelectionManager selectionManager = new SelectionManager();
    public static final BaritoneProvider INSTANCE = new BaritoneProvider();

    private final Set<IBaritone> activeBaritones = new ReferenceOpenHashSet<>();
    private final Map<RegistryKey<World>, WorldProvider> worldProviders = new HashMap<>();

    // FIXME bad singleton
    public static ISelectionManager getSelectionManager() {
        return selectionManager;
    }

    public WorldProvider getWorldProvider(RegistryKey<World> id) {
        return this.worldProviders.get(id);
    }

    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(minecraftServer -> {
            for (IBaritone baritone : this.activeBaritones) {
                baritone.getGameEventHandler().onTickServer();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Iterator<IBaritone> iterator = this.activeBaritones.iterator(); iterator.hasNext(); ) {
                ((PathingBehavior) iterator.next().getPathingBehavior()).shutdown();
                iterator.remove();
            }
        });
        ServerWorldEvents.LOAD.register((minecraftServer, serverWorld) ->
                worldProviders.computeIfAbsent(serverWorld.getRegistryKey(), r -> new WorldProvider()).initWorld(serverWorld));
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverWorld) -> {
            WorldProvider worldProvider = this.worldProviders.remove(serverWorld.getRegistryKey());
            if (worldProvider != null) worldProvider.closeWorld();
        });
    }

    public void activate(IBaritone baritone) {
        this.activeBaritones.add(baritone);
    }

    public void deactivate(IBaritone baritone) {
        this.activeBaritones.remove(baritone);
    }

    public boolean isActive(IBaritone baritone) {
        return this.activeBaritones.contains(baritone);
    }

    @Override
    public IBaritone getBaritone(LivingEntity entity) {
        if (entity.world.isClient()) throw new IllegalStateException("Lol we only support servers now");
        return IBaritone.KEY.get(entity);
    }

    @Override
    public @Nullable IBaritone getActiveBaritone(LivingEntity entity) {
        IBaritone baritone = this.getBaritone(entity);
        return this.isActive(baritone) ? baritone : null;
    }

    @Override
    public Collection<IBaritone> getActiveBaritones() {
        return this.activeBaritones;
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

}
