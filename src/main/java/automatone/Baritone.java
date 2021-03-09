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

package automatone;

import automatone.api.BaritoneAPI;
import automatone.api.IBaritone;
import automatone.api.Settings;
import automatone.api.event.listener.IEventBus;
import automatone.api.utils.IEntityContext;
import automatone.behavior.*;
import automatone.cache.WorldProvider;
import automatone.event.GameEventHandler;
import automatone.process.*;
import automatone.utils.*;
import automatone.command.manager.CommandManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Baritone implements IBaritone {

    public static final Logger LOGGER = LogManager.getLogger("Automatone");
    private static ThreadPoolExecutor threadPool;
    private static File dir;

    static {
        threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

        dir = FabricLoader.getInstance().getGameDir().resolve("baritone").toFile();
        if (!Files.exists(dir.toPath())) {
            try {
                Files.createDirectories(dir.toPath());
            } catch (IOException ignored) {}
        }
    }

    private GameEventHandler gameEventHandler;

    private PathingBehavior pathingBehavior;
    private LookBehavior lookBehavior;
    private MemoryBehavior memoryBehavior;
    private InventoryBehavior inventoryBehavior;
    private InputOverrideHandler inputOverrideHandler;

    private FollowProcess followProcess;
    private MineProcess mineProcess;
    private GetToBlockProcess getToBlockProcess;
    private CustomGoalProcess customGoalProcess;
    private BuilderProcess builderProcess;
    private ExploreProcess exploreProcess;
    private BackfillProcess backfillProcess;
    private FarmProcess farmProcess;

    private PathingControlManager pathingControlManager;
    private CommandManager commandManager;

    private IEntityContext playerContext;
    private WorldProvider worldProvider;

    public BlockStateInterface bsi;

    Baritone(IEntityContext player, WorldProvider worldProvider) {
        this.gameEventHandler = new GameEventHandler(this);

        // Define this before behaviors try and get it, or else it will be null and the builds will fail!
        this.playerContext = player;

        {
            // the Behavior constructor calls baritone.registerBehavior(this) so this populates the behaviors arraylist
            pathingBehavior = new PathingBehavior(this);
            lookBehavior = new LookBehavior(this);
            memoryBehavior = new MemoryBehavior(this);
            inventoryBehavior = new InventoryBehavior(this);
            inputOverrideHandler = new InputOverrideHandler(this);
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.pathingControlManager.registerProcess(followProcess = new FollowProcess(this));
            this.pathingControlManager.registerProcess(mineProcess = new MineProcess(this));
            this.pathingControlManager.registerProcess(customGoalProcess = new CustomGoalProcess(this)); // very high iq
            this.pathingControlManager.registerProcess(getToBlockProcess = new GetToBlockProcess(this));
            this.pathingControlManager.registerProcess(builderProcess = new BuilderProcess(this));
            this.pathingControlManager.registerProcess(exploreProcess = new ExploreProcess(this));
            this.pathingControlManager.registerProcess(backfillProcess = new BackfillProcess(this));
            this.pathingControlManager.registerProcess(farmProcess = new FarmProcess(this));
        }

        this.worldProvider = worldProvider;
        this.commandManager = new CommandManager(this);

        if (BaritoneAutoTest.ENABLE_AUTO_TEST) {
            this.gameEventHandler.registerEventListener(BaritoneAutoTest.INSTANCE);
        }
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    public void registerBehavior(Behavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IEntityContext getPlayerContext() {
        return this.playerContext;
    }

    public MemoryBehavior getMemoryBehavior() {
        return this.memoryBehavior;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public void activate() {
        BaritoneProvider.INSTANCE.activate(this);
    }

    @Override
    public void deactivate() {
        BaritoneProvider.INSTANCE.deactivate(this);
    }

    @Override
    public boolean isActive() {
        return BaritoneProvider.INSTANCE.isActive(this);
    }

    @Override
    public void openClick() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().openScreen(new GuiClick()));
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        // NO-OP
    }

    public static Settings settings() {
        return BaritoneAPI.getSettings();
    }

    public static File getDir() {
        return dir;
    }

    public static Executor getExecutor() {
        return threadPool;
    }
}
