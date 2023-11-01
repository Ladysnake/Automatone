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

package baritone.pathing.movement;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementState.MovementTarget;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.Optional;

import static baritone.pathing.movement.Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP;

/**
 * Static helpers for cost calculation
 *
 * @author leijurv
 */
public interface MovementHelper extends ActionCosts {

    static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block b = state.getBlock();
        return b == Blocks.ICE // ice becomes water, and water can mess up the path
                || b instanceof InfestedBlock // obvious reasons
                // call context.get directly with x,y,z. no need to make 5 new BlockPos for no reason
                || avoidAdjacentBreaking(bsi, x, y + 1, z, true, settings)
                || avoidAdjacentBreaking(bsi, x + 1, y, z, false, settings)
                || avoidAdjacentBreaking(bsi, x - 1, y, z, false, settings)
                || avoidAdjacentBreaking(bsi, x, y, z + 1, false, settings)
                || avoidAdjacentBreaking(bsi, x, y, z - 1, false, settings);
    }

    static boolean avoidAdjacentBreaking(BlockStateInterface bsi, int x, int y, int z, boolean directlyAbove, Settings settings) {
        // returns true if you should avoid breaking a block that's adjacent to this one (e.g. lava that will start flowing if you give it a path)
        // this is only called for north, south, east, west, and up. this is NOT called for down.
        // we assume that it's ALWAYS okay to break the block thats ABOVE liquid
        BlockState state = bsi.get0(x, y, z);
        Block block = state.getBlock();
        if (!directlyAbove // it is fine to mine a block that has a falling block directly above, this (the cost of breaking the stacked fallings) is included in cost calculations
                // therefore if directlyAbove is true, we will actually ignore if this is falling
                && block instanceof FallingBlock // obviously, this check is only valid for falling blocks
                && settings.avoidUpdatingFallingBlocks.get() // and if the setting is enabled
                && FallingBlock.canFallThrough(bsi.get0(x, y - 1, z))) { // and if it would fall (i.e. it's unsupported)
            return true; // dont break a block that is adjacent to unsupported gravel because it can cause really weird stuff
        }
        return !state.getFluidState().isEmpty();
    }

    static boolean canWalkThrough(IEntityContext ctx, BetterBlockPos pos) {
        return canWalkThrough(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
        return canWalkThrough(bsi, x, y, z, bsi.get0(x, y, z), settings);
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block block = state.getBlock();
        if (block instanceof AirBlock) { // early return for most common case
            return true;
        }
        if (block instanceof AbstractFireBlock
                || block == Blocks.TRIPWIRE
                || block == Blocks.COBWEB
                || block == Blocks.END_PORTAL
                || block == Blocks.COCOA
                || block instanceof AbstractSkullBlock
                || block == Blocks.BUBBLE_COLUMN
                || block instanceof ShulkerBoxBlock
                || block instanceof SlabBlock
                || block instanceof TrapdoorBlock
                || block == Blocks.HONEY_BLOCK
                || block == Blocks.END_ROD) {
            return false;
        }
        if (state.isIn(settings.blocksToAvoid.get())) {
            return false;
        }
        if (block instanceof DoorBlock || block instanceof FenceGateBlock) {
            // Because there's no nice method in vanilla to check if a door is openable or not, we just have to assume
            // that all wooden doors are openable and vice versa.
            return block instanceof FenceGateBlock || DoorBlock.isWoodenDoor(state);
        }
        if (block instanceof CarpetBlock) {
            return canWalkOn(bsi, x, y - 1, z, settings);
        }
        if (block instanceof SnowBlock) {
            // we've already checked doors and fence gates
            // so the only remaining dynamic isPassables are snow and trapdoor
            // if they're cached as a top block, we don't know their metadata
            // default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            // the check in BlockSnow.isPassable is layers < 5
            // while actually, we want < 3 because 3 or greater makes it impassable in a 2 high ceiling
            if (state.get(SnowBlock.LAYERS) >= 3) {
                return false;
            }
            // ok, it's low enough we could walk through it, but is it supported?
            return canWalkOn(bsi, x, y - 1, z, settings);
        }
        if (isFlowing(x, y, z, state, bsi)) {
            return false; // Don't walk through flowing liquids
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.getFluid() instanceof WaterFluid) {
            if (settings.assumeWalkOnWater.get()) {
                return false;
            }
            BlockState up = bsi.get0(x, y + 1, z);
            if ((!settings.allowSwimming.get() && !up.getFluidState().isEmpty()) || up.getBlock() instanceof LilyPadBlock) {
                return false;
            }
            return true;
        }
        // every block that overrides isPassable with anything more complicated than a "return true;" or "return false;"
        // has already been accounted for above
        // therefore it's safe to not construct a blockpos from our x, y, z ints and instead just pass null
        return state.canPathfindThrough(bsi.access, BlockPos.ORIGIN, NavigationType.LAND); // workaround for future compatibility =P
    }

    /**
     * canWalkThrough but also won't impede movement at all. so not including doors or fence gates (we'd have to right click),
     * not including water, and not including ladders or vines or cobwebs (they slow us down)
     *
     * @param context Calculation context to provide block state lookup
     * @param x       The block's x position
     * @param y       The block's y position
     * @param z       The block's z position
     * @return Whether or not the block at the specified position
     */
    static boolean fullyPassable(CalculationContext context, int x, int y, int z) {
        return fullyPassable(
                context.bsi.access,
                context.bsi.isPassableBlockPos.set(x, y, z),
                context.bsi.get0(x, y, z)
        );
    }

    static boolean fullyPassable(IEntityContext ctx, BlockPos pos) {
        return fullyPassable(ctx.world(), pos, ctx.world().getBlockState(pos));
    }

    static boolean fullyPassable(BlockView access, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AirBlock) { // early return for most common case
            return true;
        }
        // exceptions - blocks that are isPassable true, but we can't actually jump through
        if (block instanceof AbstractFireBlock
                || block == Blocks.TRIPWIRE
                || block == Blocks.COBWEB
                || block == Blocks.VINE
                || block == Blocks.LADDER
                || block == Blocks.COCOA
                || block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof SnowBlock
                || !state.getFluidState().isEmpty()
                || block instanceof TrapdoorBlock
                || block instanceof EndPortalBlock
                || block instanceof SkullBlock
                || block instanceof ShulkerBoxBlock) {
            return false;
        }
        // door, fence gate, liquid, trapdoor have been accounted for, nothing else uses the world or pos parameters
        return state.canPathfindThrough(access, pos, NavigationType.LAND);
    }

    static boolean isReplaceable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        // for MovementTraverse and MovementAscend
        // block double plant defaults to true when the block doesn't match, so don't need to check that case
        // all other overrides just return true or false
        // the only case to deal with is snow
        /*
         *  public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos)
         *     {
         *         return ((Integer)worldIn.getBlockState(pos).getValue(LAYERS)).intValue() == 1;
         *     }
         */
        Block block = state.getBlock();
        if (block instanceof AirBlock) {
            // early return for common cases hehe
            return true;
        }
        if (block instanceof SnowBlock) {
            // as before, default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            return state.get(SnowBlock.LAYERS) == 1;
        }
        if (block == Blocks.LARGE_FERN || block == Blocks.TALL_GRASS) {
            return true;
        }
        return state.materialReplaceable();
    }

    @Deprecated
    static boolean isReplacable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        return isReplaceable(x, y, z, state, bsi);
    }

    static boolean isDoorPassable(IEntityContext ctx, BlockPos doorPos, BlockPos playerPos) {
        if (playerPos.equals(doorPos)) {
            return false;
        }

        BlockState state = BlockStateInterface.get(ctx, doorPos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return true;
        }

        return isHorizontalBlockPassable(doorPos, state, playerPos, DoorBlock.OPEN);
    }

    static boolean isGatePassable(IEntityContext ctx, BlockPos gatePos, BlockPos playerPos) {
        if (playerPos.equals(gatePos)) {
            return false;
        }

        BlockState state = BlockStateInterface.get(ctx, gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return true;
        }

        return state.get(FenceGateBlock.OPEN);
    }

    static boolean isHorizontalBlockPassable(BlockPos blockPos, BlockState blockState, BlockPos playerPos, BooleanProperty propertyOpen) {
        if (playerPos.equals(blockPos)) {
            return false;
        }

        Direction.Axis facing = blockState.get(HorizontalFacingBlock.FACING).getAxis();
        boolean open = blockState.get(propertyOpen);

        Direction.Axis playerFacing;
        if (playerPos.north().equals(blockPos) || playerPos.south().equals(blockPos)) {
            playerFacing = Direction.Axis.Z;
        } else if (playerPos.east().equals(blockPos) || playerPos.west().equals(blockPos)) {
            playerFacing = Direction.Axis.X;
        } else {
            return true;
        }

        return (facing == playerFacing) == open;
    }

    static boolean avoidWalkingInto(BlockState state) {
        Block block = state.getBlock();
        return !state.getFluidState().isEmpty()
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block instanceof AbstractFireBlock
                || block == Blocks.END_PORTAL
                || block == Blocks.COBWEB
                || block == Blocks.BUBBLE_COLUMN;
    }

    /**
     * Can I walk on this block without anything weird happening like me falling
     * through? Includes water because we know that we automatically jump on
     * water
     *
     * @param bsi   Block state provider
     * @param x     The block's x position
     * @param y     The block's y position
     * @param z     The block's z position
     * @param state The state of the block at the specified location
     * @param settings
     * @return Whether or not the specified block can be walked on
     */
    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block block = state.getBlock();
        if (block instanceof AirBlock || block == Blocks.MAGMA_BLOCK || block == Blocks.BUBBLE_COLUMN || block == Blocks.HONEY_BLOCK) {
            // early return for most common case (air)
            // plus magma, which is a normal cube but it hurts you
            return false;
        }
        if (isBlockNormalCube(state)) {
            return true;
        }
        if (state.isIn(BlockTags.CLIMBABLE)) { // TODO reconsider this
            return true;
        }
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return true;
        }
        if (block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return true;
        }
        if (isWater(state)) {
            // since this is called literally millions of times per second, the benefit of not allocating millions of useless "pos.up()"
            // BlockPos s that we'd just garbage collect immediately is actually noticeable. I don't even think its a decrease in readability
            BlockState upState = bsi.get0(x, y + 1, z);
            Block up = upState.getBlock();
            if (up == Blocks.LILY_PAD || up instanceof CarpetBlock) {
                return true;
            }
            if (isFlowing(x, y, z, state, bsi) || upState.getFluidState().getFluid() == Fluids.FLOWING_WATER) {
                // the only scenario in which we can walk on flowing water is if it's under still water with jesus off
                return isWater(upState) && !settings.assumeWalkOnWater.get();
            }
            // if assumeWalkOnWater is on, we can only walk on water if there isn't water above it
            // if assumeWalkOnWater is off, we can only walk on water if there is water above it
            return isWater(upState) ^ settings.assumeWalkOnWater.get();
        }
        if (settings.assumeWalkOnLava.get() && isLava(state) && !isFlowing(x, y, z, state, bsi)) {
            return true;
        }
        if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
            return true;
        }
        if (block instanceof SlabBlock) {
            if (!settings.allowWalkOnBottomSlab.get()) {
                return state.get(SlabBlock.TYPE) != SlabType.BOTTOM;
            }
            return true;
        }
        return block instanceof StairsBlock;
    }

    static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos, BlockState state) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, state, ctx.baritone().settings());
    }

    static boolean canWalkOn(IEntityContext ctx, BlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ(), ctx.baritone().settings());
    }

    static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
    }

    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
        return canWalkOn(bsi, x, y, z, bsi.get0(x, y, z), settings);
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z) {
        return canPlaceAgainst(bsi, x, y, z, bsi.get0(x, y, z));
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, BlockPos pos) {
        return canPlaceAgainst(bsi, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean canPlaceAgainst(IEntityContext ctx, BlockPos pos) {
        return canPlaceAgainst(new BlockStateInterface(ctx), pos);
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
        // can we look at the center of a side face of this block and likely be able to place?
        // (thats how this check is used)
        // therefore dont include weird things that we technically could place against (like carpet) but practically can't
        return isBlockNormalCube(state) || state.getBlock() == Blocks.GLASS || state.getBlock() instanceof StainedGlassBlock;
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, boolean includeFalling) {
        return getMiningDurationTicks(context, x, y, z, context.get(x, y, z), includeFalling);
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, BlockState state, boolean includeFalling) {
        if (!canWalkThrough(context.bsi, x, y, z, state, context.baritone.settings())) {
            if (!state.getFluidState().isEmpty()) {
                return COST_INF;
            }
            double mult = context.breakCostMultiplierAt(x, y, z, state);
            if (mult >= COST_INF) {
                return COST_INF;
            }
            if (avoidBreaking(context.bsi, x, y, z, state, context.baritone.settings())) {
                return COST_INF;
            }
            if (context.toolSet == null) {
                return COST_INF;
            }
            double strVsBlock = context.toolSet.getStrVsBlock(state);
            if (strVsBlock <= 0) {
                return COST_INF;
            }
            double result = 1 / strVsBlock;
            result += context.breakBlockAdditionalCost;
            result *= mult;
            if (includeFalling) {
                BlockState above = context.get(x, y + 1, z);
                if (above.getBlock() instanceof FallingBlock) {
                    result += getMiningDurationTicks(context, x, y + 1, z, above, true);
                }
            }
            return result;
        }
        return 0; // we won't actually mine it, so don't check fallings above
    }

    // TODO handle other collision boxes
    static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * AutoTool for a specific block
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     */
    static void switchToBestToolFor(IEntityContext ctx, BlockState b) {
        LivingEntity entity = ctx.entity();
        if (entity instanceof PlayerEntity) {
            switchToBestToolFor(ctx, b, new ToolSet((PlayerEntity) entity), ctx.baritone().settings().preferSilkTouch.get());
        }
    }

    /**
     * AutoTool for a specific block with precomputed ToolSet data
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     * @param ts  previously calculated ToolSet
     */
    static void switchToBestToolFor(IEntityContext ctx, BlockState b, ToolSet ts, boolean preferSilkTouch) {
        PlayerInventory inventory = ctx.inventory();

        if (inventory != null && !ctx.baritone().settings().disableAutoTool.get() && !ctx.baritone().settings().assumeExternalAutoTool.get()) {
            inventory.selectedSlot = ts.getBestSlot(b.getBlock(), preferSilkTouch);
        }
    }

    static void moveTowards(IEntityContext ctx, MovementState state, BlockPos pos) {
        state.setTarget(new MovementTarget(
                new Rotation(RotationUtils.calcRotationFromVec3d(ctx.headPos(),
                        VecUtils.getBlockPosCenter(pos),
                        ctx.entityRotations()).getYaw(), ctx.entity().getPitch()),
                false
        )).setInput(Input.MOVE_FORWARD, true);
    }

    /**
     * Returns whether or not the specified block is
     * water, regardless of whether or not it is flowing.
     *
     * @param state The block state
     * @return Whether or not the block is water
     */
    static boolean isWater(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER);
    }

    /**
     * Returns whether or not the block at the specified pos is
     * water, regardless of whether or not it is flowing.
     *
     * @param ctx The player context
     * @param bp  The block pos
     * @return Whether or not the block is water
     */
    static boolean isWater(IEntityContext ctx, BlockPos bp) {
        return isWater(BlockStateInterface.get(ctx, bp));
    }

    static boolean isLava(BlockState state) {
        Fluid f = state.getFluidState().getFluid();
        return f == Fluids.LAVA || f == Fluids.FLOWING_LAVA;
    }

    /**
     * Returns whether or not the specified pos has a liquid
     *
     * @param ctx The player context
     * @param p   The pos
     * @return Whether or not the block is a liquid
     */
    static boolean isLiquid(IEntityContext ctx, BlockPos p) {
        return isLiquid(BlockStateInterface.get(ctx, p));
    }

    static boolean isLiquid(BlockState blockState) {
        // PERF: getFluidState is kinda slow, but users can install Lithium to fix it
        return !blockState.getFluidState().isEmpty();
    }

    static boolean possiblyFlowing(BlockState state) {
        FluidState fluidState = state.getFluidState();
        return fluidState.getFluid() instanceof FlowableFluid
                && fluidState.getFluid().getLevel(fluidState) != 8;
    }

    static boolean isFlowing(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        FluidState fluidState = state.getFluidState();
        if (!(fluidState.getFluid() instanceof FlowableFluid)) {
            return false;
        }
        if (fluidState.getFluid().getLevel(fluidState) != 8) {
            return true;
        }
        return possiblyFlowing(bsi.get0(x + 1, y, z))
                || possiblyFlowing(bsi.get0(x - 1, y, z))
                || possiblyFlowing(bsi.get0(x, y, z + 1))
                || possiblyFlowing(bsi.get0(x, y, z - 1));
    }

    static boolean isBlockNormalCube(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BambooBlock
                || block instanceof PistonExtensionBlock
                || block instanceof ScaffoldingBlock
                || block instanceof ShulkerBoxBlock) {
            return false;
        }
        try {
            // PERF: isShapeFullCube is slow, but people can install lithium to fix it
            // World lookups too slow, just pass null and pray
            return state.isFullCube(null, BlockPos.ORIGIN);
        } catch (NullPointerException npe) {
            return false;
        }
    }

    static PlaceResult attemptToPlaceABlock(MovementState state, IBaritone baritone, BlockPos placeAt, boolean preferDown, boolean wouldSneak) {
        IEntityContext ctx = baritone.getPlayerContext();
        Optional<Rotation> direct = RotationUtils.reachable(ctx, placeAt, wouldSneak); // we assume that if there is a block there, it must be replacable
        boolean found = false;
        if (direct.isPresent()) {
            state.setTarget(new MovementState.MovementTarget(direct.get(), true));
            found = true;
        }
        for (int i = 0; i < 5; i++) {
            BlockPos against1 = placeAt.offset(HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]);
            if (MovementHelper.canPlaceAgainst(ctx, against1)) {
                if (!((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(false, placeAt.getX(), placeAt.getY(), placeAt.getZ())) { // get ready to place a throwaway block
                    baritone.logDebug("bb pls get me some blocks. dirt, netherrack, cobble");
                    state.setStatus(MovementStatus.UNREACHABLE);
                    return PlaceResult.NO_OPTION;
                }
                double faceX = (placeAt.getX() + against1.getX() + 1.0D) * 0.5D;
                double faceY = (placeAt.getY() + against1.getY() + 0.5D) * 0.5D;
                double faceZ = (placeAt.getZ() + against1.getZ() + 1.0D) * 0.5D;
                Rotation place = RotationUtils.calcRotationFromVec3d(wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.entity()) : ctx.headPos(), new Vec3d(faceX, faceY, faceZ), ctx.entityRotations());
                HitResult res = RayTraceUtils.rayTraceTowards(ctx.entity(), place, ctx.playerController().getBlockReachDistance(), wouldSneak);
                if (res != null && res.getType() == HitResult.Type.BLOCK && ((BlockHitResult) res).getBlockPos().equals(against1) && ((BlockHitResult) res).getBlockPos().offset(((BlockHitResult) res).getSide()).equals(placeAt)) {
                    state.setTarget(new MovementState.MovementTarget(place, true));
                    found = true;

                    if (!preferDown) {
                        // if preferDown is true, we want the last option
                        // if preferDown is false, we want the first
                        break;
                    }
                }
            }
        }
        if (ctx.getSelectedBlock().isPresent()) {
            BlockPos selectedBlock = ctx.getSelectedBlock().get();
            Direction side = ((BlockHitResult) ctx.objectMouseOver()).getSide();
            // only way for selectedBlock.equals(placeAt) to be true is if it's replacable
            if (selectedBlock.equals(placeAt) || (MovementHelper.canPlaceAgainst(ctx, selectedBlock) && selectedBlock.offset(side).equals(placeAt))) {
                if (wouldSneak) {
                    state.setInput(Input.SNEAK, true);
                }
                ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
                return PlaceResult.READY_TO_PLACE;
            }
        }
        if (found) {
            if (wouldSneak) {
                state.setInput(Input.SNEAK, true);
            }
            ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
            return PlaceResult.ATTEMPTING;
        }
        return PlaceResult.NO_OPTION;
    }

    enum PlaceResult {
        READY_TO_PLACE, ATTEMPTING, NO_OPTION;
    }

    static boolean isTransparent(Block b) {

        return b == Blocks.AIR ||
                b == Blocks.LAVA ||
                b == Blocks.WATER;
    }
}
