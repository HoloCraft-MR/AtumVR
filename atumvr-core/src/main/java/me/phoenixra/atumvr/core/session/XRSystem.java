package me.phoenixra.atumvr.core.session;

import lombok.Getter;
import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeGLX;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.opengl.GLX;
import org.lwjgl.opengl.GLX12;
import org.lwjgl.opengl.WGL;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Struct;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.windows.User32;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GLX13.*;
import static org.lwjgl.system.MemoryStack.stackInts;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * XR session system (low-level OpenXR stuff)
 */
public class XRSystem {
    private final XRProvider vrProvider;

    @Getter
    private long systemId;

    public XRSystem(@NotNull XRProvider vrProvider){
        this.vrProvider = vrProvider;

    }

    public void init(){
        try (MemoryStack stack = MemoryStack.stackPush()) {


            // 1) Acquire system ID for HMD
            var sysGetInfo = XrSystemGetInfo.calloc(stack)
                    .type(XR10.XR_TYPE_SYSTEM_GET_INFO)
                    .next(NULL)
                    .formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);

            LongBuffer sysIdBuf = stack.callocLong(1);
            vrProvider.checkXRError(
                    XR10.xrGetSystem(
                            vrProvider.getSession().getInstance().getHandle(),
                            sysGetInfo,
                            sysIdBuf
                    ),
                    "xrGetSystem", "fetch HMD system ID"
            );

            systemId = sysIdBuf.get(0);
            if (systemId == XR10.XR_NULL_SYSTEM_ID) {
                throw new AtumVRException("No compatible HMD detected (system ID == 0)");
            }

            // 2) Query system properties
            var sysProps = XrSystemProperties.calloc(stack)
                    .type(XR10.XR_TYPE_SYSTEM_PROPERTIES);
            vrProvider.checkXRError(
                    XR10.xrGetSystemProperties(
                            vrProvider.getSession().getInstance().getHandle(),
                            systemId,
                            sysProps
                    ),
                    "xrGetSystemProperties", "id=" + systemId
            );

            String name = memUTF8(memAddress(sysProps.systemName()));
            var track = sysProps.trackingProperties();
            var gfx = sysProps.graphicsProperties();

            vrProvider.getLogger().logInfo(
                    String.format(
                            "Found HMD [%s] (vendor=%d): orientTrack=%b, posTrack=%b, maxRes=%dx%d, maxLayers=%d",
                            name,
                            sysProps.vendorId(),
                            track.orientationTracking(),
                            track.positionTracking(),
                            gfx.maxSwapchainImageWidth(),
                            gfx.maxSwapchainImageHeight(),
                            gfx.maxLayerCount()
                    )
            );
        }
    }


    public Struct<?> createGraphicsBinding(MemoryStack stack,
                                            XrInstance instance,
                                            long systemID,
                                            long windowHandle) {

        var graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack)
                .type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
        KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(
                instance, systemID, graphicsRequirements
        );
        //Bind the OpenGL context to the OpenXR instance and create the session
        if (Platform.get() == Platform.WINDOWS) {
            long deviceContext;
            long glContext;
            if (windowHandle != NULL) {
                deviceContext = User32.GetDC(GLFWNativeWin32.glfwGetWin32Window(windowHandle));
                glContext = GLFWNativeWGL.glfwGetWGLContext(windowHandle);
            } else {
                deviceContext = WGL.wglGetCurrentDC();
                glContext = wglGetCurrentContext();
                if (glContext == NULL) {
                    throw new AtumVRException("No OpenGL context");
                }
            }
            return XrGraphicsBindingOpenGLWin32KHR.calloc(stack).set(
                    KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR,
                    NULL,
                    deviceContext,
                    glContext
            );
        } else if (Platform.get() == Platform.LINUX) {
            long xDisplay;
            long glXContext;
            long glXWindowHandle;
            int fbXID;
            if (windowHandle != NULL) {
                xDisplay = GLFWNativeX11.glfwGetX11Display();

                glXContext = GLFWNativeGLX.glfwGetGLXContext(windowHandle);
                glXWindowHandle = GLFWNativeGLX.glfwGetGLXWindow(windowHandle);
                if (xDisplay == NULL || glXContext == NULL) {
                    throw new AtumVRException("No GLX context (Wayland/EGL?), use the Vulkan graphics API or X11");
                }

                fbXID = glXQueryDrawable(xDisplay, glXWindowHandle, GLX_FBCONFIG_ID);
            } else {
                xDisplay = GLX12.glXGetCurrentDisplay();
                glXContext = GLX.glXGetCurrentContext();
                glXWindowHandle = GLX.glXGetCurrentDrawable();
                if (xDisplay == NULL || glXContext == NULL) {
                    throw new AtumVRException("No GLX context (Wayland/EGL?), use the Vulkan graphics API or X11");
                }

                IntBuffer fbConfigId = stack.mallocInt(1);
                glXQueryContext(xDisplay, glXContext, GLX_FBCONFIG_ID, fbConfigId);
                fbXID = fbConfigId.get(0);
            }
            PointerBuffer fbConfigBuf = glXChooseFBConfig(
                    xDisplay, X11.XDefaultScreen(xDisplay),
                    stackInts(GLX_FBCONFIG_ID, fbXID, 0)
            );
            if (fbConfigBuf == null) {
                throw new AtumVRException("Framebuffer config is null");
            }
            long fbConfig = fbConfigBuf.get();

            return XrGraphicsBindingOpenGLXlibKHR.calloc(stack).set(
                    KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_XLIB_KHR,
                    NULL,
                    xDisplay,
                    (int) Objects.requireNonNull(glXGetVisualFromFBConfig(xDisplay, fbConfig)).visualid(),
                    fbConfig,
                    glXWindowHandle,
                    glXContext
            );
        } else {
            throw new AtumVRException("MacOS not supported");
        }
    }
    public void destroy(){

    }

    private static long wglGetCurrentContext() {
        try {
            try {
                return (long) WGL.class.getMethod("wglGetCurrentContext").invoke(null);
            } catch (NoSuchMethodException e) {
                return (long) WGL.class.getMethod("wglGetCurrentContext", IntBuffer.class)
                        .invoke(null, (Object) null);
            }
        } catch (ReflectiveOperationException e) {
            throw new AtumVRException("Could not query OpenGL context", e);
        }
    }
}
