package me.phoenixra.atumvr.api;


import me.phoenixra.atumvr.api.input.AtumVRInputHandler;
import me.phoenixra.atumvr.api.mr.TableAnchor;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderContext;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * The entry point and manager of VR
 */
public interface AtumVRProvider {

    /**
     * Initialize VR session
     *
     * @throws Throwable the error or exception received during initialization
     */
    void initializeVR() throws Throwable;


    /**
     * Sync VR State with VR runtime.
     * <p>
     *     Has to be called before {@link #startFrame}
     * </p>
     */
    void syncState();


    /**
     * Start the frame
     * <p>
     *     Waits for VR runtime next frame to become available,
     *     then prepares the VR frame to render and retrieves relevant VR data
     * </p>
     */
    void startFrame();


    /**
     * Render the frame and let VR runtime know
     * it is ready to be displayed on HMD
     *
     * @param context the render context
     */
    void render(@NotNull AtumVRRenderContext context);


    /**
     * Post render.
     * <p>
     *     Called after {@link #render(AtumVRRenderContext)}<br>
     *     Do nothing by default, intended to be overwritten when needed
     * </p>
     */
    void postRender();


    /**
     * Run one frame loop cycle without rendering,
     * submitting an empty frame to the VR runtime.
     * <p>
     *     Has to be called every loop iteration while the session
     *     is running but the app is not rendering VR
     *     (instead of {@link #startFrame} and {@link #render}).
     *     Keeps the session synchronized with the runtime,
     *     so it can be promoted back to visible/focused later.
     * </p>
     * <p>
     *     Does nothing if the session is not running
     * </p>
     */
    void idleFrame();


    /**
     * Prepares destroy, by stopping any active things that may be incorrectly destroyed if done late.
     * Usually, this method can be ignored, but
     * if you noticed for example haptic pulse incorrectly working during app shutdown,
     * then use this method at early app shutdown process
     */
    void prepareDestroy();

    /**
     * Destroy VR session and release all associated resources
     */
    void destroy();


    /**
     * Get VR state
     *
     * @return the VR state
     */
    @NotNull
    AtumVRState getState();

    /**
     * Get VR session
     *
     * @return the VR session
     */
    @NotNull
    AtumVRSession getSession();

    /**
     * Get VR Input Handler
     *
     * @return the VR state
     */
    @NotNull
    AtumVRInputHandler getInputHandler();

    /**
     * Get VR Renderer
     *
     * @return the VR state
     */
    @NotNull
    AtumVRRenderer getRenderer();


    /**
     * The name of the VR application
     *
     * @return the app name
     */
    @NotNull
    String getAppName();

    /**
     * Get VR logger
     *
     * @return the VRLogger
     */
    @NotNull
    AtumVRLogger getLogger();


    // -------- MIXED REALITY --------

    /**
     * If the runtime supports passthrough (XR_FB_passthrough)
     * <p>
     *     Only valid after {@link #initializeVR()}
     * </p>
     *
     * @return true if passthrough can be enabled
     */
    default boolean isPassthroughSupported() {
        return false;
    }

    /**
     * Request passthrough to be enabled/disabled.
     * <p>
     *     Applied on the next submitted frame.
     *     When enabled, the eye textures alpha channel is used to blend
     *     the app over the passthrough camera feed (alpha 0 = see-through).
     *     Ignored if passthrough is not supported.
     * </p>
     *
     * @param enabled true to enable
     */
    default void setPassthroughEnabled(boolean enabled) {
    }

    /**
     * If passthrough was requested and is supported
     *
     * @return true/false
     */
    default boolean isPassthroughEnabled() {
        return false;
    }

    /**
     * Get the mixed reality table anchor
     *
     * @return the table anchor, or null if not supported by this provider
     */
    @Nullable
    default TableAnchor getTableAnchor() {
        return null;
    }

    /**
     * Real tables from the headset's scene model
     *
     * @return the scene tables, or null if not supported by this provider
     */
    @Nullable
    default me.phoenixra.atumvr.api.mr.SceneTables getSceneTables() {
        return null;
    }

}
