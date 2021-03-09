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

package automatone.utils;

import automatone.api.event.listener.AbstractGameEventListener;
import automatone.api.pathing.goals.Goal;
import automatone.api.pathing.goals.GoalBlock;
import automatone.api.utils.Helper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.*;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.util.math.BlockPos;

/**
 * Responsible for automatically testing Baritone's pathing algorithm by automatically creating a world with a specific
 * seed, setting a specified goal, and only allowing a certain amount of ticks to pass before the pathing test is
 * considered a failure. In order to test locally, docker may be used, or through an IDE: Create a run config which runs
 * in a separate directory from the primary one (./run), and set the enrivonmental variable {@code BARITONE_AUTO_TEST}
 * to {@code true}.
 *
 * @author leijurv, Brady
 */
public class BaritoneAutoTest implements AbstractGameEventListener, Helper {

    public static final BaritoneAutoTest INSTANCE = new BaritoneAutoTest();

    public static final boolean ENABLE_AUTO_TEST = "true".equals(System.getenv("BARITONE_AUTO_TEST"));
    private static final long TEST_SEED = -928872506371745L;
    private static final BlockPos STARTING_POSITION = new BlockPos(0, 65, 0);
    private static final Goal GOAL = new GoalBlock(69, 69, 420);
    private static final int MAX_TICKS = 3300;

    /**
     * Called right after the {@link GameOptions} object is created in the {@link MinecraftClient} instance.
     */
    public void onPreInit() {
        if (!BaritoneAutoTest.ENABLE_AUTO_TEST) {
            return;
        }
        System.out.println("Optimizing Game Settings");

        GameOptions s = MinecraftClient.getInstance().options;
        s.maxFps = 20;
        s.mipmapLevels = 0;
        s.particles = ParticlesMode.MINIMAL;
        s.overrideWidth = 128;
        s.overrideHeight = 128;
        s.heldItemTooltips = false;
        s.entityShadows = false;
        s.chatScale = 0.0F;
        s.ao = AoMode.OFF;
        s.cloudRenderMode = CloudRenderMode.OFF;
        s.graphicsMode = GraphicsMode.FAST;
        s.tutorialStep = TutorialStep.NONE;
        s.hudHidden = true;
        s.fov = 30.0F;
    }

    private BaritoneAutoTest() {
    }
}
