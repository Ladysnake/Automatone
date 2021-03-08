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

import baritone.BaritoneProvider;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.utils.BaritoneAutoTest;
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

    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
                    ordinal = 5,
                    shift = At.Shift.BY,
                    by = -3
            )
    )
    private void runTick(CallbackInfo ci) {
        final Function<TickEvent.Type, TickEvent> tickProvider = TickEvent.createNextProvider();

        IBaritone baritone = BaritoneProvider.INSTANCE.getPrimaryBaritone();

        TickEvent.Type type = baritone.getPlayerContext().entity() != null && baritone.getPlayerContext().world() != null
                ? TickEvent.Type.IN
                : TickEvent.Type.OUT;

        baritone.getGameEventHandler().onTickClient(tickProvider.apply(type));
    }
}
