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
import baritone.api.Settings;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
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
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Ascends exactly one block by either jumping and placing a block at the player's feet, or by climbing up.
 *
 * <p>If {@link Settings#allowBreak} is {@code true}, this movement will break
 * all blocks in the way.
 *
 * <p>Seen from the side:
 * <pre>
 *     dest
 *     src ⬆
 * </pre>
 */
public class MovementPillar extends Movement {

    public MovementPillar(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
        super(baritone, start, end, buildPositionsToBreak(baritone.getPlayerContext().entity(), start), start);
    }

    public static BetterBlockPos[] buildPositionsToBreak(Entity entity, BetterBlockPos start) {
        int x = start.x;
        int y = start.y;
        int z = start.z;
        EntityDimensions dims = entity.getDimensions(EntityPose.STANDING);
        int requiredVerticalSpace = MathHelper.ceil(dims.height);
        int requiredSideSpace = CalculationContext.getRequiredSideSpace(dims);
        int sideLength = requiredSideSpace * 2 + 1;
        BetterBlockPos[] ret = new BetterBlockPos[sideLength * sideLength];
        int i = 0;

        for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
            for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                // If we are at the starting position, we already cleared enough space to stand there
                // So only need to check the block above our head
                ret[i++] = new BetterBlockPos(x + dx, y + requiredVerticalSpace, z + dz);
            }
        }
        return ret;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, result);
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static void cost(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
        BlockState fromState = context.get(x, y, z);
        boolean climbable = isClimbable(context.bsi, x, y, z);
        BlockState fromDown = context.get(x, y - 1, z);
        if (!climbable) {
            if (fromDown.isIn(BlockTags.CLIMBABLE)) {
                return; // can't pillar from a ladder or vine onto something that isn't also climbable
            }
            if (fromDown.getBlock() instanceof SlabBlock && fromDown.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
                return; // can't pillar up from a bottom slab onto a non ladder
            }
        } else if (context.width > 1) {
            return;    // Large entities simply cannot use ladders and vines
        }
        double totalHardness = 0;
        boolean swimmable = false;
        int requiredSideSpace = context.requiredSideSpace;
        for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
            for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                // If we are at the starting position, we already cleared enough space to stand there
                // So only need to check the block above our head
                int checkedX = x + dx;
                int checkedY = y + context.height;
                int checkedZ = z + dz;
                BlockState toBreak = context.get(checkedX, checkedY, checkedZ);
                BlockState underToBreak = context.get(x, checkedY - 1, z);
                Block toBreakBlock = toBreak.getBlock();
                if (toBreakBlock instanceof FenceGateBlock || (!climbable && toBreakBlock instanceof ScaffoldingBlock)) { // see issue #172
                    return;
                }
                boolean water = MovementHelper.isWater(toBreak);
                if (water || MovementHelper.isWater(underToBreak)) {
                    if (MovementHelper.isFlowing(checkedX, checkedY, checkedZ, toBreak, context.bsi)) {
                        return;    // not ascending flowing water
                    }
                    swimmable = true; // allow ascending pillars of water
                    if (totalHardness > 0) return; // Nop, not mining stuff in a water column
                }
                if (!water) {
                    double hardness = MovementHelper.getMiningDurationTicks(context, checkedX, checkedY, checkedZ, toBreak, true);
                    if (hardness > 0) {
                        if (hardness >= COST_INF || swimmable) {
                            return;
                        }
                        BlockState check = context.get(checkedX, checkedY + 1, checkedZ); // the block on top of the one we're going to break, could it fall on us?
                        if (check.getBlock() instanceof FallingBlock) {
                            // see MovementAscend's identical check for breaking a falling block above our head
                            if (!(toBreakBlock instanceof FallingBlock) || !(underToBreak.getBlock() instanceof FallingBlock)) {
                                return;
                            }
                        }
                        totalHardness += hardness;
                    }
                }
            }
        }
        if (!swimmable && (MovementHelper.isLiquid(fromState) && !context.canPlaceAgainst(x, y - 1, z, fromDown)) || (MovementHelper.isLiquid(fromDown) && context.assumeWalkOnWater)) {
            // otherwise, if we're standing in water, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is true, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is false, we must have ascended to here, or sneak backplaced, so it is possible to pillar again
            return;
        }
        double placeCost = 0;
        if (!climbable && !swimmable) {
            // we need to place a block where we started to jump on it
            placeCost = context.costOfPlacingAt(x, y, z, fromState);
            if (placeCost >= COST_INF) {
                return;
            }
            if (fromDown.isAir()) {
                placeCost += 0.1; // slightly (1/200th of a second) penalize pillaring on what's currently air
            }
        }

        if (climbable || swimmable) {
            result.cost = LADDER_UP_ONE_COST + totalHardness * 5;
            result.oxygenCost = context.oxygenCost(LADDER_UP_ONE_COST / 2 + totalHardness * 5, context.get(x, y + context.height - 1, z))
                    + context.oxygenCost(LADDER_UP_ONE_COST / 2, context.get(x, y + context.height, z));
        } else {
            result.cost = JUMP_ONE_BLOCK_COST + placeCost + context.jumpPenalty + totalHardness;
            // Not swimmable so no water
            result.oxygenCost = context.oxygenCost(JUMP_ONE_BLOCK_COST+placeCost+totalHardness, Blocks.AIR.getDefaultState());
        }
    }

    private static boolean isClimbable(BlockStateInterface context, int x, int y, int z) {
        if (context.get0(x, y, z).isIn(BlockTags.CLIMBABLE)) return true;
        if (context.get0(x, y + 1, z).isIn(BlockTags.CLIMBABLE)) {
            // you can only use a ladder at head level if you are standing on firm ground
            return MovementHelper.isBlockNormalCube(context.get0(x, y - 1, z));
        }
        return false;
    }

    public static BlockPos getAgainst(CalculationContext context, BetterBlockPos vine) {
        if (MovementHelper.isBlockNormalCube(context.get(vine.north()))) {
            return vine.north();
        }
        if (MovementHelper.isBlockNormalCube(context.get(vine.south()))) {
            return vine.south();
        }
        if (MovementHelper.isBlockNormalCube(context.get(vine.east()))) {
            return vine.east();
        }
        if (MovementHelper.isBlockNormalCube(context.get(vine.west()))) {
            return vine.west();
        }
        return null;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.feetPos().y < src.y) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        BlockState fromDown = BlockStateInterface.get(ctx, src);
        if (ctx.entity().isTouchingWater() || MovementHelper.isWater(ctx, src.up(MathHelper.ceil(ctx.entity().getHeight())))) {
            // stay centered while swimming up a water column
            centerForAscend(ctx, dest, state, 0.2);
            state.setInput(Input.JUMP, true);
            if (ctx.feetPos().equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }
        boolean ladder = isClimbable(((Baritone) baritone).bsi, src.x, src.y, src.z);
        Rotation rotation = RotationUtils.calcRotationFromVec3d(ctx.headPos(),
                VecUtils.getBlockPosCenter(positionToPlace),
                new Rotation(ctx.entity().getYaw(), ctx.entity().getPitch()));
        if (!ladder) {
            state.setTarget(new MovementState.MovementTarget(new Rotation(ctx.entity().getYaw(), rotation.getPitch()), true));
        }

        boolean blockIsThere = MovementHelper.canWalkOn(ctx, src) || ladder;
        if (ladder) {
            if (ctx.entity().getWidth() > 1) {
                baritone.logDirect("Large entities cannot climb ladders :/");
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
            BlockPos supportingBlock = getSupportingBlock(baritone, ctx, src, fromDown);

            if ((supportingBlock != null && ctx.feetPos().equals(supportingBlock.up())) || ctx.feetPos().equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }

            if (supportingBlock != null) {
                MovementHelper.moveTowards(ctx, state, supportingBlock);
            } else {
                // stay centered while climbing up
                centerForAscend(ctx, dest, state, 0.27);    // trial and error
            }
            return state.setInput(Input.JUMP, true);
        } else {
            // Get ready to place a throwaway block
            if (!((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, src.x, src.y, src.z)) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }


            state.setInput(Input.SNEAK, ctx.entity().getY() > dest.getY() || ctx.entity().getY() < src.getY() + 0.2D); // delay placement by 1 tick for ncp compatibility
            // since (lower down) we only right click once player.isSneaking, and that happens the tick after we request to sneak

            double diffX = ctx.entity().getX() - (dest.getX() + 0.5);
            double diffZ = ctx.entity().getZ() - (dest.getZ() + 0.5);
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            double flatMotion = Math.sqrt(ctx.entity().getVelocity().x * ctx.entity().getVelocity().x + ctx.entity().getVelocity().z * ctx.entity().getVelocity().z);
            if (dist > 0.17) {//why 0.17? because it seemed like a good number, that's why
                //[explanation added after baritone port lol] also because it needs to be less than 0.2 because of the 0.3 sneak limit
                //and 0.17 is reasonably less than 0.2

                // If it's been more than forty ticks of trying to jump and we aren't done yet, go forward, maybe we are stuck
                state.setInput(Input.MOVE_FORWARD, true);

                // revise our target to both yaw and pitch if we're going to be moving forward
                state.setTarget(new MovementState.MovementTarget(rotation, true));
            } else if (flatMotion < 0.05) {
                // If our Y coordinate is above our goal, stop jumping
                state.setInput(Input.JUMP, ctx.entity().getY() < dest.getY());
            }


            if (!blockIsThere) {
                BlockState frState = BlockStateInterface.get(ctx, src);
                // TODO: Evaluate usage of getMaterial().isReplaceable()
                if (!(frState.isAir() || frState.getMaterial().isReplaceable())) {
                    RotationUtils.reachable(ctx.entity(), src, ctx.playerController().getBlockReachDistance())
                            .map(rot -> new MovementState.MovementTarget(rot, true))
                            .ifPresent(state::setTarget);
                    state.setInput(Input.JUMP, false); // breaking is like 5x slower when you're jumping
                    state.setInput(Input.CLICK_LEFT, true);
                    blockIsThere = false;
                } else if (ctx.entity().isSneaking() && (ctx.isLookingAt(src.down()) || ctx.isLookingAt(src)) && ctx.entity().getY() > dest.getY() + 0.1) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }

        // If we are at our goal and the block below us is placed
        if (ctx.feetPos().equals(dest) && blockIsThere) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        return state;
    }

    @Nullable
    public static BlockPos getSupportingBlock(IBaritone baritone, IEntityContext ctx, BetterBlockPos src, BlockState climbableBlock) {
        BlockPos supportingBlock;
        if (Block.isFaceFullSquare(climbableBlock.getCollisionShape(ctx.world(), src), Direction.UP)) {
            supportingBlock = null;
        } else if (climbableBlock.getBlock() instanceof LadderBlock) {
            supportingBlock = src.offset(climbableBlock.get(LadderBlock.FACING).getOpposite());
        } else {
            supportingBlock = getAgainst(new CalculationContext(baritone), src);
        }
        return supportingBlock;
    }

    public static void centerForAscend(IEntityContext ctx, BetterBlockPos dest, MovementState state, double allowedDistance) {
        state.setTarget(new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.getBlockPosCenter(dest), ctx.entityRotations()), false));
        Vec3d destCenter = VecUtils.getBlockPosCenter(dest);
        if (Math.abs(ctx.entity().getX() - destCenter.x) > allowedDistance || Math.abs(ctx.entity().getZ() - destCenter.z) > allowedDistance) {
            state.setInput(Input.MOVE_FORWARD, true);
        }
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
