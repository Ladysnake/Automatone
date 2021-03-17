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

package baritone;

import baritone.entity.fakeplayer.FakeServerPlayerEntity;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

public final class AutomatoneNetworking {
    public static final Identifier FAKE_PLAYER_SPAWN = Automatone.id("fake_player_spawn");
    public static final Identifier PLAYER_PROFILE_SET = Automatone.id("fake_player_profile");
    public static final Identifier OPEN_CLICK_SCREEN = Automatone.id("open_click_screen");

    public static CustomPayloadS2CPacket createFakePlayerSpawnPacket(FakeServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(player.getEntityId());
        buf.writeUuid(player.getUuid());
        buf.writeVarInt(Registry.ENTITY_TYPE.getRawId(player.getType()));
        buf.writeString(player.getGameProfile().getName());
        buf.writeDouble(player.getX());
        buf.writeDouble(player.getY());
        buf.writeDouble(player.getZ());
        buf.writeByte((byte)((int)(player.yaw * 256.0F / 360.0F)));
        buf.writeByte((byte)((int)(player.pitch * 256.0F / 360.0F)));
        writeProfile(buf, player.getOwnerProfile());
        return new CustomPayloadS2CPacket(FAKE_PLAYER_SPAWN, buf);
    }

    public static void sendFakePlayerUpdatePacket(FakeServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(player.getEntityId());
        writeProfile(buf, player.getOwnerProfile());

        CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(PLAYER_PROFILE_SET, buf);

        for (ServerPlayerEntity e : PlayerLookup.tracking(player)) {
            e.networkHandler.sendPacket(packet);
        }
    }

    private static void writeProfile(PacketByteBuf buf, @Nullable GameProfile profile) {
        buf.writeBoolean(profile != null);

        if (profile != null) {
            buf.writeUuid(profile.getId());
            buf.writeString(profile.getName());
        }
    }
}
