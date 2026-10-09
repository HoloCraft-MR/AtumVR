package me.phoenixra.atumvr.core.rendering;

import lombok.Getter;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderContext;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderer;
import me.phoenixra.atumvr.api.rendering.AtumVRTexture;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.api.enums.EyeType;
import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import me.phoenixra.atumvr.core.enums.XRGraphicsApi;
import me.phoenixra.atumvr.core.input.device.XRDeviceHMD;
import me.phoenixra.atumvr.api.utils.GLUtils;
import me.phoenixra.atumvr.core.session.vulkan.XRVulkanBridge;
import me.phoenixra.atumvr.core.utils.XRUtils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Locale;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;


/**
 * Abstract base class for XR rendering
 */
public abstract class XRRenderer implements AtumVRRenderer {

    @Getter
    protected XRProvider vrProvider;


    @Getter
    protected int resolutionWidth;

    @Getter
    protected int resolutionHeight;


    @Getter
    protected long windowHandle;



    /** Current swapChain image index per eye. */
    protected final int[] swapIndices = new int[2];

    /** FrameBuffers for left eye. */
    protected AtumVRTexture[] leftFramebuffers;

    /** FrameBuffers for right eye. */
    protected AtumVRTexture[] rightFramebuffers;

    /** Projection layer views for frame submission. Allocated once, reused every frame. */
    protected XrCompositionLayerProjectionView.Buffer projectionLayerViews;

    /** Previous frame's views, submitted instead of current ones when the
     * bridge staging tier delays content by one frame. */
    protected XrPosef.Buffer bridgePrevPoses;
    protected XrFovf.Buffer bridgePrevFovs;

    /** Number of projection layer views, one per eye. */
    protected static final int PROJECTION_LAYER_VIEWS = 2;

    /** If the runtime wants this frame rendered (false when the headset is off/idle). */
    protected boolean frameShouldRender;

    /**Hidden area mesh for stencil mask*/
    protected final HashMap<EyeType, float[]> hiddenArea = new HashMap<>();

    /**If created gl context with {@link #setupGLContext()}*/
    private boolean glContextCreated;


    /** SteamVR + Linux workaround on GL issue */
    private boolean steamVRLinuxWorkaround;
    private int lastSceneGLError;

    public XRRenderer(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;

    }

    // -------- SETTING UP --------

    @Override
    public abstract @NotNull XRScene getCurrentScene();

    /**
     * On renderer initialized
     *
     * @throws Throwable exception or error
     */
    protected abstract void onInit() throws Throwable;

    /**
     * Create VR texture for eye display
     *
     * @param width the requested width
     * @param height the requested height
     * @param textureId the requested texture id
     * @param index the requested texture index
     *
     * @return the VR texture instance
     */
    protected @NotNull XRTexture createTexture(int width, int height,
                                               int textureId,
                                               int index){
        return new XRTexture(width, height, textureId, index);
    }


    // -------- LIFECYCLE --------

    @Override
    public void init() throws Throwable{
        steamVRLinuxWorkaround = XRUtils.detectSteamVRLinux(vrProvider);

        if (projectionLayerViews == null) {
            projectionLayerViews = XrCompositionLayerProjectionView.calloc(PROJECTION_LAYER_VIEWS);
        }
        if (isVulkanBridge() && bridgePrevPoses == null) {
            bridgePrevPoses = XrPosef.calloc(PROJECTION_LAYER_VIEWS);
            bridgePrevFovs = XrFovf.calloc(PROJECTION_LAYER_VIEWS);
        }

        restoreGLContext();
        setupResolution();
        setupEyes();
        setupHiddenArea();
        onInit();
    }

    @Override
    public void prepareFrame() {
        prepareXrFrame();
    }

    @Override
    public void renderFrame(@NotNull AtumVRRenderContext context) {


        if(glContextCreated) {
            GL30.glViewport(0, 0, resolutionWidth, resolutionHeight);
            GL30.glEnable(GL30.GL_DEPTH_TEST);
        }

        if (frameShouldRender) {
            if (isVulkanBridge()) {
                vrProvider.getSession().getVulkanBridge().beginFrameGL();
            }
            getCurrentScene().render(context);
        }

        finishXrFrame();

        if(glContextCreated) {
            GL30.glFlush();
            GL30.glFinish();
        }
    }


    protected void prepareXrFrame(){
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameState frameState = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);

            vrProvider.checkXRError(
                    XR10.xrWaitFrame(
                            vrProvider.getSession().getHandle(),
                            XrFrameWaitInfo.calloc(stack)
                                    .type(XR10.XR_TYPE_FRAME_WAIT_INFO),
                            frameState
                    ),
                    "xrWaitFrame", ""
            );

            vrProvider.setXrDisplayTime(frameState.predictedDisplayTime());

            vrProvider.checkXRError(
                    XR10.xrBeginFrame(
                            vrProvider.getSession().getHandle(),
                            XrFrameBeginInfo.calloc(stack)
                                    .type(XR10.XR_TYPE_FRAME_BEGIN_INFO)
                    ),
                    "xrBeginFrame", ""
            );

            frameShouldRender = frameState.shouldRender();
            if (!frameShouldRender) {
                return;
            }

            XrViewState viewState = XrViewState.calloc(stack).type(XR10.XR_TYPE_VIEW_STATE);
            IntBuffer intBuf = stack.callocInt(1);

            XrViewLocateInfo viewLocateInfo = XrViewLocateInfo.calloc(stack);
            viewLocateInfo.set(
                    XR10.XR_TYPE_VIEW_LOCATE_INFO,
                    0,
                    XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                    vrProvider.getXrDisplayTime(),
                    vrProvider.getSession().getXrAppSpace()
            );

            vrProvider.checkXRError(
                    XR10.xrLocateViews(
                            vrProvider.getSession().getHandle(),
                            viewLocateInfo, viewState,
                            intBuf, vrProvider.getSession().getSwapChain().getXrViewBuffer()
                    ),
                    "xrLocateViews", ""
            );

            long requiredView = XR10.XR_VIEW_STATE_ORIENTATION_VALID_BIT
                    | XR10.XR_VIEW_STATE_POSITION_VALID_BIT;
            if ((viewState.viewStateFlags() & requiredView) != requiredView) {
                frameShouldRender = false;
                return;
            }
        }


        try (MemoryStack stack = MemoryStack.stackPush()) {

            IntBuffer intBuf2 = stack.callocInt(1);

            boolean bridge = isVulkanBridge();
            boolean delayedContent = bridge && vrProvider.getSession()
                    .getVulkanBridge().isContentOneFrameDelayed();

            for (EyeType eyeType : EyeType.values()) {
                int index = eyeType.getIndex();
                XrSwapchain xrSwapchain = vrProvider.getSession()
                        .getSwapChain().getHandle(index);

                vrProvider.checkXRError(
                        XR10.xrAcquireSwapchainImage(
                                xrSwapchain,
                                XrSwapchainImageAcquireInfo
                                        .calloc(stack)
                                        .type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO),
                                intBuf2
                        ),
                        "xrAcquireSwapchainImage", eyeType.name()
                );

                vrProvider.checkXRError(
                        XR10.xrWaitSwapchainImage(xrSwapchain,
                                XrSwapchainImageWaitInfo.calloc(stack)
                                        .type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO)
                                        .timeout(XR10.XR_INFINITE_DURATION)
                        ),
                        "xrWaitSwapchainImage", eyeType.name()
                );

                this.swapIndices[index] = intBuf2.get(0);

                XrView xrView = vrProvider.getInputHandler()
                        .getDevice(XRDeviceHMD.ID, XRDeviceHMD.class)
                        .getXrView(eyeType);
                var projectionView = this.projectionLayerViews.get(index)
                        .type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW);
                if (delayedContent) {
                    projectionView.pose(bridgePrevPoses.get(index))
                            .fov(bridgePrevFovs.get(index));
                } else {
                    projectionView.pose(xrView.pose())
                            .fov(xrView.fov());
                }
                if (bridge) {
                    bridgePrevPoses.get(index).set(xrView.pose());
                    bridgePrevFovs.get(index).set(xrView.fov());
                }
                XrSwapchainSubImage subImage = projectionView.subImage();
                subImage.swapchain(xrSwapchain);
                subImage.imageRect().offset().set(0, 0);
                subImage.imageRect().extent().set(resolutionWidth, resolutionHeight);
                subImage.imageArrayIndex(0);
            }

        }

        if (steamVRLinuxWorkaround) {
            restoreGLContext();
            GLUtils.drainGLErrors();
        }
    }

    protected void finishXrFrame(){
        if (steamVRLinuxWorkaround) {
            int sceneErr = GLUtils.drainGLErrors();
            if (sceneErr != lastSceneGLError) {
                lastSceneGLError = sceneErr;
                if (sceneErr != 0) {
                    vrProvider.getLogger().logError(
                            "OpenGL error generated by application/scene rendering: "
                                    + sceneErr + " (0x" + Integer.toHexString(sceneErr)
                                    + ") - this is an app-side bug, not the"
                                    + " SteamVR/Linux interop artifact"
                    );
                }
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameEndInfo frameEndInfo = XrFrameEndInfo.calloc(stack)
                    .type(XR10.XR_TYPE_FRAME_END_INFO)
                    .displayTime(vrProvider.getXrDisplayTime())
                    .environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);

            if (frameShouldRender) {
                if (isVulkanBridge()) {
                    //must be submitted before the images are released
                    vrProvider.getSession().getVulkanBridge().transferFrame(
                            swapIndices[EyeType.LEFT.getIndex()],
                            swapIndices[EyeType.RIGHT.getIndex()]
                    );
                }
                for (EyeType eyeType : EyeType.values()) {
                    vrProvider.checkXRError(
                            XR10.xrReleaseSwapchainImage(
                                    vrProvider.getSession().getSwapChain()
                                            .getHandle(eyeType.getIndex()),
                                    XrSwapchainImageReleaseInfo.calloc(stack)
                                            .type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)),
                            "xrReleaseSwapchainImage", eyeType.name()
                    );
                }

                PointerBuffer layers = stack.callocPointer(2);
                // passthrough goes under the projection layer
                long projectionFlags = vrProvider.preparePassthroughLayer(stack, layers);

                XrCompositionLayerProjection compositionLayerProjection = XrCompositionLayerProjection.calloc(stack)
                        .type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION)
                        .layerFlags(projectionFlags)
                        .space(vrProvider.getSession().getXrAppSpace())
                        .views(this.projectionLayerViews);

                layers.put(compositionLayerProjection);
                layers.flip();

                frameEndInfo.layers(layers);
            }

            vrProvider.checkXRError(
                    XR10.xrEndFrame(vrProvider.getSession().getHandle(), frameEndInfo),
                    "xrEndFrame", ""
            );
        }

        if (steamVRLinuxWorkaround) {
            restoreGLContext();
            GLUtils.drainGLErrors();
        }
    }



    protected void restoreGLContext() {
        if (steamVRLinuxWorkaround && windowHandle != 0L) {
            glfwMakeContextCurrent(windowHandle);
        }
    }


    /**
     * Setup OpenGL context for VR.
     * <p>
     *     Optional to use.<br>
     *     If you already have OpenGL context for your app,
     *     set its {@link #windowHandle} for the field instead
     * </p>
     */
    public void setupGLContext() {
        glContextCreated = true;
        GLFWErrorCallback.createPrint(System.out).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }


        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);

        windowHandle = glfwCreateWindow(640, 480, vrProvider.getAppName(), 0L, 0L);
        if (windowHandle == 0L) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(1);

        GL.createCapabilities();
        GL30.glEnable(GL30.GL_DEPTH_TEST);

        //texture staff, better be moved to a renderers of objects
        GL30.glEnable(GL30.GL_CULL_FACE);
        GL30.glCullFace(GL30.GL_BACK);

    }

    /**
     * Setup resolution for eye display
     */
    protected void setupResolution() {

        resolutionWidth = vrProvider.getSession().getSwapChain().getEyeWidth();
        resolutionHeight = vrProvider.getSession().getSwapChain().getEyeHeight();
    }


    /**
     * Setup Eye textures and swapChains
     */
    protected void setupEyes() {
        if (isVulkanBridge()) {
            setupEyesVulkanBridge();
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (EyeType eyeType : EyeType.values()) {
                int eyeIndex = eyeType.getIndex();
                XrSwapchain xrSwapchain = vrProvider.getSession()
                        .getSwapChain().getHandle(eyeIndex);

                IntBuffer intBuffer = stack.ints(0); //Set value to 0
                int error = XR10.xrEnumerateSwapchainImages(xrSwapchain, intBuffer, null);
                vrProvider.checkXRError(error, "xrEnumerateSwapchainImages", "get count");

                int imageCount = intBuffer.get(0);
                XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = vrProvider
                        .getSession().getSwapChain().createImageBuffers(imageCount,
                                stack);

                error = XR10.xrEnumerateSwapchainImages(xrSwapchain, intBuffer,
                        XrSwapchainImageBaseHeader.create(swapchainImageBuffer.address(), swapchainImageBuffer.capacity()));
                vrProvider.checkXRError(error, "xrEnumerateSwapchainImages", "get images");

                AtumVRTexture[] framebuffers = new AtumVRTexture[imageCount];
                for (int i = 0; i < imageCount; i++) {
                    XrSwapchainImageOpenGLKHR openxrImage = swapchainImageBuffer.get(i);
                    framebuffers[i] = createTexture(
                            resolutionWidth, resolutionHeight,
                            openxrImage.image(),
                            eyeIndex
                    ).init();
                    GLUtils.checkGLError(eyeType.name() + " " + i + " framebuffer setup");
                }
                if (eyeType == EyeType.LEFT) {
                    this.leftFramebuffers = framebuffers;
                } else {
                    this.rightFramebuffers = framebuffers;
                }
            }
        }

    }


    protected void setupEyesVulkanBridge() {
        XRVulkanBridge bridge = vrProvider.getSession().getVulkanBridge();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (EyeType eyeType : EyeType.values()) {
                int eyeIndex = eyeType.getIndex();
                XrSwapchain xrSwapchain = vrProvider.getSession()
                        .getSwapChain().getHandle(eyeIndex);

                IntBuffer intBuffer = stack.ints(0);
                int error = XR10.xrEnumerateSwapchainImages(xrSwapchain, intBuffer, null);
                vrProvider.checkXRError(error, "xrEnumerateSwapchainImages", "get count");

                int imageCount = intBuffer.get(0);
                XrSwapchainImageVulkanKHR.Buffer swapchainImageBuffer =
                        bridge.createImageBuffers(imageCount, stack);
                error = XR10.xrEnumerateSwapchainImages(xrSwapchain, intBuffer,
                        XrSwapchainImageBaseHeader.create(swapchainImageBuffer.address(), swapchainImageBuffer.capacity()));
                vrProvider.checkXRError(error, "xrEnumerateSwapchainImages", "get images");

                int textureId = bridge.setupEye(
                        eyeIndex, swapchainImageBuffer,
                        resolutionWidth, resolutionHeight
                );
                AtumVRTexture texture = createTexture(
                        resolutionWidth, resolutionHeight,
                        textureId,
                        eyeIndex
                ).init();
                GLUtils.checkGLError(eyeType.name() + " bridge framebuffer setup");

                if (eyeType == EyeType.LEFT) {
                    this.leftFramebuffers = new AtumVRTexture[]{texture};
                } else {
                    this.rightFramebuffers = new AtumVRTexture[]{texture};
                }
            }
        }
    }

    private boolean isVulkanBridge() {
        return vrProvider.getSession().getGraphicsApi() == XRGraphicsApi.VULKAN;
    }


    /**
     * Loads hidden area mesh from VR session
     */
    protected void setupHiddenArea(){
        try(MemoryStack stack = MemoryStack.stackPush()) {
            if (!getVrProvider().getSession().getInstance()
                    .getHandle().getCapabilities().XR_KHR_visibility_mask) {
                getVrProvider().getLogger().logInfo(
                        "XR_KHR_visibility_mask not supported by runtime, skipping hidden-area mesh"
                );
                return;
            }
            XrSession xrSession = getVrProvider().getSession().getHandle();
            for (int eye = 0; eye < 2; ++eye) {
                // 1) Allocate the mask struct
                XrVisibilityMaskKHR mask = XrVisibilityMaskKHR
                        .calloc(stack)
                        .type(KHRVisibilityMask.XR_TYPE_VISIBILITY_MASK_KHR)
                        .next(0);

                // 2) First call: get counts
                getVrProvider().checkXRError(
                        KHRVisibilityMask.xrGetVisibilityMaskKHR(
                                xrSession,
                                XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                eye,
                                KHRVisibilityMask.XR_VISIBILITY_MASK_TYPE_HIDDEN_TRIANGLE_MESH_KHR,
                                mask
                        ),
                        "xrGetVisibilityMaskKHR",
                        "query counts"
                );
                int vertCount  = mask.vertexCountOutput();
                int indexCount = mask.indexCountOutput();

                if (indexCount <= 0) {
                    getVrProvider().getLogger().logInfo("No hidden-area mesh found for eye " + eye);
                    continue;
                }

                // 3) Allocate buffers for the data
                XrVector2f.Buffer verts  = XrVector2f.calloc(vertCount, stack);
                IntBuffer          idxBuf = stack.mallocInt(indexCount);

                mask
                        .vertexCapacityInput(vertCount)
                        .indexCapacityInput(indexCount)
                        .vertices(verts)
                        .indices(idxBuf);

                // 4) Second call: actually fill verts & indices
                getVrProvider().checkXRError(
                        KHRVisibilityMask.xrGetVisibilityMaskKHR(
                                xrSession,
                                XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                eye,
                                KHRVisibilityMask.XR_VISIBILITY_MASK_TYPE_HIDDEN_TRIANGLE_MESH_KHR,
                                mask
                        ),
                        "xrGetVisibilityMaskKHR",
                        "retrieve mesh"
                );

                // 5) Flatten into your float[] format (tri-list: x,y,x,y,…)
                float[] area = new float[indexCount * 2];
                for (int i = 0; i < indexCount; i++) {
                    XrVector2f v = verts.get(idxBuf.get(i));
                    // If your runtime gives coords in [-1..1], map them to [0..1]:
                    float ux = (v.x() * 0.5f) + 0.5f;
                    float uy = (v.y() * 0.5f) + 0.5f;
                    // then to pixels:
                    area[i*2    ] = ux * getResolutionWidth();
                    area[i*2 + 1] = uy * getResolutionHeight();
                }

                hiddenArea.put(EyeType.fromIndex(eye), area);
                getVrProvider().getLogger().logInfo("Hidden-area mesh loaded for eye " + eye);
            }
        } catch (Throwable e) {
            hiddenArea.clear();
            getVrProvider().getLogger().logError(
                    "Failed to load hidden-area mesh, continuing without it: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }


    // -------- API --------

    @Override
    public @NotNull AtumVRTexture getTextureLeftEye() {
        if(leftFramebuffers==null){
            throw new AtumVRException("Tried to get left eye texture before textures initialized");
        }
        return leftFramebuffers[isVulkanBridge() ? 0 : swapIndices[EyeType.LEFT.getIndex()]];
    }


    @Override
    public @NotNull AtumVRTexture getTextureRightEye() {
        if(rightFramebuffers==null){
            throw new AtumVRException("Tried to get right eye texture before textures initialized");
        }
        return rightFramebuffers[isVulkanBridge() ? 0 : swapIndices[EyeType.RIGHT.getIndex()]];
    }

    @Override
    public float[] getHiddenAreaVertices(@NotNull EyeType eyeType) {
        return hiddenArea.get(eyeType);
    }



    // -------- DESTROY --------

    public void destroy() {
        getCurrentScene().destroy();
        if (projectionLayerViews != null) {
            projectionLayerViews.close();
            projectionLayerViews = null;
        }
        if (bridgePrevPoses != null) {
            bridgePrevPoses.close();
            bridgePrevFovs.close();
            bridgePrevPoses = null;
            bridgePrevFovs = null;
        }
        if(glContextCreated) {
            glfwFreeCallbacks(windowHandle);
            glfwDestroyWindow(windowHandle);

            glfwTerminate();
        }
    }

}
