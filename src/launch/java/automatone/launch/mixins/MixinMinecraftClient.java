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

package automatone.launch.mixins;

import automatone.BaritoneProvider;
import automatone.api.IBaritone;
import automatone.api.event.events.TickEvent;
import automatone.utils.BaritoneAutoTest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * @author Brady
 * @since 7/31/2018
 */
@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Shadow
    public ClientPlayerEntity player;
    @Shadow
    public ClientWorld world;


    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void preInit(CallbackInfo ci) {
        BaritoneAutoTest.INSTANCE.onPreInit();
    }
}
