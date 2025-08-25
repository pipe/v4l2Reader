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
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import static pe.pi.v4l2reader.V4l2Ioctls.MAP_SHARED;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_READ;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_WRITE;
import pe.pi.v4l2reader.mediaApi.AML_ALG_CTX_S;
import pe.pi.v4l2reader.mediaApi.MangledMediaAPI;
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
    MemorySegment video_ent;
    private final MethodHandle aisp_enable;
    private final MethodHandle alg2User;
    private final MethodHandle alg2Kernel;
    private final MethodHandle algFwInterface;
    private MediaEntity vent;
    private MediaEntity pent;
    private Queue<Runnable> v4lQueue;

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
        if (Files.isReadable(path)) {
            setup();

        } else {
            Log.error("cant read " + path);
        }

    }

    enum CsCNames {
        unused, brightness, contrast, sharpness, saturation, hue, vibrance
    };
    final static HashMap<Integer,CsCNames> CsCNameMap = new HashMap();

    static {
        CsCNameMap.put(CsCNames.unused.ordinal(), CsCNames.unused);
        CsCNameMap.put(CsCNames.brightness.ordinal(), CsCNames.brightness);
        CsCNameMap.put(CsCNames.contrast.ordinal(), CsCNames.contrast);
        CsCNameMap.put(CsCNames.sharpness.ordinal(), CsCNames.sharpness);
        CsCNameMap.put(CsCNames.saturation.ordinal(), CsCNames.saturation);
        CsCNameMap.put(CsCNames.hue.ordinal(), CsCNames.hue);
        CsCNameMap.put(CsCNames.vibrance.ordinal(), CsCNames.vibrance);
    }

    public Integer[] getCsC() {

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
        direction.set(cmd, 0L, (byte) 0x1); //get
        cmdId.set(cmd, 0L, (byte) 0x1); // AML_MBI_ISP_CSCAttr
        pData.set(cmd, 0L, attr);
        pRetValue.set(cmd, 0L, rattr);
        try {
            Log.info("trying to get csc ");

            algFwInterface.invokeExact(0, cmd);
            Log.info("csc attr values are :");
            var bb = attr.asByteBuffer();
            for (int i = 0; i < 7; i++) {
                ret[i] = bb.getInt();
                Log.info(CsCNameMap.get(i) + " = " + ret[i]);
            }

        } catch (Throwable ex) {
            Log.error("algFwInterface threw exception " + ex.toString());
        }
        return ret;
    }

    public void setCsC(Integer[] csc) {
        Runnable task = new Runnable(){
            Integer v[] = csc;

            @Override
            public void run() {
                setCsCActual(v);
            }  
        };
        v4lQueue.add(task);
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
        direction.set(cmd, 0L, (byte) 0x1); //get
        cmdId.set(cmd, 0L, (byte) 0x1); // AML_MBI_ISP_CSCAttr
        pData.set(cmd, 0L, attr);
        pRetValue.set(cmd, 0L, rattr);
        direction.set(cmd, 0L, (byte) 0x0); //set
        Log.info("new csc attr values are :");

        if (csc.length == 7) {
            for (int i = 0; i < 7; i++) {
                Log.info(CsCNameMap.get(i) + " = " + csc[i]);
                attr.asByteBuffer().putInt(csc[i]);
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
        video_ent = media_stream.video_ent0(v4l2_media_stream);
        fd = media_entity.fd(video_ent);
        Log.info("Inited Media Stream video fd is " + fd);

        var v4l2_cap = v4l2_capability.allocate(arena);
        MangledMediaAPI.v4l2_video_get_capability(video_ent, v4l2_cap);
        Log.info("Got v4l2 caps for Media Stream video fd is " + fd);

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

        var v4l2_rb = v4l2_requestbuffers.allocate(arena);
        v4l2_requestbuffers.count(v4l2_rb, 4);
        v4l2_requestbuffers.type(v4l2_rb, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        v4l2_requestbuffers.memory(v4l2_rb, MangledMediaAPI.V4L2_MEMORY_MMAP());
        rc = MangledMediaAPI.v4l2_video_req_bufs(video_ent, v4l2_rb);
        if (rc < 0) {
            Log.error("v4l2_video_req_bufs stream failed ");
            return -1;
        }
        int bcount = v4l2_requestbuffers.count(v4l2_rb);

        Log.info(" v4l2_video_req_bufs got " + bcount);

        vent = new MediaEntity(video_ent, bcount);

        var video_param = media_stream.video_param(v4l2_media_stream);
        var video_ent0 = media_stream.video_ent0(v4l2_media_stream);
        v4l2_rb.fill((byte) 0);
        v4l2_requestbuffers.count(v4l2_rb, 1);
        v4l2_requestbuffers.type(v4l2_rb, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        v4l2_requestbuffers.memory(v4l2_rb, MangledMediaAPI.V4L2_MEMORY_MMAP());
        rc = MangledMediaAPI.v4l2_video_req_bufs(video_param, v4l2_rb);
        if (rc < 0) {
            Log.error("v4l2_video_req_bufs param failed ");
            return -1;
        }

        pent = new MediaEntity(video_param, 1);
        pent.eqBuffers();

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
        aisp_enable.invokeExact(ctx, pstAlgCtx, calib);

        Log.info("run alg2User ");

        MemorySegment alg_init = arena.allocate(256 * 1024);
        alg_init.fill((byte) 0);

        alg2User.invokeExact(ctx, alg_init);
        Log.info("run alg2Kernel");
        alg2Kernel.invokeExact(ctx, pent.buffers[0].mapped);

        Log.info("enqueue video buffers");

        vent.eqBuffers();

        Log.info("Start video_param");
        rc = MangledMediaAPI.v4l2_video_stream_on(video_param, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        if (rc < 0) {
            Log.error("cant start param stream  ");
        }
        return videoDev;

    }

    @Override
    public void startCap() throws Throwable {
        int rc = MangledMediaAPI.v4l2_video_stream_on(video_ent, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        if (rc < 0) {
            Log.error("cant start v4l2 capture ");
        } else {
            Log.info("started capture");
        }
    }

    @Override
    public void stop() throws Throwable {
        int rc = MangledMediaAPI.v4l2_video_stream_off(video_ent, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        if (rc < 0) {
            Log.error("cant stop v4l2 capture ");
        } else {
            Log.info("stopped capture");
        }
    }

    public V4l2Substitute getV4l2Sub() {
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
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
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
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }

            @Override
            public Long getSaturation() {
                Integer[] o = getCsC();
                return Long.valueOf(o[CsCNames.saturation.ordinal()]);
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

        MediaEntity(MemorySegment ent, int bcount) throws Throwable {
            this.ent = ent;
            buf = v4l2_buffer.allocate(arena);
            buffers = new V4l2Buffer[bcount];
            for (int i = 0; i < bcount; i++) {
                buffers[i] = new V4l2Buffer(this);
                mapBuffer(i);
                Log.verb("buffer[" + i + "] = " + buffers[i]);
            }

        }

        int getFd() {
            return media_entity.fd(ent);
        }

        int eqBuffer() throws Throwable {
            return MangledMediaAPI.v4l2_video_q_buf(ent, buf);
        }

        void eqBuffers() throws Throwable {
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
                throw new RuntimeException("VIDIOC_DQBUF failed");
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
                throw new RuntimeException("VIDIOC_QUERYBUF failed");
            }
            long offset = getOffset();
            long length = getLength();
            int rindex = getIndex();
            Log.verb("index " + index + " rindex " + rindex + " offset = " + offset + " length= " + length);
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
        Log.debug("processed buffer to " + ret.remaining());
        Runnable task = v4lQueue.poll();
        if (task != null){
            task.run();
        }
        // this is sorta questionable..... how do we know it is the same buffer (index) still?
        vent.eqBuffer();
        return ret;
    }

    public static void main(String args[]) {
        Log.setLevel(Log.VERB);
        try {
            Path outF = Paths.get("/tmp/tst300.nv12");
            OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
            try (java.nio.channels.WritableByteChannel outb = Channels.newChannel(Files.newOutputStream(outF, options))) {
                var a = new AmlMediaReader("/dev/media0", 1920, 1080) {
                    @Override
                    public ByteBuffer process(ByteBuffer frame) {
                        try {
                            outb.write(frame);
                        } catch (IOException iox) {
                            Log.error("Failed to write frame");
                        }
                        return frame;
                    }
                };
                Log.info("Should start cap now....");
                a.startCap();
                for (int i = 0; i < 30; i++) {
                    var frame = a.read();
                    if (i == 15) {
                        //a.setCsC(true, null, null, null, null, null, null);
                    }
                    Log.info("frame written " + frame.remaining());
                }
                a.stop();
            }
        } catch (Throwable t) {
            Log.error(" threw " + t.getMessage());
            t.printStackTrace();
        }
        Log.info("about to quit now....");

    }

}
