package me.phoenixra.atumvr.core.input.action.types.multi;

import lombok.Getter;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataPose;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfileType;
import me.phoenixra.atumvr.api.misc.pose.AtumVRPoseRecord;
import me.phoenixra.atumvr.core.utils.XRUtils;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.enums.XRInputActionType;
import me.phoenixra.atumvr.core.input.action.XRActionSet;
import me.phoenixra.atumvr.core.input.action.XRMultiAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackCallocPointer;
import static org.lwjgl.system.MemoryUtil.NULL;

public class PoseMultiAction extends XRMultiAction<AtumVRPoseRecord> {

    @Getter
    private HashMap<SubAction<AtumVRPoseRecord>, XrSpace> xrSpace = new HashMap<>();

    @Getter
    private final List<SubActionPose> subActionsAsPose;


    public PoseMultiAction(@NotNull XRProvider vrProvider,
                           @NotNull XRActionSet actionSet,
                           @NotNull VRActionIdentifier id,
                           @NotNull String localizedName,
                           @NotNull List<SubActionPose> subActions) {
        super(vrProvider, actionSet, id, localizedName, XRInputActionType.POSE, subActions);
        subActionsAsPose = Collections.unmodifiableList(subActions);
    }

    @Override
    protected void onInit(@NotNull XRActionSet actionSet, @NotNull MemoryStack stack) {
        for (SubAction<AtumVRPoseRecord> entry : subActions) {
            XrSession xrSession = vrProvider.getSession().getHandle();
            XrActionSpaceCreateInfo action_space_info = XrActionSpaceCreateInfo
                    .calloc(stack).set(
                            XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO,
                            NULL,
                            handle,
                            entry.getPathHandle(),
                            XRUtils.getPoseIdentity(stack)
                    );
            PointerBuffer pp = stackCallocPointer(1);
            vrProvider.checkXRError(
                    XR10.xrCreateActionSpace(
                            xrSession,
                            action_space_info, pp
                    ),
                    "xrCreateActionSpace"
            );
            xrSpace.put(entry, new XrSpace(pp.get(0), xrSession));
        }
    }

    @Override
    public void update() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (var entry : subActionsAsPose) {
                var state = XrActionStatePose.calloc(stack)
                        .type(actionType.getStateId());
                getInfo.subactionPath(entry.getPathHandle());
                getInfo.action(handle);
                vrProvider.checkXRError(
                        XR10.xrGetActionStatePose(
                                vrProvider.getSession().getHandle(),
                                getInfo,
                                state
                        ),
                        "xrGetActionStatePose"
                );
                var loc = XRUtils.xrLocationFromSpace(
                        vrProvider, xrSpace.get(entry), stack
                );
                // invalid location: keep the flag for code that must not use such a pose
                entry.located = loc != null;

                entry.update(
                        entry.writePose(loc == null ? null : loc.pose()),
                        System.nanoTime(),
                        true,
                        state.isActive()
                );

                if(entry.isChanged()){
                    vrProvider.getInputHandler().onActionChanged(
                            entry
                    );
                }
            }
        }
    }

    public static class SubActionPose extends SubAction<AtumVRPoseRecord> implements VRActionDataPose {

        private final Matrix4f matrix = new Matrix4f();
        private final Quaternionf orientation = new Quaternionf();
        private final Vector3f position = new Vector3f();
        private final AtumVRPoseRecord pose = new AtumVRPoseRecord(matrix, orientation, position);

        /**
         * If the runtime returned a valid position and orientation
         * for this pose on the last update
         */
        @Getter
        private boolean located;


        public SubActionPose(@NotNull VRActionIdentifier id,
                             @NotNull String path,
                             @NotNull AtumVRPoseRecord initialState) {
            super(id, path, initialState);

        }


        protected AtumVRPoseRecord writePose(@Nullable XrPosef xrPose) {
            if (xrPose == null) {
                matrix.identity();
                orientation.identity();
                position.zero();
                return pose;
            }
            XRUtils.normalizeXrPose(xrPose, matrix);
            XRUtils.normalizeXrQuaternion(xrPose.orientation(), orientation);
            XRUtils.normalizeXrVector(xrPose.position$(), position);
            return pose;
        }

        @Override
        public SubActionPose putDefaultBindings(@NotNull List<VRInteractionProfileType> profiles, @Nullable String source) {
            return (SubActionPose) super.putDefaultBindings(profiles, source);
        }

        @Override
        public SubActionPose putDefaultBindings(@NotNull VRInteractionProfileType profile, @Nullable String source) {
            return (SubActionPose) super.putDefaultBindings(profile, source);
        }

        @Override
        public @NotNull AtumVRPoseRecord getPose() {
            return currentState;
        }
    }

}
