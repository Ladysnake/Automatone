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

package baritone.cache;

import baritone.Automatone;
import baritone.api.BaritoneAPI;
import baritone.api.cache.IContainerMemory;
import baritone.api.cache.IRememberedInventory;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ContainerMemory implements IContainerMemory {

    /**
     * The current remembered inventories
     */
    // TODO hook up to ServerBlockEntityEvents to remember every inventory ever loaded :)
    private final Map<BlockPos, RememberedInventory> inventories = new HashMap<>();

    public void read(NbtCompound tag) {
        try {
            NbtList nbtInventories = tag.getList("inventories", NbtType.COMPOUND);
            for (int i = 0; i < nbtInventories.size(); i++) {
                NbtCompound nbtEntry = nbtInventories.getCompound(i);
                BlockPos pos = NbtHelper.toBlockPos(nbtEntry.getCompound("pos"));
                RememberedInventory rem = new RememberedInventory();
                rem.fromNbt(nbtEntry.getList("content", NbtType.LIST));
                if (rem.items.isEmpty()) {
                    continue; // this only happens if the list has no elements, not if the list has elements that are all empty item stacks
                }
                inventories.put(pos, rem);
            }
        } catch (Exception ex) {
            Automatone.LOGGER.error(ex);
            inventories.clear();
        }
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        if (BaritoneAPI.getGlobalSettings().containerMemory.get()) {
            NbtList list = new NbtList();
            for (Map.Entry<BlockPos, RememberedInventory> entry : inventories.entrySet()) {
                NbtCompound nbtEntry = new NbtCompound();
                nbtEntry.put("pos", NbtHelper.fromBlockPos(entry.getKey()));
                nbtEntry.put("content", entry.getValue().toNbt());
                list.add(nbtEntry);
            }
            tag.put("inventories", list);
        }
        return tag;
    }

    public synchronized void setup(BlockPos pos, int windowId, int slotCount) {
        RememberedInventory inventory = inventories.computeIfAbsent(pos, x -> new RememberedInventory());
        inventory.windowId = windowId;
        inventory.size = slotCount;
    }

    public synchronized Optional<RememberedInventory> getInventoryFromWindow(int windowId) {
        return inventories.values().stream().filter(i -> i.windowId == windowId).findFirst();
    }

    @Override
    public final synchronized RememberedInventory getInventoryByPos(BlockPos pos) {
        return inventories.get(pos);
    }

    @Override
    public final synchronized Map<BlockPos, IRememberedInventory> getRememberedInventories() {
        // make a copy since this map is modified from the packet thread
        return new HashMap<>(inventories);
    }

    /**
     * An inventory that we are aware of.
     * <p>
     * Associated with a {@link BlockPos} in {@link ContainerMemory#inventories}.
     */
    public static class RememberedInventory implements IRememberedInventory {

        /**
         * The list of items in the inventory
         */
        private final List<ItemStack> items;

        /**
         * The last known window ID of the inventory
         */
        private int windowId;

        /**
         * The size of the inventory
         */
        private int size;

        private RememberedInventory() {
            this.items = new ArrayList<>();
        }

        @Override
        public final List<ItemStack> getContents() {
            return Collections.unmodifiableList(this.items);
        }

        @Override
        public final int getSize() {
            return this.size;
        }

        public NbtList toNbt() {
            NbtList inv = new NbtList();
            for (ItemStack item : this.items) {
                inv.add(item.writeNbt(new NbtCompound()));
            }
            return inv;
        }

        public void fromNbt(NbtList content) {
            for (int i = 0; i < content.size(); i++) {
                this.items.add(ItemStack.fromNbt(content.getCompound(i)));
            }
            this.size = this.items.size();
            this.windowId = -1;
        }
    }
}
