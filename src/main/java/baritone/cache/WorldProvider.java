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

import baritone.Automatone;
import baritone.api.cache.IWorldProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Brady
 * @since 8/4/2018
 */
public class WorldProvider implements IWorldProvider {

    private static final Map<Path, WorldData> worldCache = new HashMap<>(); // this is how the bots have the same cached world

    private WorldData currentWorld;

    @Override
    public final WorldData getCurrentWorld() {
        return this.currentWorld;
    }

    /**
     * Called when a new world is initialized to discover the
     *
     * @param world The world being loaded
     */
    public final void initWorld(ServerWorld world) {
        Path directory;
        Path readme;

        MinecraftServer server = world.getServer();

        // If there is an integrated server running (Aka Singleplayer) then do magic to find the world save file
        directory = DimensionType.getSaveDirectory(world.getRegistryKey(), server.getSavePath(WorldSavePath.ROOT).toFile()).toPath();

        // Gets the "depth" of this directory relative the the game's run directory, 2 is the location of the world
        if (directory.relativize(FabricLoader.getInstance().getGameDir()).getNameCount() != 2) {
            // subdirectory of the main save directory for this world
            directory = directory.getParent();
        }

        directory = directory.resolve("automatone");
        readme = directory.resolve("readme.txt");

        // We will actually store the world data in a subfolder: "DIM<id>"
        Path dir = DimensionType.getSaveDirectory(world.getRegistryKey(), directory.toFile()).toPath();
        try {
            Files.createDirectories(dir);
            // lol wtf is this baritone folder in my minecraft save?
            // good thing we have a readme
            Files.write(readme, "https://github.com/Ladysnake/Automatone\n".getBytes());
        } catch (IOException ignored) {}

        Automatone.LOGGER.info("Automatone world data dir: " + dir);
        synchronized (worldCache) {
            this.currentWorld = worldCache.computeIfAbsent(dir, d -> new WorldData(d, world.getRegistryKey()));
        }
    }

    public final void closeWorld() {
        WorldData worldData = this.currentWorld;
        this.currentWorld = null;
        if (worldData != null) {
            worldData.onClose();
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        // NO-OP
    }
}
