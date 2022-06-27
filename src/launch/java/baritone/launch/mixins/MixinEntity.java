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

import baritone.api.IBaritone;
import baritone.api.utils.IEntityAccessor;
import baritone.behavior.PathingBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityAccessor {
    @Shadow public abstract World getWorld();

    @Override
    @Invoker("getEyeHeight")
    public abstract float automatone$invokeGetEyeHeight(EntityPose pose, EntityDimensions dimensions);
    @Override
    @Accessor("type")
    public abstract void automatone$setType(EntityType<?> type);

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void shutdownPathingOnUnloading(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!getWorld().isClient()) {
            IBaritone.KEY.maybeGet(this).ifPresent(b -> ((PathingBehavior) b.getPathingBehavior()).shutdown());
        }
    }
}
