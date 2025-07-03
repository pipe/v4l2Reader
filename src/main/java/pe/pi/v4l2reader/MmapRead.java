package pe.pi.v4l2reader;

import java.nio.file.Files;
import com.phono.srtplight.Log;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;
import java.util.function.Consumer;
import java.nio.ByteBuffer;
import static pe.pi.v4l2reader.V4l2Ioctls.MAP_SHARED;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_READ;
import static pe.pi.v4l2reader.V4l2Ioctls.PROT_WRITE;
import static pe.pi.v4l2reader.V4l2Ioctls.VIDIOC_DQBUF;
import static pe.pi.v4l2reader.V4l2Ioctls.VIDIOC_QBUF;
import static pe.pi.v4l2reader.V4l2Ioctls.VIDIOC_QUERYBUF;

/**
 *
 * @author thp
 */
public class MmapRead extends V4l2Ioctls implements MmapReader {

    java.nio.file.Path path;

    V4l2Buffer buffers[];
    int videoDev = 0;
    int width;
    int height;

    public MmapRead(String dev) throws Throwable {
        this(dev, 1920, 1080);
    }

    public MmapRead(String dev, int w, int h) throws Throwable {
        super();
        width = w;
        height = h;
        path = java.nio.file.Paths.get(dev);

        if (Files.isReadable(path)) {
            setup();
        } else {
            Log.error("cant read " + path);
        }
    }

    public ByteBuffer process(ByteBuffer frame) {
        return frame;
    }

    @Override
    public ByteBuffer read() throws Throwable {
        ByteBuffer ret = null;
        var buf = dqBuffer();
        int index = buf.getInt("index");
        Log.verb("bb index is " + index);
        if (index < buffers.length) {
            ByteBuffer fb = buffers[index].asByteBuffer();
            ret = process(fb);
        }
        enqueueBuffer(buf);
        return ret;
    }

    public void videoEnable(boolean enable) throws Throwable {

        long act = enable ? VIDIOC_STREAMON : VIDIOC_STREAMOFF;
        int ret = (int) ioctl.invoke(videoDev, act, videoCapture);
        if (ret < 0) {
            Log.error("Unable to " + (enable ? "start" : "stop") + " streaming ");
        } else {
            Log.info("Streaming enabled " + enable);
        }
    }

    @Override
    public void startCap() throws Throwable {
        videoEnable(true);

    }

    void enqueueBuffer(V4l2Buffer buf) throws Throwable {
        int ret = buf.eq();
        if (ret < 0) {
            Log.error("Unable to queue buffer");
        } else {
            Log.verb("buffer enqueued");
        }
    }

    public int setup() throws Throwable {
        // Define native methods
        final int O_RDWR = 2;

        Log.debug("in setup()");

        Log.debug("common functions allocated");

        MemorySegment devName = arena.allocateFrom(path.toString());

        int fd = (int) open.invoke(devName, O_RDWR);
        if (fd < 0) {
            Log.error("Failed to open video device");
            return fd;
        }
        videoDev = fd;
        Log.debug("opened " + path);

        setFormat(libc, arena, fd);
        Log.debug("set format for " + path);

        int bcount = requestCaptureBuffers(fd, arena, libc);
        Log.debug("requested capture buffers for " + path + " got " + bcount);
        buffers = new V4l2Buffer[bcount];
        for (int i = 0; i < bcount; i++) {
            buffers[i] = mapBuffer(fd, arena, libc, i);
            Log.verb("buffer[" + i + "] = " + buffers[i]);
        }

        return fd;
    }

    public void setFormat(SymbolLookup libc, Arena arena, int fd) throws Throwable {

        // Allocate and populate the v4l2_format structure
        MemorySegment fmt = arena.allocate(v4l2_format);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("type")), V4L2_BUF_TYPE_VIDEO_CAPTURE);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("width")), width);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("height")), height);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("pixelformat")), V4L2_PIX_FMT_NV12);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("field")), V4L2_FIELD_NONE);

        int result = (int) ioctl.invoke(fd, VIDIOC_S_FMT, fmt);

        if (result < 0) {
            Log.error("ioctl VIDIOC_S_FMT failed");
        } else {
            Log.info("Format set successfully!");
        }
    }

    public int requestCaptureBuffers(int fd, Arena arena, SymbolLookup libc) throws Throwable {

        // Define v4l2_requestbuffers struct
        MemorySegment req = arena.allocate(v4l2_requestbuffers);

        // Set values
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("count")), 4); // request 4 buffers
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("type")), V4L2_BUF_TYPE_VIDEO_CAPTURE);
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("memory")), V4L2_MEMORY_MMAP);
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("capabilities")), 0);
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("flags")), 0);

        int result = (int) ioctl.invoke(fd, VIDIOC_REQBUFS, req);
        int count = -1;
        if (result < 0) {
            Log.error("VIDIOC_REQBUFS failed");
        } else {
            count = req.get(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("count")));
            Log.verb("Requested MMAP buffers: " + count);
        }
        return count;
    }

    class V4l2Buffer {

        MemorySegment buf;
        MemorySegment mapped;

        void set(String name, int value) {
            buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement(name)), value);
        }

        int getInt(String name) {
            return buf.get(JAVA_INT, v4l2_buffer.byteOffset(groupElement(name)));
        }

        long getLong(String name) {
            return buf.get(JAVA_LONG, v4l2_buffer.byteOffset(groupElement(name)));
        }

        V4l2Buffer() {
            this(arena.allocate(v4l2_buffer));
        }

        V4l2Buffer(MemorySegment b) {
            buf = b;
        }

        int dq() throws Throwable {
            int res = (int) ioctl.invoke(videoDev, VIDIOC_DQBUF, buf);
            return res;
        }

        int eq() throws Throwable {
            return (int) ioctl.invoke(videoDev, VIDIOC_QBUF, buf);
        }

        int query() throws Throwable {
            return (int) ioctl.invoke(videoDev, VIDIOC_QUERYBUF, buf);
        }

        private void unmap(MemorySegment s) throws Throwable {
            if (s == mapped) {
                long offset = getLong("m_offset");
                long length = getInt("length");
                int res;
                res = (int) Mmap.munmap.invoke(offset, (long) length);
                if (res != 0) {
                    Log.error("munmap failed");
                }

                mapped = null;
            }

        }

        void map(int length, long offset) throws Throwable {
            MemorySegment addr = (MemorySegment) Mmap.mmap.invoke(
                    MemorySegment.NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, videoDev, offset);
            Consumer<MemorySegment> cleanup = s -> {
                try {
                    unmap(s);
                } catch (Throwable e) {
                    Log.error("cant unmap");
                }
            };
            Log.info("mapped to "+addr.address());
            mapped = addr.reinterpret(length, arena, cleanup);
            Log.info("reinterp to "+mapped.address());

        }

        ByteBuffer asByteBuffer() {
            return (mapped == null) ? ByteBuffer.allocate(0) : mapped.asByteBuffer();
        }
    }

    V4l2Buffer dqBuffer() throws Throwable {

        V4l2Buffer vbuf = new V4l2Buffer();
        //MemorySegment buf = arena.allocate(v4l2_buffer);
        vbuf.set("type", V4L2_BUF_TYPE_VIDEO_CAPTURE);
        vbuf.set("memory", V4L2_MEMORY_MMAP);

        int res = vbuf.dq();
        if (res < 0) {
            throw new RuntimeException("VIDIOC_DQBUF failed");
        }
        long offset = vbuf.getLong("m_offset");
        int length = vbuf.getInt("length");
        int index = vbuf.getInt("index");

        Log.verb("DQ'd index " + index + " offset = " + offset + " length= " + length);
        return vbuf;
    }

    V4l2Buffer mapBuffer(int fd, Arena arena, SymbolLookup libc, int index) throws Throwable {
        V4l2Buffer vbuf = new V4l2Buffer();
        vbuf.set("index", index);
        vbuf.set("type", V4L2_BUF_TYPE_VIDEO_CAPTURE);
        vbuf.set("memory", V4L2_MEMORY_MMAP);
        int res = vbuf.query();
        if (res < 0) {
            throw new RuntimeException("VIDIOC_QUERYBUF failed");
        }
        long offset = vbuf.getLong("m_offset");
        int length = vbuf.getInt("length");
        int rindex = vbuf.getInt("index");
        Log.verb("index " + index + " rindex " + rindex + " offset = " + offset + " length= " + length);
        vbuf.map(length, offset);
        enqueueBuffer(vbuf);
        return vbuf;
    }

    void unmapBuffer(long offset, long length) throws Throwable {

        int res = (int) Mmap.munmap.invoke(offset, (long) length);
        if (res != 0) {
            Log.error("munmap failed");
        }
    }

    public void stop() throws Throwable {

        Log.debug("stop streaming (if we are)");
        int res = (int) ioctl.invoke(videoDev, VIDIOC_STREAMOFF, videoCapture);
        if (res < 0) {
            Log.error("VIDIOC_STREAMOFF failed");
        }

        Log.debug("close video device");

        res = (int) close.invoke(videoDev);
        if (res != 0) {
            System.err.println("close failed");
        }
    }

}
