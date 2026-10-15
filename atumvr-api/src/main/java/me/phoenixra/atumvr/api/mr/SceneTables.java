package me.phoenixra.atumvr.api.mr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Real tables from the headset's scene model (Meta Space Setup: planes labelled TABLE / DESK).
 * <p>
 *     The app asks for a scan with {@link #requestScan()}; the provider queries the scene on the
 *     render thread and publishes one {@link Result}. Poses are in the app reference space (room), meters.
 * </p>
 */
public class SceneTables {

    /**
     * One table top
     *
     * @param x center, room space
     * @param y table top height, room space
     * @param z center, room space
     * @param axisX horizontal unit direction of the {@code width} side (x)
     * @param axisZ horizontal unit direction of the {@code width} side (z)
     * @param width side along (axisX, axisZ), meters
     * @param depth other side, meters
     * @param labels semantic labels as reported (e.g. "TABLE")
     */
    public record Table(float x, float y, float z, float axisX, float axisZ,
                        float width, float depth, @NotNull String labels) {
    }

    public enum Status {
        DONE,
        /** Runtime lacks XR_FB_scene / spatial entity query */
        UNSUPPORTED,
        FAILED
    }

    /**
     * @param tables found tables (empty: none, e.g. no Space Setup or USE_SCENE not granted)
     * @param message detail for logs / chat
     */
    public record Result(@NotNull Status status, @NotNull List<Table> tables, @NotNull String message) {
    }

    private volatile boolean requested;
    private volatile Result result;

    /** Ask for a scan (app side); the result arrives through {@link #pollResult()} */
    public void requestScan() {
        requested = true;
    }

    /** Provider side: take the pending scan request */
    public boolean pollRequest() {
        boolean r = requested;
        requested = false;
        return r;
    }

    /** Provider side */
    public void publish(@NotNull Result result) {
        this.result = result;
    }

    /** App side: take the latest result, if any */
    public @Nullable Result pollResult() {
        Result r = result;
        result = null;
        return r;
    }
}
