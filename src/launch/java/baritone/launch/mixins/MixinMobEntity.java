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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.type.EventState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MixinMobEntity extends LivingEntity {
    protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreUpdate(CallbackInfo ci) {
        if (!this.world.isClient()) {
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneOrNull(this);
            if (baritone != null) {
                baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
            }
        }
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    private void onPostUpdate(CallbackInfo ci) {
        if (!this.world.isClient()) {
            IBaritone baritone = BaritoneAPI.getProvider().getBaritoneOrNull(this);
            if (baritone != null) {
                baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
            }
        }
    }

    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void cancelAiTick(CallbackInfo ci) {
        if (BaritoneAPI.getProvider().isPathing(this)) ci.cancel();
    }
}
