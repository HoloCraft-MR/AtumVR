package me.phoenixra.atumvr.api.mr;

import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pose of a real-world table surface, used to anchor mixed reality content.
 * <p>
 *     Position is in meters, in the application reference space
 *     (LOCAL_FLOOR or STAGE). Only yaw is stored: the table is assumed
 *     to be level.
 * </p>
 *
 * @param x   position x in meters
 * @param y   position y (height of the table surface) in meters
 * @param z   position z in meters
 * @param yaw rotation around +Y in radians
 */
public record TablePose(float x, float y, float z, float yaw) {

    public @NotNull Vector3f position() {
        return new Vector3f(x, y, z);
    }

    public @NotNull Quaternionf orientation() {
        return new Quaternionf().rotationY(yaw);
    }
}
