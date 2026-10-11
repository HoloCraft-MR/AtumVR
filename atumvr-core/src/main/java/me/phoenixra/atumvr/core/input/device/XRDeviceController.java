package me.phoenixra.atumvr.core.input.device;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.body.AtumVRBodyView;
import me.phoenixra.atumvr.api.input.body.AtumVRBodyJoint;
import me.phoenixra.atumvr.api.input.device.AtumVRDeviceController;
import me.phoenixra.atumvr.api.misc.pose.AtumVRPose;
import me.phoenixra.atumvr.api.misc.pose.AtumVRPoseMutable;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.input.action.types.HapticPulseControllerAction;
import me.phoenixra.atumvr.core.input.action.types.multi.PoseMultiAction;
import me.phoenixra.atumvr.core.input.hand.XRHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiConsumer;


public class XRDeviceController extends XRDevice implements AtumVRDeviceController, AtumVRBodyView {
    public static final String ID_LEFT = "controller_left";
    public static final String ID_RIGHT = "controller_right";

    @Getter
    private final ControllerType type;


    @Getter
    private final AtumVRBodyJoint bodyJoint;

    private final Set<AtumVRBodyJoint> joints;

    @Getter
    private boolean gripActive;

    /**
     * If the aim / grip pose was located (valid position and orientation)
     * on the last update. Not located poses are identity,
     * e.g. a controller that is idle or out of tracking while its action is active
     */
    @Getter
    private boolean poseValid;
    @Getter
    private boolean gripPoseValid;

    @Getter
    private final AtumVRPoseMutable gripPose = new AtumVRPoseMutable();

    /**
     * If the poses come from the tracked hand (XR_EXT_hand_tracking aim / palm)
     * because the controller is not active or located this frame
     */
    @Getter
    private boolean handDriven;
    /** Pose source for the switch log: controller / hand / none */
    private String source = "none";


    private final PoseMultiAction aimAction;
    private final PoseMultiAction gripAction;
    private final HapticPulseControllerAction hapticPulseAction;

    public XRDeviceController(@NotNull XRProvider vrProvider,
                              @NotNull ControllerType controllerType,
                              @NotNull PoseMultiAction aimAction,
                              @NotNull PoseMultiAction gripAction,
                              @NotNull HapticPulseControllerAction hapticPulseAction) {
        super(vrProvider, controllerType==ControllerType.LEFT ? ID_LEFT : ID_RIGHT);
        this.type = controllerType;
        this.bodyJoint = controllerType==ControllerType.LEFT
                ? AtumVRBodyJoint.LEFT_HAND
                : AtumVRBodyJoint.RIGHT_HAND;
        this.joints = Set.of(bodyJoint);
        this.aimAction = aimAction;
        this.gripAction = gripAction;
        this.hapticPulseAction = hapticPulseAction;

    }

    @Override
    public void update() {
        var subActionAim = aimAction.getSubActionsAsPose().get(type.ordinal());
        var subActionGrip = gripAction.getSubActionsAsPose().get(type.ordinal());

        pose.update(subActionAim.getCurrentState());
        active = subActionAim.isActive();
        poseValid = subActionAim.isLocated();

        gripPose.update(subActionGrip.getCurrentState());
        gripActive = subActionGrip.isActive();
        gripPoseValid = subActionGrip.isLocated();

        // controller not active / located: the tracked hand drives this device
        var handTracking = vrProvider.getInputHandler().getHandTracking();
        XRHand hand = handTracking == null ? null : handTracking.getHand(type);
        boolean controllerOk = active && poseValid;
        handDriven = !controllerOk && hand != null && hand.isUsable();
        if (handDriven) {
            pose.update(hand.getAimPose().matrix(), hand.getAimPose().orientation(), hand.getAimPose().position());
            gripPose.update(hand.getGripPose().matrix(), hand.getGripPose().orientation(), hand.getGripPose().position());
            active = true;
            gripActive = true;
            poseValid = true;
            gripPoseValid = true;
        }
        String newSource = controllerOk ? "controller" : handDriven ? "hand" : "none";
        if (!newSource.equals(source)) {
            vrProvider.getLogger().logInfo("[MR] Hands: " + (type == ControllerType.LEFT ? "left" : "right")
                    + " source " + source + " -> " + newSource);
            source = newSource;
        }
    }




    @Override
    public void triggerHapticPulse(float frequency, float amplitude, long durationNanoSec) {
        hapticPulseAction.triggerHapticPulse(type, frequency, amplitude, durationNanoSec);
    }

    @Override
    public @NotNull AtumVRPose getAimPose() {
        return pose;
    }

    // -------- BODY JOINTS --------


    @Override
    public @Nullable AtumVRPose getJointPose(@NotNull AtumVRBodyJoint joint) {
        if (joint != bodyJoint) {
            return null;
        }
        if (gripActive) {
            return gripPose;
        }
        return active ? pose : null;
    }

    @Override
    public void collectJoints(@NotNull BiConsumer<AtumVRBodyJoint, AtumVRPose> sink) {
        AtumVRPose handPose = getJointPose(bodyJoint);
        if (handPose != null) {
            sink.accept(bodyJoint, handPose);
        }
    }

    @Override
    public @NotNull Set<AtumVRBodyJoint> getTrackedJoints() {
        return (active || gripActive) ? joints : Set.of();
    }


}
