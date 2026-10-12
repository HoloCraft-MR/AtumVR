package me.phoenixra.atumvr.core.enums;

import lombok.Getter;
import me.phoenixra.atumvr.core.session.platform.XRPlatform;
import org.lwjgl.openxr.KHROpenGLEnable;
import org.lwjgl.openxr.KHRVulkanEnable2;

/**
 * Graphics API used to submit frames to the OpenXR runtime.
 * <p>
 *     The app always renders with OpenGL. On {@link #VULKAN} the session
 *     runs on Vulkan swapchains and finished eye textures are transferred into
 *     them each frame. Vulkan makes Intel Arc compatible
 * </p>
 * <p>
 *     {@link #OPENGL_ES} is used on Android (Quest) when an
 *     {@link me.phoenixra.atumvr.core.session.platform.AndroidXRBridge} is configured
 * </p>
 */
@Getter
public enum XRGraphicsApi {
    OPENGL(KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME),
    VULKAN(KHRVulkanEnable2.XR_KHR_VULKAN_ENABLE2_EXTENSION_NAME),
    OPENGL_ES(XRPlatform.Android.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME);

    private final String extensionName;

    XRGraphicsApi(String extensionName) {
        this.extensionName = extensionName;
    }
}