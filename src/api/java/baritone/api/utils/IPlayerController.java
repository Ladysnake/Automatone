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

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.component.TransientComponent;
import net.minecraft.entity.player.PlayerEntity;
import baritone.api.BaritoneAPI;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/**
 * @author Brady
 * @since 12/14/2018
 */
public interface IPlayerController extends TransientComponent {
    ComponentKey<IPlayerController> KEY = ComponentRegistry.getOrCreate(new Identifier("automatone", "controller"), IPlayerController.class);

    boolean hasBrokenBlock();

    boolean onPlayerDamageBlock(BlockPos pos, Direction side);

    void resetBlockRemoving();

    GameMode getGameType();

    ActionResult processRightClickBlock(PlayerEntity player, World world, Hand hand, BlockHitResult result);

    ActionResult processRightClick(PlayerEntity player, World world, Hand hand);

    boolean clickBlock(BlockPos loc, Direction face);

    void setHittingBlock(boolean hittingBlock);

    default double getBlockReachDistance() {
        return this.getGameType().isCreative() ? 5.0F : BaritoneAPI.getSettings().blockReachDistance.value;
    }
}
