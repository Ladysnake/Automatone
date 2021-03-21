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

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.IEntityContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class BlockPlaceHelper {

    private final IEntityContext ctx;
    private int rightClickTimer;

    BlockPlaceHelper(IEntityContext playerContext) {
        this.ctx = playerContext;
    }

    public void tick(boolean rightClickRequested) {
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = ctx.objectMouseOver();
        boolean isRowingBoat = ctx.entity().getVehicle() != null && ctx.entity().getVehicle() instanceof BoatEntity;
        if (!rightClickRequested  || !(ctx.entity() instanceof PlayerEntity) || isRowingBoat || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }

        rightClickTimer = BaritoneAPI.getSettings().rightClickSpeed.get();
        PlayerEntity player = (PlayerEntity) ctx.entity();

        for (Hand hand : Hand.values()) {
            if (ctx.playerController().processRightClickBlock(player, ctx.world(), hand, (BlockHitResult) mouseOver) == ActionResult.SUCCESS) {
                player.swingHand(hand);
                return;
            }
            if (!player.getStackInHand(hand).isEmpty() && ctx.playerController().processRightClick(player, ctx.world(), hand) == ActionResult.SUCCESS) {
                return;
            }
        }
    }
}
