package me.phoenixra.atumvr.core.input.profile.types;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.action.data.VRActionData;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataButton;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataVec2;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfileType;
import me.phoenixra.atumvr.core.input.action.XRAction;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfile;
import me.phoenixra.atumvr.core.input.action.types.multi.BoolButtonMultiAction;
import me.phoenixra.atumvr.core.input.action.types.multi.FloatButtonMultiAction;
import me.phoenixra.atumvr.core.input.action.types.multi.Vec2MultiAction;
import me.phoenixra.atumvr.core.input.action.types.single.BoolButtonAction;
import me.phoenixra.atumvr.core.input.profile.XRInteractionProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.*;

import static me.phoenixra.atumvr.api.input.profile.types.OculusTouchProfile.*;
import static me.phoenixra.atumvr.core.input.action.XRAction.LEFT_HAND_PATH;
import static me.phoenixra.atumvr.core.input.action.XRAction.RIGHT_HAND_PATH;

@Getter
public class OculusTouchXRProfile extends XRInteractionProfile {
    private static final VRInteractionProfileType PROFILE = VRInteractionProfileType.OCULUS_TOUCH;

    /**
     * Quest 3/3S controllers. A superset of Oculus Touch paths,
     * so the same bindings are suggested for it (when the extension is enabled)
     */
    public static final String TOUCH_CONTROLLER_PLUS_PROFILE_PATH = "/interaction_profiles/meta/touch_controller_plus";
    public static final String META_TOUCH_CONTROLLER_PLUS_EXTENSION_NAME = "XR_META_touch_controller_plus";


    // Single-hand only buttons
    private BoolButtonAction menuButton;
    private BoolButtonAction systemButton;

    // Primary (X & A) / Secondary buttons (Y & B)
    private BoolButtonMultiAction primaryButton;
    private BoolButtonMultiAction primaryButtonTouch;
    private BoolButtonMultiAction secondaryButton;
    private BoolButtonMultiAction secondaryButtonTouch;

    // Squeeze & Trigger
    private FloatButtonMultiAction gripValue;
    private FloatButtonMultiAction triggerValue;
    private BoolButtonMultiAction triggerTouch;

    // Thumb stick
    private Vec2MultiAction thumbStick;
    private BoolButtonMultiAction thumbStickButton;
    private BoolButtonMultiAction thumbStickTouch;

    // Thumb rest touch
    private BoolButtonMultiAction thumbRestTouch;


    private Map<VRActionIdentifier, VRActionData> actionMap;
    private Map<VRActionIdentifier, VRActionDataButton> buttonMap;
    private Map<VRActionIdentifier, VRActionDataVec2> vec2Map;

    public OculusTouchXRProfile(XRProvider vrProvider) {
        super(vrProvider, "oculus_touch", "Oculus Touch Controller", 0);
    }

    @Override
    protected List<XRAction> loadActions(@NotNull XRProvider vrProvider) {


        // -------- MENU & SYSTEM BUTTONS --------
        menuButton = new BoolButtonAction(
                vrProvider, this,
                BUTTON_MENU, "Menu Button"
        ).putDefaultBindings(PROFILE, LEFT_HAND_PATH+"/input/menu/click");

        systemButton = new BoolButtonAction(
                vrProvider, this,
                BUTTON_SYSTEM, "System Button"
        ).putDefaultBindings(PROFILE, RIGHT_HAND_PATH+"/input/system/click");

        // -------- PRIMARY BUTTONS (X/A) --------
        primaryButton = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.primary"), "Primary Button",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_X,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/x/click"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_A,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/a/click")
                )
        );
        primaryButtonTouch = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.primary.touch"), "Primary Button Touch",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_X_TOUCH,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/x/touch"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_A_TOUCH,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/a/touch")
                )
        );
        // -------- SECONDARY (Y/B) --------
        secondaryButton = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.secondary"), "Secondary Button",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_Y,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/y/click"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_B,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/b/click")
                )
        );
        secondaryButtonTouch = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.secondary.touch"), "Secondary Button Touch",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_Y_TOUCH,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/y/touch"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_B_TOUCH,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/b/touch")
                )
        );

        // -------- GRIP --------
        gripValue = new FloatButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.grip"), "Grip Value",
                0.9f,   // press
                0.85f,  // release
                List.of(
                        new FloatButtonMultiAction.SubActionFloatButton(
                                BUTTON_GRIP_LEFT,
                                LEFT_HAND_PATH,
                                0f
                        ).putDefaultBindings(PROFILE, "input/squeeze/value"),
                        new FloatButtonMultiAction.SubActionFloatButton(
                                BUTTON_GRIP_RIGHT,
                                RIGHT_HAND_PATH,
                                0f
                        ).putDefaultBindings(PROFILE, "input/squeeze/value")
                )
        );

        // -------- TRIGGER BUTTON --------
        triggerValue = new FloatButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.trigger"), "Trigger Value",
                0.7f,   // press
                0.65f,  // release
                List.of(
                        new FloatButtonMultiAction.SubActionFloatButton(
                                BUTTON_TRIGGER_LEFT,
                                LEFT_HAND_PATH,
                                0f
                        ).putDefaultBindings(PROFILE, "input/trigger/value"),
                        new FloatButtonMultiAction.SubActionFloatButton(
                                BUTTON_TRIGGER_RIGHT,
                                RIGHT_HAND_PATH,
                                0f
                        ).putDefaultBindings(PROFILE, "input/trigger/value")
                )
        );
        triggerTouch = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.trigger.touch"), "Trigger Touch",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_TRIGGER_TOUCH_LEFT,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/trigger/touch"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_TRIGGER_TOUCH_RIGHT,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/trigger/touch")
                )
        );

        // -------- THUMB STICK --------
        thumbStick = new Vec2MultiAction(
                vrProvider, this,
                new VRActionIdentifier("vec2.thumbstick"), "Thumbstick",
                List.of(
                        new Vec2MultiAction.SubActionVec2(
                                VEC2_THUMBSTICK_LEFT,
                                LEFT_HAND_PATH,
                                new Vector2f(0,0)
                        ).putDefaultBindings(PROFILE, "input/thumbstick"),
                        new Vec2MultiAction.SubActionVec2(
                                VEC2_THUMBSTICK_RIGHT,
                                RIGHT_HAND_PATH,
                                new Vector2f(0,0)
                        ).putDefaultBindings(PROFILE, "input/thumbstick")
                )
        );
        thumbStickButton = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.thumbstick"), "Thumbstick Button",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBSTICK_LEFT,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbstick/click"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBSTICK_RIGHT,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbstick/click")
                )
        );
        thumbStickTouch = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.thumbstick.touch"), "Thumbstick Touch",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBSTICK_TOUCH_LEFT,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbstick/touch"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBSTICK_TOUCH_RIGHT,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbstick/touch")
                )
        );

        // -------- THUMB REST --------
        thumbRestTouch = new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier("button.thumbrest.touch"), "Thumbrest Touch",
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBREST_TOUCH_LEFT,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbrest/touch"),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                BUTTON_THUMBREST_TOUCH_RIGHT,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, "input/thumbrest/touch")
                )
        );

        List<VRActionDataButton> listButton = new ArrayList<>();
        listButton.add(menuButton);
        listButton.add(systemButton);
        listButton.addAll(primaryButton.getSubActionsAsButton());
        listButton.addAll(primaryButtonTouch.getSubActionsAsButton());
        listButton.addAll(secondaryButton.getSubActionsAsButton());
        listButton.addAll(secondaryButtonTouch.getSubActionsAsButton());
        listButton.addAll(gripValue.getSubActionsAsButton());
        listButton.addAll(triggerValue.getSubActionsAsButton());
        listButton.addAll(triggerTouch.getSubActionsAsButton());
        listButton.addAll(thumbStickButton.getSubActionsAsButton());
        listButton.addAll(thumbStickTouch.getSubActionsAsButton());
        listButton.addAll(thumbRestTouch.getSubActionsAsButton());

        buttonMap = new LinkedHashMap<>();
        for(var entry : listButton){
            buttonMap.put(entry.getId(), entry);
        }

        List<VRActionDataVec2> listVec2 = new ArrayList<>(thumbStick.getSubActionsAsVec2());

        vec2Map = new LinkedHashMap<>();
        for(var entry : listVec2){
            vec2Map.put(entry.getId(), entry);
        }

        actionMap = new LinkedHashMap<>();
        actionMap.putAll(buttonMap);
        actionMap.putAll(vec2Map);

        return List.of(
                menuButton, systemButton,
                primaryButton, primaryButtonTouch,
                secondaryButton, secondaryButtonTouch,
                gripValue,
                triggerValue, triggerTouch,
                thumbStick, thumbStickButton, thumbStickTouch,
                thumbRestTouch
        );
    }

    @Override
    public @NotNull VRActionDataButton getTriggerButton(@NotNull ControllerType controllerType) {
        return triggerValue.getHandSubaction(controllerType);
    }

    @Override
    public Collection<VRActionIdentifier> getActionIds() {
        return Collections.unmodifiableCollection(actionMap.keySet());
    }

    @Override
    public @Nullable VRActionData getAction(@NotNull VRActionIdentifier id) {
        return actionMap.get(id);
    }


    @Override
    public Collection<VRActionIdentifier> getButtonIds() {
        return Collections.unmodifiableCollection(buttonMap.keySet());
    }

    @Override
    public VRActionDataButton getButton(@NotNull VRActionIdentifier id) {
        return buttonMap.get(id);
    }


    @Override
    public Collection<VRActionIdentifier> getVec2Ids() {
        return Collections.unmodifiableCollection(vec2Map.keySet());
    }

    @Override
    public VRActionDataVec2 getVec2(@NotNull VRActionIdentifier id) {
        return vec2Map.get(id);
    }


    @Override
    public @NotNull VRInteractionProfileType getType() {
        return PROFILE;
    }
}
