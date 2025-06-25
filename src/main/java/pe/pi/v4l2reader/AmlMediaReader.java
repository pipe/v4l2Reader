/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    MemorySegment video_param;
    private MemorySegment video_ent;
    private V4l2Buffer[] buffers;
    private final MethodHandle aisp_enable;
    private final MethodHandle alg2User;
    private final MethodHandle alg2Kernel;

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
        if (Files.isReadable(path)) {
            setup();

        } else {
            Log.error("cant read " + path);
        }

    }

    public int setup() throws Throwable {
        int fd = 0;
        MemorySegment devName = arena.allocateFrom(path.toString());

        var v4l2_media_stream = media_stream.allocate(arena);

        var mediaDev = MangledMediaAPI.media_device_new(devName);
        //media_stream.media_dev(mediaDev,);

        /*
            rc = mediaStreamInit(&tparm->v4l2_media_stream, tparm->v4l2_media_stream.media_dev);
    if (0 != rc) {
        ERR("[T#%d] The %s device init fail.\n", tparm->mediadevname);
        return NULL;
    } 
         */
        int rc = MangledMediaAPI.mediaStreamInit(v4l2_media_stream, mediaDev);
        if (rc != 0) {
            Log.error("mediaStreamInit failed");
            return -1;
        }
        video_ent = media_stream.video_ent0(v4l2_media_stream);
        fd = media_entity.fd(video_ent);
        Log.info("Inited Media Stream video fd is " + fd);
        /*
    memset (&v4l2_cap, 0, sizeof (struct v4l2_capability));
    rc = v4l2_video_get_capability(tparm->v4l2_media_stream.video_ent0, &v4l2_cap);
    if (rc < 0) {
        ERR (" Error: get capability.\n");
        return NULL;
    }
         */
        var v4l2_cap = v4l2_capability.allocate(arena);
        MangledMediaAPI.v4l2_video_get_capability(video_ent, v4l2_cap);
        Log.info("Got v4l2 caps for Media Stream video fd is " + fd);

        /*
            media_set_wdrMode(&tparm->v4l2_media_stream, 0);
    media_set_wdrMode(&tparm->v4l2_media_stream, tparm->wdr_mode);
         */
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
        v4l2_requestbuffers.count(v4l2_rb, 5);
        v4l2_requestbuffers.type(v4l2_rb, MangledMediaAPI.V4L2_BUF_TYPE_VIDEO_CAPTURE());
        v4l2_requestbuffers.memory(v4l2_rb, MangledMediaAPI.V4L2_MEMORY_MMAP());
        rc = MangledMediaAPI.v4l2_video_req_bufs(video_ent, v4l2_rb);
        if (rc < 0) {
            Log.error("v4l2_video_req_bufs failed ");
            return -1;
        }
        int bcount = v4l2_requestbuffers.count(v4l2_rb);

        Log.info(" v4l2_video_req_bufs got " + bcount);

        video_param = media_stream.video_param(v4l2_media_stream);
        var video_ent0 = media_stream.video_ent0(v4l2_media_stream);
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

        buffers = new V4l2Buffer[bcount];
        for (int i = 0; i < bcount; i++) {
            buffers[i] = mapBuffer(i);
            Log.verb("buffer[" + i + "] = " + buffers[i]);
        }
        MemorySegment alg_init = arena.allocate(256 * 1024);
        alg_init.fill((byte)0);
        alg2User.invokeExact(ctx, alg_init);
        alg2Kernel.invokeExact(ctx, buffers[0].addr);
        /*
            memset(alg_init, 0, sizeof(alg_init));

    (ispIf.alg2User)(0, alg_init);
    (ispIf.alg2Kernel)(0, tparm->v4l2_mem_param[0]);
         */
        //int type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        return videoDev;

    }

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

        V4l2Buffer() {
            this(v4l2_buffer.allocate(arena));
        }

        V4l2Buffer(MemorySegment b) {
            buf = b;
        }

        int dq() throws Throwable {
            return MangledMediaAPI.v4l2_video_dq_buf(video_ent, buf);
        }

        int eq() throws Throwable {
            return MangledMediaAPI.v4l2_video_q_buf(video_ent, buf);
        }

        int query() throws Throwable {
            return MangledMediaAPI.v4l2_video_query_buf(video_ent, buf);
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
            addr = (MemorySegment) Mmap.mmap.invokeExact(
                    MemorySegment.NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, videoDev, offset);
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
            return (mapped == null) ? ByteBuffer.allocate(0) : mapped.asByteBuffer();
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

    V4l2Buffer mapBuffer(int index) throws Throwable {
        V4l2Buffer vbuf = new V4l2Buffer();
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
        vbuf.eq();
        return vbuf;
    }

    public byte[] process(byte[] frame) {
        Log.verb("Got from of " + frame.length);
        return frame;
    }

    V4l2Buffer dqBuffer() throws Throwable {

        V4l2Buffer vbuf = new V4l2Buffer();
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
            var a = new AmlMediaReader("/dev/media0", 3840, 2160);
            Log.info("Should start cap now....");
            Thread.sleep(1000);
            Path outF = Paths.get("/tmp/tst300.nv12");
            OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

            var outb = Files.newOutputStream(outF, options);
            a.startCap();
            for (int i = 0; i < 100; i++) {
                var frame = a.read();
                outb.write(frame);
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
