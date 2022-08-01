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

import baritone.api.IBaritone;
import baritone.api.fakeplayer.AutomatoneFakePlayer;
import baritone.api.fakeplayer.FakeClientPlayerEntity;
import baritone.api.fakeplayer.FakePlayers;
import baritone.api.selection.ISelectionManager;
import baritone.command.defaults.ClickCommand;
import baritone.command.defaults.DefaultCommands;
import baritone.selection.SelectionRenderer;
import baritone.utils.GuiClick;
import baritone.utils.PathRenderer;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@KeepName
public final class AutomatoneClient implements ClientModInitializer {
    public static final Set<Baritone> renderList = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Set<ISelectionManager> selectionRenderList = Collections.newSetFromMap(new WeakHashMap<>());

    public static void onRenderPass(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.currentScreen instanceof GuiClick) {
            ((GuiClick) mc.currentScreen).onRender(context.matrixStack(), context.projectionMatrix());
        }

        for (Baritone baritone : renderList) {
            PathRenderer.render(context, baritone.getClientPathingBehaviour());
        }

        for (ISelectionManager selectionManager : selectionRenderList) {
            SelectionRenderer.renderSelections(selectionManager.getSelections());
        }

        if (!mc.isIntegratedServerRunning()) {
            // FIXME we should really be able to render stuff in multiplayer
            return;
        }

        DefaultCommands.selCommand.renderSelectionBox();
    }

    @Override
    public void onInitializeClient(ModContainer mod) {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(AutomatoneClient::onRenderPass);
        ClientPlayNetworking.registerGlobalReceiver(ClickCommand.OPEN_CLICK_SCREEN, (client, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            client.execute(() -> MinecraftClient.getInstance().setScreen(new GuiClick(uuid)));
        });
        ClientPlayNetworking.registerGlobalReceiver(FakePlayers.SPAWN_PACKET_ID, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            UUID uuid = buf.readUuid();
            EntityType<?> entityTypeId = Registry.ENTITY_TYPE.get(buf.readVarInt());
            String name = buf.readString();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = (float)(buf.readByte() * 360) / 256.0F;
            float pitch = (float)(buf.readByte() * 360) / 256.0F;
            float headYaw = (float)(buf.readByte() * 360) / 256.0F;
            GameProfile profile = readProfile(buf);
            client.execute(() -> spawnPlayer(id, uuid, entityTypeId, name, x, y, z, yaw, pitch, headYaw, profile));
        });
        ClientPlayNetworking.registerGlobalReceiver(FakePlayers.PROFILE_UPDATE_PACKET_ID, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            GameProfile profile = readProfile(buf);
            client.execute(() -> {
                        Entity entity = Objects.requireNonNull(client.world).getEntityById(entityId);
                        if (entity instanceof AutomatoneFakePlayer) {
                            ((AutomatoneFakePlayer) entity).setDisplayProfile(profile);
                        }
                    }
            );
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            //yes, it is normal to remove an IBaritone from a Baritone set
            //noinspection SuspiciousMethodCalls
            renderList.remove(IBaritone.KEY.getNullable(entity));
            selectionRenderList.remove(ISelectionManager.KEY.getNullable(entity));
        });
    }

    private <P extends PlayerEntity & AutomatoneFakePlayer> void spawnPlayer(int id, UUID uuid, EntityType<?> entityTypeId, String name, double x, double y, double z, float yaw, float pitch, float headYaw, GameProfile profile) {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;
        @SuppressWarnings("unchecked") EntityType<P> playerType = (EntityType<P>) entityTypeId;
        P other = FakeClientPlayerEntity.createClientFakePlayer(playerType, world, new GameProfile(uuid, name));
        other.setId(id);
        other.setPosition(x, y, z);
        other.getPacketPositionCodec().method_43494(new Vec3d(x, y, z));
        other.bodyYaw = headYaw;
        other.prevBodyYaw = headYaw;
        other.headYaw = headYaw;
        other.prevHeadYaw = headYaw;
        other.updatePositionAndAngles(x, y, z, yaw, pitch);
        other.setDisplayProfile(profile);
        world.addEntity(id, other);
    }

    @Nullable
    private static GameProfile readProfile(PacketByteBuf buf) {
        boolean hasProfile = buf.readBoolean();
        return hasProfile ? new GameProfile(buf.readUuid(), buf.readString()) : null;
    }
}
