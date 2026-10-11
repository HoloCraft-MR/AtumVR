package me.phoenixra.atumvr.core;

import lombok.Getter;
import me.phoenixra.atumvr.api.AtumVRState;
import me.phoenixra.atumvr.core.enums.XREvent;
import me.phoenixra.atumvr.core.enums.XRSessionState;
import me.phoenixra.atumvr.core.session.XRInstance;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;


import static org.lwjgl.system.MemoryUtil.NULL;


public class XRState implements AtumVRState {

    @Getter
    private final XRProvider vrProvider;


    /**
     * If VR session is ready (initialized) on VR runtime side
     */
    @Getter
    protected boolean ready = false;


    @Getter
    protected boolean initialized = false;


    @Getter
    protected boolean active = false;


    @Getter
    protected boolean focused = false;

    /**
     * Session is running (between xrBeginSession and xrEndSession).
     * Frames may only be submitted while running
     */
    @Getter
    protected boolean sessionRunning = false;

    /**
     * Session (or instance) is being lost: unrecoverable without recreating it
     */
    @Getter
    protected boolean sessionLost = false;

    @Getter
    protected XRSessionState sessionState = XRSessionState.UNKNOWN;

    /** Number of times the session was (re)started */
    @Getter
    protected int sessionStarts = 0;


    public XRState(@NotNull XRProvider vrProvider){
        this.vrProvider = vrProvider;

    }

    public void init() throws Throwable{

        while (!ready){
            vrProvider.getLogger().logInfo("Waiting for OpenXR session to start...");
            pollVREvents();
        }
        vrProvider.inputHandler.init();
        vrProvider.renderer.init();

        initialized = true;
        vrProvider.inputHandler.refreshCurrentInteractionProfiles();
    }



    protected void pollVREvents() {
        XRInstance vrInstance = vrProvider.getSession().getInstance();
        XrEventDataBuffer eventBuffer = vrInstance.getXrEventBuffer();
        while (true) {
            eventBuffer.clear();
            eventBuffer.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            int error = XR10.xrPollEvent(vrInstance.getHandle(), eventBuffer);
            vrProvider.checkXRError(error, "xrPollEvent", "");
            if (error != XR10.XR_SUCCESS) {
                //no more events available
                break;
            }
            var event = XrEventDataBaseHeader.create(eventBuffer.address());
            if (vrProvider.getSession().getSpatialAnchors().onEvent(event.type(), event.address())) {
                continue;
            }
            var vrEvent = XREvent.fromId(event.type());

            if (vrEvent == XREvent.SESSION_STATE_CHANGED) {
                xrStateChanged(
                        XrEventDataSessionStateChanged.create(event.address())
                );
                continue;
            }

            if (vrEvent == XREvent.INTERACTION_PROFILE_CHANGED) {
                vrProvider.onInteractionProfileChanged();
                continue;
            }

            if (vrEvent == XREvent.VIVE_TRACKER_CONNECTED_HTCX) {
                vrProvider.onViveTrackerConnected(
                        XrEventDataViveTrackerConnectedHTCX.create(event.address())
                );
                continue;
            }

            if (vrEvent == XREvent.INSTANCE_LOSS_PENDING) {
                sessionLost = true;
                sessionRunning = false;
                active = false;
                focused = false;
                vrProvider.getLogger().logError("[MR] Session instance loss pending: OpenXR runtime is going away, restart VR");
                vrProvider.onSessionLost("instance loss pending");
                continue;
            }

            if (vrEvent == XREvent.REFERENCE_SPACE_CHANGE_PENDING) {
                XrEventDataReferenceSpaceChangePending change =
                        XrEventDataReferenceSpaceChangePending.create(event.address());
                vrProvider.onReferenceSpaceChangePending(
                        change.referenceSpaceType(), change.changeTime(), change.poseValid());
                vrProvider.getLogger().logInfo(
                        "Reference space change pending: type="
                                + change.referenceSpaceType()
                                + ", appType="
                                + vrProvider.getSession().getXrAppSpaceType()
                                + ", changeTime="
                                + change.changeTime()
                                + ", poseValid="
                                + change.poseValid()
                );
                continue;
            }

            if (vrEvent == XREvent.DISPLAY_REFRESH_RATE_CHANGED_FB) {
                XrEventDataDisplayRefreshRateChangedFB change =
                        XrEventDataDisplayRefreshRateChangedFB.create(event.address());
                vrProvider.getSession().onDisplayRefreshRateChanged(
                        change.fromDisplayRefreshRate(),
                        change.toDisplayRefreshRate()
                );
                continue;
            }

            // action sets must be attached before the profile can be queried
            if (vrEvent == XREvent.INTERACTION_PROFILE_CHANGED && initialized) {
                vrProvider.inputHandler.refreshCurrentInteractionProfiles();
            }

            vrProvider.onXREventReceived(vrEvent);
        }
        vrProvider.getSession().maintainPreferredDisplayRefreshRate(focused);
    }

    private void xrStateChanged(XrEventDataSessionStateChanged event) {
        var stateChange = XRSessionState.fromId(event.state());
        var previous = sessionState;
        sessionState = stateChange;
        vrProvider.getLogger().logInfo("[MR] Session state " + previous + " -> " + stateChange
                + " (running=" + sessionRunning + ")");

        switch (stateChange) {
            case READY -> {
                if (sessionRunning) {
                    vrProvider.getLogger().logInfo("[MR] Session READY while already running, ignored");
                    break;
                }
                int result;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    XrSessionBeginInfo sessionBeginInfo = XrSessionBeginInfo.calloc(stack)
                            .type(XR10.XR_TYPE_SESSION_BEGIN_INFO)
                            .next(NULL)
                            .primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
                    result = XR10.xrBeginSession(vrProvider.getSession().getHandle(), sessionBeginInfo);
                }
                if (result < 0 && result != XR10.XR_ERROR_SESSION_RUNNING) {
                    // first start: fatal like before; restart: keep polling, the runtime may retry
                    vrProvider.checkXRError(!initialized, result, "xrBeginSession", "XRStateChangeREADY");
                    break;
                }
                sessionStarts++;
                sessionRunning = true;
                ready = true;
                active = true; //for convenience
                vrProvider.getSession().requestPreferredDisplayRefreshRate();
                if (initialized) {
                    vrProvider.getLogger().logInfo("[MR] Session restarted (start #" + sessionStarts
                            + "), resuming frames");
                    try {
                        vrProvider.inputHandler.refreshCurrentInteractionProfiles();
                    } catch (Throwable t) {
                        vrProvider.getLogger().logError("[MR] Session restart: profile refresh failed: " + t);
                    }
                }
                vrProvider.getLogger().logInfo("OpenXR session is READY");
            }

            case STOPPING -> {
                ready = false;
                // headset off / Guardian or tracking-lost interstitial / system UI:
                // end the session (required by the spec) and wait for READY again.
                // Keep 'initialized': swapchains, actions and GL stay valid across End/Begin
                active = false;
                if (sessionRunning) {
                    int result = XR10.xrEndSession(vrProvider.getSession().getHandle());
                    sessionRunning = false;
                    vrProvider.getLogger().logInfo("[MR] Session ended on STOPPING (xrEndSession "
                            + vrProvider.getXRActionResult(result) + "), waiting for READY");
                }
            }

            case LOSS_PENDING -> {
                active = false;
                sessionRunning = false;
                sessionLost = true;
                vrProvider.getLogger().logError("[MR] Session LOSS_PENDING: session lost, restart VR");
                vrProvider.onSessionLost("session loss pending");
            }

            case VISIBLE, FOCUSED -> active = true;

            case EXITING, IDLE, SYNCHRONIZED -> active = false;

            default -> {}
        }

        focused = stateChange == XRSessionState.FOCUSED;

        vrProvider.onStateChanged(stateChange);
    }

    // -------- DESTROY --------

    /**
     * Destroy VR state by setting its fields to initial values
     */
    public void destroy(){
        ready = false;
        active = false;
        focused = false;
        initialized = false;
        sessionRunning = false;
    }
}
