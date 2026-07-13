package me.phoenixra.atumvr.core.session;

import lombok.Getter;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.enums.XRGraphicsApi;
import me.phoenixra.atumvr.core.session.platform.XRPlatform;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * XR session SwapChain (low-level OpenXR stuff)
 */
public class XRSwapChain {


    private static final int VIEW_TYPE = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;


    private final XRProvider vrProvider;

    private final List<Integer> desiredSwapChainFormats;



    private XrSwapchain[] handles;

    @Getter
    private XrView.Buffer xrViewBuffer;

    @Getter
    private int eyeWidth, eyeHeight;

    public XRSwapChain(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
        desiredSwapChainFormats = vrProvider.getSwapChainFormats();
    }



    public void init() {

        try (MemoryStack stack = MemoryStack.stackPush()) {

            XrInstance    instance = vrProvider.getSession().getInstance().getHandle();
            XrSession     session  = vrProvider.getSession().getHandle();
            long          systemId = vrProvider.getSession().getSystem().getSystemId();

            int viewCount = enumerateViewCount(instance, systemId, stack);

            var viewConfigs = enumerateViewConfigs(instance, systemId, viewCount, stack);

            long chosenFormat = pickSwapchainFormat(session, stack);

            this.handles = new XrSwapchain[viewCount];
            for (int i = 0; i < viewCount; i++) {
                handles[i] = createSwapchain(session, viewConfigs.get(i), chosenFormat, stack);
            }
            this.eyeWidth = viewConfigs.get(0).recommendedImageRectWidth();
            this.eyeHeight = viewConfigs.get(0).recommendedImageRectHeight();

            // persistent view buffer on heap
            this.xrViewBuffer = XrView.calloc(viewCount);
            for (int i = 0; i < viewCount; i++) {
                xrViewBuffer.get(i).type(XR_TYPE_VIEW).next(NULL);
            }

            vrProvider.getLogger().logInfo(String.format("Swapchains created: %s × %s×%s pixels, format 0x%s",
                    viewCount, eyeWidth, eyeHeight, Long.toHexString(chosenFormat))
            );
        }

    }

    /**
     * Get swapchain handle of the specified eye
     *
     * @param eyeIndex the eye index (0 - left, 1 - right)
     * @return the swapchain handle
     */
    public XrSwapchain getHandle(int eyeIndex) {
        return handles[eyeIndex];
    }

    private int enumerateViewCount(XrInstance instance, long systemId, MemoryStack stack) {
        IntBuffer countBuf = stack.callocInt(1);
        vrProvider.checkXRError(
                xrEnumerateViewConfigurationViews(
                        instance, systemId, VIEW_TYPE, countBuf, null
                ),
                "xrEnumerateViewConfigurationViews",
                "count"
        );
        return countBuf.get(0);
    }

    private XrViewConfigurationView.Buffer enumerateViewConfigs(
            XrInstance instance,
            long systemId,
            int viewCount,
            MemoryStack stack)
    {
        XrViewConfigurationView.Buffer configs = XrViewConfigurationView.calloc(viewCount, stack);
        for (int i = 0; i < viewCount; i++) {
            configs.get(i).type(XR_TYPE_VIEW_CONFIGURATION_VIEW).next(NULL);
        }
        IntBuffer countBuf = stack.ints(viewCount);
        int err = xrEnumerateViewConfigurationViews(instance, systemId, VIEW_TYPE, countBuf, configs);
        vrProvider.checkXRError(err, "xrEnumerateViewConfigurationViews", "views");
        return configs;
    }

    private long pickSwapchainFormat(XrSession session, MemoryStack stack) {
        IntBuffer fmtCount = stack.callocInt(1);
        vrProvider.checkXRError(
                xrEnumerateSwapchainFormats(session, fmtCount, null),
                "xrEnumerateSwapchainFormats", "count"
        );

        LongBuffer available = stack.callocLong(fmtCount.get(0));
        vrProvider.checkXRError(
                xrEnumerateSwapchainFormats(session, fmtCount, available),
                "xrEnumerateSwapchainFormats", "values"
        );

        if (vrProvider.getSession().getGraphicsApi() == XRGraphicsApi.VULKAN) {
            return vrProvider.getSession().getVulkanBridge().pickSwapchainFormat(available);
        }

        for (long want : desiredSwapChainFormats) {
            for (int i = 0; i < available.capacity(); i++) {
                if (available.get(i) == want) {
                    vrProvider.getLogger().logDebug(String.format(
                            "Selected swapchain format: 0x%s", Long.toHexString(want)
                    ));
                    return want;
                }
            }
        }

        throw new IllegalStateException("No compatible swapchain format found: " + available);
    }

    private XrSwapchain createSwapchain(
            XrSession session,
            XrViewConfigurationView viewConfig,
            long format,
            MemoryStack stack)
    {
        long usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
        if (vrProvider.getSession().getGraphicsApi() == XRGraphicsApi.VULKAN) {
            //the bridge copies into the swapchain image instead of rendering to it
            usageFlags |= XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT;
        }
        XrSwapchainCreateInfo info = XrSwapchainCreateInfo.calloc(stack)
                .type(XR_TYPE_SWAPCHAIN_CREATE_INFO)
                .next(NULL)
                .usageFlags(usageFlags)
                .format(format)
                .sampleCount(1)
                .width(viewConfig.recommendedImageRectWidth())
                .height(viewConfig.recommendedImageRectHeight())
                .faceCount(1)
                .arraySize(1)
                .mipCount(1);

        PointerBuffer handlePtr = stack.callocPointer(1);
        vrProvider.checkXRError(
                xrCreateSwapchain(session, info, handlePtr),
                "xrCreateSwapchain", "format: "+Long.toHexString(format)
        );
        return new XrSwapchain(handlePtr.get(0), session);
    }



    public XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
        return XRPlatform.get().createImageBuffers(imageCount, stack);
    }



    public void destroy() {
        destroySwapchainQuietly();
        destroyViewBuffer();
    }

    private void destroySwapchainQuietly() {
        if (handles == null) return;
        for (int i = 0; i < handles.length; i++) {
            if (handles[i] == null) continue;
            int err = xrDestroySwapchain(handles[i]);
            vrProvider.checkXRError(false, err, "xrDestroySwapchain", "eye " + i);
            handles[i] = null;
        }
        handles = null;
        vrProvider.getLogger().logDebug("Swapchains destroyed");
    }

    private void destroyViewBuffer() {
        if (xrViewBuffer != null) {
            xrViewBuffer.close();
            xrViewBuffer = null;
            vrProvider.getLogger().logDebug("View buffer destroyed");
        }
    }
}
