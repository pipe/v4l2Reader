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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private V4l2Buffer[] buffers;
    private final MethodHandle aisp_enable;
    private final MethodHandle alg2User;
    private final MethodHandle alg2Kernel;
    private final MethodHandle algFwInterface;

    public AmlMediaReader(String dev, int w, int h) throws Throwable {
        width = w;
        height = h;
        path = java.nio.file.Paths.get(dev);
        arena = Arena.ofConfined();
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

    void printSevenInt(MemorySegment ints) {
        var bb = ints.asByteBuffer();
        for (int i = 0; i < 7; i++) {
            Log.info("int[" + i + "] = " + bb.getInt());
        }
    }

    void setSevenInt(MemorySegment ints, Integer brightness, Integer contrast, Integer sharpness, Integer saturation, Integer hue, Integer vibrance) {
        var bb = ints.asByteBuffer();
        bb.putInt(1);
        bb.putInt(brightness);
        bb.putInt(contrast);
        bb.putInt(sharpness);
        bb.putInt(saturation);
        bb.putInt(hue);
        bb.putInt(vibrance);
    }

    public void setCsC(boolean enable, Integer brightness, Integer contrast, Integer sharpness, Integer saturation, Integer hue, Integer vibrance) {

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

        setSevenInt(attr,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        try {
            Log.info("trying to get csc ");

            algFwInterface.invokeExact(0, cmd);
            Log.info("csc attr values are :");

            printSevenInt(attr);
            Log.info("csc rattr values are :");

            printSevenInt(rattr);

            direction.set(cmd, 0L, (byte) 0x0); //set

            Log.info("trying to set csc ");

            algFwInterface.invokeExact(0, cmd);
            Log.info("csc attr values are :");

            printSevenInt(attr);
            Log.info("csc rattr values are :");

            printSevenInt(rattr);

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
        v4l2_requestbuffers.count(v4l2_rb, 3);
        v4l2_requestbuffers.type(v4l2_rb, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        v4l2_requestbuffers.memory(v4l2_rb, MangledMediaAPI.V4L2_MEMORY_MMAP());
        rc = MangledMediaAPI.v4l2_video_req_bufs(video_ent, v4l2_rb);
        if (rc < 0) {
            Log.error("v4l2_video_req_bufs stream failed ");
            return -1;
        }
        int bcount = v4l2_requestbuffers.count(v4l2_rb);

        Log.info(" v4l2_video_req_bufs got " + bcount);

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
        buffers = new V4l2Buffer[bcount];
        for (int i = 0; i < bcount; i++) {
            buffers[i] = mapBuffer(video_ent, i);
            Log.verb("buffer[" + i + "] = " + buffers[i]);
        }

        V4l2Buffer paramBuffer = mapBuffer(video_param, 0);
        paramBuffer.eq();
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
        MemorySegment calib = arena.allocate(1216); // Yeah, I know this is horrible - but, look at the nested enumed struct and just get the compiler to do a sizeof!
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
        alg2Kernel.invokeExact(ctx, paramBuffer.mapped);

        Log.info("enqueue video buffers");

        for (var buf : buffers) {
            buf.eq();
        }
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

    class V4l2Buffer {

        MemorySegment buf;
        MemorySegment mapped;
        MemorySegment addr;
        MemorySegment ent;

        V4l2Buffer(MemorySegment ent) {
            this.ent = ent;
            buf = v4l2_buffer.allocate(arena);
        }

        int dq() throws Throwable {
            return MangledMediaAPI.v4l2_video_dq_buf(ent, buf);
        }

        int eq() throws Throwable {
            return MangledMediaAPI.v4l2_video_q_buf(ent, buf);
        }

        int query() throws Throwable {
            return MangledMediaAPI.v4l2_video_query_buf(ent, buf);
        }

        private void unmap(MemorySegment s) throws Throwable {
            if (s == mapped) {
                var m = v4l2_buffer.m(buf);
                int offset = v4l2_buffer.m.offset(m);
                long length = v4l2_buffer.bytesused(buf);
                int res;
                res = (int) Mmap.munmap.invoke(offset, (long) length);
                if (res != 0) {
                    Log.error("munmap failed");
                } else {
                    Log.info("unmapped mapped address of " + mapped.address());
                }

                mapped = null;
            }

        }

        void map(long length, long offset) throws Throwable {
            var dev = media_entity.fd(ent);
            addr = (MemorySegment) Mmap.mmap.invokeExact(
                    MemorySegment.NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, dev, offset);
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

        private void setIndex(int index) {
            v4l2_buffer.index(buf, index);
        }

        private void setType(int type) {
            v4l2_buffer.type(buf, type);
        }

        private void setMemory(int mm) {
            v4l2_buffer.memory(buf, mm);
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

    V4l2Buffer mapBuffer(MemorySegment ent, int index) throws Throwable {
        V4l2Buffer vbuf = new V4l2Buffer(ent);
        vbuf.setIndex(index);
        vbuf.setType(MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        vbuf.setMemory(MangledMediaAPI.V4L2_MEMORY_MMAP());
        int res = vbuf.query();
        if (res < 0) {
            throw new RuntimeException("VIDIOC_QUERYBUF failed");
        }
        long offset = vbuf.getOffset();
        long length = vbuf.getLength();
        int rindex = vbuf.getIndex();
        Log.verb("index " + index + " rindex " + rindex + " offset = " + offset + " length= " + length);
        if (out == null) {
            out = new byte[(int) length];
        }
        vbuf.map(length, offset);
        //vbuf.eq();
        return vbuf;
    }

    public byte[] process(byte[] frame) {
        Log.verb("Got from of " + frame.length);
        return frame;
    }

    V4l2Buffer dqBuffer() throws Throwable {

        V4l2Buffer vbuf = new V4l2Buffer(video_ent);
        vbuf.setType(MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        vbuf.setMemory(MangledMediaAPI.V4L2_MEMORY_MMAP());

        int res = vbuf.dq();
        if (res < 0) {
            throw new RuntimeException("VIDIOC_DQBUF failed");
        }
        long offset = vbuf.getOffset();
        int length = vbuf.getLength();
        int index = vbuf.getIndex();

        Log.verb("DQ'd index " + index + " offset = " + offset + " length= " + length);
        return vbuf;
    }

    @Override
    public byte[] read() throws Throwable {
        var buf = dqBuffer();
        int index = buf.getIndex();
        byte[] ret = out;
        Log.verb("bb index is " + index);
        if (index < buffers.length) {
            var mbuf = buffers[index];
            Log.verb("mapped to  index " + mbuf.getIndex() + " offset = " + mbuf.getOffset() + " length = " + mbuf.getLength());
            Log.verb("mapped size " + mbuf.mapped.byteSize() + " address " + mbuf.mapped.address());
            ByteBuffer fb = mbuf.asByteBuffer();
            fb.get(0, out);
            Log.verb("sucked into our buffer");
            ret = process(out);

        }
        buf.eq();
        return ret;
    }

    public static void main(String args[]) {
        Log.setLevel(Log.VERB);
        try {
            Path outF = Paths.get("/tmp/tst300.nv12");
            OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
            var outb = Files.newOutputStream(outF, options);

            var a = new AmlMediaReader("/dev/media0", 1920, 1080) {
                @Override
                public byte[] process(byte[] frame) {
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
                    a.setCsC(true, null, null, null, null, null, null);
                }
                Log.info("frame written " + i);
            }
            a.stop();
            outb.close();
        } catch (Throwable t) {
            Log.error(" threw " + t.getMessage());
            t.printStackTrace();
        }
        Log.info("about to quit now....");

    }

}
