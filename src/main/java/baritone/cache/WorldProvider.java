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

import baritone.api.cache.IWorldProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.World;

/**
 * @author Brady
 * @since 8/4/2018
 */
public class WorldProvider implements IWorldProvider {
    private final WorldData currentWorld;

    public WorldProvider(World world) {
        this.currentWorld = new WorldData(world.getRegistryKey());
    }

    @Override
    public final WorldData getCurrentWorld() {
        return this.currentWorld;
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        this.currentWorld.readFromNbt(tag);
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        this.currentWorld.writeToNbt(tag);
    }
}
