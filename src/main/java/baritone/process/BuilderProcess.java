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

package baritone.process;

import baritone.Automatone;
import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.*;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.NotificationHelper;
import baritone.utils.PathingCommandContext;
import baritone.utils.schematic.MapArtSchematic;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.schematica.SchematicaHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

public final class BuilderProcess extends BaritoneProcessHelper implements IBuilderProcess {

    private HashSet<BetterBlockPos> incorrectPositions;
    private LongOpenHashSet observedCompleted; // positions that are completed even if they're out of render distance and we can't make sure right now
    private String name;
    private ISchematic realSchematic;
    private ISchematic schematic;
    private Vec3i origin;
    private int ticks;
    private boolean paused;
    private int layer;
    private int numRepeats;
    private List<BlockState> approxPlaceable;

    public BuilderProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void build(String name, ISchematic schematic, Vec3i origin) {
        this.name = name;
        this.schematic = schematic;
        this.realSchematic = null;
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        if (baritone.settings().schematicOrientationX.get()) {
            x += schematic.widthX();
        }
        if (baritone.settings().schematicOrientationY.get()) {
            y += schematic.heightY();
        }
        if (baritone.settings().schematicOrientationZ.get()) {
            z += schematic.lengthZ();
        }
        this.origin = new Vec3i(x, y, z);
        this.paused = false;
        this.layer = baritone.settings().startAtLayer.get();
        this.numRepeats = 0;
        this.observedCompleted = new LongOpenHashSet();
    }

    public void resume() {
        paused = false;
    }

    public void pause() {
        paused = true;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean build(String name, File schematic, Vec3i origin) {
        Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(schematic);
        if (!format.isPresent()) {
            return false;
        }

        ISchematic parsed;
        try {
            parsed = format.get().parse(new FileInputStream(schematic));
        } catch (Exception e) {
            Automatone.LOGGER.error(e);
            return false;
        }

        if (baritone.settings().mapArtMode.get()) {
            parsed = new MapArtSchematic((IStaticSchematic) parsed);
        }

        build(name, parsed, origin);
        return true;
    }

    @Override
    public void buildOpenSchematic() {
        if (SchematicaHelper.isSchematicaPresent()) {
            Optional<Pair<IStaticSchematic, BlockPos>> schematic = SchematicaHelper.getOpenSchematic();
            if (schematic.isPresent()) {
                IStaticSchematic s = schematic.get().getLeft();
                this.build(
                        schematic.get().getLeft().toString(),
                        baritone.settings().mapArtMode.get() ? new MapArtSchematic(s) : s,
                        schematic.get().getRight()
                );
            } else {
                logDirect("No schematic currently open");
            }
        } else {
            logDirect("Schematica is not present");
        }
    }

    public void clearArea(BlockPos corner1, BlockPos corner2) {
        BlockPos origin = new BlockPos(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ()));
        int widthX = Math.abs(corner1.getX() - corner2.getX()) + 1;
        int heightY = Math.abs(corner1.getY() - corner2.getY()) + 1;
        int lengthZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        build("clear area", new FillSchematic(widthX, heightY, lengthZ, new BlockOptionalMeta(this.baritone.getPlayerContext().world(), Blocks.AIR)), origin);
    }

    @Override
    public List<BlockState> getApproxPlaceable() {
        return new ArrayList<>(approxPlaceable);
    }

    @Override
    public boolean isActive() {
        return schematic != null;
    }

    public BlockState placeAt(int x, int y, int z, BlockState current) {
        if (!isActive()) {
            return null;
        }
        if (!schematic.inSchematic(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current)) {
            return null;
        }
        BlockState state = schematic.desiredState(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current, this.approxPlaceable);
        if (state.getBlock() instanceof AirBlock) {
            return null;
        }
        return state;
    }

    private Optional<Pair<BetterBlockPos, Rotation>> toBreakNearPlayer(BuilderCalculationContext bcc) {
        BetterBlockPos center = ctx.feetPos();
        BetterBlockPos pathStart = baritone.getPathingBehavior().pathStart();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = baritone.settings().breakFromAbove.get() ? -1 : 0; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    if (dy == -1 && x == pathStart.x && z == pathStart.z) {
                        continue; // dont mine what we're supported by, but not directly standing on
                    }
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired == null) {
                        continue; // irrelevant
                    }
                    BlockState curr = bcc.bsi.get0(x, y, z);
                    if (!(curr.getBlock() instanceof AirBlock) && !(curr.getBlock() == Blocks.WATER || curr.getBlock() == Blocks.LAVA) && !valid(curr, desired, false)) {
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        Optional<Rotation> rot = RotationUtils.reachable(ctx.entity(), pos, ctx.playerController().getBlockReachDistance());
                        if (rot.isPresent()) {
                            return Optional.of(new Pair<>(pos, rot.get()));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static class Placement {

        private final int hotbarSelection;
        private final BlockPos placeAgainst;
        private final Direction side;
        private final Rotation rot;

        public Placement(int hotbarSelection, BlockPos placeAgainst, Direction side, Rotation rot) {
            this.hotbarSelection = hotbarSelection;
            this.placeAgainst = placeAgainst;
            this.side = side;
            this.rot = rot;
        }
    }

    private Optional<Placement> searchForPlacables(BuilderCalculationContext bcc, List<BlockState> desirableOnHotbar) {
        BetterBlockPos center = ctx.feetPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 1; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired == null) {
                        continue; // irrelevant
                    }
                    BlockState curr = bcc.bsi.get0(x, y, z);
                    if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi) && !valid(curr, desired, false)) {
                        if (dy == 1 && bcc.bsi.get0(x, y + 1, z).getBlock() instanceof AirBlock) {
                            continue;
                        }
                        desirableOnHotbar.add(desired);
                        Optional<Placement> opt = possibleToPlace(desired, x, y, z, bcc.bsi);
                        if (opt.isPresent()) {
                            return opt;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean placementPlausible(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getCollisionShape(ctx.world(), pos);
        return voxelshape.isEmpty() || ctx.world().intersectsEntities(null, voxelshape.offset(pos.getX(), pos.getY(), pos.getZ()));
    }

    private Optional<Placement> possibleToPlace(BlockState toPlace, int x, int y, int z, BlockStateInterface bsi) {
        for (Direction against : Direction.values()) {
            BetterBlockPos placeAgainstPos = new BetterBlockPos(x, y, z).offset(against);
            BlockState placeAgainstState = bsi.get0(placeAgainstPos);
            if (MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)) {
                continue;
            }
            if (!toPlace.canPlaceAt(ctx.world(), new BetterBlockPos(x, y, z))) {
                continue;
            }
            if (!placementPlausible(new BetterBlockPos(x, y, z), toPlace)) {
                continue;
            }
            Box aabb = placeAgainstState.getOutlineShape(ctx.world(), placeAgainstPos).getBoundingBox();
            for (Vec3d placementMultiplier : aabbSideMultipliers(against)) {
                double placeX = placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1 - placementMultiplier.x);
                double placeY = placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y);
                double placeZ = placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1 - placementMultiplier.z);
                Rotation rot = RotationUtils.calcRotationFromVec3d(RayTraceUtils.inferSneakingEyePosition(ctx.entity()), new Vec3d(placeX, placeY, placeZ), ctx.entityRotations());
                HitResult result = RayTraceUtils.rayTraceTowards(ctx.entity(), rot, ctx.playerController().getBlockReachDistance(), true);
                if (result != null && result.getType() == HitResult.Type.BLOCK && ((BlockHitResult) result).getBlockPos().equals(placeAgainstPos) && ((BlockHitResult) result).getSide() == against.getOpposite()) {
                    OptionalInt hotbar = hasAnyItemThatWouldPlace(toPlace, result, rot);
                    if (hotbar.isPresent()) {
                        return Optional.of(new Placement(hotbar.getAsInt(), placeAgainstPos, against.getOpposite(), rot));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private OptionalInt hasAnyItemThatWouldPlace(BlockState desired, HitResult result, Rotation rot) {
        if (!(ctx.entity() instanceof PlayerEntity)) return OptionalInt.empty();
        PlayerEntity player = (PlayerEntity) ctx.entity();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.main.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            float originalYaw = player.yaw;
            float originalPitch = player.pitch;
            // the state depends on the facing of the player sometimes
            player.yaw = rot.getYaw();
            player.pitch = rot.getPitch();
            ItemPlacementContext meme = new ItemPlacementContext(new ItemUsageContext(
                    ctx.world(),
                    player,
                    Hand.MAIN_HAND,
                    stack,
                    (BlockHitResult) result
            ) {}); // that {} gives us access to a protected constructor lmfao
            BlockState wouldBePlaced = ((BlockItem) stack.getItem()).getBlock().getPlacementState(meme);
            player.yaw = originalYaw;
            player.pitch = originalPitch;
            if (wouldBePlaced == null) {
                continue;
            }
            if (!meme.canPlace()) {
                continue;
            }
            if (valid(wouldBePlaced, desired, true)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private static Vec3d[] aabbSideMultipliers(Direction side) {
        switch (side) {
            case UP:
                return new Vec3d[]{new Vec3d(0.5, 1, 0.5), new Vec3d(0.1, 1, 0.5), new Vec3d(0.9, 1, 0.5), new Vec3d(0.5, 1, 0.1), new Vec3d(0.5, 1, 0.9)};
            case DOWN:
                return new Vec3d[]{new Vec3d(0.5, 0, 0.5), new Vec3d(0.1, 0, 0.5), new Vec3d(0.9, 0, 0.5), new Vec3d(0.5, 0, 0.1), new Vec3d(0.5, 0, 0.9)};
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                double x = side.getOffsetX() == 0 ? 0.5 : (1 + side.getOffsetX()) / 2D;
                double z = side.getOffsetZ() == 0 ? 0.5 : (1 + side.getOffsetZ()) / 2D;
                return new Vec3d[]{new Vec3d(x, 0.25, z), new Vec3d(x, 0.75, z)};
            default: // null
                throw new IllegalStateException();
        }
    }

    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        return onTick(calcFailed, isSafeToCancel, 0);
    }

    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, int recursions) {
        if (recursions > 1000) { // onTick calls itself, don't crash
            return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
        }

        PlayerInventory inventory = ctx.inventory();
        if (inventory == null) {
            this.schematic = null;  // cancel the task
            return null;
        }

        approxPlaceable = approxPlaceable(36);
        if (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            ticks = 5;
        } else {
            ticks--;
        }
        baritone.getInputOverrideHandler().clearAllKeys();
        if (paused) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (baritone.settings().buildInLayers.get()) {
            if (realSchematic == null) {
                realSchematic = schematic;
            }
            ISchematic realSchematic = this.realSchematic; // wrap this properly, dont just have the inner class refer to the builderprocess.this
            int minYInclusive;
            int maxYInclusive;
            // layer = 0 should be nothing
            // layer = realSchematic.heightY() should be everything
            if (baritone.settings().layerOrder.get()) { // top to bottom
                maxYInclusive = realSchematic.heightY() - 1;
                minYInclusive = realSchematic.heightY() - layer;
            } else {
                maxYInclusive = layer - 1;
                minYInclusive = 0;
            }
            schematic = new ISchematic() {
                @Override
                public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
                    return realSchematic.desiredState(x, y, z, current, BuilderProcess.this.approxPlaceable);
                }

                @Override
                public boolean inSchematic(int x, int y, int z, BlockState currentState) {
                    return ISchematic.super.inSchematic(x, y, z, currentState) && y >= minYInclusive && y <= maxYInclusive && realSchematic.inSchematic(x, y, z, currentState);
                }

                @Override
                public void reset() {
                    realSchematic.reset();
                }

                @Override
                public int widthX() {
                    return realSchematic.widthX();
                }

                @Override
                public int heightY() {
                    return realSchematic.heightY();
                }

                @Override
                public int lengthZ() {
                    return realSchematic.lengthZ();
                }
            };
        }
        BuilderCalculationContext bcc = new BuilderCalculationContext();
        if (!recalc(bcc)) {
            if (baritone.settings().buildInLayers.get() && layer < realSchematic.heightY()) {
                logDirect("Starting layer " + layer);
                layer++;
                return onTick(calcFailed, isSafeToCancel, recursions + 1);
            }
            Vec3i repeat = baritone.settings().buildRepeat.get();
            int max = baritone.settings().buildRepeatCount.get();
            numRepeats++;
            if (repeat.equals(new Vec3i(0, 0, 0)) || (max != -1 && numRepeats >= max)) {
                logDirect("Done building");
                if (baritone.settings().desktopNotifications.get() && baritone.settings().notificationOnBuildFinished.get()) {
                    NotificationHelper.notify("Done building", false);
                }
                onLostControl();
                return null;
            }
            // build repeat time
            layer = 0;
            origin = new BlockPos(origin).add(repeat);
            if (!baritone.settings().buildRepeatSneaky.get()) {
                schematic.reset();
            }
            logDirect("Repeating build in vector " + repeat + ", new origin is " + origin);
            return onTick(calcFailed, isSafeToCancel, recursions + 1);
        }
        if (baritone.settings().distanceTrim.get()) {
            trim();
        }

        Optional<Pair<BetterBlockPos, Rotation>> toBreak = toBreakNearPlayer(bcc);
        if (toBreak.isPresent() && isSafeToCancel && ctx.entity().isOnGround()) {
            // we'd like to pause to break this block
            // only change look direction if it's safe (don't want to fuck up an in progress parkour for example
            Rotation rot = toBreak.get().getRight();
            BetterBlockPos pos = toBreak.get().getLeft();
            baritone.getLookBehavior().updateTarget(rot, true);
            MovementHelper.switchToBestToolFor(ctx, bcc.get(pos));
            if (ctx.entity().isSneaking()) {
                // really horrible bug where a block is visible for breaking while sneaking but not otherwise
                // so you can't see it, it goes to place something else, sneaks, then the next tick it tries to break
                // and is unable since it's unsneaked in the intermediary tick
                baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            }
            if (ctx.isLookingAt(pos) || ctx.entityRotations().isReallyCloseTo(rot)) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        List<BlockState> desirableOnHotbar = new ArrayList<>();
        Optional<Placement> toPlace = searchForPlacables(bcc, desirableOnHotbar);
        if (toPlace.isPresent() && isSafeToCancel && ctx.entity().isOnGround() && ticks <= 0) {
            Rotation rot = toPlace.get().rot;
            baritone.getLookBehavior().updateTarget(rot, true);
            inventory.selectedSlot = toPlace.get().hotbarSelection;
            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            if ((ctx.isLookingAt(toPlace.get().placeAgainst) && ((BlockHitResult) ctx.objectMouseOver()).getSide().equals(toPlace.get().side)) || ctx.entityRotations().isReallyCloseTo(rot)) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        if (baritone.settings().allowInventory.get()) {
            ArrayList<Integer> usefulSlots = new ArrayList<>();
            List<BlockState> noValidHotbarOption = new ArrayList<>();
            outer:
            for (BlockState desired : desirableOnHotbar) {
                for (int i = 0; i < 9; i++) {
                    if (valid(approxPlaceable.get(i), desired, true)) {
                        usefulSlots.add(i);
                        continue outer;
                    }
                }
                noValidHotbarOption.add(desired);
            }

            outer:
            for (int i = 9; i < 36; i++) {
                for (BlockState desired : noValidHotbarOption) {
                    if (valid(approxPlaceable.get(i), desired, true)) {
                        baritone.getInventoryBehavior().attemptToPutOnHotbar(i, usefulSlots::contains, inventory);
                        break outer;
                    }
                }
            }
        }

        Goal goal = assemble(bcc, approxPlaceable.subList(0, 9));
        if (goal == null) {
            goal = assemble(bcc, approxPlaceable, true); // we're far away, so assume that we have our whole inventory to recalculate placeable properly
            if (goal == null) {
                if (baritone.settings().skipFailedLayers.get() && baritone.settings().buildInLayers.get() && layer < realSchematic.heightY()) {
                    logDirect("Skipping layer that I cannot construct! Layer #" + layer);
                    layer++;
                    return onTick(calcFailed, isSafeToCancel, recursions + 1);
                }
                logDirect("Unable to do it. Pausing. resume to resume, cancel to cancel");
                paused = true;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
    }

    private boolean recalc(BuilderCalculationContext bcc) {
        if (incorrectPositions == null) {
            incorrectPositions = new HashSet<>();
            fullRecalc(bcc);
            if (incorrectPositions.isEmpty()) {
                return false;
            }
        }
        recalcNearby(bcc);
        if (incorrectPositions.isEmpty()) {
            fullRecalc(bcc);
        }
        return !incorrectPositions.isEmpty();
    }

    private void trim() {
        HashSet<BetterBlockPos> copy = new HashSet<>(incorrectPositions);
        copy.removeIf(pos -> pos.getSquaredDistance(ctx.entity().getBlockPos()) > 200);
        if (!copy.isEmpty()) {
            incorrectPositions = copy;
        }
    }

    private void recalcNearby(BuilderCalculationContext bcc) {
        BetterBlockPos center = ctx.feetPos();
        int radius = baritone.settings().builderTickScanRadius.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired != null) {
                        // we care about this position
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        if (valid(bcc.bsi.get0(x, y, z), desired, false)) {
                            incorrectPositions.remove(pos);
                            observedCompleted.add(BetterBlockPos.longHash(pos));
                        } else {
                            incorrectPositions.add(pos);
                            observedCompleted.remove(BetterBlockPos.longHash(pos));
                        }
                    }
                }
            }
        }
    }

    private void fullRecalc(BuilderCalculationContext bcc) {
        incorrectPositions = new HashSet<>();
        for (int y = 0; y < schematic.heightY(); y++) {
            for (int z = 0; z < schematic.lengthZ(); z++) {
                for (int x = 0; x < schematic.widthX(); x++) {
                    int blockX = x + origin.getX();
                    int blockY = y + origin.getY();
                    int blockZ = z + origin.getZ();
                    BlockState current = bcc.bsi.get0(blockX, blockY, blockZ);
                    if (!schematic.inSchematic(x, y, z, current)) {
                        continue;
                    }
                    if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) { // check if its in render distance, not if its in cache
                        // we can directly observe this block, it is in render distance
                        if (valid(bcc.bsi.get0(blockX, blockY, blockZ), schematic.desiredState(x, y, z, current, this.approxPlaceable), false)) {
                            observedCompleted.add(BetterBlockPos.longHash(blockX, blockY, blockZ));
                        } else {
                            incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                            observedCompleted.remove(BetterBlockPos.longHash(blockX, blockY, blockZ));
                            if (incorrectPositions.size() > baritone.settings().incorrectSize.get()) {
                                return;
                            }
                        }
                        continue;
                    }
                    // this is not in render distance
                    if (!observedCompleted.contains(BetterBlockPos.longHash(blockX, blockY, blockZ))) {
                        // and we've never seen this position be correct
                        // therefore mark as incorrect
                        incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                        if (incorrectPositions.size() > baritone.settings().incorrectSize.get()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private Goal assemble(BuilderCalculationContext bcc, List<BlockState> approxPlaceable) {
        return assemble(bcc, approxPlaceable, false);
    }

    private Goal assemble(BuilderCalculationContext bcc, List<BlockState> approxPlaceable, boolean logMissing) {
        List<BetterBlockPos> placeable = new ArrayList<>();
        List<BetterBlockPos> breakable = new ArrayList<>();
        List<BetterBlockPos> sourceLiquids = new ArrayList<>();
        List<BetterBlockPos> flowingLiquids = new ArrayList<>();
        Map<BlockState, Integer> missing = new HashMap<>();
        incorrectPositions.forEach(pos -> {
            BlockState state = bcc.bsi.get0(pos);
            if (state.getBlock() instanceof AirBlock) {
                if (approxPlaceable.contains(bcc.getSchematic(pos.x, pos.y, pos.z, state))) {
                    placeable.add(pos);
                } else {
                    BlockState desired = bcc.getSchematic(pos.x, pos.y, pos.z, state);
                    missing.put(desired, 1 + missing.getOrDefault(desired, 0));
                }
            } else {
                if (state.getBlock() instanceof FluidBlock) {
                    // if the block itself is JUST a liquid (i.e. not just a waterlogged block), we CANNOT break it
                    // TODO for 1.13 make sure that this only matches pure water, not waterlogged blocks
                    if (!MovementHelper.possiblyFlowing(state)) {
                        // if it's a source block then we want to replace it with a throwaway
                        sourceLiquids.add(pos);
                    } else {
                        flowingLiquids.add(pos);
                    }
                } else {
                    breakable.add(pos);
                }
            }
        });
        List<Goal> toBreak = new ArrayList<>();
        breakable.forEach(pos -> toBreak.add(breakGoal(pos, bcc)));
        List<Goal> toPlace = new ArrayList<>();
        placeable.forEach(pos -> {
            if (!placeable.contains(pos.down()) && !placeable.contains(pos.down(2))) {
                toPlace.add(placementGoal(pos, bcc));
            }
        });
        sourceLiquids.forEach(pos -> toPlace.add(new GoalBlock(pos.up())));

        if (!toPlace.isEmpty()) {
            return new JankyGoalComposite(new GoalComposite(toPlace.toArray(new Goal[0])), new GoalComposite(toBreak.toArray(new Goal[0])));
        }
        if (toBreak.isEmpty()) {
            if (logMissing && !missing.isEmpty()) {
                logDirect("Missing materials for at least:");
                logDirect(missing.entrySet().stream()
                        .map(e -> String.format("%sx %s", e.getValue(), e.getKey()))
                        .collect(Collectors.joining("\n")));
            }
            if (logMissing && !flowingLiquids.isEmpty()) {
                logDirect("Unreplaceable liquids at at least:");
                logDirect(flowingLiquids.stream()
                        .map(p -> String.format("%s %s %s", p.x, p.y, p.z))
                        .collect(Collectors.joining("\n")));
            }
            return null;
        }
        return new GoalComposite(toBreak.toArray(new Goal[0]));
    }

    public static class JankyGoalComposite implements Goal {

        private final Goal primary;
        private final Goal fallback;

        public JankyGoalComposite(Goal primary, Goal fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }


        @Override
        public boolean isInGoal(int x, int y, int z) {
            return primary.isInGoal(x, y, z) || fallback.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return primary.heuristic(x, y, z);
        }

        @Override
        public String toString() {
            return "JankyComposite Primary: " + primary + " Fallback: " + fallback;
        }
    }

    public static class GoalBreak extends GoalGetToBlock {

        public GoalBreak(BlockPos pos) {
            super(pos);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            // can't stand right on top of a block, that might not work (what if it's unsupported, can't break then)
            if (y > this.y) {
                return false;
            }
            // but any other adjacent works for breaking, including inside or below
            return super.isInGoal(x, y, z);
        }
    }

    private Goal placementGoal(BlockPos pos, BuilderCalculationContext bcc) {
        if (!(ctx.world().getBlockState(pos).getBlock() instanceof AirBlock)) {  // TODO can this even happen?
            return new GoalPlace(pos);
        }
        boolean allowSameLevel = !(ctx.world().getBlockState(pos.up()).getBlock() instanceof AirBlock);
        BlockState current = ctx.world().getBlockState(pos);
        for (Direction facing : Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            //noinspection ConstantConditions
            if (bcc.canPlaceAgainst(pos.offset(facing)) && placementPlausible(pos, bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current))) {
                return new GoalAdjacent(pos, pos.offset(facing), allowSameLevel);
            }
        }
        return new GoalPlace(pos);
    }

    private Goal breakGoal(BlockPos pos, BuilderCalculationContext bcc) {
        if (baritone.settings().goalBreakFromAbove.get() && bcc.bsi.get0(pos.up()).getBlock() instanceof AirBlock && bcc.bsi.get0(pos.up(2)).getBlock() instanceof AirBlock) { // TODO maybe possible without the up(2) check?
            return new JankyGoalComposite(new GoalBreak(pos), new GoalGetToBlock(pos.up()) {
                @Override
                public boolean isInGoal(int x, int y, int z) {
                    if (y > this.y || (x == this.x && y == this.y && z == this.z)) {
                        return false;
                    }
                    return super.isInGoal(x, y, z);
                }
            });
        }
        return new GoalBreak(pos);
    }

    public static class GoalAdjacent extends GoalGetToBlock {

        private boolean allowSameLevel;
        private BlockPos no;

        public GoalAdjacent(BlockPos pos, BlockPos no, boolean allowSameLevel) {
            super(pos);
            this.no = no;
            this.allowSameLevel = allowSameLevel;
        }

        public boolean isInGoal(int x, int y, int z) {
            if (x == this.x && y == this.y && z == this.z) {
                return false;
            }
            if (x == no.getX() && y == no.getY() && z == no.getZ()) {
                return false;
            }
            if (!allowSameLevel && y == this.y - 1) {
                return false;
            }
            if (y < this.y - 1) {
                return false;
            }
            return super.isInGoal(x, y, z);
        }

        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }
    }

    public static class GoalPlace extends GoalBlock {

        public GoalPlace(BlockPos placeAt) {
            super(placeAt.up());
        }

        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }
    }

    @Override
    public void onLostControl() {
        incorrectPositions = null;
        name = null;
        schematic = null;
        realSchematic = null;
        layer = baritone.settings().startAtLayer.get();
        numRepeats = 0;
        paused = false;
        observedCompleted = null;
    }

    @Override
    public String displayName0() {
        return paused ? "Builder Paused" : "Building " + name;
    }

    private List<BlockState> approxPlaceable(int size) {
        List<BlockState> result = new ArrayList<>();
        PlayerEntity player = (PlayerEntity) ctx.entity();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.inventory.main.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                result.add(Blocks.AIR.getDefaultState());
                continue;
            }
            // <toxic cloud>
            BlockState placementState = ((BlockItem) stack.getItem()).getBlock().getPlacementState(new ItemPlacementContext(
                    new ItemUsageContext(
                            ctx.world(),
                            player,
                            Hand.MAIN_HAND,
                            stack,
                            new BlockHitResult(
                                    new Vec3d(ctx.entity().getX(), ctx.entity().getY(), ctx.entity().getZ()),
                                    Direction.UP,
                                    ctx.feetPos(),
                                    false
                            )
                    ) {})   // protected constructor
            );
            if (placementState != null) {
                result.add(placementState);
            } else {
                result.add(Blocks.AIR.getDefaultState());
            }
            // </toxic cloud>
        }
        return result;
    }

    private boolean valid(BlockState current, BlockState desired, boolean itemVerify) {
        if (desired == null) {
            return true;
        }
        if (current.getBlock() instanceof FluidBlock && baritone.settings().okIfWater.get()) {
            return true;
        }
        if (current.getBlock() instanceof AirBlock && desired.getBlock() instanceof AirBlock) {
            return true;
        }
        if (current.getBlock() instanceof AirBlock && baritone.settings().okIfAir.get().contains(desired.getBlock())) {
            return true;
        }
        if (desired.getBlock() instanceof AirBlock && baritone.settings().buildIgnoreBlocks.get().contains(current.getBlock())) {
            return true;
        }
        if (!(current.getBlock() instanceof AirBlock) && baritone.settings().buildIgnoreExisting.get() && !itemVerify) {
            return true;
        }
        return current.equals(desired);
    }

    public class BuilderCalculationContext extends CalculationContext {

        private final List<BlockState> placeable;
        private final ISchematic schematic;
        private final int originX;
        private final int originY;
        private final int originZ;

        public BuilderCalculationContext() {
            super(BuilderProcess.this.baritone, true); // wew lad
            this.placeable = approxPlaceable(9);
            this.schematic = BuilderProcess.this.schematic;
            this.originX = origin.getX();
            this.originY = origin.getY();
            this.originZ = origin.getZ();

            this.jumpPenalty += 10;
            this.backtrackCostFavoringCoefficient = 1;
        }

        private BlockState getSchematic(int x, int y, int z, BlockState current) {
            if (schematic.inSchematic(x - originX, y - originY, z - originZ, current)) {
                return schematic.desiredState(x - originX, y - originY, z - originZ, current, BuilderProcess.this.approxPlaceable);
            } else {
                return null;
            }
        }

        @Override
        public double costOfPlacingAt(int x, int y, int z, BlockState current) {
            if (isProtected(x, y, z)) { // make calculation fail properly if we can't build
                return COST_INF;
            }
            BlockState sch = getSchematic(x, y, z, current);
            if (sch != null) {
                // TODO this can return true even when allowPlace is off.... is that an issue?
                if (sch.getBlock() instanceof AirBlock) {
                    // we want this to be air, but they're asking if they can place here
                    // this won't be a schematic block, this will be a throwaway
                    return placeBlockCost * 2; // we're going to have to break it eventually
                }
                if (placeable.contains(sch)) {
                    return 0; // thats right we gonna make it FREE to place a block where it should go in a structure
                    // no place block penalty at all 😎
                    // i'm such an idiot that i just tried to copy and paste the epic gamer moment emoji too
                    // get added to unicode when?
                }
                if (!hasThrowaway) {
                    return COST_INF;
                }
                // we want it to be something that we don't have
                // even more of a pain to place something wrong
                return placeBlockCost * 3;
            } else {
                if (hasThrowaway) {
                    return placeBlockCost;
                } else {
                    return COST_INF;
                }
            }
        }

        @Override
        public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
            if (!allowBreak || isProtected(x, y, z)) {
                return COST_INF;
            }
            BlockState sch = getSchematic(x, y, z, current);
            if (sch != null) {
                if (sch.getBlock() instanceof AirBlock) {
                    // it should be air
                    // regardless of current contents, we can break it
                    return 1;
                }
                // it should be a real block
                // is it already that block?
                if (valid(bsi.get0(x, y, z), sch, false)) {
                    return baritone.settings().breakCorrectBlockPenaltyMultiplier.get();
                } else {
                    // can break if it's wrong
                    // would be great to return less than 1 here, but that would actually make the cost calculation messed up
                    // since we're breaking a block, if we underestimate the cost, then it'll fail when it really takes the correct amount of time
                    return 1;

                }
                // TODO do blocks in render distace only?
                // TODO allow breaking blocks that we have a tool to harvest and immediately place back?
            } else {
                return 1; // why not lol
            }
        }
    }
}
