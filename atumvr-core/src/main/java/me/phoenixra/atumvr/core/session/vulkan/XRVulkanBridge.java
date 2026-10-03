package me.phoenixra.atumvr.core.session.vulkan;

import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import me.phoenixra.atumvr.api.utils.GLUtils;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreFD;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.openxr.KHRVulkanEnable2;
import org.lwjgl.openxr.XrGraphicsRequirementsVulkan2KHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.openxr.XrVulkanDeviceCreateInfoKHR;
import org.lwjgl.openxr.XrVulkanGraphicsDeviceGetInfoKHR;
import org.lwjgl.openxr.XrVulkanInstanceCreateInfoKHR;
import org.lwjgl.openxr.XrGraphicsBindingVulkan2KHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Struct;
import org.lwjgl.vulkan.KHRExternalMemoryFd;
import org.lwjgl.vulkan.KHRExternalMemoryWin32;
import org.lwjgl.vulkan.KHRExternalSemaphoreFd;
import org.lwjgl.vulkan.KHRExternalSemaphoreWin32;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExportMemoryAllocateInfo;
import org.lwjgl.vulkan.VkExportSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageMemoryRequirementsInfo2;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedRequirements;
import org.lwjgl.vulkan.VkMemoryGetFdInfoKHR;
import org.lwjgl.vulkan.VkMemoryGetWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkMemoryRequirements2;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreGetFdInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreGetWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.vkGetImageMemoryRequirements2;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2;


public class XRVulkanBridge {

    private static final int RING_SIZE = 2;
    private static final long GPU_TIMEOUT_NS = 1_000_000_000L;
    private static final boolean FORCE_CPU_STAGING = Boolean.getBoolean("atumvr.vulkanBridge.forceCpuStaging");

    private enum Tier { ZERO_COPY, CPU_STAGING }

    private enum Interop {
        WIN32(KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME,
                KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME,
                VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT,
                VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT),
        OPAQUE_FD(KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
                KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME,
                VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT,
                VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);

        private final String memoryExtension;
        private final String semaphoreExtension;
        private final int memoryHandleType;
        private final int semaphoreHandleType;

        Interop(String memoryExtension, String semaphoreExtension, int memoryHandleType, int semaphoreHandleType) {
            this.memoryExtension = memoryExtension;
            this.semaphoreExtension = semaphoreExtension;
            this.memoryHandleType = memoryHandleType;
            this.semaphoreHandleType = semaphoreHandleType;
        }
    }

    private final XRProvider vrProvider;

    private long pfnVkGetInstanceProcAddr;

    private VkInstance vkInstance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private int queueFamilyIndex = -1;
    private Interop interop;

    private long commandPool;
    private final VkCommandBuffer[] ringCommandBuffers = new VkCommandBuffer[RING_SIZE];
    private final long[] ringFences = new long[RING_SIZE];
    private final boolean[] ringSubmitted = new boolean[RING_SIZE];
    private int ringCursor;

    private Tier tier;
    private int vkFormat;
    private int glInternalFormat;
    private int width, height;
    private long eyeByteSize;

    private final long[][] swapchainImages = new long[2][];
    private final int[] glTextures = new int[2];

    // ZERO_COPY
    private final long[] sharedImages = new long[2];
    private final long[] sharedMemory = new long[2];
    private final int[] glMemoryObjects = new int[2];
    private long vkSemGlDone, vkSemVkDone;
    private int glSemGlDone, glSemVkDone;
    private boolean pendingGlWait;
    private IntBuffer glSemNoBuffers;
    private IntBuffer glSemTextures;
    private IntBuffer glSemLayouts;

    // CPU_STAGING
    private final int[][] pbos = new int[2][2];
    private final long[][] pboFences = new long[2][2];
    private int pboCursor;
    private boolean hasPreviousReadback;
    private long stagingBuffer;
    private long stagingMemory;
    private long stagingPtr;

    public XRVulkanBridge(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }


    // -------- SESSION SETUP --------


    public Struct<?> createGraphicsBinding(MemoryStack stack,
                                           XrInstance xrInstance,
                                           long systemId) {
        initVulkanLoader();

        var requirements = XrGraphicsRequirementsVulkan2KHR.calloc(stack).type$Default();
        vrProvider.checkXRError(
                KHRVulkanEnable2.xrGetVulkanGraphicsRequirements2KHR(xrInstance, systemId, requirements),
                "xrGetVulkanGraphicsRequirements2KHR"
        );
        int apiVersion = resolveApiVersion(requirements);

        PointerBuffer pointerBuf = stack.callocPointer(1);
        IntBuffer vkResultBuf = stack.callocInt(1);

        // VkInstance, created by the runtime itself
        var appInfo = VkApplicationInfo.calloc(stack)
                .sType$Default()
                .pApplicationName(stack.UTF8(vrProvider.getAppName()))
                .applicationVersion(1)
                .pEngineName(stack.UTF8("AtumEngine"))
                .engineVersion(1)
                .apiVersion(apiVersion);
        var instanceInfo = VkInstanceCreateInfo.calloc(stack)
                .sType$Default()
                .pApplicationInfo(appInfo);
        var xrInstanceInfo = XrVulkanInstanceCreateInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .pfnGetInstanceProcAddr(pfnVkGetInstanceProcAddr)
                .vulkanCreateInfo(instanceInfo);
        vrProvider.checkXRError(
                KHRVulkanEnable2.xrCreateVulkanInstanceKHR(xrInstance, xrInstanceInfo, pointerBuf, vkResultBuf),
                "xrCreateVulkanInstanceKHR"
        );
        checkVk(vkResultBuf.get(0), "vkCreateInstance through the runtime");
        vkInstance = new VkInstance(pointerBuf.get(0), instanceInfo);

        // physical device the runtime renders on
        var deviceGetInfo = XrVulkanGraphicsDeviceGetInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .vulkanInstance(vkInstance);
        vrProvider.checkXRError(
                KHRVulkanEnable2.xrGetVulkanGraphicsDevice2KHR(xrInstance, deviceGetInfo, pointerBuf),
                "xrGetVulkanGraphicsDevice2KHR"
        );
        physicalDevice = new VkPhysicalDevice(pointerBuf.get(0), vkInstance);

        queueFamilyIndex = findGraphicsQueueFamily(stack);
        interop = resolveInterop();

        // VkDevice, created by the runtime itself
        var queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
        queueInfo.get(0)
                .sType$Default()
                .queueFamilyIndex(queueFamilyIndex)
                .pQueuePriorities(stack.floats(1.0f));
        var deviceInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueInfo);
        if (interop != null) {
            deviceInfo.ppEnabledExtensionNames(stack.pointers(
                    stack.UTF8(interop.memoryExtension),
                    stack.UTF8(interop.semaphoreExtension)
            ));
        }
        var xrDeviceInfo = XrVulkanDeviceCreateInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .pfnGetInstanceProcAddr(pfnVkGetInstanceProcAddr)
                .vulkanPhysicalDevice(physicalDevice)
                .vulkanCreateInfo(deviceInfo);
        vrProvider.checkXRError(
                KHRVulkanEnable2.xrCreateVulkanDeviceKHR(xrInstance, xrDeviceInfo, pointerBuf, vkResultBuf),
                "xrCreateVulkanDeviceKHR"
        );
        checkVk(vkResultBuf.get(0), "vkCreateDevice through the runtime");
        device = new VkDevice(pointerBuf.get(0), physicalDevice, deviceInfo);

        vkGetDeviceQueue(device, queueFamilyIndex, 0, pointerBuf);
        queue = new VkQueue(pointerBuf.get(0), device);

        createCommandRing(stack);

        vrProvider.getLogger().logInfo(
                "Vulkan bridge device ready (queue family " + queueFamilyIndex
                        + ", external interop " + (interop != null ? interop : "unavailable") + ")"
        );

        return XrGraphicsBindingVulkan2KHR.calloc(stack)
                .type$Default()
                .instance(vkInstance)
                .physicalDevice(physicalDevice)
                .device(device)
                .queueFamilyIndex(queueFamilyIndex)
                .queueIndex(0);
    }


    public long pickSwapchainFormat(@NotNull LongBuffer runtimeFormats) {
        List<Integer> skipped = new ArrayList<>();
        for (int glFormat : vrProvider.getSwapChainFormats()) {
            int candidate = mapGlToVkFormat(glFormat);
            if (candidate == -1) {
                skipped.add(glFormat);
                continue;
            }
            for (int i = 0; i < runtimeFormats.capacity(); i++) {
                if (runtimeFormats.get(i) == candidate) {
                    this.vkFormat = candidate;
                    this.glInternalFormat = glFormat;
                    if (!skipped.isEmpty()) {
                        vrProvider.getLogger().logDebug(
                                "Swapchain formats without a bridge mapping, skipped: " + skipped
                        );
                    }
                    return candidate;
                }
            }
        }
        StringBuilder available = new StringBuilder();
        for (int i = 0; i < runtimeFormats.capacity(); i++) {
            available.append(i == 0 ? "" : ", ").append(runtimeFormats.get(i));
        }
        throw new AtumVRException(
                "No bridge-compatible Vulkan swapchain format; runtime offers: [" + available + "]"
        );
    }

    public XrSwapchainImageVulkanKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
        var buffer = XrSwapchainImageVulkanKHR.calloc(imageCount, stack);
        for (XrSwapchainImageVulkanKHR image : buffer) {
            image.type$Default();
        }
        return buffer;
    }


    public int setupEye(int eyeIndex,
                        @NotNull XrSwapchainImageVulkanKHR.Buffer images,
                        int width, int height) {
        long[] handles = new long[images.capacity()];
        for (int i = 0; i < handles.length; i++) {
            handles[i] = images.get(i).image();
        }
        swapchainImages[eyeIndex] = handles;

        if (tier == null) {
            this.width = width;
            this.height = height;
            this.eyeByteSize = (long) width * height * 4L;
            tier = decideTier();
            if (tier == Tier.ZERO_COPY && !createZeroCopyTargets()) {
                tier = Tier.CPU_STAGING;
            }
            if (tier == Tier.CPU_STAGING) {
                createStagingBuffer();
            }
        } else if (width != this.width || height != this.height) {
            throw new AtumVRException("Vulkan bridge expects equal eye resolutions, got "
                    + width + "x" + height + " vs " + this.width + "x" + this.height);
        }

        if (tier == Tier.CPU_STAGING) {
            glTextures[eyeIndex] = createStagingEyeTexture(eyeIndex);
        }
        return glTextures[eyeIndex];
    }


    // -------- FRAME --------


    public void beginFrameGL() {
        if (tier == Tier.ZERO_COPY && pendingGlWait) {
            EXTSemaphore.glWaitSemaphoreEXT(glSemVkDone, glSemNoBuffers, glSemTextures, glSemLayouts);
            pendingGlWait = false;
        }
    }

    public boolean isContentOneFrameDelayed() {
        return tier == Tier.CPU_STAGING && hasPreviousReadback;
    }


    public void transferFrame(int leftImageIndex, int rightImageIndex) {
        if (tier == Tier.ZERO_COPY) {
            transferZeroCopy(leftImageIndex, rightImageIndex);
        } else {
            transferCpuStaging(leftImageIndex, rightImageIndex);
        }
    }

    private void transferZeroCopy(int leftImageIndex, int rightImageIndex) {
        EXTSemaphore.glSignalSemaphoreEXT(glSemGlDone, glSemNoBuffers, glSemTextures, glSemLayouts);
        GL11.glFlush();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int slot = ringCursor;
            VkCommandBuffer commandBuffer = beginRingCommandBuffer(slot, stack);

            if (interop == Interop.OPAQUE_FD) {
                recordSharedOwnershipBarriers(commandBuffer, stack, true);
            }
            recordAcquireBarriers(commandBuffer, stack, leftImageIndex, rightImageIndex);

            // blit instead of copy: flips Y (GL bottom-up -> Vulkan top-down)
            var region = VkImageBlit.calloc(1, stack);
            region.get(0)
                    .srcSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .dstSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1));
            region.get(0).srcOffsets(0).set(0, height, 0);
            region.get(0).srcOffsets(1).set(width, 0, 1);
            region.get(0).dstOffsets(0).set(0, 0, 0);
            region.get(0).dstOffsets(1).set(width, height, 1);
            vkCmdBlitImage(commandBuffer,
                    sharedImages[0], VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    swapchainImages[0][leftImageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region, VK_FILTER_NEAREST);
            vkCmdBlitImage(commandBuffer,
                    sharedImages[1], VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    swapchainImages[1][rightImageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region, VK_FILTER_NEAREST);

            if (interop == Interop.OPAQUE_FD) {
                recordSharedOwnershipBarriers(commandBuffer, stack, false);
            }
            recordReleaseBarriers(commandBuffer, stack, leftImageIndex, rightImageIndex);
            checkVk(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(bridge)");

            var submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(vkSemGlDone))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .pSignalSemaphores(stack.longs(vkSemVkDone));
            submitRing(slot, submit);
        }
        pendingGlWait = true;
    }

    private void transferCpuStaging(int leftImageIndex, int rightImageIndex) {
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int prevRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int prevSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int prevSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);

            int cur = pboCursor;
            for (int eye = 0; eye < 2; eye++) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextures[eye]);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[eye][cur]);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                if (pboFences[eye][cur] != 0) {
                    GL32.glDeleteSync(pboFences[eye][cur]);
                }
                pboFences[eye][cur] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            }

            // waiting the slot fence first also frees the slot's staging region for the memcpy
            int slot = ringCursor;
            VkCommandBuffer commandBuffer = beginRingCommandBuffer(slot, stack);
            long slotOffset = slot * eyeByteSize * 2;

            // first frame has no previous readback: consume the fresh one (one-time stall)
            int use = hasPreviousReadback ? (cur ^ 1) : cur;
            for (int eye = 0; eye < 2; eye++) {
                long fence = pboFences[eye][use];
                int status = GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, GPU_TIMEOUT_NS);
                if (status == GL32.GL_TIMEOUT_EXPIRED || status == GL32.GL_WAIT_FAILED) {
                    throw new AtumVRException("Bridge readback sync failed for eye " + eye
                            + ": status 0x" + Integer.toHexString(status));
                }
                GL32.glDeleteSync(fence);
                pboFences[eye][use] = 0;
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[eye][use]);
                var mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, eyeByteSize, GL30.GL_MAP_READ_BIT);
                if (mapped == null) {
                    throw new AtumVRException("glMapBufferRange returned null for bridge PBO, eye " + eye);
                }
                // rows reversed: GL readback is bottom-up, Vulkan wants top-down
                long rowBytes = (long) width * 4L;
                long src = memAddress(mapped);
                long dst = stagingPtr + slotOffset + eye * eyeByteSize;
                for (int row = 0; row < height; row++) {
                    memCopy(src + row * rowBytes, dst + (height - 1L - row) * rowBytes, rowBytes);
                }
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
            pboCursor ^= 1;
            hasPreviousReadback = true;

            recordAcquireBarriers(commandBuffer, stack, leftImageIndex, rightImageIndex);

            var region = VkBufferImageCopy.calloc(1, stack);
            region.get(0)
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .imageExtent(e -> e.set(width, height, 1));
            region.get(0).bufferOffset(slotOffset);
            vkCmdCopyBufferToImage(commandBuffer, stagingBuffer,
                    swapchainImages[0][leftImageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            region.get(0).bufferOffset(slotOffset + eyeByteSize);
            vkCmdCopyBufferToImage(commandBuffer, stagingBuffer,
                    swapchainImages[1][rightImageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            recordReleaseBarriers(commandBuffer, stack, leftImageIndex, rightImageIndex);
            checkVk(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(bridge)");

            var submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer));
            submitRing(slot, submit);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevAlignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, prevRowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, prevSkipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, prevSkipRows);
        }
    }


    // -------- INTERNALS: DEVICE --------

    private void initVulkanLoader() {
        try {
            VK.create();
        } catch (IllegalStateException alreadyCreated) {
            // fine, someone initialized the loader before us
        } catch (Throwable t) {
            throw new AtumVRException("Vulkan loader is unavailable: " + t.getMessage());
        }
        pfnVkGetInstanceProcAddr = VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr");
        if (pfnVkGetInstanceProcAddr == NULL) {
            throw new AtumVRException("vkGetInstanceProcAddr not found in the Vulkan loader");
        }
    }

    private int resolveApiVersion(XrGraphicsRequirementsVulkan2KHR requirements) {
        int minMajor = (int) ((requirements.minApiVersionSupported() >> 48) & 0xFFFF);
        int minMinor = (int) ((requirements.minApiVersionSupported() >> 32) & 0xFFFF);
        int maxMajor = (int) ((requirements.maxApiVersionSupported() >> 48) & 0xFFFF);
        int maxMinor = (int) ((requirements.maxApiVersionSupported() >> 32) & 0xFFFF);
        // external memory/semaphore caps are core since 1.1
        if (maxMajor == 1 && maxMinor < 1) {
            throw new AtumVRException("Runtime supports Vulkan up to " + maxMajor + "." + maxMinor
                    + ", the bridge needs at least 1.1");
        }
        int major = Math.max(1, minMajor);
        int minor = (major == 1) ? Math.max(1, minMinor) : minMinor;
        return (major << 22) | (minor << 12);
    }

    private int findGraphicsQueueFamily(MemoryStack stack) {
        IntBuffer countBuf = stack.callocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, countBuf, null);
        var families = VkQueueFamilyProperties.calloc(countBuf.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, countBuf, families);
        for (int i = 0; i < families.capacity(); i++) {
            if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                return i;
            }
        }
        throw new AtumVRException("Runtime-selected Vulkan device has no graphics queue");
    }

    private Interop resolveInterop() {
        Interop candidate = Platform.get() == Platform.WINDOWS ? Interop.WIN32
                : Platform.get() == Platform.LINUX ? Interop.OPAQUE_FD
                : null;
        if (candidate == null || !hasDeviceExtensions(candidate.memoryExtension, candidate.semaphoreExtension)) {
            return null;
        }
        return candidate;
    }

    private boolean hasDeviceExtensions(String... names) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer countBuf = stack.callocInt(1);
            checkVk(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, countBuf, null),
                    "vkEnumerateDeviceExtensionProperties count");
            var props = VkExtensionProperties.calloc(countBuf.get(0));
            try {
                checkVk(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, countBuf, props),
                        "vkEnumerateDeviceExtensionProperties list");
                Set<String> available = new HashSet<>();
                for (int i = 0; i < props.capacity(); i++) {
                    available.add(props.get(i).extensionNameString());
                }
                return available.containsAll(List.of(names));
            } finally {
                props.free();
            }
        }
    }

    private void createCommandRing(MemoryStack stack) {
        var poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex);
        LongBuffer longBuf = stack.callocLong(1);
        checkVk(vkCreateCommandPool(device, poolInfo, null, longBuf), "vkCreateCommandPool(bridge)");
        commandPool = longBuf.get(0);

        var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(RING_SIZE);
        PointerBuffer commandBuffersBuf = stack.callocPointer(RING_SIZE);
        checkVk(vkAllocateCommandBuffers(device, allocInfo, commandBuffersBuf), "vkAllocateCommandBuffers(bridge)");

        var fenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
        for (int i = 0; i < RING_SIZE; i++) {
            ringCommandBuffers[i] = new VkCommandBuffer(commandBuffersBuf.get(i), device);
            checkVk(vkCreateFence(device, fenceInfo, null, longBuf), "vkCreateFence(bridge)");
            ringFences[i] = longBuf.get(0);
        }
    }

    private VkCommandBuffer beginRingCommandBuffer(int slot, MemoryStack stack) {
        if (ringSubmitted[slot]) {
            int result = vkWaitForFences(device, ringFences[slot], true, GPU_TIMEOUT_NS);
            if (result != VK_SUCCESS) {
                throw new AtumVRException("vkWaitForFences(bridge) returned " + result);
            }
            checkVk(vkResetFences(device, ringFences[slot]), "vkResetFences(bridge)");
            ringSubmitted[slot] = false;
        }
        VkCommandBuffer commandBuffer = ringCommandBuffers[slot];
        checkVk(vkResetCommandBuffer(commandBuffer, 0), "vkResetCommandBuffer(bridge)");
        var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        checkVk(vkBeginCommandBuffer(commandBuffer, beginInfo), "vkBeginCommandBuffer(bridge)");
        return commandBuffer;
    }

    private void submitRing(int slot, VkSubmitInfo submit) {
        checkVk(vkQueueSubmit(queue, submit, ringFences[slot]), "vkQueueSubmit(bridge)");
        ringSubmitted[slot] = true;
        ringCursor = (slot + 1) % RING_SIZE;
    }

    private void recordAcquireBarriers(VkCommandBuffer commandBuffer, MemoryStack stack,
                                       int leftImageIndex, int rightImageIndex) {
        var barriers = VkImageMemoryBarrier.calloc(2, stack);
        // UNDEFINED is fine as source: the copy overwrites the full image
        setImageBarrier(barriers.get(0), swapchainImages[0][leftImageIndex],
                VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0, VK_ACCESS_TRANSFER_WRITE_BIT);
        setImageBarrier(barriers.get(1), swapchainImages[1][rightImageIndex],
                VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0, VK_ACCESS_TRANSFER_WRITE_BIT);
        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, null, null, barriers);
    }

    private void recordReleaseBarriers(VkCommandBuffer commandBuffer, MemoryStack stack,
                                       int leftImageIndex, int rightImageIndex) {
        var barriers = VkImageMemoryBarrier.calloc(2, stack);
        // the runtime expects released color images in COLOR_ATTACHMENT_OPTIMAL
        setImageBarrier(barriers.get(0), swapchainImages[0][leftImageIndex],
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_ACCESS_TRANSFER_WRITE_BIT, 0);
        setImageBarrier(barriers.get(1), swapchainImages[1][rightImageIndex],
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_ACCESS_TRANSFER_WRITE_BIT, 0);
        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0, null, null, barriers);
    }

    // GL hands the shared images over through VK_QUEUE_FAMILY_EXTERNAL, Mesa drivers may keep them compressed
    private void recordSharedOwnershipBarriers(VkCommandBuffer commandBuffer, MemoryStack stack, boolean acquire) {
        var barriers = VkImageMemoryBarrier.calloc(2, stack);
        for (int eye = 0; eye < 2; eye++) {
            setImageBarrier(barriers.get(eye), sharedImages[eye],
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    acquire ? 0 : VK_ACCESS_TRANSFER_READ_BIT,
                    acquire ? VK_ACCESS_TRANSFER_READ_BIT : 0);
            barriers.get(eye)
                    .srcQueueFamilyIndex(acquire ? VK_QUEUE_FAMILY_EXTERNAL : queueFamilyIndex)
                    .dstQueueFamilyIndex(acquire ? queueFamilyIndex : VK_QUEUE_FAMILY_EXTERNAL);
        }
        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                acquire ? VK_PIPELINE_STAGE_TRANSFER_BIT : VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0, null, null, barriers);
    }

    private void setImageBarrier(VkImageMemoryBarrier barrier, long image,
                                 int oldLayout, int newLayout,
                                 int srcAccess, int dstAccess) {
        barrier.sType$Default()
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(r -> r
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
    }


    // -------- INTERNALS: TIERS --------

    private Tier decideTier() {
        GLCapabilities caps;
        try {
            caps = GL.getCapabilities();
        } catch (IllegalStateException e) {
            throw new AtumVRException("Vulkan bridge needs a current OpenGL context on this thread");
        }
        boolean glExternal = caps.GL_EXT_memory_object && caps.GL_EXT_semaphore && (interop == Interop.WIN32
                ? caps.GL_EXT_memory_object_win32 && caps.GL_EXT_semaphore_win32
                : caps.GL_EXT_memory_object_fd && caps.GL_EXT_semaphore_fd);

        boolean sameDevice = interop != Interop.OPAQUE_FD || !glExternal || isGlOnBridgeDevice();
        Tier picked = (interop != null && glExternal && sameDevice && !FORCE_CPU_STAGING)
                ? Tier.ZERO_COPY : Tier.CPU_STAGING;
        vrProvider.getLogger().logInfo(
                "Vulkan bridge tier: " + picked
                        + (picked == Tier.CPU_STAGING
                        ? " (GL interop " + glExternal + ", VK interop " + (interop != null)
                        + (sameDevice ? "" : ", GL and Vulkan on different GPUs")
                        + (FORCE_CPU_STAGING ? ", forced" : "")
                        + "; expect one frame of extra latency)"
                        : "")
        );
        return picked;
    }

    private boolean isGlOnBridgeDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var idProperties = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
            vkGetPhysicalDeviceProperties2(physicalDevice, VkPhysicalDeviceProperties2.calloc(stack)
                    .sType$Default()
                    .pNext(idProperties.address()));

            ByteBuffer glUuid = stack.calloc(EXTMemoryObject.GL_UUID_SIZE_EXT);
            EXTMemoryObject.glGetUnsignedBytevEXT(EXTMemoryObject.GL_DRIVER_UUID_EXT, glUuid);
            boolean driverMatch = glUuid.equals(idProperties.driverUUID());
            boolean deviceMatch = false;
            int glDevices = GL11.glGetInteger(EXTMemoryObject.GL_NUM_DEVICE_UUIDS_EXT);
            for (int i = 0; i < glDevices && !deviceMatch; i++) {
                EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, i, glUuid);
                deviceMatch = glUuid.equals(idProperties.deviceUUID());
            }
            return GLUtils.drainGLErrors() == 0 && driverMatch && deviceMatch;
        } catch (Throwable t) {
            GLUtils.drainGLErrors();
            vrProvider.getLogger().logDebug("GL/Vulkan device match failed: " + t.getMessage());
            return false;
        }
    }

    private boolean createZeroCopyTargets() {
        try {
            createSharedSemaphores();
            for (int eye = 0; eye < 2; eye++) {
                glTextures[eye] = createSharedEyeTexture(eye);
            }
            glSemTextures.put(0, glTextures[0]).put(1, glTextures[1]);
            return true;
        } catch (Throwable t) {
            vrProvider.getLogger().logError(
                    "Vulkan bridge zero-copy setup failed, falling back to CPU staging: " + t.getMessage()
            );
            discardZeroCopyTargets();
            return false;
        }
    }

    private void discardZeroCopyTargets() {
        GLUtils.drainGLErrors();
        for (int eye = 0; eye < 2; eye++) {
            if (glTextures[eye] != 0) GL11.glDeleteTextures(glTextures[eye]);
            if (glMemoryObjects[eye] != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObjects[eye]);
            if (sharedImages[eye] != NULL) vkDestroyImage(device, sharedImages[eye], null);
            if (sharedMemory[eye] != NULL) vkFreeMemory(device, sharedMemory[eye], null);
            glTextures[eye] = glMemoryObjects[eye] = 0;
            sharedImages[eye] = sharedMemory[eye] = NULL;
        }
        if (glSemGlDone != 0) EXTSemaphore.glDeleteSemaphoresEXT(glSemGlDone);
        if (glSemVkDone != 0) EXTSemaphore.glDeleteSemaphoresEXT(glSemVkDone);
        if (vkSemGlDone != NULL) vkDestroySemaphore(device, vkSemGlDone, null);
        if (vkSemVkDone != NULL) vkDestroySemaphore(device, vkSemVkDone, null);
        glSemGlDone = glSemVkDone = 0;
        vkSemGlDone = vkSemVkDone = NULL;
        GLUtils.drainGLErrors();
    }

    private int createSharedEyeTexture(int eyeIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longBuf = stack.callocLong(1);

            var externalInfo = VkExternalMemoryImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(interop.memoryHandleType);
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(externalInfo.address())
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(vkFormat)
                    .extent(e -> e.set(width, height, 1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            checkVk(vkCreateImage(device, imageInfo, null, longBuf), "vkCreateImage(shared eye " + eyeIndex + ")");
            sharedImages[eyeIndex] = longBuf.get(0);

            var dedicatedReq = VkMemoryDedicatedRequirements.calloc(stack).sType$Default();
            var memReq2 = VkMemoryRequirements2.calloc(stack).sType$Default().pNext(dedicatedReq.address());
            var reqInfo = VkImageMemoryRequirementsInfo2.calloc(stack).sType$Default().image(sharedImages[eyeIndex]);
            vkGetImageMemoryRequirements2(device, reqInfo, memReq2);
            VkMemoryRequirements memReq = memReq2.memoryRequirements();
            boolean dedicated = dedicatedReq.requiresDedicatedAllocation() || dedicatedReq.prefersDedicatedAllocation();

            var exportInfo = VkExportMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(interop.memoryHandleType);
            if (dedicated) {
                var dedicatedInfo = VkMemoryDedicatedAllocateInfo.calloc(stack)
                        .sType$Default()
                        .image(sharedImages[eyeIndex]);
                exportInfo.pNext(dedicatedInfo.address());
            }
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(exportInfo.address())
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(findMemoryType(memReq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            checkVk(vkAllocateMemory(device, allocInfo, null, longBuf), "vkAllocateMemory(shared eye " + eyeIndex + ")");
            sharedMemory[eyeIndex] = longBuf.get(0);
            checkVk(vkBindImageMemory(device, sharedImages[eyeIndex], sharedMemory[eyeIndex], 0),
                    "vkBindImageMemory(shared eye " + eyeIndex + ")");

            int memoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();
            glMemoryObjects[eyeIndex] = memoryObject;
            EXTMemoryObject.glMemoryObjectParameteriEXT(memoryObject,
                    EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT,
                    dedicated ? GL11.GL_TRUE : GL11.GL_FALSE);
            importSharedMemory(stack, eyeIndex, memoryObject, memReq.size());

            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, EXTMemoryObject.GL_TEXTURE_TILING_EXT, EXTMemoryObject.GL_OPTIMAL_TILING_EXT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            EXTMemoryObject.glTexStorageMem2DEXT(GL11.GL_TEXTURE_2D, 1, glInternalFormat, width, height, memoryObject, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLUtils.checkGLError("Bridge shared texture import, eye " + eyeIndex);
            return texture;
        }
    }

    private void importSharedMemory(MemoryStack stack, int eyeIndex, int memoryObject, long size) {
        if (interop == Interop.OPAQUE_FD) {
            var fdInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(sharedMemory[eyeIndex])
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            IntBuffer fdBuf = stack.callocInt(1);
            checkVk(KHRExternalMemoryFd.vkGetMemoryFdKHR(device, fdInfo, fdBuf),
                    "vkGetMemoryFdKHR(eye " + eyeIndex + ")");


            EXTMemoryObjectFD.glImportMemoryFdEXT(memoryObject, size,
                    EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fdBuf.get(0));
        } else {
            var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(sharedMemory[eyeIndex])
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            PointerBuffer handleBuf = stack.callocPointer(1);
            checkVk(KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device, handleInfo, handleBuf),
                    "vkGetMemoryWin32HandleKHR(eye " + eyeIndex + ")");


            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(memoryObject, size,
                    EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handleBuf.get(0));
        }
    }

    private void createSharedSemaphores() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var exportInfo = VkExportSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(interop.semaphoreHandleType);
            var semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(exportInfo.address());
            LongBuffer longBuf = stack.callocLong(1);
            checkVk(vkCreateSemaphore(device, semaphoreInfo, null, longBuf), "vkCreateSemaphore(glDone)");
            vkSemGlDone = longBuf.get(0);
            checkVk(vkCreateSemaphore(device, semaphoreInfo, null, longBuf), "vkCreateSemaphore(vkDone)");
            vkSemVkDone = longBuf.get(0);

            glSemGlDone = importSemaphoreToGL(stack, vkSemGlDone, "glDone");
            glSemVkDone = importSemaphoreToGL(stack, vkSemVkDone, "vkDone");
            GLUtils.checkGLError("Bridge semaphore import");

            glSemNoBuffers = memAllocInt(0);
            glSemTextures = memAllocInt(2);
            glSemLayouts = memAllocInt(2);
            glSemLayouts.put(0, EXTSemaphore.GL_LAYOUT_TRANSFER_SRC_EXT)
                    .put(1, EXTSemaphore.GL_LAYOUT_TRANSFER_SRC_EXT);
        }
    }

    private int importSemaphoreToGL(MemoryStack stack, long vkSemaphore, String label) {
        if (interop == Interop.OPAQUE_FD) {
            var fdInfo = VkSemaphoreGetFdInfoKHR.calloc(stack)
                    .sType$Default()
                    .semaphore(vkSemaphore)
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            IntBuffer fdBuf = stack.callocInt(1);
            checkVk(KHRExternalSemaphoreFd.vkGetSemaphoreFdKHR(device, fdInfo, fdBuf),
                    "vkGetSemaphoreFdKHR(" + label + ")");
            int glSemaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreFD.glImportSemaphoreFdEXT(glSemaphore,
                    EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fdBuf.get(0));
            return glSemaphore;
        }
        var handleInfo = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                .sType$Default()
                .semaphore(vkSemaphore)
                .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        PointerBuffer handleBuf = stack.callocPointer(1);
        checkVk(KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR(device, handleInfo, handleBuf),
                "vkGetSemaphoreWin32HandleKHR(" + label + ")");
        int glSemaphore = EXTSemaphore.glGenSemaphoresEXT();
        EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(glSemaphore,
                EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handleBuf.get(0));
        return glSemaphore;
    }

    private int createStagingEyeTexture(int eyeIndex) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        for (int i = 0; i < 2; i++) {
            int pbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, eyeByteSize, GL15.GL_STREAM_READ);
            pbos[eyeIndex][i] = pbo;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GLUtils.checkGLError("Bridge staging texture setup, eye " + eyeIndex);
        return texture;
    }

    private void createStagingBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longBuf = stack.callocLong(1);
            //one region of two eyes per ring slot, so uploads never race in-flight reads
            var bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(eyeByteSize * 2 * RING_SIZE)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            checkVk(vkCreateBuffer(device, bufferInfo, null, longBuf), "vkCreateBuffer(staging)");
            stagingBuffer = longBuf.get(0);

            var memReq = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memReq);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(findMemoryType(memReq.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
            checkVk(vkAllocateMemory(device, allocInfo, null, longBuf), "vkAllocateMemory(staging)");
            stagingMemory = longBuf.get(0);
            checkVk(vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0), "vkBindBufferMemory(staging)");

            PointerBuffer dataBuf = stack.callocPointer(1);
            checkVk(vkMapMemory(device, stagingMemory, 0, VK_WHOLE_SIZE, 0, dataBuf), "vkMapMemory(staging)");
            stagingPtr = dataBuf.get(0);
        }
    }

    private int findMemoryType(int typeBits, int requiredProperties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0
                        && (memProperties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties) {
                    return i;
                }
            }
        }
        throw new AtumVRException("No Vulkan memory type with properties 0x"
                + Integer.toHexString(requiredProperties));
    }

    private int mapGlToVkFormat(int glInternalFormat) {
        if (glInternalFormat == GL21.GL_SRGB8_ALPHA8) return VK_FORMAT_R8G8B8A8_SRGB;
        if (glInternalFormat == GL11.GL_RGBA8) return VK_FORMAT_R8G8B8A8_UNORM;
        return -1;
    }

    private void checkVk(int result, @NotNull String caller) {
        if (result < 0) {
            throw new AtumVRException(caller + " failed with VkResult " + result);
        }
    }


    // -------- DESTROY --------

    public void destroy() {
        if (device != null) {
            vkDeviceWaitIdle(device);
        }
        destroyGLObjects();
        if (device != null) {
            if (vkSemGlDone != NULL) vkDestroySemaphore(device, vkSemGlDone, null);
            if (vkSemVkDone != NULL) vkDestroySemaphore(device, vkSemVkDone, null);
            for (int eye = 0; eye < 2; eye++) {
                if (sharedImages[eye] != NULL) vkDestroyImage(device, sharedImages[eye], null);
                if (sharedMemory[eye] != NULL) vkFreeMemory(device, sharedMemory[eye], null);
            }
            if (stagingBuffer != NULL) vkDestroyBuffer(device, stagingBuffer, null);
            if (stagingMemory != NULL) vkFreeMemory(device, stagingMemory, null);
            for (long fence : ringFences) {
                if (fence != NULL) vkDestroyFence(device, fence, null);
            }
            if (commandPool != NULL) vkDestroyCommandPool(device, commandPool, null);
            vkDestroyDevice(device, null);
            device = null;
        }
        if (vkInstance != null) {
            vkDestroyInstance(vkInstance, null);
            vkInstance = null;
        }
        if (glSemNoBuffers != null) {
            // cast to Buffer: LWJGL 3.3.3 and older have no typed memFree overloads
            memFree((java.nio.Buffer) glSemNoBuffers);
            memFree((java.nio.Buffer) glSemTextures);
            memFree((java.nio.Buffer) glSemLayouts);
            glSemNoBuffers = null;
        }
    }

    private void destroyGLObjects() {
        try {
            GL.getCapabilities();
        } catch (Throwable noContext) {
            vrProvider.getLogger().logDebug("No current GL context, skipping bridge GL cleanup");
            return;
        }
        try {
            for (int eye = 0; eye < 2; eye++) {
                if (glTextures[eye] != 0) GL11.glDeleteTextures(glTextures[eye]);
                if (glMemoryObjects[eye] != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObjects[eye]);
                for (int i = 0; i < 2; i++) {
                    if (pbos[eye][i] != 0) GL15.glDeleteBuffers(pbos[eye][i]);
                    if (pboFences[eye][i] != 0) GL32.glDeleteSync(pboFences[eye][i]);
                }
            }
            if (glSemGlDone != 0) EXTSemaphore.glDeleteSemaphoresEXT(glSemGlDone);
            if (glSemVkDone != 0) EXTSemaphore.glDeleteSemaphoresEXT(glSemVkDone);
        } catch (Throwable t) {
            vrProvider.getLogger().logError("Bridge GL cleanup failed: " + t.getMessage());
        }
    }
}
