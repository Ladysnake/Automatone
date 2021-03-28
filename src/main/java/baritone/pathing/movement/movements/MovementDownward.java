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
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.tag.BlockTags;

import java.util.Set;

/**
 * Moves exactly one block downward, either by breaking the block the player is standing on
 * or by going down a climbable block.
 *
 * <p>This movement will only be used if {@link Settings#allowDownward} is {@code true}.
 *
 * <p>Seen from the side:
 * <pre>
 *     src ⬇
 *     dest
 * </pre>
 */
public class MovementDownward extends Movement {

    private int numTicks = 0;

    public MovementDownward(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
        super(baritone, start, end, computeBlocksToBreak(baritone.getPlayerContext().entity(), end));
    }

    private static BetterBlockPos[] computeBlocksToBreak(Entity entity, BetterBlockPos end) {
        int x = end.x;
        int y = end.y;
        int z = end.z;
        EntityDimensions dims = entity.getDimensions(EntityPose.STANDING);
        int requiredSideSpace = CalculationContext.getRequiredSideSpace(dims);
        int sideLength = requiredSideSpace * 2 + 1;
        BetterBlockPos[] ret = new BetterBlockPos[sideLength * sideLength];
        int i = 0;

        for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
            for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                // If we are at the starting position, we already cleared enough space to stand there
                // So only need to check the blocks below our feet
                ret[i++] = new BetterBlockPos(x + dx, y - 1, z + dz);
            }
        }

        return ret;
    }

    @Override
    public void reset() {
        super.reset();
        numTicks = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static double cost(CalculationContext context, int x, int y, int z) {
        if (!context.allowDownward) {
            return COST_INF;
        }
        if (!MovementHelper.canWalkOn(context.bsi, x, y - 2, z, context.baritone.settings())) {
            return COST_INF;
        }
        if (context.get(x, y - 1, z).isIn(BlockTags.CLIMBABLE)) {
            // Larger entities cannot use ladders and stuff
            return context.requiredSideSpace == 0 ? LADDER_DOWN_ONE_COST : COST_INF;
        } else {
            double totalHardness = 0;
            int requiredSideSpace = context.requiredSideSpace;
            for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
                for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                    // If we are at the starting position, we already cleared enough space to stand there
                    // So only need to check the blocks below us
                    int checkedX = x + dx;
                    int checkedZ = z + dz;
                    BlockState toBreak = context.get(checkedX, y - 1, checkedZ);
                    // we're standing on it, while it might be block falling, it'll be air by the time we get here in the movement
                    totalHardness += MovementHelper.getMiningDurationTicks(context, checkedX, y - 1, checkedZ, toBreak, false);
                }
            }
            return FALL_N_BLOCKS_COST[1] + totalHardness;
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.feetPos().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        } else if (!playerInValidPosition()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        } else if (((Baritone) this.baritone).bsi.get0(this.baritone.getPlayerContext().feetPos().down()).getBlock() == Blocks.SCAFFOLDING) {
            // Sneak to go down scaffolding
            state.setInput(Input.SNEAK, true);
        }
        double diffX = ctx.entity().getX() - (dest.getX() + 0.5);
        double diffZ = ctx.entity().getZ() - (dest.getZ() + 0.5);
        double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);

        if (numTicks++ < 10 && ab < 0.2) {
            return state;
        }
        MovementHelper.moveTowards(ctx, state, dest);
        return state;
    }
}
