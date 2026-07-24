/*
#include <turbojpeg.h>
#include <stdint.h>

int jpeg_to_yuv420p(
    const uint8_t *jpeg,
    unsigned long jpegSize,
    uint8_t *yuv420p,
    int width,
    int height)
{
    tjhandle handle = tjInitDecompress();
    if (!handle)
        return -1;

    int w, h, subsamp, colorspace;

    if (tjDecompressHeader3(handle,
                            jpeg,
                            jpegSize,
                            &w,
                            &h,
                            &subsamp,
                            &colorspace) != 0)
    {
        tjDestroy(handle);
        return -2;
    }

    if (w != width || h != height)
    {
        tjDestroy(handle);
        return -3;
    }

    if (tjDecompressToYUV2(handle,
                           jpeg,
                           jpegSize,
                           yuv420p,
                           width,   // align to width (no padding)
                           4) != 0) // 4 = TJSAMP_420
    {
        tjDestroy(handle);
        return -4;
    }

    tjDestroy(handle);
    return 0;
}
 */
package pe.pi.turbojpeg;

import com.phono.srtplight.Log;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 *
 * @author thp
 */
public class JpegDec {

    final static int TJFLAG_STOPONWARNING = 8192;
    final static int TJFLAG_FASTUPSAMPLE = 256;
    final static int TJFLAG_FASTDCT = 2048;
    protected final Arena arena;
    protected final MethodHandle tjInitDecompress;
    protected final MethodHandle tjDecompressHeader3;
    protected final MethodHandle tjDecompressToYUV2;
    protected final MethodHandle tjDestroy;
    protected final MemorySegment handle;
    int width, height, flags;
    //protected final MemorySegment videoCapture;
    private final MemorySegment wp;
    private final MemorySegment hp;
    private final MemorySegment sap;
    private final MemorySegment csp;
    private MemorySegment jpegBuf;
    private final MemorySegment yuv;
    private final MethodHandle tjGetErrorStr;
    private final MethodHandle tjBufSizeYUV2;

    public JpegDec(int width, int height) throws Throwable {
        this.width = width;
        this.height = height;

        arena = Arena.ofConfined();
        Linker linker = Linker.nativeLinker();

        var tjLib = SymbolLookup.libraryLookup("libturbojpeg.so", arena);

        tjInitDecompress = linker.downcallHandle(
                tjLib.find("tjInitDecompress").orElseThrow(),
                FunctionDescriptor.of(ADDRESS)
        );
        tjDecompressHeader3 = linker.downcallHandle(
                tjLib.find("tjDecompressHeader3").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT,
                        ADDRESS, // tjhandle handle
                        ADDRESS, // char *jpegBuf
                        JAVA_LONG, //long jpegSize
                        ADDRESS, //int *width
                        ADDRESS, //int *height
                        ADDRESS, //int *jpegSubsamp
                        ADDRESS // int *jpegColorspace
                )
        );

        tjDestroy = linker.downcallHandle(
                tjLib.find("tjDestroy").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS // tjhandle
                )
        );

        tjDecompressToYUV2 = linker.downcallHandle(
                tjLib.find("tjDecompressToYUV2").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT,
                        ADDRESS, //tjhandle
                        ADDRESS, //const unsigned char *jpegBuf
                        JAVA_LONG,// long jpegSize
                        ADDRESS, // unsigned char *dstBuf
                        JAVA_INT, // width
                        JAVA_INT, // align
                        JAVA_INT, // height
                        JAVA_INT // flags
                )
        );
        // DLLEXPORT char *tjGetErrorStr2(tjhandle handle);
        tjGetErrorStr = linker.downcallHandle(
                tjLib.find("tjGetErrorStr").orElseThrow(),
                FunctionDescriptor.of(ADDRESS)
        );
        //DLLEXPORT unsigned long tjBufSizeYUV(int width, int height, int subsamp);
        tjBufSizeYUV2 = linker.downcallHandle(
                tjLib.find("tjBufSizeYUV2").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)
        );
        handle = (MemorySegment) tjInitDecompress.invokeExact();
        wp = arena.allocate(ValueLayout.JAVA_INT.byteSize());
        hp = arena.allocate(ValueLayout.JAVA_INT.byteSize());
        sap = arena.allocate(ValueLayout.JAVA_INT.byteSize());
        csp = arena.allocate(ValueLayout.JAVA_INT.byteSize());
        flags = TJFLAG_FASTDCT | TJFLAG_FASTUPSAMPLE;//| TJFLAG_STOPONWARNING;
        jpegBuf = arena.allocate(512000);
        yuv = arena.allocate((1920 * 1080 * 2));
        Log.debug("Inited decoder");

    }

/** 
 * remap the i420 data from to nv1 
     * @param i420 input buffer - 3 planes Y U and V U and v are vertically sub-sampled. From camera or jpeg decode
     * @param nv12 output buffer - 2 planes Y and UV where UV are vertically sub-sampled again. suitable for Nv12 encode in h264
     * @param width original image width from camera or jpeg
     * @param height original image height from camera or jpeg
*/
    public static void i420ToNV12(
            ByteBuffer i420,
            ByteBuffer nv12,
            int width,
            int height) {
        int ySize = width * height;
        int uvSize = ySize / 2; // input subsample

        // Copy Y plane as is
        nv12.clear();
        nv12.put(0, i420, 0, ySize);

        int uoffset = ySize; // input u plane start
        int voffset = ySize + uvSize; // input v plane start
        nv12.position(ySize); // output uv plane start
        byte rowu[] = new byte[width]; // hold u values to be subsampled
        byte rowv[] = new byte[width];// hold v values to be subsampled
        int i = 0;
        for (int r = 0; r < height / 2; r++) { // iterate over the input rows
            for (int c = 0; c < width; c++) { // iterate over the input columns
                if (r % 2 == 0) {
                    // collect the data in an even row and stash it
                    rowu[c] = i420.get(uoffset + i);
                    rowv[c] = i420.get(voffset + i);
                    i++;
                } else {
                    // collect the data in an odd row
                    byte u = i420.get(uoffset + i);
                    byte v = i420.get(voffset + i);
                    i++;
                    // then average it with the stash
                    int umean = ((0xff & u) + (0xff & rowu[c])) / 2;
                    int vmean = ((0xff & v) + (0xff & rowv[c])) / 2;
                    // and write it out
                    nv12.put((byte) umean);
                    nv12.put((byte) vmean);
                }
            }
        }
        i420.clear();
        nv12.flip();
    }

    public int decompress(ByteBuffer jpeg, ByteBuffer out) throws Throwable {
        long jpegSize = jpeg.remaining();
        if (jpegBuf.byteSize() < jpegSize) {
            jpegBuf = arena.allocate(jpegSize);
        }
        jpegBuf.asByteBuffer().put(jpeg);
        Log.debug("about to decode header");

        int result = (int) tjDecompressHeader3.invokeExact(handle, jpegBuf, jpegSize, wp, hp, sap, csp);
        if (result == 0) {
            int w = wp.get(JAVA_INT, 0);
            int h = hp.get(JAVA_INT, 0);
            int sa = sap.get(JAVA_INT, 0);
            int cs = csp.get(JAVA_INT, 0);

            Log.debug("decoded  w = " + w + " h = " + h + " sa =" + sa + " cs=" + cs);

            long yuvsize = (long) tjBufSizeYUV2.invokeExact(w, 1, h, sa);
            Log.debug("decoded yuv buffer needs" + yuvsize + " vs " + yuv.byteSize());

            if ((w == width) && (h == height)) {
                result = (int) tjDecompressToYUV2.invokeExact(handle, jpegBuf, jpegSize, yuv, w, 1, h, flags);
                if (result != 0) {
                    /*var error = (MemorySegment) tjGetErrorStr.invokeExact();
                    String errs = error.getString(0);*/
                    Log.error("Jpeg decode failed " + result);
                } else {
                    out.position(0);
                    out.put(yuv.asByteBuffer());
                    out.flip();
                    Log.debug("decode ok");
                }
            } else {
                Log.error("Jpeg dimensions unexpected");
            }

        } else {
            /*var error = (MemorySegment) tjGetErrorStr.invokeExact();
            String errs = error.getString(0);*/
            Log.error("Jpeg header didn't decompress " + result);
        }

        return result;
    }

    public static void main(String[] args) {
        Log.setLevel(Log.ALL);
        try {
            var peg = Paths.get("100.jpeg");
            var bmp = Paths.get("100.yuv");
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};
            var decoder = new JpegDec(1920, 1080);
            var outfile = Files.newOutputStream(bmp, options);

            var buf = Files.readAllBytes(peg);
            ByteBuffer in = ByteBuffer.wrap(buf);
            int len = buf.length;
            int outlen = (1920 * 1080 * 2);
            ByteBuffer out = ByteBuffer.allocate(outlen);
            for (int i = 0; i < 100; i++) {
                long then = System.currentTimeMillis();
                in.position(0);
                in.limit(len);
                out.position(0);
                decoder.decompress(in, out);
                System.out.println("took " + (System.currentTimeMillis() - then) + " ms");
            }
            outfile.write(out.array());
        } catch (Throwable x) {
            x.printStackTrace();
        }
    }
}
