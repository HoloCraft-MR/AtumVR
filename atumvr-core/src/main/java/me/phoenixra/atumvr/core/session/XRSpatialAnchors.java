package me.phoenixra.atumvr.core.session;

import lombok.Getter;
import me.phoenixra.atumvr.api.mr.TableAnchor;
import me.phoenixra.atumvr.api.mr.TablePose;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Meta spatial anchors for the {@link TableAnchor} (low-level OpenXR stuff)
 * <p>
 *     Uses XR_FB_spatial_entity (create + components), XR_FB_spatial_entity_storage
 *     (save/erase, local) and XR_FB_spatial_entity_query (load by UUID).
 *     All operations are async: requests are issued from {@link #update()}
 *     and continued from the completion events ({@link #onEvent(int, long)}).
 * </p>
 * <ul>
 *     <li>placement: create anchor at the placed pose, enable LOCATABLE + STORABLE,
 *     save locally, then erase the previous anchor(s) and report the new UUID;</li>
 *     <li>session start: query + load the saved UUID, enable LOCATABLE;</li>
 *     <li>each frame: locate the anchor in the app space, so the table is world-locked.</li>
 * </ul>
 * <p>
 *     On any failure the table keeps using the placed (reference space) pose.
 *     All methods must be called from the render thread
 * </p>
 */
public class XRSpatialAnchors {
    private static final long OPERATION_TIMEOUT_NANOS = 15_000_000_000L;
    private static final long LOCATE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final long LOCATION_VALID = XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT
            | XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;

    private final XRProvider vrProvider;

    private XrSession session;
    private XrSpace appSpace;

    /** If the runtime supports the required extensions */
    @Getter
    private boolean supported;

    /** The anchor the table is located from */
    private XrSpace anchor;
    private UUID anchorUuid;

    /** Anchor being set up (created or loaded), waiting for components / save */
    private XrSpace setupSpace;
    private UUID setupUuid;
    private boolean setupIsNew;

    /** Replaced anchors, erased once the new one is saved (or failed) */
    private final List<XrSpace> toErase = new ArrayList<>();
    private final Map<Long, XrSpace> eraseRequests = new HashMap<>();

    private long createRequest;
    private long statusRequest;
    private long saveRequest;
    private long queryRequest;
    private UUID queryUuid;
    private boolean queryFound;
    private long busySince;

    /** Placement received while busy, latest wins */
    private TablePose stashedPlacement;
    private TablePose creatingPose;

    private boolean loadChecked;
    private int locateFailures;
    private long lastLocateLog;
    private boolean locatedLogged;

    public XRSpatialAnchors(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }

    public void init(@NotNull XrSession session, @NotNull XrSpace appSpace) {
        this.session = session;
        this.appSpace = appSpace;
        var caps = session.getCapabilities();
        supported = caps.XR_FB_spatial_entity
                && caps.XR_FB_spatial_entity_storage
                && caps.XR_FB_spatial_entity_query;
        if (!supported) {
            log("[MR] Anchor unavailable: runtime lacks"
                    + (caps.XR_FB_spatial_entity ? "" : " XR_FB_spatial_entity")
                    + (caps.XR_FB_spatial_entity_storage ? "" : " XR_FB_spatial_entity_storage")
                    + (caps.XR_FB_spatial_entity_query ? "" : " XR_FB_spatial_entity_query")
                    + ", using pose-based table placement");
        } else {
            log("[MR] Anchor support: XR_FB_spatial_entity + storage + query");
        }
    }

    private TableAnchor table() {
        return vrProvider.getTableAnchor();
    }

    private boolean isBusy() {
        return createRequest != 0 || statusRequest != 0 || saveRequest != 0
                || queryRequest != 0 || setupSpace != null;
    }

    /**
     * Start pending requests and locate the anchor.
     * Call once per frame, after xrWaitFrame
     */
    public void update() {
        if (!supported) {
            return;
        }
        TablePose placement = table().pollPlaceRequest();
        if (placement != null) {
            stashedPlacement = placement;
            loadChecked = true; // a new placement replaces the saved anchor
            // stop locating the replaced anchor right away
            if (anchor != null) {
                toErase.add(anchor);
                anchor = null;
                anchorUuid = null;
            }
        }
        if (isBusy() && System.nanoTime() - busySince > OPERATION_TIMEOUT_NANOS) {
            logError("[MR] Anchor operation timed out after "
                    + OPERATION_TIMEOUT_NANOS / 1_000_000_000L + " s, using placed pose");
            failSetup();
        }
        if (!isBusy()) {
            if (stashedPlacement != null) {
                TablePose pose = stashedPlacement;
                stashedPlacement = null;
                startCreate(pose);
            } else if (!loadChecked && table().getSavedAnchorUuid() != null) {
                // the app may hand over the saved UUID after the session started
                loadChecked = true;
                startQuery(table().getSavedAnchorUuid());
            }
        }
        locate();
    }

    /**
     * Reference space changed (recenter / floor change): the anchor is re-located
     * in the new space from the next frame; drop the stale located pose and re-log it
     */
    public void onReferenceSpaceChanged() {
        locateFailures = 0;
        locatedLogged = false;
        if (vrProvider.getTableAnchor() != null) {
            vrProvider.getTableAnchor().setAnchoredPose(null);
        }
        log("[MR] Anchor re-locate after reference space change"
                + (anchor != null ? " (anchor " + anchorUuid + ")" : " (no anchor, placed pose kept)"));
    }

    // -------- CREATE / COMPONENTS / SAVE --------

    private void startCreate(@NotNull TablePose pose) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var orientation = pose.orientation();
            var info = XrSpatialAnchorCreateInfoFB.calloc(stack)
                    .type(FBSpatialEntity.XR_TYPE_SPATIAL_ANCHOR_CREATE_INFO_FB)
                    .space(appSpace)
                    .poseInSpace(p -> p
                            .orientation(q -> q.set(orientation.x, orientation.y, orientation.z, orientation.w))
                            .position$(v -> v.set(pose.x(), pose.y(), pose.z())))
                    .time(vrProvider.getXrDisplayTime());
            LongBuffer request = stack.callocLong(1);
            int result = FBSpatialEntity.xrCreateSpatialAnchorFB(session, info, request);
            if (result < 0) {
                logError("[MR] Anchor create failed: xrCreateSpatialAnchorFB "
                        + vrProvider.getXRActionResult(result) + ", using placed pose");
                finishPlacement(null);
                return;
            }
            createRequest = request.get(0);
            creatingPose = pose;
            busySince = System.nanoTime();
        }
    }

    private void onCreateComplete(@NotNull XrEventDataSpatialAnchorCreateCompleteFB event) {
        createRequest = 0;
        if (event.result() < 0) {
            logError("[MR] Anchor create failed: " + vrProvider.getXRActionResult(event.result())
                    + ", using placed pose");
            finishPlacement(null);
            return;
        }
        setupSpace = new XrSpace(event.space(), session);
        setupUuid = toUuid(event.uuid());
        setupIsNew = true;
        TablePose pose = creatingPose;
        log("[MR] Anchor created " + setupUuid + (pose == null ? "" : String.format(
                " at x=%.3f, y=%.3f, z=%.3f m, yaw=%.1f deg",
                pose.x(), pose.y(), pose.z(), Math.toDegrees(pose.yaw()))));
        advanceSetup();
    }

    /**
     * Enable LOCATABLE (and STORABLE + save for new anchors), one async step at a time
     */
    private void advanceSetup() {
        if (setupSpace == null) {
            return;
        }
        int locatable = ensureComponent(setupSpace, FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_LOCATABLE_FB);
        if (locatable < 0) {
            failSetup();
            return;
        }
        if (locatable == 0) {
            return;
        }
        if (!setupIsNew) {
            // loaded anchor is ready
            useAnchor(setupSpace, setupUuid);
            log("[MR] Anchor loaded " + anchorUuid);
            setupSpace = null;
            return;
        }
        int storable = ensureComponent(setupSpace, FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_STORABLE_FB);
        if (storable < 0) {
            failSetup();
            return;
        }
        if (storable == 0) {
            return;
        }
        // locate from the new anchor while it is being saved
        useAnchor(setupSpace, setupUuid);
        setupSpace = null;
        if (anchor == null) {
            // replaced by a newer placement before it was saved
            finishPlacement(null);
            return;
        }
        startSave();
    }

    /**
     * @return 1 enabled, 0 enable requested (wait for event), -1 failed
     */
    private int ensureComponent(@NotNull XrSpace space, int component) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var status = XrSpaceComponentStatusFB.calloc(stack)
                    .type(FBSpatialEntity.XR_TYPE_SPACE_COMPONENT_STATUS_FB);
            int result = FBSpatialEntity.xrGetSpaceComponentStatusFB(space, component, status);
            if (result >= 0 && status.enabled() && !status.changePending()) {
                return 1;
            }
            var info = XrSpaceComponentStatusSetInfoFB.calloc(stack)
                    .type(FBSpatialEntity.XR_TYPE_SPACE_COMPONENT_STATUS_SET_INFO_FB)
                    .componentType(component)
                    .enabled(true)
                    .timeout(0);
            LongBuffer request = stack.callocLong(1);
            result = FBSpatialEntity.xrSetSpaceComponentStatusFB(space, info, request);
            if (result == FBSpatialEntity.XR_ERROR_SPACE_COMPONENT_STATUS_ALREADY_SET_FB) {
                return 1;
            }
            if (result < 0) {
                logError("[MR] Anchor component " + componentName(component)
                        + " enable failed: " + vrProvider.getXRActionResult(result));
                return -1;
            }
            statusRequest = request.get(0);
            busySince = System.nanoTime();
            return 0;
        }
    }

    private void onSetStatusComplete(@NotNull XrEventDataSpaceSetStatusCompleteFB event) {
        statusRequest = 0;
        int result = event.result();
        if (result < 0 && result != FBSpatialEntity.XR_ERROR_SPACE_COMPONENT_STATUS_ALREADY_SET_FB) {
            logError("[MR] Anchor component " + componentName(event.componentType())
                    + " enable failed: " + vrProvider.getXRActionResult(result));
            failSetup();
            return;
        }
        advanceSetup();
    }

    private void startSave() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = XrSpaceSaveInfoFB.calloc(stack)
                    .type(FBSpatialEntityStorage.XR_TYPE_SPACE_SAVE_INFO_FB)
                    .space(anchor)
                    .location(FBSpatialEntityQuery.XR_SPACE_STORAGE_LOCATION_LOCAL_FB)
                    .persistenceMode(FBSpatialEntityStorage.XR_SPACE_PERSISTENCE_MODE_INDEFINITE_FB);
            LongBuffer request = stack.callocLong(1);
            int result = FBSpatialEntityStorage.xrSaveSpaceFB(session, info, request);
            if (result < 0) {
                logError("[MR] Anchor save failed: xrSaveSpaceFB " + vrProvider.getXRActionResult(result)
                        + ", placement not persisted as anchor");
                finishPlacement(null);
                return;
            }
            saveRequest = request.get(0);
            busySince = System.nanoTime();
        }
    }

    private void onSaveComplete(@NotNull XrEventDataSpaceSaveCompleteFB event) {
        saveRequest = 0;
        UUID uuid = toUuid(event.uuid());
        if (event.result() < 0) {
            logError("[MR] Anchor save failed: " + uuid + " " + vrProvider.getXRActionResult(event.result())
                    + ", placement not persisted as anchor");
            finishPlacement(null);
            return;
        }
        log("[MR] Anchor saved " + uuid + " (local)");
        finishPlacement(uuid);
    }

    /**
     * Placement chain ended: report the saved UUID (null = not persisted) and erase replaced anchors
     */
    private void finishPlacement(@Nullable UUID saved) {
        creatingPose = null;
        table().onAnchorSaved(saved);
        for (XrSpace space : toErase) {
            startErase(space);
        }
        toErase.clear();
    }

    private void failSetup() {
        boolean wasNew = setupIsNew || createRequest != 0 || saveRequest != 0;
        if (setupSpace != null && setupSpace != anchor) {
            destroySpace(setupSpace);
        }
        setupSpace = null;
        setupUuid = null;
        setupIsNew = false;
        createRequest = 0;
        statusRequest = 0;
        saveRequest = 0;
        if (queryRequest != 0) {
            queryRequest = 0;
            logError("[MR] Anchor load failed: " + queryUuid + " not loaded, using placed pose");
        }
        if (wasNew) {
            finishPlacement(null);
        }
    }

    // -------- ERASE --------

    private void startErase(@NotNull XrSpace space) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = XrSpaceEraseInfoFB.calloc(stack)
                    .type(FBSpatialEntityStorage.XR_TYPE_SPACE_ERASE_INFO_FB)
                    .space(space)
                    .location(FBSpatialEntityQuery.XR_SPACE_STORAGE_LOCATION_LOCAL_FB);
            LongBuffer request = stack.callocLong(1);
            int result = FBSpatialEntityStorage.xrEraseSpaceFB(session, info, request);
            if (result < 0) {
                logError("[MR] Anchor erase failed: xrEraseSpaceFB " + vrProvider.getXRActionResult(result));
                destroySpace(space);
                return;
            }
            eraseRequests.put(request.get(0), space);
        }
    }

    private void onEraseComplete(@NotNull XrEventDataSpaceEraseCompleteFB event) {
        XrSpace space = eraseRequests.remove(event.requestId());
        UUID uuid = toUuid(event.uuid());
        if (event.result() < 0) {
            logError("[MR] Anchor erase failed: " + uuid + " " + vrProvider.getXRActionResult(event.result()));
        } else {
            log("[MR] Anchor erased " + uuid + " (previous table)");
        }
        if (space != null) {
            destroySpace(space);
        }
    }

    // -------- LOAD --------

    private void startQuery(@NotNull UUID uuid) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var location = XrSpaceStorageLocationFilterInfoFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_STORAGE_LOCATION_FILTER_INFO_FB)
                    .location(FBSpatialEntityQuery.XR_SPACE_STORAGE_LOCATION_LOCAL_FB);
            var uuids = XrUuidEXT.calloc(1, stack);
            putUuid(uuids.get(0), uuid);
            var uuidFilter = XrSpaceUuidFilterInfoFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_UUID_FILTER_INFO_FB)
                    .next(location.address())
                    .uuids(uuids);
            var info = XrSpaceQueryInfoFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_QUERY_INFO_FB)
                    .queryAction(FBSpatialEntityQuery.XR_SPACE_QUERY_ACTION_LOAD_FB)
                    .maxResultCount(1)
                    .timeout(0)
                    .filter(XrSpaceFilterInfoBaseHeaderFB.create(uuidFilter.address()));
            LongBuffer request = stack.callocLong(1);
            int result = FBSpatialEntityQuery.xrQuerySpacesFB(
                    session, XrSpaceQueryInfoBaseHeaderFB.create(info.address()), request
            );
            if (result < 0) {
                logError("[MR] Anchor load failed: xrQuerySpacesFB " + vrProvider.getXRActionResult(result)
                        + ", using placed pose");
                return;
            }
            queryRequest = request.get(0);
            queryUuid = uuid;
            queryFound = false;
            busySince = System.nanoTime();
            log("[MR] Anchor load requested " + uuid);
        }
    }

    private void onQueryResultsAvailable(@NotNull XrEventDataSpaceQueryResultsAvailableFB event) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var results = XrSpaceQueryResultsFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_QUERY_RESULTS_FB);
            int result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, event.requestId(), results);
            if (result < 0 || results.resultCountOutput() == 0) {
                if (result < 0) {
                    logError("[MR] Anchor load failed: xrRetrieveSpaceQueryResultsFB "
                            + vrProvider.getXRActionResult(result));
                }
                return;
            }
            // output-only struct: no stack calloc in the Quest LWJGL 3.3.3 bindings
            var buffer = new XrSpaceQueryResultFB.Buffer(
                    stack.calloc(XrSpaceQueryResultFB.SIZEOF * results.resultCountOutput())
            );
            results.resultCapacityInput(buffer.capacity()).results(buffer);
            result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, event.requestId(), results);
            if (result < 0) {
                logError("[MR] Anchor load failed: xrRetrieveSpaceQueryResultsFB "
                        + vrProvider.getXRActionResult(result));
                return;
            }
            for (int i = 0; i < results.resultCountOutput(); i++) {
                var entry = buffer.get(i);
                XrSpace space = new XrSpace(entry.space(), session);
                UUID uuid = toUuid(entry.uuid());
                if (queryFound || !uuid.equals(queryUuid)) {
                    destroySpace(space);
                    continue;
                }
                queryFound = true;
                setupSpace = space;
                setupUuid = uuid;
                setupIsNew = false;
            }
        }
    }

    private void onQueryComplete(@NotNull XrEventDataSpaceQueryCompleteFB event) {
        queryRequest = 0;
        if (event.result() < 0) {
            logError("[MR] Anchor load failed: " + queryUuid + " "
                    + vrProvider.getXRActionResult(event.result()) + ", using placed pose");
            failSetup();
            return;
        }
        if (!queryFound) {
            logError("[MR] Anchor load failed: " + queryUuid + " not found, using placed pose");
            return;
        }
        advanceSetup();
    }

    // -------- LOCATE --------

    private void useAnchor(@NotNull XrSpace space, @NotNull UUID uuid) {
        if (anchor != null && anchor != space) {
            toErase.add(anchor);
        }
        anchor = space;
        anchorUuid = uuid;
        locateFailures = 0;
        locatedLogged = false;
        if (stashedPlacement != null) {
            // replaced before it was ready
            toErase.add(anchor);
            anchor = null;
            anchorUuid = null;
        }
    }

    private void locate() {
        if (anchor == null) {
            table().setAnchoredPose(null);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
            int result = XR10.xrLocateSpace(anchor, appSpace, vrProvider.getXrDisplayTime(), location);
            if (result < 0 || (location.locationFlags() & LOCATION_VALID) != LOCATION_VALID) {
                table().setAnchoredPose(null);
                locateFailures++;
                long now = System.nanoTime();
                if (now - lastLocateLog > LOCATE_LOG_INTERVAL_NANOS) {
                    lastLocateLog = now;
                    logError("[MR] Anchor locate failed " + anchorUuid + ": "
                            + (result < 0 ? vrProvider.getXRActionResult(result)
                            : "flags 0x" + Long.toHexString(location.locationFlags()))
                            + " (" + locateFailures + " frames), using placed pose");
                }
                return;
            }
            if (locateFailures != 0 || !locatedLogged) {
                log("[MR] Anchor located " + anchorUuid
                        + (locateFailures != 0 ? " after " + locateFailures + " failed frames" : ""));
                locateFailures = 0;
                locatedLogged = true;
            }
            var p = location.pose().position$();
            var q = location.pose().orientation();
            // yaw such that rotateY(yaw) maps -Z to the anchor's forward (see TablePose)
            float fx = -2f * (q.x() * q.z() + q.w() * q.y());
            float fz = -(1f - 2f * (q.x() * q.x() + q.y() * q.y()));
            float yaw = (float) Math.atan2(-fx, -fz);
            table().setAnchoredPose(new TablePose(p.x(), p.y(), p.z(), yaw));
        }
    }

    // -------- EVENTS --------

    /**
     * Handle spatial entity events
     *
     * @param type the event type
     * @param address the event address
     * @return true if handled
     */
    public boolean onEvent(int type, long address) {
        switch (type) {
            case FBSpatialEntity.XR_TYPE_EVENT_DATA_SPATIAL_ANCHOR_CREATE_COMPLETE_FB -> {
                var event = XrEventDataSpatialAnchorCreateCompleteFB.create(address);
                if (event.requestId() == createRequest) {
                    onCreateComplete(event);
                }
            }
            case FBSpatialEntity.XR_TYPE_EVENT_DATA_SPACE_SET_STATUS_COMPLETE_FB -> {
                var event = XrEventDataSpaceSetStatusCompleteFB.create(address);
                if (event.requestId() == statusRequest) {
                    onSetStatusComplete(event);
                }
            }
            case FBSpatialEntityStorage.XR_TYPE_EVENT_DATA_SPACE_SAVE_COMPLETE_FB -> {
                var event = XrEventDataSpaceSaveCompleteFB.create(address);
                if (event.requestId() == saveRequest) {
                    onSaveComplete(event);
                }
            }
            case FBSpatialEntityStorage.XR_TYPE_EVENT_DATA_SPACE_ERASE_COMPLETE_FB ->
                    onEraseComplete(XrEventDataSpaceEraseCompleteFB.create(address));
            case FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_RESULTS_AVAILABLE_FB -> {
                var event = XrEventDataSpaceQueryResultsAvailableFB.create(address);
                if (event.requestId() == queryRequest) {
                    onQueryResultsAvailable(event);
                }
            }
            case FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_COMPLETE_FB -> {
                var event = XrEventDataSpaceQueryCompleteFB.create(address);
                if (event.requestId() == queryRequest) {
                    onQueryComplete(event);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // -------- HELPERS --------

    private static @NotNull UUID toUuid(@NotNull XrUuidEXT uuid) {
        ByteBuffer data = uuid.data();
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data.get(i) & 0xFF);
            lsb = (lsb << 8) | (data.get(8 + i) & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    private static void putUuid(@NotNull XrUuidEXT target, @NotNull UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            target.data(i, (byte) (msb >>> (56 - 8 * i)));
            target.data(8 + i, (byte) (lsb >>> (56 - 8 * i)));
        }
    }

    private static @NotNull String componentName(int component) {
        return switch (component) {
            case FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_LOCATABLE_FB -> "LOCATABLE";
            case FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_STORABLE_FB -> "STORABLE";
            default -> Integer.toString(component);
        };
    }

    private void destroySpace(@NotNull XrSpace space) {
        vrProvider.checkXRError(false, XR10.xrDestroySpace(space), "xrDestroySpace", "anchor");
    }

    private void log(@NotNull String message) {
        vrProvider.getLogger().logInfo(message);
    }

    private void logError(@NotNull String message) {
        vrProvider.getLogger().logError(message);
    }

    public void destroy() {
        table().setAnchoredPose(null);
        if (anchor != null) {
            destroySpace(anchor);
            anchor = null;
        }
        if (setupSpace != null) {
            destroySpace(setupSpace);
            setupSpace = null;
        }
        for (XrSpace space : toErase) {
            destroySpace(space);
        }
        toErase.clear();
        for (XrSpace space : eraseRequests.values()) {
            destroySpace(space);
        }
        eraseRequests.clear();
        supported = false;
    }
}
