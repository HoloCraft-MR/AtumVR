package me.phoenixra.atumvr.api.input.profile;

import lombok.Getter;
import me.phoenixra.atumvr.api.input.action.VRActionIdentifier;
import me.phoenixra.atumvr.api.input.profile.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The enum, that represents supported interaction profiles
 */
public enum VRInteractionProfileType {

    VALVE_INDEX("/interaction_profiles/valve/index_controller", Kind.CONTROLLER),

    OCULUS_TOUCH("/interaction_profiles/oculus/touch_controller", Kind.CONTROLLER),

    VIVE("/interaction_profiles/htc/vive_controller", Kind.CONTROLLER),

    VIVE_COSMOS("/interaction_profiles/htc/vive_cosmos_controller", Kind.CONTROLLER),

    HP_MIXED_REALITY("/interaction_profiles/hp/mixed_reality_controller", Kind.CONTROLLER),

    WINDOWS_MOTION("/interaction_profiles/microsoft/motion_controller", Kind.CONTROLLER),

    STEAM_FRAME("/interaction_profiles/valve/frame_controller_valve", Kind.CONTROLLER),

    VIVE_TRACKER("/interaction_profiles/htc/vive_tracker_htcx", Kind.TRACKER);


    public enum Kind {
        CONTROLLER,
        TRACKER
    }

    @Getter
    private final String xrPath;

    @Getter
    private final Kind kind;

    private static final HashMap<String, VRInteractionProfileType> values = new HashMap<>();
    private static final VRInteractionProfileType[] valuesControllers;

    static {
        var list = new ArrayList<VRInteractionProfileType>();
        for(VRInteractionProfileType entry : values()){
            values.put(entry.xrPath, entry);
            if(entry.isController()){
                list.add(entry);
            }
        }
        valuesControllers = list.toArray(new VRInteractionProfileType[0]);
    }

    VRInteractionProfileType(String xrPath, Kind kind){
        this.xrPath = xrPath;
        this.kind = kind;
    }

    /**
     * If this is a controller profile.
     *
     * @return true/false
     */
    public boolean isController(){
        return kind == Kind.CONTROLLER;
    }

    /**
     * If this is a tracker profile.
     *
     * @return true/false
     */
    public boolean isTracker(){
        return kind == Kind.TRACKER;
    }


    /**
     * Get values with controllers only types
     *
     * @return list of controllers only interaction profile types
     */
    @NotNull
    public static VRInteractionProfileType[] valuesController(){
        return valuesControllers;
    }

    /**
     * Get enum from interaction profile path
     */
    public static @Nullable VRInteractionProfileType fromXRPath(@NotNull String path){
        return values.get(path);
    }



    /**
     * Get action ids of specified profile type
     *
     * @param profileType the interaction profile type
     * @return the list of action ids
     */
    public static List<VRActionIdentifier> getActionIdsOf(@NotNull VRInteractionProfileType profileType){
        return switch (profileType){
            case HP_MIXED_REALITY -> HpMixedRealityProfile.ALL_ACTION_IDS;

            case OCULUS_TOUCH -> OculusTouchProfile.ALL_ACTION_IDS;

            case VALVE_INDEX -> ValveIndexProfile.ALL_ACTION_IDS;

            case VIVE_COSMOS -> ViveCosmosProfile.ALL_ACTION_IDS;

            case VIVE -> ViveProfile.ALL_ACTION_IDS;

            case WINDOWS_MOTION -> WindowsMotionProfile.ALL_ACTION_IDS;

            case STEAM_FRAME -> SteamFrameProfile.ALL_ACTION_IDS;

            case VIVE_TRACKER ->  List.of();
        };
    }

    /**
     * Get action button ids of specified profile type
     *
     * @param profileType the interaction profile type
     * @return the list of action ids
     */
    public static List<VRActionIdentifier> getButtonIdsOf(@NotNull VRInteractionProfileType profileType){
        return switch (profileType){
            case HP_MIXED_REALITY -> HpMixedRealityProfile.BUTTON_IDS;

            case OCULUS_TOUCH -> OculusTouchProfile.BUTTON_IDS;

            case VALVE_INDEX -> ValveIndexProfile.BUTTON_IDS;

            case VIVE_COSMOS -> ViveCosmosProfile.BUTTON_IDS;

            case VIVE -> ViveProfile.BUTTON_IDS;

            case WINDOWS_MOTION -> WindowsMotionProfile.BUTTON_IDS;

            case STEAM_FRAME -> SteamFrameProfile.BUTTON_IDS;

            case VIVE_TRACKER ->  List.of();
        };
    }

    /**
     * Get action vec2 ids of specified profile type
     *
     * @param profileType the interaction profile type
     * @return the list of action ids
     */
    public static List<VRActionIdentifier> getVec2IdsOf(@NotNull VRInteractionProfileType profileType){
        return switch (profileType){
            case HP_MIXED_REALITY -> HpMixedRealityProfile.VEC2_IDS;

            case OCULUS_TOUCH -> OculusTouchProfile.VEC2_IDS;

            case VALVE_INDEX -> ValveIndexProfile.VEC2_IDS;

            case VIVE_COSMOS -> ViveCosmosProfile.VEC2_IDS;

            case VIVE -> ViveProfile.VEC2_IDS;

            case WINDOWS_MOTION -> WindowsMotionProfile.VEC2_IDS;

            case STEAM_FRAME -> SteamFrameProfile.VEC2_IDS;

            case VIVE_TRACKER ->  List.of();
        };
    }
}
