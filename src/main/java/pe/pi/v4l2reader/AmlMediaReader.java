/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import static pe.pi.v4l2reader.V4l2Ioctls.MAP_SHARED;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_READ;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_WRITE;
//import pe.pi.v4l2reader.mediaApi.ALG_SENSOR_DEFAULT_S;
import pe.pi.v4l2reader.mediaApi.AML_ALG_CTX_S;
import pe.pi.v4l2reader.mediaApi.MangledMediaAPI;
import static pe.pi.v4l2reader.mediaApi.MangledMediaAPI.segToString;
import pe.pi.v4l2reader.mediaApi.aml_format;
import pe.pi.v4l2reader.mediaApi.media_entity;
import pe.pi.v4l2reader.mediaApi.media_stream;
import pe.pi.v4l2reader.mediaApi.stream_configuration;
import pe.pi.v4l2reader.mediaApi.v4l2_buffer;
import pe.pi.v4l2reader.mediaApi.v4l2_capability;
import pe.pi.v4l2reader.mediaApi.v4l2_format;
import pe.pi.v4l2reader.mediaApi.v4l2_pix_format;
import pe.pi.v4l2reader.mediaApi.v4l2_requestbuffers;

/**
 *
 * @author thp
 */
public class AmlMediaReader implements MmapReader {

    java.nio.file.Path path;

    byte[] out;
    int videoDev = 0;
    int width;
    int height;
    private final Arena arena;
    //MemorySegment video_ent;
    private final MethodHandle aisp_enable;
    private final MethodHandle alg2User;
    private final MethodHandle alg2Kernel;
    private final MethodHandle algFwInterface;
    private MediaEntity vent;
    private MediaEntity pent;
    private Queue<Runnable> v4lQueue;
    private MediaEntity sent;
    //  private final MethodHandle fps_set_imx678;
    private String sensorname;
    private long exposure = 900;
    private int autoExposure;
    private int strategy;
    private int exmode;
    private final MethodHandle aml_debug_mode_set;

    public AmlMediaReader(String dev, int w, int h) throws Throwable {
        width = w;
        height = h;
        path = java.nio.file.Paths.get(dev);
        arena = Arena.global();
        v4lQueue = new ConcurrentLinkedQueue();
        Linker linker = Linker.nativeLinker();

        var ispLib = SymbolLookup.libraryLookup("libispaml.so", arena);

        aisp_enable = linker.downcallHandle(
                ispLib.find("aisp_enable").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_INT, // uint32_t ctx_id (mapped to int)
                        ValueLayout.ADDRESS, // void* pstAlgCtx
                        ValueLayout.ADDRESS // void* calib
                )
        );
        alg2User = linker.downcallHandle(
                ispLib.find("aisp_alg2user").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_INT, // uint32_t ctx_id (mapped to int)
                        ValueLayout.ADDRESS // void* alg_init
                )
        );
        alg2Kernel = linker.downcallHandle(
                ispLib.find("aisp_alg2kernel").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_INT, // uint32_t ctx_id (mapped to int)
                        ValueLayout.ADDRESS // void* alg_init
                )
        );
        algFwInterface = linker.downcallHandle(
                ispLib.find("aisp_fw_interface").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_INT, // uint32_t ctx_id (mapped to int)
                        ValueLayout.ADDRESS // void* api_type
                )
        );
        aml_debug_mode_set = linker.downcallHandle(
                ispLib.find("aml_debug_mode_set").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, // int retval 0 or -1
                        ValueLayout.JAVA_INT // debug (1 or 0)
                )
        );

        /*
        var camLib = SymbolLookup.libraryLookup("libtuning.so", arena);
        fps_set_imx678 = linker.downcallHandle(
                camLib.find("_Z19cmos_fps_set_imx678ifP20ALG_SENSOR_DEFAULT_S").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.JAVA_INT, // int ViPipe,  
                        ValueLayout.JAVA_FLOAT, // float f32Fps,
                        ValueLayout.ADDRESS // ALG_SENSOR_DEFAULT_S *pstAeSnsDft
                )
        );
         */
        if (Files.isReadable(path)) {
            setup();

        } else {
            Log.error("cant read " + path);
        }

    }

    private void printStreamNames(MemorySegment mediaS) {
        /*
         *     char media_dev_name[64];
 *     char lens_ent_name[32];
 *     char sensor_ent_name[32];
 *     char csiphy_ent_name[32];
 *     char adap_ent_name[32];
 *     char isp_ent_name[32];
 *     char video_ent_name0[32];
 *     char video_ent_name1[32];
 *     char video_ent_name2[32];
 *     char video_ent_name3[32];
 *     char video_stats_name[32];
 *     char video_param_name[32];
         */
        StringBuilder mess = new StringBuilder();
        mess.append("\tmedia_dev_name ").append(segToString(media_stream.media_dev_name(mediaS)));
        mess.append("\n\tlens_ent_name ").append(segToString(media_stream.lens_ent_name(mediaS)));
        mess.append("\n\tsensor_ent_name ").append(segToString(media_stream.sensor_ent_name(mediaS)));
        mess.append("\n\tcsiphy_ent_name ").append(segToString(media_stream.csiphy_ent_name(mediaS)));
        mess.append("\n\tadap_ent_name ").append(segToString(media_stream.adap_ent_name(mediaS)));
        mess.append("\n\tisp_ent_name ").append(segToString(media_stream.isp_ent_name(mediaS)));
        mess.append("\n\tvideo_ent_name0 ").append(segToString(media_stream.video_ent_name0(mediaS)));
        mess.append("\n\tvideo_ent_name1 ").append(segToString(media_stream.video_ent_name1(mediaS)));
        mess.append("\n\tvideo_ent_name2 ").append(segToString(media_stream.video_ent_name2(mediaS)));
        mess.append("\n\tvideo_ent_name3 ").append(segToString(media_stream.video_ent_name3(mediaS)));
        mess.append("\n\tvideo_param_name ").append(segToString(media_stream.video_param_name(mediaS)));
        Log.info("MediaStream Names " + mess.toString());

    }

    private void printcaps(MemorySegment v4l2_cap) {
        /*
         * struct v4l2_capability {
 *     __u8 driver[16];
 *     __u8 card[32];
 *     __u8 bus_info[32];
 *     __u32 version;
 *     __u32 capabilities;
 *     __u32 device_caps;
 *     __u32 reserved[3];
 * }
         */
        String driver = segToString(v4l2_capability.driver(v4l2_cap));
        String card = segToString(v4l2_capability.card(v4l2_cap));

        String bus_info = segToString(v4l2_capability.bus_info(v4l2_cap));
        int version = v4l2_capability.version(v4l2_cap);
        int capabilities = v4l2_capability.capabilities(v4l2_cap);
        int device_caps = v4l2_capability.device_caps(v4l2_cap);

        Log.info("caps: driver=" + driver + "\tcard=" + card + "\tbus_info" + bus_info);
        Log.info("caps: version=" + version + "\tcapabilities=" + Integer.toBinaryString(capabilities) + "\tdevice_caps=" + Integer.toBinaryString(device_caps));
    }

    private String getAE() {
        return (autoExposure == 0 ? "1" : "0");
    }

    private void setAE(Long v) {
        autoExposure = v == 0 ? 1 : 0;
        setExposure(exposure);
    }

    private Long getExposure() {
        int estructLen = 168;
        int ints = 168/4;
        MemorySegment attr = arena.allocate(estructLen);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        direction.set(cmd, 0L, (byte) 0x0); //get
        cmdId.set(cmd, 0L, (byte) 40); // AML_MBI_ISP_ExposureAttr;
        pData.set(cmd, 0L, attr);
        try {
            Log.info("trying to get exposure data ");

            algFwInterface.invokeExact(0, cmd);
            var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
            for (int i = 0; i < ints; i++) {
                Log.info("Exposure attr[" + i + "] = " + bb.getInt());
            }
            exposure = bb.getInt(this.stManual_u32ExpTime);
        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
        return exposure;
    }

    enum CsCNames {
        unused, brightness, contrast, sharpness, saturation, hue, vibrance
    };
    final static HashMap<Integer, CsCNames> CsCNameMap = new HashMap();

    static {
        CsCNameMap.put(CsCNames.unused.ordinal(), CsCNames.unused);
        CsCNameMap.put(CsCNames.brightness.ordinal(), CsCNames.brightness);
        CsCNameMap.put(CsCNames.contrast.ordinal(), CsCNames.contrast);
        CsCNameMap.put(CsCNames.sharpness.ordinal(), CsCNames.sharpness);
        CsCNameMap.put(CsCNames.saturation.ordinal(), CsCNames.saturation);
        CsCNameMap.put(CsCNames.hue.ordinal(), CsCNames.hue);
        CsCNameMap.put(CsCNames.vibrance.ordinal(), CsCNames.vibrance);
    }

    Integer[] getCsC() {

        Integer[] ret = new Integer[7];

        MemorySegment attr = arena.allocate(MangledMediaAPI.aml_isp_csc_attrLayout);
        MemorySegment rattr = arena.allocate(MangledMediaAPI.aml_isp_csc_attrLayout);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdType = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdType"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle value = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("value"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        VarHandle pRetValue = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pRetValue"));
        direction.set(cmd, 0L, (byte) 0x0); //get
        cmdId.set(cmd, 0L, (byte) 0x1); // AML_MBI_ISP_CSCAttr
        pData.set(cmd, 0L, attr);
        pRetValue.set(cmd, 0L, rattr);
        try {
            Log.info("trying to get csc ");

            algFwInterface.invokeExact(0, cmd);
            Log.info("csc attr values are :");
            var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
            for (int i = 0; i < 7; i++) {
                ret[i] = bb.getInt();
                Log.info(CsCNameMap.get(i) + " = " + ret[i]);
            }

        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
        return ret;
    }

    enum RoiNames {
        en, wgt, x, y, width, height, target
    };
    final static HashMap<Integer, RoiNames> RoiNameMap = new HashMap();

    static {
        RoiNameMap.put(RoiNames.en.ordinal(), RoiNames.en);
        RoiNameMap.put(RoiNames.wgt.ordinal(), RoiNames.wgt);
        RoiNameMap.put(RoiNames.x.ordinal(), RoiNames.x);
        RoiNameMap.put(RoiNames.y.ordinal(), RoiNames.y);
        RoiNameMap.put(RoiNames.width.ordinal(), RoiNames.width);
        RoiNameMap.put(RoiNames.height.ordinal(), RoiNames.height);
        RoiNameMap.put(RoiNames.target.ordinal(), RoiNames.target);
    }
    private final static int AML_MBI_ISP_AERoiAttr = 52;

    private Long getAERoi() {
        Long ret = 0L;
        int len = 16 * (7 * 4);

        MemorySegment attr = arena.allocate(len);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        direction.set(cmd, 0L, (byte) 0x0); //get
        cmdId.set(cmd, 0L, (byte) AML_MBI_ISP_AERoiAttr); // AML_MBI_ISP_CSCAttr
        pData.set(cmd, 0L, attr);
        try {
            Log.info("trying to get roi ");

            algFwInterface.invokeExact(0, cmd);
            Log.info("roi attr values are :");
            var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
            for (int at = 0; at < 16; at++) {
                for (int i = 0; i < 7; i++) {
                    int nv = bb.getInt();
                    Log.info(RoiNameMap.get(i) + "[" + at + "]" + " = " + nv);
                }
            }
            bb.rewind();
            int x = bb.getInt(8) * 128 / width;
            int y = bb.getInt(12) * 128 / height;
            int x2 = x + bb.getInt(16) * 128 / width;
            int y2 = y + bb.getInt(20) * 128 / height;
            ret = (long) (x << 24) + (y << 16) + (x2 << 8) + y2;

        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
        Log.info("returning roi of " + ret);

        return ret;
    }

    //AML_MBI_ISP_AERouterAttr
    void setAERoiActual(Long v) {

//        typedef struct {
//    uint32_t ae_roi_en;            /**< u1, AE ROI0 work mode. 0:off 1:on */
//    uint32_t ae_roi_wgt;           /**< u4, ROI0 zone exposure weight */
//    uint32_t ae_roi_x_st;          /**< u14, ROI0 rectangle zone start position x  */
//    uint32_t ae_roi_y_st;          /**< u14, ROI0 rectangle zone start position y  */
//    uint32_t ae_roi_width;         /**< u14, ROI0 rectangle zone width  */
//    uint32_t ae_roi_height;        /**< u14, ROI0 rectangle zone height */
//    uint32_t ae_roi_target;
//} aisp_ae_roi;
//typedef struct {
//    aisp_ae_roi ae_roi[16];
//} aml_isp_ae_roi_attr;
        int len = 16 * (7 * 4);

        MemorySegment attr = arena.allocate(len);
        MemorySegment rattr = arena.allocate(len);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        cmdId.set(cmd, 0L, (byte) AML_MBI_ISP_AERoiAttr); // AML_MBI_ISP_AERoiAttr
        pData.set(cmd, 0L, attr);
        direction.set(cmd, 0L, (byte) 01); //set
        Log.info("new roi attr values are :");
        var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
        bb.putInt(0x00000001); //en
        bb.putInt(15); //weight
        //           let v = (x << 24) + (y << 16) + (x2 << 8) + y2;
        int x = (int) ((width * ((0xff) & (v >>> 24))) / 128);
        bb.putInt(x);
        int y = (int) ((height * ((0xff) & (v >>> 16))) / 128);
        bb.putInt(y);
        int x2 = (int) ((width * ((0xff) & (v >>> 8))) / 128);
        bb.putInt((x2 - x));
        int y2 = (int) ((height * ((0xff) & (v))) / 128);
        bb.putInt((y2 - y));
        int roiTarget = (int) exposure; // shrug?
        bb.putInt(roiTarget);
        bb.rewind();
        for (int i = 0; i < 7; i++) {
            int nv = bb.getInt();
            Log.info("\tset" + RoiNameMap.get(i) + " = " + nv);
        }
// only set the first one - the rest are all zero implicitly disabled.
        try {
            Log.info("trying to set AE roi ");

            //printSevenInt(attr);
            algFwInterface.invokeExact(0, cmd);

            /*
            api_type->u8Direction = AML_CMD_SET;
            api_type->u8CmdType = 0;// not used
            api_type->u8CmdId = AML_MBI_ISP_ExposureAttr;
            api_type->u32Value = 0;// not used
            api_type->pData = (uint32_t *)&data;
             */
        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
    }

    private void setAERoi(Long newv) {
        Runnable task = new Runnable() {
            Long v = newv;

            @Override
            public void run() {
                getAERoi();
                setAERoiActual(v);
            }

        };
        v4lQueue.add(task);
    }

    void setCsC(Integer[] csc) {
        Runnable task = new Runnable() {
            Integer v[] = csc;

            @Override
            public void run() {
                setCsCActual(v);
            }
        };
        v4lQueue.add(task);
    }

    void setExposure(Long newv) {
        exposure = newv;
        Log.info("set exposure var to " + exposure);

        Runnable task = new Runnable() {
            Long v = newv;

            @Override
            public void run() {
                Log.info("Not directly setting exposure to " + v);
                setExposureActual(v);

            }
        };
        v4lQueue.add(task);

    }

    /*
    int IspMgr::set_exposure_time(int shuttime_value) {
  aisp_api_type_t param;
  isp_exposure_attr_s data;
  aisp_api_type_t *api_type = &param;
  isp_exposure_attr_s *attr = &data;
  api_type->u8Direction = AML_CMD_GET;
  api_type->u8CmdType = 0; // not used
  api_type->u8CmdId = AML_MBI_ISP_ExposureAttr;
  api_type->u32Value = 0; // not used
  api_type->pData = (uint32_t *)&data;
  (IspMgr::mIspIF.algFwInterface)(mId, api_type);

  attr->stManual.enExpTimeOpType = OP_TYPE_MANUAL;
  attr->stManual.u32ExpTime = shuttime_value;
  api_type->u8Direction = AML_CMD_SET;
  (IspMgr::mIspIF.algFwInterface)(mId, api_type);
  return 0;
}

    */
    int stManual_enExpTimeOpTyp = 4;
    int stManual_u32ExpTime = 24;
    int stAuto_enExposMode =44;
    void setExposureActual(Long v) {
        int estructLen = 168;
        int val = v.intValue();
        MemorySegment attr = arena.allocate(estructLen);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        direction.set(cmd, 0L, (byte) 0x0); //get
        cmdId.set(cmd, 0L, (byte) 40); // AML_MBI_ISP_ExposureAttr;
        pData.set(cmd, 0L, attr);
        try {
            Log.info("trying to get exposure settings ");
            algFwInterface.invokeExact(0, cmd);
            var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
            Log.info("bypass was "+bb.getInt(0));
            bb.putInt(0,0);
            Log.info("bypass will be "+bb.getInt(0));
            
            Log.info("stManual_enExpTimeOpTyp "+bb.getInt(stManual_enExpTimeOpTyp));
            bb.putInt(stManual_enExpTimeOpTyp,((this.autoExposure == 1)?0:1));
            Log.info("stManual_enExpTimeOpTyp "+bb.getInt(stManual_enExpTimeOpTyp));
            
            int xmo = bb.getInt(stAuto_enExposMode);
            Log.info("stAuto_enExposMode was "+xmo);
            if (this.autoExposure==1) {
                xmo++;
                if (xmo > 4){
                    xmo = 0;
                }
            }
            Log.info("stAuto_enExposMode will be "+xmo);
            bb.putInt(stAuto_enExposMode,xmo);

            bb.putInt(stManual_u32ExpTime,val);
            direction.set(cmd, 0L, (byte) 0x1); //set
            Log.info("trying to set exposure settings ");
            algFwInterface.invokeExact(0, cmd);
        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
        /*
        typedef enum _ISP_OP_TYPE_E
{
    OP_TYPE_AUTO    = 0,
    OP_TYPE_MANUAL  = 1,
    OP_TYPE_LUT     = 2,
    OP_TYPE_MAX
} ISP_OP_TYPE_E;
        
    typedef ISP_OP_TYPE_E     aml_isp_op_type;
        
        typedef struct {
    aml_isp_op_type enExpTimeOpType;
    aml_isp_op_type enAGainOpType;
    aml_isp_op_type enDGainOpType;
    aml_isp_op_type enISPDGainOpType;
    aml_isp_op_type enExposureRatio;

    uint32_t u32ExpTime;
    uint32_t u32AGain;
    uint32_t u32DGain;
    uint32_t u32ISPDGain;
        uint32_t u32ExposureRatio;
} ISP_ME_ATTR_S;
        typedef struct _ISP_AE_ATTR_S 
       {
    ISP_EXP_MODE_E enExposMode;
    ISP_STRATEGY_E enExposStrategy;
    ISP_ROUTE_STRATEGY_E enRouteStrategy;
    ISP_ROUTE_DEFLICKER_MODE_E enRouteDeflkrMode;
    uint32_t u32Convergence;
    uint32_t u32Compensation;
    uint32_t u32LumaTarget;
    uint32_t u32LumaHdrTarget;
    uint32_t u32LowlightMode;
    uint32_t u32LowlightStr;
    uint32_t u32LowlightGainMax;
    uint32_t u32HighlightTh;
    uint32_t u32HighlightStr;
    uint32_t u32Tolerance;
    mbp_bool_e bEnDelay;
    uint32_t u32DelayCnt;
    uint32_t u32DelayTol;
    uint32_t u32LongClip;
    uint32_t u32ErAvgCoeff;
    mbp_bool_e bEnReduceFps;
    uint32_t u32ReduceFps;
    uint32_t u32ReduceFpsTh;
    uint32_t u32ReduceFpsLag;
    uint32_t u32EnableGdg;
} ISP_AE_ATTR_S;
        
        typedef struct {
    mbp_bool_e      bByPass;
    ISP_ME_ATTR_S       stManual;
    ISP_AE_ATTR_S       stAuto;
} isp_exposure_attr_s;
        
         */
    }

    public void setCsCActual(Integer[] csc) {

        MemorySegment attr = arena.allocate(MangledMediaAPI.aml_isp_csc_attrLayout);
        MemorySegment rattr = arena.allocate(MangledMediaAPI.aml_isp_csc_attrLayout);

        MemorySegment cmd = arena.allocate(MangledMediaAPI.aisp_api_type_tLayout);

        VarHandle direction = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("direction"));
        VarHandle cmdType = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdType"));
        VarHandle cmdId = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("cmdId"));
        VarHandle value = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("value"));
        VarHandle pData = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pData"));
        VarHandle pRetValue = MangledMediaAPI.aisp_api_type_tLayout.varHandle(PathElement.groupElement("pRetValue"));
        cmdId.set(cmd, 0L, (byte) 0x1); // AML_MBI_ISP_CSCAttr
        pData.set(cmd, 0L, attr);
        pRetValue.set(cmd, 0L, rattr);
        direction.set(cmd, 0L, (byte) 0x1); //set
        Log.info("new csc attr values are :");
        var bb = attr.asByteBuffer().order(ByteOrder.nativeOrder());
        if (csc.length == 7) {
            for (int i = 0; i < 7; i++) {
                Log.info(CsCNameMap.get(i) + " = " + csc[i]);
                bb.putInt(csc[i]);
            }
        }
        try {
            Log.info("trying to set csc ");

            //printSevenInt(attr);
            algFwInterface.invokeExact(0, cmd);

            /*
            api_type->u8Direction = AML_CMD_SET;
            api_type->u8CmdType = 0;// not used
            api_type->u8CmdId = AML_MBI_ISP_ExposureAttr;
            api_type->u32Value = 0;// not used
            api_type->pData = (uint32_t *)&data;
             */
        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }

    }

    /*
    public void getFrameRate(MemorySegment ent, String name) {
        int rc;
        var frac = arena.allocate(V4l2Structs.v4l2_fract);
        rc = MangledMediaAPI.v4l2_subdev_get_frame_interval(ent, frac);
        if (rc < 0) {
            Log.error(name + " v4l2_subdev_get_frame_interval failed");
        } else {
            var n = frac.get(JAVA_INT, V4l2Structs.v4l2_fract.byteOffset(groupElement("numerator")));
            var d = frac.get(JAVA_INT, V4l2Structs.v4l2_fract.byteOffset(groupElement("denominator")));

            Log.info(name + " v4l2_subdev_get_frame_interval ok " + n + "/" + d);
        }
    }
     */
    public String getSensorName() {
        return sensorname;
    }

    public int setup() throws Throwable {
        int fd = 0;
        MemorySegment devName = arena.allocateFrom(path.toString());

        MemorySegment v4l2_media_stream = media_stream.allocate(arena);

        var mediaDev = MangledMediaAPI.media_device_new(devName);

        int rc = MangledMediaAPI.mediaStreamInit(v4l2_media_stream, mediaDev);
        if (rc != 0) {
            Log.error("mediaStreamInit failed");
            return -1;
        }
        printStreamNames(v4l2_media_stream);
        sensorname = segToString(media_stream.sensor_ent_name(v4l2_media_stream));
        var video_ent = media_stream.video_ent0(v4l2_media_stream);
        fd = media_entity.fd(video_ent);
        Log.info("Inited Media Stream video fd is " + fd);

        var v4l2_cap = v4l2_capability.allocate(arena);

        MangledMediaAPI.v4l2_video_get_capability(video_ent, v4l2_cap);
        Log.info("Got v4l2 caps for Media Stream video fd is " + fd);
        printcaps(v4l2_cap);

        MangledMediaAPI.media_set_wdrMode(v4l2_media_stream, 0);
        MangledMediaAPI.media_set_wdrMode(v4l2_media_stream, MangledMediaAPI.WDR_MODE_NONE());

        var stream_config = stream_configuration.allocate(arena);
        var format = stream_configuration.format(stream_config);
        aml_format.width(format, width);
        aml_format.height(format, height);
        aml_format.fourcc(format, V4l2Structs.V4L2_PIX_FMT_NV12);
        aml_format.code(format, MangledMediaAPI.MEDIA_BUS_FMT_SRGGB12_1X12());
        aml_format.nplanes(format, 1);

        var vformat = stream_configuration.vformat(stream_config);
        aml_format.width(vformat, width);
        aml_format.height(vformat, height);
        aml_format.fourcc(vformat, V4l2Structs.V4L2_PIX_FMT_NV12);

        rc = MangledMediaAPI.mediaStreamConfig(v4l2_media_stream, stream_config);
        if (rc < 0) {
            Log.error("mediaStreamConfig failed ");
            return -1;
        }
        Log.info(" mediaStreamConfig ok " + fd);

        var v4l2_fmt = v4l2_format.allocate(arena);
        v4l2_format.type(v4l2_fmt, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        var fmt = v4l2_format.fmt(v4l2_fmt);
        var pix = v4l2_format.fmt.pix(fmt);
        v4l2_pix_format.width(pix, width);
        v4l2_pix_format.height(pix, height);
        v4l2_pix_format.pixelformat(pix, V4l2Structs.V4L2_PIX_FMT_NV12);
        v4l2_pix_format.field(pix, MangledMediaAPI.V4L2_FIELD_ANY());

        rc = MangledMediaAPI.v4l2_video_get_format(video_ent, v4l2_fmt);
        if (rc < 0) {
            Log.error("v4l2_video_get_format failed ");
            return -1;
        }
        Log.info(" v4l2_video_get_format ok " + fd);

        vent = new MediaEntity(video_ent, 4, "video");

        var datastream_config = stream_configuration.allocate(arena);
        var dataformat = stream_configuration.format(datastream_config);
        aml_format.width(dataformat, width);
        aml_format.height(dataformat, height);
        aml_format.nplanes(dataformat, 1);
        //           stream_configuration_t     stream_config;
        //   stream_config.format.width = 1024;
        //   stream_config.format.height = 256;
        //   stream_config.format.nplanes   = 1;

//    rc = setDataFormat(&v4l2_media_stream, &stream_config);
// mediaApi.h:int setDataFormat(media_stream_t *camera, stream_configuration_t *cfg);
        MangledMediaAPI.setConfigFormat(v4l2_media_stream, datastream_config);
        MangledMediaAPI.setDataFormat(v4l2_media_stream, datastream_config);

        var video_param = media_stream.video_param(v4l2_media_stream);
        var video_ent0 = media_stream.video_ent0(v4l2_media_stream);
        var video_stats = media_stream.video_stats(v4l2_media_stream);
        /*
        getFrameRate(video_param, "video parm");
        getFrameRate(video_ent0, "video ent0");
        getFrameRate(video_stats, "video stats");
         */
        pent = new MediaEntity(video_param, 1, "params");

        sent = new MediaEntity(video_stats, 4, "stats");

        MemorySegment sensor_ent = media_stream.sensor_ent(v4l2_media_stream);

        videoDev = media_entity.fd(video_ent0);

        MemorySegment sensorCfg = MangledMediaAPI.matchSensorConfig(v4l2_media_stream);
        MemorySegment lensCfg = MangledMediaAPI.matchLensConfig(v4l2_media_stream);
        var pstAlgCtx = AML_ALG_CTX_S.allocate(arena);
        MemorySegment stSnsExp = AML_ALG_CTX_S.stSnsExp(pstAlgCtx);
        Log.debug("lens config is at " + lensCfg.address());
        if (lensCfg.address() > 0) {
            Log.debug("trying to config the lens " + lensCfg.address());

            MemorySegment lens_ent = media_stream.lens_ent(v4l2_media_stream);
            MangledMediaAPI.lens_set_entity(lensCfg, lens_ent);
            //lens_control_cb(tparm->lensCfg, &tparm->pstAlgCtx.stLensFunc);
        }

        Log.info("calibrating the camera and enabling the isp");
        MemorySegment calib = arena.allocate(1216 * 2); // Yeah, I know this is horrible - but, look at the nested enumed struct and just get the compiler to do a sizeof then double it!
        MangledMediaAPI.cmos_set_sensor_entity(sensorCfg, sensor_ent, 0);
        MangledMediaAPI.cmos_sensor_control_cb(sensorCfg, stSnsExp);
        MangledMediaAPI.cmos_get_sensor_calibration(sensorCfg, sensor_ent, calib);
        int ctx = 0;
        /*     var sdf = AML_ALG_CTX_S.stSnsDft(pstAlgCtx);

        printCtxFps(sdf);
        setCtxFps(sdf, 30);

        setIspFps(ctx, 30.0f,sdf);
        
         */
        Log.info("about to aml_debug_mode_set(1) ");
        int res = (int) aml_debug_mode_set.invokeExact(1);
        Log.info("aml_debug_mode_set(1) returned " + res);

        Log.info("about to run aisp_enable ");

        aisp_enable.invokeExact(ctx, pstAlgCtx, calib);
        /*    setIspFps(ctx, 30.0f,sdf);
        printCtxFps(sdf);
        setCtxFps(sdf, 30);
        printCtxFps(sdf);
         */

        MemorySegment alg_init = arena.allocate(256 * 1024);
        alg_init.fill((byte) 0);
        Log.info("about to run alg2User ");

        alg2User.invokeExact(ctx, alg_init);
        Log.info("about to run alg2Kernel");
        alg2Kernel.invokeExact(ctx, pent.buffers[0].mapped);

        return videoDev;
    }

    /*
    void printCtxFps(MemorySegment ctx) {
        try {
            int fps = ALG_SENSOR_DEFAULT_S.fps(ctx);
            Log.info("ctx thinks fps is " + fps);
        } catch (Throwable t) {
            Log.warn("Can't access ctx segment because " + t.getMessage());
        }
    }

    void setCtxFps(MemorySegment ctx, int fps) {
        try {
            ALG_SENSOR_DEFAULT_S.fps(ctx, fps);
            Log.info("ctx set fps to " + fps);
        } catch (Throwable t) {
            Log.warn("Can't access ctx segment because " + t.getMessage());
        }
    }

    void setIspFps(int ctx, float fps,MemorySegment sdf) {
        if (fps_set_imx678 != null) {
            try {
                fps_set_imx678.invokeExact(ctx, fps, sdf);
            } catch (Throwable ex) {
                Log.error("cant set fps because " + ex.getMessage());
            }
        }
    }
     */
    @Override
    public void startCap() throws Throwable {
        vent.start();
        startStats();
        startParams();
    }

    @Override
    public void stop() throws Throwable {
        vent.stop();
    }

    class DQException extends RuntimeException {

        DQException(String fail) {
            super(fail);
        }
    };

    public V4l2Substitute getV4l2Sub() {
        final var that = this;
        return new V4l2Substitute() {

            @Override
            public void setBrightness(Long v) {
                Integer[] o = getCsC();
                o[CsCNames.brightness.ordinal()] = v.intValue();
                setCsC(o);
            }

            @Override
            public void setHue(Long v) {
                Integer[] o = getCsC();
                o[CsCNames.hue.ordinal()] = v.intValue();
                setCsC(o);
            }

            @Override
            public void setContrast(Long v) {
                Integer[] o = getCsC();
                o[CsCNames.contrast.ordinal()] = v.intValue();
                setCsC(o);
            }

            @Override
            public void setSaturation(Long v) {
                Integer[] o = getCsC();
                o[CsCNames.saturation.ordinal()] = v.intValue();
                setCsC(o);
            }

            @Override
            public void setExposure(Long v) {
                that.setExposure(v);
            }

            @Override
            public Long getBrightness() {
                Integer[] o = getCsC();
                return Long.valueOf(o[CsCNames.brightness.ordinal()]);
            }

            @Override
            public Long getHue() {
                Integer[] o = getCsC();
                return Long.valueOf(o[CsCNames.hue.ordinal()]);
            }

            @Override
            public Long getContrast() {
                Integer[] o = getCsC();
                return Long.valueOf(o[CsCNames.contrast.ordinal()]);
            }

            @Override
            public Long getExposure() {
                return that.getExposure();
            }

            @Override
            public Long getSaturation() {
                Integer[] o = getCsC();
                return Long.valueOf(o[CsCNames.saturation.ordinal()]);
            }

            @Override
            public void setAERoi(Long v) {
                that.setAERoi(v);
            }

            @Override
            public Long getAERoi() {
                return that.getAERoi();
            }

            @Override
            public String getSensorName() {
                return that.getSensorName();
            }

            @Override
            public String getAE() {
                return that.getAE();

            }

            @Override
            public void setAE(Long v) {
                that.setAE(v);

            }

        };
    }

    class V4l2Buffer {

        MediaEntity ment;
        MemorySegment mapped;
        MemorySegment addr;
        long moffset;
        long mlength;

        V4l2Buffer(MediaEntity m) {
            ment = m;
        }

        private void unmap(MemorySegment s) throws Throwable {
            if (s == mapped) {
                int res;
                res = (int) Mmap.munmap.invoke(moffset, mlength);
                if (res != 0) {
                    Log.error("munmap failed");
                } else {
                    Log.info("unmapped mapped address of " + mapped.address());
                }
                mapped = null;
            }

        }

        void map(long length, long offset) throws Throwable {
            var dev = ment.getFd();
            addr = (MemorySegment) Mmap.mmap.invokeExact(
                    MemorySegment.NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, dev, offset);
            moffset = offset;
            mlength = length;
            Consumer<MemorySegment> cleanup = s -> {
                try {
                    unmap(s);
                } catch (Throwable e) {
                    Log.error("cant unmap");
                }
            };
            if (addr != null) {
                Log.info("mapped address is " + addr.address());
                mapped = addr.reinterpret(length, arena, cleanup);
                Log.info("mapped re-interpreted to " + mapped.address());
            } else {
                Log.error("failed to map");
            }
        }

        ByteBuffer asByteBuffer() {
            return mapped.asByteBuffer();
        }
    }

    class MediaEntity {

        V4l2Buffer[] buffers;

        MemorySegment buf;
        MemorySegment ent;
        String ent_name;

        MediaEntity(MemorySegment ent, int bcount, String name) throws Throwable {
            this.ent = ent;
            this.ent_name = name;
            var v4l2_rb = v4l2_requestbuffers.allocate(arena);
            v4l2_requestbuffers.count(v4l2_rb, bcount);
            v4l2_requestbuffers.type(v4l2_rb, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
            v4l2_requestbuffers.memory(v4l2_rb, MangledMediaAPI.V4L2_MEMORY_MMAP());
            int rc = MangledMediaAPI.v4l2_video_req_bufs(ent, v4l2_rb);
            if (rc < 0) {
                Log.error("v4l2_video_req_bufs stream failed ");
                throw new RuntimeException("cant get buffers for " + ent_name);
            }
            int acount = v4l2_requestbuffers.count(v4l2_rb);

            Log.info(ent_name + " v4l2_video_req_bufs got " + acount + " wanted " + bcount);

            buf = v4l2_buffer.allocate(arena);
            buffers = new V4l2Buffer[bcount];
            for (int i = 0; i < bcount; i++) {
                buffers[i] = new V4l2Buffer(this);
                mapBuffer(i);
                Log.verb("buffer[" + i + "] = " + buffers[i]);
            }
            Log.info(ent_name + "enqueue video buffers");
            eqBuffers();
        }

        int getFd() {
            return media_entity.fd(ent);
        }

        int eqBuffer() throws Throwable {
            return MangledMediaAPI.v4l2_video_q_buf(ent, buf);
        }

        private void eqBuffers() throws Throwable {
            for (int i = 0; i < buffers.length; i++) {
                v4l2_buffer.index(buf, i);
                eqBuffer();
            }
        }

        V4l2Buffer dqBuffer() throws Throwable {
            v4l2_buffer.type(buf, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
            v4l2_buffer.memory(buf, MangledMediaAPI.V4L2_MEMORY_MMAP());

            int res = MangledMediaAPI.v4l2_video_dq_buf(ent, buf);
            if (res < 0) {
                throw new DQException("VIDIOC_DQBUF failed");
            }
            long offset = getOffset();
            int length = getLength();
            int index = getIndex();

            Log.verb("DQ'd index " + index + " offset = " + offset + " length= " + length);
            return buffers[index];
        }

        private void mapBuffer(int index) throws Throwable {
            v4l2_buffer.index(buf, index);
            v4l2_buffer.type(buf, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
            v4l2_buffer.memory(buf, MangledMediaAPI.V4L2_MEMORY_MMAP());
            int res = MangledMediaAPI.v4l2_video_query_buf(ent, buf);
            if (res < 0) {
                throw new DQException("VIDIOC_QUERYBUF failed");
            }
            long offset = getOffset();
            long length = getLength();
            int rindex = getIndex();
            Log.info("index " + index + " rindex " + rindex + " offset = " + offset + " length= " + length);
            if (out == null) {
                out = new byte[(int) length];
            }
            var vbuf = buffers[index];
            vbuf.map(length, offset);
        }

        private long getOffset() {
            var m = v4l2_buffer.m(buf);
            return v4l2_buffer.m.offset(m);
        }

        private int getLength() {
            return v4l2_buffer.length(buf);
        }

        private int getIndex() {
            return v4l2_buffer.index(buf);
        }

        public void start() {
            int rc = MangledMediaAPI.v4l2_video_stream_on(ent, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
            if (rc < 0) {
                Log.error("cant start v4l2 capture " + ent_name);
            } else {
                Log.info("started capture" + ent_name);
            }
        }

        public void stop() {
            int rc = MangledMediaAPI.v4l2_video_stream_off(ent, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
            if (rc < 0) {
                Log.error("cant start v4l2 capture " + ent_name);
            } else {
                Log.info("started capture" + ent_name);
            }
        }

    }

    public ByteBuffer process(ByteBuffer frame) {
        Log.verb("Got from of " + frame.remaining());
        return frame;
    }

    @Override
    synchronized public ByteBuffer read() throws Throwable {
        var mbuf = vent.dqBuffer();

        Log.verb("mapped size " + mbuf.mapped.byteSize() + " address " + mbuf.mapped.address());
        ByteBuffer fb = mbuf.asByteBuffer();
        fb.position(0);
        fb.limit((int) mbuf.mapped.byteSize());
        Log.debug("grabbed our buffer, remaining is " + fb.remaining());
        var ret = process(fb);
        Log.debug("processed buffer to " + ((ret == null) ? "empty" : ret.remaining()));
        // this is sorta questionable..... how do we know it is the same buffer (index) still?
        vent.eqBuffer();
        return ret;
    }

    Thread statsRunner;
    Thread paramRunner;

    public void stopStatsAndParams() {
        statsRunner = null;
        paramRunner = null;
    }

    final static void sleepOrNot(long nap) {
        try {
            Thread.sleep(nap);
        } catch (InterruptedException x) {
        }
    }

    public int getFrameRate() {
        return 60;
    }

    public void startParams() {
        paramRunner = new Thread(() -> {
            int i = 0;
            try {
                Log.info("starting param loop");
                pent.start();
                while (paramRunner != null) {
                    try {
                        int ctx = 0;
                        var pbuf = pent.dqBuffer();
                        Log.verb("param mapped size " + pbuf.mapped.byteSize() + " address " + pbuf.mapped.address());
                        alg2Kernel.invokeExact(ctx, pbuf.mapped);
                        sleepOrNot(100);
                        pent.eqBuffer();
                    } catch (DQException rex) {
                        sleepOrNot(100);
                        Log.warn(" param dq failed.");
                    }
                }
                Log.info("Stats thread ended normally");
                pent.stop();
            } catch (Throwable t) {
                Log.error("Stats thread ended because " + t.getMessage());
            }
        });
        paramRunner.setName("vl4paramRunner");
        paramRunner.start();
    }

    void doATask() {
        Runnable task = v4lQueue.poll();
        if (task != null) {
            task.run();
        }
    }

    public void startStats() {
        statsRunner = new Thread(() -> {
            try {
                Log.info("starting stats loop");
                sent.start();
                int i = 0;
                while (statsRunner != null) {
                    try {
                        var sbuf = sent.dqBuffer();

                        Log.verb("stats mapped size " + sbuf.mapped.byteSize() + " address " + sbuf.mapped.address());
                        int ctx = 0;
                        alg2User.invokeExact(ctx, sbuf.mapped);
                        doATask();
                        sleepOrNot(100);
                        sent.eqBuffer();
                    } catch (DQException rex) {
                        Log.warn("stats (or param) dq failed.");
                        sleepOrNot(100);
                    }
                }
                Log.info("Stats thread ended normally");
                sent.stop();
            } catch (Throwable t) {
                Log.error("Stats thread ended because " + t.getMessage());
            }
        }
        );
        statsRunner.setName("vl4statsRunner");
        statsRunner.start();
    }

    public static void main(String args[]) {
        Log.setLevel(Log.INFO);
        try {
            // Note this needs some magic to work
            // LD_PRELOAD="/usr/lib/liblens.so /usr/lib/libtuning.so /usr/lib/libmediaAPI.so " 
            // and java 24 on a VIM4
            Path outF = Paths.get("/tmp/tst300.nv12");
            OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
            try (java.nio.channels.WritableByteChannel outb = Channels.newChannel(Files.newOutputStream(outF, options))) {
                int[] framecount = new int[1];
                framecount[0] = 0;
                var a = new AmlMediaReader("/dev/media0", 1920, 1080) {
                    @Override
                    public ByteBuffer process(ByteBuffer frame) {
                        try {
                            if ((framecount[0] % 30) == 0) {
                                outb.write(frame);
                                Log.info("frame " + framecount[0] + " written " + frame.position());

                            }
                            framecount[0]++;
                        } catch (IOException iox) {
                            Log.error("Failed to write frame");
                        }
                        return frame;
                    }
                };
                Log.info("Should start cap now....");
                a.startCap();
                for (int i = 0; i < 300; i++) {
                    var frame = a.read();
                }
                a.stop();
            }
            Thread.sleep(1000);
            Log.info("about to quit now....");
            System.exit(0);

        } catch (Throwable t) {
            Log.error(" threw " + t.getMessage());
            t.printStackTrace();
        }

    }

}
