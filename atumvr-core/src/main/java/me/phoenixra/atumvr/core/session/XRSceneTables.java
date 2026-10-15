package me.phoenixra.atumvr.core.session;

import me.phoenixra.atumvr.api.mr.SceneTables;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Real tables from the Meta scene model (Space Setup), for MR table auto-placement.
 * <p>
 *     XR_FB_spatial_entity_query: load every space with a SEMANTIC_LABELS component;
 *     XR_FB_scene: keep the ones labelled TABLE (or DESK on runtimes before the desk-to-table
 *     migration), read their BOUNDED_2D rect and locate them in the app space.
 *     The plane's local X / Y are in the table top, Z its normal.
 * </p>
 * <p>
 *     Scene entities only come back with com.oculus.permission.USE_SCENE granted (runtime
 *     permission) and after the user ran Space Setup; otherwise the query just finds nothing.
 *     Render thread only.
 * </p>
 */
public class XRSceneTables {
    private static final long TIMEOUT_NANOS = 10_000_000_000L;
    private static final int MAX_RESULTS = 128;
    private static final long LOCATION_VALID = XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT
            | XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;

    private final XRProvider vrProvider;
    private XrSession session;
    private XrSpace appSpace;
    private boolean supported;

    private long queryRequest;
    private long queryStart;
    private final List<XrSpace> found = new ArrayList<>();

    public XRSceneTables(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }

    public void init(@NotNull XrSession session, @NotNull XrSpace appSpace) {
        this.session = session;
        this.appSpace = appSpace;
        var caps = session.getCapabilities();
        supported = caps.XR_FB_scene && caps.XR_FB_spatial_entity && caps.XR_FB_spatial_entity_query;
        log("[MR] Scene tables " + (supported ? "supported (XR_FB_scene)"
                : "unavailable: runtime lacks" + (caps.XR_FB_scene ? "" : " XR_FB_scene")
                + (caps.XR_FB_spatial_entity ? "" : " XR_FB_spatial_entity")
                + (caps.XR_FB_spatial_entity_query ? "" : " XR_FB_spatial_entity_query")));
    }

    private SceneTables tables() {
        return vrProvider.getSceneTables();
    }

    /** Once per frame, after xrWaitFrame */
    public void update() {
        if (session == null) {
            return;
        }
        if (tables().pollRequest()) {
            if (!supported) {
                tables().publish(new SceneTables.Result(SceneTables.Status.UNSUPPORTED, List.of(),
                        "runtime lacks XR_FB_scene"));
            } else if (queryRequest == 0) {
                startQuery();
            }
        }
        if (queryRequest != 0 && System.nanoTime() - queryStart > TIMEOUT_NANOS) {
            logError("[MR] Scene tables: query timed out");
            queryRequest = 0;
            destroyFound();
            tables().publish(new SceneTables.Result(SceneTables.Status.FAILED, List.of(), "query timed out"));
        }
    }

    private void startQuery() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filter = XrSpaceComponentFilterInfoFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_COMPONENT_FILTER_INFO_FB)
                    .componentType(FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_SEMANTIC_LABELS_FB);
            var info = XrSpaceQueryInfoFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_QUERY_INFO_FB)
                    .queryAction(FBSpatialEntityQuery.XR_SPACE_QUERY_ACTION_LOAD_FB)
                    .maxResultCount(MAX_RESULTS)
                    .timeout(0)
                    .filter(XrSpaceFilterInfoBaseHeaderFB.create(filter.address()));
            LongBuffer request = stack.callocLong(1);
            int result = FBSpatialEntityQuery.xrQuerySpacesFB(
                    session, XrSpaceQueryInfoBaseHeaderFB.create(info.address()), request);
            if (result < 0) {
                String r = vrProvider.getXRActionResult(result);
                logError("[MR] Scene tables: xrQuerySpacesFB " + r);
                tables().publish(new SceneTables.Result(SceneTables.Status.FAILED, List.of(),
                        "xrQuerySpacesFB " + r));
                return;
            }
            queryRequest = request.get(0);
            queryStart = System.nanoTime();
            log("[MR] Scene tables: query started");
        }
    }

    /** @return true if the event was for this query */
    public boolean onEvent(int type, long address) {
        if (queryRequest == 0) {
            return false;
        }
        if (type == FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_RESULTS_AVAILABLE_FB) {
            var event = XrEventDataSpaceQueryResultsAvailableFB.create(address);
            if (event.requestId() != queryRequest) {
                return false;
            }
            retrieve(event.requestId());
            return true;
        }
        if (type == FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_COMPLETE_FB) {
            var event = XrEventDataSpaceQueryCompleteFB.create(address);
            if (event.requestId() != queryRequest) {
                return false;
            }
            queryRequest = 0;
            complete(event.result());
            return true;
        }
        return false;
    }

    private void retrieve(long requestId) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var results = XrSpaceQueryResultsFB.calloc(stack)
                    .type(FBSpatialEntityQuery.XR_TYPE_SPACE_QUERY_RESULTS_FB);
            int result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, requestId, results);
            if (result < 0 || results.resultCountOutput() == 0) {
                return;
            }
            var buffer = new XrSpaceQueryResultFB.Buffer(
                    stack.calloc(XrSpaceQueryResultFB.SIZEOF * results.resultCountOutput()));
            results.resultCapacityInput(buffer.capacity()).results(buffer);
            result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, requestId, results);
            if (result < 0) {
                logError("[MR] Scene tables: xrRetrieveSpaceQueryResultsFB " + vrProvider.getXRActionResult(result));
                return;
            }
            for (int i = 0; i < results.resultCountOutput(); i++) {
                found.add(new XrSpace(buffer.get(i).space(), session));
            }
        }
    }

    private void complete(int result) {
        if (result < 0) {
            String r = vrProvider.getXRActionResult(result);
            logError("[MR] Scene tables: query failed " + r);
            destroyFound();
            tables().publish(new SceneTables.Result(SceneTables.Status.FAILED, List.of(), "query " + r));
            return;
        }
        List<SceneTables.Table> out = new ArrayList<>();
        int labelled = found.size();
        for (XrSpace space : found) {
            try {
                SceneTables.Table table = readTable(space);
                if (table != null) {
                    out.add(table);
                }
            } catch (Throwable t) {
                logError("[MR] Scene tables: reading a space failed: " + t);
            }
        }
        destroyFound();
        String message = labelled + " labelled spaces, " + out.size() + " tables";
        log("[MR] Scene tables: " + message);
        tables().publish(new SceneTables.Result(SceneTables.Status.DONE, out, message));
    }

    private SceneTables.Table readTable(XrSpace space) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!isEnabled(space, FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_SEMANTIC_LABELS_FB, stack)) {
                return null;
            }
            String labels = labels(space, stack);
            String upper = labels.toUpperCase(java.util.Locale.ROOT);
            if (!upper.contains("TABLE") && !upper.contains("DESK")) {
                return null;
            }
            if (!isEnabled(space, FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_BOUNDED_2D_FB, stack)
                    || !isEnabled(space, FBSpatialEntity.XR_SPACE_COMPONENT_TYPE_LOCATABLE_FB, stack)) {
                log("[MR] Scene tables: " + labels + " without 2D bounds / locatable, skipped");
                return null;
            }
            var rect = XrRect2Df.calloc(stack);
            int result = FBScene.xrGetSpaceBoundingBox2DFB(session, space, rect);
            if (result < 0) {
                logError("[MR] Scene tables: xrGetSpaceBoundingBox2DFB " + vrProvider.getXRActionResult(result));
                return null;
            }
            var location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
            result = XR10.xrLocateSpace(space, appSpace, vrProvider.getXrDisplayTime(), location);
            if (result < 0 || (location.locationFlags() & LOCATION_VALID) != LOCATION_VALID) {
                log("[MR] Scene tables: " + labels + " not located, skipped");
                return null;
            }
            var p = location.pose().position$();
            var q = location.pose().orientation();
            float qx = q.x(), qy = q.y(), qz = q.z(), qw = q.w();
            // plane axes in the app space: X (width), Y (height), Z (normal)
            float xx = 1 - 2 * (qy * qy + qz * qz), xy = 2 * (qx * qy + qw * qz), xz = 2 * (qx * qz - qw * qy);
            float yx = 2 * (qx * qy - qw * qz), yy = 1 - 2 * (qx * qx + qz * qz), yz = 2 * (qy * qz + qw * qx);
            float zy = 1 - 2 * (qx * qx + qy * qy);
            if (Math.abs(zy) < 0.9f) {
                log("[MR] Scene tables: " + labels + " not horizontal, skipped");
                return null;
            }
            float w = rect.extent().width(), h = rect.extent().height();
            float cu = rect.offset().x() + w * 0.5f, cv = rect.offset().y() + h * 0.5f;
            float cx = p.x() + xx * cu + yx * cv;
            float cy = p.y() + xy * cu + yy * cv;
            float cz = p.z() + xz * cu + yz * cv;
            float len = (float) Math.sqrt(xx * xx + xz * xz);
            if (len < 1e-4f) {
                return null;
            }
            return new SceneTables.Table(cx, cy, cz, xx / len, xz / len, w, h, labels);
        }
    }

    private boolean isEnabled(XrSpace space, int component, MemoryStack stack) {
        var status = XrSpaceComponentStatusFB.calloc(stack).type(FBSpatialEntity.XR_TYPE_SPACE_COMPONENT_STATUS_FB);
        int result = FBSpatialEntity.xrGetSpaceComponentStatusFB(space, component, status);
        return result >= 0 && status.enabled();
    }

    private String labels(XrSpace space, MemoryStack stack) {
        var labels = XrSemanticLabelsFB.calloc(stack).type(FBScene.XR_TYPE_SEMANTIC_LABELS_FB);
        int result = FBScene.xrGetSpaceSemanticLabelsFB(session, space, labels);
        if (result < 0 || labels.bufferCountOutput() == 0) {
            return "";
        }
        ByteBuffer buffer = stack.calloc(labels.bufferCountOutput());
        labels.bufferCapacityInput(buffer.capacity()).buffer(buffer);
        result = FBScene.xrGetSpaceSemanticLabelsFB(session, space, labels);
        if (result < 0) {
            return "";
        }
        int n = Math.min(labels.bufferCountOutput(), buffer.capacity());
        byte[] bytes = new byte[n];
        buffer.get(0, bytes);
        int end = 0;
        while (end < n && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    private void destroyFound() {
        for (XrSpace space : found) {
            XR10.xrDestroySpace(space);
        }
        found.clear();
    }

    public void destroy() {
        queryRequest = 0;
        destroyFound();
        supported = false;
    }

    private void log(String message) {
        vrProvider.getLogger().logInfo(message);
    }

    private void logError(String message) {
        vrProvider.getLogger().logError(message);
    }
}
