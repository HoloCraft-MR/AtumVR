package me.phoenixra.atumvr.core.input.action.types.multi;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataButton;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.enums.XRInputActionType;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfileType;
import me.phoenixra.atumvr.core.input.action.XRActionSet;
import me.phoenixra.atumvr.core.input.action.XRMultiAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrActionStateBoolean;
import org.lwjgl.system.MemoryStack;

import java.util.Collections;
import java.util.List;

public class BoolButtonMultiAction extends XRMultiAction<Boolean> {

    @Getter
    private final List<SubActionBoolButton> subActionsAsButton;

    public BoolButtonMultiAction(@NotNull XRProvider vrProvider,
                                 @NotNull XRActionSet actionSet,
                                 @NotNull VRActionIdentifier id,
                                 @NotNull String localizedName,
                                 @NotNull List<SubActionBoolButton> subActions) {
        super(vrProvider, actionSet, id, localizedName, XRInputActionType.BOOLEAN, subActions);
        subActionsAsButton = Collections.unmodifiableList(subActions);
    }

    @Override
    protected void onInit(@NotNull XRActionSet actionSet, @NotNull MemoryStack stack) {

    }

    @Override
    public void update() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            for(var entry : subActionsAsButton) {
                var state = XrActionStateBoolean.calloc(stack)
                        .type(actionType.getStateId());
                getInfo.subactionPath(entry.getPathHandle());
                getInfo.action(handle);
                vrProvider.checkXRError(
                        XR10.xrGetActionStateBoolean(
                                vrProvider.getSession().getHandle(),
                                getInfo,
                                state
                        ),
                        "xrGetActionStateBoolean"
                );
                boolean value = state.currentState();
                boolean changedSinceLastSync = state.changedSinceLastSync();
                boolean isActive = state.isActive();
                // this hand is driven by hand tracking (controller not active / located)
                if (entry.handValue != null) {
                    value = entry.handValue;
                    changedSinceLastSync = value != entry.getCurrentState();
                    isActive = true;
                }
                entry.update(
                        value,
                        changedSinceLastSync ? vrProvider.getXrDisplayTime() : entry.getLastChangeTime(),
                        changedSinceLastSync,
                        isActive
                );
                if(changedSinceLastSync){
                    vrProvider.getInputHandler().onActionChanged(
                            entry
                    );
                }
            }
        }
    }

    @Override
    public SubActionBoolButton getHandSubaction(@NotNull ControllerType type) {
        return (SubActionBoolButton) super.getHandSubaction(type);
    }


    public static class SubActionBoolButton extends SubAction<Boolean> implements VRActionDataButton {

        /** Value from a tracked hand, set while the hand drives the controller (null = none) */
        protected Boolean handValue;

        /**
         * Hand tracking input for this sub-action, overrides the controller
         * action state (set only while the hand drives the controller)
         *
         * @param value the value, or null for none
         */
        public void setHandValue(Boolean value) {
            handValue = value;
        }


        public SubActionBoolButton(@NotNull VRActionIdentifier id,
                                   @NotNull String path,
                                   @NotNull Boolean initialState) {
            super(id, path, initialState);

        }

        @Override
        public SubActionBoolButton putDefaultBindings(@NotNull List<VRInteractionProfileType> profiles, @Nullable String source) {
            return (SubActionBoolButton) super.putDefaultBindings(profiles, source);
        }

        @Override
        public SubActionBoolButton putDefaultBindings(@NotNull VRInteractionProfileType profile, @Nullable String source) {
            return (SubActionBoolButton) super.putDefaultBindings(profile, source);
        }

        @Override
        public boolean isPressed() {
            return currentState;
        }

        @Override
        public boolean isButtonChanged() {
            return changed;
        }

        @Override
        public long getButtonLastChangeTime() {
            return lastChangeTime;
        }
    }
}
