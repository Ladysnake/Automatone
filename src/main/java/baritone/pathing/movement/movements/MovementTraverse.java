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

package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.Set;

public class MovementTraverse extends Movement {

    /**
     * Did we have to place a bridge block or was it always there
     */
    private boolean wasTheBridgeBlockAlwaysThere = true;

    public MovementTraverse(IBaritone baritone, BetterBlockPos from, BetterBlockPos to) {
        super(baritone, from, to, computeBlocksToBreak(baritone.getPlayerContext().entity(), from, to), to.down());
    }

    @Override
    public void reset() {
        super.reset();
        wasTheBridgeBlockAlwaysThere = true;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static BetterBlockPos[] computeBlocksToBreak(Entity e, BetterBlockPos from, BetterBlockPos to) {
        int x = from.x;
        int y = from.y;
        int z = from.z;
        int destX = to.x;
        int destZ = to.z;
        int movX = destX - x;
        int movZ = destZ - z;
        EntityDimensions dimensions = e.getDimensions(EntityPose.STANDING);
        int requiredSideSpace = CalculationContext.getRequiredSideSpace(dimensions);
        int checkedXShift = movX * requiredSideSpace;
        int checkedZShift = movZ * requiredSideSpace;
        int checkedX = destX + checkedXShift;
        int checkedZ = destZ + checkedZShift;
        int height = MathHelper.ceil(dimensions.height);
        int requiredForwardSpace = Math.max(requiredSideSpace, 1);
        int volume = requiredForwardSpace * (requiredSideSpace * 2 + 1) * height;
        int i = 0;
        BetterBlockPos[] ret = new BetterBlockPos[volume];

        for (int df = 0; df < requiredForwardSpace; df++) {
            for (int ds = -requiredSideSpace; ds <= requiredSideSpace; ds++) {
                for (int dy = 0; dy < height; dy++) {
                    ret[i++] = new BetterBlockPos(checkedX + (movX == 0 ? ds : df), y + dy, checkedZ + (movZ == 0 ? ds : df));
                }
            }
        }

        return ret;
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
        BlockState destOn = context.get(destX, y - 1, destZ);
        BlockState srcOn = context.get(x, y - 1, z);
        Block srcOnBlock = srcOn.getBlock();
        // we need to check that nothing is in the way, no matter the collision box of our entity
        // we know that if we are standing at (x,y,z), the space around us is already cleared out
        // (not always true, we may be pressing against the wall, but close enough)
        // MovementTraverse only moves on a single axis, by one block
        // For example:
        // [-----]X                 [-----] is our entity's collision box (big)
        // +++sd+++                 s is our source position, d is our destination
        // 12345678                 coordinates
        // X is a wall, which prevents us from walking from s to d despite both positions being clear
        // So only coord 8 has to be checked, despite moving from 4 to 5
        int movX = destX - x;
        int movZ = destZ - z;
        int checkedXShift = movX * context.requiredSideSpace;
        int checkedZShift = movZ * context.requiredSideSpace;
        int checkedX = destX + checkedXShift;
        int checkedZ = destZ + checkedZShift;

        if (MovementHelper.canWalkOn(context.bsi, destX, y - 1, destZ, destOn)) {//this is a walk, not a bridge
            double WC = WALK_ONE_BLOCK_COST;
            boolean water = false;
            for (int dy = 0; dy < context.height; dy++) {
                if (MovementHelper.isWater(context.get(destX, y+dy, destZ))) {
                    WC = context.waterWalkSpeed;
                    water = true;
                    break;
                }
            }
            if (!water) {
                if (destOn.getBlock() == Blocks.SOUL_SAND) {
                    WC += (WALK_ONE_OVER_SOUL_SAND_COST - WALK_ONE_BLOCK_COST) / 2;
                } else if (destOn.getBlock() == Blocks.WATER) {
                    WC += context.walkOnWaterOnePenalty;
                }
                if (srcOnBlock == Blocks.SOUL_SAND) {
                    WC += (WALK_ONE_OVER_SOUL_SAND_COST - WALK_ONE_BLOCK_COST) / 2;
                }
            }

            double hardness = 0;
            int hardnessModifier = srcOnBlock == Blocks.LADDER || srcOnBlock == Blocks.VINE ? 5 : 1;
            for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
                for (int dy = 0; dy < context.height; dy++) {
                    hardness += MovementHelper.getMiningDurationTicks(
                            context,
                            checkedX + dxz * movZ,  // if not moving along the z axis (movZ == 0), we only need to check blocks at checkedX
                            y + dy,
                            checkedZ + dxz * movX,  // if not moving along the x axis (movX == 0), we only need to check blocks at checkedZ
                            dy == context.height - 1 // only consider falling blocks on the uppermost block to break
                    ) * hardnessModifier;

                    if (hardness >= COST_INF) {
                        return COST_INF;
                    }
                }
            }
            if (hardness == 0 && !water && context.canSprint) {
                // If there's nothing in the way, and this isn't water, and we aren't sneak placing
                // We can sprint =D
                // Don't check for soul sand, since we can sprint on that too
                WC *= SPRINT_MULTIPLIER;
            }
            return WC + hardness;
        } else {//this is a bridge, so we need to place a block
            if (srcOnBlock == Blocks.LADDER || srcOnBlock == Blocks.VINE) {
                return COST_INF;
            }
            if (MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
                boolean throughWater = false;
                for (int dy = 0; dy < context.height; dy++) {
                    if (MovementHelper.isWater(context.get(destX, y+dy, destZ))) {
                        throughWater = true;
                        if (MovementHelper.isWater(destOn)) {
                            // this happens when assume walk on water is true and this is a traverse in water, which isn't allowed
                            return COST_INF;
                        }
                        break;
                    }
                }

                double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn);
                if (placeCost >= COST_INF) {
                    return COST_INF;
                }

                double hardness = 0;
                for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
                    for (int dy = 0; dy < context.height; dy++) {
                        hardness += MovementHelper.getMiningDurationTicks(
                                context,
                                checkedX + dxz * movZ,  // if not moving along the z axis (movZ == 0), we only need to check blocks at checkedX
                                y + dy,
                                checkedZ + dxz * movX,  // if not moving along the x axis (movX == 0), we only need to check blocks at checkedZ
                                dy == context.height - 1 // only consider falling blocks on the uppermost block to break
                        );

                        if (hardness >= COST_INF) {
                            return COST_INF;
                        }
                    }
                }

                double WC = throughWater ? context.waterWalkSpeed : WALK_ONE_BLOCK_COST;
                for (int i = 0; i < 5; i++) {
                    int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetX();
                    int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetY();
                    int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetZ();
                    if (againstX == x && againstZ == z) { // this would be a backplace
                        continue;
                    }
                    if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) { // found a side place option
                        return WC + placeCost + hardness;
                    }
                }
                // now that we've checked all possible directions to side place, we actually need to backplace
                if (srcOnBlock == Blocks.SOUL_SAND || (srcOnBlock instanceof SlabBlock && srcOn.get(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    return COST_INF; // can't sneak and backplace against soul sand or half slabs (regardless of whether it's top half or bottom half) =/
                }
                if (srcOn.getFluidState().getFluid() instanceof WaterFluid) {
                    return COST_INF; // this is obviously impossible
                }
                WC = WC * (SNEAK_ONE_BLOCK_COST / WALK_ONE_BLOCK_COST);//since we are sneak backplacing, we are sneaking lol
                return WC + placeCost + hardness;
            }
            return COST_INF;
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        BlockState[] bss = new BlockState[positionsToBreak.length];
        for (int i = 0; i < positionsToBreak.length; i++) {
            bss[i] = BlockStateInterface.get(ctx, positionsToBreak[i]);
        }
        if (state.getStatus() != MovementStatus.RUNNING) {
            // if the setting is enabled
            if (!Baritone.settings().walkWhileBreaking.value) {
                return state;
            }

            // and if we're prepping (aka mining the block in front)
            if (state.getStatus() != MovementStatus.PREPPING) {
                return state;
            }

            // and if it's fine to walk into the blocks in front
            for (BlockState pb : bss) {
                if (MovementHelper.avoidWalkingInto(pb)) {
                    return state;
                }
            }

            // and we aren't already pressed up against the block
            double dist = Math.max(Math.abs(ctx.entity().getX() - (dest.getX() + 0.5D)), Math.abs(ctx.entity().getZ() - (dest.getZ() + 0.5D)));
            if (dist < 0.83) {
                return state;
            }

            if (!state.getTarget().getRotation().isPresent()) {
                // this can happen rarely when the server lags and doesn't send the falling sand entity until you've already walked through the block and are now mining the next one
                return state;
            }

            EntityDimensions dims = ctx.entity().getDimensions(ctx.entity().getPose());
            if (dims.width > 1 || dims.height < 1 || dims.height > 2) { // player-sized entities get the optimized path, others stop and break blocks
                return state;
            }

            float yawToDest = 0;
            float pitchToBreak = 0;

            // combine the yaw to the center of the destination, and the pitch to the specific block we're trying to break
            // it's safe to do this since the two blocks we break (in a traverse) are right on top of each other and so will have the same yaw
            yawToDest = RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.calculateBlockCenter(ctx.world(), dest), ctx.entityRotations()).getYaw();
            pitchToBreak = state.getTarget().getRotation().get().getPitch();
            if ((MovementHelper.isBlockNormalCube(bss[0]) || bss[0].getBlock() instanceof AirBlock && (MovementHelper.isBlockNormalCube(bss[1]) || bss[1].getBlock() instanceof AirBlock))) {
                // in the meantime, before we're right up against the block, we can break efficiently at this angle
                pitchToBreak = 26;
            }

            return state.setTarget(new MovementState.MovementTarget(new Rotation(yawToDest, pitchToBreak), true))
                    .setInput(Input.MOVE_FORWARD, true)
                    .setInput(Input.SPRINT, true);
        }

        //sneak may have been set to true in the PREPPING state while mining an adjacent block
        state.setInput(Input.SNEAK, false);

        Block fd = BlockStateInterface.get(ctx, src.down()).getBlock();
        boolean ladder = fd == Blocks.LADDER || fd == Blocks.VINE;

        for (BlockState bs : bss) {
            if (bs.getBlock() instanceof DoorBlock) {
                boolean notPassable = bs.getBlock() instanceof DoorBlock && !MovementHelper.isDoorPassable(ctx, src, dest);
                boolean canOpen = !bs.isOf(Blocks.IRON_DOOR);

                if (notPassable && canOpen) {
                    return state.setTarget(new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.calculateBlockCenter(ctx.world(), positionsToBreak[0]), ctx.entityRotations()), true))
                            .setInput(Input.CLICK_RIGHT, true);
                }
            } else if (bs.getBlock() instanceof FenceGateBlock) {
                BlockPos blocked = !MovementHelper.isGatePassable(ctx, positionsToBreak[0], src.up()) ? positionsToBreak[0]
                        : !MovementHelper.isGatePassable(ctx, positionsToBreak[1], src) ? positionsToBreak[1]
                        : null;
                if (blocked != null) {
                    Optional<Rotation> rotation = RotationUtils.reachable(ctx, blocked);
                    if (rotation.isPresent()) {
                        return state.setTarget(new MovementState.MovementTarget(rotation.get(), true)).setInput(Input.CLICK_RIGHT, true);
                    }
                }
            }
        }


        boolean isTheBridgeBlockThere = MovementHelper.canWalkOn(ctx, positionToPlace) || ladder;
        BlockPos feet = ctx.feetPos();
        if (feet.getY() != dest.getY() && !ladder) {
            logDebug("Wrong Y coordinate");
            if (feet.getY() < dest.getY()) {
                return state.setInput(Input.JUMP, true);
            }
            return state;
        }

        if (isTheBridgeBlockThere) {
            if (feet.equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (Baritone.settings().overshootTraverse.value && (feet.equals(dest.add(getDirection())) || feet.equals(dest.add(getDirection()).add(getDirection())))) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            Block low = BlockStateInterface.get(ctx, src).getBlock();
            Block high = BlockStateInterface.get(ctx, src.up()).getBlock();
            if (ctx.entity().getY() > src.y + 0.1D && !ctx.entity().isOnGround() && (low == Blocks.VINE || low == Blocks.LADDER || high == Blocks.VINE || high == Blocks.LADDER)) {
                // hitting W could cause us to climb the ladder instead of going forward
                // wait until we're on the ground
                return state;
            }
            BlockPos into = dest.subtract(src).add(dest);
            BlockState intoBelow = BlockStateInterface.get(ctx, into);
            BlockState intoAbove = BlockStateInterface.get(ctx, into.up());
            if (wasTheBridgeBlockAlwaysThere && (!MovementHelper.isLiquid(ctx, feet) || Baritone.settings().sprintInWater.value) && (!MovementHelper.avoidWalkingInto(intoBelow) || MovementHelper.isWater(intoBelow)) && !MovementHelper.avoidWalkingInto(intoAbove)) {
                state.setInput(Input.SPRINT, true);
            }

            BlockState destDown = BlockStateInterface.get(ctx, dest.down());
            BlockPos against = positionsToBreak[0];
            if (feet.getY() != dest.getY() && ladder && (destDown.getBlock() == Blocks.VINE || destDown.getBlock() == Blocks.LADDER)) {
                against = destDown.getBlock() == Blocks.VINE ? MovementPillar.getAgainst(new CalculationContext(baritone), dest.down()) : dest.offset(destDown.get(LadderBlock.FACING).getOpposite());
                if (against == null) {
                    logDirect("Unable to climb vines. Consider disabling allowVines.");
                    return state.setStatus(MovementStatus.UNREACHABLE);
                }
            }
            MovementHelper.moveTowards(ctx, state, against);
        } else {
            wasTheBridgeBlockAlwaysThere = false;
            Block standingOn = BlockStateInterface.get(ctx, feet.down()).getBlock();
            if (standingOn.equals(Blocks.SOUL_SAND) || standingOn instanceof SlabBlock) { // see issue #118
                double dist = Math.max(Math.abs(dest.getX() + 0.5 - ctx.entity().getX()), Math.abs(dest.getZ() + 0.5 - ctx.entity().getZ()));
                if (dist < 0.85) { // 0.5 + 0.3 + epsilon
                    MovementHelper.moveTowards(ctx, state, dest);
                    return state.setInput(Input.MOVE_FORWARD, false)
                            .setInput(Input.MOVE_BACK, true);
                }
            }
            double dist1 = Math.max(Math.abs(ctx.entity().getX() - (dest.getX() + 0.5D)), Math.abs(ctx.entity().getZ() - (dest.getZ() + 0.5D)));
            PlaceResult p = MovementHelper.attemptToPlaceABlock(state, baritone, dest.down(), false, true);
            if ((p == PlaceResult.READY_TO_PLACE || dist1 < 0.6) && !Baritone.settings().assumeSafeWalk.value) {
                state.setInput(Input.SNEAK, true);
            }
            switch (p) {
                case READY_TO_PLACE: {
                    if (ctx.entity().isSneaking() || Baritone.settings().assumeSafeWalk.value) {
                        state.setInput(Input.CLICK_RIGHT, true);
                    }
                    return state;
                }
                case ATTEMPTING: {
                    if (dist1 > 0.83) {
                        // might need to go forward a bit
                        float yaw = RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.getBlockPosCenter(dest), ctx.entityRotations()).getYaw();
                        if (Math.abs(state.getTarget().rotation.getYaw() - yaw) < 0.1) {
                            // but only if our attempted place is straight ahead
                            return state.setInput(Input.MOVE_FORWARD, true);
                        }
                    } else if (ctx.entityRotations().isReallyCloseTo(state.getTarget().rotation)) {
                        // well i guess theres something in the way
                        return state.setInput(Input.CLICK_LEFT, true);
                    }
                    return state;
                }
                default:
                    break;
            }
            if (feet.equals(dest)) {
                // If we are in the block that we are trying to get to, we are sneaking over air and we need to place a block beneath us against the one we just walked off of
                // Out.log(from + " " + to + " " + faceX + "," + faceY + "," + faceZ + " " + whereAmI);
                double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D;
                double faceY = (dest.getY() + src.getY() - 1.0D) * 0.5D;
                double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D;
                // faceX, faceY, faceZ is the middle of the face between from and to
                BlockPos goalLook = src.down(); // this is the block we were just standing on, and the one we want to place against

                Rotation backToFace = RotationUtils.calcRotationFromVec3d(ctx.headPos(), new Vec3d(faceX, faceY, faceZ), ctx.entityRotations());
                float pitch = backToFace.getPitch();
                double dist2 = Math.max(Math.abs(ctx.entity().getX() - faceX), Math.abs(ctx.entity().getZ() - faceZ));
                if (dist2 < 0.29) { // see issue #208
                    float yaw = RotationUtils.calcRotationFromVec3d(VecUtils.getBlockPosCenter(dest), ctx.headPos(), ctx.entityRotations()).getYaw();
                    state.setTarget(new MovementState.MovementTarget(new Rotation(yaw, pitch), true));
                    state.setInput(Input.MOVE_BACK, true);
                } else {
                    state.setTarget(new MovementState.MovementTarget(backToFace, true));
                }
                if (ctx.isLookingAt(goalLook)) {
                    return state.setInput(Input.CLICK_RIGHT, true); // wait to right click until we are able to place
                }
                // Out.log("Trying to look at " + goalLook + ", actually looking at" + Baritone.whatAreYouLookingAt());
                if (ctx.entityRotations().isReallyCloseTo(state.getTarget().rotation)) {
                    state.setInput(Input.CLICK_LEFT, true);
                }
                return state;
            }
            MovementHelper.moveTowards(ctx, state, positionsToBreak[0]);
            // TODO MovementManager.moveTowardsBlock(to); // move towards not look at because if we are bridging for a couple blocks in a row, it is faster if we dont spin around and walk forwards then spin around and place backwards for every block
        }
        return state;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we're in the process of breaking blocks before walking forwards
        // or if this isn't a sneak place (the block is already there)
        // then it's safe to cancel this
        return state.getStatus() != MovementStatus.RUNNING || MovementHelper.canWalkOn(ctx, dest.down());
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (ctx.feetPos().equals(src) || ctx.feetPos().equals(src.down())) {
            Block block = BlockStateInterface.getBlock(ctx, src.down());
            if (block == Blocks.LADDER || block == Blocks.VINE) {
                state.setInput(Input.SNEAK, true);
            }
        }
        return super.prepared(state);
    }
}
