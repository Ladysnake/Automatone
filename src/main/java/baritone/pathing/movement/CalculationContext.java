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

import baritone.Automatone;
import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.ActionCosts;
import baritone.behavior.InventoryBehavior;
import baritone.cache.WorldData;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import baritone.utils.accessor.ILivingEntityAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

/**
 * @author Brady
 * @since 8/7/2018
 */
public class CalculationContext {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);

    public final boolean safeForThreadedUse;
    public final IBaritone baritone;
    public final World world;
    public final WorldData worldData;
    public final BlockStateInterface bsi;
    public final @Nullable ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAt256;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    public final int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final int worldHeight;
    public final int width;
    /**The extra space required on each side of the entity for free movement; 0 in the case of a normal size player*/
    public final int requiredSideSpace;
    public final int height;
    private final PlayerEntity player;
    private final BlockPos.Mutable blockPos;
    public final int breathTime;
    private final int airIncreaseOnLand;
    private final int airDecreaseInWater;

    public CalculationContext(IBaritone baritone) {
        this(baritone, false);
    }

    public CalculationContext(IBaritone baritone, boolean forUseOnAnotherThread) {
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.baritone = baritone;
        LivingEntity entity = baritone.getPlayerContext().entity();
        this.player = entity instanceof PlayerEntity ? (PlayerEntity) entity : null;
        this.world = baritone.getPlayerContext().world();
        this.worldData = (WorldData) baritone.getWorldProvider().getCurrentWorld();
        this.bsi = new BlockStateInterface(world);
        this.toolSet = player == null ? null : new ToolSet(player);
        this.hasThrowaway = baritone.settings().allowPlace.get() && ((Baritone) baritone).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket = player != null && baritone.settings().allowWaterBucketFall.get() && PlayerInventory.isValidHotbarIndex(InventoryBehavior.getSlotWithStack(player.inventory, Automatone.WATER_BUCKETS)) && !world.getDimension().isUltrawarm();
        this.canSprint = player != null && baritone.settings().allowSprint.get() && player.getHungerManager().getFoodLevel() > 6;
        this.placeBlockCost = baritone.settings().blockPlacementPenalty.get();
        this.allowBreak = baritone.settings().allowBreak.get();
        this.allowParkour = baritone.settings().allowParkour.get();
        this.allowParkourPlace = baritone.settings().allowParkourPlace.get();
        this.allowJumpAt256 = baritone.settings().allowJumpAt256.get();
        this.allowParkourAscend = baritone.settings().allowParkourAscend.get();
        this.assumeWalkOnWater = baritone.settings().assumeWalkOnWater.get();
        this.allowDiagonalDescend = baritone.settings().allowDiagonalDescend.get();
        this.allowDiagonalAscend = baritone.settings().allowDiagonalAscend.get();
        this.allowDownward = baritone.settings().allowDownward.get();
        this.maxFallHeightNoWater = baritone.settings().maxFallHeightNoWater.get();
        this.maxFallHeightBucket = baritone.settings().maxFallHeightBucket.get();
        int depth = EnchantmentHelper.getDepthStrider(entity);
        if (depth > 3) {
            depth = 3;
        }
        float mult = depth / 3.0F;
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST * (1 - mult) + ActionCosts.WALK_ONE_BLOCK_COST * mult;
        this.breakBlockAdditionalCost = baritone.settings().blockBreakAdditionalPenalty.get();
        this.backtrackCostFavoringCoefficient = baritone.settings().backtrackCostFavoringCoefficient.get();
        this.jumpPenalty = baritone.settings().jumpPenalty.get();
        this.walkOnWaterOnePenalty = baritone.settings().walkOnWaterOnePenalty.get();
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldHeight = world.getHeight();
        EntityDimensions dimensions = entity.getDimensions(EntityPose.STANDING);
        this.width = MathHelper.ceil(dimensions.width);
        // Note: if width is less than 1 (but not negative), we get side space of 0
        this.requiredSideSpace = getRequiredSideSpace(dimensions);
        this.height = MathHelper.ceil(dimensions.height);
        this.blockPos = new BlockPos.Mutable();
        this.breathTime = entity.getMaxAir();
        this.airIncreaseOnLand = ((ILivingEntityAccessor) entity).automatone$getNextAirOnLand(0);
        this.airDecreaseInWater = breathTime - ((ILivingEntityAccessor) entity).automatone$getNextAirUnderwater(breathTime);
    }

    public static int getRequiredSideSpace(EntityDimensions dimensions) {
        return MathHelper.ceil((dimensions.width - 1) * 0.5f);
    }

    public final IBaritone getBaritone() {
        return baritone;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, BlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (isProtected(x, y, z)) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak) {
            return COST_INF;
        }
        if (isProtected(x, y, z)) {
            return COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    public boolean canPlaceAgainst(BlockPos pos) {
        return this.canPlaceAgainst(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean canPlaceAgainst(int againstX, int againstY, int againstZ) {
        return this.canPlaceAgainst(againstX, againstY, againstZ, this.bsi.get0(againstX, againstY, againstZ));
    }

    public boolean canPlaceAgainst(int againstX, int againstY, int againstZ, BlockState state) {
        return !this.isProtected(againstX, againstY, againstZ) && MovementHelper.canPlaceAgainst(this.bsi, againstX, againstY, againstZ, state);
    }

    public boolean isProtected(int x, int y, int z) {
        this.blockPos.set(x, y, z);
        return this.player != null && !world.canPlayerModifyAt(this.player, this.blockPos);
    }

    public double oxygenCost(double baseCost, BlockState headState) {
        if (headState.getFluidState().isIn(FluidTags.WATER) && !headState.isOf(Blocks.BUBBLE_COLUMN)) {
            return airDecreaseInWater * baseCost;
        }
        return -1 * airIncreaseOnLand * baseCost;
    }
}
