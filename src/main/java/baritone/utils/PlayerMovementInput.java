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

package baritone.utils;

import baritone.api.utils.input.Input;

public class PlayerMovementInput extends net.minecraft.client.input.Input {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick(boolean p_225607_1_) {
        this.movementSideways = 0.0F;
        this.movementForward = 0.0F;

        this.jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

        if (this.pressingForward = handler.isInputForcedDown(Input.MOVE_FORWARD)) {
            this.movementForward++;
        }

        if (this.pressingBack = handler.isInputForcedDown(Input.MOVE_BACK)) {
            this.movementForward--;
        }

        if (this.pressingLeft = handler.isInputForcedDown(Input.MOVE_LEFT)) {
            this.movementSideways++;
        }

        if (this.pressingRight = handler.isInputForcedDown(Input.MOVE_RIGHT)) {
            this.movementSideways--;
        }

        if (this.sneaking = handler.isInputForcedDown(Input.SNEAK)) {
            this.movementSideways *= 0.3D;
            this.movementForward *= 0.3D;
        }
    }
}
