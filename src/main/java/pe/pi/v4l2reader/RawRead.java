/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package pe.pi.v4l2reader;

import java.nio.file.Files;
import com.phono.srtplight.Log;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;
import java.util.function.Consumer;
import java.nio.ByteBuffer;
import pe.pi.amlh264enc.Encoder;

/**
 *
 * @author thp
 */
public class RawRead extends V4l2Ioctls {

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

    MemorySegment buffers[];
    byte[] out;
    int videoDev =0;

    public RawRead(String dev) throws Throwable {
        super();
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

        long act = enable?VIDIOC_STREAMON:VIDIOC_STREAMOFF;
        int ret = (int) ioctl.invoke(videoDev, act, videoCapture);
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

        return fd;
    }

    public void setFormat(SymbolLookup libc, Arena arena, int fd) throws Throwable {

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
            Log.debug("Requested MMAP buffers: " + count);
        }
        return count;
    }

    public MemorySegment dqBuffer() throws Throwable {

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

        Log.debug("stop streaming (if we are)");
        int res = (int) ioctl.invoke(fd, VIDIOC_STREAMOFF, videoCapture);
        if (res < 0) {
            Log.error("VIDIOC_STREAMOFF failed");
        }

        Log.debug("close video device");

        res = (int) close.invoke(fd);
        if (res != 0) {
            System.err.println("close failed");
        }
    }

}
