package me.phoenixra.atumvr.core.input.hand;

import lombok.Getter;
import me.phoenixra.atumvr.api.enums.ControllerType;
import me.phoenixra.atumvr.api.misc.pose.AtumVRPoseMutable;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.openxr.FBHandTrackingAim;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Tracked hand data of the last frame (XR_EXT_hand_tracking + XR_FB_hand_tracking_aim)
 * and the gestures derived from it.
 * <p>
 *     Poses are in the app reference space (room), meters, like the controller poses
 * </p>
 */
public class XRHand {
    public static final int JOINT_COUNT = 26;

    // XR_HAND_JOINT_*_EXT indices
    public static final int PALM = 0;
    public static final int WRIST = 1;
    public static final int THUMB_TIP = 5;
    public static final int INDEX_TIP = 10;
    public static final int MIDDLE_PROXIMAL = 12;
    public static final int MIDDLE_TIP = 15;
    public static final int RING_TIP = 20;
    public static final int LITTLE_TIP = 25;

    /** Index pinch: press / release on pinch strength (hysteresis) */
    public static final float PINCH_DOWN = 0.85f;
    public static final float PINCH_UP = 0.5f;
    /** Fist: average fingertip-to-palm distance in units of the palm-to-knuckle distance */
    public static final float FIST_DOWN = 1.25f;
    public static final float FIST_UP = 1.5f;
    /** Flat open hand (fingers extended), same unit */
    public static final float FLAT_MIN = 1.75f;
    /** Keep the menu gesture pressed at least this long, so 20 Hz ticks never miss it */
    public static final long MENU_MIN_MS = 150L;

    @Getter
    private final ControllerType type;

    private final Vector3f[] jointPositions = new Vector3f[JOINT_COUNT];
    private final Quaternionf[] jointOrientations = new Quaternionf[JOINT_COUNT];
    private final float[] jointRadii = new float[JOINT_COUNT];
    private final boolean[] jointValid = new boolean[JOINT_COUNT];

    /** Runtime reports the hand as tracked and the palm is located */
    @Getter
    boolean tracked;
    /** XR_FB_hand_tracking_aim aim pose valid this frame */
    @Getter
    boolean aimValid;
    @Getter
    final AtumVRPoseMutable aimPose = new AtumVRPoseMutable();
    /** Palm position with the aim orientation (stand-in for the controller grip pose) */
    @Getter
    final AtumVRPoseMutable gripPose = new AtumVRPoseMutable();

    @Getter
    float pinchStrengthIndex;
    @Getter
    float pinchStrengthMiddle;
    /** XR_HAND_TRACKING_AIM_*_BIT_FB flags */
    @Getter
    long aimStatus;

    // -------- gestures --------
    /** Index pinch held (hysteresis), never while fist / system or menu gesture */
    @Getter
    private boolean pinching;
    /** Fist held (hysteresis) */
    @Getter
    private boolean fist;
    /** Meta menu gesture (palm toward face + pinch), held at least {@link #MENU_MIN_MS} */
    @Getter
    private boolean menuGesture;
    /** Palm toward face, the runtime may be about to use the pinch for the system menu */
    @Getter
    private boolean systemGesture;
    /** Fingers extended, not pinching */
    @Getter
    private boolean flat;
    /** Palm normal (out of the palm) y component: 1 = palm facing straight down */
    @Getter
    private float palmDown;
    /** Average fingertip-to-palm distance / palm-to-knuckle distance (~2.2 open, ~1 fist) */
    @Getter
    private float curl;

    private long menuSeenMs;

    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmpP = new Vector3f();
    private final Quaternionf tmpQ = new Quaternionf();
    private final Matrix4f tmpM = new Matrix4f();

    public XRHand(@NotNull ControllerType type) {
        this.type = type;
        for (int i = 0; i < JOINT_COUNT; i++) {
            jointPositions[i] = new Vector3f();
            jointOrientations[i] = new Quaternionf();
        }
    }

    /**
     * Tracked and the aim pose is valid: usable as a controller replacement
     */
    public boolean isUsable() {
        return tracked && aimValid;
    }

    public @NotNull Vector3fc getJointPosition(int joint) {
        return jointPositions[joint];
    }

    public @NotNull Quaternionfc getJointOrientation(int joint) {
        return jointOrientations[joint];
    }

    public float getJointRadius(int joint) {
        return jointRadii[joint];
    }

    public boolean isJointValid(int joint) {
        return jointValid[joint];
    }

    void setJoint(int joint, boolean valid, float px, float py, float pz,
                  float qx, float qy, float qz, float qw, float radius) {
        jointValid[joint] = valid;
        if (!valid) {
            return;
        }
        jointPositions[joint].set(px, py, pz);
        jointOrientations[joint].set(qx, qy, qz, qw);
        jointRadii[joint] = radius;
    }

    void setAim(boolean valid, float px, float py, float pz, float qx, float qy, float qz, float qw) {
        aimValid = valid && tracked;
        if (!aimValid) {
            return;
        }
        Quaternionf q = tmpQ.set(qx, qy, qz, qw);
        Vector3f p = tmpP.set(px, py, pz);
        aimPose.update(tmpM.identity().translate(p).rotate(q), q, p);
        Vector3f palm = jointValid[PALM] ? jointPositions[PALM] : p;
        gripPose.update(tmpM.identity().translate(palm).rotate(q), q, palm);
    }

    /**
     * Derive gestures from the joints / aim state of this frame
     */
    void updateGestures(long nowMs) {
        if (!tracked) {
            pinching = false;
            fist = false;
            flat = false;
            menuGesture = false;
            systemGesture = false;
            palmDown = 0f;
            curl = 0f;
            return;
        }
        // curl: fingertips (index..little) to palm, relative to the hand size
        float scale = jointPositions[PALM].distance(jointPositions[MIDDLE_PROXIMAL]);
        if (scale > 1e-3f && jointValid[PALM]) {
            Vector3f palm = jointPositions[PALM];
            curl = (palm.distance(jointPositions[INDEX_TIP]) + palm.distance(jointPositions[MIDDLE_TIP])
                    + palm.distance(jointPositions[RING_TIP]) + palm.distance(jointPositions[LITTLE_TIP]))
                    / (4f * scale);
        }
        fist = fist ? curl < FIST_UP : curl < FIST_DOWN;

        // palm joint: +Y out of the back of the hand, so the palm normal is -Y
        jointOrientations[PALM].transform(tmp.set(0f, 1f, 0f));
        palmDown = tmp.y;

        systemGesture = (aimStatus & FBHandTrackingAim.XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB) != 0;
        if ((aimStatus & FBHandTrackingAim.XR_HAND_TRACKING_AIM_MENU_PRESSED_BIT_FB) != 0) {
            menuSeenMs = nowMs;
        }
        menuGesture = menuSeenMs > 0 && nowMs - menuSeenMs < MENU_MIN_MS;

        boolean pinchBit = (aimStatus & FBHandTrackingAim.XR_HAND_TRACKING_AIM_INDEX_PINCHING_BIT_FB) != 0;
        boolean blocked = fist || systemGesture || menuGesture || !aimValid;
        if (blocked) {
            pinching = false;
        } else if (pinching) {
            pinching = pinchStrengthIndex >= PINCH_UP;
        } else {
            pinching = pinchBit || pinchStrengthIndex >= PINCH_DOWN;
        }
        flat = !fist && !pinching && curl >= FLAT_MIN;
    }

    /** Trigger value fed to the controller trigger action: 1 while pinching, else the strength capped below press */
    public float getTriggerValue() {
        return pinching ? 1f : Math.min(pinchStrengthIndex, 0.6f);
    }
}
