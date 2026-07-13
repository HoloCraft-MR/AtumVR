package me.phoenixra.atumvr.core.session.platform;

import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLESAndroidKHR;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLWin32KHR;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLXlibKHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLESKHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInstanceCreateInfoAndroidKHR;
import org.lwjgl.openxr.XrLoaderInitInfoAndroidKHR;
import org.lwjgl.openxr.XrLoaderInitInfoBaseHeaderKHR;
import org.lwjgl.openxr.XrSwapchainImageOpenGLKHR;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeGLX;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.opengl.GLX;
import org.lwjgl.opengl.GLX12;
import org.lwjgl.opengl.WGL;
import org.lwjgl.openxr.KHRAndroidCreateInstance;
import org.lwjgl.openxr.KHRLoaderInit;
import org.lwjgl.openxr.KHRLoaderInitAndroid;
import org.lwjgl.openxr.KHROpenGLEnable;
import org.lwjgl.openxr.KHROpenGLESEnable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Struct;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.windows.User32;

import java.nio.IntBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GLX13.GLX_FBCONFIG_ID;
import static org.lwjgl.opengl.GLX13.glXChooseFBConfig;
import static org.lwjgl.opengl.GLX13.glXGetVisualFromFBConfig;
import static org.lwjgl.opengl.GLX13.glXQueryContext;
import static org.lwjgl.opengl.GLX13.glXQueryDrawable;
import static org.lwjgl.system.MemoryStack.stackInts;
import static org.lwjgl.system.MemoryUtil.NULL;

public interface XRPlatform {
    void initializeLoader(MemoryStack stack);
    long getInstanceCreateInfo(MemoryStack stack);
    String getGraphicsExtension();
    Struct<?> createGraphicsBinding(MemoryStack stack, XrInstance instance, long systemId, long windowHandle);
    XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack);

    static XRPlatform get() {
        return Holder.platform;
    }

    static void setAndroidBridge(AndroidXRBridge bridge) {
        Holder.bridge = bridge;
        if (isAndroid()) {
            Holder.platform = new Android(bridge);
        }
    }

    static boolean isAndroid() {
        return System.getProperty("os.version", "").contains("Android");
    }

    class Holder {
        private static volatile AndroidXRBridge bridge;
        private static volatile XRPlatform platform = new Desktop();
    }

    class Desktop implements XRPlatform {
        @Override
        public void initializeLoader(MemoryStack stack) {
        }

        @Override
        public long getInstanceCreateInfo(MemoryStack stack) {
            return NULL;
        }

        @Override
        public String getGraphicsExtension() {
            return KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME;
        }

        @Override
        public Struct<?> createGraphicsBinding(MemoryStack stack, XrInstance instance, long systemId, long windowHandle) {
            XrGraphicsRequirementsOpenGLKHR requirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack)
                    .type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
            KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(instance, systemId, requirements);

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
            }

            if (Platform.get() == Platform.LINUX) {
                long display;
                long context;
                long drawable;
                int fbConfigId;
                if (windowHandle != NULL) {
                    display = GLFWNativeX11.glfwGetX11Display();
                    context = GLFWNativeGLX.glfwGetGLXContext(windowHandle);
                    drawable = GLFWNativeGLX.glfwGetGLXWindow(windowHandle);
                    if (display == NULL || context == NULL) {
                        throw new AtumVRException("No GLX context (Wayland/EGL?), use the Vulkan graphics API or X11");
                    }
                    fbConfigId = glXQueryDrawable(display, drawable, GLX_FBCONFIG_ID);
                } else {
                    display = GLX12.glXGetCurrentDisplay();
                    context = GLX.glXGetCurrentContext();
                    drawable = GLX.glXGetCurrentDrawable();
                    if (display == NULL || context == NULL) {
                        throw new AtumVRException("No GLX context (Wayland/EGL?), use the Vulkan graphics API or X11");
                    }
                    IntBuffer fbConfigIdBuf = stack.mallocInt(1);
                    glXQueryContext(display, context, GLX_FBCONFIG_ID, fbConfigIdBuf);
                    fbConfigId = fbConfigIdBuf.get(0);
                }
                PointerBuffer configs = glXChooseFBConfig(display, X11.XDefaultScreen(display), stackInts(GLX_FBCONFIG_ID, fbConfigId, 0));
                if (configs == null) {
                    throw new AtumVRException("Framebuffer config is null");
                }
                long config = configs.get();
                return XrGraphicsBindingOpenGLXlibKHR.calloc(stack).set(
                        KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_XLIB_KHR,
                        NULL,
                        display,
                        (int) Objects.requireNonNull(glXGetVisualFromFBConfig(display, config)).visualid(),
                        config,
                        drawable,
                        context
                );
            }

            throw new AtumVRException("MacOS not supported");
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

        @Override
        public XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
            XrSwapchainImageOpenGLKHR.Buffer images = XrSwapchainImageOpenGLKHR.calloc(imageCount, stack);
            for (XrSwapchainImageOpenGLKHR image : images) {
                image.type(KHROpenGLEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR);
            }
            return images;
        }
    }

    class Android implements XRPlatform {
        private final AndroidXRBridge bridge;

        Android(AndroidXRBridge bridge) {
            if (bridge == null) {
                throw new IllegalStateException("Android OpenXR bridge is not configured");
            }
            this.bridge = bridge;
        }

        @Override
        public void initializeLoader(MemoryStack stack) {
            bridge.setupAndroid();
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            XrLoaderInitInfoAndroidKHR info = XrLoaderInitInfoAndroidKHR.calloc(stack).set(
                    KHRLoaderInitAndroid.XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR,
                    NULL,
                    bridge.getDalvikVM(),
                    bridge.getDalvikActivity()
            );
            KHRLoaderInit.xrInitializeLoaderKHR(XrLoaderInitInfoBaseHeaderKHR.create(info.address()));
        }

        @Override
        public long getInstanceCreateInfo(MemoryStack stack) {
            return XrInstanceCreateInfoAndroidKHR.calloc(stack).set(
                    KHRAndroidCreateInstance.XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR,
                    NULL,
                    bridge.getDalvikVM(),
                    bridge.getDalvikActivity()
            ).address();
        }

        @Override
        public String getGraphicsExtension() {
            return KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
        }

        @Override
        public Struct<?> createGraphicsBinding(MemoryStack stack, XrInstance instance, long systemId, long windowHandle) {
            XrGraphicsRequirementsOpenGLESKHR requirements = XrGraphicsRequirementsOpenGLESKHR.calloc(stack)
                    .type(KHROpenGLESEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR);
            KHROpenGLESEnable.xrGetOpenGLESGraphicsRequirementsKHR(instance, systemId, requirements);
            return XrGraphicsBindingOpenGLESAndroidKHR.calloc(stack).set(
                    KHROpenGLESEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
                    NULL,
                    bridge.getEGLDisplay(),
                    bridge.getEGLConfig(),
                    bridge.getEGLContext()
            );
        }

        @Override
        public XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
            XrSwapchainImageOpenGLKHR.Buffer images = XrSwapchainImageOpenGLKHR.calloc(imageCount, stack);
            for (XrSwapchainImageOpenGLKHR image : images) {
                image.type(KHROpenGLESEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR);
            }
            return images;
        }
    }
}
