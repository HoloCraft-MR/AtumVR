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
import org.lwjgl.openxr.XrActionStateFloat;
import org.lwjgl.system.MemoryStack;

import java.util.Collections;
import java.util.List;

public class FloatButtonMultiAction extends XRMultiAction<Float> {


    @Getter
    private final float pressThreshold;
    @Getter
    private final float releaseThreshold;


    @Getter
    private final List<SubActionFloatButton> subActionsAsButton;

    public FloatButtonMultiAction(@NotNull XRProvider vrProvider,
                                  @NotNull XRActionSet actionSet,
                                  @NotNull VRActionIdentifier id,
                                  @NotNull String localizedName,
                                  float pressThreshold,
                                  float releaseThreshold,
                                  @NotNull List<SubActionFloatButton> subActions) {
        super(vrProvider, actionSet, id, localizedName, XRInputActionType.FLOAT, subActions);
        this.pressThreshold = pressThreshold;
        this.releaseThreshold = releaseThreshold;
        subActionsAsButton = Collections.unmodifiableList(subActions);

    }

    @Override
    protected void onInit(@NotNull XRActionSet actionSet, @NotNull MemoryStack stack) {

    }

    @Override
    public void update() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            for(SubActionFloatButton entry : subActionsAsButton) {
                var state = XrActionStateFloat.calloc(stack)
                        .type(actionType.getStateId());
                getInfo.subactionPath(entry.getPathHandle());
                getInfo.action(handle);
                vrProvider.checkXRError(
                        XR10.xrGetActionStateFloat(
                                vrProvider.getSession().getHandle(),
                                getInfo,
                                state
                        ),
                        "xrGetActionStateFloat"
                );
                float v = state.currentState();
                boolean isActive = state.isActive();
                boolean changedSinceLastSync = state.changedSinceLastSync();
                // this hand is driven by hand tracking (controller not active / located)
                if (!Float.isNaN(entry.handValue)) {
                    v = entry.handValue;
                    changedSinceLastSync = v != entry.getCurrentState();
                    isActive = true;
                }
                boolean newButtonState;
                if (entry.isPressed()) {
                    // once pressed, only go false when drop below the lower threshold
                    newButtonState = v >= releaseThreshold;
                } else {
                    // only go true once exceed the higher threshold
                    newButtonState = v >= pressThreshold;
                }
                boolean buttonChanged = newButtonState != entry.isPressed();
                long buttonLastChangeTime = entry.getButtonLastChangeTime();
                if (buttonChanged) {
                    buttonLastChangeTime = System.currentTimeMillis();
                }
                entry.pressed = newButtonState;
                entry.buttonChanged = buttonChanged;
                entry.buttonLastChangeTime = buttonLastChangeTime;


                entry.update(
                        v,
                        state.lastChangeTime(),
                        changedSinceLastSync,
                        isActive
                );
                if(entry.buttonChanged){
                    vrProvider.getInputHandler().onActionChanged(
                            entry
                    );
                }
            }
        }
    }

    @Override
    public SubActionFloatButton getHandSubaction(@NotNull ControllerType type) {
        return (SubActionFloatButton) super.getHandSubaction(type);
    }

    @Getter
    public static class SubActionFloatButton extends SubAction<Float> implements VRActionDataButton {

        protected boolean pressed;
        protected boolean buttonChanged;
        protected long buttonLastChangeTime;
        /** Value from a tracked hand, set while the hand drives the controller (NaN = none) */
        protected float handValue = Float.NaN;

        /**
         * Hand tracking input for this sub-action, overrides the controller
         * action state (set only while the hand drives the controller)
         *
         * @param value the value, or NaN for none
         */
        public void setHandValue(float value) {
            handValue = value;
        }

        public SubActionFloatButton(@NotNull VRActionIdentifier id,
                                    @NotNull String path,
                                    @NotNull Float initialState) {
            super(id, path, initialState);
        }

        @Override
        public SubActionFloatButton putDefaultBindings(@NotNull List<VRInteractionProfileType> profiles,
                                                       @Nullable String source) {
            return (SubActionFloatButton) super.putDefaultBindings(profiles, source);
        }

        @Override
        public SubActionFloatButton putDefaultBindings(@NotNull VRInteractionProfileType profile, @Nullable String source) {
            return (SubActionFloatButton) super.putDefaultBindings(profile, source);
        }

    }
}
