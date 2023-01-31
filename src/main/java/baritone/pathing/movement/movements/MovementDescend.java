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
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Descends one block in a staircase.
 *
 * <p>If {@link Settings#allowBreak} is {@code true}, this movement will break
 * all blocks in the way.
 *
 * <p>Seen from the side:
 * <pre>
 *     src ↘
 *        dest
 * </pre>
 */
public class MovementDescend extends Movement {

    private int numTicks = 0;

    public MovementDescend(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
        super(baritone, start, end, buildPositionsToBreak(baritone.getPlayerContext().entity(), start, end), end.down());
    }

    @NotNull
    private static BetterBlockPos[] buildPositionsToBreak(LivingEntity entity, BetterBlockPos start, BetterBlockPos end) {
        BetterBlockPos[] wall = MovementTraverse.buildPositionsToBreak(entity, start, end.up());
        BetterBlockPos[] floor = MovementDownward.buildPositionsToBreak(entity, end);
        return ArrayUtils.addAll(wall, floor);
    }

    @Override
    public void reset() {
        super.reset();
        numTicks = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, dest.x, dest.z, result);
        if (result.y != dest.y) {
            return COST_INF; // doesn't apply to us, this position is a fall not a descend
        }
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest.up(), dest);
    }

    public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
        double frontBreak = 0;
        BlockState destDown = context.get(destX, y - 1, destZ);
        if (destDown.isOf(Blocks.SCAFFOLDING) && destDown.get(ScaffoldingBlock.BOTTOM)) {
            // scaffolding gains a floor when it is not supported
            // we want to avoid breaking unsupported scaffolding, so stop here
            return;
        }
        frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y - 1, destZ, destDown, false);
        if (frontBreak >= COST_INF) {
            return;
        }
        BlockState destUp = context.get(destX, y, destZ);
        if (destUp.isOf(Blocks.SCAFFOLDING) && destUp.get(ScaffoldingBlock.BOTTOM)) {
            // same as above
            return;
        }
        frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y, destZ, destUp, false);
        if (frontBreak >= COST_INF) {
            return;
        }
        frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, true); // only the top block in the 3 we need to mine needs to consider the falling blocks above
        if (frontBreak >= COST_INF) {
            return;
        }

        BlockState fromDown = context.get(x, y - 1, z);
        if (fromDown.isIn(BlockTags.CLIMBABLE)) {
            return;
        }

        // A
        //SA
        // A
        // B
        // C
        // D
        //if S is where you start, B needs to be air for a movementfall
        //A is plausibly breakable by either descend or fall
        //C, D, etc determine the length of the fall

        BlockState below = context.get(destX, y - 2, destZ);
        if (!MovementHelper.canWalkOn(context.bsi, destX, y - 2, destZ, below, context.baritone.settings())) {
            dynamicFallCost(context, x, y, z, destX, destZ, frontBreak, below, res);
            res.oxygenCost += context.oxygenCost(WALK_OFF_BLOCK_COST + frontBreak, context.get(x, y+context.height-1, z));
            return;
        }

        double totalCost = frontBreak;

        if (destDown.getBlock() == Blocks.LADDER || destDown.getBlock() == Blocks.VINE) {
            return;
        }

        // we walk half the block plus 0.3 to get to the edge, then we walk the other 0.2 while simultaneously falling (math.max because of how it's in parallel)
        boolean water = MovementHelper.isWater(destUp);    // TODO improve water detection
        double waterModifier = water ? context.waterWalkSpeed / WALK_ONE_BLOCK_COST : 1;
        double walk = waterModifier * (WALK_OFF_BLOCK_COST / fromDown.getBlock().getVelocityMultiplier());
        double fall = waterModifier * Math.max(FALL_N_BLOCKS_COST[1], CENTER_AFTER_FALL_COST);
        totalCost += walk + fall;
        res.x = destX;
        res.y = y - 1;
        res.z = destZ;
        res.cost = totalCost;
        res.oxygenCost = context.oxygenCost(walk / 2 + frontBreak, context.get(x, y+context.height-1, z));
        res.oxygenCost += context.oxygenCost(fall/2, context.get(destX, y+context.height-2, destZ));
        res.oxygenCost += context.oxygenCost(walk/2+fall/2, context.get(destX, y+context.height-1, destZ));
    }

    /**
     * @return {@code true} if a water bucket needs to be placed
     */
    public static boolean dynamicFallCost(CalculationContext context, int x, int y, int z, int destX, int destZ, double frontBreak, BlockState below, MutableMoveResult res) {
        if (frontBreak != 0 && context.get(destX, y + 2, destZ).getBlock() instanceof FallingBlock) {
            // if frontBreak is 0 we can actually get through this without updating the falling block and making it actually fall
            // but if frontBreak is nonzero, we're breaking blocks in front, so don't let anything fall through this column,
            // and potentially replace the water we're going to fall into
            return false;
        }
        if (!MovementHelper.canWalkThrough(context.bsi, destX, y - 2, destZ, below, context.baritone.settings())) {
            return false;
        }
        double costSoFar = 0;
        int effectiveStartHeight = y;
        for (int fallHeight = 3; true; fallHeight++) {
            int newY = y - fallHeight;
            if (newY < context.worldBottom) {
                // when pathing in the end, where you could plausibly fall into the void
                // this check prevents it from getting the block at y=-1 and crashing
                return false;
            }
            BlockState ontoBlock = context.get(destX, newY, destZ);
            int unprotectedFallHeight = fallHeight - (y - effectiveStartHeight); // equal to fallHeight - y + effectiveFallHeight, which is equal to -newY + effectiveFallHeight, which is equal to effectiveFallHeight - newY
            double fallCost = FALL_N_BLOCKS_COST[unprotectedFallHeight] + costSoFar;
            double tentativeCost = WALK_OFF_BLOCK_COST + fallCost + frontBreak;
            if (MovementHelper.isWater(ontoBlock)) {
                if (!MovementHelper.canWalkThrough(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                    return false;
                }
                if (context.assumeWalkOnWater) {
                    return false; // TODO fix
                }
                if (MovementHelper.isFlowing(destX, newY, destZ, ontoBlock, context.bsi)) {
                    return false; // TODO flowing check required here?
                }
                if (!MovementHelper.canWalkOn(context.bsi, destX, newY - 1, destZ, context.baritone.settings())) {
                    // we could punch right through the water into something else
                    return false;
                }
                // found a fall into water
                res.x = destX;
                res.y = newY;
                res.z = destZ;
                res.cost = tentativeCost;// TODO incorporate water swim up cost?
                // if there was water along the way, the fall would have stopped there
                res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.getDefaultState());
                return false;
            }
            if (unprotectedFallHeight <= 11 && (ontoBlock.getBlock() == Blocks.VINE || ontoBlock.getBlock() == Blocks.LADDER)) {
                // if fall height is greater than or equal to 11, we don't actually grab on to vines or ladders. the more you know
                // this effectively "resets" our falling speed
                costSoFar += FALL_N_BLOCKS_COST[unprotectedFallHeight - 1];// we fall until the top of this block (not including this block)
                costSoFar += LADDER_DOWN_ONE_COST;
                effectiveStartHeight = newY;
                continue;
            }
            if (MovementHelper.canWalkThrough(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                continue;
            }
            if (!MovementHelper.canWalkOn(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                return false;
            }
            if (MovementHelper.isBottomSlab(ontoBlock)) {
                return false; // falling onto a half slab is really glitchy, and can cause more fall damage than we'd expect
            }
            if (unprotectedFallHeight <= context.maxFallHeightNoWater + 1) {
                // fallHeight = 4 means onto.up() is 3 blocks down, which is the max
                res.x = destX;
                res.y = newY + 1;
                res.z = destZ;
                res.cost = tentativeCost;
                res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.getDefaultState());
                return false;
            }
            if (context.hasWaterBucket && unprotectedFallHeight <= context.maxFallHeightBucket + 1) {
                res.x = destX;
                res.y = newY + 1;// this is the block we're falling onto, so dest is +1
                res.z = destZ;
                res.cost = tentativeCost + context.placeBucketCost();
                res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.getDefaultState());
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        BlockPos playerFeet = ctx.feetPos();
        BlockPos fakeDest = new BlockPos(dest.getX() * 2 - src.getX(), dest.getY(), dest.getZ() * 2 - src.getZ());
        if ((playerFeet.equals(dest) || playerFeet.equals(fakeDest)) && (MovementHelper.isLiquid(ctx, dest) || ctx.entity().getY() - dest.getY() < 0.5)) { // lilypads
            // Wait until we're actually on the ground before saying we're done because sometimes we continue to fall if the next action starts immediately
            return state.setStatus(MovementStatus.SUCCESS);
            /* else {
                // Automatone.LOGGER.debug(player().getPositionVec().y + " " + playerFeet.getY() + " " + (player().getPositionVec().y - playerFeet.getY()));
            }*/
        }

        double diffX = ctx.entity().getX() - (dest.getX() + 0.5);
        double diffZ = ctx.entity().getZ() - (dest.getZ() + 0.5);
        double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);

        if (ab < 0.20 && (ctx.world().getBlockState(dest).isOf(Blocks.SCAFFOLDING) || ctx.entity().isSubmergedInWater() && ctx.entity().getY() > src.y)) {
            state.setInput(Input.SNEAK, true);
        }

        if (safeMode()) {
            double destX = (src.getX() + 0.5) * 0.17 + (dest.getX() + 0.5) * 0.83;
            double destZ = (src.getZ() + 0.5) * 0.17 + (dest.getZ() + 0.5) * 0.83;
            LivingEntity player = ctx.entity();
            state.setTarget(new MovementState.MovementTarget(
                    new Rotation(RotationUtils.calcRotationFromVec3d(ctx.headPos(),
                            new Vec3d(destX, dest.getY(), destZ),
                            new Rotation(player.getYaw(), player.getPitch())).getYaw(), player.getPitch()),
                    false
            )).setInput(Input.MOVE_FORWARD, true);
            return state;
        }
        double x = ctx.entity().getX() - (src.getX() + 0.5);
        double z = ctx.entity().getZ() - (src.getZ() + 0.5);
        double fromStart = Math.sqrt(x * x + z * z);
        if (!playerFeet.equals(dest) || ab > 0.25) {
            if (numTicks++ < 20 && fromStart < 1.25) {
                MovementHelper.moveTowards(ctx, state, fakeDest);
            } else {
                MovementHelper.moveTowards(ctx, state, dest);
            }
        }
        return state;
    }

    public boolean safeMode() {
        // (dest - src) + dest is offset 1 more in the same direction
        // so it's the block we'd need to worry about running into if we decide to sprint straight through this descend
        BlockPos into = dest.subtract(src.down()).add(dest);
        if (skipToAscend()) {
            // if dest extends into can't walk through, but the two above are can walk through, then we can overshoot and glitch in that weird way
            return true;
        }
        for (int y = 0; y <= 2; y++) { // we could hit any of the three blocks
            BlockState state = BlockStateInterface.get(ctx, into.up(y));
            if (MovementHelper.avoidWalkingInto(state)
                    && !(MovementHelper.isWater(state) && baritone.settings().allowSwimming.get())) {
                return true;
            }
        }
        return false;
    }

    public boolean skipToAscend() {
        BlockPos into = dest.subtract(src.down()).add(dest);
        return !MovementHelper.canWalkThrough(ctx, new BetterBlockPos(into)) && MovementHelper.canWalkThrough(ctx, new BetterBlockPos(into).up()) && MovementHelper.canWalkThrough(ctx, new BetterBlockPos(into).up(2));
    }
}
