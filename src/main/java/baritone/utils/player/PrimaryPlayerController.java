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

package baritone.utils.player;

import baritone.api.utils.Helper;
import baritone.utils.accessor.IPlayerController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;


/**
 * Implementation of {@link baritone.api.utils.IPlayerController} that chains to the primary player controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public enum PrimaryPlayerController implements baritone.api.utils.IPlayerController, Helper {

    INSTANCE;

    @Override
    public void syncHeldItem() {
        ((IPlayerController) mc.interactionManager).callSyncSelectedSlot();
    }

    @Override
    public boolean hasBrokenBlock() {
        return ((IPlayerController) mc.interactionManager).getCurrentBreakingPos().getY() == -1;
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return mc.interactionManager.updateBlockBreakingProgress(pos, side);
    }

    @Override
    public void resetBlockRemoving() {
        mc.interactionManager.cancelBlockBreaking();
    }

    @Override
    public ItemStack windowClick(int windowId, int slotId, int mouseButton, SlotActionType type, PlayerEntity player) {
        return mc.interactionManager.clickSlot(windowId, slotId, mouseButton, type, player);
    }

    @Override
    public GameMode getGameType() {
        return mc.interactionManager.getCurrentGameMode();
    }

    @Override
    public ActionResult processRightClickBlock(PlayerEntity player, World world, Hand hand, BlockHitResult result) {
        // primaryplayercontroller is always in a ClientWorld so this is ok
        return mc.interactionManager.interactBlock((ClientPlayerEntity) player, (ClientWorld) world, hand, result);
    }

    @Override
    public ActionResult processRightClick(PlayerEntity player, World world, Hand hand) {
        return mc.interactionManager.interactItem(player, world, hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return mc.interactionManager.attackBlock(loc, face);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        ((IPlayerController) mc.interactionManager).setBreakingBlock(hittingBlock);
    }
}
