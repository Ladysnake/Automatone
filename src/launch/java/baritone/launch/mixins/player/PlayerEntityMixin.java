/*
 * Requiem
 * Copyright (C) 2017-2021 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 *
 * Linking this mod statically or dynamically with other
 * modules is making a combined work based on this mod.
 * Thus, the terms and conditions of the GNU General Public License cover the whole combination.
 *
 * In addition, as a special exception, the copyright holders of
 * this mod give you permission to combine this mod
 * with free software programs or libraries that are released under the GNU LGPL
 * and with code included in the standard release of Minecraft under All Rights Reserved (or
 * modified versions of such code, with unchanged license).
 * You may copy and distribute such a system following the terms of the GNU GPL for this mod
 * and the licenses of the other code concerned.
 *
 * Note that people who make modified versions of this mod are not obligated to grant
 * this special exception for their modified versions; it is their choice whether to do so.
 * The GNU General Public License gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version which carries forward this exception.
 */
package baritone.launch.mixins.player;

import baritone.api.fakeplayer.AutomatoneFakePlayer;
import baritone.api.fakeplayer.FakeServerPlayerEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    @Shadow @Final @Mutable
    private GameProfile gameProfile;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "shouldSave", at = @At("HEAD"), cancellable = true)
    private void allowFakePlayerSave(CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof AutomatoneFakePlayer) {
            cir.setReturnValue(super.shouldSave());
        }
    }

    /**
     * @reason minecraft overwrites the uuid when reading data, which breaks anything that tries to find this entity after a reload
     */
    @Inject(method = "readCustomDataFromNbt", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V"))
    private void bringBackUuid(NbtCompound nbt, CallbackInfo ci) {
        if (this instanceof AutomatoneFakePlayer) {
            this.gameProfile = new GameProfile(this.getUuid(), this.gameProfile.getName());
        }
    }

    /**
     * Minecraft cancels knockback for players and instead relies entirely on the client for handling the velocity change.
     * This injection cancels the cancellation for fake players, as they do not have an associated client.
     */
    @ModifyVariable(
        method = "attack",
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/EntityVelocityUpdateS2CPacket;<init>(Lnet/minecraft/entity/Entity;)V"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/sound/SoundEvents;ENTITY_PLAYER_ATTACK_CRIT:Lnet/minecraft/sound/SoundEvent;")
        ),
        at = @At(value = "LOAD")
    )
    private Vec3d cancelKnockbackCancellation(Vec3d previousVelocity, Entity target) {
        if (target instanceof FakeServerPlayerEntity) {
            return target.getVelocity();
        }
        return previousVelocity;
    }
}
