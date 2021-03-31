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

package baritone.cache;

import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IContainerMemory;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.IWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Data about a world, from baritone's point of view. Includes cached chunks, waypoints, and map data.
 *
 * @author leijurv
 */
public class WorldData implements IWorldData {

    private final WaypointCollection waypoints;
    private final ContainerMemory containerMemory;
    //public final MapData map;
    public final RegistryKey<World> dimension;

    WorldData(RegistryKey<World> dimension) {
        this.waypoints = new WaypointCollection();
        this.containerMemory = new ContainerMemory();
        this.dimension = dimension;
    }

    public void readFromNbt(CompoundTag tag) {
        this.containerMemory.read(tag.getCompound("containers"));
        this.waypoints.readFromNbt(tag.getCompound("waypoints"));
    }

    public void writeToNbt(CompoundTag tag) {
        tag.put("containers", containerMemory.toNbt());
        tag.put("waypoints", waypoints.toNbt());
    }

    @Override
    public ICachedWorld getCachedWorld() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IWaypointCollection getWaypoints() {
        return this.waypoints;
    }

    @Override
    public IContainerMemory getContainerMemory() {
        return this.containerMemory;
    }
}
