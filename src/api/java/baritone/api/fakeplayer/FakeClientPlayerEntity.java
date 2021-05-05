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
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;

public class FakeClientPlayerEntity extends OtherClientPlayerEntity implements AutomatoneFakePlayer {
    protected PlayerListEntry listEntry;

    public static <P extends PlayerEntity & AutomatoneFakePlayer> P createClientFakePlayer(EntityType<P> type, ClientWorld world, GameProfile profile) {
        return FakePlayers.<ClientWorld, P>getFakeClientPlayerFactory(type).create(type, world, profile);
    }

    public FakeClientPlayerEntity(EntityType<?> type, ClientWorld clientWorld, GameProfile gameProfile) {
        super(clientWorld, gameProfile);
        ((IEntityAccessor)this).automatone$setType(type);
    }

    @Nullable
    @Override
    protected PlayerListEntry getPlayerListEntry() {
        return this.listEntry;
    }

    public void setPlayerListEntry(@Nullable GameProfile profile) {
        this.listEntry = profile == null
            ? null
            : new PlayerListEntry(new PlayerListS2CPacket(). new Entry(profile, 0, null, null));
    }

    @Override
    public Text getName() {
        GameProfile ownerProfile = this.getDisplayProfile();
        if (ownerProfile != null) {
            return new LiteralText(ownerProfile.getName());
        }
        return super.getName();
    }

    @Override
    @Nullable
    public GameProfile getDisplayProfile() {
        return this.getPlayerListEntry() != null ? this.getPlayerListEntry().getProfile() : null;
    }

    @Override
    public void setDisplayProfile(@CheckForNull GameProfile profile) {
        this.setPlayerListEntry(profile);
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.world.playSound(this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), volume, pitch, false);
        }
    }

    @Override
    public void playSound(SoundEvent event, SoundCategory category, float volume, float pitch) {
        this.world.playSound(this.getX(), this.getY(), this.getZ(), event, category, volume, pitch, false);
    }
}
