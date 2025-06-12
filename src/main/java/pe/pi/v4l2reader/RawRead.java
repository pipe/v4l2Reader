/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package pe.pi.v4l2reader;

import java.nio.file.Files;
import com.phono.srtplight.Log;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;
import java.util.function.Consumer;
import java.nio.ByteBuffer;
import pe.pi.amlh264enc.Encoder;

/**
 *
 * @author thp
 */
public class RawRead {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Log.setLevel(Log.DEBUG);
        String adev = "/dev/media0";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            RawRead reader = new RawRead(adev);
            reader.read();
        } catch (Throwable ex) {
            Log.error("can't read from " + adev + " because " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    static final Linker linker = Linker.nativeLinker();

    java.nio.file.Path path;
    Arena arena;
    MethodHandle ioctl;
    private MethodHandle munmap;
    private MethodHandle mmap;
    MemorySegment buffers[];
    byte[] out;
    int videoDev =0;
    MemorySegment typeSeg;
    final static int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;

    public RawRead(String dev) throws Throwable {
        // fd = open("/dev/video0", O_RDWR);

        path = java.nio.file.Paths.get(dev);

        if (Files.isReadable(path)) {
            setup();
        } else {
            Log.error("cant read " + path);
        }
    }

    public void read()throws Throwable {
	startCap();
    }
    public void videoEnable(boolean enable) throws Throwable{       
        long VIDIOC_STREAMOFF = 1074026003L;
	long VIDIOC_STREAMON = 1074026002L;
        long act = enable?VIDIOC_STREAMON:VIDIOC_STREAMOFF;
        int ret = (int) ioctl.invoke(videoDev, act, typeSeg);
        if (ret < 0) {
                Log.error("Unable to " + (enable ? "start" : "stop") +" streaming ");             
        } else {
		Log.debug("Streaming enabled "+enable);
        }
    }  
    public void startCap() throws Throwable {
	videoEnable(true);
        while (true){
		var buf = dqBuffer();
		int index = buf.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("index")));

                Log.debug("bb index is "+index);
		if (index < buffers.length){
                   ByteBuffer fb = buffers[index].asByteBuffer();
                   fb.get(0, out);
		   Log.debug("sucked into our buffer");
                   var h = encoder.encode(out);
		   Log.debug("encoded to h264 "+h.length);
		}
                enqueueBuffer(buf);
        }
    }
    public void enqueueBuffer(MemorySegment buf) throws Throwable {
	final long VIDIOC_QBUF = 3227014671L;
	int ret = (int) ioctl.invoke(videoDev, VIDIOC_QBUF, buf);
	if (ret < 0){
		Log.error("Unable to queue buffer");
	} else {
		Log.debug("buffer enqueued");
	}
    }
    Encoder encoder;

    public int setup() throws Throwable {
        // Define native methods
        final int O_RDWR = 2;

        Log.debug("in setup()");
  
        encoder = new Encoder(1920,1080);


        arena = Arena.ofConfined();
       // Log.debug("loading extra libs");
       // var libtuning = SymbolLookup.libraryLookup("/lib/libtuning.so", arena);
       // Log.debug("loaded libtuning");

       // var liblens = SymbolLookup.libraryLookup("/lib/liblens.so", arena);
       // Log.debug("loaded liblens");

        //var mediaAPI = SymbolLookup.libraryLookup("/lib/libmediaAPI.so", arena);
        //Log.debug("loaded libmediaAPI ");

        var libc = linker.defaultLookup(); //

        MethodHandle open = linker.downcallHandle(
                libc.find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
        );
        ioctl = linker.downcallHandle(
                libc.find("ioctl").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS)
        );
        munmap = linker.downcallHandle(
                libc.find("munmap").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)
        );
        mmap = linker.downcallHandle(
                libc.find("mmap").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)
        );
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
        buffers = new MemorySegment[bcount];
	for (int i=0;i<bcount;i++){
		buffers[i] = mapBuffer(fd, arena,libc , i);
		Log.debug("buffer["+i+"] = "+buffers[i]);
        }
        typeSeg = arena.allocate(JAVA_INT);
        typeSeg.set(JAVA_INT, 0, V4L2_BUF_TYPE_VIDEO_CAPTURE);
        return fd;
    }

    public void setFormat(SymbolLookup libc, Arena arena, int fd) throws Throwable {

        final int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;
        final int V4L2_FIELD_NONE = 1;
        final int V4L2_PIX_FMT_NV12 = 0x3231564E; // 'NV12'
        final int V4L2_PIX_FMT_YUYV = 0x56595559;
        //#define v4l2_fourcc(a, b, c, d)\
        //((__u32)(a) | ((__u32)(b) << 8) | ((__u32)(c) << 16) | ((__u32)(d) << 24))

        final int VIDIOC_S_FMT = 0xC0D05605;

        // Define v4l2_format layout
        // struct v4l2_pix_format {
        //     uint32_t width, height, pixelformat, field, bytesperline, sizeimage, colorspace, priv, flags, ycbcr_enc, quantization, xfer_func;
        // };
        GroupLayout v4l2_pix_format = MemoryLayout.structLayout(
                JAVA_INT.withName("width"),
                JAVA_INT.withName("height"),
                JAVA_INT.withName("pixelformat"),
                JAVA_INT.withName("field"),
                JAVA_INT.withName("bytesperline"),
                JAVA_INT.withName("sizeimage"),
                JAVA_INT.withName("colorspace"),
                JAVA_INT.withName("priv"),
                JAVA_INT.withName("flags"),
                JAVA_INT.withName("ycbcr_enc"),
                JAVA_INT.withName("quantization"),
                JAVA_INT.withName("xfer_func")
        );

        // struct v4l2_format {
        //     uint32_t type;
        //     union {
        //         struct v4l2_pix_format pix;
        //         char raw_data[200]; // to pad the union
        //     };
        // };
        GroupLayout v4l2_format = MemoryLayout.structLayout(
                JAVA_INT.withName("type"),
                MemoryLayout.paddingLayout(4), // Align union to 8 bytes
                v4l2_pix_format.withName("pix"),
                MemoryLayout.paddingLayout(200 - v4l2_pix_format.byteSize()) // pad to full union size
        );

        // Allocate and populate the v4l2_format structure
        MemorySegment fmt = arena.allocate(v4l2_format);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("type")), V4L2_BUF_TYPE_VIDEO_CAPTURE);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("width")), 1920);
        fmt.set(JAVA_INT, v4l2_format.byteOffset(groupElement("pix"), groupElement("height")), 1080);
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
        final long VIDIOC_REQBUFS = 3222558216L;
        final int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;
        final int V4L2_MEMORY_MMAP = 1;
        final int V4L2_MEMORY_DMABUF = 4;

        // Define v4l2_requestbuffers struct
        GroupLayout v4l2_requestbuffers = MemoryLayout.structLayout(
                JAVA_INT.withName("count"),
                JAVA_INT.withName("type"),
                JAVA_INT.withName("memory"),
                JAVA_INT.withName("capabilities"),
                JAVA_INT.withName("flags")// lie - last 3 bytes are reserved.
        );

        MemorySegment req = arena.allocate(v4l2_requestbuffers);

        // Set values
        req.set(JAVA_INT, v4l2_requestbuffers.byteOffset(groupElement("count")), 3); // request 4 buffers
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
            Log.debug("Requested MMAP buffers: " + count);
        }
        return count;
    }
    GroupLayout v4l2_buffer = MemoryLayout.structLayout(
                JAVA_INT.withName("index"),
                JAVA_INT.withName("type"),
                JAVA_INT.withName("bytesused"),
                JAVA_INT.withName("flags"),
                JAVA_INT.withName("field"),
                MemoryLayout.paddingLayout(4),
                MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG).withName("timestamp"),
                MemoryLayout.structLayout(JAVA_INT, JAVA_INT,JAVA_INT,JAVA_INT).withName("timecode"),
		JAVA_INT.withName("sequence"),
                JAVA_INT.withName("memory"),
                JAVA_LONG.withName("m_offset"),
                JAVA_INT.withName("length"),
                JAVA_INT.withName("reserved2"),
                JAVA_INT.withName("reserved")
        );
    public MemorySegment dqBuffer() throws Throwable {
	long VIDIOC_DQBUF = 3227014673L;
        int V4L2_MEMORY_MMAP = 1;

        MemorySegment buf = arena.allocate(v4l2_buffer);
	buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement("type")), V4L2_BUF_TYPE_VIDEO_CAPTURE);
	buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement("memory")), V4L2_MEMORY_MMAP);
        int res = (int) ioctl.invoke(videoDev, VIDIOC_DQBUF, buf);
        if (res < 0) {
            throw new RuntimeException("VIDIOC_DQBUF failed");
        }
        long offset = buf.get(JAVA_LONG,v4l2_buffer.byteOffset(groupElement("m_offset")));
        int length = buf.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("length")));
        int index = buf.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("index")));
        Log.debug("DQ'd index "+index+" offset = "+offset+" length= " +length);
        return buf;
    }

    public MemorySegment mapBuffer(int fd, Arena arena, SymbolLookup libc, int index) throws Throwable {
	long VIDIOC_QUERYBUF = 3227014665L;
        int V4L2_MEMORY_MMAP = 1;

        MemorySegment buf = arena.allocate(v4l2_buffer);
	buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement("index")), index);
	buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement("type")), V4L2_BUF_TYPE_VIDEO_CAPTURE);
	buf.set(JAVA_INT, v4l2_buffer.byteOffset(groupElement("memory")), V4L2_MEMORY_MMAP);
        int res = (int) ioctl.invoke(fd, VIDIOC_QUERYBUF, buf);
        if (res < 0) {
            throw new RuntimeException("VIDIOC_QUERYBUF failed");
        }
        long offset = buf.get(JAVA_LONG,v4l2_buffer.byteOffset(groupElement("m_offset")));
        int length = buf.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("length")));
        int rindex = buf.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("index")));
        Log.debug("index "+index+" rindex "+rindex+" offset = "+offset+" length= " +length);
        if (out == null){
	    out = new byte[length];
        }
        // mmap(NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, offset)
        int PROT_READ = 0x1, PROT_WRITE = 0x2, MAP_SHARED = 0x01;
        MemorySegment addr = (MemorySegment) mmap.invoke(
                MemorySegment.NULL, (long) length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, (long) offset
        );
        Consumer<MemorySegment> cleanup = s -> {
            try {
            long off = s.get(JAVA_LONG,v4l2_buffer.byteOffset(groupElement("m_offset")));    
            int len = s.get(JAVA_INT,v4l2_buffer.byteOffset(groupElement("length")));
		unmapBuffer(off,len);	
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
        enqueueBuffer(buf);
        return addr.reinterpret(length, arena, cleanup);
    }

    public void unmapBuffer(long offset, long length) throws Throwable {

        int res = (int) munmap.invoke(offset, (long) length);
        if (res != 0) {
            Log.error("munmap failed");
        }
    }

    public void stop(SymbolLookup libc, Arena arena, int fd) throws Throwable {
        long VIDIOC_STREAMOFF = 1074026003L;
        int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;

        MemorySegment typeSeg = arena.allocate(JAVA_INT);
        typeSeg.set(JAVA_INT, 0, V4L2_BUF_TYPE_VIDEO_CAPTURE);
        Log.debug("stop streaming (if we are)");
        int res = (int) ioctl.invoke(fd, VIDIOC_STREAMOFF, typeSeg);
        if (res < 0) {
            Log.error("VIDIOC_STREAMOFF failed");
        }
        MethodHandle close = linker.downcallHandle(
                libc.find("close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );
        Log.debug("close video device");

        res = (int) close.invoke(fd);
        if (res != 0) {
            System.err.println("close failed");
        }
    }

}
