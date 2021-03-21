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
import baritone.api.Settings;
import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommandSystem;
import baritone.api.schematic.ISchematicSystem;
import baritone.behavior.PathingBehavior;
import baritone.cache.WorldScanner;
import baritone.command.CommandSystem;
import baritone.utils.SettingsLoader;
import baritone.utils.schematic.SchematicSystem;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider {

    public static final BaritoneProvider INSTANCE = new BaritoneProvider();

    private final Set<IBaritone> activeBaritones = new ReferenceOpenHashSet<>();
    private final Settings settings;

    public BaritoneProvider() {
        this.settings = new Settings();
        SettingsLoader.readAndApply(settings);
    }

    public void tick() {
        for (IBaritone baritone : this.activeBaritones) {
            baritone.getGameEventHandler().onTickServer();
        }
    }

    public void shutdown() {
        for (Iterator<IBaritone> iterator = this.activeBaritones.iterator(); iterator.hasNext(); ) {
            IBaritone b = iterator.next();
            iterator.remove();  // remove first, so that the instance sees itself as deactivated
            ((PathingBehavior) b.getPathingBehavior()).shutdown();
        }
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
        IBaritone baritone = IBaritone.KEY.getNullable(entity);
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

    @Override
    public Settings getSettings() {
        return this.settings;
    }
}
