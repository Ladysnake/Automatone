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

package baritone.api.utils;

import baritone.api.utils.accessor.IItemStack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BlockOptionalMeta {

    private final Block block;
    private final Set<BlockState> blockstates;
    private final IntSet stateHashes;
    private final IntSet stackHashes;
    private static final Pattern pattern = Pattern.compile("^(.+?)(?::(\\d+))?$");
    private static final Map<Block, List<Item>> drops = new HashMap<>();

    public BlockOptionalMeta(ServerWorld world, @Nonnull Block block) {
        this.block = block;
        this.blockstates = getStates(block);
        this.stateHashes = getStateHashes(blockstates);
        this.stackHashes = getStackHashes(world, blockstates);
    }

    public BlockOptionalMeta(ServerWorld world, @Nonnull String selector) {
        Matcher matcher = pattern.matcher(selector);

        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid block selector");
        }

        MatchResult matchResult = matcher.toMatchResult();

        block = BlockUtils.stringToBlockRequired(matchResult.group(1));
        blockstates = getStates(block);
        stateHashes = getStateHashes(blockstates);
        stackHashes = getStackHashes(world, blockstates);
    }

    private static Set<BlockState> getStates(@Nonnull Block block) {
        return new HashSet<>(block.getStateManager().getStates());
    }

    private static IntSet getStateHashes(Set<BlockState> blockstates) {
        return blockstates.stream()
                        .map(BlockState::hashCode)
                        .collect(Collectors.toCollection(IntOpenHashSet::new));
    }

    private static IntSet getStackHashes(ServerWorld world, Set<BlockState> blockstates) {
        //noinspection ConstantConditions
        return blockstates.stream()
                        .flatMap(state -> drops(world, state.getBlock())
                                .stream()
                                .map(item -> new ItemStack(item, 1))
                        )
                        .map(stack -> ((IItemStack) (Object) stack).getBaritoneHash())
                        .collect(Collectors.toCollection(IntOpenHashSet::new));
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(@Nonnull Block block) {
        return block == this.block;
    }

    public boolean matches(@Nonnull BlockState blockstate) {
        Block block = blockstate.getBlock();
        return block == this.block && stateHashes.contains(blockstate.hashCode());
    }

    public boolean matches(ItemStack stack) {
        //noinspection ConstantConditions
        int hash = ((IItemStack) (Object) stack).getBaritoneHash();

        hash -= stack.getDamage();

        return stackHashes.contains(hash);
    }

    @Override
    public String toString() {
        return String.format("BlockOptionalMeta{block=%s}", block);
    }

    public BlockState getAnyBlockState() {
        if (blockstates.size() > 0) {
            return blockstates.iterator().next();
        }

        return null;
    }

    // TODO check if erasing the metadata of both the block and the drops is a good idea
    private static synchronized List<Item> drops(ServerWorld world, Block b) {
        return drops.computeIfAbsent(b, block -> {
            Identifier lootTableLocation = block.getLootTableId();
            if (lootTableLocation == LootTables.EMPTY) {
                return Collections.emptyList();
            } else {
                List<Item> items = new ArrayList<>();

                world.getServer().getLootManager().getLootTable(lootTableLocation).generateLoot(
                    new LootContext.Builder(new LootContextParameterSet.Builder(world)
                            .add(LootContextParameters.ORIGIN, Vec3d.of(BlockPos.ZERO))
                            .add(LootContextParameters.TOOL, ItemStack.EMPTY)
                            .addOptional(LootContextParameters.BLOCK_ENTITY, null)
                            .add(LootContextParameters.BLOCK_STATE, block.getDefaultState())
                            .build(LootContextTypes.BLOCK))
                        .withRandomSeed(world.getSeed())
                        .build(null),
                    stack -> items.add(stack.getItem())
                );
                return items;
            }
        });
    }
}
