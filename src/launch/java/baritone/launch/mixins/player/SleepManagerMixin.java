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

package baritone.launch.mixins.player;

import baritone.api.fakeplayer.AutomatoneFakePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SleepManager.class)
public abstract class SleepManagerMixin {
    private boolean requiem$fakePlayerSleeping = false;

    @ModifyVariable(method = "update", at = @At(value = "STORE"))
    private ServerPlayerEntity captureSleepingPlayer(ServerPlayerEntity player) {
        requiem$fakePlayerSleeping = player instanceof AutomatoneFakePlayer;
        return player;
    }

    @ModifyVariable(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSleeping()Z"), ordinal = 0)
    private int discountFakePlayers(int spectatorPlayers) {
        if (requiem$fakePlayerSleeping) {
            requiem$fakePlayerSleeping = false;
            return spectatorPlayers + 1;
        }
        return spectatorPlayers;
    }
}
