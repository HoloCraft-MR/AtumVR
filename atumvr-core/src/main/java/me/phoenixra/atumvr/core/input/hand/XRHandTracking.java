package me.phoenixra.atumvr.core.input.hand;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.util.Locale;

import static org.lwjgl.openxr.EXTHandTracking.*;
import static org.lwjgl.openxr.FBHandTrackingAim.*;

/**
 * XR_EXT_hand_tracking (+ XR_FB_hand_tracking_aim) handler (low-level OpenXR stuff)
 * <p>
 *     Creates one hand tracker per hand and locates the joints + aim state
 *     every frame. The input handler feeds the hands into the controller devices
 *     and the Oculus Touch profile while a hand's controller is not active
 * </p>
 * <p>
 *     All methods must be called from the render thread
 * </p>
 */
public class XRHandTracking {
    /** Min interval between pinch / fist log lines per hand */
    private static final long LOG_INTERVAL_MS = 1000L;

    private final XRProvider vrProvider;

    /** Hand trackers created */
    @Getter
    private boolean supported;
    /** XR_FB_hand_tracking_aim available (aim pose + pinch strength) */
    @Getter
    private boolean aimSupported;

    private final XRHand[] hands = {new XRHand(ControllerType.LEFT), new XRHand(ControllerType.RIGHT)};
    private final XrHandTrackerEXT[] trackers = new XrHandTrackerEXT[2];
    private final XrHandJointLocationEXT.Buffer[] jointBuffers = new XrHandJointLocationEXT.Buffer[2];
    private final XrHandJointLocationsEXT[] locations = new XrHandJointLocationsEXT[2];
    private final XrHandTrackingAimStateFB[] aimStates = new XrHandTrackingAimStateFB[2];
    private XrHandJointsLocateInfoEXT locateInfo;

    private final boolean[] loggedTracked = new boolean[2];
    private final boolean[] loggedPinch = new boolean[2];
    private final boolean[] loggedFist = new boolean[2];
    private final long[] lastGestureLogMs = new long[2];
    private final int[] skippedGestureLogs = new int[2];

    public XRHandTracking(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }

    public void init(@NotNull XrSession session, long systemId) {
        var caps = session.getCapabilities();
        aimSupported = caps.XR_FB_hand_tracking_aim;
        if (!caps.XR_EXT_hand_tracking) {
            log("[MR] Hands support: none (runtime lacks XR_EXT_hand_tracking), controllers only");
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var handProps = XrSystemHandTrackingPropertiesEXT.calloc(stack)
                    .type(XR_TYPE_SYSTEM_HAND_TRACKING_PROPERTIES_EXT);
            var sysProps = XrSystemProperties.calloc(stack)
                    .type(XR10.XR_TYPE_SYSTEM_PROPERTIES)
                    .next(handProps.address());
            int result = XR10.xrGetSystemProperties(session.getInstance(), systemId, sysProps);
            if (result < 0 || !handProps.supportsHandTracking()) {
                log("[MR] Hands support: none (system supportsHandTracking=false, "
                        + vrProvider.getXRActionResult(result) + "), controllers only");
                return;
            }
            for (int i = 0; i < 2; i++) {
                PointerBuffer pb = stack.callocPointer(1);
                result = xrCreateHandTrackerEXT(
                        session,
                        XrHandTrackerCreateInfoEXT.calloc(stack)
                                .type(XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT)
                                .hand(i == 0 ? XR_HAND_LEFT_EXT : XR_HAND_RIGHT_EXT)
                                .handJointSet(XR_HAND_JOINT_SET_DEFAULT_EXT),
                        pb
                );
                if (result < 0) {
                    logError("[MR] Hands support: none (xrCreateHandTrackerEXT "
                            + vrProvider.getXRActionResult(result) + ")");
                    destroy();
                    return;
                }
                trackers[i] = new XrHandTrackerEXT(pb.get(0), session);
                jointBuffers[i] = XrHandJointLocationEXT.calloc(XRHand.JOINT_COUNT);
                aimStates[i] = XrHandTrackingAimStateFB.calloc().type(XR_TYPE_HAND_TRACKING_AIM_STATE_FB);
                locations[i] = XrHandJointLocationsEXT.calloc()
                        .type(XR_TYPE_HAND_JOINT_LOCATIONS_EXT)
                        .next(aimSupported ? aimStates[i].address() : 0L)
                        .jointLocations(jointBuffers[i]);
            }
        }
        locateInfo = XrHandJointsLocateInfoEXT.calloc().type(XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT);
        supported = true;
        log("[MR] Hands support: XR_EXT_hand_tracking" + (aimSupported
                ? " + XR_FB_hand_tracking_aim (aim pose + pinch)"
                : " only (no XR_FB_hand_tracking_aim: no aim pose / pinch, hands can't replace controllers)"));
    }

    /**
     * Locate both hands for the current display time.
     * Call after xrSyncActions, before the action sets / devices update
     */
    public void update() {
        if (!supported) {
            return;
        }
        long now = System.currentTimeMillis();
        locateInfo.baseSpace(vrProvider.getSession().getXrAppSpace())
                .time(vrProvider.getXrDisplayTime());
        for (int i = 0; i < 2; i++) {
            XRHand hand = hands[i];
            XrHandJointLocationsEXT loc = locations[i];
            int result = xrLocateHandJointsEXT(trackers[i], locateInfo, loc);
            boolean active = result >= 0 && loc.isActive();

            var buf = jointBuffers[i];
            for (int j = 0; j < XRHand.JOINT_COUNT; j++) {
                var joint = buf.get(j);
                boolean valid = active
                        && (joint.locationFlags() & XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0
                        && (joint.locationFlags() & XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0;
                var pose = joint.pose();
                hand.setJoint(j, valid,
                        pose.position$().x(), pose.position$().y(), pose.position$().z(),
                        pose.orientation().x(), pose.orientation().y(), pose.orientation().z(), pose.orientation().w(),
                        joint.radius());
            }
            hand.tracked = active && hand.isJointValid(XRHand.PALM);

            if (aimSupported && hand.tracked) {
                var aim = aimStates[i];
                hand.aimStatus = aim.status();
                hand.pinchStrengthIndex = aim.pinchStrengthIndex();
                hand.pinchStrengthMiddle = aim.pinchStrengthMiddle();
                var pose = aim.aimPose();
                hand.setAim((aim.status() & XR_HAND_TRACKING_AIM_VALID_BIT_FB) != 0,
                        pose.position$().x(), pose.position$().y(), pose.position$().z(),
                        pose.orientation().x(), pose.orientation().y(), pose.orientation().z(), pose.orientation().w());
            } else {
                hand.aimStatus = 0;
                hand.pinchStrengthIndex = 0f;
                hand.pinchStrengthMiddle = 0f;
                hand.setAim(false, 0, 0, 0, 0, 0, 0, 1);
            }
            hand.updateGestures(now);
            logChanges(i, hand, now);
        }
    }

    private void logChanges(int i, @NotNull XRHand hand, long now) {
        String name = i == 0 ? "left" : "right";
        if (hand.isTracked() != loggedTracked[i]) {
            loggedTracked[i] = hand.isTracked();
            log("[MR] Hands: " + name + " hand tracking " + (hand.isTracked() ? "active" : "lost"));
        }
        boolean pinchChanged = hand.isPinching() != loggedPinch[i];
        boolean fistChanged = hand.isFist() != loggedFist[i];
        if (!pinchChanged && !fistChanged) {
            return;
        }
        loggedPinch[i] = hand.isPinching();
        loggedFist[i] = hand.isFist();
        if (now - lastGestureLogMs[i] < LOG_INTERVAL_MS) {
            skippedGestureLogs[i]++;
            return;
        }
        lastGestureLogMs[i] = now;
        StringBuilder sb = new StringBuilder("[MR] Hands: ").append(name);
        if (pinchChanged) {
            sb.append(hand.isPinching() ? " pinch start" : " pinch end")
                    .append(String.format(Locale.ROOT, " (%.2f)", hand.getPinchStrengthIndex()));
        }
        if (fistChanged) {
            sb.append(hand.isFist() ? " fist start" : " fist end")
                    .append(String.format(Locale.ROOT, " (curl %.2f)", hand.getCurl()));
        }
        if (skippedGestureLogs[i] > 0) {
            sb.append(", ").append(skippedGestureLogs[i]).append(" changes not logged");
            skippedGestureLogs[i] = 0;
        }
        log(sb.toString());
    }

    public @NotNull XRHand getHand(@NotNull ControllerType type) {
        return hands[type.ordinal()];
    }

    public void destroy() {
        supported = false;
        for (int i = 0; i < 2; i++) {
            if (trackers[i] != null) {
                xrDestroyHandTrackerEXT(trackers[i]);
                trackers[i] = null;
            }
            if (locations[i] != null) {
                locations[i].free();
                locations[i] = null;
            }
            if (jointBuffers[i] != null) {
                jointBuffers[i].free();
                jointBuffers[i] = null;
            }
            if (aimStates[i] != null) {
                aimStates[i].free();
                aimStates[i] = null;
            }
            hands[i].tracked = false;
            hands[i].aimValid = false;
            hands[i].updateGestures(0L);
        }
        if (locateInfo != null) {
            locateInfo.free();
            locateInfo = null;
        }
    }

    private void log(String msg) {
        vrProvider.getLogger().logInfo(msg);
    }

    private void logError(String msg) {
        vrProvider.getLogger().logError(msg);
    }
}
