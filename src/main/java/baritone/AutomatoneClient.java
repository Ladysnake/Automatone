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
import baritone.api.event.events.RenderEvent;
import baritone.api.selection.ISelectionManager;
import baritone.command.defaults.DefaultCommands;
import baritone.entity.fakeplayer.AutomatoneFakePlayer;
import baritone.entity.fakeplayer.FakeClientPlayerEntity;
import baritone.selection.SelectionRenderer;
import baritone.utils.GuiClick;
import baritone.utils.PathRenderer;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AutomatoneClient implements ClientModInitializer {
    public static final Set<Baritone> renderList = Collections.newSetFromMap(new WeakHashMap<>());

    public static void onRenderPass(RenderEvent renderEvent) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.currentScreen instanceof GuiClick) {
            ((GuiClick) mc.currentScreen).onRender(renderEvent.getModelViewStack(), renderEvent.getProjectionMatrix());
        }

        for (Baritone baritone : renderList) {
            PathRenderer.render(renderEvent, baritone.getClientPathingBehaviour());
        }

        SelectionRenderer.renderSelections(renderEvent.getModelViewStack(), ISelectionManager.KEY.get(mc.world).getSelections());

        if (!mc.isIntegratedServerRunning()) {
            // FIXME we should really be able to render stuff in multiplayer
            return;
        }

        DefaultCommands.selCommand.renderSelectionBox(renderEvent);
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.INSTANCE.register(Automatone.FAKE_PLAYER, (r, it) -> new PlayerEntityRenderer(r));
        ClientPlayNetworking.registerGlobalReceiver(AutomatoneNetworking.OPEN_CLICK_SCREEN, (client, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            client.execute(() -> MinecraftClient.getInstance().openScreen(new GuiClick(uuid)));
        });
        ClientPlayNetworking.registerGlobalReceiver(AutomatoneNetworking.FAKE_PLAYER_SPAWN, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            UUID uuid = buf.readUuid();
            EntityType<?> entityTypeId = Registry.ENTITY_TYPE.get(buf.readVarInt());
            String name = buf.readString();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = (float)(buf.readByte() * 360) / 256.0F;
            float pitch = (float)(buf.readByte() * 360) / 256.0F;
            GameProfile profile = readProfile(buf);
            client.execute(() -> {
                ClientWorld world = MinecraftClient.getInstance().world;
                assert world != null;
                // TODO allow mods to provide fake client player factories
                FakeClientPlayerEntity other = new FakeClientPlayerEntity(entityTypeId, world, new GameProfile(uuid, name));
                other.setEntityId(id);
                other.resetPosition(x, y, z);
                other.updateTrackedPosition(x, y, z);
                other.updatePositionAndAngles(x, y, z, yaw, pitch);
                other.setOwnerProfile(profile);
                world.addEntity(id, other);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AutomatoneNetworking.PLAYER_PROFILE_SET, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            GameProfile profile = readProfile(buf);
            client.execute(() -> {
                        Entity entity = Objects.requireNonNull(client.world).getEntityById(entityId);
                        if (entity instanceof AutomatoneFakePlayer) {
                            ((AutomatoneFakePlayer) entity).setOwnerProfile(profile);
                        }
                    }
            );
        });
        //yes, it is normal to remove an IBaritone from a Baritone set
        //noinspection SuspiciousMethodCalls
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> renderList.remove(IBaritone.KEY.getNullable(entity)));
    }

    @Nullable
    private static GameProfile readProfile(PacketByteBuf buf) {
        boolean hasProfile = buf.readBoolean();
        return hasProfile ? new GameProfile(buf.readUuid(), buf.readString()) : null;
    }
}
