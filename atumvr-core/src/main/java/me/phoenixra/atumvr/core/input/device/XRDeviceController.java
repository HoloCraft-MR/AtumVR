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
