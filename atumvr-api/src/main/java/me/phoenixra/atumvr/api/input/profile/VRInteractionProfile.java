package me.phoenixra.atumvr.api.input.profile;

import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.action.AtumVRActionSet;
import me.phoenixra.atumvr.api.input.action.data.VRActionData;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataButton;
import me.phoenixra.atumvr.api.input.action.data.VRActionDataVec2;
import me.phoenixra.atumvr.api.input.profile.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Base interface for interaction profile.
 *
 * <p>
 *     It is an action set made for specific {@link VRInteractionProfileType interaction profile type}.<br>
 *     Contains all the actions supported by it.
 * </p>
 *
 * @see HpMixedRealityProfile
 * @see OculusTouchProfile
 * @see ValveIndexProfile
 * @see ViveProfile
 * @see ViveCosmosProfile
 * @see WindowsMotionProfile
 * @see SteamFrameProfile
 */
public interface VRInteractionProfile extends AtumVRActionSet {

    // ---------- COMMON ACTION IDENTIFIERS ----------

    VRActionIdentifier POSE_HAND_AIM_LEFT = new VRActionIdentifier("hand.aim.left", ControllerType.LEFT);

    VRActionIdentifier POSE_HAND_AIM_RIGHT = new VRActionIdentifier("hand.aim.right", ControllerType.RIGHT);


    VRActionIdentifier POSE_HAND_GRIP_LEFT = new VRActionIdentifier("hand.grip.left", ControllerType.LEFT);

    VRActionIdentifier POSE_HAND_GRIP_RIGHT = new VRActionIdentifier("hand.grip.right", ControllerType.RIGHT);

    // ----------------------------------------

    /**
     * Get interaction profile type
     *
     * @return the type
     */
    @NotNull VRInteractionProfileType getType();


    /**
     * Get trigger value action (float button) of specified controller type
     * <p>
     *     It is a common action for all {@link VRInteractionProfileType interaction profiles}
     * </p>
     *
     * @param controllerType the controller type to get data from
     * @return the action
     */
    @NotNull
    VRActionDataButton getTriggerButton(@NotNull ControllerType controllerType);

    /**
     * Get all action ids
     *
     * @return the action ids
     */
    Collection<VRActionIdentifier> getActionIds();

    /**
     * Get action by specified id or null if not found
     *
     * @param id the action identifier
     * @return the action or null
     */
    @Nullable VRActionData getAction(@NotNull VRActionIdentifier id);


    /**
     * Get all button action ids
     *
     * @return the button action ids
     */
    Collection<VRActionIdentifier> getButtonIds();

    /**
     * Get button action by specified id or null if not found
     *
     * @param id the action identifier
     * @return the button action or null
     */
    @Nullable VRActionDataButton getButton(@NotNull VRActionIdentifier id);


    /**
     * Get all vec2 action ids
     *
     * @return the vec2 action ids
     */
    Collection<VRActionIdentifier> getVec2Ids();

    /**
     * Get vec2 action by specified id or null if not found
     *
     * @param id the action identifier
     * @return action data or null
     */
    @Nullable VRActionDataVec2 getVec2(@NotNull VRActionIdentifier id);


    /**
     * If this interaction profile is active.
     * <p>
     *     Only one interaction profile can be active at the same time
     * </p>
     *
     * @return true/false
     */
    default boolean isProfileActive(){
        return getTriggerButton(ControllerType.LEFT).isActive()
                || getTriggerButton(ControllerType.RIGHT).isActive();
    }



}
