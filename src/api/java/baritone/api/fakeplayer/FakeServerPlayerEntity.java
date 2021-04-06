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
package baritone.api.fakeplayer;

import baritone.api.utils.IEntityAccessor;
import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.util.Objects;
import java.util.UUID;

public class FakeServerPlayerEntity extends ServerPlayerEntity implements AutomatoneFakePlayer {

    protected @Nullable GameProfile displayProfile;
    private boolean release;

    public FakeServerPlayerEntity(EntityType<?> type, ServerWorld world) {
        this(type, world, new GameProfile(UUID.randomUUID(), "FakePlayer"));
        this.stepHeight = 0.6f; // same step height as LivingEntity
    }

    public FakeServerPlayerEntity(EntityType<?> type, ServerWorld world, GameProfile profile) {
        super(world.getServer(), world, profile, new ServerPlayerInteractionManager(world));
        ((IEntityAccessor)this).automatone$setType(type);
        // Side effects go brr
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.CLIENTBOUND), this);
    }

    public void selectHotbarSlot(int hotbarSlot) {
        Preconditions.checkArgument(PlayerInventory.isValidHotbarIndex(hotbarSlot));
        if (this.inventory.selectedSlot != hotbarSlot && this.getActiveHand() == Hand.MAIN_HAND) {
            this.clearActiveItem();
        }

        this.inventory.selectedSlot = hotbarSlot;
        this.updateLastActionTime();
    }

    public void swapHands() {
        ItemStack offhandStack = this.getStackInHand(Hand.OFF_HAND);
        this.setStackInHand(Hand.OFF_HAND, this.getStackInHand(Hand.MAIN_HAND));
        this.setStackInHand(Hand.MAIN_HAND, offhandStack);
        this.clearActiveItem();
    }

    /**
     * Calls {@link #clearActiveItem()} at the end of the tick if nothing re-activated it
     */
    public void releaseActiveItem() {
        this.release = true;
    }

    public void useItem(Hand hand) {
        if (this.release && hand != this.getActiveHand()) {
            this.clearActiveItem();
        }

        if (this.isUsingItem()) return;

        ItemStack stack = this.getStackInHand(hand);

        if (!stack.isEmpty()) {
            ActionResult actionResult = this.interactionManager.interactItem(
                this,
                this.getServerWorld(),
                stack,
                hand
            );

            if (actionResult.shouldSwingHand()) {
                this.swingHand(hand, true);
            }
        }
    }

    @Override
    public void tick() {
        this.closeHandledScreen();
        super.tick();
        this.playerTick();
    }

    @Override
    public void tickMovement() {
        if (this.isTouchingWater() && this.isSneaking() && this.method_29920()) {
            // Mirrors ClientPlayerEntity's sinking behaviour
            this.setVelocity(this.getVelocity().add(0.0D, -0.04, 0.0D));
            // TODO MC 1.17 replace with this.knockDownwards()
        }
        super.tickMovement();
    }

    @Override
    protected void tickNewAi() {
        super.tickNewAi();
        if (this.release) {
            this.clearActiveItem();
            this.release = false;
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        this.attack(target);
        return false;
    }

    @Override
    public void takeKnockback(float f, double d, double e) {
        if (this.velocityModified) {
            super.takeKnockback(f, d, e);
        }
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
        this.handleFall(heightDifference, onGround);
    }

    @Override
    public boolean isSleepingLongEnough() {
        return true;    // Fake players do not delay the sleep of other players
    }

    @Override
    public Text getName() {
        GameProfile displayProfile = this.getDisplayProfile();
        if (displayProfile != null) {
            return new LiteralText(displayProfile.getName());
        }
        return super.getName();
    }

    @Nullable
    public GameProfile getDisplayProfile() {
        return this.displayProfile;
    }

    public void setDisplayProfile(@CheckForNull GameProfile profile) {
        if (!Objects.equals(profile, this.displayProfile)) {
            this.displayProfile = profile;
            this.sendProfileUpdatePacket();
        }
    }

    @Override
    public void readCustomDataFromTag(CompoundTag tag) {
        super.readCustomDataFromTag(tag);
        if (tag.contains("automatone:display_profile", NbtType.COMPOUND)) {
            this.displayProfile = NbtHelper.toGameProfile(tag.getCompound("automatone:display_profile"));
        }
    }

    @Override
    public void writeCustomDataToTag(CompoundTag tag) {
        super.writeCustomDataToTag(tag);
        if (this.displayProfile != null) {
            tag.put("automatone:display_profile", NbtHelper.fromGameProfile(new CompoundTag(), this.displayProfile));
        }
    }

    @Override
    public Packet<?> createSpawnPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        writeToSpawnPacket(buf);
        return new CustomPayloadS2CPacket(FakePlayers.SPAWN_PACKET_ID, buf);
    }

    protected void writeToSpawnPacket(PacketByteBuf buf) {
        buf.writeVarInt(this.getEntityId());
        buf.writeUuid(this.getUuid());
        buf.writeVarInt(Registry.ENTITY_TYPE.getRawId(this.getType()));
        buf.writeString(this.getGameProfile().getName());
        buf.writeDouble(this.getX());
        buf.writeDouble(this.getY());
        buf.writeDouble(this.getZ());
        buf.writeByte((byte)((int)(this.yaw * 256.0F / 360.0F)));
        buf.writeByte((byte)((int)(this.pitch * 256.0F / 360.0F)));
        writeProfile(buf, this.getDisplayProfile());
    }

    public void sendProfileUpdatePacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(this.getEntityId());
        writeProfile(buf, this.getDisplayProfile());

        CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(FakePlayers.PROFILE_UPDATE_PACKET_ID, buf);

        for (ServerPlayerEntity e : PlayerLookup.tracking(this)) {
            e.networkHandler.sendPacket(packet);
        }
    }

    public static void writeProfile(PacketByteBuf buf, @Nullable GameProfile profile) {
        buf.writeBoolean(profile != null);

        if (profile != null) {
            buf.writeUuid(profile.getId());
            buf.writeString(profile.getName());
        }
    }
}
