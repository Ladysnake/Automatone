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

import baritone.api.utils.Helper;
import baritone.api.utils.IEntityContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {

    private final IEntityContext ctx;
    private @Nullable BlockPos lastPos;

    BlockBreakHelper(IEntityContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        if (ctx.entity() != null && lastPos != null) {
            if (!ctx.playerController().hasBrokenBlock()) {
                // insane bypass to check breaking succeeded
                ctx.playerController().setHittingBlock(true);
            }
            ctx.playerController().resetBlockRemoving();
            lastPos = null;
        }
    }

    public void tick(boolean isLeftClick) {
        HitResult trace = ctx.objectMouseOver();
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;

        if (isLeftClick && isBlockTrace) {
            BlockPos pos = ((BlockHitResult) trace).getBlockPos();
            if (!Objects.equals(lastPos, pos)) {
                ctx.playerController().clickBlock(pos, ((BlockHitResult) trace).getSide());
                ctx.entity().swingHand(Hand.MAIN_HAND);
            }

            // Attempt to break the block
            if (ctx.playerController().onPlayerDamageBlock(pos, ((BlockHitResult) trace).getSide())) {
                ctx.entity().swingHand(Hand.MAIN_HAND);
            }

            ctx.playerController().setHittingBlock(false);

            lastPos = pos;
        } else if (lastPos != null) {
            stopBreakingBlock();
            lastPos = null;
        }
    }
}
