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

import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Set;

/**
 * Ascends one block in a staircase.
 *
 * <p>If {@link Settings#allowBreak} is {@code true}, this movement will break
 * all blocks in the way.
 *
 * <p> Seen from the side:
 * <pre>
 *       ↗ dest
 *     src
 * </pre>
 */
public class MovementAscend extends Movement {

    private int ticksWithoutPlacement = 0;

    public MovementAscend(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, buildPositionsToBreak(baritone.getPlayerContext().entity(), src, dest), buildPositionsToPlace(baritone.getPlayerContext().entity(), src, dest));
    }

    private static BetterBlockPos buildPositionsToPlace(LivingEntity entity, BetterBlockPos src, BetterBlockPos dest) {
        int diffX = dest.x - src.x;
        int diffZ = dest.z - src.z;
        assert Math.abs(diffX) <= 1 && Math.abs(diffZ) <= 1;
        int requiredSideSpace = CalculationContext.getRequiredSideSpace(entity.getDimensions(EntityPose.STANDING));
        int placeX = dest.x + diffX * requiredSideSpace;
        int placeZ = dest.z + diffZ * requiredSideSpace;
        return new BetterBlockPos(placeX, src.y, placeZ);
    }

    private static BetterBlockPos[] buildPositionsToBreak(LivingEntity entity, BetterBlockPos src, BetterBlockPos dest) {
        BetterBlockPos[] ceiling = MovementPillar.buildPositionsToBreak(entity, src);
        BetterBlockPos[] wall = MovementTraverse.buildPositionsToBreak(entity, src.up(), dest);
        return ArrayUtils.addAll(ceiling, wall);
    }

    @Override
    public void reset() {
        super.reset();
        ticksWithoutPlacement = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        BetterBlockPos prior = new BetterBlockPos(src.subtract(getDirection()).up()); // sometimes we back up to place the block, also sprint ascends, also skip descend to straight ascend
        return ImmutableSet.of(src,
                src.up(),
                dest,
                prior,
                prior.up()
        );
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
        int diffX = destX - x;
        int diffZ = destZ - z;
        assert Math.abs(diffX) <= 1 && Math.abs(diffZ) <= 1;
        int placeX = destX + diffX * context.requiredSideSpace;
        int placeZ = destZ + diffZ * context.requiredSideSpace;
        BlockState toPlace = context.get(placeX, y, placeZ);
        double additionalPlacementCost = 0;
        if (!MovementHelper.canWalkOn(context.bsi, placeX, y, placeZ, toPlace, context.baritone.settings())) {
            // TODO maybe check if we really can place or mine at that distance, for really large entities
            additionalPlacementCost = context.costOfPlacingAt(placeX, y, placeZ, toPlace);
            if (additionalPlacementCost >= COST_INF) {
                return COST_INF;
            }
            if (!MovementHelper.isReplaceable(placeX, y, placeZ, toPlace, context.bsi)) {
                return COST_INF;
            }
            boolean foundPlaceOption = false;
            for (int i = 0; i < 5; i++) {
                int againstX = placeX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetX();
                int againstY = y + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetY();
                int againstZ = placeZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getOffsetZ();
                if ((placeX - againstX) == diffX && (placeZ - againstZ) == diffZ) {
                    // placeXZ - againstXZ == destXZ - xz => this is the direction we are coming from
                    // we might be able to backplace now, but it doesn't matter because it will have been broken by the time we'd need to use it
                    continue;
                }
                if (context.canPlaceAgainst(againstX, againstY, againstZ)) {
                    foundPlaceOption = true;
                    break;
                }
            }
            if (!foundPlaceOption) { // didn't find a valid place =(
                return COST_INF;
            }
        }
        double miningTicks = 0;
        BlockState srcDown = context.get(x, y - 1, z);
        if (srcDown.getBlock() == Blocks.LADDER || srcDown.getBlock() == Blocks.VINE) {
            return COST_INF;
        }
        boolean inLiquid = MovementHelper.isLiquid(srcDown);
        for (int dx = -context.requiredSideSpace; dx <= context.requiredSideSpace; dx++) {
            for (int dz = -context.requiredSideSpace; dz <= context.requiredSideSpace; dz++) {
                int x1 = x + dx;
                int y1 = y + context.height;
                int z1 = z + dz;
                BlockState srcUp2 = context.get(x1, y1, z1); // used lower down anyway
                if (context.get(x1, y1 + 1, z1).getBlock() instanceof FallingBlock && (MovementHelper.canWalkThrough(context.bsi, x1, y1 - 1, z1, context.baritone.settings()) || !(srcUp2.getBlock() instanceof FallingBlock))) {//it would fall on us and possibly suffocate us
                    // HOWEVER, we assume that we're standing in the start position
                    // that means that src and src.up(1) are both air
                    // maybe they aren't now, but they will be by the time this starts
                    // if the lower one is can't walk through and the upper one is falling, that means that by standing on src
                    // (the presupposition of this Movement)
                    // we have necessarily already cleared the entire FallingBlock stack
                    // on top of our head

                    // as in, if we have a block, then two FallingBlocks on top of it
                    // and that block is x, y+1, z, and we'd have to clear it to even start this movement
                    // we don't need to worry about those FallingBlocks because we've already cleared them
                    return COST_INF;
                    // you may think we only need to check srcUp2, not srcUp
                    // however, in the scenario where glitchy world gen where unsupported sand / gravel generates
                    // it's possible srcUp is AIR from the start, and srcUp2 is falling
                    // and in that scenario, when we arrive and break srcUp2, that lets srcUp3 fall on us and suffocate us
                }
                // includeFalling isn't needed because of the falling check above -- if srcUp3 is falling we will have already exited with COST_INF if we'd actually have to break it
                miningTicks += MovementHelper.getMiningDurationTicks(context, x1, y1, z1, srcUp2, false);
                inLiquid |= MovementHelper.isWater(srcUp2);
                if (miningTicks >= COST_INF || (inLiquid && miningTicks > 0)) {
                    return COST_INF; // Not mining in water
                }
            }
        }
        // we can jump from soul sand, but not from a bottom slab
        boolean jumpingFromBottomSlab =!inLiquid && MovementHelper.isBottomSlab(srcDown);
        boolean jumpingToBottomSlab = !inLiquid && MovementHelper.isBottomSlab(toPlace);
        if (jumpingFromBottomSlab && !jumpingToBottomSlab) {
            return COST_INF;// the only thing we can ascend onto from a bottom slab is another bottom slab
        }
        double walk;
        if (jumpingToBottomSlab) {
            if (jumpingFromBottomSlab) {
                walk = Math.max(JUMP_ONE_BLOCK_COST, WALK_ONE_BLOCK_COST); // we hit space immediately on entering this action
                walk += context.jumpPenalty;
            } else {
                walk = WALK_ONE_BLOCK_COST; // we don't hit space we just walk into the slab
            }
        } else {
            // jumpingFromBottomSlab must be false
            if (inLiquid) {
                walk = (context.waterWalkSpeed / WALK_ONE_BLOCK_COST) * Math.max(JUMP_ONE_BLOCK_COST, WALK_ONE_BLOCK_COST);
            } else {
                // we are jumping and moving in parallel, hence the max
                walk = Math.max(JUMP_ONE_BLOCK_COST, WALK_ONE_BLOCK_COST / toPlace.getBlock().getVelocityMultiplier());
                walk += context.jumpPenalty;
            }
        }

        double totalCost = walk + additionalPlacementCost;
        totalCost += miningTicks;
        if (totalCost >= COST_INF) {
            return COST_INF;
        }
        for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
            for (int dy = 0; dy < context.height; dy++) {
                miningTicks = MovementHelper.getMiningDurationTicks(
                        context,
                        placeX + dxz * diffZ,  // if not moving along the z axis (movZ == 0), we only need to check blocks at placeX
                        y + dy + 1,
                        placeZ + dxz * diffX,  // if not moving along the x axis (movX == 0), we only need to check blocks at placeZ
                        dy == context.height - 1    // only include falling for uppermost block
                );
                totalCost += miningTicks;
                // Not mining anything in water
                if (totalCost >= COST_INF || (miningTicks > 0 && inLiquid)) {
                    return COST_INF;
                }
            }
        }
        return totalCost;
    }

    @Override
    public MovementState updateState(MovementState state) {
        if (ctx.feetPos().y < src.y) {
            // this check should run even when in preparing state (breaking blocks)
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        super.updateState(state);
        // TODO incorporate some behavior from ActionClimb (specifically how it waited until it was at most 1.2 blocks away before starting to jump
        // for efficiency in ascending minimal height staircases, which is just repeated MovementAscend, so that it doesn't bonk its head on the ceiling repeatedly)
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.feetPos().equals(dest) || ctx.feetPos().equals(dest.add(getDirection().down()))) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        BlockState jumpingOnto = BlockStateInterface.get(ctx, positionToPlace);
        if (!MovementHelper.canWalkOn(ctx, positionToPlace, jumpingOnto)) {
            ticksWithoutPlacement++;
            if (MovementHelper.attemptToPlaceABlock(state, baritone, positionToPlace, false, true) == PlaceResult.READY_TO_PLACE) {
                state.setInput(Input.SNEAK, true);
                if (ctx.entity().isSneaking()) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
            if (ticksWithoutPlacement > 10) {
                // After 10 ticks without placement, we might be standing in the way, move back
                state.setInput(Input.MOVE_BACK, true);
            }

            return state;
        }
        MovementHelper.moveTowards(ctx, state, dest);
        if (MovementHelper.isBottomSlab(jumpingOnto) && !MovementHelper.isBottomSlab(BlockStateInterface.get(ctx, src.down()))) {
            return state; // don't jump while walking from a non double slab into a bottom slab
        }

        if (baritone.settings().assumeStep.get() || canStopJumping()) {
            // no need to hit space if we're already jumping
            return state;
        }

        int xAxis = Math.abs(src.getX() - dest.getX()); // either 0 or 1
        int zAxis = Math.abs(src.getZ() - dest.getZ()); // either 0 or 1
        double flatDistToNext = xAxis * Math.abs((dest.getX() + 0.5D) - ctx.entity().getX()) + zAxis * Math.abs((dest.getZ() + 0.5D) - ctx.entity().getZ());
        double sideDist = zAxis * Math.abs((dest.getX() + 0.5D) - ctx.entity().getX()) + xAxis * Math.abs((dest.getZ() + 0.5D) - ctx.entity().getZ());

        double lateralMotion = xAxis * ctx.entity().getVelocity().z + zAxis * ctx.entity().getVelocity().x;
        if (Math.abs(lateralMotion) > 0.1) {
            return state;
        }

        if (headBonkClear()) {
            return state.setInput(Input.JUMP, true);
        }

        if (flatDistToNext > 1.2 || sideDist > 0.2) {
            return state;
        }

        // Once we are pointing the right way and moving, start jumping
        // This is slightly more efficient because otherwise we might start jumping before moving, and fall down without moving onto the block we want to jump onto
        // Also wait until we are close enough, because we might jump and hit our head on an adjacent block
        return state.setInput(Input.JUMP, true);
    }

    private boolean canStopJumping() {
        BetterBlockPos srcUp = src.up();
        double entityY = ctx.entity().getY();
        if (entityY < srcUp.y) {
            return false;
        } else if (entityY <= srcUp.y + 0.1) {
            return !MovementHelper.isWater(ctx.world().getBlockState(srcUp));
        }
        return true;
    }

    // TODO handle wider entities
    public boolean headBonkClear() {
        BetterBlockPos startUp = src.up(MathHelper.ceil(ctx.entity().getHeight()));
        for (int i = 0; i < 4; i++) {
            BetterBlockPos check = startUp.offset(Direction.fromHorizontal(i));
            if (!MovementHelper.canWalkThrough(ctx, check)) {
                // We might bonk our head
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we had to place, don't allow pause
        return state.getStatus() != MovementStatus.RUNNING || ticksWithoutPlacement == 0;
    }
}
