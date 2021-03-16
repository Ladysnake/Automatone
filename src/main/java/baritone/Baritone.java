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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.cache.IWorldProvider;
import baritone.api.event.listener.IEventBus;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.IEntityContext;
import baritone.behavior.*;
import baritone.cache.WorldProvider;
import baritone.command.defaults.DefaultCommands;
import baritone.event.GameEventHandler;
import baritone.process.*;
import baritone.render.ClientPathingBehaviour;
import baritone.utils.*;
import baritone.command.manager.BaritoneCommandManager;
import baritone.utils.player.EntityContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Baritone implements IBaritone {

    private static final ThreadPoolExecutor threadPool;
    private static final File dir;

    static {
        AtomicInteger threadCounter = new AtomicInteger(0);
        threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "Baritone Worker " + threadCounter.incrementAndGet()));

        dir = FabricLoader.getInstance().getGameDir().resolve("baritone").toFile();
        if (!Files.exists(dir.toPath())) {
            try {
                Files.createDirectories(dir.toPath());
            } catch (IOException ignored) {}
        }
    }

    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final MemoryBehavior memoryBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final BackfillProcess backfillProcess;
    private final FarmProcess farmProcess;
    private final IBaritoneProcess execControlProcess;
    
    private final PathingControlManager pathingControlManager;
    private final BaritoneCommandManager commandManager;

    private final IEntityContext playerContext;
    private final WorldProvider worldProvider;

    private final @Nullable ClientPathingBehaviour clientPathingBehaviour;

    public BlockStateInterface bsi;

    public Baritone(LivingEntity player) {
        this.gameEventHandler = new GameEventHandler(this);

        // Define this before behaviors try and get it, or else it will be null and the builds will fail!
        this.playerContext = new EntityContext(player);

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

        this.worldProvider = (WorldProvider) IWorldProvider.KEY.get(player.world);
        this.commandManager = new BaritoneCommandManager(this);
        this.execControlProcess = DefaultCommands.controlCommands.registerProcess(this);
        this.clientPathingBehaviour = player.world.isClient ? new ClientPathingBehaviour(player) : null;
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
    public BaritoneCommandManager getCommandManager() {
        return this.commandManager;
    }

    public IBaritoneProcess getExecControlProcess() {
        return execControlProcess;
    }

    public ClientPathingBehaviour getClientPathingBehaviour() {
        if (this.clientPathingBehaviour == null) throw new IllegalStateException("Not a clientside baritone instance");
        return this.clientPathingBehaviour;
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
    public void readFromNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        // NO-OP
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return player.server.getPermissionLevel(player.getGameProfile()) >= 2;
    }

    @Override
    public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(this.isActive());
        this.pathingBehavior.writeToPacket(buf);
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        assert this.clientPathingBehaviour != null : "applySyncPacket called on a server world";
        boolean active = buf.readBoolean();
        if (active) {
            AutomatoneClient.renderList.add(this);
        } else {
            AutomatoneClient.renderList.remove(this);
        }
        this.clientPathingBehaviour.readFromPacket(buf);
    }

    public static Settings settings() {
        return BaritoneAPI.getSettings();
    }

    public static File getDir() {
        return dir;
    }

    public static ThreadPoolExecutor getExecutor() {
        return threadPool;
    }
}
