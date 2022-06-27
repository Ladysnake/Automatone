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

package io.github.ladysnake.otomaton;

import baritone.api.fakeplayer.FakeClientPlayerEntity;
import baritone.api.fakeplayer.FakePlayers;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;

public class OtomatonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient(ModContainer mod) {
        FakePlayers.registerClientFactory(Otomaton.FAKE_PLAYER, FakeClientPlayerEntity::new);
        // shh, it's fine
        @SuppressWarnings("unchecked") EntityType<? extends AbstractClientPlayerEntity> fakePlayerType = (EntityType<? extends AbstractClientPlayerEntity>) (EntityType<? extends PlayerEntity>) Otomaton.FAKE_PLAYER;
        EntityRendererRegistry.register(fakePlayerType, (ctx) -> new PlayerEntityRenderer(ctx, false));
    }
}
