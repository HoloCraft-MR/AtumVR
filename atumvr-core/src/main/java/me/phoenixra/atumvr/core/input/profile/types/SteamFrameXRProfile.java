package me.phoenixra.atumvr.core.input.profile.types;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.action.data.VRActionData;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataButton;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataVec2;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfileType;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.input.action.XRAction;
import me.phoenixra.atumvr.core.input.action.types.multi.BoolButtonMultiAction;
import me.phoenixra.atumvr.core.input.action.types.multi.FloatButtonMultiAction;
import me.phoenixra.atumvr.core.input.action.types.multi.Vec2MultiAction;
import me.phoenixra.atumvr.core.input.profile.XRInteractionProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.*;

import static me.phoenixra.atumvr.api.input.profile.types.SteamFrameProfile.*;
import static me.phoenixra.atumvr.core.input.action.XRAction.LEFT_HAND_PATH;
import static me.phoenixra.atumvr.core.input.action.XRAction.RIGHT_HAND_PATH;

@Getter
public class SteamFrameXRProfile extends XRInteractionProfile {
    // without it SteamVR will see controllers as Oculus Touch
    public static final String EXTENSION_NAME = "XR_VALVE_frame_controller_interaction";

    private static final VRInteractionProfileType PROFILE = VRInteractionProfileType.STEAM_FRAME;



    // System buttons
    private BoolButtonMultiAction systemButton;
    private BoolButtonMultiAction systemButtonTouch;

    // View (left) / Menu (right)
    private BoolButtonMultiAction menuButton;
    private BoolButtonMultiAction menuButtonTouch;

    // Face buttons, paired by position: d-pad on the left, A/B/X/Y on the right
    private BoolButtonMultiAction faceTopButton;
    private BoolButtonMultiAction faceTopButtonTouch;
    private BoolButtonMultiAction faceOutsideButton;
    private BoolButtonMultiAction faceOutsideButtonTouch;
    private BoolButtonMultiAction faceBottomButton;
    private BoolButtonMultiAction faceBottomButtonTouch;
    private BoolButtonMultiAction faceInsideButton;
    private BoolButtonMultiAction faceInsideButtonTouch;

    // Shoulder
    private BoolButtonMultiAction shoulderButton;
    private BoolButtonMultiAction shoulderButtonTouch;

    // Squeeze & Trigger
    private FloatButtonMultiAction gripValue;
    private BoolButtonMultiAction gripClick;
    private BoolButtonMultiAction gripTouch;
    private FloatButtonMultiAction triggerValue;
    private BoolButtonMultiAction triggerClick;
    private BoolButtonMultiAction triggerTouch;

    // Thumb stick
    private Vec2MultiAction thumbStick;
    private BoolButtonMultiAction thumbStickButton;
    private BoolButtonMultiAction thumbStickTouch;


    private Map<VRActionIdentifier, VRActionData> actionMap;
    private Map<VRActionIdentifier, VRActionDataButton> buttonMap;
    private Map<VRActionIdentifier, VRActionDataVec2> vec2Map;

    public SteamFrameXRProfile(XRProvider vrProvider) {
        super(vrProvider, "steam_frame", "Steam Frame Controller", 0);
    }

    @Override
    protected List<XRAction> loadActions(@NotNull XRProvider vrProvider) {

        // -------- SYSTEM --------
        systemButton = buttonPair(vrProvider, "button.system", "System Button",
                BUTTON_SYSTEM_LEFT, "input/system/click",
                BUTTON_SYSTEM_RIGHT, "input/system/click");
        systemButtonTouch = buttonPair(vrProvider, "button.system.touch", "System Button Touch",
                BUTTON_SYSTEM_TOUCH_LEFT, "input/system/touch",
                BUTTON_SYSTEM_TOUCH_RIGHT, "input/system/touch");

        // -------- VIEW / MENU --------
        menuButton = buttonPair(vrProvider, "button.menu", "View/Menu Button",
                BUTTON_VIEW, "input/view/click",
                BUTTON_MENU, "input/menu/click");
        menuButtonTouch = buttonPair(vrProvider, "button.menu.touch", "View/Menu Button Touch",
                BUTTON_VIEW_TOUCH, "input/view/touch",
                BUTTON_MENU_TOUCH, "input/menu/touch");

        // -------- FACE BUTTONS --------
        faceTopButton = buttonPair(vrProvider, "button.face.top", "Top Face Button",
                BUTTON_DPAD_UP, "input/dpad_up/click",
                BUTTON_Y, "input/y/click");
        faceTopButtonTouch = buttonPair(vrProvider, "button.face.top.touch", "Top Face Button Touch",
                BUTTON_DPAD_UP_TOUCH, "input/dpad_up/touch",
                BUTTON_Y_TOUCH, "input/y/touch");
        faceOutsideButton = buttonPair(vrProvider, "button.face.outside", "Outside Face Button",
                BUTTON_DPAD_LEFT, "input/dpad_left/click",
                BUTTON_B, "input/b/click");
        faceOutsideButtonTouch = buttonPair(vrProvider, "button.face.outside.touch", "Outside Face Button Touch",
                BUTTON_DPAD_LEFT_TOUCH, "input/dpad_left/touch",
                BUTTON_B_TOUCH, "input/b/touch");
        faceBottomButton = buttonPair(vrProvider, "button.face.bottom", "Bottom Face Button",
                BUTTON_DPAD_DOWN, "input/dpad_down/click",
                BUTTON_A, "input/a/click");
        faceBottomButtonTouch = buttonPair(vrProvider, "button.face.bottom.touch", "Bottom Face Button Touch",
                BUTTON_DPAD_DOWN_TOUCH, "input/dpad_down/touch",
                BUTTON_A_TOUCH, "input/a/touch");
        faceInsideButton = buttonPair(vrProvider, "button.face.inside", "Inside Face Button",
                BUTTON_DPAD_RIGHT, "input/dpad_right/click",
                BUTTON_X, "input/x/click");
        faceInsideButtonTouch = buttonPair(vrProvider, "button.face.inside.touch", "Inside Face Button Touch",
                BUTTON_DPAD_RIGHT_TOUCH, "input/dpad_right/touch",
                BUTTON_X_TOUCH, "input/x/touch");

        // -------- SHOULDER --------
        shoulderButton = buttonPair(vrProvider, "button.shoulder", "Shoulder Button",
                BUTTON_SHOULDER_LEFT, "input/shoulder/click",
                BUTTON_SHOULDER_RIGHT, "input/shoulder/click");
        shoulderButtonTouch = buttonPair(vrProvider, "button.shoulder.touch", "Shoulder Button Touch",
                BUTTON_SHOULDER_TOUCH_LEFT, "input/shoulder/touch",
                BUTTON_SHOULDER_TOUCH_RIGHT, "input/shoulder/touch");

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
        gripClick = buttonPair(vrProvider, "button.grip.click", "Grip Click",
                BUTTON_GRIP_CLICK_LEFT, "input/squeeze/click",
                BUTTON_GRIP_CLICK_RIGHT, "input/squeeze/click");
        gripTouch = buttonPair(vrProvider, "button.grip.touch", "Grip Touch",
                BUTTON_GRIP_TOUCH_LEFT, "input/squeeze/touch",
                BUTTON_GRIP_TOUCH_RIGHT, "input/squeeze/touch");

        // -------- TRIGGER --------
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
        triggerClick = buttonPair(vrProvider, "button.trigger.click", "Trigger Click",
                BUTTON_TRIGGER_CLICK_LEFT, "input/trigger/click",
                BUTTON_TRIGGER_CLICK_RIGHT, "input/trigger/click");
        triggerTouch = buttonPair(vrProvider, "button.trigger.touch", "Trigger Touch",
                BUTTON_TRIGGER_TOUCH_LEFT, "input/trigger/touch",
                BUTTON_TRIGGER_TOUCH_RIGHT, "input/trigger/touch");

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
        thumbStickButton = buttonPair(vrProvider, "button.thumbstick", "Thumbstick Button",
                BUTTON_THUMBSTICK_LEFT, "input/thumbstick/click",
                BUTTON_THUMBSTICK_RIGHT, "input/thumbstick/click");
        thumbStickTouch = buttonPair(vrProvider, "button.thumbstick.touch", "Thumbstick Touch",
                BUTTON_THUMBSTICK_TOUCH_LEFT, "input/thumbstick/touch",
                BUTTON_THUMBSTICK_TOUCH_RIGHT, "input/thumbstick/touch");

        List<VRActionDataButton> listButton = new ArrayList<>();
        listButton.addAll(systemButton.getSubActionsAsButton());
        listButton.addAll(systemButtonTouch.getSubActionsAsButton());
        listButton.addAll(menuButton.getSubActionsAsButton());
        listButton.addAll(menuButtonTouch.getSubActionsAsButton());
        listButton.addAll(faceTopButton.getSubActionsAsButton());
        listButton.addAll(faceTopButtonTouch.getSubActionsAsButton());
        listButton.addAll(faceOutsideButton.getSubActionsAsButton());
        listButton.addAll(faceOutsideButtonTouch.getSubActionsAsButton());
        listButton.addAll(faceBottomButton.getSubActionsAsButton());
        listButton.addAll(faceBottomButtonTouch.getSubActionsAsButton());
        listButton.addAll(faceInsideButton.getSubActionsAsButton());
        listButton.addAll(faceInsideButtonTouch.getSubActionsAsButton());
        listButton.addAll(shoulderButton.getSubActionsAsButton());
        listButton.addAll(shoulderButtonTouch.getSubActionsAsButton());
        listButton.addAll(gripValue.getSubActionsAsButton());
        listButton.addAll(gripClick.getSubActionsAsButton());
        listButton.addAll(gripTouch.getSubActionsAsButton());
        listButton.addAll(triggerValue.getSubActionsAsButton());
        listButton.addAll(triggerClick.getSubActionsAsButton());
        listButton.addAll(triggerTouch.getSubActionsAsButton());
        listButton.addAll(thumbStickButton.getSubActionsAsButton());
        listButton.addAll(thumbStickTouch.getSubActionsAsButton());

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
                systemButton, systemButtonTouch,
                menuButton, menuButtonTouch,
                faceTopButton, faceTopButtonTouch,
                faceOutsideButton, faceOutsideButtonTouch,
                faceBottomButton, faceBottomButtonTouch,
                faceInsideButton, faceInsideButtonTouch,
                shoulderButton, shoulderButtonTouch,
                gripValue, gripClick, gripTouch,
                triggerValue, triggerClick, triggerTouch,
                thumbStick, thumbStickButton, thumbStickTouch
        );
    }

    private BoolButtonMultiAction buttonPair(@NotNull XRProvider vrProvider,
                                             @NotNull String id,
                                             @NotNull String localizedName,
                                             @NotNull VRActionIdentifier leftId,
                                             @NotNull String leftSource,
                                             @NotNull VRActionIdentifier rightId,
                                             @NotNull String rightSource) {
        return new BoolButtonMultiAction(
                vrProvider, this,
                new VRActionIdentifier(id), localizedName,
                List.of(
                        new BoolButtonMultiAction.SubActionBoolButton(
                                leftId,
                                LEFT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, leftSource),
                        new BoolButtonMultiAction.SubActionBoolButton(
                                rightId,
                                RIGHT_HAND_PATH,
                                false
                        ).putDefaultBindings(PROFILE, rightSource)
                )
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
