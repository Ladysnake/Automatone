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

package baritone.behavior;

import baritone.Baritone;
import baritone.utils.ToolSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;

public final class InventoryBehavior extends Behavior {

    public InventoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTickServer() {
        if (!baritone.settings().allowInventory.get()) {
            return;
        }
        if (!(ctx.entity() instanceof PlayerEntity player)) {
            return;
        }
        if (player.playerScreenHandler != player.currentScreenHandler) {
            // we have a crafting table or a chest or something open
            return;
        }
        if (firstValidThrowaway(player.getInventory()) >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            swapWithHotBar(firstValidThrowaway(player.getInventory()), 8, player.getInventory());
        }
        int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
        if (pick >= 9) {
            swapWithHotBar(pick, 0, player.getInventory());
        }
    }

    public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar, PlayerInventory inventory) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        if (destination.isPresent()) {
            swapWithHotBar(inMainInvy, destination.getAsInt(), inventory);
        }
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        PlayerInventory inventory = ctx.inventory();
        if (inventory == null) return OptionalInt.empty();

        // we're using 0 and 8 for pickaxe and throwaway
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            if (inventory.main.get(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
    }

    private void swapWithHotBar(int inInventory, int inHotbar, PlayerInventory inventory) {
        ItemStack h = inventory.getStack(inHotbar);
        inventory.setStack(inHotbar, inventory.getStack(inInventory));
        inventory.setStack(inInventory, h);
    }

    private int firstValidThrowaway(PlayerInventory inventory) { // TODO offhand idk
        DefaultedList<ItemStack> invy = inventory.main;
        for (int i = 0; i < invy.size(); i++) {
            if (invy.get(i).isIn(baritone.settings().acceptableThrowawayItems.get())) {
                return i;
            }
        }
        return -1;
    }

    private int bestToolAgainst(Block against, Class<? extends ToolItem> cla$$) {
        DefaultedList<ItemStack> invy = ctx.inventory().main;
        int bestInd = -1;
        double bestSpeed = -1;
        for (int i = 0; i < invy.size(); i++) {
            ItemStack stack = invy.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (baritone.settings().itemSaver.get() && stack.getDamage() >= stack.getMaxDamage() && stack.getMaxDamage() > 1) {
                continue;
            }
            if (cla$$.isInstance(stack.getItem())) {
                double speed = ToolSet.calculateSpeedVsBlock(stack, against.getDefaultState()); // takes into account enchants
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInd = i;
                }
            }
        }
        return bestInd;
    }

    public boolean hasGenericThrowaway() {
        return throwaway(false,
                stack -> stack.isIn(baritone.settings().acceptableThrowawayItems.get()));
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        if (!(ctx.entity() instanceof PlayerEntity player)) return false;

        BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && maybe.equals(((BlockItem) stack.getItem()).getBlock().getPlacementState(new ItemPlacementContext(new ItemUsageContext(ctx.world(), player, Hand.MAIN_HAND, stack, new BlockHitResult(new Vec3d(player.getX(), player.getY(), player.getZ()), Direction.UP, ctx.feetPos(), false)) {}))))) {
            return true; // gotem
        }
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
        }
        return throwaway(select,
                stack -> stack.isIn(baritone.settings().acceptableThrowawayItems.get()));
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        if (!(ctx.entity() instanceof PlayerEntity p)) return false;

        DefaultedList<ItemStack> inv = p.getInventory().main;
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.get(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    p.getInventory().selectedSlot = i;
                }
                return true;
            }
        }
        if (desired.test(p.getInventory().offHand.get(0))) {
            // main hand takes precedence over off hand
            // that means that if we have block A selected in main hand and block B in off hand, right clicking places block B
            // we've already checked above ^ and the main hand can't possible have an acceptablethrowawayitem
            // so we need to select in the main hand something that doesn't right click
            // so not a shovel, not a hoe, not a block, etc
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.get(i);
                if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
                    if (select) {
                        p.getInventory().selectedSlot = i;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static int getSlotWithStack(PlayerInventory inv, TagKey<Item> tag) {
        for(int i = 0; i < inv.main.size(); ++i) {
            if (!inv.main.get(i).isEmpty() && inv.main.get(i).isIn(tag)) {
                return i;
            }
        }

        return -1;
    }
}
