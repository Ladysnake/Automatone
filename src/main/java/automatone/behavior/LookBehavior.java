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

package automatone.behavior;

import automatone.Baritone;
import automatone.api.Settings;
import automatone.api.behavior.ILookBehavior;
import automatone.api.event.events.PlayerUpdateEvent;
import automatone.api.utils.Rotation;

public final class LookBehavior extends Behavior implements ILookBehavior {

    /**
     * Target's values are as follows:
     */
    private Rotation target;

    /**
     * Whether or not rotations are currently being forced
     */
    private boolean force;

    /**
     * The last player yaw angle. Used when free looking
     *
     * @see Settings#freeLook
     */
    private float lastYaw;

    public LookBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void updateTarget(Rotation target, boolean force) {
        this.target = target;
        if (!force) {
            double rand = Math.random() - 0.5;
            if (Math.abs(rand) < 0.1) {
                rand *= 4;
            }
            this.target = new Rotation(this.target.getYaw() + (float) (rand * Baritone.settings().randomLooking113.value), this.target.getPitch());
        }
        this.force = force || !Baritone.settings().freeLook.value;
    }

    @Override
    public void onTickServer() {
        if (this.target == null) {
            return;
        }

        if (this.force) {
            ctx.entity().yaw = this.target.getYaw();
            float oldPitch = ctx.entity().pitch;
            float desiredPitch = this.target.getPitch();
            ctx.entity().pitch = desiredPitch;
            ctx.entity().yaw += (Math.random() - 0.5) * Baritone.settings().randomLooking.value;
            ctx.entity().pitch += (Math.random() - 0.5) * Baritone.settings().randomLooking.value;
            if (desiredPitch == oldPitch && !Baritone.settings().freeLook.value) {
                nudgeToLevel();
            }
            this.target = null;
        }
    }

    public void pig() {
        if (this.target != null) {
            ctx.entity().yaw = this.target.getYaw();
        }
    }

    /**
     * Nudges the player's pitch to a regular level. (Between {@code -20} and {@code 10}, increments are by {@code 1})
     */
    private void nudgeToLevel() {
        if (ctx.entity().pitch < -20) {
            ctx.entity().pitch++;
        } else if (ctx.entity().pitch > 10) {
            ctx.entity().pitch--;
        }
    }
}
