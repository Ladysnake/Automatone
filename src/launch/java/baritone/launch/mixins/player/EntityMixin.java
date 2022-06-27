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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @SuppressWarnings("unused") // makes the field mutable for use by IEntityAccessor
    @Shadow @Mutable @Final private EntityType<?> type;

    @Dynamic("hasPlayerRider player check lambda")
    @Inject(method = "m_lsaraprt", at = @At(value = "HEAD"), cancellable = true)
    private static void removeFakePlayers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AutomatoneFakePlayer) {
            cir.setReturnValue(false);
        }
    }
}
