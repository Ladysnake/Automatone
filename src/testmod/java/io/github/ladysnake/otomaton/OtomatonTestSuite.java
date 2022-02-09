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

import baritone.api.fakeplayer.FakeServerPlayerEntity;
import io.github.ladysnake.elmendorf.GameTestUtil;
import io.github.ladysnake.otomaton.mixin.ServerWorldAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class OtomatonTestSuite implements FabricGameTest {
    @BeforeBatch(batchId = "sleepingBatch")
    public void beforeSleepingTests(ServerWorld world) {
        world.setTimeOfDay(20000);
        world.calculateAmbientDarkness();
    }

    @GameTest(structureName = EMPTY_STRUCTURE, batchId = "sleepingBatch")
    public void shellsDoNotPreventSleeping(TestContext ctx) {
        ServerPlayerEntity player = ctx.spawnServerPlayer(1, 0, 1);
        ServerPlayerEntity fakePlayer = new FakeServerPlayerEntity(Otomaton.FAKE_PLAYER, ctx.getWorld());
        fakePlayer.copyPositionAndRotation(player);
        ctx.getWorld().spawnEntity(fakePlayer);
        ItemStack bed = Items.RED_BED.getDefaultStack();
        fakePlayer.setStackInHand(Hand.MAIN_HAND, bed);
        BlockPos bedPos = new BlockPos(1, 0, 2);
        fakePlayer.interactionManager.interactBlock(
                fakePlayer,
                fakePlayer.world,
                bed,
                Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(0.5, 0.5, 0.5), Direction.UP, ctx.getAbsolutePos(bedPos), false)
        );
        ctx.expectBlock(Blocks.RED_BED, bedPos);
        player.interactionManager.interactBlock(
                player, player.world, ItemStack.EMPTY, Hand.OFF_HAND,
                new BlockHitResult(new Vec3d(0.5, 0.5, 0.5), Direction.UP, ctx.getAbsolutePos(bedPos), false)
        );
        GameTestUtil.assertTrue("player should be sleeping", player.isSleeping());
        GameTestUtil.assertTrue("all players should be sleeping", ((ServerWorldAccessor) player.world).requiem$getSleepManager().canResetTime(100, List.of(fakePlayer, player)));
        ctx.complete();
    }
}