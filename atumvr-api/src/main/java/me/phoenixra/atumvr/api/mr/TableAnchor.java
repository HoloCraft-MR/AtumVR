package me.phoenixra.atumvr.api.mr;

import me.phoenixra.atumvr.api.AtumVRLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Holds the manually placed {@link TablePose} and persists it
 * to a properties file in a configurable directory.
 * <p>
 *     Manual placement only: no scene API or spatial anchors are used,
 *     so the pose is only valid as long as the runtime keeps the same
 *     reference space origin (e.g. no guardian/boundary reset).
 * </p>
 */
public class TableAnchor {
    public static final String FILE_NAME = "table_pose.properties";

    private final AtumVRLogger logger;

    private volatile TablePose pose;
    private Path directory;

    public TableAnchor(@NotNull AtumVRLogger logger) {
        this.logger = logger;
    }

    /**
     * Set directory to store the table pose in, and load the pose from it if present
     *
     * @param directory the directory
     */
    public synchronized void setStorageDirectory(@NotNull Path directory) {
        this.directory = directory;
        load();
    }

    public @Nullable TablePose getPose() {
        return pose;
    }

    public boolean hasPose() {
        return pose != null;
    }

    /**
     * Set the table pose and persist it, if storage directory is configured
     *
     * @param pose the new pose or null to clear it
     */
    public synchronized void setPose(@Nullable TablePose pose) {
        this.pose = pose;
        save();
    }

    private void load() {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            pose = null;
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            pose = new TablePose(
                    Float.parseFloat(props.getProperty("x")),
                    Float.parseFloat(props.getProperty("y")),
                    Float.parseFloat(props.getProperty("z")),
                    Float.parseFloat(props.getProperty("yaw"))
            );
            logger.logInfo("Loaded table pose: " + pose);
        } catch (IOException | RuntimeException e) {
            pose = null;
            logger.logError("Failed to load table pose from " + file + ": " + e);
        }
    }

    private void save() {
        if (directory == null) {
            return;
        }
        Path file = directory.resolve(FILE_NAME);
        try {
            if (pose == null) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(directory);
            Properties props = new Properties();
            props.setProperty("x", Float.toString(pose.x()));
            props.setProperty("y", Float.toString(pose.y()));
            props.setProperty("z", Float.toString(pose.z()));
            props.setProperty("yaw", Float.toString(pose.yaw()));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "AtumVR mixed reality table pose (app reference space, meters/radians)");
            }
        } catch (IOException e) {
            logger.logError("Failed to save table pose to " + file + ": " + e);
        }
    }
}
