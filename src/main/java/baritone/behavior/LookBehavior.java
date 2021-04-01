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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.ILookBehavior;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.utils.InputOverrideHandler;
import com.google.common.base.Preconditions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public final class LookBehavior extends Behavior implements ILookBehavior {

    /**
     * Target's values are as follows:
     */
    private Rotation target;
    private Rotation secondaryTarget;

    /**
     * Whether or not rotations are currently being forced
     */
    private boolean force;

    public LookBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void updateSecondaryTarget(Rotation target) {
        this.secondaryTarget = target;
    }

    @Override
    public void updateTarget(Rotation target, boolean force) {
        this.target = target;
        if (!force) {
            double rand = Math.random() - 0.5;
            if (Math.abs(rand) < 0.1) {
                rand *= 4;
            }
            this.target = new Rotation(this.target.getYaw() + (float) (rand * baritone.settings().randomLooking113.get()), this.target.getPitch());
        }
        this.force = force;
    }

    @Override
    public void onTickServer() {
        if (this.target != null || this.secondaryTarget != null) {
            this.updateLook(this.target, this.force, this.secondaryTarget);
        }

        this.target = null;
        this.secondaryTarget = null;
        this.force = false;
    }

    @Contract("null, true, _ -> fail; null, _, null -> fail")
    private void updateLook(@Nullable Rotation primaryTarget, boolean forcePrimary, @Nullable Rotation secondaryTarget) {
        Preconditions.checkArgument(primaryTarget != null || !forcePrimary);
        Preconditions.checkArgument(primaryTarget != null || secondaryTarget != null);

        Rotation actualTarget;

        if (!forcePrimary && !this.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT)) {
            // If we are sprinting, we really need to look in the right direction, otherwise we will
            // mess up the current path and possibly a bunch of other things
            actualTarget = getActualTarget(primaryTarget, secondaryTarget);
            if (actualTarget == null) return;
        } else {
            actualTarget = primaryTarget;
        }

        assert actualTarget != null;
        LivingEntity entity = this.ctx.entity();
        double lookScrambleFactor = baritone.settings().randomLooking.get();
        updateLook(entity, actualTarget, lookScrambleFactor, !baritone.settings().freeLook.get());
    }

    private static void updateLook(LivingEntity entity, Rotation target, double lookScrambleFactor, boolean nudgePitch) {
        entity.yaw = target.getYaw();
        float oldPitch = entity.pitch;
        float desiredPitch = target.getPitch();
        entity.pitch = desiredPitch;
        entity.yaw += (Math.random() - 0.5) * lookScrambleFactor;
        entity.pitch += (Math.random() - 0.5) * lookScrambleFactor;
        if (desiredPitch == oldPitch && nudgePitch) {
            nudgeToLevel(entity);
        }
    }

    @Nullable
    private Rotation getActualTarget(@Nullable Rotation primaryTarget, @Nullable Rotation secondaryTarget) {
        if (baritone.settings().freeLook.get()) {
            // free look is enabled, do not touch the rotations but make sure we move correctly
            updateControlsToMatch(this.baritone.getInputOverrideHandler(), primaryTarget, this.ctx.entity().yaw);
            return null;
        } else if (secondaryTarget != null) {
            // we have a secondary target, use it to set the rotations but still make sure we move correctly
            updateControlsToMatch(this.baritone.getInputOverrideHandler(), primaryTarget, secondaryTarget.getYaw());
            return secondaryTarget;
        }
        // We could look elsewhere, but we have nowhere more important to look at than where we are going
        return primaryTarget;
    }

    public void pig() {
        if (this.target != null) {
            this.ctx.entity().yaw = this.target.getYaw();
        }
    }

    private static void updateControlsToMatch(InputOverrideHandler inputs, Rotation target, float actualYaw) {
        if (target == null) return;
        // TODO handle other directional movement keys being pressed
        // no hurry though, no process uses them currently
        if (!inputs.isInputForcedDown(Input.MOVE_FORWARD)) return;

        float desiredYaw = MathHelper.wrapDegrees(target.getYaw());
        float yawDifference = MathHelper.subtractAngles(actualYaw, desiredYaw);
        // +/-0 -> looking where we should
        // -90 -> looking to the right of where we should -> need to move to the left
        // +90 -> looking to the left of where we should -> need to move to the right
        // +/-180 -> looking backwards
        float absoluteDifference = Math.abs(yawDifference);

        if (absoluteDifference >= 89) {
            // not going forward at all
            inputs.setInputForceState(Input.MOVE_FORWARD, false);

            if (absoluteDifference >= 91) {
                // actually going backwards
                inputs.setInputForceState(Input.MOVE_BACK, true);
            }
        }

        if (absoluteDifference >= 1 && absoluteDifference <= 179) {
            // going diagonal or sideways
            if (yawDifference > 0) {
                inputs.setInputForceState(Input.MOVE_RIGHT, true);
            } else {
                inputs.setInputForceState(Input.MOVE_LEFT, true);
            }
        }
    }

    /**
     * Nudges the player's pitch to a regular level. (Between {@code -20} and {@code 10}, increments are by {@code 1})
     */
    private static void nudgeToLevel(LivingEntity entity) {
        if (entity.pitch < -20) {
            entity.pitch++;
        } else if (entity.pitch > 10) {
            entity.pitch--;
        }
    }
}
