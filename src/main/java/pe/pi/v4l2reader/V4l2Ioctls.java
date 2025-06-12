/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import static pe.pi.v4l2reader.RawRead.linker;

/**
 *
 * @author thp
 */
public class V4l2Ioctls extends V4l2Structs {

    final static long VIDIOC_STREAMOFF = 1074026003L;
    final static long VIDIOC_STREAMON = 1074026002L;
    final static long VIDIOC_QBUF = 3227014671L;
    final static long VIDIOC_REQBUFS = 3222558216L;
    final static long VIDIOC_DQBUF = 3227014673L;
    final static long VIDIOC_S_FMT = 0xC0D05605L;
    final static long VIDIOC_QUERYBUF = 3227014665L;

    final static int PROT_READ = 0x1, PROT_WRITE = 0x2, MAP_SHARED = 0x01; // strictly mmap not ioctl but hey...
    protected final Arena arena;
    protected final MethodHandle open;
    protected final MethodHandle ioctl;
    protected final MethodHandle munmap;
    protected final MethodHandle mmap;
    protected final SymbolLookup libc;
    protected final MethodHandle close;
    protected final MemorySegment videoCapture;

    public V4l2Ioctls() {
        super();
        arena = Arena.ofConfined();
        libc = linker.defaultLookup(); //

        open = linker.downcallHandle(
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
        close = linker.downcallHandle(
                libc.find("close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );
        videoCapture = arena.allocate(JAVA_INT);
        videoCapture.set(JAVA_INT, 0, V4L2_BUF_TYPE_VIDEO_CAPTURE);
    }
}
