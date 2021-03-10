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

package automatone.api.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class RayTraceUtils {

    private RayTraceUtils() {}

    /**
     * Performs a block raytrace with the specified rotations. This should only be used when
     * any entity collisions can be ignored, because this method will not recognize if an
     * entity is in the way or not. The local player's block reach distance will be used.
     *
     * @param entity             The entity representing the raytrace source
     * @param rotation           The rotation to raytrace towards
     * @param blockReachDistance The block reach distance of the entity
     * @return The calculated raytrace result
     */
    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance) {
        return rayTraceTowards(entity, rotation, blockReachDistance, false);
    }

    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance, boolean wouldSneak) {
        Vec3d start;
        if (wouldSneak) {
            start = inferSneakingEyePosition(entity);
        } else {
            start = entity.getCameraPosVec(1.0F); // do whatever is correct
        }
        Vec3d direction = RotationUtils.calcVector3dFromRotation(rotation);
        Vec3d end = start.add(
                direction.x * blockReachDistance,
                direction.y * blockReachDistance,
                direction.z * blockReachDistance
        );
        return entity.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
    }

    public static Vec3d inferSneakingEyePosition(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY() + ((IEntity) entity).getEyeHeight(EntityPose.CROUCHING, entity.getDimensions(EntityPose.CROUCHING)), entity.getZ());
    }
}
