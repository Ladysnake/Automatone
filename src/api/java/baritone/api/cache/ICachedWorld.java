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

package baritone.api.cache;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;

/**
 * @author Brady
 * @since 9/24/2018
 */
public interface ICachedWorld {

    /**
     * Returns whether or not the block at the specified X and Z coordinates
     * is cached in this world.
     *
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @return Whether or not the specified XZ location is cached
     */
    boolean isCached(int blockX, int blockZ);

    /**
     * Scans the cached chunks for location of the specified special block. The
     * information that is returned by this method may not be up to date, because
     * older cached chunks can contain data that is much more likely to have changed.
     *
     * @param block               The special block to search for
     * @param maximum             The maximum number of position results to receive
     * @param centerX             The x block coordinate center of the search
     * @param centerZ             The z block coordinate center of the search
     * @param maxRegionDistanceSq The maximum region distance, squared
     * @return The locations found that match the special block
     */
    ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq);

}
