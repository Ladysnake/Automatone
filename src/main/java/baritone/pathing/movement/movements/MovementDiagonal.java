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
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Moves one block in diagonal on the horizontal plane.
 *
 * <p>If a block prevents a perfect diagonal straight line, this movement will attempt to edge around it.
 * It will not attempt to break any block unless they were placed after the path is computed.
 *
 * <p>If {@link Settings#allowDiagonalAscend} and/or {@link Settings#allowDiagonalDescend} are {@code true},
 * this movement can be diagonal on all 3 axes simultaneously.
 *
 * <p>Seen from above:
 * <pre>
 *     src ↘        OR  src ⮯       OR   src blk
 *         dest         blk dest          ➥ dest
 * </pre>
 */
public class MovementDiagonal extends Movement {

    private static final double SQRT_2 = Math.sqrt(2);

    public MovementDiagonal(IBaritone baritone, BetterBlockPos start, Direction dir1, Direction dir2, int dy) {
        this(baritone, start, start.offset(dir1), start.offset(dir2), dir2, dy);
        // super(start, start.offset(dir1).offset(dir2), new BlockPos[]{start.offset(dir1), start.offset(dir1).up(), start.offset(dir2), start.offset(dir2).up(), start.offset(dir1).offset(dir2), start.offset(dir1).offset(dir2).up()}, new BlockPos[]{start.offset(dir1).offset(dir2).down()});
    }

    private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos dir1, BetterBlockPos dir2, Direction drr2, int dy) {
        this(baritone, start, dir1.offset(drr2).up(dy), dir1, dir2);
    }

    private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos end, BetterBlockPos dir1, BetterBlockPos dir2) {
        super(baritone, start, end, computeBlocksToBreak(baritone.getPlayerContext().entity(), end, dir1, dir2));
    }

    @NotNull
    private static BetterBlockPos[] computeBlocksToBreak(LivingEntity entity, BetterBlockPos end, BetterBlockPos dir1, BetterBlockPos dir2) {
        if (entity.getDimensions(EntityPose.STANDING).height <= 1) {
            return new BetterBlockPos[]{dir1, dir2, end};
        }
        return new BetterBlockPos[]{dir1, dir1.up(), dir2, dir2.up(), end, end.up()};
    }

    @Override
    protected boolean safeToCancel(MovementState state) {
        //too simple. backfill does not work after cornering with this
        //return MovementHelper.canWalkOn(ctx, ctx.playerFeet().down());
        LivingEntity player = ctx.entity();
        double offset = 0.25;
        double x = player.getX();
        double y = player.getY() - 1;
        double z = player.getZ();
        //standard
        if (ctx.feetPos().equals(src)){
            return true;
        }
        //both corners are walkable
        if (MovementHelper.canWalkOn(ctx, new BlockPos(src.x, src.y - 1, dest.z))
            && MovementHelper.canWalkOn(ctx, new BlockPos(dest.x, src.y - 1, src.z))){
                return true;
        }
        //we are in a likely unwalkable corner, check for a supporting block
        if (ctx.feetPos().equals(new BetterBlockPos(src.x, src.y, dest.z))
            || ctx.feetPos().equals(new BetterBlockPos(dest.x, src.y, src.z))){
                return (MovementHelper.canWalkOn(ctx, new BetterBlockPos(x + offset, y, z + offset))
                   || MovementHelper.canWalkOn(ctx, new BetterBlockPos(x + offset, y, z - offset))
                   || MovementHelper.canWalkOn(ctx, new BetterBlockPos(x - offset, y, z + offset))
                   || MovementHelper.canWalkOn(ctx, new BetterBlockPos(x - offset, y, z - offset)));
        }
        return true;
   }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, dest.x, dest.z, result);
        if (result.y != dest.y) {
            return COST_INF; // doesn't apply to us, this position is incorrect
        }
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        BetterBlockPos diagA = new BetterBlockPos(src.x, src.y, dest.z);
        BetterBlockPos diagB = new BetterBlockPos(dest.x, src.y, src.z);
        if (dest.y < src.y) {
            return ImmutableSet.of(src, dest.up(), diagA, diagB, dest, diagA.down(), diagB.down());
        }
        if (dest.y > src.y) {
            return ImmutableSet.of(src, src.up(), diagA, diagB, dest, diagA.up(), diagB.up());
        }
        return ImmutableSet.of(src, dest, diagA, diagB);
    }

    public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
        if (!MovementHelper.canWalkThrough(context.bsi, destX, y + 1, destZ, context.baritone.settings())) {
            return;
        }
        if (context.width > 1 || context.height > 2) {    // TODO handle larger entities
            return;
        }
        BlockState destInto = context.get(destX, y, destZ);
        boolean ascend = false;
        BlockState destWalkOn;
        boolean descend = false;
        if (!MovementHelper.canWalkThrough(context.bsi, destX, y, destZ, destInto, context.baritone.settings())) {
            ascend = true;
            if (!context.allowDiagonalAscend || !MovementHelper.canWalkThrough(context.bsi, x, y + 2, z, context.baritone.settings()) || !MovementHelper.canWalkOn(context.bsi, destX, y, destZ, destInto, context.baritone.settings()) || !MovementHelper.canWalkThrough(context.bsi, destX, y + 2, destZ, context.baritone.settings())) {
                return;
            }
            destWalkOn = destInto;
        } else {
            destWalkOn = context.get(destX, y - 1, destZ);
            if (!MovementHelper.canWalkOn(context.bsi, destX, y - 1, destZ, destWalkOn, context.baritone.settings())) {
                descend = true;
                if (!context.allowDiagonalDescend || !MovementHelper.canWalkOn(context.bsi, destX, y - 2, destZ, context.baritone.settings()) || !MovementHelper.canWalkThrough(context.bsi, destX, y - 1, destZ, destWalkOn, context.baritone.settings())) {
                    return;
                }
            }
        }
        // For either possible velocity modifying block, that affects half of our walking
        double multiplier = WALK_ONE_BLOCK_COST / destWalkOn.getBlock().getVelocityMultiplier() / 2;
        if (destWalkOn.isOf(Blocks.WATER)) {
            multiplier += context.walkOnWaterOnePenalty * SQRT_2;
        }
        Block fromDown = context.get(x, y - 1, z).getBlock();
        if (fromDown == Blocks.LADDER || fromDown == Blocks.VINE) {
            return;
        }
        multiplier += WALK_ONE_BLOCK_COST / fromDown.getVelocityMultiplier() / 2;
        BlockState cuttingOver1 = context.get(x, y - 1, destZ);
        if (cuttingOver1.getBlock() == Blocks.MAGMA_BLOCK || MovementHelper.isLava(cuttingOver1)) {
            return;
        }
        BlockState cuttingOver2 = context.get(destX, y - 1, z);
        if (cuttingOver2.getBlock() == Blocks.MAGMA_BLOCK || MovementHelper.isLava(cuttingOver2)) {
            return;
        }
        boolean water = false;
        BlockState startState = context.get(x, y, z);
        Block startIn = startState.getBlock();
        if (MovementHelper.isWater(startState) || MovementHelper.isWater(destInto)) {
            if (ascend) {
                return;
            }
            // Ignore previous multiplier
            // Whatever we were walking on (possibly soul sand) doesn't matter as we're actually floating on water
            // Not even touching the blocks below
            multiplier = context.waterWalkSpeed;
            water = true;
        }
        // if the player can fit in 1 block spaces, we have fewer blocks to check
        boolean smol = context.height <= 1;
        BlockState diagonalA = context.get(x, y, destZ);
        BlockState diagonalB = context.get(destX, y, z);
        if (ascend) {
            boolean ATop = smol || MovementHelper.canWalkThrough(context.bsi, x, y + 2, destZ, context.baritone.settings());
            boolean AMid = MovementHelper.canWalkThrough(context.bsi, x, y + 1, destZ, context.baritone.settings());
            boolean ALow = MovementHelper.canWalkThrough(context.bsi, x, y, destZ, diagonalA, context.baritone.settings());
            boolean BTop = smol || MovementHelper.canWalkThrough(context.bsi, destX, y + 2, z, context.baritone.settings());
            boolean BMid = MovementHelper.canWalkThrough(context.bsi, destX, y + 1, z, context.baritone.settings());
            boolean BLow = MovementHelper.canWalkThrough(context.bsi, destX, y, z, diagonalB, context.baritone.settings());
            if ((!(ATop && AMid && ALow) && !(BTop && BMid && BLow)) // no option
                    || MovementHelper.avoidWalkingInto(diagonalA) // bad
                    || MovementHelper.avoidWalkingInto(diagonalB) // bad
                    || (ATop && AMid && MovementHelper.canWalkOn(context.bsi, x, y, destZ, diagonalA, context.baritone.settings())) // we could just ascend
                    || (BTop && BMid && MovementHelper.canWalkOn(context.bsi, destX, y, z, diagonalB, context.baritone.settings())) // we could just ascend
                    || (!ATop && AMid && ALow) // head bonk A
                    || (!BTop && BMid && BLow)) { // head bonk B
                return;
            }
            res.cost = multiplier * SQRT_2 + JUMP_ONE_BLOCK_COST;
            res.x = destX;
            res.z = destZ;
            res.y = y + 1;
            return;
        }
        double optionA = MovementHelper.getMiningDurationTicks(context, x, y, destZ, diagonalA, false);
        double optionB = MovementHelper.getMiningDurationTicks(context, destX, y, z, diagonalB, false);
        if (optionA != 0 && optionB != 0) {
            // check these one at a time -- if diagonalA and pb2 were nonzero, we already know that (optionA != 0 && optionB != 0)
            // so no need to check diagonalUpA as well, might as well return early here
            return;
        }
        BlockState diagonalUpA = context.get(x, y + 1, destZ);
        if (!smol) {
            optionA += MovementHelper.getMiningDurationTicks(context, x, y + 1, destZ, diagonalUpA, true);
            if (optionA != 0 && optionB != 0) {
                // same deal, if diagonalUpA makes optionA nonzero and option B already was nonzero, diagonalUpB can't affect the result
                return;
            }
        }
        BlockState diagonalUpB = context.get(destX, y + 1, z);
        if (optionA == 0 && ((MovementHelper.avoidWalkingInto(diagonalB) && diagonalB.getBlock() != Blocks.WATER) || (!smol && MovementHelper.avoidWalkingInto(diagonalUpB)))) {
            // at this point we're done calculating optionA, so we can check if it's actually possible to edge around in that direction
            return;
        }
        if (!smol) {
            optionB += MovementHelper.getMiningDurationTicks(context, destX, y + 1, z, diagonalUpB, true);
            if (optionA != 0 && optionB != 0) {
                // and finally, if the cost is nonzero for both ways to approach this diagonal, it's not possible
                return;
            }
        }
        if (optionB == 0 && ((MovementHelper.avoidWalkingInto(diagonalA) && diagonalA.getBlock() != Blocks.WATER) || (!smol && MovementHelper.avoidWalkingInto(diagonalUpA)))) {
            // and now that option B is fully calculated, see if we can edge around that way
            return;
        }
        BlockState optionHeadBlock;
        if (optionA != 0 || optionB != 0) {
            multiplier *= SQRT_2 - 0.001; // TODO tune
            if (startIn == Blocks.LADDER || startIn == Blocks.VINE) {
                // edging around doesn't work if doing so would climb a ladder or vine instead of moving sideways
                return;
            }
            optionHeadBlock = optionA != 0 ? diagonalUpA : diagonalUpB;
        } else {
            // only can sprint if not edging around
            if (context.canSprint && !water) {
                // If we aren't edging around anything, and we aren't in water
                // We can sprint =D
                // Don't check for soul sand, since we can sprint on that too
                multiplier *= SPRINT_MULTIPLIER;
            }
            optionHeadBlock = null;
        }
        res.cost = multiplier * SQRT_2;
        double costPerBlock;
        if (optionHeadBlock == null) {
            costPerBlock = res.cost / 2;
        } else {
            costPerBlock = res.cost / 3;
            res.oxygenCost += context.oxygenCost(costPerBlock, optionHeadBlock);
        }
        res.oxygenCost += context.oxygenCost(costPerBlock, context.get(x, y+context.height-1, z));
        if (descend) {
            res.cost += Math.max(FALL_N_BLOCKS_COST[1], CENTER_AFTER_FALL_COST);
            res.oxygenCost += context.oxygenCost(costPerBlock, context.get(destX, y+context.height-2, destZ));
            res.y = y - 1;
        } else {
            res.oxygenCost += context.oxygenCost(costPerBlock, context.get(destX, y+context.height-1, destZ));
            res.y = y;
        }
        res.x = destX;
        res.z = destZ;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.feetPos().equals(dest) || MovementHelper.isWater(ctx, ctx.feetPos()) && ctx.feetPos().equals(dest.down())) {
            return state.setStatus(MovementStatus.SUCCESS);
        } else if (!playerInValidPosition() && !(MovementHelper.isLiquid(ctx, src) && getValidPositions().contains(ctx.feetPos().up()))) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (dest.y > src.y && ctx.entity().getY() < src.y + 0.1 && ctx.entity().horizontalCollision) {
            state.setInput(Input.JUMP, true);
        }
        if (sprint()) {
            state.setInput(Input.SPRINT, true);
        }
        MovementHelper.moveTowards(ctx, state, dest);
        return state;
    }

    private boolean sprint() {
        if (MovementHelper.isLiquid(ctx, ctx.feetPos()) && !baritone.settings().sprintInWater.get()) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!MovementHelper.canWalkThrough(ctx, positionsToBreak[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean prepared(MovementState state) {
        return true;
    }

    @Override
    public List<BlockPos> toBreak(BlockStateInterface bsi) {
        if (toBreakCached != null) {
            return toBreakCached;
        }
        List<BlockPos> result = new ArrayList<>();
        for (int i = 4; i < 6; i++) {
            if (!MovementHelper.canWalkThrough(bsi, positionsToBreak[i].x, positionsToBreak[i].y, positionsToBreak[i].z, ctx.baritone().settings())) {
                result.add(positionsToBreak[i]);
            }
        }
        toBreakCached = result;
        return result;
    }

    @Override
    public List<BlockPos> toWalkInto(BlockStateInterface bsi) {
        if (toWalkIntoCached == null) {
            toWalkIntoCached = new ArrayList<>();
        }
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (!MovementHelper.canWalkThrough(bsi, positionsToBreak[i].x, positionsToBreak[i].y, positionsToBreak[i].z, ctx.baritone().settings())) {
                result.add(positionsToBreak[i]);
            }
        }
        toWalkIntoCached = result;
        return toWalkIntoCached;
    }
}
