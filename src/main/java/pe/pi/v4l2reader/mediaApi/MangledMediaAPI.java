package pe.pi.v4l2reader.mediaApi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import static pe.pi.v4l2reader.mediaApi.mediaAPI_3.SYMBOL_LOOKUP;

/**
 *
 * @author thp
 *
 * Hideous override to mangle the names for C++ linker compatibility
 *
 */
public class MangledMediaAPI extends mediaAPI {

    static final Map<String, String> unmangle = Map.ofEntries(
            Map.entry("v4l2_video_stream_on", "_Z20v4l2_video_stream_onP12media_entityi"),
            Map.entry("v4l2_video_req_bufs", "_Z19v4l2_video_req_bufsP12media_entityP19v4l2_requestbuffers"),
            Map.entry("v4l2_video_query_buf", "_Z20v4l2_video_query_bufP12media_entityP11v4l2_buffer"),
            Map.entry("v4l2_video_q_buf", "_Z16v4l2_video_q_bufP12media_entityP11v4l2_buffer"),
            Map.entry("v4l2_video_dq_buf", "_Z17v4l2_video_dq_bufP12media_entityP11v4l2_buffer"),
            Map.entry("v4l2_video_get_capability", "_Z25v4l2_video_get_capabilityP12media_entityP15v4l2_capability"),
            Map.entry("v4l2_video_stream_off", "_Z21v4l2_video_stream_offP12media_entityi"),
            Map.entry("v4l2_video_get_format", "_Z21v4l2_video_get_formatP12media_entityP11v4l2_format"),
            Map.entry("media_device_new", "_Z16media_device_newPKc"),
            Map.entry("mediaStreamConfig", "_Z17mediaStreamConfigP12media_streamP20stream_configuration"),
            Map.entry("mediaStreamInit", "_Z15mediaStreamInitP12media_streamP12media_device"),
            Map.entry("media_set_wdrMode", "_Z17media_set_wdrModeP12media_streamj"),
            Map.entry("matchLensConfig", "_Z15matchLensConfigP12media_stream"),
            Map.entry("matchSensorConfig", "_Z17matchSensorConfigP12media_stream"),
            Map.entry("lens_set_entity", "_Z15lens_set_entityP10lensConfigP12media_entity"),
            Map.entry("cmos_set_sensor_entity", "_Z22cmos_set_sensor_entityP12sensorConfigP12media_entityi"),
            Map.entry("cmos_sensor_control_cb", "_Z22cmos_sensor_control_cbP12sensorConfigP21ALG_SENSOR_EXP_FUNC_S"),
            Map.entry("cmos_get_sensor_calibration", "_Z27cmos_get_sensor_calibrationP12sensorConfigP12media_entityP17aisp_calib_info_s")
    );

    static MemorySegment findOrThrow(String symbol) {
        return SYMBOL_LOOKUP.find(unmangle.get(symbol))
                .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + symbol));
    }

    private static class matchSensorConfig {

        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("matchSensorConfig");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static MemorySegment matchSensorConfig(MemorySegment stream) {
        var mh$ = matchSensorConfig.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("matchSensorConfig", stream);
            }
            return (MemorySegment) mh$.invokeExact(stream);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class matchLensConfig {

        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("matchLensConfig");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static MemorySegment matchLensConfig(MemorySegment stream) {
        var mh$ = matchLensConfig.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("matchLensConfig", stream);
            }
            return (MemorySegment) mh$.invokeExact(stream);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class lens_set_entity {

        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("lens_set_entity");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static void lens_set_entity(MemorySegment cfg, MemorySegment lens_ent) {
        var mh$ = lens_set_entity.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lens_set_entity", cfg, lens_ent);
            }
            mh$.invokeExact(cfg, lens_ent);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class cmos_set_sensor_entity {

        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER,
                mediaAPI.C_INT
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("cmos_set_sensor_entity");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static void cmos_set_sensor_entity(MemorySegment cfg, MemorySegment sens_ent, int v) {
        var mh$ = cmos_set_sensor_entity.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lens_set_entity", cfg, sens_ent, v);
            }
            mh$.invokeExact(cfg, sens_ent, v);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class cmos_sensor_control_cb {

        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("cmos_sensor_control_cb");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static void cmos_sensor_control_cb(MemorySegment cfg, MemorySegment fun) {
        var mh$ = cmos_sensor_control_cb.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("cmos_sensor_control_cb", cfg, fun);
            }
            mh$.invokeExact(cfg, fun);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class cmos_get_sensor_calibration {

        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER,
                mediaAPI.C_POINTER
        );

        public static final MemorySegment ADDR = MangledMediaAPI.findOrThrow("cmos_get_sensor_calibration");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static void cmos_get_sensor_calibration(MemorySegment cfg, MemorySegment sens_ent, MemorySegment calib) {
        var mh$ = cmos_get_sensor_calibration.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("cmos_get_sensor_calibration", cfg, sens_ent, calib);
            }
            mh$.invokeExact(cfg, sens_ent, calib);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /* hand rolled stuff */
    public static final GroupLayout aml_isp_csc_attrLayout = MemoryLayout.structLayout(
            MangledMediaAPI.C_INT.withName("csc_enable"),
            MangledMediaAPI.C_INT.withName("glb_brightness"),
            MangledMediaAPI.C_INT.withName("glb_contrast"),
            MangledMediaAPI.C_INT.withName("glb_sharpness"),
            MangledMediaAPI.C_INT.withName("glb_sturation"),
            MangledMediaAPI.C_INT.withName("glb_hue"),
            MangledMediaAPI.C_INT.withName("glb_vibrance")
    ).withName("aml_isp_csc_attr");

    public static final GroupLayout aisp_api_type_tLayout = MemoryLayout.structLayout(
            mediaAPI.C_CHAR.withName("direction"),
            mediaAPI.C_CHAR.withName("cmdType"),
            mediaAPI.C_CHAR.withName("cmdId"),
            mediaAPI.C_CHAR.withName("pad"),
            mediaAPI.C_INT.withName("value"),
            mediaAPI.C_POINTER.withName("pData"),
            mediaAPI.C_POINTER.withName("pRetValue")
    ).withName("aisp_api_type_t");

}
