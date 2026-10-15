package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import pe.pi.amlh264enc.AMLEncoder;
import pe.pi.libav.H264Dec;

/**
 *
 * @author thp
 */
public class MmapReadAndDecodeH264 extends MmapRead {

    private final H264Dec decoder;
    private final ByteBuffer i4xx;
    private final ByteBuffer nv12;

    public MmapReadAndDecodeH264(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate, V4L2_PIX_FMT_H264);
        Log.info("reading raw h264 from " + dev);
        decoder = new H264Dec(w, h);
        i4xx = ByteBuffer.allocate(2 * w * h);
        nv12 = ByteBuffer.allocate(3 * w * h / 2);
        Log.debug("i4xx is " + i4xx.capacity());
        Log.debug("nv12 is " + nv12.capacity());
    }

    @Override
    public ByteBuffer process(ByteBuffer frame) {
        ByteBuffer ret = null;
        try {
            Log.debug("got frame  " + frame.remaining());
            var fbuffer = new byte[frame.remaining()];
            frame.get(fbuffer);
            frame.flip();
            // Files.write(peg, fbuffer, options);

            int r = decoder.decompress(frame, i4xx);
            //Files.write(f420, i4xx.array(), options);

            if (r >= 0) {
                switch (r) {
                    case 0 -> {
                        Log.debug("i420 frame of " + i4xx.remaining());
                        H264Dec.i420ToNV12(i4xx, nv12, width, height);
                        Log.debug("nv12 frame of " + nv12.remaining());
                        ret = nv12;
                    }
                    case 1 -> {
                        Log.debug("i422 frame of " + i4xx.remaining());
                        H264Dec.i422ToNV12(i4xx, nv12, width, height);
                        Log.debug("nv12 frame of " + nv12.remaining());
                        ret = nv12;
                    }
                    default -> {
                        Log.debug("unexpected jpec frame decode  frame of " + r);
                        ret = i4xx;
                    }
                }
            } else {
                Log.error("couldn't de compress jpeg");
            }
        } catch (Throwable t) {
            Log.error("can't transcode because " + t.getMessage());
            t.printStackTrace();
        }
        return ret;
    }

    public static void main(String[] args) {
        Log.setLevel(Log.VERB);
        String adev = "/dev/video2";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            int w = 1920; int h=1080;
            var encoder = new AMLEncoder(w, h, 0, false, 30, false);
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};

            MmapReadAndDecodeH264 reader = new MmapReadAndDecodeH264(adev, w, h, 30) {
                @Override
                public ByteBuffer process(ByteBuffer frame) {
                    ByteBuffer ret = super.process(frame);
                    if ((ret != null) && (ret.hasRemaining())) {
                        try {
                            ret = encoder.encode(ret);
                            Log.debug("encoded frame to " + ret.remaining());
                        } catch (Throwable t) {
                            Log.error("can't transcode because " + t.getMessage());
                            t.printStackTrace();
                        }
                    } else {
                        Log.warn("Empty frame in H264Processor");
                    }
                    return ret;
                }
            };
            reader.startCap();
            int fno = 0;
            var h264 = Paths.get("/tmp/4s.h264");

            var fout = new BufferedOutputStream(Files.newOutputStream(h264, options),2000000);
            while (fno < 120) {
                if ((fno % 30) == 0) {
                    encoder.keyFrameReq();
                }
                var frame = reader.read();
                if (frame != null) {
                    fno++;
                    byte[] dst = new byte[frame.remaining()];
                    Log.debug("copying bytes " + dst.length);
                    frame.get(dst);
                    fout.write(dst);
                } else {
                    Log.warn("Skipping empty frame in write");
                }
            }
            fout.close();
        } catch (Throwable ex) {
            Log.error("can't read from " + adev + " because " + ex.getMessage());
            ex.printStackTrace();
        }
    }

}
