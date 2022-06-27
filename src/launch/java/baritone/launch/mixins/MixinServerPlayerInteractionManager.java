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

package baritone.launch.mixins;

import baritone.utils.accessor.IServerPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class MixinServerPlayerInteractionManager implements IServerPlayerInteractionManager {
    private boolean automatone$brokeBlock;

    @Override
    @Accessor
    public abstract boolean isMining();
    @Override
    @Accessor
    public abstract BlockPos getMiningPos();
    @Accessor
    public abstract int getBlockBreakingProgress();

    @Override
    public boolean automatone$hasBrokenBlock() {
        return this.automatone$brokeBlock;
    }

    @Inject(method = "processBlockBreakingAction", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;miningPos:Lnet/minecraft/util/math/BlockPos;", opcode = Opcodes.PUTFIELD))
    private void setBrokeBlock(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction, int worldHeight, int i, CallbackInfo ci) {
        this.automatone$brokeBlock = true;
    }
}
