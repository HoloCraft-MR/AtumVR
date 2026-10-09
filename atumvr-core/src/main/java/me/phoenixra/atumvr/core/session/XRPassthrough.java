package me.phoenixra.atumvr.core.session;

import lombok.Getter;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

/**
 * XR_FB_passthrough handler (low-level OpenXR stuff)
 * <p>
 *     Creates a passthrough feature + a RECONSTRUCTION layer (both paused),
 *     then starts/pauses them according to the requested state.
 *     While running, the passthrough layer is submitted under the projection
 *     layer, and the projection layer blends using the eye textures alpha.
 * </p>
 * <p>
 *     All methods must be called from the render thread
 * </p>
 */
public class XRPassthrough {
    private final XRProvider vrProvider;

    private XrPassthroughFB passthrough;
    private XrPassthroughLayerFB layer;

    /** If passthrough feature and layer were created successfully */
    @Getter
    private boolean supported;

    /** If passthrough is currently started */
    @Getter
    private boolean running;

    public XRPassthrough(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }

    public void init(@NotNull XrSession session) {
        if (!session.getCapabilities().XR_FB_passthrough) {
            vrProvider.getLogger().logInfo("XR_FB_passthrough is not supported by runtime, mixed reality unavailable");
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pb = stack.callocPointer(1);
            int result = FBPassthrough.xrCreatePassthroughFB(
                    session,
                    XrPassthroughCreateInfoFB.calloc(stack)
                            .type(FBPassthrough.XR_TYPE_PASSTHROUGH_CREATE_INFO_FB)
                            .flags(0),
                    pb
            );
            if (result < 0) {
                vrProvider.getLogger().logError(
                        "xrCreatePassthroughFB failed: " + vrProvider.getXRActionResult(result)
                );
                return;
            }
            passthrough = new XrPassthroughFB(pb.get(0), session);

            result = FBPassthrough.xrCreatePassthroughLayerFB(
                    session,
                    XrPassthroughLayerCreateInfoFB.calloc(stack)
                            .type(FBPassthrough.XR_TYPE_PASSTHROUGH_LAYER_CREATE_INFO_FB)
                            .passthrough(passthrough)
                            .flags(0)
                            .purpose(FBPassthrough.XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB),
                    pb
            );
            if (result < 0) {
                vrProvider.getLogger().logError(
                        "xrCreatePassthroughLayerFB failed: " + vrProvider.getXRActionResult(result)
                );
                FBPassthrough.xrDestroyPassthroughFB(passthrough);
                passthrough = null;
                return;
            }
            layer = new XrPassthroughLayerFB(pb.get(0), session);
            supported = true;
            vrProvider.getLogger().logInfo("Passthrough created (paused)");
        }
    }

    /**
     * Start or pause passthrough to match the requested state
     *
     * @param requested if passthrough should be running
     */
    public void sync(boolean requested) {
        if (!supported || requested == running) {
            return;
        }
        if (requested) {
            int result = FBPassthrough.xrPassthroughStartFB(passthrough);
            if (result < 0) {
                vrProvider.getLogger().logError(
                        "xrPassthroughStartFB failed: " + vrProvider.getXRActionResult(result)
                );
                // don't retry every frame
                supported = false;
                return;
            }
            vrProvider.checkXRError(false,
                    FBPassthrough.xrPassthroughLayerResumeFB(layer),
                    "xrPassthroughLayerResumeFB"
            );
            running = true;
            vrProvider.getLogger().logInfo("Passthrough started");
        } else {
            vrProvider.checkXRError(false,
                    FBPassthrough.xrPassthroughLayerPauseFB(layer),
                    "xrPassthroughLayerPauseFB"
            );
            vrProvider.checkXRError(false,
                    FBPassthrough.xrPassthroughPauseFB(passthrough),
                    "xrPassthroughPauseFB"
            );
            running = false;
            vrProvider.getLogger().logInfo("Passthrough paused");
        }
    }

    /**
     * Put passthrough composition layer to the layers buffer, if running.
     * <p>
     *     Must be called before putting the projection layer,
     *     so passthrough is composited under it
     * </p>
     *
     * @param stack the memory stack, valid until xrEndFrame
     * @param layers the layers buffer
     */
    public void putLayer(@NotNull MemoryStack stack, @NotNull PointerBuffer layers) {
        if (!running) {
            return;
        }
        layers.put(
                XrCompositionLayerPassthroughFB.calloc(stack)
                        .type(FBPassthrough.XR_TYPE_COMPOSITION_LAYER_PASSTHROUGH_FB)
                        .flags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                        .layerHandle(layer)
        );
    }

    /**
     * Get flags for the projection layer.
     * <p>
     *     When passthrough is running, the projection layer is blended
     *     using its alpha. Alpha is treated as premultiplied (no UNPREMULTIPLIED bit),
     *     since translucent content alpha-blended over a cleared (0,0,0,0) target
     *     produces premultiplied color.
     * </p>
     *
     * @return the layer flags
     */
    public long getProjectionLayerFlags() {
        return running
                ? XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT
                : 0;
    }

    public void destroy() {
        if (layer != null) {
            vrProvider.checkXRError(false,
                    FBPassthrough.xrDestroyPassthroughLayerFB(layer),
                    "xrDestroyPassthroughLayerFB"
            );
            layer = null;
        }
        if (passthrough != null) {
            vrProvider.checkXRError(false,
                    FBPassthrough.xrDestroyPassthroughFB(passthrough),
                    "xrDestroyPassthroughFB"
            );
            passthrough = null;
        }
        supported = false;
        running = false;
    }
}
