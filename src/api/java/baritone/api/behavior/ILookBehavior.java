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

package baritone.api.behavior;

import baritone.api.Settings;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.Rotation;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface ILookBehavior extends IBehavior {

    /**
     * Updates the current {@link ILookBehavior} target to target
     * the specified rotations on the next tick. If force is {@code true},
     * then freeLook will be overriden and angles will be set regardless.
     * If any sort of block interaction is required, force should be {@code true},
     * otherwise, it should be {@code false};
     *
     * <p>This method should be called exactly once a tick while a process is ongoing.
     * If it is not called in a tick, the entity's rotations will not be touched even
     * if they do not match the previous target anymore. If it is called more than once in a tick,
     * only the last call will be taken into account.
     *
     * @param rotation The target rotations
     * @param force    Whether or not to "force" the rotations
     */
    void updateTarget(Rotation rotation, boolean force);

    /**
     * Updates the current {@link ILookBehavior} secondary target to target
     * the specified rotations on the next tick.
     *
     * <p>This target will be handled differently based on whether {@link #updateTarget(Rotation, boolean)}
     * has been called in the same tick:
     * <ul>
     *     <li>If no primary target has been set, the secondary target will be treated as a primary target.</li>
     *     <li>If a primary target has been set with {@code force = false}, the entity's rotation will
     *     be updated to match the <em>secondary</em> target, but {@linkplain IInputOverrideHandler inputs}
     *     will be updated such that the direction of the movement is the same as if the primary target had been used.</li>
     *     <li>If a primary target has been set with {@code force = true}, the secondary target is discarded.</li>
     * </ul>
     *
     * <p><strong>The secondary target will be ignored if sprinting has been requested for this tick.</strong>
     * This is because we can only sprint forward, and arbitrarily cancelling sprints would mess with cost estimates,
     * parkour moves, and other processes. If you want to ensure more reliable secondary target following, set
     * {@link Settings#allowSprint} to {@code false}.
     *
     * <p>This method should be called at most once a tick.
     * If it is not called in a tick, this {@link ILookBehavior} will use only the primary target when
     * updating rotations. If it is called more than once in a tick, only the last call will be taken into account.
     *
     * @param target the secondary target rotations
     * @apiNote this method can be used to look in the direction of an entity while moving, e.g. to simulate aiming
     * or to block incoming attacks.
     */
    void updateSecondaryTarget(Rotation target);
}
