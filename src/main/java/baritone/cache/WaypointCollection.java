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

import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.Waypoint;
import baritone.api.utils.BetterBlockPos;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Waypoints for a world
 *
 * @author leijurv
 */
public class WaypointCollection implements IWaypointCollection {

    private final Map<IWaypoint.Tag, Set<IWaypoint>> waypoints;

    WaypointCollection() {
        this.waypoints = new EnumMap<>(Arrays.stream(IWaypoint.Tag.values())
                .collect(Collectors.toMap(Function.identity(), t -> new HashSet<>())));
    }

    public void readFromNbt(NbtCompound nbt) {
        for (Waypoint.Tag tag : Waypoint.Tag.values()) {
            this.waypoints.put(tag, readFromNbt(tag, nbt.getList(tag.name(), NbtType.COMPOUND)));
        }
    }

    private synchronized Set<IWaypoint> readFromNbt(Waypoint.Tag tag, NbtList nbt) {
        Set<IWaypoint> ret = new HashSet<>();
        for (int i = 0; i < nbt.size(); i++) {
            NbtCompound in = nbt.getCompound(i);
            String name = in.getString("name");
            long creationTimestamp = in.getLong("created");
            BetterBlockPos pos = new BetterBlockPos(NbtHelper.toBlockPos(in.getCompound("pos")));
            ret.add(new Waypoint(name, tag, pos, creationTimestamp));
        }
        return ret;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        for (IWaypoint.Tag waypointTag : IWaypoint.Tag.values()) {
            nbt.put(waypointTag.name(), save(waypointTag));
        }
        return nbt;
    }

    private synchronized NbtList save(Waypoint.Tag waypointTag) {
        NbtList list = new NbtList();
        for (IWaypoint waypoint : this.waypoints.get(waypointTag)) {
            NbtCompound serializedWaypoint = new NbtCompound();
            serializedWaypoint.putString("name", waypoint.getName());
            serializedWaypoint.putLong("created", waypoint.getCreationTimestamp());
            serializedWaypoint.put("pos", NbtHelper.fromBlockPos(waypoint.getLocation()));
            list.add(serializedWaypoint);
        }
        return list;
    }

    @Override
    public void addWaypoint(IWaypoint waypoint) {
        // no need to check for duplicate, because it's a Set not a List
        waypoints.get(waypoint.getTag()).add(waypoint);
    }

    @Override
    public void removeWaypoint(IWaypoint waypoint) {
        waypoints.get(waypoint.getTag()).remove(waypoint);
    }

    @Override
    public IWaypoint getMostRecentByTag(IWaypoint.Tag tag) {
        // Find a waypoint of the given tag which has the greatest timestamp value, indicating the most recent
        return this.waypoints.get(tag).stream().min(Comparator.comparingLong(w -> -w.getCreationTimestamp())).orElse(null);
    }

    @Override
    public Set<IWaypoint> getByTag(IWaypoint.Tag tag) {
        return Collections.unmodifiableSet(this.waypoints.get(tag));
    }

    @Override
    public Set<IWaypoint> getAllWaypoints() {
        return this.waypoints.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
