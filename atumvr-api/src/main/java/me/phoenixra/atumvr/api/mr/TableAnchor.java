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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Holds the manually placed {@link TablePose} and persists it
 * to a properties file in a configurable directory.
 * <p>
 *     If the provider supports spatial anchors (Meta XR_FB_spatial_entity),
 *     each placement is also turned into a persisted world-locked anchor:
 *     the provider polls {@link #pollPlaceRequest()}, reports the located
 *     anchor pose every frame via {@link #setAnchoredPose(TablePose)} and the
 *     saved anchor UUID via {@link #onAnchorSaved(UUID)}. The app stores that
 *     UUID and hands it back with {@link #setSavedAnchorUuid(UUID)} on start,
 *     so the anchor is loaded again.
 * </p>
 * <p>
 *     Without anchors (or while the anchor is not located) the placed pose is used,
 *     which is only valid as long as the runtime keeps the same
 *     reference space origin (e.g. no guardian/boundary reset).
 * </p>
 */
public class TableAnchor {
    public static final String FILE_NAME = "table_pose.properties";

    private final AtumVRLogger logger;

    private volatile TablePose pose;
    private Path directory;

    /** Pose of the located spatial anchor this frame, null if none / not located */
    private volatile TablePose anchoredPose;
    /** Placement waiting for the provider to create a spatial anchor */
    private TablePose placeRequest;
    private volatile UUID savedAnchorUuid;
    private Consumer<UUID> anchorSavedListener;

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

    /**
     * @return the located spatial anchor pose if available, else the placed pose
     */
    public @Nullable TablePose getPose() {
        TablePose anchored = anchoredPose;
        return anchored != null ? anchored : pose;
    }

    public boolean hasPose() {
        return getPose() != null;
    }

    /**
     * Set the table pose and persist it, if storage directory is configured.
     * A non-null pose is also requested as a new spatial anchor
     *
     * @param pose the new pose or null to clear it
     */
    public synchronized void setPose(@Nullable TablePose pose) {
        this.pose = pose;
        this.anchoredPose = null;
        this.placeRequest = pose;
        save();
    }

    // -------- SPATIAL ANCHOR (provider side) --------

    /**
     * Take the pending placement to create a spatial anchor for
     *
     * @return the pose or null if none
     */
    public synchronized @Nullable TablePose pollPlaceRequest() {
        TablePose request = placeRequest;
        placeRequest = null;
        return request;
    }

    /**
     * Set the located anchor pose for this frame
     *
     * @param pose the pose in the app reference space, or null if not located
     */
    public void setAnchoredPose(@Nullable TablePose pose) {
        this.anchoredPose = pose;
    }

    /**
     * Saved anchor changed: a new anchor was saved, or null if the placement
     * could not be saved (then the placed pose is used on next start)
     *
     * @param uuid the saved anchor UUID or null
     */
    public void onAnchorSaved(@Nullable UUID uuid) {
        savedAnchorUuid = uuid;
        Consumer<UUID> listener = anchorSavedListener;
        if (listener != null) {
            listener.accept(uuid);
        }
    }

    // -------- SPATIAL ANCHOR (app side) --------

    /**
     * @return UUID of the persisted spatial anchor to load, or null
     */
    public @Nullable UUID getSavedAnchorUuid() {
        return savedAnchorUuid;
    }

    /**
     * Set UUID of the persisted spatial anchor, loaded by the provider when its session starts
     *
     * @param uuid the UUID or null
     */
    public void setSavedAnchorUuid(@Nullable UUID uuid) {
        this.savedAnchorUuid = uuid;
    }

    /**
     * Called with the new saved anchor UUID (or null), so the app can store it
     *
     * @param listener the listener, called on the render thread
     */
    public void setAnchorSavedListener(@Nullable Consumer<UUID> listener) {
        this.anchorSavedListener = listener;
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
