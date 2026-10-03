package me.phoenixra.atumvr.api.input.profile.types;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;

import java.util.*;

@Getter
public class SteamFrameProfile {

    public static final VRActionIdentifier BUTTON_SYSTEM_LEFT = new VRActionIdentifier("button.system.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_SYSTEM_RIGHT = new VRActionIdentifier("button.system.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_SYSTEM_TOUCH_LEFT = new VRActionIdentifier("button.system.touch.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_SYSTEM_TOUCH_RIGHT = new VRActionIdentifier("button.system.touch.right", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_VIEW = new VRActionIdentifier("button.view", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_MENU = new VRActionIdentifier("button.menu", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_VIEW_TOUCH = new VRActionIdentifier("button.view.touch", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_MENU_TOUCH = new VRActionIdentifier("button.menu.touch", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_DPAD_UP = new VRActionIdentifier("button.dpad.up", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_LEFT = new VRActionIdentifier("button.dpad.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_DOWN = new VRActionIdentifier("button.dpad.down", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_RIGHT = new VRActionIdentifier("button.dpad.right", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_UP_TOUCH = new VRActionIdentifier("button.dpad.up.touch", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_LEFT_TOUCH = new VRActionIdentifier("button.dpad.left.touch", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_DOWN_TOUCH = new VRActionIdentifier("button.dpad.down.touch", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_DPAD_RIGHT_TOUCH = new VRActionIdentifier("button.dpad.right.touch", ControllerType.LEFT);


    public static final VRActionIdentifier BUTTON_Y = new VRActionIdentifier("button.y", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_B = new VRActionIdentifier("button.b", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_A = new VRActionIdentifier("button.a", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_X = new VRActionIdentifier("button.x", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_Y_TOUCH = new VRActionIdentifier("button.y.touch", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_B_TOUCH = new VRActionIdentifier("button.b.touch", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_A_TOUCH = new VRActionIdentifier("button.a.touch", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_X_TOUCH = new VRActionIdentifier("button.x.touch", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_SHOULDER_LEFT = new VRActionIdentifier("button.shoulder.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_SHOULDER_RIGHT = new VRActionIdentifier("button.shoulder.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_SHOULDER_TOUCH_LEFT = new VRActionIdentifier("button.shoulder.touch.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_SHOULDER_TOUCH_RIGHT = new VRActionIdentifier("button.shoulder.touch.right", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_GRIP_LEFT = new VRActionIdentifier("button.grip.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_GRIP_RIGHT = new VRActionIdentifier("button.grip.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_GRIP_CLICK_LEFT = new VRActionIdentifier("button.grip.click.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_GRIP_CLICK_RIGHT = new VRActionIdentifier("button.grip.click.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_GRIP_TOUCH_LEFT = new VRActionIdentifier("button.grip.touch.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_GRIP_TOUCH_RIGHT = new VRActionIdentifier("button.grip.touch.right", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_TRIGGER_LEFT = new VRActionIdentifier("button.trigger.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_TRIGGER_RIGHT = new VRActionIdentifier("button.trigger.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_TRIGGER_CLICK_LEFT = new VRActionIdentifier("button.trigger.click.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_TRIGGER_CLICK_RIGHT = new VRActionIdentifier("button.trigger.click.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_TRIGGER_TOUCH_LEFT = new VRActionIdentifier("button.trigger.touch.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_TRIGGER_TOUCH_RIGHT = new VRActionIdentifier("button.trigger.touch.right", ControllerType.RIGHT);


    public static final VRActionIdentifier BUTTON_THUMBSTICK_LEFT = new VRActionIdentifier("button.thumbstick.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_THUMBSTICK_RIGHT = new VRActionIdentifier("button.thumbstick.right", ControllerType.RIGHT);
    public static final VRActionIdentifier BUTTON_THUMBSTICK_TOUCH_LEFT = new VRActionIdentifier("button.thumbstick.touch.left", ControllerType.LEFT);
    public static final VRActionIdentifier BUTTON_THUMBSTICK_TOUCH_RIGHT = new VRActionIdentifier("button.thumbstick.touch.right", ControllerType.RIGHT);


    public static final VRActionIdentifier VEC2_THUMBSTICK_LEFT = new VRActionIdentifier("vec2.thumbstick.left", ControllerType.LEFT);
    public static final VRActionIdentifier VEC2_THUMBSTICK_RIGHT = new VRActionIdentifier("vec2.thumbstick.right", ControllerType.RIGHT);


    public static final List<VRActionIdentifier> ALL_ACTION_IDS;
    public static final List<VRActionIdentifier> BUTTON_IDS;
    public static final List<VRActionIdentifier> VEC2_IDS;

    static {
        BUTTON_IDS = List.of(
                BUTTON_SYSTEM_LEFT, BUTTON_SYSTEM_RIGHT,
                BUTTON_SYSTEM_TOUCH_LEFT, BUTTON_SYSTEM_TOUCH_RIGHT,
                BUTTON_VIEW, BUTTON_MENU,
                BUTTON_VIEW_TOUCH, BUTTON_MENU_TOUCH,
                BUTTON_DPAD_UP, BUTTON_DPAD_LEFT, BUTTON_DPAD_DOWN, BUTTON_DPAD_RIGHT,
                BUTTON_DPAD_UP_TOUCH, BUTTON_DPAD_LEFT_TOUCH, BUTTON_DPAD_DOWN_TOUCH, BUTTON_DPAD_RIGHT_TOUCH,
                BUTTON_Y, BUTTON_B, BUTTON_A, BUTTON_X,
                BUTTON_Y_TOUCH, BUTTON_B_TOUCH, BUTTON_A_TOUCH, BUTTON_X_TOUCH,
                BUTTON_SHOULDER_LEFT, BUTTON_SHOULDER_RIGHT,
                BUTTON_SHOULDER_TOUCH_LEFT, BUTTON_SHOULDER_TOUCH_RIGHT,
                BUTTON_GRIP_LEFT, BUTTON_GRIP_RIGHT,
                BUTTON_GRIP_CLICK_LEFT, BUTTON_GRIP_CLICK_RIGHT,
                BUTTON_GRIP_TOUCH_LEFT, BUTTON_GRIP_TOUCH_RIGHT,
                BUTTON_TRIGGER_LEFT, BUTTON_TRIGGER_RIGHT,
                BUTTON_TRIGGER_CLICK_LEFT, BUTTON_TRIGGER_CLICK_RIGHT,
                BUTTON_TRIGGER_TOUCH_LEFT, BUTTON_TRIGGER_TOUCH_RIGHT,
                BUTTON_THUMBSTICK_LEFT, BUTTON_THUMBSTICK_RIGHT,
                BUTTON_THUMBSTICK_TOUCH_LEFT, BUTTON_THUMBSTICK_TOUCH_RIGHT
        );
        VEC2_IDS = List.of(
                VEC2_THUMBSTICK_LEFT, VEC2_THUMBSTICK_RIGHT
        );
        var all = new ArrayList<>(BUTTON_IDS);
        all.addAll(VEC2_IDS);
        ALL_ACTION_IDS = List.copyOf(all);
    }



}
